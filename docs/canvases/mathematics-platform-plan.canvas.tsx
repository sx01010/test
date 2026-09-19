import {
  Button,
  Callout,
  Card,
  CardBody,
  CardHeader,
  Divider,
  Grid,
  H1,
  H2,
  H3,
  Pill,
  Row,
  Stack,
  Table,
  Text,
  useCanvasAction,
  useCanvasState,
  useHostTheme,
} from "cursor/canvas";

const productModules = [
  ["刷题闭环", "搜索筛选、作答、判题、看解析、相似题、可选计时", "V1"],
  ["学习数据", "错题本按知识点与时间筛选、知识点掌握度进度", "V1"],
  ["内容质量", "题目纠错、管理员修正升版、历史提交重判、审计", "V1"],
  ["题库运营", "管理端录入、版本管理、批量导入、来源与授权闸门", "V1"],
  ["资料下载", "来源清晰的真题与自编讲义 PDF；对象存储签名下载", "V1"],
  ["内容互动", "题解、评论、点赞、收藏、举报、审核队列", "V2"],
  ["用户关系", "个人主页、关注、粉丝与关注列表", "V2"],
  ["做题小组", "邀请成员、布置题单、进度统计、角色权限", "V3"],
];

const domains = [
  ["identity", "用户、登录、角色", "user", "V1"],
  ["problem", "题目、版本、知识点、来源与授权", "problem / problem_version / tag / problem_source", "V1"],
  ["practice", "作答、判题、提交记录、错题与进度", "submission / wrong_item / user_tag_progress", "V1"],
  ["quality", "题目纠错、修正升版、重判与审计", "problem_feedback / audit_log", "V1"],
  ["collection", "题单、题目排序与整套练习", "collection / collection_item", "V2"],
  ["material", "真题讲义、来源授权、对象存储与下载凭证", "material / material_file", "V1"],
  ["community", "题解、评论、点赞、收藏、举报、关注", "solution / comment / reaction / follow", "V2"],
  ["moderation", "审核队列、封禁、敏感内容", "review_task", "V2"],
  ["group", "小组、成员、邀请、作业与统计", "study_group / member / assignment", "V3"],
];

const roadmap = [
  ["第 0 步 · 先决", "内容来源定性", "确定原创/改编/授权比例，放弃分发赛事原卷，写清改编规则"],
  ["V1 · 约 7 周", "最小闭环 + 下载", "登录、筛题搜索、五种题型判分、解析、错题本、知识点进度、纠错重判、管理端录入、真题讲义签名下载"],
  ["V1.5 · 按需", "内容积累", "录入到 300 道题，补找回密码、批量导入、移动端与无障碍打磨"],
  ["V2 · 触发式", "内容与社区", "题单与整套计时、题解评论、收藏、个人分类、关注粉丝"],
  ["V3 · 远期", "协作与规模化", "做题小组、通知、搜索优化、缓存与容量规划"],
];

