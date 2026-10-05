package com.mathematics.config;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.mathematics.notify.CodeDelivery;
import com.mathematics.notify.CodeSender;
import com.mathematics.notify.MailCodeSender;
import com.mathematics.notify.SmsCodeSender;
import com.mathematics.notify.SmsGateway;

@Configuration
public class NotifyConfig {

    /**
     * 打开邮件通道却没配 spring.mail.host 时启动即失败：比上线后才发现所有人都收不到验证码好。
     */
    @Bean
    @ConditionalOnProperty(name = "mathematics.notification.mail.enabled", havingValue = "true")
    public CodeSender mailCodeSender(ObjectProvider<JavaMailSender> mail, AppProperties properties) {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("mathematics.notification.mail.enabled=true 但没有配置 spring.mail.host");
        }
        return new MailCodeSender(sender, properties.notification().mail().from());
    }

    @Bean
    @ConditionalOnProperty(name = "mathematics.notification.sms.enabled", havingValue = "true")
    public CodeSender smsCodeSender(ObjectProvider<SmsGateway> gateway, AppProperties properties) {
        SmsGateway sms = gateway.getIfAvailable();
        if (sms == null) {
            throw new IllegalStateException("mathematics.notification.sms.enabled=true 但没有提供 SmsGateway 实现");
        }
        return new SmsCodeSender(sms, properties.notification().sms().templateCode());
    }

    /**
     * 独立的小线程池，不注册成 Bean：注册成 Executor 会顶掉 Spring Boot 默认的 applicationTaskExecutor。
     * 邮件服务器慢的时候也不该占满公共线程池。随 CodeDelivery 关闭。
     */
    @Bean
    public CodeDelivery codeDelivery(List<CodeSender> senders, AppProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notify-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.initialize();
        return new CodeDelivery(senders, executor, executor::shutdown, properties.auth().logResetCodes());
    }
}
