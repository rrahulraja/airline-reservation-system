-- Airline reservation system - initial schema.
--
-- Design notes that matter when reading this file:
--
--  * No seat rows are stored. A seat exists because the aircraft configuration
--    (row_count x seat_letters) says so. Only occupancy is persisted, in
--    seat_assignment.
--  * flight_instance rows are created lazily, on first booking or hold for a
--    (schedule, date) pair. Schedules are the source of truth for what flies.
--  * ux_seat_active is the double-booking guarantee. It is a PARTIAL unique
--    index and must stay partial: cancellation and hold expiry delete rows, and
--    the predicate is what lets a released seat be sold again.

CREATE TABLE airport (
    id        BIGSERIAL    PRIMARY KEY,
    iata_code CHAR(3)      NOT NULL UNIQUE,
    name      VARCHAR(120) NOT NULL,
    city      VARCHAR(80)  NOT NULL,
    country   VARCHAR(80)  NOT NULL
);

CREATE TABLE aircraft (
    id            BIGSERIAL   PRIMARY KEY,
    aircraft_code VARCHAR(16) NOT NULL UNIQUE,
    aircraft_type VARCHAR(40) NOT NULL,
    row_count     INT         NOT NULL CHECK (row_count BETWEEN 1 AND 100),
    seat_letters  VARCHAR(12) NOT NULL CHECK (seat_letters ~ '^[A-Z]+$')
);

CREATE TABLE app_user (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(60)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('ADMIN', 'CUSTOMER')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE flight_schedule (
    id                     BIGSERIAL   PRIMARY KEY,
    flight_number          VARCHAR(8)  NOT NULL,
    source_airport_id      BIGINT      NOT NULL REFERENCES airport (id),
    destination_airport_id BIGINT      NOT NULL REFERENCES airport (id),
    departure_time         TIME        NOT NULL,
    arrival_time           TIME        NOT NULL,
    arrival_day_offset     SMALLINT    NOT NULL DEFAULT 0
                                       CHECK (arrival_day_offset BETWEEN 0 AND 1),
    aircraft_id            BIGINT      NOT NULL REFERENCES aircraft (id),
    -- 7-bit mask, bit 0 = Monday ... bit 6 = Sunday.
    days_of_operation      SMALLINT    NOT NULL CHECK (days_of_operation BETWEEN 1 AND 127),
    valid_from             DATE        NOT NULL,
    valid_to               DATE        NOT NULL,
    active                 BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_schedule_distinct_airports CHECK (source_airport_id <> destination_airport_id),
    CONSTRAINT ck_schedule_validity_range    CHECK (valid_to >= valid_from)
);

-- At most one active schedule per flight number. Schedules are immutable once
-- created, so this is a safe invariant rather than a limitation.
CREATE UNIQUE INDEX ux_schedule_active_flight_number
    ON flight_schedule (flight_number) WHERE active;

CREATE INDEX ix_schedule_route
    ON flight_schedule (source_airport_id, destination_airport_id) WHERE active;

CREATE TABLE flight_instance (
    id           BIGSERIAL   PRIMARY KEY,
    schedule_id  BIGINT      NOT NULL REFERENCES flight_schedule (id),
    flight_date  DATE        NOT NULL,
    departure_at TIMESTAMPTZ NOT NULL,
    arrival_at   TIMESTAMPTZ NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED',
    version      BIGINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Makes lazy creation race-safe: concurrent first-bookers converge on one
    -- row via INSERT ... ON CONFLICT DO NOTHING.
    CONSTRAINT ux_instance_schedule_date UNIQUE (schedule_id, flight_date)
);

CREATE INDEX ix_instance_date ON flight_instance (flight_date);

CREATE TABLE booking (
    id                 BIGSERIAL    PRIMARY KEY,
    pnr                CHAR(6)      NOT NULL UNIQUE,
    flight_instance_id BIGINT       NOT NULL REFERENCES flight_instance (id),
    status             VARCHAR(16)  NOT NULL
                         CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    -- No upper bound here on purpose: the per-booking seat cap is the
    -- configurable booking.max-seats and is enforced in the service layer. A
    -- literal in the database would silently contradict the configuration.
    passenger_count    SMALLINT     NOT NULL CHECK (passenger_count >= 1),
    contact_name       VARCHAR(120),
    created_by_user_id BIGINT       REFERENCES app_user (id),
    hold_expires_at    TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX ix_booking_instance ON booking (flight_instance_id);

-- Keeps the expiry sweep from scanning anything it does not need.
CREATE INDEX ix_booking_hold_expiry
    ON booking (hold_expires_at) WHERE status = 'HELD';

CREATE TABLE passenger (
    id         BIGSERIAL    PRIMARY KEY,
    booking_id BIGINT       NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    full_name  VARCHAR(120) NOT NULL,
    -- The record of what was sold. Survives cancellation, unlike the
    -- seat_assignment row, which is live inventory.
    seat_label VARCHAR(4)   NOT NULL
);

CREATE INDEX ix_passenger_booking ON passenger (booking_id);

CREATE TABLE seat_assignment (
    id                 BIGSERIAL   PRIMARY KEY,
    flight_instance_id BIGINT      NOT NULL REFERENCES flight_instance (id),
    seat_label         VARCHAR(4)  NOT NULL,
    booking_id         BIGINT      NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    status             VARCHAR(16) NOT NULL CHECK (status IN ('HELD', 'BOOKED')),
    expires_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- THE double-booking guarantee.
--
-- Partial on purpose. The predicate covers both HELD and BOOKED, so confirming a
-- hold is an in-place UPDATE that never frees the seat, while cancellation and
-- hold expiry DELETE the row and the seat becomes immediately sellable again.
--
-- This holds regardless of application correctness and across multiple
-- application instances, which is why no row locking is needed on the booking
-- happy path.
CREATE UNIQUE INDEX ux_seat_active
    ON seat_assignment (flight_instance_id, seat_label)
    WHERE status IN ('HELD', 'BOOKED');

CREATE INDEX ix_seat_booking ON seat_assignment (booking_id);
