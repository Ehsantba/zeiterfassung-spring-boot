package de.zeiterfassung.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF-Schutz: Das Token liegt in einem für JavaScript lesbaren Cookie (XSRF-TOKEN)
            // und muss bei schreibenden Anfragen als Header X-XSRF-TOKEN zurückgeschickt werden.
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterAfter(new CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/favicon.svg").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/api/login").permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().denyAll())
            .formLogin(form -> form
                    .loginProcessingUrl("/api/login")
                    .successHandler((req, res, auth) -> res.setStatus(HttpStatus.OK.value()))
                    .failureHandler((req, res, ex) -> res.setStatus(HttpStatus.UNAUTHORIZED.value())))
            .logout(logout -> logout
                    .logoutUrl("/api/logout")
                    .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .headers(h -> h
                    .contentSecurityPolicy(csp -> csp.policyDirectives(
                            "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'"))
                    .frameOptions(f -> f.deny()))
            .httpBasic(b -> b.disable());
        return http.build();
    }

    /** Sorgt dafür, dass das CSRF-Cookie bei jeder Anfrage gesetzt wird (Spring lädt das Token sonst erst bei Bedarf). */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken(); // löst das Schreiben des Cookies aus
            }
            chain.doFilter(request, response);
        }
    }
}
