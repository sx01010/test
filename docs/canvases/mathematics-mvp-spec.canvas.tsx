import {
  Button,
  Callout,
  Card,
  CardBody,
  CardHeader,
  Code,
  Divider,
  Grid,
  H1,
  H2,
  H3,
  Pill,
  Row,
  Stack,
  Stat,
  Table,
  Text,
  useCanvasAction,
  useCanvasState,
  useHostTheme,
} from "cursor/canvas";

const tabs = ["V1 范围", "需求清单", "数据表", "接口", "内容与合规", "推迟清单"] as const;
type Tab = (typeof tabs)[number];

type Domain = "全部" | "identity" | "problem" | "practice" | "material" | "quality" | "admin";
type Priority = "P0" | "P1";

interface Requirement {
  id: string;
  persona: "学员" | "管理员";
  domain: Exclude<Domain, "全部">;
  title: string;
  acceptance: string;
  priority: Priority;
}

interface Column {
  name: string;
  type: string;
  constraint: string;
  note: string;
}

interface DbTable {
  name: string;
  domain: Exclude<Domain, "全部">;
  purpose: string;
  columns: Column[];
  indexes: string[];
}

interface ApiItem {
  id: string;
  module: Exclude<Domain, "全部">;
  method: "GET" | "POST" | "PATCH" | "DELETE";
  path: string;
  auth: string;
  summary: string;
  request: string;
  response: string;
  notes: string;
}

const requirements: Requirement[] = [
  {
    id: "R01",
    persona: "学员",
    domain: "identity",
    title: "注册与登录",
    acceptance: "昵称 + 邮箱或手机号 + 密码即可注册，不收集真实姓名、学校、年龄；登录返回 accessToken 与 refreshToken。",
    priority: "P0",
  },
  {
    id: "R02",
    persona: "学员",
    domain: "identity",
    title: "账号安全基线",
    acceptance: "密码 BCrypt 存储；同账号连续 5 次失败锁定 15 分钟；refresh token 轮换，旧 token 立即作废。",
    priority: "P0",
  },
  {
    id: "R03",
    persona: "学员",
    domain: "identity",
    title: "练习模式开关",
    acceptance: "个人设置里可开启“做完再看解析”。开启后解析接口要求该题已有提交记录，关闭时随时可看，默认关闭。",
    priority: "P0",
  },
  {
    id: "R04",
    persona: "学员",
    domain: "problem",
    title: "搜索与筛选题目",
    acceptance: "题型多选，叠加知识点、难度、年级、来源类型与关键词；游标分页，筛选条件回填到 URL。",
    priority: "P0",
  },
  {
    id: "R05",
    persona: "学员",
    domain: "problem",
    title: "题目详情不内联答案",
    acceptance: "详情只返回题干、选项、知识点与出处；答案和解析由独立接口返回。",
    priority: "P0",
  },
  {
    id: "R06",
    persona: "学员",
    domain: "problem",
    title: "查看解析",
    acceptance: "默认不要求先提交；练习模式开启时未提交返回 CONTENT_NOT_VISIBLE；接口按用户与 IP 限流。",
    priority: "P0",
  },
  {
    id: "R07",
    persona: "学员",
    domain: "problem",
    title: "相似题推荐",
    acceptance: "详情页与判题结果页给出 5 道同知识点、难度 ±1 的题；排除当前题和最近做对的题，不足时放宽难度。",
    priority: "P0",
  },
  {
    id: "R08",
    persona: "学员",
    domain: "practice",
    title: "客观题自动判分",
    acceptance: "单选、多选、判断、数值、多空五种题型提交后即时出分；多空按空给分，数值按容差判定。",
    priority: "P0",
  },
  {
    id: "R09",
    persona: "学员",
    domain: "practice",
    title: "提交幂等",
    acceptance: "进入题目时前端生成 UUID 作为 Idempotency-Key，重试复用、重新作答换新值；服务端靠唯一索引兜底。",
    priority: "P0",
  },
  {
    id: "R10",
    persona: "学员",
    domain: "practice",
    title: "可选计时",
    acceptance: "默认不计时；开启单题计时后仅记录 duration_ms，不参与判分，也不做任何排名展示。",
    priority: "P0",
  },
  {
    id: "R11",
    persona: "学员",
    domain: "practice",
    title: "提交历史",
    acceptance: "按题目查看自己的提交列表，含版本号、对错、得分、耗时；题目已升版时标注“此题已更新”。",
    priority: "P0",
  },
  {
    id: "R12",
    persona: "学员",
    domain: "practice",
    title: "错题本",
    acceptance: "WRONG 和 PARTIAL 都自动入本；连续做对 2 次自动标记 mastered，也可手动标记；阈值做成配置项。",
    priority: "P0",
  },
  {
    id: "R13",
    persona: "学员",
    domain: "practice",
    title: "错题本筛选",
    acceptance: "可按知识点和加入时间（今天 / 最近 7 天 / 全部）筛选，默认只看未掌握。",
    priority: "P0",
  },
  {
    id: "R14",
    persona: "学员",
    domain: "practice",
    title: "知识点进度",
    acceptance: "按知识点统计作答数、正确数和最近作答时间，个人页用条形图展示，弱项排在最前。",
    priority: "P0",
  },
  {
    id: "R15",
    persona: "学员",
    domain: "quality",
    title: "题目纠错",
    acceptance: "对任一题可提交纠错，理由含答案有误、题面笔误、表述不清；同一人对同一题未结案时只保留一条。",
    priority: "P0",
  },
  {
    id: "R16",
    persona: "管理员",
    domain: "admin",
    title: "题目录入与版本",
    acceptance: "后台录入题干、答案、解析、知识点、难度；已发布题的内容变更生成新 version_no，历史提交仍指向旧版本。",
    priority: "P0",
  },
  {
    id: "R17",
    persona: "管理员",
    domain: "admin",
    title: "来源与授权登记",
    acceptance: "每道题必须登记 origin_type；ADAPTED 必须写改编说明，LICENSED 必须填授权凭证，否则不能发布。",
    priority: "P0",
  },
  {
    id: "R18",
    persona: "管理员",
    domain: "admin",
    title: "纠错处理与重判",
    acceptance: "处理纠错可选择修正：生成新版本并按旧版本回溯受影响的提交，重判后写审计日志，结果计入用户正确率。",
    priority: "P0",
  },
  {
    id: "R19",
    persona: "管理员",
    domain: "admin",
    title: "批量导入",
    acceptance: "用 JSON 模板批量导入题目，逐行校验题型、答案格式与来源字段，失败行返回行号和原因，成功行照常入库。",
    priority: "P1",
  },
  {
    id: "R20",
    persona: "学员",
    domain: "identity",
    title: "找回密码",
    acceptance: "邮箱或短信验证码重置，验证码 10 分钟失效，同一账号 1 分钟内只发一次。",
    priority: "P1",
  },
  {
    id: "R21",
    persona: "学员",
    domain: "material",
    title: "真题与讲义下载",
    acceptance: "可按资料类型、年级和年份筛选；只有来源为 LICENSED、PUBLIC 或平台自有的已发布 PDF 才能下载，点击后取得 5 分钟有效的签名 URL。",
    priority: "P0",
  },
  {
    id: "R22",
    persona: "管理员",
    domain: "material",
    title: "资料上传与发布",
    acceptance: "管理员通过上传凭证将 PDF 直传对象存储；服务端校验扩展名、MIME、大小与恶意文件扫描结果，授权凭证不完整时禁止发布。",
    priority: "P0",
  },
  {
    id: "R23",
    persona: "管理员",
    domain: "material",
    title: "资料下架",
    acceptance: "资料可立即下架；下架后不再签发下载 URL，已有短链最多 5 分钟失效；文件延迟 7 天清理并保留审计记录。",
    priority: "P0",
  },
];

