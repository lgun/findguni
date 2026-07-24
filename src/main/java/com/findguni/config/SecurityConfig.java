package com.findguni.config;

import com.findguni.model.Role;
import com.findguni.model.UserAccount;
import com.findguni.repository.UserAccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    private final String rememberMeKey;

    public SecurityConfig(@Value("${findguni.security.remember-me-key}") String rememberMeKey) {
        this.rememberMeKey = rememberMeKey;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService userDetailsService(UserAccountRepository accounts) {
        return username -> {
            UserAccount account = accounts.findByEmailIgnoreCase(username.trim())
                    .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("계정을 찾을 수 없습니다."));
            return User.withUsername(account.getEmail())
                    .password(account.getPasswordHash())
                    .roles(account.getRole().name())
                    .disabled(!account.isActive())
                    .build();
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/health", "/games", "/signup", "/login", "/play/**", "/error/**",
                        "/css/**", "/js/**", "/images/**", "/uploads/**", "/webjars/**", "/favicon.ico")
                    .permitAll()
                .requestMatchers("/platform/**").hasRole(Role.ADMIN.name())
                .requestMatchers("/maker/**").hasRole(Role.MAKER.name())
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .successHandler((request, response, authentication) -> {
                    boolean admin = authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                    response.sendRedirect(admin ? "/platform" : "/maker");
                })
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/").permitAll())
            .rememberMe(remember -> remember.rememberMeParameter("rememberMe")
                    .tokenValiditySeconds(60 * 60 * 24 * 30).key(rememberMeKey))
            .sessionManagement(session -> session.sessionFixation().migrateSession());
        return http.build();
    }
}
