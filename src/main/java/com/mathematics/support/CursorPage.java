package com.mathematics.support;

import java.util.List;

/**
 * 游标分页。V1 所有列表都按 id 倒序翻页，cursor 就是上一页最后一条的 id。
 */
public record CursorPage<T>(List<T> items, String nextCursor) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    public static int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    public static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor.trim());
        } catch (NumberFormatException ex) {
            throw ApiException.invalid("cursor 不合法");
        }
    }

    public <R> CursorPage<R> map(java.util.function.Function<T, R> mapper) {
        return new CursorPage<>(items.stream().map(mapper).toList(), nextCursor);
    }

    /**
     * 多查一条判断有没有下一页，避免额外的 count 查询。
     */
    public static <T> CursorPage<T> of(List<T> rows, int limit, java.util.function.Function<T, Long> idOf) {
        if (rows.size() <= limit) {
            return new CursorPage<>(rows, null);
        }
        List<T> page = rows.subList(0, limit);
        return new CursorPage<>(List.copyOf(page), String.valueOf(idOf.apply(page.get(page.size() - 1))));
    }
}