const tables: DbTable[] = [
  {
    name: "user",
    domain: "identity",
    purpose: "账号。面向中小学生，只存必要信息：头像用预设编号而非上传文件，不存真实姓名、学校和生日。",
    columns: [
      { name: "id", type: "BIGINT", constraint: "PK", note: "主键" },
      { name: "nickname", type: "VARCHAR(32)", constraint: "NOT NULL", note: "展示名" },
      { name: "email", type: "VARCHAR(128)", constraint: "UNIQUE NULL", note: "与 phone 至少填一个" },
      { name: "phone", type: "VARCHAR(20)", constraint: "UNIQUE NULL", note: "与 email 至少填一个" },
      { name: "password_hash", type: "VARCHAR(128)", constraint: "NOT NULL", note: "BCrypt" },
      { name: "avatar_preset", type: "TINYINT", constraint: "NOT NULL DEFAULT 0", note: "预设头像编号，不做上传" },
      { name: "role", type: "VARCHAR(16)", constraint: "NOT NULL", note: "USER / ADMIN，V1 不需要单独角色表" },
      { name: "practice_mode", type: "TINYINT", constraint: "NOT NULL DEFAULT 0", note: "1 表示做完才看解析" },
      { name: "status", type: "VARCHAR(16)", constraint: "NOT NULL", note: "ACTIVE / LOCKED" },
      { name: "fail_count / locked_until", type: "INT / DATETIME", constraint: "NULL", note: "登录失败锁定" },
      { name: "created_at / updated_at / last_login_at", type: "DATETIME", constraint: "—", note: "时间列" },
    ],
    indexes: ["uk_user_email", "uk_user_phone"],
  },
  {
    name: "tag",
    domain: "problem",
    purpose: "知识点树。筛选、推荐和进度统计都挂在它上面，是 V1 唯一的分类维度。",
    columns: [
      { name: "id", type: "BIGINT", constraint: "PK", note: "主键" },
      { name: "parent_id", type: "BIGINT", constraint: "NULL", note: "父知识点" },
      { name: "name", type: "VARCHAR(64)", constraint: "NOT NULL", note: "如“行程问题”" },
      { name: "slug", type: "VARCHAR(64)", constraint: "UNIQUE", note: "稳定标识" },
      { name: "sort_order", type: "INT", constraint: "NOT NULL", note: "同级排序" },
    ],
    indexes: ["uk_tag_slug", "idx_tag_parent"],
  },
  {
    name: "problem",
    domain: "problem",
    purpose: "题目头信息。V1 全部是官方题，没有可见性概念，也没有用户自建题目。",
    columns: [
      { name: "id", type: "BIGINT", constraint: "PK", note: "主键" },
      { name: "title", type: "VARCHAR(200)", constraint: "NOT NULL", note: "题目标题" },
      { name: "problem_type", type: "VARCHAR(16)", constraint: "NOT NULL", note: "SINGLE / MULTI / JUDGE / NUMERIC / BLANK" },
      { name: "difficulty", type: "TINYINT", constraint: "NOT NULL", note: "2 入门 / 3 进阶 / 4 挑战" },
      { name: "grade", type: "VARCHAR(16)", constraint: "NOT NULL", note: "适用年级" },
      { name: "current_version_id", type: "BIGINT", constraint: "NULL", note: "当前对外版本" },
      { name: "status", type: "VARCHAR(16)", constraint: "NOT NULL", note: "DRAFT / PUBLISHED / HIDDEN" },
      { name: "created_by", type: "BIGINT", constraint: "NOT NULL", note: "录入管理员" },
      { name: "created_at / updated_at / deleted_at", type: "DATETIME", constraint: "软删", note: "时间列" },
    ],
    indexes: ["idx_problem_list (status, difficulty, created_at)", "idx_problem_type (problem_type)"],
  },
  {
    name: "problem_version",
    domain: "problem",
    purpose: "不可变题目快照。提交永远引用具体版本，纠错改题后历史记录仍可回溯。",
    columns: [
      { name: "id", type: "BIGINT", constraint: "PK", note: "主键" },
      { name: "problem_id", type: "BIGINT", constraint: "NOT NULL", note: "所属题目" },
      { name: "version_no", type: "INT", constraint: "NOT NULL", note: "从 1 递增" },
      { name: "stem_md", type: "MEDIUMTEXT", constraint: "NOT NULL", note: "题干 Markdown + LaTeX" },
      { name: "options_json", type: "JSON", constraint: "NULL", note: "选项，不含对错标记" },
      { name: "answer_json", type: "JSON", constraint: "NOT NULL", note: "标准答案，任何列表接口都不返回" },
      { name: "explanation_md", type: "MEDIUMTEXT", constraint: "NOT NULL", note: "解析，必填，自己撰写" },
      { name: "grader_config_json", type: "JSON", constraint: "NULL", note: "tolerance、orderIndependent" },
      { name: "max_score", type: "INT", constraint: "NOT NULL DEFAULT 100", note: "满分" },
      { name: "change_note", type: "VARCHAR(256)", constraint: "NULL", note: "为什么升版，纠错修正时必填" },
      { name: "created_by / created_at", type: "BIGINT / DATETIME", constraint: "NOT NULL", note: "写入后不改" },
    ],
    indexes: ["uk_problem_version (problem_id, version_no)"],
  },
  {
    name: "problem_tag",
    domain: "problem",
    purpose: "题目与知识点多对多。",
    columns: [
      { name: "problem_id", type: "BIGINT", constraint: "PK", note: "复合主键" },
      { name: "tag_id", type: "BIGINT", constraint: "PK", note: "复合主键" },
    ],
    indexes: ["pk (problem_id, tag_id)", "idx_tag_problem (tag_id, problem_id)"],
  },
  {
    name: "problem_source",
    domain: "problem",
    purpose: "来源与授权登记。这张表是版权策略的落地点，字段不齐就不允许发布。",
    columns: [
      { name: "problem_id", type: "BIGINT", constraint: "PK", note: "一对一挂题目" },
      { name: "origin_type", type: "VARCHAR(16)", constraint: "NOT NULL", note: "ORIGINAL 原创 / ADAPTED 改编 / LICENSED 已授权 / PUBLIC 公开可用" },
      { name: "contest_name", type: "VARCHAR(128)", constraint: "NULL", note: "参考赛事名，仅作标注" },
      { name: "year / round", type: "SMALLINT / VARCHAR(32)", constraint: "NULL", note: "年份与轮次" },
      { name: "rewrite_note", type: "VARCHAR(500)", constraint: "NULL", note: "ADAPTED 必填：改了哪些数据和表述" },
      { name: "license_ref", type: "VARCHAR(256)", constraint: "NULL", note: "LICENSED 必填：授权凭证编号或文件 key" },
      { name: "source_url", type: "VARCHAR(512)", constraint: "NULL", note: "PUBLIC 填公开来源链接" },
    ],
    indexes: ["idx_source_origin (origin_type)", "idx_source_year (year)"],
  },
  {
    name: "submission",
    domain: "practice",
    purpose: "一次作答快照。答案、版本、判题结果、耗时都在这一行，重判会写新行而不是改旧行。",
    columns: [
      { name: "id", type: "BIGINT", constraint: "PK", note: "主键" },
      { name: "user_id", type: "BIGINT", constraint: "NOT NULL", note: "作答人" },
      { name: "problem_id", type: "BIGINT", constraint: "NOT NULL", note: "题目" },
      { name: "problem_version_id", type: "BIGINT", constraint: "NOT NULL", note: "作答时的版本" },
      { name: "answer_json", type: "JSON", constraint: "NOT NULL", note: "用户答案快照" },
      { name: "result", type: "VARCHAR(16)", constraint: "NOT NULL", note: "CORRECT / PARTIAL / WRONG" },
      { name: "score / max_score", type: "DECIMAL(6,2)", constraint: "NOT NULL", note: "得分与满分" },
      { name: "details_json", type: "JSON", constraint: "NULL", note: "逐空对错" },
      { name: "duration_ms", type: "INT", constraint: "NULL", note: "不计时就留空" },
      { name: "idempotency_key", type: "VARCHAR(64)", constraint: "NOT NULL", note: "前端 UUID" },
      { name: "regraded_from", type: "BIGINT", constraint: "NULL", note: "非空表示由纠错重判产生" },
      { name: "created_at", type: "DATETIME", constraint: "NOT NULL", note: "提交时间" },
    ],
    indexes: ["uk_submission_idemp (user_id, idempotency_key)", "idx_sub_user_problem (user_id, problem_id, created_at)", "idx_sub_version (problem_version_id)"],
  },
  {
    name: "wrong_item",
    domain: "practice",
    purpose: "错题本。一人一题一行，WRONG 和 PARTIAL 都会进来。",
    columns: [
      { name: "id", type: "BIGINT", constraint: "PK", note: "主键" },
      { name: "user_id / problem_id", type: "BIGINT", constraint: "NOT NULL", note: "唯一组合" },
      { name: "last_submission_id", type: "BIGINT", constraint: "NOT NULL", note: "最近一次相关提交" },
      { name: "last_version_id", type: "BIGINT", constraint: "NOT NULL", note: "做错时的版本，用于提示题目已更新" },
      { name: "wrong_count", type: "INT", constraint: "NOT NULL", note: "累计做错" },
      { name: "consecutive_correct", type: "INT", constraint: "NOT NULL DEFAULT 0", note: "连续做对次数，达 2 自动掌握" },
      { name: "mastered", type: "TINYINT", constraint: "NOT NULL DEFAULT 0", note: "1 表示已掌握" },
      { name: "last_wrong_at", type: "DATETIME", constraint: "NOT NULL", note: "最近做错时间，供时间筛选" },
    ],
    indexes: ["uk_wrong (user_id, problem_id)", "idx_wrong_user (user_id, mastered, last_wrong_at)"],
  },
  {
    name: "user_tag_progress",
    domain: "practice",
    purpose: "只按知识点聚合的进度表。放弃多态 scope，写入行数可控，也够画个人页的掌握度。",
    columns: [
      { name: "user_id", type: "BIGINT", constraint: "PK", note: "复合主键" },
      { name: "tag_id", type: "BIGINT", constraint: "PK", note: "复合主键" },
      { name: "attempt_count", type: "INT", constraint: "NOT NULL", note: "作答次数" },
      { name: "correct_count", type: "INT", constraint: "NOT NULL", note: "全对次数" },
      { name: "last_submitted_at", type: "DATETIME", constraint: "NULL", note: "最近作答" },
    ],
    indexes: ["pk (user_id, tag_id)"],
  },
  {
    name: "problem_feedback",
    domain: "quality",
    purpose: "题目纠错工单。V1 唯一的用户产出内容，所以不需要完整的审核队列。",
    columns: [
      { name: "id", type: "BIGINT", constraint: "PK", note: "主键" },
      { name: "user_id", type: "BIGINT", constraint: "NOT NULL", note: "提交人" },
      { name: "problem_id / problem_version_id", type: "BIGINT", constraint: "NOT NULL", note: "针对哪一版" },
      { name: "reason", type: "VARCHAR(24)", constraint: "NOT NULL", note: "ANSWER_ERROR / TYPO / UNCLEAR / OTHER" },
      { name: "detail", type: "VARCHAR(500)", constraint: "NULL", note: "补充说明" },
      { name: "status", type: "VARCHAR(16)", constraint: "NOT NULL", note: "OPEN / FIXED / REJECTED" },
      { name: "handled_by / handled_at / remark", type: "BIGINT / DATETIME / VARCHAR", constraint: "NULL", note: "处理结果" },
      { name: "created_at", type: "DATETIME", constraint: "NOT NULL", note: "提交时间" },
    ],
    indexes: ["idx_feedback_open (status, created_at)", "uk_feedback_open (user_id, problem_id, status)"],
  },
  {
    name: "audit_log",
    domain: "admin",
    purpose: "只追加。重点记录改题和重判这类会影响用户已有成绩的操作。",
    columns: [
      { name: "id", type: "BIGINT", constraint: "PK", note: "主键" },
      { name: "actor_id", type: "BIGINT", constraint: "NULL", note: "操作者，系统可空" },
      { name: "action", type: "VARCHAR(48)", constraint: "NOT NULL", note: "PROBLEM_PUBLISH / PROBLEM_FIX / SUBMISSION_REGRADE" },
      { name: "target_type / target_id", type: "VARCHAR(16) / BIGINT", constraint: "NOT NULL", note: "操作对象" },
      { name: "detail_json", type: "JSON", constraint: "NULL", note: "变更前后与影响行数" },
      { name: "created_at", type: "DATETIME", constraint: "NOT NULL", note: "时间" },
    ],
    indexes: ["idx_audit_target (target_type, target_id, created_at)"],
  },
  {
    name: "material",
    domain: "material",
    purpose: "可下载资料的元数据与发布闸门。V1 只允许管理员发布，不接收用户上传。",
    columns: [
      { name: "id", type: "BIGINT", constraint: "PK", note: "主键" },
      { name: "title", type: "VARCHAR(200)", constraint: "NOT NULL", note: "资料标题" },
      { name: "material_type", type: "VARCHAR(16)", constraint: "NOT NULL", note: "PAPER / HANDOUT" },
      { name: "grade / year", type: "VARCHAR(16) / SMALLINT", constraint: "NULL", note: "筛选维度" },
      { name: "origin_type", type: "VARCHAR(16)", constraint: "NOT NULL", note: "OWNED / LICENSED / PUBLIC" },
      { name: "license_ref / source_url", type: "VARCHAR(256) / VARCHAR(512)", constraint: "NULL", note: "授权或公开来源，按来源类型必填" },
      { name: "status", type: "VARCHAR(16)", constraint: "NOT NULL", note: "DRAFT / PUBLISHED / HIDDEN" },
      { name: "created_by / created_at / updated_at", type: "BIGINT / DATETIME", constraint: "NOT NULL", note: "审计字段" },
    ],
    indexes: ["idx_material_list (status, material_type, grade, year)"],
  },
  {
    name: "material_file",
    domain: "material",
    purpose: "对象存储文件引用。应用不代理文件流，只签发短期下载地址。",
    columns: [
      { name: "id / material_id", type: "BIGINT", constraint: "PK / NOT NULL", note: "文件与资料" },
      { name: "object_key", type: "VARCHAR(512)", constraint: "UNIQUE NOT NULL", note: "不可预测的对象键" },
      { name: "original_name", type: "VARCHAR(255)", constraint: "NOT NULL", note: "展示文件名" },
      { name: "mime_type", type: "VARCHAR(64)", constraint: "NOT NULL", note: "V1 仅 application/pdf" },
      { name: "size_bytes / sha256", type: "BIGINT / CHAR(64)", constraint: "NOT NULL", note: "≤ 30 MB，校验完整性" },
      { name: "scan_status", type: "VARCHAR(16)", constraint: "NOT NULL", note: "PENDING / CLEAN / REJECTED" },
      { name: "created_at / deleted_at", type: "DATETIME", constraint: "—", note: "下架后延迟清理" },
    ],
    indexes: ["uk_material_file_object_key", "idx_material_file_material (material_id)"],
  },
];

