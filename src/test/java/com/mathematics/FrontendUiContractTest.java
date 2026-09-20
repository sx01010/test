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

    @Test
    void partialResultUsesPaperBackgroundWithThemeColorStrip() throws IOException {
        String css = resource("static/app.css");

        assertThat(css).contains(
                ".result.wait {",
                "background: var(--paper)",
                ".result.wait::before",
                "background: var(--second)");
        assertThat(css).doesNotContain(
                ".result.wait { border-color: var(--second); background: var(--second-soft) }");
    }

    @Test
    void wrongAnswerMarksOnlyTheStudentsOwnChoice() throws IOException {
        String js = resource("static/app.js");

        // 判分后不再去取标准答案。答错时把正确选项亮出来等于替学生填答案，
        // 「重新作答」就只剩照抄。函数保持同步，没有网络调用，答案无从泄露。
        assertThat(js).contains("function markObjectiveOptions(result) {");
        assertThat(js).doesNotContain("async function markObjectiveOptions");
        assertThat(js).doesNotContain("answer = (await api(`/problems/${problem.id}/explanation`)).answerJson");

        // 只标学生自己勾的那一项，对错由判分结果决定
        assertThat(js).contains(
                "const mark = result.result === 'CORRECT' ? 'right' : 'wrong'",
                "$$('#answerZone .opt.on').forEach(option => option.classList.add(mark))");

        // 标准答案只有「查看解析」这一条出口，整份脚本里解析接口只被请求一次
        assertThat(js).containsOnlyOnce("/explanation");
        assertThat(js).contains("function revealStandardOptions", "revealStandardOptions(payload.answerJson)");
    }

    @Test
    void adminEntryIsRoleGatedAndFormFieldsAreLabelled() throws IOException {
        String html = resource("static/index.html");
        String js = resource("static/app.js");

        // 管理入口默认隐藏，只有 role=ADMIN 才显示
        assertThat(html).contains("<a data-view=\"admin\" id=\"navAdmin\" hidden>管理</a>");
        assertThat(js).contains("S.me?.role === 'ADMIN'", "$('#navAdmin').hidden = !isAdmin");

        // 每个控件都有 label for 关联，不拿 placeholder 当标签
        assertThat(html).contains(
                "<label for=\"apTitle\">标题</label>",
                "<label for=\"apType\">题型</label>",
                "<label for=\"apDifficulty\">难度</label>",
                "<label for=\"apGrade\">适用年级</label>",
                "<label for=\"apTags\">知识点</label>",
                "<label for=\"apStem\">题干</label>",
                "<label for=\"apExplanation\">解析</label>",
                "<label for=\"apOrigin\">来源类型</label>",
                "<label for=\"apChangeNote\">变更说明</label>");

        // 存草稿与发布是两个独立动作，发布按钮不靠禁用来表达「来源没填齐」
        assertThat(html).contains("id=\"apSaveDraft\">保存草稿", "id=\"apPublish\">发布");
        assertThat(js).contains("saveAdminProblem(false)", "saveAdminProblem(true)");
    }

    @Test
    void failedAdminSubmitFocusesLinkedErrorSummary() throws IOException {
        String html = resource("static/index.html");
        String css = resource("static/app.css");
        String js = resource("static/app.js");

        // 错误摘要要能被读屏播报，也要能接收焦点
        assertThat(html).contains("id=\"adminError\" role=\"alert\" tabindex=\"-1\" hidden");
        assertThat(js).contains("function showAdminError", "box.focus()");
        assertThat(css).contains(".form-error");
    }

    @Test
    void adminAnswerControlsFollowProblemTypeWithAccessibleNames() throws IOException {
        String js = resource("static/app.js");

        // 题型切换后重建答案区：选择题给键位勾选，判断题给是/否，多空题按空分行
        assertThat(js).contains(
                "$('#apType').onchange",
                "renderAdminAnswerArea",
                "aria-label=\"把选项 ${key} 设为正确答案\"",
                "aria-label=\"选项 ${key} 的内容\"",
                "name=\"apJudge\"",
                "data-blank-input");
        // 来源类型驱动发布前必填项，和后端闸门一一对应
        assertThat(js).contains("SOURCE_EXTRA", "rewriteNote", "licenseRef", "sourceUrl");
    }

    private static String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
