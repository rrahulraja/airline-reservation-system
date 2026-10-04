# Low-Level Design — Airline Reservation System

Companion to `hld/architecture.md`. This document covers modules, classes, public
signatures, validation, error handling and transaction boundaries.

Base package: `com.airline.booking`.

---

## 1. Modules

| Package | Classes | Responsibility |
|---|---|---|
| `config` | `ClockConfig`, `BookingProperties`, `OpenApiConfig`, `SchedulingConfig` | The `Clock` bean, externalized policy, API docs, scheduling |
| `common` | `ErrorCode`, `DomainException` (+8 subclasses), `ApiError`, `GlobalExceptionHandler`, `RequestIdFilter`, `PnrGenerator` | One error contract, request correlation, PNRs |
| `security` | `Role`, `AppUser`, `AppUserRepository`, `AuthenticatedUser`, `JwtService`, `JwtAuthFilter`, `SecurityConfig`, `AuthController` | Stateless JWT auth, two roles |
| `airport` | `Airport`, `AirportRepository`, `AirportService`, `AirportController` | Reference data, IATA resolution |
| `aircraft` | `Aircraft`, `AircraftRepository`, `AircraftController`, `SeatMapGenerator` | Reference data, seat-label generation and normalization |
| `schedule` | `FlightSchedule`, `DaysOfOperation`, `DaysOfOperationConverter`, `ScheduleRepository`, `ScheduleValidator`, `ScheduleService`, `AdminScheduleController` | Schedule creation and lookup |
| `flight` | `FlightInstance`, `FlightInstanceRepository`, `FlightInstanceResolver`, `FlightSearchRepository`, `FlightSearchRow`, `FlightSearchService`, `SeatMapService`, `SeatAssignmentLookup`, `BookingWindowValidator`, `FlightController` | Instance derivation, search, seat map |
| `booking` | `Booking`, `Passenger`, `SeatAssignment`, `BookingStatus`, `SeatStatus`, repositories, `BookingRequestValidator`, `SeatClaimService`, `BookingService`, `HoldService`, `HoldExpiryJob`, `BookingController` | Seat claim and booking lifecycle |

---

## 2. Key classes and signatures

### Seat labels — `aircraft.SeatMapGenerator`

```java
List<String>     generateLabels(int rowCount, String seatLetters);  // row-major, 1-based
int              capacity(int rowCount, String seatLetters);
Optional<String> normalize(String rawLabel);   // trims, uppercases, rejects malformed
boolean          isValidFor(String normalizedLabel, int rowCount, String seatLetters);
```

`normalize` and `isValidFor` are separate because they fail for different reasons and
map to different messages: malformed input (`"A12"`) versus a well-formed label that
does not exist on this aircraft (`"31A"` on a 30-row plane). It also rejects leading
zeros, since `"01A"` would otherwise be a second spelling of row 1 and defeat the
unique index.

### Days of operation — `schedule.DaysOfOperation`

```java
record DaysOfOperation(short mask) {
    static DaysOfOperation of(Set<DayOfWeek> days);
    static DaysOfOperation ofMask(short mask);
    static short           bitFor(DayOfWeek day);   // Monday = 1 … Sunday = 64
    boolean                operatesOn(DayOfWeek day);
    Set<DayOfWeek>         days();
}
```

`bitFor` has two call sites that must agree exactly: `operatesOn`, and the `:dayBit`
parameter of the native search query. One definition, so they cannot drift.

### The seam — `flight.FlightInstanceResolver`

```java
@Transactional(propagation = MANDATORY)
FlightInstance resolveOrCreate(FlightSchedule schedule, LocalDate date);
```

`MANDATORY` is deliberate: it refuses to run outside a caller's transaction, because
instance creation and seat claim must commit or roll back together.

### The seat claim primitive — `booking.SeatClaimService`

```java
@Transactional(propagation = MANDATORY)
List<SeatAssignment> claimSeats(FlightInstance instance,
                                Booking booking,
                                List<String> sortedLabels,   // normalized AND sorted
                                SeatStatus status,
                                Instant expiresAt);
```

