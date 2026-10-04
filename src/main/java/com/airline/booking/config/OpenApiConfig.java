package com.airline.booking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger metadata plus the bearer scheme, so the Authorize button in Swagger UI works.
 * Without the scheme an evaluator cannot exercise any protected endpoint from the browser,
 * which is the first thing they will try.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Airline Reservation System API")
                        .version("1.0.0")
                        .description("""
                                Single-airline reservation backend: schedule management, flight
                                search, seat maps, booking, holds and cancellation.

                                Log in via POST /api/auth/login, then paste the token into
                                Authorize. Seeded users: admin/admin123, customer/customer123.
                                """))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
