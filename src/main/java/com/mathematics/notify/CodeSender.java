package com.mathematics.notify;

import java.time.Duration;

/**
 * 一个发送通道。每个 {@link Channel} 最多一个生效的实现，由 {@link CodeDelivery} 按渠道挑选。
 *
 * <p>实现可以阻塞（SMTP 往返、短信网关 HTTP 调用），调用方保证它跑在后台线程、事务提交之后。
 * 抛异常只会被记日志，不会传回给申请验证码的人。
 */
public interface CodeSender {

    Channel channel();

    void send(String destination, String code, Duration ttl);
}