const apis: ApiItem[] = [
  { id: "A01", module: "identity", method: "POST", path: "/api/v1/auth/register", auth: "匿名 + 限流", summary: "注册", request: "{ nickname, email?, phone?, password }", response: "{ userId, accessToken, refreshToken }", notes: "邮箱手机至少一项，密码 ≥ 8 位" },
  { id: "A02", module: "identity", method: "POST", path: "/api/v1/auth/login", auth: "匿名 + 限流", summary: "登录", request: "{ account, password }", response: "{ userId, accessToken, refreshToken }", notes: "连续 5 次失败锁定 15 分钟" },
  { id: "A03", module: "identity", method: "POST", path: "/api/v1/auth/refresh", auth: "refreshToken", summary: "刷新令牌", request: "{ refreshToken }", response: "新的令牌对", notes: "旧 refresh 立即作废" },
  { id: "A04", module: "identity", method: "POST", path: "/api/v1/auth/logout", auth: "登录", summary: "登出", request: "{ refreshToken }", response: "{ ok: true }", notes: "" },
  { id: "A05", module: "identity", method: "GET", path: "/api/v1/users/me", auth: "登录", summary: "我的信息", request: "无", response: "{ id, nickname, avatarPreset, role, practiceMode }", notes: "" },
  { id: "A06", module: "identity", method: "PATCH", path: "/api/v1/users/me", auth: "登录", summary: "改资料与偏好", request: "{ nickname?, avatarPreset?, practiceMode? }", response: "最新资料", notes: "practiceMode 控制解析可见策略" },
  { id: "A07", module: "problem", method: "GET", path: "/api/v1/tags", auth: "可匿名", summary: "知识点树", request: "无", response: "[{ id, parentId, name, slug }]", notes: "可整棵缓存" },
  { id: "A08", module: "problem", method: "GET", path: "/api/v1/problems", auth: "可匿名", summary: "搜索题目", request: "?type=SINGLE,BLANK&tagId=&difficulty=&grade=&origin=&q=&cursor=", response: "游标页题目摘要", notes: "题干与标题走 ngram 全文索引" },
  { id: "A09", module: "problem", method: "GET", path: "/api/v1/problems/{id}", auth: "可匿名", summary: "题目详情", request: "path id", response: "{ id, title, type, stemMd, options, tags, source, versionId }", notes: "不含答案与解析；versionId 供提交使用" },
  { id: "A10", module: "problem", method: "GET", path: "/api/v1/problems/{id}/explanation", auth: "按 practiceMode + 限流", summary: "解析与标准答案", request: "path id", response: "{ explanationMd, answerJson }", notes: "练习模式开启且无提交时返回 CONTENT_NOT_VISIBLE" },
  { id: "A11", module: "problem", method: "GET", path: "/api/v1/problems/{id}/similar", auth: "可匿名", summary: "相似题", request: "?limit=5", response: "[{ id, title, difficulty, sharedTagCount }]", notes: "同知识点、难度 ±1，登录时排除近期做对的题" },
  { id: "A12", module: "practice", method: "POST", path: "/api/v1/submissions", auth: "登录 + Idempotency-Key", summary: "提交作答", request: "{ problemId, problemVersionId, answer, durationMs? }", response: "{ id, result, score, maxScore, details }", notes: "版本不一致返回 PROBLEM_VERSION_STALE" },
  { id: "A13", module: "practice", method: "GET", path: "/api/v1/submissions/{id}", auth: "本人", summary: "提交详情", request: "path id", response: "答案快照与判题结果", notes: "含版本号与是否已升版" },
  { id: "A14", module: "practice", method: "GET", path: "/api/v1/me/submissions", auth: "登录", summary: "我的提交", request: "?problemId=&cursor=", response: "游标页", notes: "" },
  { id: "A15", module: "practice", method: "GET", path: "/api/v1/me/wrong-items", auth: "登录", summary: "错题本", request: "?mastered=0&tagId=&since=7d&cursor=", response: "游标页，含 versionChanged 标记", notes: "默认只看未掌握" },
  { id: "A16", module: "practice", method: "PATCH", path: "/api/v1/me/wrong-items/{problemId}", auth: "登录", summary: "标记掌握", request: "{ mastered }", response: "{ problemId, mastered }", notes: "手动覆盖自动判定" },
  { id: "A17", module: "practice", method: "GET", path: "/api/v1/me/progress", auth: "登录", summary: "知识点进度", request: "无", response: "[{ tagId, tagName, attemptCount, correctCount }]", notes: "弱项排前，供个人页图表" },
  { id: "A18", module: "quality", method: "POST", path: "/api/v1/problems/{id}/feedback", auth: "登录 + 限流", summary: "题目纠错", request: "{ reason, detail? }", response: "{ feedbackId, status: OPEN }", notes: "同一人同题未结案时返回已有工单" },
  { id: "A19", module: "admin", method: "POST", path: "/api/v1/admin/problems", auth: "ADMIN", summary: "录入或升版题目", request: "{ title, type, stemMd, answerJson, explanationMd, tagIds, source }", response: "{ id, versionNo }", notes: "已发布题内容变更自动生成新版本" },
  { id: "A20", module: "admin", method: "POST", path: "/api/v1/admin/problems/import", auth: "ADMIN", summary: "批量导入", request: "{ items: [...] }", response: "{ succeeded, failed: [{ line, reason }] }", notes: "逐行校验，失败行不阻塞其他行" },
  { id: "A21", module: "admin", method: "POST", path: "/api/v1/admin/feedback/{id}/resolve", auth: "ADMIN", summary: "处理纠错", request: "{ decision: FIXED|REJECTED, newVersion?, regrade?, remark }", response: "{ status, regradedCount }", notes: "FIXED + regrade 会回溯重判受影响的提交并写审计" },
  { id: "A22", module: "material", method: "GET", path: "/api/v1/materials", auth: "可匿名", summary: "筛选下载资料", request: "?type=&grade=&year=&cursor=", response: "游标页资料摘要", notes: "只返回 PUBLISHED，公开列表不暴露 objectKey" },
  { id: "A23", module: "material", method: "POST", path: "/api/v1/materials/{id}/download-url", auth: "登录 + 限流", summary: "申请下载地址", request: "path id", response: "{ url, expiresAt }", notes: "签名 URL 5 分钟有效，记录下载审计" },
  { id: "A24", module: "admin", method: "POST", path: "/api/v1/admin/materials/upload-ticket", auth: "ADMIN", summary: "申请资料上传凭证", request: "{ fileName, mimeType, sizeBytes, sha256 }", response: "{ objectKey, uploadUrl, expiresAt }", notes: "仅 PDF、≤ 30 MB，上传后必须完成回调与扫描" },
  { id: "A25", module: "admin", method: "POST", path: "/api/v1/admin/materials", auth: "ADMIN", summary: "登记或发布资料", request: "{ title, type, grade?, year?, originType, licenseRef?, sourceUrl?, objectKey }", response: "{ id, status }", notes: "文件 CLEAN 且来源字段完整才可 PUBLISHED" },
];

