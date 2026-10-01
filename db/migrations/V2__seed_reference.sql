-- Reference data. Airports and aircraft are preloaded and have no CRUD API.

INSERT INTO airport (iata_code, name, city, country) VALUES
    ('DXB', 'Dubai International Airport',                  'Dubai',     'United Arab Emirates'),
    ('LHR', 'Heathrow Airport',                             'London',    'United Kingdom'),
    ('BOM', 'Chhatrapati Shivaji Maharaj International',     'Mumbai',    'India'),
    ('DEL', 'Indira Gandhi International Airport',          'Delhi',     'India'),
    ('SIN', 'Singapore Changi Airport',                     'Singapore', 'Singapore'),
    ('JFK', 'John F. Kennedy International Airport',        'New York',  'United States');

-- Two deliberately different geometries. A test that hard-codes 180 seats will
-- fail against the ATR72, which is the point.
INSERT INTO aircraft (aircraft_code, aircraft_type, row_count, seat_letters) VALUES
    ('A320-01',  'A320',  30, 'ABCDEF'),  -- 180 seats
    ('A320-02',  'A320',  30, 'ABCDEF'),  -- 180 seats
    ('ATR72-01', 'ATR72', 18, 'ABCD');    --  72 seats

-- Illustrative credentials, not a production identity design.
-- Plaintext passwords: admin -> admin123, customer -> customer123
-- Hashes are BCrypt, cost 10.
INSERT INTO app_user (username, password_hash, role) VALUES
    ('admin',    '$2a$10$2uAKyI4zf4.6e.S5IUb9NODR247K09CmDpYAbnj.5bEydVxF02L5u', 'ADMIN'),
    ('customer', '$2a$10$XCIXjiuLLVi.DZvhY.qKk.38Mg7HUvMu/Ay.M1PsVYEkJlKxIHkUC', 'CUSTOMER');
