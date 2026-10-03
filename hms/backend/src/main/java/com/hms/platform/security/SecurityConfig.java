package com.hms.platform.security;

import com.hms.platform.config.HmsProperties;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(HmsProperties props) {
        List<String> origins = Arrays.stream(props.cors().allowedOrigins().split(",")).map(String::trim).filter(o -> !o.isEmpty()).toList();
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.addAllowedHeader("*");
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setExposedHeaders(List.of("Retry-After", "ETag"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TokenFilter tokenFilter, IdempotencyFilter idempotencyFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.contentTypeOptions(c -> {}).frameOptions(f -> f.deny())
                        .httpStrictTransportSecurity(t -> t.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(401);
                    response.setContentType("application/problem+json");
                    response.getWriter().write("{\"title\":\"Unauthorized\",\"status\":401,\"code\":\"unauthorized\",\"detail\":\"Sign in to continue.\"}");
                }))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/objects/*/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/auth/login", "/v1/organisations", "/portal/auth/login", "/portal/auth/activate").permitAll()
                        .requestMatchers("/portal/**").hasAuthority("portal:self")
                        .anyRequest().authenticated())
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(idempotencyFilter, TokenFilter.class);
        return http.build();
    }
}
