package com.kenyarealestate.payment.security;
import com.kenyarealestate.payment.ratelimit.RateLimitFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final RateLimitFilter rateLimitFilter;
    private final InternalSecretFilter internalSecretFilter;

    public SecurityConfig(JwtAuthenticationFilter f, InternalSecretFilter internalSecretFilter, RateLimitFilter rateLimitFilter) {
        this.rateLimitFilter = rateLimitFilter;
        this.jwtFilter = f;
        this.internalSecretFilter = internalSecretFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/payments/mpesa/callback/**").permitAll()
                        .requestMatchers("/api/payments/mpesa/c2b/**").permitAll()
                        .requestMatchers("/api/revenue/mpesa/b2c/callback/**").permitAll()
                        .requestMatchers("/api/revenue/mpesa/b2c/status-callback/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/payments/config").permitAll()
                        .requestMatchers("/api/payments/internal/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll()
                        .requestMatchers("/api/revenue/**").hasRole("ADMIN")
                        .requestMatchers("/api/payments/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(internalSecretFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            // After the JWT filter, so the bucket can be keyed on who is calling
            // rather than on an address that a whole office may share.
            .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
