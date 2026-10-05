package com.mathematics.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CodeDeliveryTest {

    private record Sent(String phone, String template, Map<String, String> params) {
    }

    /** 接短信只要实现 SmsGateway：这里用一个记录调用的假网关验证这条扩展路径。 */
    @Test
    void smsGatewayReceivesTemplateAndVariables() {
        List<Sent> sent = new ArrayList<>();
        SmsGateway gateway = (phone, template, params) -> sent.add(new Sent(phone, template, params));
        CodeDelivery delivery = new CodeDelivery(List.of(new SmsCodeSender(gateway, "SMS_123")),
                Runnable::run, () -> { }, false);

        assertTrue(delivery.supports(Channel.PHONE));
        assertFalse(delivery.supports(Channel.EMAIL));
        delivery.deliverAfterCommit(7, Channel.PHONE, "13800000000", "042137", Duration.ofMinutes(10));

        assertEquals(List.of(new Sent("13800000000", "SMS_123", Map.of("code", "042137", "minutes", "10"))), sent);
    }

    @Test
    void senderFailureIsSwallowedSoTheCallerCannotTellAccountsApart() {
        CodeSender broken = new CodeSender() {
            @Override
            public Channel channel() {
                return Channel.EMAIL;
            }

            @Override
            public void send(String destination, String code, Duration ttl) {
                throw new IllegalStateException("smtp down");
            }
        };
        CodeDelivery delivery = new CodeDelivery(List.of(broken), Runnable::run, () -> { }, false);
        delivery.deliverAfterCommit(1, Channel.EMAIL, "a@b.c", "000000", Duration.ofMinutes(10));
    }

    @Test
    void twoSendersForOneChannelIsAConfigurationError() {
        SmsGateway gateway = (phone, template, params) -> { };
        assertThrows(IllegalStateException.class, () -> new CodeDelivery(
                List.of(new SmsCodeSender(gateway, "a"), new SmsCodeSender(gateway, "b")),
                Runnable::run, () -> { }, false));
    }
}
