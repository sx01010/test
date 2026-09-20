package com.mathematics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class FrontendUiContractTest {

    @Test
    void problemFiltersUseNativeSingleSelects() throws IOException {
        String html = resource("static/index.html");
        String js = resource("static/app.js");

        assertThat(html).contains(
                "<select id=\"typeFilter\"",
                "<option value=\"\">全部题型</option>",
                "<select id=\"tagFilter\"",
                "<option value=\"\">全部知识点</option>");
        assertThat(html).doesNotContain("data-type=\"SINGLE\"");
        assertThat(js).contains(
                "$('#typeFilter').onchange",
                "S.filter.types.clear()",
                "$('#tagFilter').onchange",
                "S.filter.tagId = event.target.value ? Number(event.target.value) : null");
    }

    @Test
    void difficultyUsesAccessibleButtonsAtFilterEnd() throws IOException {
        String html = resource("static/index.html");
        String css = resource("static/app.css");
        String js = resource("static/app.js");

        assertThat(html).contains(
                "class=\"difficulty-filter\"",
                "data-difficulty=\"2\"",
                "aria-label=\"筛选入门难度\"",
                "data-difficulty=\"3\"",
                "aria-label=\"筛选进阶难度\"",
                "data-difficulty=\"4\"",
                "aria-label=\"筛选挑战难度\"");
        assertThat(css).contains(".difficulty-filter", "margin-left: auto");
        assertThat(js).contains("setAttribute('aria-pressed'");
    }

    @Test
    void statusColorsFollowThemeInsteadOfFixedGreenRed() throws IOException {
        String css = resource("static/app.css");
        String js = resource("static/app.js");

        // 温暖主题：橄榄苔绿表示正确，赭红表示错误，都在大地色系内
        assertThat(css).contains(
                "--ok: #687A38", "--ok-bg: #F3F2E4", "--ok-ink: #55632C",
                "--bad: #A8503C", "--bad-bg: #F9EBE7", "--bad-ink: #8E4030");
        // 炫酷主题：松青绿表示正确，莓红表示错误，都在冷色系内
        assertThat(css).contains(
                "--ok: #2A7A6B", "--ok-bg: #E6F2EF", "--ok-ink: #1E6154",
                "--bad: #A8435B", "--bad-bg: #FAEAEE", "--bad-ink: #8C3449");
        // 不允许再出现跨色系借来的色相：暖主题里的靛蓝、冷主题里的琥珀
        assertThat(css).doesNotContain("#5F6F9A", "#9D6214");
        // 颜色不是唯一信息载体：文字结论与 ✓/✗ 都保留
        assertThat(js).contains("RESULT_NAME[result.result]", "'✓' : '✗'");
    }

    private static String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
