package com.dev.ResQNet;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http){

        http

            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/station/**").hasRole("STATION_MANAGER")
                .anyRequest().permitAll()
            )

            .httpBasic(Customizer.withDefaults());

//             .formLogin(form -> form
//                 .loginPage("/login.html")
//                 .loginProcessingUrl("/user/login") // spring security intercepts it
//                 .successHandler(customLogin)
//                 .failureUrl("/login.html?error=true")
//                 .permitAll()
//         )

//             .logout(logout -> logout
//                 .logoutUrl("/logout")
//                 .logoutSuccessHandler(customLogout)
//             );

            return http.build();

    }
}
