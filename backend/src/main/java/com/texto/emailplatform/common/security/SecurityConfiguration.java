package com.texto.emailplatform.common.security;

import com.texto.emailplatform.auth.security.ApiKeyAuthenticationFilter;
import com.texto.emailplatform.auth.security.DashboardSessionAuthenticationFilter;
import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.ops.OpsAuditFilter;
import com.texto.emailplatform.ops.security.OpsAdminAccessFilter;
import com.texto.emailplatform.ops.security.PlatformAdminAuthenticationFilter;
import com.texto.emailplatform.tenant.web.TenantContextFilter;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    private final EmailPlatformProperties properties;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final PlatformAdminAuthenticationFilter platformAdminAuthenticationFilter;
    private final DashboardSessionAuthenticationFilter dashboardSessionAuthenticationFilter;
    private final TenantContextFilter tenantContextFilter;
    private final OpsAdminAccessFilter opsAdminAccessFilter;
    private final OpsAuditFilter opsAuditFilter;

    public SecurityConfiguration(
            EmailPlatformProperties properties,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
            PlatformAdminAuthenticationFilter platformAdminAuthenticationFilter,
            DashboardSessionAuthenticationFilter dashboardSessionAuthenticationFilter,
            TenantContextFilter tenantContextFilter,
            OpsAdminAccessFilter opsAdminAccessFilter,
            OpsAuditFilter opsAuditFilter
    ) {
        this.properties = properties;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
        this.platformAdminAuthenticationFilter = platformAdminAuthenticationFilter;
        this.dashboardSessionAuthenticationFilter = dashboardSessionAuthenticationFilter;
        this.tenantContextFilter = tenantContextFilter;
        this.opsAdminAccessFilter = opsAdminAccessFilter;
        this.opsAuditFilter = opsAuditFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                            .requestMatchers("/api/v1/status").permitAll()
                            .requestMatchers("/api/v1/plans", "/api/v1/plans/**").permitAll()
                            .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/login").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/v1/billing/config").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/v1/billing/webhooks/razorpay").permitAll();
                    if (properties.getSecurity().isApiDocsPublic()) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    } else {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").denyAll();
                    }
                    auth.requestMatchers("/api/v1/**").authenticated()
                            .anyRequest().denyAll();
                })
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(platformAdminAuthenticationFilter, ApiKeyAuthenticationFilter.class)
                .addFilterAfter(dashboardSessionAuthenticationFilter, PlatformAdminAuthenticationFilter.class)
                .addFilterAfter(tenantContextFilter, DashboardSessionAuthenticationFilter.class)
                .addFilterAfter(opsAdminAccessFilter, TenantContextFilter.class)
                .addFilterAfter(opsAuditFilter, OpsAdminAccessFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    FilterRegistrationBean<PlatformAdminAuthenticationFilter> platformAdminAuthenticationFilterRegistration(
            PlatformAdminAuthenticationFilter filter
    ) {
        return disabled(filter);
    }

    @Bean
    FilterRegistrationBean<OpsAdminAccessFilter> opsAdminAccessFilterRegistration(OpsAdminAccessFilter filter) {
        return disabled(filter);
    }

    @Bean
    FilterRegistrationBean<OpsAuditFilter> opsAuditFilterRegistration(OpsAuditFilter filter) {
        return disabled(filter);
    }

    private static <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disabled(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(properties.getCors().getAllowedOrigins().split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Request-Id",
                "X-Api-Key",
                "Idempotency-Key"
        ));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