const deferred = [
  ["题解、评论、点赞", "表结构与审核队列方案已设计，V1 没有 UGC 所以整个审核模块都不需要", "日活稳定、有人愿意写题解时"],
  ["关注、粉丝、个人主页动态", "user_follow 单表双向查询方案已定", "有内容可关注之后，否则空转"],
  ["用户自建题目", "私有免审核会带来图片滥用风险，需要配额与机器审核配套", "V2 连同配额和扫描一起做"],
  ["题单与整套计时", "practice_session 表与交卷结算逻辑已设计", "题量超过 300 道、成套练习有意义时"],
  ["个人分类标签", "user_label 两表方案已设计", "V2，和收藏一起做"],
  ["微信扫码登录", "user_identity 表在 V2 建，登录页届时才出现入口", "拿到企业主体认证之后"],
];

function BulletList({ items }: { items: string[] }) {
  const theme = useHostTheme();
  return (
    <Stack gap={7}>
      {items.map((item) => (
        <div key={item}>
          <Row gap={9} align="start">
            <span style={{ color: theme.accent.primary, lineHeight: "20px" }}>•</span>
            <Text style={{ margin: 0 }}>{item}</Text>
          </Row>
        </div>
      ))}
    </Stack>
  );
}

function FilterPills<T extends string>({ items, value, onChange }: { items: readonly T[]; value: T; onChange: (v: T) => void }) {
  return (
    <Row gap={8} wrap>
      {items.map((item) => (
        <span key={item}>
          <Pill active={value === item} onClick={() => onChange(item)}>
            {item}
          </Pill>
        </span>
      ))}
    </Row>
  );
}

