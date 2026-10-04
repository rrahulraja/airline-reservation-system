package com.airline.booking.support;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Runs N callables as simultaneously as the JVM allows, and reports what each one did.
 *
 * <p>All workers block on a start latch and are released together, so they contend for the
 * same instant rather than running in a staggered sequence that would prove nothing.
 *
 * <p>IMPORTANT for callers: each worker must reach the service through Spring so it gets
 * its own transaction and its own EntityManager. Never share an EntityManager or a managed
 * entity across threads, and never annotate the calling test @Transactional, which would
 * enlist every worker in one transaction and remove all contention.
 */
public final class ConcurrentRunner {

    private ConcurrentRunner() {
    }

    public record Outcome<T>(List<T> successes, List<Throwable> failures) {

        public int successCount() {
            return successes.size();
        }

        public int failureCount() {
            return failures.size();
        }
    }

    public static <T> Outcome<T> runAll(int threads, Callable<T> task) throws InterruptedException {
        var successes = new ConcurrentLinkedQueue<T>();
        var failures = new ConcurrentLinkedQueue<Throwable>();
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        successes.add(task.call());
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            if (!done.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Workers did not finish within 60s - likely a deadlock or a lock wait");
            }
        } finally {
            pool.shutdownNow();
        }

        return new Outcome<>(List.copyOf(successes), List.copyOf(failures));
    }

    /** Runs two different tasks concurrently, once. */
    public static <T> Outcome<T> runPair(Callable<T> first, Callable<T> second)
            throws InterruptedException {
        var successes = new ConcurrentLinkedQueue<T>();
        var failures = new ConcurrentLinkedQueue<Throwable>();
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            for (Callable<T> task : List.of(first, second)) {
                pool.submit(() -> {
                    try {
                        start.await();
                        successes.add(task.call());
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            if (!done.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Pair did not finish - deadlock?");
            }
        } finally {
            pool.shutdownNow();
        }

        return new Outcome<>(List.copyOf(successes), List.copyOf(failures));
    }

    /** Whether a throwable chain contains a PostgreSQL deadlock (SQLSTATE 40P01). */
    public static boolean isDeadlock(Throwable t) {
        Throwable cause = t;
        while (cause != null) {
            if (cause instanceof java.sql.SQLException sql && "40P01".equals(sql.getSQLState())) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null && message.toLowerCase().contains("deadlock")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
