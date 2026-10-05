package com.mathematics.practice;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.mathematics.identity.CurrentUser;

/**
 * 每日一题与连续练习天数。
 *
 * <p>连续天数按「当天交过任意一道题」算，不要求必须做每日一题：给小学生的激励宜宽不宜严，
 * 断签的挫败感比坚持的成就感更容易让人流失。
 */
@Service
public class DailyService {

    /** 30 天内出过的题不再当每日一题，题库不够 30 道时才允许重复。 */
    private static final int NO_REPEAT_DAYS = 30;
    private static final int STREAK_LOOKBACK_DAYS = 366;
    private static final String PUBLISHED = "status = 'PUBLISHED' AND deleted_at IS NULL";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DailyService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record DailyProblem(long id, String title, String type, int difficulty, String grade) {
    }

    /** 匿名访问时只有 date 与 problem，其余字段为 null 不输出。 */
    public record Daily(LocalDate date, DailyProblem problem, Integer streak, Boolean practicedToday,
                        Boolean attempted, Boolean solved) {
    }

    public Daily today(CurrentUser me) {
        LocalDate today = LocalDate.now(clock);
        Optional<DailyProblem> problem = pick(today).flatMap(this::load);
        if (!me.loggedIn()) {
            return new Daily(today, problem.orElse(null), null, null, null, null);
        }
        long userId = me.id();
        List<LocalDate> days = practiceDays(userId, today);
        boolean practicedToday = !days.isEmpty() && days.get(0).equals(today);
        Boolean attempted = problem.map(p -> dailyResultCount(userId, p.id(), today, false) > 0).orElse(null);
        Boolean solved = problem.map(p -> dailyResultCount(userId, p.id(), today, true) > 0).orElse(null);
        return new Daily(today, problem.orElse(null), streak(days, today), practicedToday, attempted, solved);
    }

    /**
     * 多实例同时首次请求时，每个实例按同一个确定性规则选题，再抢主键插入；
     * 抢输的读回赢家写的那行。选出来的题后来被下架，就当天重新选。
     */
    Optional<Long> pick(LocalDate day) {
        Optional<Long> stored = stored(day);
        if (stored.isPresent() && isPublished(stored.get())) {
            return stored;
        }
        Optional<Long> chosen = choose(day);
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        if (stored.isPresent()) {
            jdbc.update("UPDATE daily_problem SET problem_id = ? WHERE for_date = ?", chosen.get(), Date.valueOf(day));
            return chosen;
        }
        try {
            jdbc.update("INSERT INTO daily_problem (for_date, problem_id) VALUES (?, ?)", Date.valueOf(day), chosen.get());
            return chosen;
        } catch (DuplicateKeyException lostRace) {
            return stored(day);
        }
    }

    private Optional<Long> choose(LocalDate day) {
        List<Long> fresh = jdbc.queryForList("SELECT id FROM problem WHERE " + PUBLISHED + """
                 AND id NOT IN (SELECT problem_id FROM daily_problem WHERE for_date >= ? AND for_date < ?)
                ORDER BY id
                """, Long.class, Date.valueOf(day.minusDays(NO_REPEAT_DAYS)), Date.valueOf(day));
        List<Long> candidates = fresh.isEmpty()
                ? jdbc.queryForList("SELECT id FROM problem WHERE " + PUBLISHED + " ORDER BY id", Long.class)
                : fresh;
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        // 乘一个大奇数打散，相邻两天不至于总是选到 id 相邻的题
        int index = (int) Math.floorMod(day.toEpochDay() * 2_654_435_761L, (long) candidates.size());
        return Optional.of(candidates.get(index));
    }

    private Optional<Long> stored(LocalDate day) {
        return jdbc.queryForList("SELECT problem_id FROM daily_problem WHERE for_date = ?", Long.class, Date.valueOf(day))
                .stream().findFirst();
    }

    private boolean isPublished(long problemId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM problem WHERE id = ? AND " + PUBLISHED, Integer.class, problemId);
        return count != null && count > 0;
    }

    private Optional<DailyProblem> load(long problemId) {
        return jdbc.query("""
                SELECT id, title, problem_type, difficulty, grade FROM problem WHERE id = ?
                """, (rs, i) -> new DailyProblem(rs.getLong("id"), rs.getString("title"),
                rs.getString("problem_type"), rs.getInt("difficulty"), rs.getString("grade")), problemId)
                .stream().findFirst();
    }

    /** 最近一年里有作答的日期，新到旧。 */
    private List<LocalDate> practiceDays(long userId, LocalDate today) {
        return jdbc.query("""
                SELECT DISTINCT CAST(created_at AS DATE) AS d
                  FROM submission
                 WHERE user_id = ? AND regraded_from IS NULL AND created_at >= ?
                 ORDER BY d DESC
                """, (rs, i) -> rs.getDate("d").toLocalDate(),
                userId, Date.valueOf(today.minusDays(STREAK_LOOKBACK_DAYS)));
    }

    /** 今天还没做不算断：从昨天往回数，给用户留一整天补上。 */
    static int streak(List<LocalDate> daysNewestFirst, LocalDate today) {
        if (daysNewestFirst.isEmpty()) {
            return 0;
        }
        LocalDate expected = daysNewestFirst.get(0).equals(today) ? today : today.minusDays(1);
        int streak = 0;
        for (LocalDate day : daysNewestFirst) {
            if (!day.equals(expected)) {
                break;
            }
            streak++;
            expected = expected.minusDays(1);
        }
        return streak;
    }

    private int dailyResultCount(long userId, long problemId, LocalDate today, boolean correctOnly) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM submission
                 WHERE user_id = ? AND problem_id = ? AND created_at >= ? AND created_at < ?
                   AND regraded_from IS NULL
                """ + (correctOnly ? " AND result = 'CORRECT'" : ""), Integer.class,
                userId, problemId, Date.valueOf(today), Date.valueOf(today.plusDays(1)));
        return count == null ? 0 : count;
    }
}
