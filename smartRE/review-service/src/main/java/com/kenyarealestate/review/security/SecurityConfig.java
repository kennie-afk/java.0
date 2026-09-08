package com.kenyarealestate.review.security;
import com.kenyarealestate.review.ratelimit.RateLimitFilter;
import org.springframework.context.annotation.*; import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy; import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;
    private final RateLimitFilter rateLimitFilter;
    public SecurityConfig(JwtAuthenticationFilter f, RateLimitFilter rateLimitFilter) {
        this.rateLimitFilter = rateLimitFilter; this.jwtFilter=f; }
    @Bean public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a
                .requestMatchers("/api/reviews/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/reviews/**").permitAll()
                .requestMatchers("/swagger-ui/**","/v3/api-docs/**","/actuator/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            // After the JWT filter, so the bucket can be keyed on who is calling
            // rather than on an address that a whole office may share.
            .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
