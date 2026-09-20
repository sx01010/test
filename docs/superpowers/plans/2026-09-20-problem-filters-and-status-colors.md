# Problem Filters and Status Colors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the problem type and tag chips with single-select dropdowns, move accessible difficulty icon buttons to the filter bar's right edge, and apply theme-specific answer status colors.

**Architecture:** Keep the existing static HTML/CSS/JavaScript SPA and backend API unchanged. Reuse `S.filter`, `searchParams()`, and `refreshList()`; only the controls that write filter state change. Add a small classpath contract test so structural and theme-token regressions are caught without adding a frontend toolchain.

**Tech Stack:** HTML5, CSS custom properties, vanilla JavaScript, inline SVG, Spring Boot MockMvc/JUnit 5.

## Global Constraints

- Use native controls and already-installed dependencies only.
- Type and knowledge-point dropdowns are single-select and include an “all” option.
- Difficulty remains single-select; clicking the active level clears it.
- Difficulty controls expose accessible names and `aria-pressed`.
- Color is not the only correct/incorrect indicator; retain text and ✓/✗.
- Do not change backend APIs or the “我的练习” filters.

---

### Task 1: Native Type and Tag Dropdowns

**Files:**
- Create: `src/test/java/com/mathematics/FrontendUiContractTest.java`
- Modify: `src/main/resources/static/index.html:44-60`
- Modify: `src/main/resources/static/app.js:334-385`
- Modify: `src/main/resources/static/app.css:126-133`

**Interfaces:**
- Consumes: existing `S.filter.types`, `S.filter.tagId`, `refreshList()`, and `S.tags`.
- Produces: `#typeFilter` and `#tagFilter` native selects that write zero-or-one type and one optional tag into existing filter state.

- [ ] **Step 1: Write the failing contract test**

```java
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

    private static String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
D:\Tools\apache-maven-3.9.9\bin\mvn.cmd -Dtest=FrontendUiContractTest test
```

Expected: FAIL because `#typeFilter` and `#tagFilter` do not exist.

- [ ] **Step 3: Add the native selects**

Replace the type and tag chip groups with labeled selects:

```html
<label class="filter-field">
  <span>题型</span>
  <select id="typeFilter">
    <option value="">全部题型</option>
    <option value="SINGLE">单选</option>
    <option value="MULTI">多选</option>
    <option value="JUDGE">判断</option>
    <option value="NUMERIC">填空</option>
    <option value="BLANK">多空填空</option>
  </select>
</label>
<label class="filter-field">
  <span>知识点</span>
  <select id="tagFilter">
    <option value="">全部知识点</option>
  </select>
</label>
```

Style `.filter-field` and `.filter-select` through native select selectors, including `:focus-visible`.

- [ ] **Step 4: Connect selects to existing state**

```javascript
$('#typeFilter').onchange = event => {
  S.filter.types.clear();
  if (event.target.value) S.filter.types.add(event.target.value);
  refreshList();
};

$('#tagFilter').onchange = event => {
  S.filter.tagId = event.target.value ? Number(event.target.value) : null;
  refreshList();
};
```

In `loadTags()`, populate `#tagFilter` with one option per tag while keeping `#wrongTagFilters` unchanged.

- [ ] **Step 5: Run the focused test and full suite**

Run the focused command from Step 2, then:

```powershell
D:\Tools\apache-maven-3.9.9\bin\mvn.cmd test
```

Expected: focused test PASS; full suite reports zero failures.

- [ ] **Step 6: Commit**

```powershell
git add src/test/java/com/mathematics/FrontendUiContractTest.java src/main/resources/static/index.html src/main/resources/static/app.js src/main/resources/static/app.css
git commit -m "feat: simplify problem filters"
```

---

### Task 2: Right-Aligned Difficulty Icon Buttons

**Files:**
- Modify: `src/test/java/com/mathematics/FrontendUiContractTest.java`
- Modify: `src/main/resources/static/index.html:44-60`
- Modify: `src/main/resources/static/app.js:342-353`
- Modify: `src/main/resources/static/app.css:126-133`

**Interfaces:**
- Consumes: existing `S.filter.difficulty`, `refreshList()`, and `levelIcon(level)`.
- Produces: `.difficulty-filter` containing three buttons with `data-difficulty`, accessible names, and synchronized `aria-pressed`.

- [ ] **Step 1: Add a failing difficulty-control test**

```java
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
```

- [ ] **Step 2: Run the focused test and verify RED**

Run the focused test command from Task 1.

Expected: FAIL because the current difficulty controls are spans without labels or pressed state.

- [ ] **Step 3: Add three inline-SVG buttons**

Use `<button type="button" class="difficulty-btn" ... aria-pressed="false">` for each level. Keep one consistent outline SVG family, make the three glyphs progressively denser, and include visible labels “入门 / 进阶 / 挑战”.

- [ ] **Step 4: Synchronize visual and accessibility state**

