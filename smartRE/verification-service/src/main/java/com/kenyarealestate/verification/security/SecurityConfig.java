package com.kenyarealestate.verification.security;
import com.kenyarealestate.verification.ratelimit.RateLimitFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final RateLimitFilter rateLimitFilter;
    private final InternalSecretFilter internalSecretFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, InternalSecretFilter internalSecretFilter, RateLimitFilter rateLimitFilter) {
        this.rateLimitFilter = rateLimitFilter;
        this.jwtFilter = jwtFilter;
        this.internalSecretFilter = internalSecretFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET,  "/api/verification/trust-status/**").permitAll()
                .requestMatchers("/api/verification/identity/internal/**").permitAll()
                .requestMatchers("/api/verification/ownership/internal/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll()
                .requestMatchers("/api/verification/identity/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/verification/ownership/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/verification/reports/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(internalSecretFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            // After the JWT filter, so the bucket can be keyed on who is calling
            // rather than on an address that a whole office may share.
            .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
