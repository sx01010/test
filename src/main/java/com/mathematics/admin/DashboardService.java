package com.mathematics.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 运营看板。「活跃」定义为当天至少交过一次作答；纠错重判生成的行不是用户行为，不算。
 *
 * <p>SQL 只做取数（按日去重的用户-日期对），留存在 Java 里算：两种方言的日期函数各不相同，
 * MVP 阶段的数据量也撑得住。日活过万以后应改成每日离线汇总表。
 */
@Service
public class DashboardService {

    public static final int MAX_DAYS = 60;
    private static final int RETENTION_LAG = 7;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DashboardService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Day(LocalDate date, int activeUsers, int newUsers, int submissions,
                      BigDecimal correctRate, BigDecimal submissionsPerActiveUser) {
    }

    /** d1 / d7 为 null 表示还没到观察日，不是 0%。 */
    public record Cohort(LocalDate date, int size, BigDecimal d1, BigDecimal d7) {
    }

    public record Summary(int totalUsers, int activeToday, int activeLast7Days, int submissionsToday) {
    }

    public record Dashboard(LocalDate from, LocalDate to, Summary summary, List<Day> days, List<Cohort> cohorts) {
    }

    @Transactional(readOnly = true)
    public Dashboard load(int days) {
        int span = Math.max(1, Math.min(days, MAX_DAYS));
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(span - 1L);

        Map<LocalDate, Set<Long>> activeByDay = activeUsersByDay(from.minusDays(RETENTION_LAG), today);
        Map<LocalDate, List<Long>> signupsByDay = signupsByDay(from, today);
        Map<LocalDate, int[]> volumeByDay = submissionVolumeByDay(from, today);

        List<Day> dayRows = new ArrayList<>();
        List<Cohort> cohorts = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
            int active = activeByDay.getOrDefault(date, Set.of()).size();
            int[] volume = volumeByDay.getOrDefault(date, new int[2]);
            List<Long> signups = signupsByDay.getOrDefault(date, List.of());
            dayRows.add(new Day(date, active, signups.size(), volume[0],
                    ratio(volume[1], volume[0]), ratio(volume[0], active)));
            cohorts.add(new Cohort(date, signups.size(),
                    retention(signups, activeByDay, date.plusDays(1), today),
                    retention(signups, activeByDay, date.plusDays(7), today)));
        }

        Set<Long> lastWeek = new HashSet<>();
        for (LocalDate date = today.minusDays(6); !date.isAfter(today); date = date.plusDays(1)) {
            lastWeek.addAll(activeByDay.getOrDefault(date, Set.of()));
        }
        Integer totalUsers = jdbc.queryForObject(
                "SELECT COUNT(*) FROM `user` WHERE status <> 'DELETED'", Integer.class);
        Summary summary = new Summary(totalUsers == null ? 0 : totalUsers,
                activeByDay.getOrDefault(today, Set.of()).size(), lastWeek.size(),
                volumeByDay.getOrDefault(today, new int[2])[0]);
        return new Dashboard(from, today, summary, dayRows, cohorts);
    }

    private Map<LocalDate, Set<Long>> activeUsersByDay(LocalDate from, LocalDate to) {
        Map<LocalDate, Set<Long>> result = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT user_id, CAST(created_at AS DATE) AS d
                  FROM submission
                 WHERE created_at >= ? AND created_at < ? AND regraded_from IS NULL
                """, rs -> {
            result.computeIfAbsent(rs.getDate("d").toLocalDate(), key -> new HashSet<>()).add(rs.getLong("user_id"));
        }, Date.valueOf(from), Date.valueOf(to.plusDays(1)));
        return result;
    }

    private Map<LocalDate, List<Long>> signupsByDay(LocalDate from, LocalDate to) {
        Map<LocalDate, List<Long>> result = new HashMap<>();
        jdbc.query("""
                SELECT id, CAST(created_at AS DATE) AS d
                  FROM `user`
                 WHERE created_at >= ? AND created_at < ?
                """, rs -> {
            result.computeIfAbsent(rs.getDate("d").toLocalDate(), key -> new ArrayList<>()).add(rs.getLong("id"));
        }, Date.valueOf(from), Date.valueOf(to.plusDays(1)));
        return result;
    }

    /** 值为 {作答数, 答对数}。 */
    private Map<LocalDate, int[]> submissionVolumeByDay(LocalDate from, LocalDate to) {
        Map<LocalDate, int[]> result = new HashMap<>();
        jdbc.query("""
                SELECT CAST(created_at AS DATE) AS d, COUNT(*) AS total,
                       SUM(CASE WHEN result = 'CORRECT' THEN 1 ELSE 0 END) AS correct
                  FROM submission
                 WHERE created_at >= ? AND created_at < ? AND regraded_from IS NULL
                 GROUP BY CAST(created_at AS DATE)
                """, rs -> {
            result.put(rs.getDate("d").toLocalDate(), new int[] {rs.getInt("total"), rs.getInt("correct")});
        }, Date.valueOf(from), Date.valueOf(to.plusDays(1)));
        return result;
    }

    private static BigDecimal retention(List<Long> cohort, Map<LocalDate, Set<Long>> activeByDay,
                                        LocalDate observedOn, LocalDate today) {
        if (cohort.isEmpty() || observedOn.isAfter(today)) {
            return null;
        }
        Set<Long> active = activeByDay.getOrDefault(observedOn, Set.of());
        long retained = cohort.stream().filter(active::contains).count();
        return ratio(retained, cohort.size());
    }

    private static BigDecimal ratio(long part, long whole) {
        if (whole == 0) {
            return null;
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(whole), 4, RoundingMode.HALF_UP);
    }
}
