# 题目批量导入

管理端「录题 → 批量导入」粘贴 `items` 数组，或直接 `POST /api/v1/admin/problems/import`。
[problem-import-template.json](problem-import-template.json) 是五种题型各一道的可用样例，测试会原样导入它，保证格式不过期。

- 导入只新建、不覆盖；坏条目只让那一行失败，其余照常入库。
- 先以 `"publish": false` 导成草稿，审校后在管理端发布。
- 难度只有 `2` 入门 / `3` 进阶 / `4` 挑战。
- 知识点写 `tagSlugs`（各环境稳定），不要写 `tagIds`（各环境的自增 id 不同）。
- 公式用 `\\( … \\)` 行内、`\\[ … \\]` 独立（JSON 里反斜杠要写两个）；配图先在录题页「插入配图」上传，拿到 `![](asset:…)` 再贴进题干。

## 标准答案格式

| 题型 | `answerJson` | 说明 |
|---|---|---|
| SINGLE | `{"choice":"B"}` | |
| MULTI | `{"choices":["B","D"]}` | 顺序无关 |
| JUDGE | `{"value":true}` | |
| NUMERIC | `{"value":"9/10"}` | 支持整数、小数、分数、带分数（`1又1/2`）、百分数；`graderConfig.tolerance` 为 0 时精确比较，学生写 `0.9` 或 `18/20` 同样判对 |
| BLANK | `{"blanks":[["16"],["4"]]}` | 每空一组可接受写法；纯数字的空按数值等价判 |

## 知识点 slug

| 一级 | 二级 |
|---|---|
| `travel` 行程问题 | `travel-meet-chase` 相遇与追及 · `travel-boat` 流水行船 · `travel-train` 火车过桥 · `travel-circular` 环形跑道 |
| `number-theory` 数论 | `divisibility` 数的整除 · `nt-primes` 质数与合数 · `nt-factors` 因数与倍数 · `nt-remainders` 余数问题 · `nt-place-value` 位值原理 |
| `plane-geometry` 平面几何 | `geo-area` 面积计算 · `geo-equal-area` 等积变形 · `geo-models` 鸟头与燕尾模型 · `geo-circle` 圆与扇形 · `geo-solid` 立体图形 |
| `combinatorics` 组合计数 | `comb-add-mult` 加法与乘法原理 · `comb-perm` 排列组合 · `comb-inclusion-exclusion` 容斥原理 · `comb-pigeonhole` 抽屉原理 · `comb-enumeration` 枚举与标数 |
| `word-problems` 应用题 | `wp-sum-diff` 和差倍 · `wp-chicken-rabbit` 鸡兔同笼 · `wp-surplus` 盈亏 · `wp-work` 工程 · `wp-concentration` 浓度 · `wp-age` 年龄 |
| `calculation` 计算 | `calc-tricks` 速算与巧算 · `calc-fractions` 分数运算 · `calc-series` 数列求和 · `calc-new-operation` 定义新运算 |
| `logic` 逻辑与规律 | `logic-reasoning` 逻辑推理 · `logic-cryptarithm` 数字谜 · `logic-patterns` 找规律 |

新增知识点走数据库迁移（参照 `db/migration/*/V91__tag_taxonomy.sql`），两种方言都要加。
