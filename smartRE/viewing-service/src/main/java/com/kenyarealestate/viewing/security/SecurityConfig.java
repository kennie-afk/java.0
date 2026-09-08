package com.kenyarealestate.viewing.security;
import com.kenyarealestate.viewing.ratelimit.RateLimitFilter;
import org.springframework.context.annotation.*; import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy; import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;
    private final InternalSecretFilter internalSecretFilter;
    private final RateLimitFilter rateLimitFilter;
    public SecurityConfig(JwtAuthenticationFilter f, InternalSecretFilter isf, RateLimitFilter rlf) {
        this.jwtFilter=f; this.internalSecretFilter=isf; this.rateLimitFilter=rlf;
    }
    @Bean public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a
                .requestMatchers("/api/viewings/internal/**").permitAll()
                .requestMatchers("/swagger-ui/**","/v3/api-docs/**","/actuator/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(internalSecretFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            // After the JWT filter, so the bucket can be keyed on who is calling rather
            // than on an address that a whole office may share.
            .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
