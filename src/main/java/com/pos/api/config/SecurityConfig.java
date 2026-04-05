package com.pos.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.api.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/login", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/users").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/change-password").hasAnyRole("OWNER", "ADMIN", "CASHIER")

                        .requestMatchers(HttpMethod.GET, "/products", "/products/**").hasAnyRole("OWNER", "ADMIN", "CASHIER")
                        .requestMatchers(HttpMethod.POST, "/products").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/products/**").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/products/**").hasAnyRole("OWNER", "ADMIN")

                        .requestMatchers(HttpMethod.GET, "/categories", "/categories/**").hasAnyRole("OWNER", "ADMIN", "CASHIER")
                        .requestMatchers(HttpMethod.POST, "/categories").hasAnyRole("OWNER", "ADMIN")

                        .requestMatchers(HttpMethod.GET, "/branches", "/branches/**").hasAnyRole("OWNER", "ADMIN", "CASHIER")
                        .requestMatchers(HttpMethod.POST, "/branches").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/branches/**").hasAnyRole("OWNER", "ADMIN")

                        .requestMatchers(HttpMethod.GET, "/accounts", "/accounts/**").hasAnyRole("OWNER", "ADMIN", "CASHIER")
                        .requestMatchers(HttpMethod.POST, "/accounts").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/accounts/**").hasAnyRole("OWNER", "ADMIN")

                        .requestMatchers(HttpMethod.GET, "/journal-entries", "/journal-entries/**").hasAnyRole("OWNER", "ADMIN", "CASHIER")
                        .requestMatchers(HttpMethod.POST, "/journal-entries").hasAnyRole("OWNER", "ADMIN")

                        .requestMatchers(HttpMethod.GET, "/employees", "/employees/**").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/employees").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/employees/**").hasAnyRole("OWNER", "ADMIN")

                        .requestMatchers(HttpMethod.GET, "/attendance", "/attendance/**").hasAnyRole("OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/attendance", "/attendance/**").hasAnyRole("OWNER", "ADMIN")

                        .requestMatchers(HttpMethod.GET, "/payroll/summary", "/payroll/**").hasAnyRole("OWNER", "ADMIN")

                        .requestMatchers(HttpMethod.GET, "/customers", "/customers/**").hasAnyRole("OWNER", "ADMIN", "CASHIER")
                        .requestMatchers(HttpMethod.POST, "/customers", "/customers/**").hasAnyRole("OWNER", "ADMIN", "CASHIER")

                        .requestMatchers(HttpMethod.GET, "/sales", "/sales/**").hasAnyRole("OWNER", "ADMIN", "CASHIER")
                        .requestMatchers(HttpMethod.POST, "/sales", "/sales/**").hasAnyRole("OWNER", "ADMIN", "CASHIER")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeSecurityError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "Unauthorized", "Authentication is required.", request.getRequestURI()))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeSecurityError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "Forbidden", "You do not have permission to access this resource.", request.getRequestURI()))
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeSecurityError(HttpServletResponse response,
                                    int status,
                                    String error,
                                    String message,
                                    String path) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("path", path);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
