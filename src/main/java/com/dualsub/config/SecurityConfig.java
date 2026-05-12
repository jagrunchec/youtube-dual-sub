package com.dualsub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — SPA uses session cookie (SameSite=Lax is enough for localhost)
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                // Public: auth endpoints + static front-end resources
                .requestMatchers(
                    "/api/auth/**",
                    "/", "/index.html", "/app.js", "/style.css",
                    "/favicon.svg", "/favicon.ico",
                    "/*.js", "/*.css", "/*.svg", "/*.png", "/*.ico"
                ).permitAll()
                // Admin-only
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Everything else requires login
                .anyRequest().authenticated()
            )

            // Return 401 JSON instead of redirecting to /login (SPA-friendly)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )

            // Session management
            .sessionManagement(session -> session
                .maximumSessions(5)   // allow multiple devices
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
