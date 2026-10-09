package com.airline.booking.flight;

import com.airline.booking.schedule.FlightSchedule;
import com.airline.booking.schedule.ScheduleService;
import com.airline.booking.support.AbstractIntegrationTest;
import com.airline.booking.support.InventoryFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FlightInstanceResolverTest extends AbstractIntegrationTest {

    @Autowired
    private FlightInstanceResolver resolver;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private InventoryFixture inventory;

    /**
     * resolveOrCreate is MANDATORY, so calling it without a transaction throws. That is
     * the design refusing to be used in a way that could orphan a row, so the test
     * supplies a transaction, mirroring how BookingService will call it.
     */
    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void tidy() {
        inventory.cleanUp();
    }

    @Test
    @DisplayName("the first call creates one instance with UTC times from the schedule")
    void createsInstanceOnFirstCall() {
        LocalDate date = nextWeekday(DayOfWeek.WEDNESDAY);

        var instance = transactionTemplate.execute(s ->
                resolver.resolveOrCreate(schedule("XY101"), date));

        assertThat(instance).isNotNull();
        assertThat(instance.getFlightDate()).isEqualTo(date);
        assertThat(instance.getDepartureAt()).isEqualTo(date.atTime(9, 30).toInstant(ZoneOffset.UTC));
        assertThat(instance.getArrivalAt()).isEqualTo(date.atTime(13, 45).toInstant(ZoneOffset.UTC));
        assertThat(instance.getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("instance times do not depend on the JVM's default timezone")
    void timesIgnoreJvmTimezone() {
        LocalDate date = nextWeekday(DayOfWeek.WEDNESDAY);
        TimeZone original = TimeZone.getDefault();
        try {
            // IST, +05:30. Before java_time_use_direct_jdbc, the schedule's 09:30 TIME
            // column read back as 15:00 here and the instance departed at 15:00Z.
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));

            var instance = transactionTemplate.execute(s ->
                    resolver.resolveOrCreate(schedule("XY101"), date));

            assertThat(instance.getDepartureAt())
                    .isEqualTo(date.atTime(9, 30).toInstant(ZoneOffset.UTC));
            assertThat(instance.getArrivalAt())
                    .isEqualTo(date.atTime(13, 45).toInstant(ZoneOffset.UTC));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("calling twice returns the same row, not a second one")
    void idempotent() {
        LocalDate date = nextWeekday(DayOfWeek.WEDNESDAY);

        Long first = transactionTemplate.execute(s ->
                resolver.resolveOrCreate(schedule("XY101"), date).getId());
        Long second = transactionTemplate.execute(s ->
                resolver.resolveOrCreate(schedule("XY101"), date).getId());

        assertThat(second).isEqualTo(first);
        assertThat(inventory.countInstances()).isEqualTo(1);
    }

    @Test
    @DisplayName("an overnight schedule arrives on the following day")
    void respectsArrivalDayOffset() {
        LocalDate date = nextWeekday(DayOfWeek.WEDNESDAY);

        var instance = transactionTemplate.execute(s ->
                resolver.resolveOrCreate(schedule("XY102"), date));

        assertThat(instance.getArrivalAt().atZone(ZoneOffset.UTC).toLocalDate())
                .isEqualTo(date.plusDays(1));
        assertThat(instance.getArrivalAt()).isAfter(instance.getDepartureAt());
    }

    @Test
    @DisplayName("ten concurrent first calls create exactly one row and all see the same id")
    void concurrentFirstCallCreatesExactlyOne() throws Exception {
        LocalDate date = nextWeekday(DayOfWeek.WEDNESDAY);
        FlightSchedule schedule = schedule("XY101");

        int threads = 10;
        var ids = ConcurrentHashMap.<Long>newKeySet();
        var failures = ConcurrentHashMap.<Throwable>newKeySet();
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    // Each worker gets its OWN transaction. Sharing one would make this
                    // test prove nothing about concurrency.
                    ids.add(transactionTemplate.execute(s ->
                            resolver.resolveOrCreate(schedule, date).getId()));
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(failures).as("no call may fail").isEmpty();
        assertThat(ids).as("every caller must see the same instance").hasSize(1);
        assertThat(inventory.countInstances()).isEqualTo(1);
    }

    private FlightSchedule schedule(String flightNumber) {
        return transactionTemplate.execute(s ->
                scheduleService.requireActiveByFlightNumber(flightNumber));
    }

    private static LocalDate nextWeekday(DayOfWeek day) {
        return LocalDate.now().with(TemporalAdjusters.next(day));
    }
}
