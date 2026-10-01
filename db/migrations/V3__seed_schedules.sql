-- Sample schedules so flight search returns results on a fresh database.
--
-- days_of_operation is a 7-bit mask, bit 0 = Monday:
--   Mon 1, Tue 2, Wed 4, Thu 8, Fri 16, Sat 32, Sun 64
--
-- valid_to is deliberately CURRENT_DATE + 400 days, past the 365-day booking
-- window. Search must reject a date beyond the window even though the schedule
-- itself is still valid then, and the seeds make that case reachable.

INSERT INTO flight_schedule (
    flight_number, source_airport_id, destination_airport_id,
    departure_time, arrival_time, arrival_day_offset,
    aircraft_id, days_of_operation, valid_from, valid_to, active
)
SELECT 'XY101', src.id, dst.id, TIME '09:30', TIME '13:45', 0,
       ac.id, 21, CURRENT_DATE, CURRENT_DATE + 400, TRUE
FROM airport src, airport dst, aircraft ac
WHERE src.iata_code = 'DXB' AND dst.iata_code = 'LHR' AND ac.aircraft_code = 'A320-01';

-- Return leg, crossing midnight: arrival_day_offset = 1.
INSERT INTO flight_schedule (
    flight_number, source_airport_id, destination_airport_id,
    departure_time, arrival_time, arrival_day_offset,
    aircraft_id, days_of_operation, valid_from, valid_to, active
)
SELECT 'XY102', src.id, dst.id, TIME '21:30', TIME '06:45', 1,
       ac.id, 21, CURRENT_DATE, CURRENT_DATE + 400, TRUE
FROM airport src, airport dst, aircraft ac
WHERE src.iata_code = 'LHR' AND dst.iata_code = 'DXB' AND ac.aircraft_code = 'A320-01';

-- Operates every day: mask 127.
INSERT INTO flight_schedule (
    flight_number, source_airport_id, destination_airport_id,
    departure_time, arrival_time, arrival_day_offset,
    aircraft_id, days_of_operation, valid_from, valid_to, active
)
SELECT 'XY201', src.id, dst.id, TIME '06:00', TIME '08:15', 0,
       ac.id, 127, CURRENT_DATE, CURRENT_DATE + 400, TRUE
FROM airport src, airport dst, aircraft ac
WHERE src.iata_code = 'BOM' AND dst.iata_code = 'DEL' AND ac.aircraft_code = 'A320-02';

-- Weekends only, on the small aircraft (72 seats).
INSERT INTO flight_schedule (
    flight_number, source_airport_id, destination_airport_id,
    departure_time, arrival_time, arrival_day_offset,
    aircraft_id, days_of_operation, valid_from, valid_to, active
)
SELECT 'XY202', src.id, dst.id, TIME '20:45', TIME '23:00', 0,
       ac.id, 96, CURRENT_DATE, CURRENT_DATE + 400, TRUE
FROM airport src, airport dst, aircraft ac
WHERE src.iata_code = 'DEL' AND dst.iata_code = 'BOM' AND ac.aircraft_code = 'ATR72-01';

-- Tuesday and Thursday only: mask 10.
INSERT INTO flight_schedule (
    flight_number, source_airport_id, destination_airport_id,
    departure_time, arrival_time, arrival_day_offset,
    aircraft_id, days_of_operation, valid_from, valid_to, active
)
SELECT 'XY301', src.id, dst.id, TIME '02:30', TIME '14:00', 0,
       ac.id, 10, CURRENT_DATE, CURRENT_DATE + 400, TRUE
FROM airport src, airport dst, aircraft ac
WHERE src.iata_code = 'DXB' AND dst.iata_code = 'SIN' AND ac.aircraft_code = 'A320-01';
