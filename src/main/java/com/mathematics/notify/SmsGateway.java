package com.mathematics.notify;

import java.util.Map;

/**
 * 短信服务商的接入点。V1 没有接任何服务商；接入时只需要提供这个接口的一个 Spring Bean，
 * 并打开 {@code mathematics.notification.sms.enabled}，验证码就会经由 {@link SmsCodeSender} 发出。
 *
 * <p>国内短信平台（阿里云、腾讯云）都是「模板 + 变量」模式，不允许发自由文本，
 * 所以接口按模板设计。阿里云的实现大致是：
 *
 * <pre>{@code
 * @Component
 * class AliyunSmsGateway implements SmsGateway {
 *     public void sendTemplate(String phone, String templateCode, Map<String, String> params) {
 *         client.sendSms(new SendSmsRequest()
 *                 .setPhoneNumbers(phone).setSignName(signName)
 *                 .setTemplateCode(templateCode).setTemplateParam(toJson(params)));
 *     }
 * }
 * }</pre>
 */
public interface SmsGateway {

    void sendTemplate(String phone, String templateCode, Map<String, String> params);
}
