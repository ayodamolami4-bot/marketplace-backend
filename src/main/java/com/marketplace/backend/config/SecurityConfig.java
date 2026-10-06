package com.marketplace.backend.config;

import com.marketplace.backend.auth.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final CustomUserDetailsService
            customUserDetailsService;

    private final AuthRateLimitFilter
            authRateLimitFilter;

    public SecurityConfig(
            CustomUserDetailsService customUserDetailsService,
            AuthRateLimitFilter authRateLimitFilter
    ) {

        this.customUserDetailsService =
                customUserDetailsService;

        this.authRateLimitFilter =
                authRateLimitFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            PasswordEncoder passwordEncoder
    ) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(
                        customUserDetailsService
                );

        provider.setPasswordEncoder(
                passwordEncoder
        );

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            DaoAuthenticationProvider authenticationProvider
    ) {

        return new ProviderManager(
                authenticationProvider
        );
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> {

                    List<String> roles =
                            jwt.getClaimAsStringList(
                                    "roles"
                            );

                    if (roles == null ||
                            roles.isEmpty()) {

                        return List.of();
                    }

                    Collection<GrantedAuthority> authorities =
                            roles.stream()
                                    .filter(
                                            role ->
                                                    role != null &&
                                                            !role.isBlank()
                                    )
                                    .map(
                                            role ->
                                                    role.trim()
                                                            .toUpperCase()
                                    )
                                    .map(
                                            role ->
                                                    role.startsWith(
                                                            "ROLE_"
                                                    )
                                                            ? role
                                                            : "ROLE_" + role
                                    )
                                    .distinct()
                                    .map(
                                            role ->
                                                    (GrantedAuthority)
                                                            new SimpleGrantedAuthority(
                                                                    role
                                                            )
                                    )
                                    .toList();

                    return authorities;
                }
        );

        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {

        http
                .csrf(
                        csrf ->
                                csrf.disable()
                )

                .cors(
                        Customizer.withDefaults()
                )

                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(
                                        SessionCreationPolicy.STATELESS
                                )
                )

                .authorizeHttpRequests(
                        auth ->
                                auth

                                        /*
                                         * Infrastructure health endpoints.
                                         *
                                         * No sensitive details are exposed.
                                         */
                                        .requestMatchers(
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/livez",
                                                "/readyz"
                                        )
                                        .permitAll()

                                        /*
                                         * Other exposed monitoring endpoints
                                         * require ADMIN.
                                         */
                                        .requestMatchers(
                                                "/actuator/**"
                                        )
                                        .hasRole(
                                                "ADMIN"
                                        )

                                        .requestMatchers(
                                                "/api/v1/auth/signup",
                                                "/api/v1/auth/login"
                                        )
                                        .permitAll()

                                        .requestMatchers(
                                                "/error"
                                        )
                                        .permitAll()

                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/products",
                                                "/api/v1/products/**",
                                                "/api/v1/categories",
                                                "/api/v1/categories/**"
                                        )
                                        .permitAll()

                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/payments/webhook"
                                        )
                                        .permitAll()

                                        .requestMatchers(
                                                "/api/v1/vendor/**"
                                        )
                                        .hasRole(
                                                "VENDOR"
                                        )

                                        .requestMatchers(
                                                "/api/v1/admin/**"
                                        )
                                        .hasRole(
                                                "ADMIN"
                                        )

                                        .anyRequest()
                                        .authenticated()
                )

                .oauth2ResourceServer(
                        oauth2 ->
                                oauth2
                                        .jwt(
                                                jwt ->
                                                        jwt.jwtAuthenticationConverter(
                                                                jwtAuthenticationConverter
                                                        )
                                        )

                                        .authenticationEntryPoint(
                                                (
                                                        request,
                                                        response,
                                                        exception
                                                ) ->
                                                        writeSecurityError(
                                                                response,
                                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                                "UNAUTHORIZED",
                                                                "Authentication is required"
                                                        )
                                        )
                )

                .exceptionHandling(
                        exceptions ->
                                exceptions

                                        .authenticationEntryPoint(
                                                (
                                                        request,
                                                        response,
                                                        exception
                                                ) ->
                                                        writeSecurityError(
                                                                response,
                                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                                "UNAUTHORIZED",
                                                                "Authentication is required"
                                                        )
                                        )

                                        .accessDeniedHandler(
                                                (
                                                        request,
                                                        response,
                                                        exception
                                                ) ->
                                                        writeSecurityError(
                                                                response,
                                                                HttpServletResponse.SC_FORBIDDEN,
                                                                "FORBIDDEN",
                                                                "You do not have permission to perform this action"
                                                        )
                                        )
                )

                .addFilterBefore(
                        authRateLimitFilter,
                        BearerTokenAuthenticationFilter.class
                )

                .formLogin(
                        form ->
                                form.disable()
                )

                .httpBasic(
                        basic ->
                                basic.disable()
                )

                .logout(
                        logout ->
                                logout.disable()
                );

        return http.build();
    }

    private static void writeSecurityError(
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {

        response.setStatus(
                status
        );

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        String body =
                """
                {
                  "error": {
                    "code": "%s",
                    "message": "%s"
                  }
                }
                """.formatted(
                        code,
                        message
                );

        response.getWriter()
                .write(
                        body
                );
    }
}