package com.soko.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.addAllowedOriginPattern("*");
        cors.addAllowedHeader("*");
        cors.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TokenFilter tokenFilter)
            throws Exception {
        http.exceptionHandling(
                        handling ->
                                handling.authenticationEntryPoint(
                                        (request, response, ex) -> {
                                            response.setStatus(401);
                                            response.setContentType("application/problem+json");
                                            response.getWriter()
                                                    .write(
                                                            "{\"title\":\"unauthorized\",\"status\":401,"
                                                                + "\"detail\":\"This endpoint needs a bearer token\","
                                                                + "\"code\":\"unauthorized\"}");
                                        }))
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/actuator/health", "/actuator/info").permitAll()
                                        .requestMatchers(HttpMethod.POST, "/v1/auth/**").permitAll()
                                        .requestMatchers("/v1/supplier/**").hasRole("SUPPLIER")
                                        .requestMatchers("/v1/shop/**").hasRole("CUSTOMER")
                                        .requestMatchers("/v1/**")
                                        .hasAnyRole("OWNER", "OPERATOR")
                                        .anyRequest().authenticated())
                .exceptionHandling(
                        handling ->
                                handling.accessDeniedHandler(
                                        (request, response, ex) -> {
                                            response.setStatus(403);
                                            response.setContentType("application/problem+json");
                                            response.getWriter()
                                                    .write(
                                                            "{\"title\":\"forbidden\",\"status\":403,"
                                                                + "\"detail\":\"That account may not use this endpoint\","
                                                                + "\"code\":\"forbidden\"}");
                                        }))
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