Every booking entry point routes through this, so one concurrency proof covers all of
them.

### Booking lifecycle — `booking.BookingService` / `HoldService`

```java
BookingResponse create(CreateBookingRequest request, Long userId);   // CONFIRMED
BookingResponse findByPnr(String pnr);
BookingResponse cancel(String pnr);
BookingResponse hold(CreateHoldRequest request, Long userId);        // HELD + expiry
BookingResponse confirm(String pnr);                                  // HELD → CONFIRMED
int             sweep();                                              // expire lapsed holds
```

---

## 3. API

| Method | Path | Role | Success | Notable failures |
|---|---|---|---|---|
| POST | `/api/auth/login` | public | 200 | 401 `UNAUTHENTICATED` |
| GET | `/api/airports` | any | 200 | 401 |
| GET | `/api/aircraft` | ADMIN | 200 | 403 `FORBIDDEN` |
| POST | `/api/admin/schedules` | ADMIN | 201 | 400, 404, 409 `DUPLICATE_FLIGHT_NUMBER` |
| GET | `/api/admin/schedules` | ADMIN | 200 | 403 |
| GET | `/api/admin/schedules/{id}` | ADMIN | 200 | 404 |
| GET | `/api/flights/search` | CUSTOMER | 200 (may be empty) | 400, 404 `AIRPORT_NOT_FOUND`, 422 |
| GET | `/api/flights/{flightNumber}/seat-map` | CUSTOMER | 200 | 404, 422 |
| POST | `/api/bookings` | CUSTOMER | 201 | 400, 404, 409 `SEAT_UNAVAILABLE`, 422 |
| GET | `/api/bookings/{pnr}` | CUSTOMER | 200 | 404 `BOOKING_NOT_FOUND` |
| POST | `/api/bookings/{pnr}/cancel` | CUSTOMER | 200 | 404, 409 `BOOKING_NOT_CANCELLABLE` |
| POST | `/api/bookings/holds` | CUSTOMER | 201 | same as booking |
| POST | `/api/bookings/{pnr}/confirm` | CUSTOMER | 200 | 409 `HOLD_EXPIRED`, 409 `BOOKING_NOT_CONFIRMABLE` |

### Why flights are addressed by `flightNumber + date`

Not by instance id. This follows directly from lazy generation: the instance row may
not exist while a customer is looking at the flight, so an id would be unaddressable
until someone books. `flight_number` is unique among active schedules, so the pair
resolves deterministically.

It is the clearest example in this codebase of the API design following from the data
design rather than being chosen independently — and it has a real consequence: the API
exposes no database ids at all on customer-facing endpoints.

### Why cancel is `POST /{pnr}/cancel`, not `DELETE /{pnr}`

The booking persists with a changed status. `DELETE` would advertise that the record
disappears. This is a state transition, not resource removal.

---

## 4. Validation

### Booking and hold — `BookingRequestValidator`, in this order

| # | Check | Failure |
|---|---|---|
| 1 | Active schedule exists for the flight number | 404 `FLIGHT_NOT_FOUND` |
| 2 | Date within `[today, today + window-days]` | 422 `DATE_OUT_OF_BOOKING_WINDOW` |
| 3 | Flight operates that weekday, within validity range | 422 `FLIGHT_NOT_OPERATING` |
| 4 | Departure has not already passed | 422 `FLIGHT_NOT_OPERATING` |
| 5 | Seat count ≤ `booking.max-seats` | 400 `VALIDATION_ERROR` |
| 6 | Every label normalizes | 400 `INVALID_SEAT_LABEL` |
| 7 | Every label exists on the aircraft | 400 `INVALID_SEAT_LABEL` |
| 8 | No duplicate label within the request | 400 `VALIDATION_ERROR` |
| 9 | Labels sorted for claiming | — |

The order is chosen so the client learns about the problem it can act on first.
Check 8 exists so a duplicate is rejected *before* reaching the database; otherwise it
surfaces as a 409 claiming someone else took a seat nobody else booked.

### Schedule creation — `ScheduleValidator`

