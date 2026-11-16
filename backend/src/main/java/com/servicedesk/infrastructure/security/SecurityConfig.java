package com.servicedesk.infrastructure.security;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security configuration for the ServiceDesk REST API.
 *
 * <ul>
 *   <li><b>Stateless bearer tokens</b>: clients log in once and send {@code Authorization: Bearer
 *       <jwt>} with every request. No server session, so CSRF protection is unnecessary and is
 *       disabled.
 *   <li><b>Public</b>: health checks, login and self-registration. <b>Everything else</b> needs a
 *       valid token.
 *   <li><b>Role-based authorisation</b> in three layers: URL rules here, {@code @PreAuthorize} on
 *       controllers (method security), and the domain's own permission checks on every action - so
 *       a missing annotation can never open a hole.
 *   <li>Errors are returned as JSON ({@link JsonSecurityErrorHandler}).
 *   <li>CORS origins come from configuration.
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final List<String> allowedOrigins;

  public SecurityConfig(
      @Value("${app.security.cors.allowed-origins:http://localhost:3000}")
          String allowedOriginsStr) {
    this.allowedOrigins =
        Arrays.stream(allowedOriginsStr.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, ActiveUserJwtConverter jwtConverter, JsonSecurityErrorHandler errors)
      throws Exception {
    http
        // stateless REST API using bearer tokens, no cookies: CSRF does not apply
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/health", "/actuator/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMINISTRATOR")
                    .requestMatchers("/api/v1/reports/**")
                    .hasAuthority("VIEW_REPORTS")
                    .requestMatchers("/api/v1/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                    .authenticationEntryPoint(errors)
                    .accessDeniedHandler(errors))
        .exceptionHandling(ex -> ex.authenticationEntryPoint(errors).accessDeniedHandler(errors));

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
    configuration.setExposedHeaders(List.of("Authorization"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
