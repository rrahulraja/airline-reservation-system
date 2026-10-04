# Airline Reservation System

Backend for a single-airline reservation system: flight schedule management, flight
search, seat inventory, booking, holds and cancellation.

**Stack:** Java 21 · Spring Boot 3.5 · PostgreSQL 16 · Flyway · Spring Data JPA ·
Spring Security (JWT) · Maven · JUnit 5 · Testcontainers

**Status:** 127 tests, all passing. The concurrency suite has been run ten consecutive
times with zero failures (§ Concurrency evidence).

---

## Quick start

```bash
docker compose up --build
```

That is the whole setup. It starts PostgreSQL 16, waits for its healthcheck, runs the
three Flyway migrations, seeds reference data and sample schedules, and serves the API
on `http://localhost:8080`.

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Seeded users (illustrative, not a production identity design):

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `customer` | `customer123` | CUSTOMER |

### Running locally without Docker for the app

```bash
docker compose up -d db          # PostgreSQL only
./mvnw spring-boot:run
```

Flyway migrates on startup from `db/migrations`, a path relative to the working
directory — run from the repository root or Flyway will find no migrations.

### Resetting the database

```bash
docker compose down -v           # stops and WIPES the volume
```

Do this whenever you change a migration: Flyway refuses to re-run an applied script
whose checksum changed.

### Tests

```bash
./mvnw test
```

Requires Docker: the integration tests run against real PostgreSQL via Testcontainers.
H2 is deliberately not used — the partial unique index and `INSERT ... ON CONFLICT`
that this design depends on do not exist there, so testing against it would validate
nothing that matters.

---

## Repository layout

```
README.md
hld/architecture.md            High-level design
hld/diagrams/                  5 diagrams: .drawio source + generated .svg and .png
lld/design.md                  Low-level design
pom.xml, src/main, src/test    Maven project
db/migrations/                 Flyway migrations - the schema source of truth
db/schema.sql                  Generated snapshot, for reading
tests/                         HTTP collection + concurrency smoke script
docker-compose.yml, Dockerfile
```

One deviation from the brief's suggested tree, stated rather than left to be
discovered: Maven requires `src/main/java` and `src/test/java`, so `pom.xml` sits at
the root with standard Maven layout (satisfying `src/` naturally), and `tests/` holds
the HTTP request collection and the concurrency smoke script.

---

## Design decisions

Each decision, its reason, and what was rejected.

1. **Flight instances are derived, not materialized.** Schedules are the source of
   truth; a `flight_instance` row is created lazily on first booking. Storage is
   proportional to real bookings, there is no generation job, and a schedule added
   mid-year is bookable immediately. *Rejected:* eager materialization (~100k rows plus
   backfill plus an operational failure mode where a missed job makes flights vanish).
2. **Seat maps are generated, not stored.** The aircraft configuration already
   describes every seat; only occupancy is persisted. *Rejected:* a seat row per
   instance, ~18M rows buying nothing.
3. **A partial unique index is the primary concurrency control.** Unconditional,
   survives application bugs and horizontal scaling. *Rejected:* `SELECT ... FOR UPDATE`
   as the primary mechanism — see § Booking algorithm.
4. **Seats are claimed in sorted order.** A consistent lock-acquisition order removes
   deadlocks between overlapping seat requests.
5. **A lapsed hold reads as free immediately.** Correctness stops depending on a
   scheduled job.
6. **A hold is a booking in `HELD` status**, not a separate entity. One lifecycle, and
   the feature stays removable.
7. **Flights are addressed by `flightNumber + date`**, never by instance id — the row
   may not exist yet. Follows directly from decision 1.
8. **Days of operation are a 7-bit mask.** One indexed column, a parameterized bit
   test. *Rejected:* a child table (a join for a seven-value set), a Postgres array
   (awkward JPA mapping).
9. **Native SQL for search only**, isolated in its own repository. JPQL cannot express
   the bitmask test or `LATERAL`; JPA retains entity lifecycle everywhere else.
10. **`Clock` is an injected bean.** The booking window and hold expiry become testable
    without sleeping.
11. **Cancel is `POST /{pnr}/cancel`.** A state transition; the record persists.
12. **Testcontainers over H2.** The mechanisms under test are PostgreSQL-specific.

---