Bean validation covers shape (pattern, presence, range). Cross-field rules live in the
validator: origin ≠ destination; `validTo >= validFrom`; non-empty day set; and a
same-day arrival must follow departure, or `arrivalDayOffset` must be 1. That last
rule prevents a typo creating a flight that arrives before it departs, which would
corrupt every arrival time derived from it.

---

## 5. Error handling

One response shape:

```json
{ "code": "SEAT_UNAVAILABLE", "message": "Seats no longer available: 12A",
  "details": ["12A"], "traceId": "b7f1c2e4", "timestamp": "2026-10-04T10:12:03Z" }
```

| HTTP | Codes |
|---|---|
| 400 | `VALIDATION_ERROR`, `INVALID_SEAT_LABEL` |
| 401 / 403 | `UNAUTHENTICATED`, `FORBIDDEN` |
| 404 | `FLIGHT_NOT_FOUND`, `BOOKING_NOT_FOUND`, `AIRPORT_NOT_FOUND`, `AIRCRAFT_NOT_FOUND` |
| 409 | `SEAT_UNAVAILABLE`, `BOOKING_NOT_CANCELLABLE`, `BOOKING_NOT_CONFIRMABLE`, `HOLD_EXPIRED`, `DUPLICATE_FLIGHT_NUMBER` |
| 422 | `FLIGHT_NOT_OPERATING`, `DATE_OUT_OF_BOOKING_WINDOW` |
| 500 | `INTERNAL_ERROR` (traceId only) |

The status lives on the `ErrorCode` enum, so `GlobalExceptionHandler` never writes a
status literal and two endpoints cannot disagree about what a code means.

**No handler echoes a framework exception message to the client.** Jackson's parse
errors quote the request body and name the target class; `DataIntegrityViolationException`
quotes SQL. The 500 handler returns a fixed string plus the `traceId`, and a test
asserts the thrown message, the exception type and the package name all stay out of
the response.

**Security failures are handled separately.** Spring Security rejects requests inside
the filter chain, before `@RestControllerAdvice` runs, so `SecurityConfig` supplies an
`AuthenticationEntryPoint` and an `AccessDeniedHandler` that serialize the same
`ApiError` with the same `ObjectMapper`. Without them, 401 and 403 would be HTML while
every other error is JSON.

---

## 6. Transaction boundaries

| Operation | Propagation | Notes |
|---|---|---|
| `FlightSearchService.search` | `REQUIRED`, readOnly | no writes, ever |
| `SeatMapService.seatMap` | `REQUIRED`, readOnly | must not materialize an instance |
| `BookingService.create` / `createInternal` | `REQUIRED` | validation through seat insert |
| `FlightInstanceResolver.resolveOrCreate` | **`MANDATORY`** | joins the caller's transaction |
| `SeatClaimService.claimSeats` | **`MANDATORY`** | same |
| `BookingService.cancel` | `REQUIRED` | status update + inventory delete |
| `HoldService.hold` / `confirm` | `REQUIRED` | |
| `HoldExpiryJob.sweep` | `REQUIRED` | one transaction per batch |

Two `MANDATORY` propagations are load-bearing. If either opened its own transaction, a
rolled-back booking could leave an orphaned `flight_instance` row or orphaned seat
assignments.

---

## 7. Two implementation details that are easy to get wrong

### Flush timing in `SeatClaimService`

Hibernate flushes at transaction commit by default, and commit happens *outside* the
service method. A `try/catch` around the insert therefore never sees the constraint
violation — it surfaces after the method returns and renders as `500` instead of `409`.
The fix is an explicit `entityManager.flush()` inside the `try`. The same trap appears
in milder form in `ScheduleService`, which uses `saveAndFlush` for the same reason.

### Context clearing in `HoldExpiryJob`

`deleteByBookingIdIn` is annotated `clearAutomatically = true`, which detaches the
entire persistence context. Marking bookings `EXPIRED` *after* the delete silently did
nothing, because the entities were detached — seats were released while bookings stayed
`HELD` forever. The sweep now expires and flushes first, then deletes in one statement
rather than a loop, because each call clears the context.

Both were found by tests rather than by review, which is the argument for having the
tests assert on database state and not just on status codes.
