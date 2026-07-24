package com.example.sillyspringboot.config;

import com.example.sillyspringboot.auth.token.AppTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            AppTokenService tokenService
    ) throws Exception {
        http
                .cors(c -> c.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new AppTokenAuthenticationFilter(tokenService), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/error",
                                "/favicon.ico",
                                "/uploads/**",
                                "/login",
                                "/captchaImage",
                                "/admin/**",
                                "/system/**",
                                "/getInfo",
                                "/getRouters",
                                "/logout",
                                "/unlockscreen",
                                "/noise/dashboard/state"
                        ).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/index/**", "/api/common/**", "/api/user/**").permitAll()
                        .requestMatchers(
                                "/api/v1/app/**",
                                "/api/v1/characters/**",
                                "/api/v1/checkin/**",
                                "/api/v1/image/**",
                                "/api/v1/st-assets/**",
                                "/api/v1/store/**",
                                "/api/v1/support/**",
                                "/api/v1/tavern/**"
                        ).permitAll()
                        .requestMatchers("/api/internal/st-frontend-bridge/**").permitAll()
                        .requestMatchers("/api/dev/st-debug/**").permitAll()
                        .requestMatchers("/api/telegram/stars/webhook").permitAll()
                        .requestMatchers("/api/payment/epay/notify", "/api/payment/epay/return").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/app/ping").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/app/auth/telegram/login",
                                "/api/app/auth/h5/login",
                                "/api/app/auth/h5/register",
                                "/api/app/auth/h5/password-reset/request",
                                "/api/app/auth/h5/password-reset/confirm"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/app/discover/**",
                                "/api/app/illustrations/works",
                                "/api/app/illustrations/works/*",
                                "/api/app/illustrations/access-key/validate",
                                "/api/app/illustrations/notices"
                        ).permitAll()
                        .requestMatchers(new RegexRequestMatcher("^/api/app/characters/\\d+(?:\\?.*)?$", HttpMethod.GET.name())).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/app/illustrations/works/submissions").permitAll()
                        .requestMatchers("/api/app/**").authenticated()
                        .anyRequest().denyAll());
        return http.build();
    }
}