function ScopeView() {
  return (
    <Stack gap={16}>
      <Callout tone="success" title="V1 只验证一件事：有没有人愿意每天来做题">
        一个人能用最少的题目和最少的代码，把“筛题 → 作答 → 即时判分 → 看解析 → 错题复练”跑通。
        下载作为核心获客入口首发，但仅提供来源清晰的 PDF，并与刷题链路保持独立。
      </Callout>

      <Grid columns={2} gap={18}>
        <Stack gap={8}>
          <H3>V1 交付</H3>
          <BulletList
            items={[
              "注册登录，信息最小化，账号安全基线。",
              "按题型、知识点、难度筛题与关键词搜索。",
              "五种客观题自动判分，多空按空给分。",
              "解析默认随时可看，可开启“做完再看”。",
              "错题本按知识点和时间筛选，连对 2 次出本。",
              "知识点掌握度进度，弱项排前。",
              "题目纠错闭环：反馈 → 修正 → 重判 → 审计。",
              "管理端题目录入、版本管理与批量导入。",
              "来源清晰的真题与自编讲义 PDF，登录后签名下载。",
            ]}
          />
        </Stack>
        <Stack gap={8}>
          <H3>V1 不做</H3>
          <BulletList
            items={[
              "任何 UGC：题解、评论、点赞、收藏、用户自建题目。",
              "任何社交：关注、粉丝、动态、排行榜。",
              "任何用户文件上传，包括头像与题目配图上传入口；只有管理员能上传资料 PDF。",
              "题单、整套计时会话、个人分类标签。",
              "微信登录、推荐算法、小组、付费。",
            ]}
          />
        </Stack>
      </Grid>

      <H2>砍到这个程度之后，风险跟着消失了</H2>
      <Table
        headers={["原审查项", "V1 的处理"]}
        rows={[
          ["真题版权（阻塞）", "不分发原卷，题目按 ORIGINAL / ADAPTED / LICENSED / PUBLIC 四类登记，字段不齐不能发布"],
          ["MVP 膨胀（阻塞）", "23 条需求、13 张表、25 个接口；下载是唯一恢复到 V1 的非刷题能力"],
          ["未成年人合规（阻塞）", "只收昵称与一个联系方式，无社交无私信无上传，暴露面接近于零"],
          ["纠错闭环断裂（高）", "problem_feedback 直通修正与重判，是 V1 的核心质量能力而不是附属功能"],
          ["解析全开无退路（高）", "practice_mode 开关，默认开放，想自律的人可以要求做完再看"],
          ["图片上传滥用（高）", "V1 不开放任何用户上传，问题不存在"],
          ["资料与题目割裂（高）", "V1 下载是独立获客入口；暂不强绑题目，V2 用 collection 建专题关联，避免错误的一对一关系"],
          ["多态孤儿数据（中）", "只剩 problem_feedback 一处单一指向，无多态"],
          ["进度写放大（中）", "进度只按知识点聚合，一次提交最多更新 3 行"],
        ]}
        rowTone={["danger", "danger", "danger", "warning", "warning", "warning", "neutral", "neutral", "neutral"]}
        striped
      />

      <H2>单人全职的节奏参考</H2>
      <Table
        headers={["周次", "目标", "产出"]}
        rows={[
          ["第 1 周", "地基", "Spring Boot 骨架、Flyway 基线、认证与限流、CI、Docker Compose"],
          ["第 2 周", "题目与判题", "problem/version/tag 模型、五种 Grader、参数化单元测试"],
          ["第 3 周", "管理端", "题目录入、版本升级、批量导入、来源闸门"],
          ["第 4 周", "刷题前端", "筛选、作答、判分反馈、解析、公式与几何图渲染验证"],
          ["第 5 周", "学习数据", "错题本、进度、纠错反馈与重判"],
          ["第 6 周", "下载与内容", "对象存储签名下载、资料发布闸门、录入 100–150 道题"],
          ["第 7 周", "上线", "公式与几何图验收、移动端验证、备份、监控与恢复演练"],
        ]}
        striped
      />
      <Text tone="tertiary" size="small">
        估算前提：单人全职、已有 Spring Boot 与 Vue 经验、内容录入与开发并行。下载恢复首发后约 7 周；兼职推进请按两倍计。
      </Text>
    </Stack>
  );
}

