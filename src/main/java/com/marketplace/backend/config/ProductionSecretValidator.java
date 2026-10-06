package com.marketplace.backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionSecretValidator
        implements ApplicationRunner {

    private final Environment environment;

    public ProductionSecretValidator(
            Environment environment
    ) {
        this.environment = environment;
    }

    @Override
    public void run(
            ApplicationArguments args
    ) {

        String jwtSecret =
                getFirstAvailable(
                        "JWT_SECRET",
                        "jwt.secret",
                        "jwt.secret-key"
                );

        String databasePassword =
                getFirstAvailable(
                        "DB_PASSWORD",
                        "spring.datasource.password"
                );

        String paystackSecret =
                getFirstAvailable(
                        "PAYSTACK_SECRET_KEY",
                        "paystack.secret-key"
                );

        requireSecret(
                "JWT secret",
                jwtSecret,
                32
        );

        requireSecret(
                "Database password",
                databasePassword,
                8
        );

        requireSecret(
                "Paystack secret key",
                paystackSecret,
                10
        );

        String allowedOrigins =
                environment.getProperty(
                        "app.cors.allowed-origins",
                        ""
                );

        if (allowedOrigins.contains(
                "localhost"
        )) {
            throw new IllegalStateException(
                    "Production CORS must not allow localhost"
            );
        }

        if (allowedOrigins.contains(
                "*"
        )) {
            throw new IllegalStateException(
                    "Production CORS must not use wildcard origins"
            );
        }
    }

    private String getFirstAvailable(
            String... propertyNames
    ) {

        for (String propertyName :
                propertyNames) {

            String value =
                    environment.getProperty(
                            propertyName
                    );

            if (value != null &&
                    !value.isBlank()) {

                return value;
            }
        }

        return null;
    }

    private static void requireSecret(
            String name,
            String value,
            int minimumLength
    ) {

        if (value == null ||
                value.isBlank()) {

            throw new IllegalStateException(
                    name +
                            " is required in production"
            );
        }

        if (value.length() <
                minimumLength) {

            throw new IllegalStateException(
                    name +
                            " is too short for production"
            );
        }
    }
}