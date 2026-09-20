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

    private static String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
