package com.cowork.config;

import com.cowork.auth.AuthCookieService;
import com.cowork.auth.CookieOriginFilter;
import com.cowork.auth.JwtAuthenticationFilter;
import com.cowork.auth.JwtUtil;
import com.cowork.user.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final AuthCookieService authCookieService;
    private final CookieOriginFilter cookieOriginFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Public endpoints
                .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout", "/api/auth/sso/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/surveys/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/surveys/*/respond").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/timetables/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/timetables/*/respond").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/mobile/sessions/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/mobile/sessions/*/ocr").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/mobile/sessions/*/upload").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/mobile/sessions/*/expense").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(cookieOriginFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(jwtAuthenticationFilter(), CookieOriginFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil, userDetailsService, authCookieService);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
