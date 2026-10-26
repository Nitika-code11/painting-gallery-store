package com.example.painting.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Lets the H2 database console (dev profile only) bypass the API's JWT rules. The console has its own login
 * page, and it needs frames from the same origin to render.
 *
 * This is a second, separate security filter chain. {@code @Order(HIGHEST_PRECEDENCE)} makes it be consulted
 * first, but it only matches /h2-console/**, so every API request still goes through {@link SecurityConfig}.
 * Both conditions below keep it out of any non-dev environment.
 */

@Configuration
@Profile("dev")
@ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
public class DevH2ConsoleSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain h2ConsoleSecurity(HttpSecurity http) throws Exception {
        http
                .securityMatcher(PathRequest.toH2Console())                 // apply only to the H2 console URLs
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()) // the console authenticates itself
                .csrf(AbstractHttpConfigurer::disable)                      // the console posts forms without Spring's CSRF token
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)) // console uses iframes
                .cors(Customizer.withDefaults());
        return http.build();
    }
}