function RequirementsView() {
  const [persona, setPersona] = useCanvasState<"全部" | "学员" | "管理员">("v1-persona", "全部");
  const visible = requirements.filter((r) => persona === "全部" || r.persona === persona);
  return (
    <Stack gap={16}>
      <FilterPills items={["全部", "学员", "管理员"] as const} value={persona} onChange={setPersona} />
      <Table
        headers={["编号", "角色", "需求", "验收标准", "优先级"]}
        rows={visible.map((r) => [r.id, r.persona, r.title, r.acceptance, r.priority])}
        rowTone={visible.map((r) => (r.priority === "P0" ? "success" : "info"))}
        striped
        stickyHeader
      />
      <Callout tone="info" title="P1 可以跟着上线，也可以下一个小版本再补">
        找回密码与批量导入不阻塞首次上线：前者可以先人工处理，后者在题量小的时候手工录入也来得及。
      </Callout>
    </Stack>
  );
}

function SchemaView() {
  const [name, setName] = useCanvasState<string>("v1-table", "problem_version");
  const selected = tables.find((t) => t.name === name) ?? tables[0];
  return (
    <Stack gap={16}>
      <Callout tone="info" title="13 张表，两条清晰链路">
        user 做题 → submission 记录作答并绑定 problem_version → 判错进 wrong_item → 聚合进 user_tag_progress。
        material → material_file 独立负责下载；problem_feedback 是反向质量通道。
      </Callout>
      <FilterPills items={tables.map((t) => t.name)} value={selected.name} onChange={setName} />
      <Card>
        <CardHeader trailing={<Text size="small">{selected.domain}</Text>}>{selected.name}</CardHeader>
        <CardBody>
          <Text>{selected.purpose}</Text>
        </CardBody>
      </Card>
      <Table
        headers={["字段", "类型", "约束", "说明"]}
        rows={selected.columns.map((c) => [c.name, c.type, c.constraint, c.note])}
        striped
        stickyHeader
      />
      <Text tone="secondary" size="small">
        索引：{selected.indexes.join(" · ")}
      </Text>
    </Stack>
  );
}

