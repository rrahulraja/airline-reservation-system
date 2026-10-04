-- GENERATED FILE - do not edit.
-- Produced by: docker compose exec db pg_dump -U airline --schema-only airline
-- The source of truth is db/migrations/. Regenerate after changing a migration.

--
-- PostgreSQL database dump
--

\restrict RfPUFc0O41UNXqCi4cKwqZqtpbDimb6EjmskltBfgLykcopsAF5EhlhnaUSPK8X

-- Dumped from database version 16.14 (Debian 16.14-1.pgdg13+1)
-- Dumped by pg_dump version 16.14 (Debian 16.14-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: aircraft; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.aircraft (
    id bigint NOT NULL,
    aircraft_code character varying(16) NOT NULL,
    aircraft_type character varying(40) NOT NULL,
    row_count integer NOT NULL,
    seat_letters character varying(12) NOT NULL,
    CONSTRAINT aircraft_row_count_check CHECK (((row_count >= 1) AND (row_count <= 100))),
    CONSTRAINT aircraft_seat_letters_check CHECK (((seat_letters)::text ~ '^[A-Z]+$'::text))
);


--
-- Name: aircraft_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.aircraft_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: aircraft_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.aircraft_id_seq OWNED BY public.aircraft.id;


--
-- Name: airport; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.airport (
    id bigint NOT NULL,
    iata_code character varying(3) NOT NULL,
    name character varying(120) NOT NULL,
    city character varying(80) NOT NULL,
    country character varying(80) NOT NULL
);


--
-- Name: airport_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.airport_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: airport_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.airport_id_seq OWNED BY public.airport.id;


--
-- Name: app_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_user (
    id bigint NOT NULL,
    username character varying(60) NOT NULL,
    password_hash character varying(100) NOT NULL,
    role character varying(16) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT app_user_role_check CHECK (((role)::text = ANY ((ARRAY['ADMIN'::character varying, 'CUSTOMER'::character varying])::text[])))
);


--
-- Name: app_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: app_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_user_id_seq OWNED BY public.app_user.id;


--
-- Name: booking; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.booking (
    id bigint NOT NULL,
    pnr character varying(6) NOT NULL,
    flight_instance_id bigint NOT NULL,
    status character varying(16) NOT NULL,
    passenger_count smallint NOT NULL,
    contact_name character varying(120),
    created_by_user_id bigint,
    hold_expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT booking_passenger_count_check CHECK ((passenger_count >= 1)),
    CONSTRAINT booking_status_check CHECK (((status)::text = ANY ((ARRAY['HELD'::character varying, 'CONFIRMED'::character varying, 'CANCELLED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: booking_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.booking_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: booking_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.booking_id_seq OWNED BY public.booking.id;


--
-- Name: flight_instance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flight_instance (
    id bigint NOT NULL,
    schedule_id bigint NOT NULL,
    flight_date date NOT NULL,
    departure_at timestamp with time zone NOT NULL,
    arrival_at timestamp with time zone NOT NULL,
    status character varying(16) DEFAULT 'SCHEDULED'::character varying NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: flight_instance_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.flight_instance_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: flight_instance_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.flight_instance_id_seq OWNED BY public.flight_instance.id;


--
-- Name: flight_schedule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flight_schedule (
    id bigint NOT NULL,
    flight_number character varying(8) NOT NULL,
    source_airport_id bigint NOT NULL,
    destination_airport_id bigint NOT NULL,
    departure_time time without time zone NOT NULL,
    arrival_time time without time zone NOT NULL,
    arrival_day_offset smallint DEFAULT 0 NOT NULL,
    aircraft_id bigint NOT NULL,
    days_of_operation smallint NOT NULL,
    valid_from date NOT NULL,
    valid_to date NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_schedule_distinct_airports CHECK ((source_airport_id <> destination_airport_id)),
    CONSTRAINT ck_schedule_validity_range CHECK ((valid_to >= valid_from)),
    CONSTRAINT flight_schedule_arrival_day_offset_check CHECK (((arrival_day_offset >= 0) AND (arrival_day_offset <= 1))),
    CONSTRAINT flight_schedule_days_of_operation_check CHECK (((days_of_operation >= 1) AND (days_of_operation <= 127)))
);


--
-- Name: flight_schedule_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.flight_schedule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: flight_schedule_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.flight_schedule_id_seq OWNED BY public.flight_schedule.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: passenger; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.passenger (
    id bigint NOT NULL,
    booking_id bigint NOT NULL,
    full_name character varying(120) NOT NULL,
    seat_label character varying(4) NOT NULL
);


--
-- Name: passenger_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.passenger_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: passenger_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.passenger_id_seq OWNED BY public.passenger.id;


--
-- Name: seat_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seat_assignment (
    id bigint NOT NULL,
    flight_instance_id bigint NOT NULL,
    seat_label character varying(4) NOT NULL,
    booking_id bigint NOT NULL,
    status character varying(16) NOT NULL,
    expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT seat_assignment_status_check CHECK (((status)::text = ANY ((ARRAY['HELD'::character varying, 'BOOKED'::character varying])::text[])))
);


--
-- Name: seat_assignment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seat_assignment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: seat_assignment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.seat_assignment_id_seq OWNED BY public.seat_assignment.id;


--
-- Name: aircraft id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aircraft ALTER COLUMN id SET DEFAULT nextval('public.aircraft_id_seq'::regclass);


--
-- Name: airport id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.airport ALTER COLUMN id SET DEFAULT nextval('public.airport_id_seq'::regclass);


--
-- Name: app_user id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user ALTER COLUMN id SET DEFAULT nextval('public.app_user_id_seq'::regclass);


--
-- Name: booking id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking ALTER COLUMN id SET DEFAULT nextval('public.booking_id_seq'::regclass);


--
-- Name: flight_instance id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flight_instance ALTER COLUMN id SET DEFAULT nextval('public.flight_instance_id_seq'::regclass);


--
-- Name: flight_schedule id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flight_schedule ALTER COLUMN id SET DEFAULT nextval('public.flight_schedule_id_seq'::regclass);


--
-- Name: passenger id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.passenger ALTER COLUMN id SET DEFAULT nextval('public.passenger_id_seq'::regclass);


--
-- Name: seat_assignment id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seat_assignment ALTER COLUMN id SET DEFAULT nextval('public.seat_assignment_id_seq'::regclass);


--
-- Name: aircraft aircraft_aircraft_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aircraft
    ADD CONSTRAINT aircraft_aircraft_code_key UNIQUE (aircraft_code);


--
-- Name: aircraft aircraft_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aircraft
    ADD CONSTRAINT aircraft_pkey PRIMARY KEY (id);


--
-- Name: airport airport_iata_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.airport
    ADD CONSTRAINT airport_iata_code_key UNIQUE (iata_code);


--
-- Name: airport airport_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.airport
    ADD CONSTRAINT airport_pkey PRIMARY KEY (id);


--
-- Name: app_user app_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);


--
-- Name: app_user app_user_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_username_key UNIQUE (username);


--
-- Name: booking booking_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking
    ADD CONSTRAINT booking_pkey PRIMARY KEY (id);


--
-- Name: booking booking_pnr_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking
    ADD CONSTRAINT booking_pnr_key UNIQUE (pnr);


--
-- Name: flight_instance flight_instance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flight_instance
    ADD CONSTRAINT flight_instance_pkey PRIMARY KEY (id);


--
-- Name: flight_schedule flight_schedule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flight_schedule
    ADD CONSTRAINT flight_schedule_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: passenger passenger_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.passenger
    ADD CONSTRAINT passenger_pkey PRIMARY KEY (id);


--
-- Name: seat_assignment seat_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seat_assignment
    ADD CONSTRAINT seat_assignment_pkey PRIMARY KEY (id);


--
-- Name: flight_instance ux_instance_schedule_date; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flight_instance
    ADD CONSTRAINT ux_instance_schedule_date UNIQUE (schedule_id, flight_date);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: ix_booking_hold_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_booking_hold_expiry ON public.booking USING btree (hold_expires_at) WHERE ((status)::text = 'HELD'::text);


--
-- Name: ix_booking_instance; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_booking_instance ON public.booking USING btree (flight_instance_id);


--
-- Name: ix_instance_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_instance_date ON public.flight_instance USING btree (flight_date);


--
-- Name: ix_passenger_booking; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_passenger_booking ON public.passenger USING btree (booking_id);


--
-- Name: ix_schedule_route; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_schedule_route ON public.flight_schedule USING btree (source_airport_id, destination_airport_id) WHERE active;


--
-- Name: ix_seat_booking; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_seat_booking ON public.seat_assignment USING btree (booking_id);


--
-- Name: ux_schedule_active_flight_number; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_schedule_active_flight_number ON public.flight_schedule USING btree (flight_number) WHERE active;


--
-- Name: ux_seat_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_seat_active ON public.seat_assignment USING btree (flight_instance_id, seat_label) WHERE ((status)::text = ANY ((ARRAY['HELD'::character varying, 'BOOKED'::character varying])::text[]));


--
-- Name: booking booking_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking
    ADD CONSTRAINT booking_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.app_user(id);


--
-- Name: booking booking_flight_instance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking
    ADD CONSTRAINT booking_flight_instance_id_fkey FOREIGN KEY (flight_instance_id) REFERENCES public.flight_instance(id);


--
-- Name: flight_instance flight_instance_schedule_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flight_instance
    ADD CONSTRAINT flight_instance_schedule_id_fkey FOREIGN KEY (schedule_id) REFERENCES public.flight_schedule(id);


--
-- Name: flight_schedule flight_schedule_aircraft_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flight_schedule
    ADD CONSTRAINT flight_schedule_aircraft_id_fkey FOREIGN KEY (aircraft_id) REFERENCES public.aircraft(id);


--
-- Name: flight_schedule flight_schedule_destination_airport_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flight_schedule
    ADD CONSTRAINT flight_schedule_destination_airport_id_fkey FOREIGN KEY (destination_airport_id) REFERENCES public.airport(id);


--
-- Name: flight_schedule flight_schedule_source_airport_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flight_schedule
    ADD CONSTRAINT flight_schedule_source_airport_id_fkey FOREIGN KEY (source_airport_id) REFERENCES public.airport(id);


--
-- Name: passenger passenger_booking_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.passenger
    ADD CONSTRAINT passenger_booking_id_fkey FOREIGN KEY (booking_id) REFERENCES public.booking(id) ON DELETE CASCADE;


--
-- Name: seat_assignment seat_assignment_booking_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seat_assignment
    ADD CONSTRAINT seat_assignment_booking_id_fkey FOREIGN KEY (booking_id) REFERENCES public.booking(id) ON DELETE CASCADE;


--
-- Name: seat_assignment seat_assignment_flight_instance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seat_assignment
    ADD CONSTRAINT seat_assignment_flight_instance_id_fkey FOREIGN KEY (flight_instance_id) REFERENCES public.flight_instance(id);


--
-- PostgreSQL database dump complete
--

\unrestrict RfPUFc0O41UNXqCi4cKwqZqtpbDimb6EjmskltBfgLykcopsAF5EhlhnaUSPK8X

