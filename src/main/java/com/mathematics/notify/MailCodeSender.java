package com.mathematics.notify;

import java.time.Duration;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 邮件通道。纯文本邮件：验证码邮件不需要 HTML，HTML 反而更容易被判成钓鱼邮件。
 */
public class MailCodeSender implements CodeSender {

    private final JavaMailSender mail;
    private final String from;

    public MailCodeSender(JavaMailSender mail, String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public void send(String destination, String code, Duration ttl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(destination);
        message.setSubject("【mathematics】找回密码验证码");
        message.setText("""
                你的验证码是：%s

                %d 分钟内有效，只能使用一次。
                如果不是你本人在找回密码，请忽略这封邮件，你的密码不会被修改。
                """.formatted(code, ttl.toMinutes()));
        mail.send(message);
    }
}
