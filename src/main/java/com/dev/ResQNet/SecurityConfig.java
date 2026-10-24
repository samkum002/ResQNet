package com.dev.ResQNet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired(required = false)
    private customLogin customLogin;

    @Autowired(required = false)
    private customLogout customLogout;

    @Autowired(required = false)
    private customLoginFailure customLoginFailure;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/public/**")
            )

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/disaster/*/update").hasAnyRole("ADMIN", "USER")
                .requestMatchers("/disaster/**").hasRole("USER")
                .requestMatchers("/fetch/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/station/**").hasRole("STATION_MANAGER")
                .requestMatchers("/dispatch/**").hasRole("WORKER")
                .requestMatchers("/user/**").authenticated()
                .anyRequest().permitAll()
            )

            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )

            .formLogin(form -> {
                form.loginPage("/login")
                    .loginProcessingUrl("/user/login")
                    .permitAll();
                if (customLogin != null) {
                    form.successHandler(customLogin);
                }
                if (customLoginFailure != null) {
                    form.failureHandler(customLoginFailure);
                } else {
                    form.failureUrl("/login?error=true");
                }
            })

            .logout(logout -> {
                logout.logoutUrl("/logout");
                if (customLogout != null) {
                    logout.logoutSuccessHandler(customLogout);
                }
            });

        return http.build();
    }
}
