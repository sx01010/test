-- 开发种子数据，MySQL 版。内容与 db/seed/h2/V90__seed_dev_content.sql 完全一致，
-- 差异只在三处方言上：
--   1. 自增序列：H2 是 ALTER COLUMN id RESTART WITH n，MySQL 是 AUTO_INCREMENT = n。
--   2. 反斜杠：MySQL 的字符串字面量把 \ 当转义符，\b 会变成退格符、\( 的反斜杠会被吞掉，
--      LaTeX 直接报废。所以公式里的反斜杠全部写成 \\。H2 不做这层转义，这个坑在 H2 上
--      永远暴露不出来。
--   3. user / year / round 是 MySQL 保留字或函数名，一律加反引号。
--
-- 正式环境不该加载这个目录：用 FLYWAY_LOCATIONS 覆盖掉 classpath:db/seed/mysql。
-- id=1 是内容作者系统账号，password_hash 故意不是合法 BCrypt，登不进来，只用于 created_by。

INSERT INTO `user` (id, nickname, email, password_hash, role, practice_mode, status)
VALUES (1, '内容组', 'content@mathematics.local', 'x-not-a-valid-bcrypt-hash', 'ADMIN', 0, 'ACTIVE');

INSERT INTO `tag` (id, parent_id, name, slug, sort_order) VALUES
    (1, NULL, '行程问题',  'travel',        1),
    (2, NULL, '数论',      'number-theory', 2),
    (3, 2,    '数的整除',  'divisibility',  1),
    (4, NULL, '平面几何',  'plane-geometry',3),
    (5, NULL, '组合计数',  'combinatorics', 4),
    (6, NULL, '应用题',    'word-problems', 5);

-- 五道题覆盖五种客观题型，用来验证判题器与前端作答区
INSERT INTO `problem` (id, title, problem_type, difficulty, grade, status, created_by) VALUES
    (1, '相遇问题 · 中途休息',        'SINGLE',  3, '六年级', 'PUBLISHED', 1),
    (2, '被 7 整除且数字和为 12',     'NUMERIC', 4, '六年级', 'PUBLISHED', 1),
    (3, '既是 3 的倍数又是 4 的倍数', 'MULTI',   2, '六年级', 'PUBLISHED', 1),
    (4, '内角比 1:2:3 的三角形',      'JUDGE',   2, '六年级', 'PUBLISHED', 1),
    (5, '正方形中的两个三角形',        'BLANK',   3, '六年级', 'PUBLISHED', 1);

INSERT INTO `problem_version` (id, problem_id, version_no, stem_md, options_json, answer_json, explanation_md, grader_config_json, max_score, created_by) VALUES
    (1, 1, 1,
     '甲、乙两人分别从相距 **56 千米** 的 A、B 两地同时出发相向而行，甲每小时行 **8 千米**，乙每小时行 **7 千米**。若甲出发 1 小时后休息了 30 分钟再继续前进，两人从出发到相遇一共需要多少小时？',
     '[{"key":"A","textMd":"3.6 小时"},{"key":"B","textMd":"4 小时"},{"key":"C","textMd":"4.2 小时"},{"key":"D","textMd":"4.5 小时"}]',
     '{"choice":"B"}',
     '设从出发到相遇共 t 小时。乙全程未停，行 7t 千米；甲休息 0.5 小时，实际行走 (t − 0.5) 小时，行 8(t − 0.5) 千米。

列方程：8(t − 0.5) + 7t = 56，即 15t − 4 = 56，解得 \\(t = 4\\)。

所以两人从出发到相遇共需 **4 小时**。',
     NULL, 100, 1),
    (2, 2, 1,
     '在所有三位数中，既能被 **7** 整除、各位数字之和又等于 **12** 的数共有多少个？（填个数）',
     NULL,
     '{"value":"9"}',
     '满足条件的数依次为 147、273、336、462、525、651、714、840、903，共 **9 个**。

相邻两数之差在 126 与 63 之间交替，可以用这个规律快速枚举验证。',
     '{"tolerance":0}', 100, 1),
    (3, 3, 1,
     '下列各数中，既是 3 的倍数又是 4 的倍数的有哪些？（多选）',
     '[{"key":"A","textMd":"108"},{"key":"B","textMd":"124"},{"key":"C","textMd":"132"},{"key":"D","textMd":"150"}]',
     '{"choices":["A","C"]}',
     '判断 3 的倍数看各位数字之和，判断 4 的倍数看末两位。

- 108：1+0+8=9 是 3 的倍数，08÷4=2，成立
- 124：1+2+4=7 不是 3 的倍数
- 132：1+3+2=6 成立，32÷4=8 成立
- 150：50÷4 除不尽

答案为 **A、C**。等价的快捷判断是直接看是否为 12 的倍数。',
     NULL, 100, 1),
    (4, 4, 1,
     '一个三角形三个内角度数满足 \\(\\alpha:\\beta:\\gamma = 1:2:3\\)，那么这个三角形一定是直角三角形。这句话对吗？',
     NULL,
     '{"value":true}',
     '三角形内角和为 180°，按 1:2:3 分配，总份数为 6，每份 30°，三个角分别是 30°、60°、90°。

最大角恰为 90°，所以一定是直角三角形，判断 **正确**。',
     NULL, 100, 1),
    (5, 5, 1,
     '正方形 ABCD 的边长为 **8**，E 是边 BC 的中点，连接 AE、DE。请分别求出 △ABE 与 △ADE 的面积（第 1 空填 △ABE，第 2 空填 △ADE）。',
     NULL,
     '{"blanks":[["16"],["32"]]}',
     'E 为 BC 中点，故 BE = 4。

△ABE：以 AB = 8 为高、BE = 4 为底，面积 = 8 × 4 ÷ 2 = **16**。

△ADE：以 AD = 8 为底，E 到 AD 的距离即 AB = 8，面积 = 8 × 8 ÷ 2 = **32**。

验算：16 + 32 + △DCE(16) = 64，正好是正方形面积。',
     '{"orderIndependent":false}', 100, 1);

UPDATE `problem` SET current_version_id = 1 WHERE id = 1;
UPDATE `problem` SET current_version_id = 2 WHERE id = 2;
UPDATE `problem` SET current_version_id = 3 WHERE id = 3;
UPDATE `problem` SET current_version_id = 4 WHERE id = 4;
UPDATE `problem` SET current_version_id = 5 WHERE id = 5;

INSERT INTO `problem_tag` (problem_id, tag_id) VALUES (1, 1), (2, 2), (3, 3), (4, 4), (5, 4);

-- 来源登记：参考真题的两道走 ADAPTED 并写改编说明，其余为原创
INSERT INTO `problem_source` (problem_id, origin_type, contest_name, `year`, `round`, rewrite_note, source_url) VALUES
    (1, 'ADAPTED', '华罗庚金杯少年数学邀请赛', 2023, '初赛', '参考真题结构重新命题：更换全部数据与人物设定，题面与解析均自行撰写。', NULL),
    (2, 'ADAPTED', '迎春杯数学花园探秘',       2022, '复赛', '参考真题考点（同余 + 数字和）重新命题，换掉原始数值并自写解析。', NULL),
    (3, 'ORIGINAL', NULL, NULL, NULL, NULL, NULL),
    (4, 'ORIGINAL', NULL, NULL, NULL, NULL, NULL),
    (5, 'ORIGINAL', NULL, NULL, NULL, NULL, NULL);

-- 显式写入主键后需要把自增序列推到下一个可用值
ALTER TABLE `user` AUTO_INCREMENT = 2;
ALTER TABLE `tag` AUTO_INCREMENT = 7;
ALTER TABLE `problem` AUTO_INCREMENT = 6;
ALTER TABLE `problem_version` AUTO_INCREMENT = 6;
