package ru.mecotrade.kidtracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import ru.mecotrade.kidtracker.security.UserDeviceFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService users,
            @Value("${remember.me.token.validity.seconds}") int validity) throws Exception {
        http.csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                        .ignoringRequestMatchers(new AntPathRequestMatcher("/device/**")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/csrf", "/login", "/error").permitAll()
                        .requestMatchers("/h2-console/**").denyAll()
                        .requestMatchers("/api/admin/**").hasAuthority("ADMIN")
                        .anyRequest().hasAnyAuthority("USER", "ADMIN"))
                .exceptionHandling(errors -> errors
                        .defaultAuthenticationEntryPointFor((req, res, ex) -> res.sendError(401),
                                new AntPathRequestMatcher("/api/**")))
                .addFilterBefore(new ru.mecotrade.kidtracker.security.LoginAttemptFilter(),
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new UserDeviceFilter(), AuthorizationFilter.class)
                .formLogin(form -> form.defaultSuccessUrl("/", true).permitAll())
                .logout(logout -> logout.permitAll())
                .rememberMe(remember -> remember.userDetailsService(users).tokenValiditySeconds(validity));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public AuthenticationProvider daoAuthenticationProvider(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return provider;
    }
}