function ApiView() {
  const [id, setId] = useCanvasState<string>("v1-api", "A12");
  const selected = apis.find((a) => a.id === id) ?? apis[0];
  return (
    <Stack gap={16}>
      <Text>
        统一前缀 <Code>/api/v1</Code>。列表一律游标分页；提交带幂等键；答案与解析永远走独立接口，列表和详情都不返回。
      </Text>
      <Table
        headers={["编号", "方法", "路径", "鉴权", "说明"]}
        rows={apis.map((a) => [a.id, a.method, a.path, a.auth, a.summary])}
        striped
        stickyHeader
      />
      <Row gap={7} wrap>
        {apis.map((a) => (
          <span key={a.id}>
            <Pill size="sm" active={selected.id === a.id} onClick={() => setId(a.id)}>
              {a.id}
            </Pill>
          </span>
        ))}
      </Row>
      <Card>
        <CardHeader trailing={<Text size="small">{selected.method}</Text>}>{selected.path}</CardHeader>
        <CardBody>
          <Stack gap={8}>
            <Text>{selected.summary}</Text>
            <Text size="small">
              请求 <Code>{selected.request}</Code>
            </Text>
            <Text size="small">
              响应 <Code>{selected.response}</Code>
            </Text>
            {selected.notes ? (
              <Text tone="secondary" size="small">
                {selected.notes}
              </Text>
            ) : null}
          </Stack>
        </CardBody>
      </Card>
    </Stack>
  );
}

