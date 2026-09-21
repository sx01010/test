package com.mathematics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mathematics.identity.AuthService;
import com.mathematics.identity.CurrentUser;
import com.mathematics.identity.IdentityDtos.RegisterRequest;

/**
 * 登录态要活过重启。这是默认 profile 从内存 H2 换成落盘 H2 的原因，所以用测试钉住，
 * 免得哪天有人图省事把地址改回 jdbc:h2:mem 而没人发现。
 *
 * <p>做法是在同一个 JVM 里先后起两个 Spring 上下文，指向同一个库文件：第一个里注册拿令牌，
 * 关掉上下文（连接池关闭、库落盘），第二个上下文再用那个旧令牌换用户。令牌本身存在
 * auth_token 表里，所以这同时也验证了「库没了登录态就没了」的因果关系。
 *
 * <p>刻意不带 DB_CLOSE_DELAY=-1：带上它库会留在内存里直到 JVM 退出，那就测不出落盘了。
 */
class LoginSurvivesRestartTest {

    @TempDir
    Path dbDir;

    @Test
    void accessTokenStillResolvesAfterRestart() throws IOException {
        // Windows 上 Path.toString() 带反斜杠，H2 会把 \t、\n 之类当成转义，库文件就落到别处去了。
        // 一律换成正斜杠的绝对路径，两边都能认。
        String dbFile = dbDir.resolve("mathematics").toAbsolutePath().toString().replace('\\', '/');
        String url = "jdbc:h2:file:" + dbFile
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER,YEAR,VALUE";
        String email = "restart-" + UUID.randomUUID() + "@example.com";
        String token;
        long userId;

        try (ConfigurableApplicationContext first = boot(url)) {
            var issued = first.getBean(AuthService.class)
                    .register(new RegisterRequest("重启验证", email, null, "demo12345"));
            token = issued.accessToken();
            userId = issued.userId();
            assertTrue(resolve(first, token).isPresent(), "刚发的令牌当场就该认");
        }

        // 第一个上下文已关闭，库文件此刻是唯一还留着登录态的地方
        Path dbOnDisk = Path.of(dbFile + ".mv.db");
        assertTrue(Files.exists(dbOnDisk), "库应该真的落在磁盘上，实际找的是：" + dbOnDisk);

        try (ConfigurableApplicationContext second = boot(url)) {
            assertEquals(userId, resolve(second, token).orElseThrow().requireId(),
                    "重启后旧令牌应该还能换回同一个用户，不该要求重新登录");

            // 顺带确认没有重复迁移或重复灌种子：同一个邮箱只该有一行
            Integer rows = second.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT COUNT(*) FROM `user` WHERE email = ?", Integer.class, email);
            assertEquals(1, rows, "第二次启动不该把用户或种子数据再灌一遍");
        }
    }

    private static ConfigurableApplicationContext boot(String url) {
        // properties() 设的是默认属性，优先级低于 application.yml，会被落盘地址盖掉。
        // 命令行参数优先级更高，才能真正把测试钉到临时目录。
        return new SpringApplicationBuilder(MathematicsApplication.class)
                .run("--spring.datasource.url=" + url, "--server.port=0");
    }

    private static Optional<CurrentUser> resolve(ConfigurableApplicationContext context, String token) {
        return context.getBean(AuthService.class).resolveAccessToken(token);
    }
}
