package com.mathematics.material;

public final class MaterialRows {

    private MaterialRows() {
    }

    public record Summary(
            long id,
            String title,
            String materialType,
            String grade,
            Integer year,
            String originType,
            String originalName,
            long sizeBytes) {
    }

    /**
     * 下载要用的那几个字段。{@code status} 和 {@code deletedAt} 一起取回来，
     * 是为了每次下载都能回查「还能不能给」——下架后已经发出去的链接要立刻失效。
     */
    public record Downloadable(
            long id,
            String objectKey,
            String originalName,
            String status,
            boolean fileDeleted) {

        public boolean downloadable() {
            return "PUBLISHED".equals(status) && !fileDeleted;
        }
    }
}
