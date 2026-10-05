package com.mathematics.notify;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 验证码投递。两条约束决定了它的形状：
 * <ul>
 *   <li><b>事务提交后才发。</b>先发后提交，提交失败时用户手里就是一个库里不存在的码。</li>
 *   <li><b>后台线程发。</b>同步发送的话，存在的账号要等一次 SMTP 往返、不存在的账号立即返回，
 *       响应时间差就把「账号是否存在」泄露出去了，前面刻意统一的响应体等于白做。</li>
 * </ul>
 */
public class CodeDelivery implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CodeDelivery.class);

    private final Map<Channel, CodeSender> senders = new EnumMap<>(Channel.class);
    private final Executor executor;
    private final Runnable onClose;
    private final boolean logCodes;

    public CodeDelivery(List<CodeSender> senders, Executor executor, Runnable onClose, boolean logCodes) {
        for (CodeSender sender : senders) {
            if (this.senders.putIfAbsent(sender.channel(), sender) != null) {
                throw new IllegalStateException("渠道 " + sender.channel() + " 配置了不止一个发送实现");
            }
        }
        this.executor = executor;
        this.onClose = onClose;
        this.logCodes = logCodes;
    }

    @Override
    public void close() {
        onClose.run();
    }

    public boolean supports(Channel channel) {
        return senders.containsKey(channel);
    }

    public void deliverAfterCommit(long userId, Channel channel, String destination, String code, Duration ttl) {
        Runnable task = () -> deliver(userId, channel, destination, code, ttl);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(task);
                }
            });
        } else {
            executor.execute(task);
        }
    }

    private void deliver(long userId, Channel channel, String destination, String code, Duration ttl) {
        if (logCodes) {
            log.info("[开发环境] 用户 {} 的找回密码验证码：{}（{} 分钟内有效）", userId, code, ttl.toMinutes());
        }
        CodeSender sender = senders.get(channel);
        if (sender == null) {
            if (!logCodes) {
                log.warn("用户 {} 申请了 {} 验证码，但这个渠道没有配置发送通道，验证码无法送达", userId, channel);
            }
            return;
        }
        try {
            sender.send(destination, code, ttl);
        } catch (RuntimeException ex) {
            log.error("用户 {} 的 {} 验证码发送失败", userId, channel, ex);
        }
    }
}
