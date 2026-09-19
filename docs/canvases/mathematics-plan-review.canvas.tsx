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
  Stat,
  Table,
  Text,
  useCanvasAction,
  useCanvasState,
  useHostTheme,
} from "cursor/canvas";

type Level = "阻塞" | "高" | "中";
type Area = "范围与合规" | "产品设计" | "数据模型" | "工程实现" | "前端";

interface Finding {
  id: string;
  level: Level;
  area: Area;
  topic: string;
  problem: string;
  advice: string;
}

const findings: Finding[] = [
  {
    id: "F01",
    level: "阻塞",
    area: "范围与合规",
    topic: "真题版权没有真正落实",
    problem:
      "方案里 license_type / license_ref 只是字段，填了不等于拿到授权。华杯赛、迎春杯这类赛事真题的版权归主办方，个人项目几乎不可能拿到批量分发授权，而试卷 PDF 下载正是侵权认定最直接的形态。",
    advice:
      "上线前先确定内容来源：只做原创与改编题、只做题目讲解不分发原卷，或逐份拿到书面授权。这一条会直接改变产品形态，越早定越好。",
  },
  {
    id: "F02",
    level: "阻塞",
    area: "范围与合规",
    topic: "MVP 在持续膨胀，6–8 周已不现实",
    problem:
      "最初的 MVP 是“筛题 → 作答 → 判分 → 错题本”。现在叠加了关注粉丝、个人主页、雷达图、个人分类、整套计时、题解抽屉、资料下载和审核后台，共 32 条需求、26 张表、55 个接口。单人开发这个体量，阶段 1 更接近 3–4 个月。",
    advice:
      "切出真正的首版：刷题闭环 + 解析 + 错题本 + 资料下载。关注粉丝、题解评论、用户自建题目公开流程全部推到第二版，先验证有没有人真的每天来做题。",
  },
  {
    id: "F03",
    level: "阻塞",
    area: "范围与合规",
    topic: "未成年人合规仍未决策",
    problem:
      "目标用户是中小学生，但注册流程按成人产品设计：收集邮箱手机号、开放关注与评论、允许上传内容。缺少监护人同意、年龄标识和信息最小化，属于上线红线而不是优化项。",
    advice:
      "要么按最小化方案做（昵称+密码，不收真实姓名和学校，关闭陌生人私信），要么正式设计监护人同意流程。两者都要配隐私政策。",
  },
  {
    id: "F04",
    level: "高",
    area: "产品设计",
    topic: "题目纠错闭环是断的",
    problem:
      "举报理由第一项是“答案或解析有误”，但 review_task 的结果只有 APPROVED / REJECTED / HIDDEN，没有“需要修正”的路径。谁改题、改完要不要重判历史提交、要不要通知做错的人，规格里全是空白。奥数题答案错是高频事件，这个闭环断了运营会很痛。",
    advice:
      "补一个 NEEDS_FIX 状态：审核通过后生成新 problem_version，按旧版本回溯受影响的 submission，批量重判并给受影响用户发通知。",
  },
  {
    id: "F05",
    level: "高",
    area: "产品设计",
    topic: "解析全局开放没有留退路",
    problem:
      "解析随时可看降低了卡住的挫败感，但也让学生可以不思考直接看答案，题目数据还更容易被整站抓走。限流挡得住脚本，挡不住慢速爬虫。",
    advice:
      "保留默认开放，但加一个「练习模式」开关（自己或家长设定：提交后才显示解析）。这样既不劝退，也给想认真练的人一个自律工具。",
  },
  {
    id: "F06",
    level: "高",
    area: "工程实现",
    topic: "私有题目免审核 + 图片直传 = 免费图床",
    problem:
      "学员自建私有题目不走审核，同时可以申请 PROBLEM_IMAGE 上传凭证。这等于允许任何注册用户往你的对象存储写图片且不经任何检查，会被当图床滥用，也可能存进违法内容。",
    advice:
      "私有题目图片也必须：按用户配额限制（如每天 20 张、累计 200MB）、只能通过签名 URL 读取、接入机器识别做异步扫描，命中即冻结并转人工。",
  },
  {
    id: "F07",
    level: "高",
    area: "产品设计",
    topic: "资料和题目是两套割裂的内容",
    problem:
      "用户下载了「2023 华杯赛初赛试卷」，平台里同时也有这套题的电子版，但两者没有任何关联。数据模型里 material 和 problem 之间没有关系表，用户体验上也没有“这份试卷的题目去在线做”的入口。",
    advice:
      "加一张 material_problem 关联表，或者让 material 直接挂一个 collection。试卷详情页给出“在线练这套题”，练习页给出“下载原卷”，两边互相导流。",
  },
  {
    id: "F08",
    level: "高",
    area: "数据模型",
    topic: "题目改版后，历史记录会对不上",
    problem:
      "submission 绑定了 problem_version_id，这部分是对的。但错题本、收藏、个人分类都只存 problem_id。题目改版后用户回到错题本，看到的是新版题面，和他当时做错的可能已经不是同一道题。",
    advice:
      "错题本和提交历史展示时对比版本号，不一致就标注“此题已更新”，并提供查看当时版本的入口。",
  },
  {
    id: "F09",
    level: "中",
    area: "数据模型",
    topic: "错题本的移出阈值没定义",
    problem:
      "需求写的是“连续做对或手动标记 mastered 后移出”，界面上写的是“连续做对 2 次”，表里有 consecutive_correct 字段但没有阈值常量。三处口径不一致，实现时必然要回来问。",
    advice:
      "定死为连续做对 2 次自动 mastered，并把阈值做成配置项，后续可调。",
  },
  {
    id: "F10",
    level: "中",
    area: "数据模型",
    topic: "PARTIAL 算不算错题没写",
    problem:
      "多空题部分正确返回 PARTIAL。需求只说“判错自动入本”，没说部分正确怎么处理。原型里按进错题本实现，但这只是我的默认选择。",
    advice:
      "明确 PARTIAL 进错题本，因为部分对恰恰说明有知识点没掌握，正是需要复习的情况。",
  },
  {
    id: "F11",
    level: "中",
    area: "工程实现",
    topic: "幂等键的生成规则没定义",
    problem:
      "submission 上有 (user_id, idempotency_key) 唯一索引，但没规定 key 怎么生成。如果前端复用同一个 key，用户第二次作答同一题会被当成重复提交直接拒绝。",
    advice:
      "规定每次进入题目由前端生成一个 UUID，提交成功后作废；重试用同一个 key，重新作答换新 key。写进接口文档而不是靠口头约定。",
  },
  {
    id: "F12",
    level: "中",
    area: "数据模型",
    topic: "封禁用户后的关注计数未定义",
    problem:
      "规格写“封禁后已有关注关系保留但不可再互动”。那被封用户还算不算别人的粉丝、他的关注还算不算数、他的粉丝数要不要显示，都没写。这类边界最后往往靠实现时随手决定，然后前后端对不上。",
    advice:
      "定一条规则：封禁用户从各类计数和列表中隐藏，但 user_follow 行保留，解封后自动恢复。",
  },
  {
    id: "F13",
    level: "中",
    area: "数据模型",
    topic: "多态关联没有外键，会留孤儿数据",
    problem:
      "reaction、favorite、report、review_task 都用 target_type + target_id 指向不同表，数据库层没法加外键。题目或题解被硬删后，这些行会变成指向不存在对象的孤儿数据。",
    advice:
      "坚持软删是第一道防线；再加一个每日清理任务扫描孤儿行。MVP 不要为此引入复杂的领域事件机制。",
  },
  {
    id: "F14",
    level: "中",
    area: "工程实现",
    topic: "一次提交会写放大成多行更新",
    problem:
      "user_progress 按 PROBLEM / COLLECTION / TAG 三种维度聚合，而一道题可能挂 3 个知识点。一次提交最多要更新 5 行以上，全部塞在提交事务里会让核心接口变慢。",
    advice:
      "提交事务里只写 submission 和 wrong_item，progress 用 Spring Modulith 的领域事件异步更新，允许秒级延迟。",
  },
  {
    id: "F15",
    level: "中",
    area: "工程实现",
    topic: "整套练习中途题目被下架",
    problem:
      "practice_session 在开始时固化了 problem_ids_json。如果管理员在会话进行中隐藏了其中一题，用户做到那题会直接报错，整场练习卡死。",
    advice:
      "提交时遇到已下架题目，跳过并标记为“题目已下架，本题不计分”，结算时从总分里扣除该题满分。",
  },
  {
    id: "F16",
    level: "中",
    area: "工程实现",
    topic: "题目图片的生命周期没人管",
    problem:
      "题干用 Markdown 引用对象存储里的图片，但题目删除或改版后，旧图片不会被清理，存储只增不减，也无法判断某张图还有没有被引用。",
    advice:
      "上传时记录 object_key 与 problem_version 的引用关系，配一个定期任务清理无引用对象，并开启存储桶生命周期规则。",
  },
  {
    id: "F17",
    level: "高",
    area: "前端",
    topic: "公式与几何图这一关还没验证",
    problem:
      "原型里的题干是纯文本。真实奥数题有分数、根号、方程组，几何题必须配图。KaTeX 渲染、图片缩放、移动端公式换行都没试过，这部分的工作量容易被低估。",
    advice:
      "尽早用 10 道真实题做渲染验证，把最复杂的几何题和多行公式先跑通，再决定编辑器方案。",
  },
  {
    id: "F18",
    level: "中",
    area: "前端",
    topic: "移动端只做了断点，没做真实验证",
    problem:
      "中小学生大量用手机和平板，但原型只加了 CSS 断点。小屏下作答区、抽屉题解、雷达图和筛选栏的实际体验都没验证过。",
    advice:
      "把手机视口当成主要场景之一验证一轮，尤其是作答区和提交按钮的可达性。",
  },
  {
    id: "F19",
    level: "中",
    area: "前端",
    topic: "键盘与无障碍完全没做",
    problem:
      "颜色对比度处理过了，但选项是 div 不是按钮，没有 tab 顺序、焦点环和 aria 标签，键盘用户无法作答，读屏软件也读不出题目结构。",
    advice:
      "把选项改成 button 或加 role/tabindex，给作答区补焦点样式。做题类产品用键盘选 A/B/C/D 本身也更快。",
  },
];