## Search algorithm

**How flights are identified.** By `flightNumber + flightDate`. `flight_number` is
unique among active schedules (partial unique index), so the pair resolves
deterministically without exposing any database id.

**How schedules are matched.** One native query filters on route, on the date falling
within `valid_from .. valid_to`, and on the day-of-week bit:

```sql
SELECT s.id, s.flight_number, s.departure_time, s.arrival_time, s.arrival_day_offset,
       a.aircraft_type, a.row_count, a.seat_letters, COALESCE(c.occupied, 0) AS occupied
FROM flight_schedule s
JOIN aircraft a ON a.id = s.aircraft_id
LEFT JOIN flight_instance fi ON fi.schedule_id = s.id AND fi.flight_date = :date
LEFT JOIN LATERAL (
    SELECT count(*) AS occupied
    FROM seat_assignment sa
    WHERE sa.flight_instance_id = fi.id
      AND sa.status IN ('HELD', 'BOOKED')
      AND (sa.expires_at IS NULL OR sa.expires_at > now())
) c ON TRUE
WHERE s.active
  AND s.source_airport_id = :sourceAirportId
  AND s.destination_airport_id = :destinationAirportId
  AND :date BETWEEN s.valid_from AND s.valid_to
  AND (s.days_of_operation & :dayBit) > 0
ORDER BY s.departure_time
```

`:dayBit` is computed in Java as `1 << (dayOfWeek.getValue() - 1)`, keeping `EXTRACT`
out of the query.

**How instances are generated.** They are not, on this path. The `LEFT JOIN` to
`flight_instance` must stay a left join: most flights have no row because nobody has
booked them, and an inner join would hide exactly those. `fi.id` is then `NULL`, the
lateral count matches nothing, and `COALESCE` yields zero.

**How availability is computed.** `row_count × length(seat_letters) − occupied`, never
a stored counter. The `expires_at > now()` term is load-bearing: without it a lapsed
hold keeps occupying a seat until the sweep job runs, which would make that job a
correctness dependency. With it, availability tells the truth the instant a hold lapses.

---

## Booking algorithm

**Seat validation** — nine checks in order, detailed in `lld/design.md` § 4. Nothing is
written until all nine pass. Two are worth calling out:

- *Seat labels are normalized to uppercase.* This is a correctness requirement, not
  cosmetic: `ux_seat_active` enforces uniqueness on the exact stored string, so without
  normalization `12A` and `12a` would be two rows and the same physical seat would be
  sold twice.
- *A duplicate seat within one request is rejected before the database is touched.*
  Otherwise it hits the unique index and surfaces as a 409 claiming someone else took a
  seat nobody else booked.

**Seat locking strategy** — five layers, strongest first:

1. **Partial unique index** `ux_seat_active (flight_instance_id, seat_label) WHERE
   status IN ('HELD','BOOKED')`. Two customers cannot hold the same seat even if every
   line of service code is wrong.
2. **One transaction per booking, all seats inserted in a single sorted batch.**
3. **Fail-fast conflict mapping** to `409 SEAT_UNAVAILABLE` naming the lost seats.
4. **`ON CONFLICT DO NOTHING`** for the instance row, so concurrent first-bookers
   converge without a lock.
5. **`@Version`** on `booking` and `flight_instance` for status transitions.

**Transaction flow**

```
BEGIN
  validate (9 checks)                → 400 / 404 / 422, nothing written
  resolveOrCreate(schedule, date)    → INSERT ... ON CONFLICT DO NOTHING, then SELECT
  purge lapsed holds on this flight  → DELETE
  INSERT booking + passengers
  INSERT seat_assignment × N (sorted) + explicit flush()
                                     → unique violation ⇒ 409, whole transaction rolls back
COMMIT
```

**Why there is no `SELECT ... FOR UPDATE` on this path.** A row lock on
`flight_instance` would serialize every booking on a popular flight, and it is a
*weaker* guarantee than the index: it holds only while every writer remembers to take
it, whereas the index holds unconditionally, across application bugs and across
multiple application instances.

This is backed by evidence rather than assertion. `twentyThreadsSameSeatExactlyOneSucceeds`
proves safety — but a row-lock design passes it too. `concurrentDistinctSeatsAllSucceed`
proves the design does not over-lock: twenty threads booking twenty *different* seats
all succeed, where a row-lock design would queue them behind one lock.