const decisions = [
  ["前端", "Vue 3 + TypeScript + Vite + Vue Router + Pinia + Element Plus", "对新手友好、中文资料多、后台与业务组件成熟"],
  ["后端", "Java 17 + Spring Boot 3.x + Spring Security + Spring Modulith", "先做模块化单体，边界清楚且部署简单"],
  ["数据", "MySQL 8 + Flyway；Redis 只做缓存/会话/限流", "核心数据以数据库为准，避免早期多数据源复杂度"],
  ["文件", "S3 兼容对象存储 + 签名 URL；只收 PDF/图片，单文件 ≤ 30MB", "真题讲义体积可控；不做视频转码和多码率存储"],
  ["接口", "REST + OpenAPI；统一错误码、游标分页、幂等键", "契约清晰，可生成 TypeScript API 客户端"],
  ["登录", "邮箱/手机密码为主；微信扫码排到 P1", "微信网站应用扫码登录要企业主体认证，个人申请不了"],
  ["部署", "Docker Compose 起步；Nginx + 单应用 + MySQL + Redis", "不要前期上微服务、Kubernetes、MQ 全家桶"],
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

function ProductView() {
  return (
    <Stack gap={16}>
      <Callout tone="info" title="产品核心不是“题库”，而是可持续的学习闭环">
        发现合适题目 → 作答 → 即时反馈 → 看思路/讨论 → 沉淀错题 → 按知识点复练。
      </Callout>
      <Table
        headers={["模块", "最小能力", "建议阶段"]}
        rows={productModules}
        rowTone={["success", "success", "success", "success", "success", "info", "info", "neutral"]}
        striped
      />
      <Grid columns={2} gap={16}>
        <Stack gap={8}>
          <H3>先定义清楚的题型</H3>
          <BulletList
            items={[
              "单选、多选、判断、数值填空：可自动判题。",
              "多空填空：按空保存答案与得分，支持顺序无关。",
              "过程题/证明题：先做人工批改；AI 只能辅助，不能直接定分。",
              "答案采用结构化 JSON，题目正文与解析支持 Markdown + LaTeX。",
            ]}
          />
        </Stack>
        <Stack gap={8}>
          <H3>必须内置的治理能力</H3>
          <BulletList
            items={[
              "往年真题记录赛事、年份、年级、轮次、来源和授权凭证。",
              "用户内容默认进入可审核状态，具备举报、下架和审计记录。",
              "若面向未成年人，默认最小化收集信息，并设计监护与隐私流程。",
              "题目版本不可静默覆盖，历史提交必须能回溯当时版本。",
            ]}
          />
        </Stack>
      </Grid>
    </Stack>
  );
}

function ArchitectureView() {
  return (
    <Stack gap={16}>
      <Callout tone="success" title="推荐：模块化单体，而不是微服务">
        一个后端应用、一个主数据库，按领域模块隔离代码与表访问。等通知或搜索出现独立扩容需求时再拆。
      </Callout>
      <Grid columns="1fr auto 1.35fr auto 1fr" gap={10} align="center">
        <Card>
          <CardHeader>Web 客户端</CardHeader>
          <CardBody>
            <Text>Vue 3 SPA</Text>
            <Text tone="secondary" size="small">题库、刷题、资料下载、创作中心</Text>
          </CardBody>
        </Card>
        <Text tone="tertiary">→</Text>
        <Card size="lg">
          <CardHeader>Spring Boot 模块化单体</CardHeader>
          <CardBody>
            <Text>REST API + 领域服务 + 后台任务</Text>
            <Divider style={{ margin: "10px 0" }} />
            <Text tone="secondary" size="small">模块间只通过公开接口/领域事件协作</Text>
          </CardBody>
        </Card>
        <Text tone="tertiary">→</Text>
        <Card>
          <CardHeader>基础设施</CardHeader>
          <CardBody>
            <Text>MySQL · Redis</Text>
            <Text tone="secondary" size="small">对象存储 · CDN · 邮件/短信</Text>
          </CardBody>
        </Card>
      </Grid>
      <H3>资料只走对象存储，明确不做视频</H3>
      <Text>
        管理员申请上传凭证 → PDF/图片直传对象存储 → 回调写入 material_file → 登录用户拿短期签名 URL 下载。
        不转码、不存多清晰度、不把文件二进制写进 MySQL。
      </Text>
      <Table
        headers={["内容形态", "单份体积", "100 份合计", "主要额外成本"]}
        rows={[
          ["1080p 一小时视频", "0.5–2 GB", "50–200 GB", "转码、多码率副本、带宽、审核"],
          ["真题试卷 PDF", "2–15 MB", "0.2–1.5 GB", "签名下载与版权校验"],
          ["讲义 PDF", "5–30 MB", "0.5–3 GB", "签名下载与版权校验"],
        ]}
        striped
      />
      <Text tone="tertiary" size="small">
        估算假设：单份取常见教研资料体积区间，非正式压测。同等份数下视频存储大约是 PDF 的 50–200 倍，还要加转码副本。
      </Text>
      <Table headers={["技术决策", "推荐", "理由"]} rows={decisions} striped />
    </Stack>
  );
}

function ModelView() {
  return (
    <Stack gap={14}>
      <Text>
        每个模块拥有自己的实体和应用服务，禁止 Controller 直接拼 SQL，也禁止跨模块随意访问 Mapper。
      </Text>
      <Table headers={["领域模块", "职责", "关键实体/表", "阶段"]} rows={domains} striped stickyHeader />
      <Callout tone="warning" title="三个容易返工的模型点">
        题目与题目版本分离；题库与题目是多对多且带排序；提交记录保存题目版本、答案快照、判题结果和耗时。
      </Callout>
      <Grid columns={3} gap={12}>
        <Card collapsible defaultOpen>
          <CardHeader>可见性</CardHeader>
          <CardBody>
            <Text size="small">PUBLIC / PRIVATE / GROUP。权限在服务层统一校验，不散落在查询条件里。</Text>
          </CardBody>
        </Card>
        <Card collapsible defaultOpen>
          <CardHeader>内容状态</CardHeader>
          <CardBody>
            <Text size="small">DRAFT → REVIEWING → PUBLISHED → HIDDEN；删除优先软删除并保留审计。</Text>
          </CardBody>
        </Card>
        <Card collapsible defaultOpen>
          <CardHeader>判题扩展</CardHeader>
          <CardBody>
            <Text size="small">Grader 接口按题型注册实现，未来新增分数容差、单位换算或人工评分无需改主流程。</Text>
          </CardBody>
        </Card>
      </Grid>
    </Stack>
  );
}

function RoadmapView() {
  return (
    <Stack gap={16}>
      <Table headers={["阶段", "目标", "交付范围"]} rows={roadmap} rowTone={["danger", "success", "info", "info", "neutral"]} striped />
      <H3>V1 明确不做</H3>
      <BulletList
        items={[
          "不分发来源不清的赛事原卷 PDF；只有自有、明确公开许可或获得书面授权的资料才能发布。",
          "不做任何 UGC 与社交：题解、评论、收藏、关注、用户自建题目全部推后。",
          "不开放任何用户文件上传，包括头像和题目配图。",
          "不做视频、推荐算法、付费、排行榜和原生 App。",
          "不做微服务、Kubernetes 和消息中间件全家桶。",
        ]}
      />
      <Callout tone="info" title="首个可验收目标">
        100–150 道来源清晰、答案与解析齐全的题，以及首批有授权凭证的真题与自编讲义 PDF。用户能筛题、作答、复练和签名下载；
        发现题目有错能提交纠错，管理员修正后历史提交会被重判。
      </Callout>
    </Stack>
  );
}

function QualityView() {
  return (
    <Stack gap={16}>
      <Grid columns={2} gap={18}>
        <Stack gap={8}>
          <H3>质量门槛</H3>
          <BulletList
            items={[
              "核心判题器做参数化单元测试；提交、权限、题库发布做集成测试。",
              "Testcontainers 启动真实 MySQL/Redis；Flyway 迁移必须可重复验证。",
              "前端 TypeScript 严格模式；关键刷题路径做 Playwright E2E。",
              "CI 执行格式检查、测试、依赖漏洞扫描与 Docker 镜像构建。",
            ]}
          />
        </Stack>
        <Stack gap={8}>
          <H3>稳定性与运维</H3>
          <BulletList
            items={[
              "结构化日志 + traceId；Actuator + Micrometer 指标与健康检查。",
              "提交、点赞、邀请等写操作提供幂等保护；通知失败可重试。",
              "限流覆盖登录、提交、评论、上传凭证；Redis 故障时核心读写可降级。",
              "数据库每日备份并定期恢复演练；对象存储开启生命周期与防盗链。",
            ]}
          />
        </Stack>
      </Grid>
      <Callout tone="warning" title="上线前红线">
        来源字段没填齐的题不允许发布；不分发赛事原卷；审核链路没建起来之前不开放任何 UGC；用户侧不开放文件上传。
      </Callout>
    </Stack>
  );
}

const tabs = ["产品边界", "系统架构", "领域模型", "分期路线", "质量与风险"] as const;
type Tab = (typeof tabs)[number];

export default function MathematicsPlatformPlan() {
  const [tab, setTab] = useCanvasState<Tab>("active-section", "产品边界");
  const dispatch = useCanvasAction();

  return (
    <Stack gap={18} style={{ padding: 24, maxWidth: 1180, margin: "0 auto" }}>
      <Stack gap={7}>
        <H1>mathematics · 产品技术方案</H1>
        <Text tone="secondary">
          V1 以筛题、作答、判分、解析和错题复练为主链路，并将来源清晰的真题讲义下载作为核心卖点首发。
        </Text>
      </Stack>

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
      {tab === "产品边界" && <ProductView />}
      {tab === "系统架构" && <ArchitectureView />}
      {tab === "领域模型" && <ModelView />}
      {tab === "分期路线" && <RoadmapView />}
      {tab === "质量与风险" && <QualityView />}

      <Divider />
      <Row justify="space-between" align="center" wrap gap={12}>
        <Text tone="tertiary" size="small">
          参考 LeetCode 的题单、练习、题解与社区闭环，但不复制其复杂度。
        </Text>
        <Button
          variant="primary"
          onClick={() =>
            dispatch({
              type: "newComposerChat",
              userPrompt: "按 V1 最小 MVP 规格生成 Flyway 建表 SQL、OpenAPI 初稿和判题器骨架代码。",
            })
          }
        >
          生成 V1 建表与接口骨架
        </Button>
      </Row>
    </Stack>
  );
}