const levels: Array<Level | "全部"> = ["全部", "阻塞", "高", "中"];
const toneOf = (l: Level) => (l === "阻塞" ? "danger" : l === "高" ? "warning" : "neutral");

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

export default function MathematicsPlanReview() {
  const [level, setLevel] = useCanvasState<Level | "全部">("review-level", "全部");
  const [openId, setOpenId] = useCanvasState<string>("review-open", "F01");
  const dispatch = useCanvasAction();

  const visible = findings.filter((f) => level === "全部" || f.level === level);
  const selected = visible.find((f) => f.id === openId) ?? visible[0];
  const count = (l: Level) => findings.filter((f) => f.level === l).length;

  return (
    <Stack gap={18} style={{ padding: 24, maxWidth: 1180, margin: "0 auto" }}>
      <Stack gap={7}>
        <H1>mathematics · 方案问题审查</H1>
        <Text tone="secondary">
          历史审查基线。问题已在当前 MVP 规格中逐项收口，本页保留原始风险与决策依据，避免后续扩展时重新踩坑。
        </Text>
      </Stack>

      <Callout tone="success" title="当前最小 MVP 已完成口径修复">
        错题连续做对 2 次出本、PARTIAL 入错题本、每次新作答生成新幂等键、错题保存并展示当时版本；
        公式使用 KaTeX、几何图使用响应式 SVG 进入第 7 周上线验收。下载恢复首发，但只允许来源凭证完整且扫描通过的 PDF。
      </Callout>

      <Callout tone="danger" title="最需要现在就决定的一件事：真题从哪来">
        整个产品的内容基础是往年真题，但个人项目拿到赛事真题的分发授权非常困难，而试卷 PDF 下载恰恰是侵权认定最直接的形态。
        这件事会决定产品形态——做原创题库、做讲解而不分发原卷、还是逐份取得授权——所以应该排在所有技术工作前面。
      </Callout>

      <Row gap={16} wrap>
        <Stat value={String(count("阻塞"))} label="阻塞项" tone="danger" />
        <Stat value={String(count("高"))} label="高优先级" tone="warning" />
        <Stat value={String(count("中"))} label="待定义细节" />
        <Stat value="23 / 13 / 25" label="当前需求 / 表 / 接口" />
      </Row>

      <Divider />

      <Row gap={8} wrap>
        {levels.map((l) => (
          <span key={l}>
            <Pill active={level === l} onClick={() => setLevel(l)}>
              {l === "全部" ? "全部" : l + "（" + count(l as Level) + "）"}
            </Pill>
          </span>
        ))}
      </Row>

      <Table
        headers={["编号", "分类", "问题", "建议动作"]}
        rows={visible.map((f) => [f.id, f.area, f.topic, f.advice.slice(0, 28) + "…"])}
        rowTone={visible.map((f) => toneOf(f.level))}
        striped
        stickyHeader
      />

      <Row gap={7} wrap>
        {visible.map((f) => (
          <span key={f.id}>
            <Pill size="sm" active={selected.id === f.id} onClick={() => setOpenId(f.id)}>
              {f.id}
            </Pill>
          </span>
        ))}
      </Row>

      <Card>
        <CardHeader trailing={<Text size="small">{selected.level}</Text>}>
          {selected.id} · {selected.topic}
        </CardHeader>
        <CardBody>
          <Stack gap={10}>
            <Text>{selected.problem}</Text>
            <Divider />
            <Text tone="secondary">建议：{selected.advice}</Text>
          </Stack>
        </CardBody>
      </Card>

      <Divider />

      <H2>范围建议：把首版再砍一刀</H2>
      <Grid columns={2} gap={18}>
        <Stack gap={8}>
          <H3>第一个可上线版本保留</H3>
          <BulletList
            items={[
              "注册登录、按题型和知识点筛题。",
              "作答、自动判分、解析、错题本、简单进度。",
              "真题讲义下载（前提是版权问题已解决）。",
              "举报入口与最小审核后台。",
            ]}
          />
        </Stack>
        <Stack gap={8}>
          <H3>推到第二版</H3>
          <BulletList
            items={[
              "关注、粉丝、个人主页与雷达图。",
              "题解、评论、点赞。",
              "用户自建题目的公开与审核流程。",
              "整套计时会话与练习记录。",
            ]}
          />
        </Stack>
      </Grid>
      <Callout tone="info" title="为什么建议砍">
        社交与创作功能的价值依赖用户规模，而冷启动阶段既没有用户也没有内容，这些模块会空转。
        先用最小闭环验证“有人愿意每天来做题”，再决定要不要投入社区。砍掉的部分数据模型已经设计好，随时能接上。
      </Callout>

      <Divider />
      <Row justify="space-between" align="center" wrap gap={12}>
        <Text tone="tertiary" size="small">
          审查对象：产品技术方案、MVP 规格、前端交互原型（截至当前版本）。
        </Text>
        <Button
          variant="primary"
          onClick={() =>
            dispatch({
              type: "newComposerChat",
              userPrompt: "按这份审查清单，先把阻塞项和高优先级问题的方案改掉。",
            })
          }
        >
          按清单修订方案
        </Button>
      </Row>
    </Stack>
  );
}
