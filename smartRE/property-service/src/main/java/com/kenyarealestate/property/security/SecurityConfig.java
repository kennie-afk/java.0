package com.kenyarealestate.property.security;
import com.kenyarealestate.property.ratelimit.RateLimitFilter;

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
                .requestMatchers(HttpMethod.GET, "/api/properties/search").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/properties/{id}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/properties/seller/{sellerId}").permitAll()
                .requestMatchers("/api/properties/internal/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                 "/v3/api-docs/**", "/actuator/**").permitAll()
                .requestMatchers("/api/properties/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/properties").hasAnyRole("SELLER", "LANDLORD")
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
