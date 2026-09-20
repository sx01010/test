package com.mathematics.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mathematics.judge.GraderRegistry;

@Configuration
public class JudgeConfig {

    @Bean
    public GraderRegistry graderRegistry() {
        return GraderRegistry.defaults();
    }
}