**What would change the answer:** a requirement to read-then-decide across multiple
seats atomically — auto-assigning the best available block, say — where a conflict is
expensive to retry rather than cheap. Then the pessimistic variant earns its cost.

---

## Cancellation algorithm

```
BEGIN
  load booking by PNR               → 404 if unknown
  reject if not HELD or CONFIRMED   → 409 BOOKING_NOT_CANCELLABLE
  read the seat labels              (before the delete, so the response can report them)
  booking.status = CANCELLED
  DELETE seat_assignment WHERE booking_id = ?
COMMIT
```

**Seats are released by deleting the rows, not by flipping a status.** `ux_seat_active`
covers only `HELD` and `BOOKED`, so the absence of a row is what makes the seat sellable
again — no extra bookkeeping, and no stale `RELEASED` row blocking a sale.

**Passenger rows are kept.** They are the record of what was sold, so cancellation does
not erase history. This is why `seat_label` is deliberately duplicated across
`passenger` and `seat_assignment`: one records what was sold, the other what is
currently occupied.

A second cancellation returns 409 rather than an idempotent 200, because a client
cancelling twice has a stale view of the booking and saying so is more useful than
silently agreeing.

---

## Concurrency evidence

```
$ ./mvnw test -Dtest=ConcurrentBookingTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Run ten consecutive times: **10 passed, 0 failed.** A concurrency test that passes once
proves very little.

| Test | Asserts |
|---|---|
| `twentyThreadsSameSeatExactlyOneSucceeds` | 1 success, 19 × `SeatUnavailableException`, exactly 1 row in `seat_assignment` |
| `concurrentDistinctSeatsAllSucceed` | 20 distinct seats, 20 successes, 0 failures — proves no over-locking |
| `opposingSeatOrdersDoNotDeadlock` | 15 rounds of `[1A,1B]` vs `[1B,1A]`, no SQLSTATE 40P01, one winner each |
| `concurrentFirstBookingCreatesOneInstance` | 10 concurrent first-bookers share one `flight_instance` row |

Reproducible over HTTP with no Java toolchain:

```
$ docker compose up -d && bash tests/concurrency-smoke.sh
Target: XY101 on 2026-10-07, seat 29F, 20 parallel attempts

  201 Created        : 1
  409 Conflict       : 19
  other status codes : 0

PASS: exactly one booking won the seat; 19 lost cleanly with 409.
```

Full suite:

```
[INFO] Tests run: 127, Failures: 0, Errors: 0, Skipped: 0
```

---

## Error codes

Every failure returns one shape:

```json
{ "code": "SEAT_UNAVAILABLE", "message": "Seats no longer available: 12A",
  "details": ["12A"], "traceId": "b7f1c2e4", "timestamp": "2026-10-04T10:12:03Z" }
```

| HTTP | Codes | Meaning |
|---|---|---|
| 400 | `VALIDATION_ERROR`, `INVALID_SEAT_LABEL` | Malformed or self-inconsistent input |
| 401 | `UNAUTHENTICATED` | Missing or invalid token |
| 403 | `FORBIDDEN` | Authenticated but not permitted |
| 404 | `FLIGHT_NOT_FOUND`, `BOOKING_NOT_FOUND`, `AIRPORT_NOT_FOUND`, `AIRCRAFT_NOT_FOUND` | Unknown resource |
| 409 | `SEAT_UNAVAILABLE`, `BOOKING_NOT_CANCELLABLE`, `BOOKING_NOT_CONFIRMABLE`, `HOLD_EXPIRED`, `DUPLICATE_FLIGHT_NUMBER` | State or uniqueness conflict |
| 422 | `FLIGHT_NOT_OPERATING`, `DATE_OUT_OF_BOOKING_WINDOW` | Well-formed, rejected by a business rule |
| 500 | `INTERNAL_ERROR` | `traceId` only; nothing about the cause leaks |

`traceId` correlates the response with the logged stack trace via the MDC request id.

**No idempotency-key table.** A retried `POST /api/bookings` collides with
`ux_seat_active` and returns 409 — never a double booking. A deliberate choice, not an
omission.

---

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `booking.window-days` | 365 | Booking horizon |
| `booking.max-seats` | 9 | Seats per booking |
| `booking.hold.ttl` | PT5M | Hold lifetime |
| `booking.hold.sweep-interval` | PT1M | Expiry job cadence |
| `booking.hold.sweep-batch-size` | 200 | Rows per sweep transaction |
| `security.jwt.secret` | env `JWT_SECRET` | HS256 signing key (≥ 32 bytes) |
| `security.jwt.expiry` | PT1H | Token lifetime |

No service hard-codes any of these.

---

## curl walkthrough

```bash
# 1. Log in
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"customer","password":"customer123"}' | jq -r .token)

