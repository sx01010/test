package com.mathematics.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.mathematics.identity.UserRepository;

/**
 * 开发环境建一个能直接登录的演示账号，省掉每次重启都要注册。
 * mysql profile 里 mathematics.dev.seed-demo-user=false，不会生效。
 */
@Configuration
@ConditionalOnProperty(name = "mathematics.dev.seed-demo-user", havingValue = "true")
public class DemoUserInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoUserInitializer.class);
    private static final String EMAIL = "demo@mathematics.local";
    private static final String PASSWORD = "demo12345";

    @Bean
    public ApplicationRunner seedDemoUser(UserRepository users, PasswordEncoder passwordEncoder) {
        return args -> {
            if (users.existsByEmail(EMAIL)) {
                return;
            }
            users.insert("小满", EMAIL, null, passwordEncoder.encode(PASSWORD), "USER");
            log.info("已创建演示账号 {} / {}", EMAIL, PASSWORD);
        };
    }
}