```javascript
$$('[data-difficulty]').forEach(button => {
  button.onclick = () => {
    const value = Number(button.dataset.difficulty);
    S.filter.difficulty = S.filter.difficulty === value ? null : value;
    $$('[data-difficulty]').forEach(other => {
      const selected = Number(other.dataset.difficulty) === S.filter.difficulty;
      other.classList.toggle('on', selected);
      other.setAttribute('aria-pressed', String(selected));
    });
    refreshList();
  };
});
```

Use a 40px minimum desktop hit target and 44px minimum at the mobile breakpoint. Add `:focus-visible` and avoid layout-shifting transforms.

- [ ] **Step 5: Run focused and full tests**

Run the commands from Task 1 Step 5.

Expected: zero failures.

- [ ] **Step 6: Commit**

```powershell
git add src/test/java/com/mathematics/FrontendUiContractTest.java src/main/resources/static/index.html src/main/resources/static/app.js src/main/resources/static/app.css
git commit -m "feat: add accessible difficulty filters"
```

---

### Task 3: Theme-Adaptive Correct and Incorrect Colors

**Files:**
- Modify: `src/test/java/com/mathematics/FrontendUiContractTest.java`
- Modify: `src/main/resources/static/app.css:5-30`
- Modify: `src/main/resources/static/app.js:653-713`

**Interfaces:**
- Consumes: existing semantic CSS variables `--ok`, `--ok-bg`, `--ok-ink`, `--bad`, `--bad-bg`, and `--bad-ink`.
- Produces: approved theme-specific token values and explicit ✓/✗ option markers.

- [ ] **Step 1: Add failing theme-token and non-color-indicator tests**

```java
@Test
void answerStatusColorsAdaptToEachThemeWithoutRelyingOnColor() throws IOException {
    String css = resource("static/app.css");
    String js = resource("static/app.js");

    assertThat(css).contains(
            "--ok: #5F6F9A; --ok-bg: #EFF1F7",
            "--bad: #A86452; --bad-bg: #F8EEEA",
            "--ok: #287A8B; --ok-bg: #E8F3F5",
            "--bad: #A86B1F; --bad-bg: #FAF1E3");
    assertThat(js).contains(
            "option.dataset.feedback = '✓'",
            "option.dataset.feedback = '✗'");
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run the focused test command from Task 1.

Expected: FAIL because old green/red variables and no option feedback markers remain.

- [ ] **Step 3: Replace only semantic theme tokens**

Set:

```css
[data-theme="warm"] {
  --ok: #5F6F9A; --ok-bg: #EFF1F7; --ok-ink: #465477;
  --bad: #A86452; --bad-bg: #F8EEEA; --bad-ink: #80483A;
}
[data-theme="cool"] {
  --ok: #287A8B; --ok-bg: #E8F3F5; --ok-ink: #1F6270;
  --bad: #A86B1F; --bad-bg: #FAF1E3; --bad-ink: #7D5017;
}
```

Do not hardcode these values in component selectors.

- [ ] **Step 4: Add explicit option feedback markers**

In `markObjectiveOptions`, set `option.dataset.feedback = '✓'` for standard answers and `option.dataset.feedback = '✗'` for selected wrong answers. Render the marker with `.opt[data-feedback]::after`; keep existing result titles and state badges.

- [ ] **Step 5: Run focused and full tests**

Run the commands from Task 1 Step 5.

Expected: zero failures.

- [ ] **Step 6: Check recently edited files for diagnostics**

Check `index.html`, `app.css`, `app.js`, and `FrontendUiContractTest.java`; fix introduced errors before committing.

- [ ] **Step 7: Commit**

```powershell
git add src/test/java/com/mathematics/FrontendUiContractTest.java src/main/resources/static/app.css src/main/resources/static/app.js
git commit -m "feat: adapt answer status colors by theme"
```

---

### Task 4: Final Browser and Build Verification

**Files:**
- Verify only; no planned production edits.

**Interfaces:**
- Consumes: completed Tasks 1–3.
- Produces: fresh verification evidence for handoff.

- [ ] **Step 1: Run the complete test suite**

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
D:\Tools\apache-maven-3.9.9\bin\mvn.cmd test
```

Expected: all tests pass with zero failures and zero errors.

- [ ] **Step 2: Check browser behavior**

Start the app and verify at 1180px and 375px:

- Type and tag dropdowns refresh the list and can return to “all”.
- Difficulty buttons are right-aligned on desktop, wrap without overlap on mobile, toggle off, and expose pressed state.
- Warm correct/incorrect states use indigo/terracotta.
- Cool correct/incorrect states use cyan/amber.
- Correct/incorrect remains understandable without color through text and ✓/✗.
- Keyboard focus is visible on selects and difficulty buttons.

- [ ] **Step 3: Inspect the exact diff**

Confirm only the planned test, static frontend files, design document, and plan document changed for this feature. Do not overwrite unrelated work.