# 2. Search (XY101 flies Mon/Wed/Fri)
curl -s "localhost:8080/api/flights/search?origin=DXB&destination=LHR&date=2026-10-07" \
  -H "Authorization: Bearer $TOKEN" | jq
# [{"flightNumber":"XY101","departureAt":"2026-10-07T09:30:00Z","totalSeats":180,
#   "availableSeats":180, ...}]

# 3. Seat map
curl -s "localhost:8080/api/flights/XY101/seat-map?date=2026-10-07" \
  -H "Authorization: Bearer $TOKEN" | jq '.rows[0]'

# 4. Book two seats
PNR=$(curl -s -X POST localhost:8080/api/bookings \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"flightNumber":"XY101","flightDate":"2026-10-07","contactName":"R Raja",
       "passengers":[{"fullName":"R Raja","seatLabel":"12A"},
                     {"fullName":"A Sharma","seatLabel":"12B"}]}' | jq -r .pnr)

# 5. The same seat again - 409, naming the seat
curl -s -X POST localhost:8080/api/bookings \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"flightNumber":"XY101","flightDate":"2026-10-07",
       "passengers":[{"fullName":"Someone","seatLabel":"12A"}]}' | jq
# {"code":"SEAT_UNAVAILABLE","details":["12A"], ...}

# 6. Cancel - seats free immediately
curl -s -X POST "localhost:8080/api/bookings/$PNR/cancel" \
  -H "Authorization: Bearer $TOKEN" | jq .status
# "CANCELLED"
```

`tests/api-collection.http` covers the same flow plus holds and confirmation, runnable
from IntelliJ or the VS Code REST Client.

---

## Assumptions

From the brief: single airline; a seat belongs to at most one booking; a booking covers
one flight instance; seat maps are fixed per aircraft; schedules do not change after
creation; airports and aircraft are preloaded; the booking window is 365 days; all
timestamps are UTC and time-zone conversion is out of scope.

Added by this implementation:

1. Aircraft seating is a uniform grid — no blocked seats, no cabin classes.
2. At most one **active** schedule per flight number.
3. Schedule times are UTC times-of-day; `arrival_day_offset` handles overnight flights.
4. Maximum 9 seats per booking.
5. Passenger data is a name only; no validation.
6. A cancelled booking is retained; its inventory rows are deleted.
7. Authentication is illustrative: seeded users, HS256 JWT, BCrypt hashes.
8. **A flight that has already departed today cannot be booked**, even though the
   365-day window permits the date. The brief does not require this — it is a decision,
   and the alternative (allow it, as a standby-seat system might) is defensible.
9. **Seat labels are normalized to uppercase before any comparison**, because the unique
   index can only protect identically-spelled labels.

---

## Known limitations

1. The hold-expiry job runs on every instance in a multi-instance deployment. The fix is
   ShedLock or a PostgreSQL advisory lock; out of scope. **Correctness does not depend on
   the job** — search, the seat map and the booking path all treat a lapsed hold as free.
2. Search reads the schedule table on every request with no caching. Schedules are
   immutable, so a short-TTL cache is the obvious next step; unnecessary at this scale.
3. Under extreme contention for one seat, losing clients retry at the application layer;
   there is no queue or waitlist.
4. Seat maps assume a uniform grid. Real aircraft have missing seats at exits and
   bulkheads.
5. No multi-leg itineraries, so no cross-flight transactional booking.
6. A booking is readable and cancellable by anyone holding its PNR. Scoping lookups to
   the authenticated user is a small change, deliberately out of scope for the brief.
7. JWTs cannot be revoked before expiry — the cost of statelessness.
8. `db/schema.sql` is generated and must be regenerated when migrations change.
