package com.mathematics.notify;

import java.time.Duration;
import java.util.Map;

/**
 * 短信通道。模板变量名 {@code code}、{@code minutes} 要和服务商后台审核通过的模板一致。
 */
public class SmsCodeSender implements CodeSender {

    private final SmsGateway gateway;
    private final String templateCode;

    public SmsCodeSender(SmsGateway gateway, String templateCode) {
        this.gateway = gateway;
        this.templateCode = templateCode;
    }

    @Override
    public Channel channel() {
        return Channel.PHONE;
    }

    @Override
    public void send(String destination, String code, Duration ttl) {
        gateway.sendTemplate(destination, templateCode,
                Map.of("code", code, "minutes", String.valueOf(ttl.toMinutes())));
    }
}
