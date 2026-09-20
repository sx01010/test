package com.mathematics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * V1 只需要 BCrypt 与不透明令牌，没有引入完整的 Spring Security 过滤器链：
 * 鉴权在 CurrentUserArgumentResolver 里按接口显式要求，避免为 6 个匿名接口写一堆放行规则。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
