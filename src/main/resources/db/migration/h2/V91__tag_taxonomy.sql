-- 知识点树（两级，7 个一级、32 个二级）。正式环境不挂 seed，知识点却是录题的必填项，所以放在结构迁移里。
--
-- 版本号排在 V90 种子之后是有意的：开发库的种子用固定 id 插了 6 个一级/二级节点，
-- 这里必须后跑、按 slug 跳过已存在的节点，否则新库上会先占掉 id 1–6 让种子主键冲突。
-- 之后的结构迁移照常从 V12 往下编，靠 out-of-order 补跑。
--
-- 每条都是「slug 不存在才插」，H2 与 MySQL 共用同一份写法。

-- 一级
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT NULL, '行程问题', 'travel', 1 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'travel');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT NULL, '数论', 'number-theory', 2 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'number-theory');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT NULL, '平面几何', 'plane-geometry', 3 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'plane-geometry');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT NULL, '组合计数', 'combinatorics', 4 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'combinatorics');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT NULL, '应用题', 'word-problems', 5 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'word-problems');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT NULL, '计算', 'calculation', 6 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'calculation');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT NULL, '逻辑与规律', 'logic', 7 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'logic');

-- 行程问题
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'travel'), '相遇与追及', 'travel-meet-chase', 1 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'travel-meet-chase');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'travel'), '流水行船', 'travel-boat', 2 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'travel-boat');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'travel'), '火车过桥', 'travel-train', 3 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'travel-train');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'travel'), '环形跑道', 'travel-circular', 4 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'travel-circular');

-- 数论（「数的整除」在开发种子里已有，新库在这里补）
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'number-theory'), '数的整除', 'divisibility', 1 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'divisibility');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'number-theory'), '质数与合数', 'nt-primes', 2 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'nt-primes');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'number-theory'), '因数与倍数', 'nt-factors', 3 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'nt-factors');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'number-theory'), '余数问题', 'nt-remainders', 4 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'nt-remainders');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'number-theory'), '位值原理', 'nt-place-value', 5 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'nt-place-value');

-- 平面几何
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'plane-geometry'), '面积计算', 'geo-area', 1 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'geo-area');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'plane-geometry'), '等积变形', 'geo-equal-area', 2 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'geo-equal-area');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'plane-geometry'), '鸟头与燕尾模型', 'geo-models', 3 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'geo-models');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'plane-geometry'), '圆与扇形', 'geo-circle', 4 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'geo-circle');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'plane-geometry'), '立体图形', 'geo-solid', 5 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'geo-solid');

-- 组合计数
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'combinatorics'), '加法与乘法原理', 'comb-add-mult', 1 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'comb-add-mult');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'combinatorics'), '排列组合', 'comb-perm', 2 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'comb-perm');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'combinatorics'), '容斥原理', 'comb-inclusion-exclusion', 3 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'comb-inclusion-exclusion');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'combinatorics'), '抽屉原理', 'comb-pigeonhole', 4 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'comb-pigeonhole');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'combinatorics'), '枚举与标数', 'comb-enumeration', 5 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'comb-enumeration');

-- 应用题
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'word-problems'), '和差倍问题', 'wp-sum-diff', 1 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'wp-sum-diff');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'word-problems'), '鸡兔同笼', 'wp-chicken-rabbit', 2 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'wp-chicken-rabbit');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'word-problems'), '盈亏问题', 'wp-surplus', 3 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'wp-surplus');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'word-problems'), '工程问题', 'wp-work', 4 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'wp-work');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'word-problems'), '浓度问题', 'wp-concentration', 5 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'wp-concentration');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'word-problems'), '年龄问题', 'wp-age', 6 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'wp-age');

-- 计算
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'calculation'), '速算与巧算', 'calc-tricks', 1 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'calc-tricks');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'calculation'), '分数运算', 'calc-fractions', 2 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'calc-fractions');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'calculation'), '数列求和', 'calc-series', 3 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'calc-series');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'calculation'), '定义新运算', 'calc-new-operation', 4 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'calc-new-operation');

-- 逻辑与规律
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'logic'), '逻辑推理', 'logic-reasoning', 1 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'logic-reasoning');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'logic'), '数字谜', 'logic-cryptarithm', 2 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'logic-cryptarithm');
INSERT INTO tag (parent_id, name, slug, sort_order) SELECT (SELECT id FROM tag WHERE slug = 'logic'), '找规律', 'logic-patterns', 3 FROM (SELECT 1 AS x) d WHERE NOT EXISTS (SELECT 1 FROM tag WHERE slug = 'logic-patterns');
