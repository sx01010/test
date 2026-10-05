package com.mathematics.guard;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

final class Requests {

    private Requests() {
    }

    /** 切面只织入控制器，正常调用链上一定有当前请求；拿不到说明被误用在了非 Web 线程。 */
    static HttpServletRequest current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        throw new IllegalStateException("guard annotations only apply to web request handlers");
    }
}
