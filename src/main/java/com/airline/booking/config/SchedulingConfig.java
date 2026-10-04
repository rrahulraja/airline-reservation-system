package com.airline.booking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Scheduled.
 *
 * <p>Its own class so tests can neutralize it. A sweep firing in the background mid-test
 * makes failures non-reproducible: the test and the job race over the same rows and the
 * result depends on timing.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
