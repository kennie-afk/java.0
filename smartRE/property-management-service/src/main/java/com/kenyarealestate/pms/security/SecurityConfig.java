package com.kenyarealestate.pms.security;
import com.kenyarealestate.pms.ratelimit.RateLimitFilter;

import org.springframework.context.annotation.*;
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

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, InternalSecretFilter internalSecretFilter, RateLimitFilter rateLimitFilter) {
        this.rateLimitFilter = rateLimitFilter;
        this.jwtFilter = jwtFilter;
        this.internalSecretFilter = internalSecretFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/pms/internal/**").permitAll()
                .requestMatchers("/api/pms/admin/**").hasRole("ADMIN")
                // The tenancy side of the house. These were reachable by any authenticated
                // account, which is what made TENANT a label rather than a role: a seller
                // could call /my-tenancy and a tenant could call the landlord views.
                //
                // Expressed as request matchers rather than @PreAuthorize deliberately -
                // this service does not enable method security, so an annotation here would
                // be decoration that reads like enforcement.
                .requestMatchers("/api/*/my-tenancy", "/api/*/my-tenancy/**")
                    .hasAnyRole("TENANT", "ADMIN")
                .requestMatchers("/api/*/my", "/api/*/my/**")
                    .hasAnyRole("LANDLORD", "ADMIN")
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(internalSecretFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            // After the JWT filter, so the bucket can be keyed on who is calling
            // rather than on an address that a whole office may share.
            .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