function ComplianceView() {
  return (
    <Stack gap={16}>
      <Callout tone="warning" title="老师能用，不等于网站能发">
        《著作权法》第二十四条第六项允许“为学校课堂教学少量复制已发表作品供教学人员使用”，但同一条明确写着
        <Text weight="semibold"> 不得出版发行</Text>。把试卷放到公开网站供任何人下载，属于信息网络传播，不在这项豁免里。
        网上 PDF 多，是因为维权成本高、平台靠“通知删除”免责，而不是因为合法——管理员自己上传的内容不适用避风港。
      </Callout>

      <Grid columns={2} gap={18}>
        <Stack gap={8}>
          <H3>受保护的是表达，不是数学</H3>
          <BulletList
            items={[
              "数学问题本身、公式、解题方法属于思想与事实，不受著作权保护。",
              "受保护的是：具体文字表述、配图、解析文字，以及整份试卷的选题与编排（汇编作品）。",
              "所以把题目内核重新表述、换数据、自己画图、自己写解析，产出的是你自己的作品。",
              "扫描原卷、整份复制、照搬解析文字，则是明确的复制与信息网络传播。",
            ]}
          />
        </Stack>
        <Stack gap={8}>
          <H3>V1 的内容来源规则</H3>
          <BulletList
            items={[
              "ORIGINAL：完全原创，可自由使用。",
              "ADAPTED：参考真题改编，必须换数据、重写表述、自写解析，并填写改编说明。",
              "PUBLIC：明确开放的来源（如 IMO 历年题、公有领域教材），填来源链接。",
              "LICENSED：拿到书面授权，填凭证编号。没有凭证不允许发布。",
            ]}
          />
        </Stack>
      </Grid>

      <Table
        headers={["做法", "风险", "V1 是否做"]}
        rows={[
          ["分发有书面授权或明确公开许可的赛事原卷 PDF", "授权范围必须包含信息网络传播", "做，凭证不全不发布"],
          ["整卷题目逐题照搬文字", "仍属复制，且汇编编排也受保护", "不做"],
          ["参考真题改编：换数据、重写表述、自写解析", "落在思想表达二分法的安全侧", "做，占主体"],
          ["完全原创题", "无风险", "做"],
          ["标注“参考 2023 某赛事”", "标注本身不免责，但体现善意且便于检索", "做，仅作标注"],
          ["分发自己撰写的讲义 PDF", "自有作品，无风险", "V1 做"],
        ]}
        rowTone={["danger", "danger", "success", "success", "neutral", "info"]}
        striped
      />

      <H2>未成年人保护：靠砍功能而不是靠补协议</H2>
      <BulletList
        items={[
          "注册只要昵称加一个联系方式，不收真实姓名、学校、生日和头像图片。",
          "V1 没有关注、私信、评论和公开主页，陌生人之间不存在互动通道。",
          "不展示排行榜与他人成绩，避免制造比较压力；计时默认关闭。",
          "隐私政策与账号注销入口在上线前必须就位，注销走匿名化而不是物理删除。",
        ]}
      />

      <Callout tone="info" title="免责说明">
        以上是基于法条原文和公开资料做的产品风险判断，不构成法律意见。真要大规模使用真题，请找专业律师确认，或直接联系赛事主办方询问授权。
      </Callout>
    </Stack>
  );
}

function DeferredView() {
  return (
    <Stack gap={16}>
      <Callout tone="success" title="砍掉不等于作废">
        这些功能的数据模型和接口设计都已经做完，留在历史版本里。V1 的表结构刻意没有挡住它们的接入路径，
        比如 problem 保留了 status 状态机，submission 保留了版本绑定，V2 加 collection 和 UGC 时不需要改现有表。
      </Callout>
      <Table
        headers={["推迟的功能", "已完成的设计", "重新启用的条件"]}
        rows={deferred}
        striped
        stickyHeader
      />
      <H2>V2 的触发条件，而不是时间表</H2>
      <BulletList
        items={[
          "题库超过 300 道且有稳定的每日作答量，再做题单与整套练习。",
          "出现用户主动写解题思路的需求，再开题解与评论，同时把审核队列一起做起来。",
          "日活稳定后再考虑社交功能；没有用户基数时，关注和粉丝只是空壳。",
        ]}
      />
    </Stack>
  );
}

export default function MathematicsMvpSpec() {
  const [tab, setTab] = useCanvasState<Tab>("v1-tab", "V1 范围");
  const dispatch = useCanvasAction();

  return (
    <Stack gap={18} style={{ padding: 24, maxWidth: 1180, margin: "0 auto" }}>
      <Stack gap={7}>
        <H1>mathematics · 最小 MVP（V1）</H1>
        <Text tone="secondary">
          23 条需求、13 张表、25 个接口。下载作为核心卖点首发，其余范围仍围绕刷题闭环收敛。
        </Text>
      </Stack>

      <Row gap={16} wrap>
        <Stat value="23" label="需求（21 个 P0）" tone="success" />
        <Stat value="13" label="数据表" />
        <Stat value="25" label="REST 接口" />
        <Stat value="7 周" label="单人全职估算" tone="info" />
      </Row>

      <Row gap={8} wrap>
        {tabs.map((item) => (
          <span key={item}>
            <Pill active={tab === item} onClick={() => setTab(item)}>
              {item}
            </Pill>
          </span>
        ))}
      </Row>

      <Divider />
      {tab === "V1 范围" && <ScopeView />}
      {tab === "需求清单" && <RequirementsView />}
      {tab === "数据表" && <SchemaView />}
      {tab === "接口" && <ApiView />}
      {tab === "内容与合规" && <ComplianceView />}
      {tab === "推迟清单" && <DeferredView />}

      <Divider />
      <Row justify="space-between" align="center" wrap gap={12}>
        <Text tone="tertiary" size="small">
          Flyway 建议拆成 identity / problem / practice / material / quality 五个版本文件。
        </Text>
        <Button
          variant="primary"
          onClick={() =>
            dispatch({
              type: "newComposerChat",
              userPrompt: "按 V1 规格生成 Flyway 建表 SQL、OpenAPI 初稿和判题器骨架代码。",
            })
          }
        >
          生成建表 SQL 与接口骨架
        </Button>
      </Row>
    </Stack>
  );
}
