package com.airline.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AirlineReservationApplication {

    public static void main(String[] args) {
        // All stored times are UTC. Pinning the JVM keeps any remaining default-zone
        // conversion from depending on the host's OS timezone.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(AirlineReservationApplication.class, args);
    }
}
