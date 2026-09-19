package com.mathematics.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public Map<String, Object> index() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", "mathematics");
        body.put("status", "ok");
        return body;
    }

    @GetMapping("/hello")
    public Map<String, Object> hello(@RequestParam(value = "name", defaultValue = "mathematics") String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Hello, " + name);
        return body;
    }
}
