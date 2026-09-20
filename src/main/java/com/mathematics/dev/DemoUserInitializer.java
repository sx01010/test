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
 * 开发环境建两个能直接登录的账号，省掉每次重启都要注册。
 * mysql profile 里 mathematics.dev.seed-demo-user=false，不会生效。
 *
 * <p>种子数据里的 content@mathematics.local 虽然是 ADMIN，但 password_hash 是故意写坏的，
 * 登不进来，所以管理端需要这里单独建一个。生产环境的第一个管理员靠手工提权，不由代码凭空造。
 */
@Configuration
@ConditionalOnProperty(name = "mathematics.dev.seed-demo-user", havingValue = "true")
public class DemoUserInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoUserInitializer.class);
    private static final String USER_EMAIL = "demo@mathematics.local";
    private static final String USER_PASSWORD = "demo12345";
    private static final String ADMIN_EMAIL = "admin@mathematics.local";
    private static final String ADMIN_PASSWORD = "admin12345";

    @Bean
    public ApplicationRunner seedDemoUser(UserRepository users, PasswordEncoder passwordEncoder) {
        return args -> {
            seed(users, passwordEncoder, "小满", USER_EMAIL, USER_PASSWORD, "USER");
            seed(users, passwordEncoder, "内容组值班", ADMIN_EMAIL, ADMIN_PASSWORD, "ADMIN");
        };
    }

    private static void seed(UserRepository users, PasswordEncoder passwordEncoder, String nickname,
                             String email, String password, String role) {
        if (users.existsByEmail(email)) {
            return;
        }
        users.insert(nickname, email, null, passwordEncoder.encode(password), role);
        log.info("已创建{}账号 {} / {}", "ADMIN".equals(role) ? "管理" : "演示", email, password);
    }
}
