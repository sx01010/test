package com.mathematics;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MathematicsApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    /**
     * 前端单页由 Spring Boot 直接当静态资源发出去，一个进程就能跑通整套。
     * "/" 走 welcome page，是一次 forward，MockMvc 不会跟随，所以正文断言放在 /index.html 上。
     */
    @Test
    void servesSinglePageApp() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                // 静态资源的 Content-Type 不带 charset，MockMvc 会按 ISO-8859-1 解码，
                // 所以这里只断言 ASCII 标记，中文正文交给浏览器按 meta charset 渲染。
                .andExpect(content().string(containsString("app.js")));
    }

    /**
     * Flyway 的种子数据把知识点树灌进来了，题库不需要登录也能浏览。
     */
    @Test
    void tagTreeIsSeeded() throws Exception {
        mockMvc.perform(get("/api/v1/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("travel"));
    }
}
