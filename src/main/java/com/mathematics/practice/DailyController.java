package com.mathematics.practice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.identity.CurrentUser;

/** 匿名可看今天的题；登录后附带连续天数和今天这道做没做。 */
@RestController
@RequestMapping("/api/v1/daily")
public class DailyController {

    private final DailyService daily;

    public DailyController(DailyService daily) {
        this.daily = daily;
    }

    @GetMapping
    public DailyService.Daily today(CurrentUser me) {
        return daily.today(me);
    }
}
