# R18 纠错处理与回溯重判 · 设计

日期：2026-09-21。对应规格 R18 与接口 A21。

## 要解决的问题

学生已经能对题目提交纠错工单（A18，`problem_feedback` 表，状态 `OPEN`）。但工单目前是个死信箱：没有任何接口能处理它，也没有任何机制修正已经判错的历史提交。

一道题的标准答案写错了，影响面不只是这道题本身：做对的学生被判成错，错题本里多出一条本不该存在的记录，知识点正确率被压低。改完答案不回溯，这些错误会永久留在学生数据里。

## 范围

本里程碑做完整闭环：后端结案与重判 + 管理端纠错队列界面。

不做：通知受影响用户（V1 规格里没有通知模块）、纠错工单的分配与流转（V1 只有一个管理员角色）。

## 接口

沿用 OpenAPI 里已经定好的 A21，另加一个列表接口——队列界面需要它，原 OpenAPI 漏了。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/admin/feedback` | 按状态列纠错工单，默认 `OPEN` |
| POST | `/api/v1/admin/feedback/{id}/resolve` | 结案，可选升版与重判 |

结案请求体：`{ decision: FIXED|REJECTED, newVersion?: UpsertProblemRequest, regrade?: boolean, remark? }`，响应 `{ status, regradedCount }`。

## 结案规则

只有 `OPEN` 的工单能结案。重复结案直接拒绝——这既防误操作，也顺带保证了重判不会跑两遍。

`REJECTED` 只改状态、记 `remark` 与处理人，不碰题目。

`FIXED` 必须真的有修正动作，二者之一：请求里带 `newVersion`，或者题目的当前版本已经不是工单记录的那个版本（管理员先在录题界面改过了）。两者都不满足就拒绝，否则"标记已修正"会退化成一句空话。

带 `newVersion` 时直接复用 `AdminProblemService.upsert`，强制 `id` 为工单所属题目、`publish=true`。复用而不是另写一套，是为了让来源闸门、标准答案自检、`changeNote` 强制这些规则自动生效——纠错升版没有理由比正常升版宽松。

## 重判

`regrade=true` 时按新版本回溯这道题的历史提交。

**选取范围**：每条作答链的末端，且 `problem_version_id` 不是新版本。一条提交一旦有后继（别的行的 `regraded_from` 指向它），就不再单独重判。跨两次修正时只重新评估末端那一行；把原始行也算进去的话，后续一次「改个错字」会把已经翻正过的提交再加一次正确数。

**判分复用**：把学生当时提交的 `answer_json` 原样喂给同一套 `GraderRegistry`，对照新版本的标准答案与判题配置。不另写一份判分逻辑。

**只处理结果变化的**：结果没变就跳过，不写新行也不动任何统计。答案改对了，本来就答对的学生不该多出一条记录。

**结果落库**：追加新 `submission` 行，`regraded_from` 指向原提交，`problem_version_id` 指向新版本。旧行原样保留——"改题前判成什么样"是回溯问题时唯一的证据。幂等键取 `regrade-{feedbackId}-{原提交id}`，撞上唯一索引就说明这条已经重判过。

**统计修正**：

- 由错转对：`wrong_item.wrong_count` 减一，减到 0 就删掉整行（这条记录本来就是我们自己的 bug 造出来的）；每个关联知识点的 `correct_count` 加一。
- 由对转错：照正常答错处理，入错题本；`correct_count` 减一。
- 两种情况都不动 `attempt_count`。学生并没有重新作答，作答次数凭什么变。

`correct_count` 的加减带边界保护，不允许越过 `0` 与 `attempt_count`。

## 模块边界

重判要同时写 `submission`、`wrong_item`、`user_tag_progress`，这三张表属于 practice 模块。所以重判逻辑放在 `practice.RegradeService`，由 `admin.AdminFeedbackService` 调用。admin 负责编排（结案、升版、审计），practice 负责自己表里的一致性，两边不越界。

整个结案落在一个事务里：升版、重判、改状态、写审计要么全成要么全不成。半成功的结案会留下一道改了答案但没重判的题，而工单已经关了，没人会再去看它。

## 审计

结案写一条 `audit_log`：`FEEDBACK_FIX` 或 `FEEDBACK_REJECT`，`target_type=FEEDBACK`，detail 里记 `decision`、`versionNo`、`regradedCount`、`remark`。升版本身另有 `PROBLEM_PUBLISH`，两条合起来能还原完整动作。

## 前端

管理端加「纠错」视图，和现有「录题」并列，同样只对 `ADMIN` 可见。

列表按状态筛选（默认 `OPEN`），每条显示题目标题、理由、学生填的细节、提交时间。结案表单：选 `FIXED` / `REJECTED`、填处理说明、勾选是否重判。

选 `FIXED` 时不在这个界面里重做一遍录题表单——那是录题界面的职责。流程是：点「去修正」跳到录题界面改答案并发布，回到纠错队列再结案，此时「当前版本已变」这个条件已经满足。这样避免了两个界面各有一份题目编辑表单。

重判结果用 `role="alert"` 播报「已重判 N 条提交」，0 条也要明确说，否则管理员无法判断是没受影响还是功能没生效。

## 测试

集成测试用独立的 H2 库名，避免和其他测试的精确条数断言互相干扰。

覆盖：非管理员被拦；`REJECTED` 只改状态；`FIXED` + 重判把答错改成答对并修正错题本与正确率；`regrade=false` 不产生新提交；结果不变的提交被跳过；已结案工单不能再结案；`FIXED` 但没有任何修正动作被拒绝。
