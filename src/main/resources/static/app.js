/* ============================================================
   mathematics · V1 前端单页
   判分、错题归档、知识点统计全部走服务端；这里只负责渲染与状态。
   ============================================================ */

const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];

const TYPE_NAME = { SINGLE: '单选', MULTI: '多选', JUDGE: '判断', NUMERIC: '填空', BLANK: '多空填空' };
const LEVEL_NAME = { 2: '入门', 3: '进阶', 4: '挑战' };
const ORIGIN_NAME = { ORIGINAL: '原创', ADAPTED: '改编', LICENSED: '已授权', PUBLIC: '公开来源' };
const RESULT_NAME = { CORRECT: '回答正确', PARTIAL: '部分正确', WRONG: '回答错误' };
const STATUS_NAME = { DRAFT: '草稿', PUBLISHED: '已发布', HIDDEN: '已隐藏' };
const KEYS = ['A', 'B', 'C', 'D', 'E', 'F'];
// 来源类型决定发布前还要补哪一个字段，和后端 AdminProblemService 的闸门一一对应
const SOURCE_EXTRA = {
  ORIGINAL: null,
  ADAPTED: ['rewriteNote', '改编说明', '写清改了哪些数据与表述。发布前必填。'],
  LICENSED: ['licenseRef', '授权凭证', '授权编号或凭证文件 key。发布前必填。'],
  PUBLIC: ['sourceUrl', '公开来源链接', '能追溯到原始出处的链接。发布前必填。']
};
const REASONS = [
  ['ANSWER_ERROR', '答案或解析有误'],
  ['TYPO', '错别字或排版问题'],
  ['UNCLEAR', '题意不清'],
  ['OTHER', '其他问题']
];

const S = {
  me: null,
  tags: [],
  filter: { types: new Set(), difficulty: null, tagId: null, q: '' },
  list: [], nextCursor: null,
  current: null,
  drafts: {},        // problemId -> 作答草稿
  results: {},       // problemId -> 最近一次判题结果
  idempotencyKeys: {},
  timerMode: 'PER_PROBLEM', seconds: 0, tick: null,
  today: { done: 0, correct: 0 },
  wrongFilter: { mastered: '0', since: 'all', tagId: null },
  admin: { editingId: null }
};

/* ========================= 基础工具 ========================= */

let toastTimer;
function toast(message) {
  const box = $('#toast');
  box.textContent = message;
  box.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => box.classList.remove('show'), 2800);
}

function openModal(html) {
  const mask = $('#mask');
  mask.hidden = false;
  mask.innerHTML = `<div class="modal">${html}</div>`;
  mask.onclick = event => {
    if (event.target === mask || event.target.hasAttribute('data-close')) closeModal();
  };
}
function closeModal() {
  $('#mask').hidden = true;
  $('#mask').innerHTML = '';
}
document.addEventListener('keydown', event => { if (event.key === 'Escape') closeModal(); });

function esc(text) {
  return String(text ?? '').replace(/[&<>"]/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[ch]));
}

/**
 * 题干与解析是 Markdown + LaTeX。这里只支持粗体、段落和无序列表，
 * 公式交给 KaTeX；先转义再替换，避免题库内容里的 HTML 被执行。
 */
function md(text) {
  if (!text) return '';
  return esc(text).split(/\n{2,}/).map(block => {
    const lines = block.split('\n').filter(line => line.trim() !== '');
    if (lines.length && lines.every(line => line.trim().startsWith('- '))) {
      return `<ul>${lines.map(line => `<li>${inline(line.trim().slice(2))}</li>`).join('')}</ul>`;
    }
    return `<p>${lines.map(inline).join('<br>')}</p>`;
  }).join('');
}
function inline(text) {
  return text.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
}

function typeset(root) {
  if (window.renderMathInElement) {
    window.renderMathInElement(root || document.body, {
      delimiters: [{ left: '\\(', right: '\\)', display: false }, { left: '\\[', right: '\\]', display: true }],
      throwOnError: false
    });
  }
}

function uuid() {
  return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function formatSeconds(total) {
  const minutes = String(Math.floor(total / 60)).padStart(2, '0');
  return `${minutes}:${String(total % 60).padStart(2, '0')}`;
}

function formatTime(iso) {
  if (!iso) return '';
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString('zh-CN', { hour12: false });
}

function isToday(iso) {
  if (!iso) return false;
  const date = new Date(iso);
  const now = new Date();
  return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth() && date.getDate() === now.getDate();
}

/* ========================= 接口调用 ========================= */

const TOKEN_KEY = 'mathematics-tokens';

function tokens() {
  try {
    return JSON.parse(localStorage.getItem(TOKEN_KEY)) || null;
  } catch {
    return null;
  }
}
function saveTokens(pair) {
  localStorage.setItem(TOKEN_KEY, JSON.stringify(pair));
}
function clearTokens() {
  localStorage.removeItem(TOKEN_KEY);
}

class ApiError extends Error {
  constructor(status, code, message) {
    super(message || code || `HTTP ${status}`);
    this.status = status;
    this.code = code;
  }
}

/**
 * access token 过期时用 refresh token 换一对新的再重试一次。
 * 服务端会立即作废旧 refresh token，所以这里必须把新的一对存回去。
 */
async function api(path, options = {}) {
  const { method = 'GET', body, headers = {}, retryOnUnauthorized = true } = options;
  const pair = tokens();
  const requestHeaders = { ...headers };
  if (body !== undefined) requestHeaders['Content-Type'] = 'application/json';
  if (pair?.accessToken) requestHeaders.Authorization = `Bearer ${pair.accessToken}`;

  const response = await fetch(`/api/v1${path}`, {
    method,
    headers: requestHeaders,
    body: body === undefined ? undefined : JSON.stringify(body)
  });

  if (response.status === 401 && retryOnUnauthorized && pair?.refreshToken) {
    const refreshed = await refreshTokens(pair.refreshToken);
    if (refreshed) return api(path, { ...options, retryOnUnauthorized: false });
  }

  if (response.status === 204) return null;

  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(response.status, payload?.code, payload?.message);
  }
  return payload;
}

async function refreshTokens(refreshToken) {
  const response = await fetch('/api/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken })
  });
  if (!response.ok) {
    clearTokens();
    S.me = null;
    syncAuth();
    return false;
  }
  saveTokens(await response.json());
  return true;
}

/* ========================= 主题与视图 ========================= */

$$('#themeSwitch button').forEach(button => {
  button.onclick = () => {
    $$('#themeSwitch button').forEach(other => other.classList.remove('on'));
    button.classList.add('on');
    document.body.dataset.theme = button.dataset.themeBtn;
    localStorage.setItem('mathematics-theme', button.dataset.themeBtn);
  };
});
const savedTheme = localStorage.getItem('mathematics-theme');
if (savedTheme) {
  document.body.dataset.theme = savedTheme;
  $$('#themeSwitch button').forEach(button => button.classList.toggle('on', button.dataset.themeBtn === savedTheme));
}

const VIEWS = ['problems', 'me', 'admin'];
function go(view) {
  VIEWS.forEach(name => { $(`#view-${name}`).hidden = name !== view; });
  $$('#nav a').forEach(link => link.classList.toggle('on', link.dataset.view === view));
  if (view === 'me') loadMyPractice();
  if (view === 'admin') refreshAdminList();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}
$$('#nav a').forEach(link => { link.onclick = () => go(link.dataset.view); });

/* ========================= 登录注册 ========================= */

function syncAuth() {
  $('#btnLogin').hidden = !!S.me;
  $('#avatar').hidden = !S.me;
  if (S.me) $('#avatar').textContent = S.me.nickname.slice(0, 1);

  // 管理入口只对 ADMIN 显示；后端每个管理接口仍然会自己校验角色，这里只是别让人白点
  const isAdmin = S.me?.role === 'ADMIN';
  $('#navAdmin').hidden = !isAdmin;
  if (!isAdmin && !$('#view-admin').hidden) go('problems');
}

$('#btnLogin').onclick = () => openAuthModal('login');
$('#avatar').onclick = () => openSettingsModal();

function openAuthModal(mode) {
  const isLogin = mode === 'login';
  openModal(`
    <h3>${isLogin ? '登录 mathematics' : '注册 mathematics'}</h3>
    <p>只需要昵称和一个联系方式，不收集真实姓名、学校或年龄</p>
    ${isLogin ? '' : '<div class="field"><label>昵称</label><input id="authNickname" maxlength="32" placeholder="怎么称呼你"></div>'}
    <div class="field"><label>邮箱${isLogin ? '或手机号' : ''}</label><input id="authAccount" placeholder="demo@mathematics.local"></div>
    <div class="field"><label>密码（至少 8 位）</label><input id="authPassword" type="password" placeholder="demo12345"></div>
    <div class="divider-text">${isLogin ? '还没有账号' : '已经有账号'}</div>
    <button class="btn" style="width:100%" id="authSwitch">${isLogin ? '去注册' : '去登录'}</button>
    <div class="modal-foot">
      <button class="btn" data-close>取消</button>
      <button class="btn btn-primary" id="authSubmit">${isLogin ? '登录' : '注册并登录'}</button>
    </div>`);

  $('#authSwitch').onclick = () => openAuthModal(isLogin ? 'register' : 'login');
  $('#authSubmit').onclick = async () => {
    const account = $('#authAccount').value.trim();
    const password = $('#authPassword').value;
    if (!account || !password) return toast('账号和密码都要填');
    try {
      const pair = isLogin
        ? await api('/auth/login', { method: 'POST', body: { account, password } })
        : await api('/auth/register', {
            method: 'POST',
            body: {
              nickname: $('#authNickname').value.trim() || account.split('@')[0],
              email: account.includes('@') ? account : null,
              phone: account.includes('@') ? null : account,
              password
            }
          });
      saveTokens(pair);
      await loadMe();
      closeModal();
      toast(`欢迎，${S.me.nickname}`);
      await Promise.all([loadStats(), refreshSimilar()]);
    } catch (error) {
      toast(error.message);
    }
  };
}

async function loadMe() {
  if (!tokens()?.accessToken) {
    S.me = null;
    syncAuth();
    return;
  }
  try {
    S.me = await api('/users/me');
  } catch {
    clearTokens();
    S.me = null;
  }
  syncAuth();
}

function openSettingsModal() {
  const me = S.me;
  openModal(`
    <h3>我的设置</h3>
    <p>练习模式打开后，没提交过的题看不到解析</p>
    <div class="field"><label>昵称</label><input id="setNickname" maxlength="32" value="${esc(me.nickname)}"></div>
    <div class="radio-list">
      <div class="radio ${me.practiceMode ? '' : 'on'}" data-practice="0"><i></i>随时可以看解析</div>
      <div class="radio ${me.practiceMode ? 'on' : ''}" data-practice="1"><i></i>做完才看解析（练习模式）</div>
    </div>
    <div class="divider-text">账号</div>
    <button class="btn" style="width:100%" id="doLogout">退出登录</button>
    <div class="modal-foot">
      <button class="btn" data-close>取消</button>
      <button class="btn btn-primary" id="saveSettings">保存</button>
    </div>`);

  $$('#mask .radio').forEach(radio => {
    radio.onclick = () => {
      $$('#mask .radio').forEach(other => other.classList.remove('on'));
      radio.classList.add('on');
    };
  });
  $('#doLogout').onclick = async () => {
    const pair = tokens();
    if (pair?.refreshToken) {
      await api('/auth/logout', { method: 'POST', body: { refreshToken: pair.refreshToken } }).catch(() => {});
    }
    clearTokens();
    S.me = null;
    S.results = {};
    S.today = { done: 0, correct: 0 };
    syncAuth();
    closeModal();
    updateTodayPanel();
    toast('已退出登录');
  };
  $('#saveSettings').onclick = async () => {
    const practiceMode = $('#mask .radio.on').dataset.practice === '1';
    try {
      S.me = await api('/users/me', {
        method: 'PATCH',
        body: { nickname: $('#setNickname').value.trim() || null, practiceMode }
      });
      syncAuth();
      closeModal();
      toast(practiceMode ? '已开启练习模式' : '已关闭练习模式');
    } catch (error) {
      toast(error.message);
    }
  };
}

/* ========================= 筛选与列表 ========================= */

$('#typeFilter').onchange = event => {
  S.filter.types.clear();
  if (event.target.value) S.filter.types.add(event.target.value);
  refreshList();
};
$$('[data-difficulty]').forEach(button => {
  button.insertAdjacentHTML('afterbegin', levelIcon(Number(button.dataset.difficulty)));
  button.onclick = () => {
    const value = Number(button.dataset.difficulty);
    S.filter.difficulty = S.filter.difficulty === value ? null : value;
    $$('[data-difficulty]').forEach(other => {
      const on = Number(other.dataset.difficulty) === S.filter.difficulty;
      other.classList.toggle('on', on);
      other.setAttribute('aria-pressed', String(on));
    });
    refreshList();
  };
});

let searchTimer;
$('#kw').oninput = event => {
  S.filter.q = event.target.value.trim();
  clearTimeout(searchTimer);
  searchTimer = setTimeout(refreshList, 300);
};

async function loadTags() {
  S.tags = await api('/tags');
  const options = S.tags.map(tag => `<option value="${tag.id}">${esc(tag.name)}</option>`).join('');
  $('#tagFilter').insertAdjacentHTML('beforeend', options);
  $('#apTags').innerHTML = options;
  $('#tagFilter').onchange = event => {
    S.filter.tagId = event.target.value ? Number(event.target.value) : null;
    refreshList();
  };

  const wrongTags = $('#wrongTagFilters');
  wrongTags.innerHTML = S.tags
    .map(tag => `<span class="chip" data-tag="${tag.id}">${esc(tag.name)}</span>`)
    .join('');
  wrongTags.querySelectorAll('[data-tag]').forEach(chip => {
    chip.onclick = () => {
    const value = Number(chip.dataset.tag);
    const turningOff = S.wrongFilter.tagId === value;
    $('#wrongTagFilters').querySelectorAll('[data-tag]').forEach(other => other.classList.remove('on'));
    S.wrongFilter.tagId = turningOff ? null : value;
    if (!turningOff) chip.classList.add('on');
    refreshWrongItems();
    };
  });
}

function searchParams(cursor) {
  const params = new URLSearchParams();
  if (S.filter.types.size) params.set('type', [...S.filter.types].join(','));
  if (S.filter.difficulty) params.set('difficulty', S.filter.difficulty);
  if (S.filter.tagId) params.set('tagId', S.filter.tagId);
  if (S.filter.q) params.set('q', S.filter.q);
  if (cursor) params.set('cursor', cursor);
  params.set('limit', '10');
  return params.toString();
}

/** 筛选条件变化时用这个，接口出错只提示不炸控制台。 */
function refreshList() {
  reloadList().catch(error => toast(error.message));
}

async function reloadList() {
  const page = await api(`/problems?${searchParams(null)}`);
  S.list = page.items;
  S.nextCursor = page.nextCursor;
  renderList();
  if (!S.current || !S.list.some(item => item.id === S.current.id)) {
    if (S.list.length) await openProblem(S.list[0].id);
    else $('#solve').innerHTML = '<div class="empty">没有符合条件的题目，试试放宽筛选</div>';
  }
}

$('#btnMore').onclick = async () => {
  const page = await api(`/problems?${searchParams(S.nextCursor)}`);
  S.list = S.list.concat(page.items);
  S.nextCursor = page.nextCursor;
  renderList();
};

function renderList() {
  $('#listCount').textContent = `共 ${S.list.length} 题${S.nextCursor ? '（还有更多）' : ''}`;
  $('#btnMore').hidden = !S.nextCursor;
  $('#list').innerHTML = S.list.map((item, index) => {
    const result = S.results[item.id];
    const state = !result ? ['new', '未作答']
      : result.result === 'CORRECT' ? ['ok', '已做对']
      : result.result === 'PARTIAL' ? ['wait', '部分正确'] : ['bad', '做错了'];
    return `<article class="card row ${S.current && S.current.id === item.id ? 'active' : ''}" data-id="${item.id}">
      <span class="row-idx">${String(index + 1).padStart(2, '0')}</span>
      <div class="row-main">
        <div class="row-title">${esc(item.title)}</div>
        <div class="row-sub">${item.tags.map(tag => esc(tag.name)).join('、') || '未分类'} ·
          ${TYPE_NAME[item.type] || item.type} · ${LEVEL_NAME[item.difficulty] || item.difficulty} ·
          ${ORIGIN_NAME[item.originType] || item.originType || '来源待登记'}</div>
      </div>
      <span class="state ${state[0]}">${state[1]}</span>
    </article>`;
  }).join('') || '<div class="card empty">没有符合条件的题目，试试放宽筛选</div>';

  $$('#list .row').forEach(row => {
    row.onclick = () => openProblem(Number(row.dataset.id));
  });
}

/* ========================= 作答 ========================= */

function levelIcon(level) {
  const bars = [[1, 9, 6], [6.2, level >= 3 ? 5.5 : 9, level >= 3 ? 9.5 : 6], [11.4, level >= 4 ? 2 : 9, level >= 4 ? 13 : 6]];
  return `<svg class="lv-icon" viewBox="0 0 16 16" style="color:var(--second)">${bars
    .map((bar, index) => `<rect x="${bar[0]}" y="${bar[1]}" width="3.6" height="${bar[2]}" rx="1.2" opacity="${index < level - 1 ? 1 : .2}"/>`)
    .join('')}</svg>`;
}

async function openProblem(problemId) {
  try {
    S.current = await api(`/problems/${problemId}`);
  } catch (error) {
    return toast(error.message);
  }
  if (!S.idempotencyKeys[problemId]) S.idempotencyKeys[problemId] = uuid();
  renderProblem();
  renderList();
  refreshSimilar();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function renderProblem() {
  const problem = S.current;
  const source = problem.source;
  const sourceLabel = source
    ? [ORIGIN_NAME[source.originType] || source.originType, source.contestName, source.year, source.round]
        .filter(Boolean).join(' · ')
    : '来源待登记';

  $('#solve').innerHTML = `
    <div class="meta">
      <span class="tag src">${esc(sourceLabel)}</span>
      ${problem.tags.map(tag => `<span class="tag">${esc(tag.name)}</span>`).join('')}
      <span class="tag">${esc(problem.grade)}</span>
      <span class="tag">${TYPE_NAME[problem.type] || problem.type}</span>
      <span class="chip" style="cursor:default">${levelIcon(problem.difficulty)}${LEVEL_NAME[problem.difficulty] || ''}</span>
      <span class="tag">v${problem.versionNo} · 满分 ${problem.maxScore}</span>
    </div>
    <h1 class="q">${esc(problem.title)}</h1>
    <div class="stem" id="stem">${md(problem.stemMd)}</div>
    <div class="answer-zone" id="answerZone"></div>
    <div class="bubble" id="bubble">解析随时可看；开了练习模式就要先提交一次。</div>
    <div id="resultBox"></div>
    <div class="solve-foot">
      <span class="timer" id="timer">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
          <circle cx="12" cy="13" r="8"/><path d="M12 9.5V13l2.5 1.5M9 2h6"/>
        </svg>
        <span id="clock">00:00</span>
      </span>
      <button class="btn" id="btnExplain">查看解析</button>
      <button class="btn" id="btnReport">纠错</button>
      <button class="btn btn-primary push" id="btnSubmit">提交答案</button>
    </div>`;

  typeset($('#stem'));
  renderAnswerZone(problem);
  $('#btnExplain').onclick = showExplanation;
  $('#btnReport').onclick = openFeedbackModal;
  $('#btnSubmit').onclick = submitAnswer;

  const result = S.results[problem.id];
  if (result) renderResult(result, false);
  startTimer(true);
}

function draft(problemId) {
  if (!S.drafts[problemId]) S.drafts[problemId] = {};
  return S.drafts[problemId];
}

function renderAnswerZone(problem) {
  const zone = $('#answerZone');
  const saved = draft(problem.id);
  const locked = !!S.results[problem.id];

  if (problem.type === 'SINGLE' || problem.type === 'MULTI') {
    const options = problem.options || [];
    zone.innerHTML = `<div class="opts two-col">${options.map(option =>
      `<div class="opt" data-key="${esc(option.key)}"><span class="opt-key">${esc(option.key)}</span>
        <span>${md(option.textMd).replace(/^<p>|<\/p>$/g, '')}</span></div>`).join('')}</div>`;
    const picked = new Set(problem.type === 'SINGLE' ? (saved.choice ? [saved.choice] : []) : (saved.choices || []));
    zone.querySelectorAll('.opt').forEach(option => {
      option.classList.toggle('on', picked.has(option.dataset.key));
      option.onclick = () => {
        if (locked) return;
        if (problem.type === 'SINGLE') {
          zone.querySelectorAll('.opt').forEach(other => other.classList.remove('on'));
          option.classList.add('on');
          saved.choice = option.dataset.key;
        } else {
          option.classList.toggle('on');
          saved.choices = [...zone.querySelectorAll('.opt.on')].map(selected => selected.dataset.key);
        }
      };
    });
    typeset(zone);
    return;
  }

  if (problem.type === 'JUDGE') {
    zone.innerHTML = `<div class="opts two-col">
      <div class="opt" data-value="true"><span class="opt-key">√</span><span>正确</span></div>
      <div class="opt" data-value="false"><span class="opt-key">×</span><span>错误</span></div></div>`;
    zone.querySelectorAll('.opt').forEach(option => {
      option.classList.toggle('on', String(saved.value) === option.dataset.value);
      option.onclick = () => {
        if (locked) return;
        zone.querySelectorAll('.opt').forEach(other => other.classList.remove('on'));
        option.classList.add('on');
        saved.value = option.dataset.value === 'true';
      };
    });
    return;
  }

  if (problem.type === 'NUMERIC') {
    zone.innerHTML = `<div class="blank-row"><label>答案</label>
      <input class="fill" id="numericInput" placeholder="填入数值" value="${esc(saved.value ?? '')}"></div>`;
    $('#numericInput').disabled = locked;
    $('#numericInput').oninput = event => { saved.value = event.target.value.trim(); };
    return;
  }

  // BLANK：详情里的 blankCount 只告诉前端有几个空，答案仍然只在解析接口里
  const count = problem.blankCount || 1;
  saved.blanks = saved.blanks || new Array(count).fill('');
  zone.innerHTML = Array.from({ length: count }, (unused, index) =>
    `<div class="blank-row"><label>第 ${index + 1} 空</label>
      <input class="fill" data-blank="${index}" value="${esc(saved.blanks[index] ?? '')}"></div>`).join('');
  zone.querySelectorAll('[data-blank]').forEach(input => {
    input.disabled = locked;
    input.oninput = () => {
      saved.blanks[Number(input.dataset.blank)] = input.value.trim();
    };
  });
}

function buildAnswer(problem) {
  const saved = draft(problem.id);
  switch (problem.type) {
    case 'SINGLE':
      return saved.choice ? { choice: saved.choice } : null;
    case 'MULTI':
      return saved.choices && saved.choices.length ? { choices: saved.choices } : null;
    case 'JUDGE':
      return typeof saved.value === 'boolean' ? { value: saved.value } : null;
    case 'NUMERIC':
      return saved.value ? { value: saved.value } : null;
    case 'BLANK':
      return (saved.blanks || []).some(value => value && value.trim())
        ? { blanks: (saved.blanks || []).map(value => (value || '').trim()) } : null;
    default:
      return null;
  }
}

async function submitAnswer() {
  const problem = S.current;
  if (!S.me) {
    toast('先登录才能提交作答');
    return openAuthModal('login');
  }
  if (S.results[problem.id]) return nextProblem();

  const answer = buildAnswer(problem);
  if (!answer) return toast('请先作答再提交');

  $('#btnSubmit').disabled = true;
  try {
    const result = await api('/submissions', {
      method: 'POST',
      headers: { 'Idempotency-Key': S.idempotencyKeys[problem.id] },
      body: {
        problemId: problem.id,
        problemVersionId: problem.versionId,
        answer,
        durationMs: S.timerMode === 'NONE' ? null : S.seconds * 1000
      }
    });
    S.results[problem.id] = result;
    stopTimer();
    renderResult(result, true);
    renderList();
    await Promise.all([loadStats(), refreshSimilar()]);
  } catch (error) {
    if (error.code === 'PROBLEM_VERSION_STALE') {
      toast('这道题刚更新过，已为你载入新版本');
      await openProblem(problem.id);
    } else {
      toast(error.message);
    }
  } finally {
    $('#btnSubmit').disabled = false;
  }
}

function nextProblem() {
  const index = S.list.findIndex(item => item.id === S.current.id);
  const next = S.list[index + 1];
  if (!next) return toast('这一页的题做完了，换个筛选或加载更多');
  openProblem(next.id);
}

function renderResult(result, fresh) {
  const problem = S.current;
  const tone = result.result === 'CORRECT' ? 'ok' : result.result === 'PARTIAL' ? 'wait' : 'bad';
  const blanks = result.details?.blanks;
  const detailHtml = blanks
    ? blanks.map(item => `第 ${item.index + 1} 空：${item.correct ? '✓' : '✗'}`).join('　')
    : `得分 ${result.score} / ${result.maxScore}`;

  $('#bubble').hidden = true;
  $('#resultBox').innerHTML = `
    <div class="result ${tone}">
      <div class="result-head">
        <span class="result-title">${RESULT_NAME[result.result] || result.result}</span>
        <span class="state ${tone}">${result.score} / ${result.maxScore}</span>
        <span class="result-score">${S.timerMode === 'NONE' ? '未计时' : '用时 ' + formatSeconds(S.seconds)}</span>
      </div>
      <div class="result-detail">${detailHtml}</div>
      <div class="result-actions">
        <button class="btn" id="resExplain">查看解析</button>
        <button class="btn" id="resRetry">重新作答</button>
        <button class="btn btn-primary" id="resNext">下一题</button>
      </div>
      <div id="explainBox"></div>
    </div>`;

  $('#resExplain').onclick = showExplanation;
  $('#resNext').onclick = nextProblem;
  $('#resRetry').onclick = () => {
    delete S.results[problem.id];
    S.drafts[problem.id] = {};
    // 重新作答换新 key；只有网络重试才复用旧的，这样重做会被判成新的一次提交
    S.idempotencyKeys[problem.id] = uuid();
    renderProblem();
  };

  markObjectiveOptions(result);
  $('#btnSubmit').textContent = '下一题';
  if (fresh && result.result !== 'CORRECT') toast('这道题已自动加入错题本');
  if (fresh && result.result === 'CORRECT') toast('答对了，继续保持');
}

/**
 * 客观题提交后把正确选项标出来。标准答案来自解析接口，
 * 没开练习模式就直接取；开了练习模式此刻也已经提交过，同样能取到。
 */
async function markObjectiveOptions(result) {
  const problem = S.current;
  if (problem.type !== 'SINGLE' && problem.type !== 'MULTI' && problem.type !== 'JUDGE') return;
  let answer;
  try {
    answer = (await api(`/problems/${problem.id}/explanation`)).answerJson;
  } catch {
    return;
  }
  const standard = problem.type === 'SINGLE' ? [answer.choice]
    : problem.type === 'MULTI' ? answer.choices : [String(answer.value)];
  $$('#answerZone .opt').forEach(option => {
    const value = option.dataset.key || option.dataset.value;
    if (standard.includes(value)) option.classList.add('right');
    else if (option.classList.contains('on')) option.classList.add('wrong');
  });
}

async function showExplanation() {
  const problem = S.current;
  try {
    const payload = await api(`/problems/${problem.id}/explanation`);
    const target = $('#explainBox') || $('#resultBox');
    target.innerHTML = `<div class="explain"><h4>解析</h4>${md(payload.explanationMd)}
      <p class="muted" style="font-size:13px;margin-top:10px">标准答案：<code>${esc(JSON.stringify(payload.answerJson))}</code></p></div>`;
    $('#bubble').hidden = true;
    typeset(target);
  } catch (error) {
    if (error.code === 'CONTENT_NOT_VISIBLE') {
      toast('你开启了练习模式，先提交一次再看解析');
    } else {
      toast(error.message);
    }
  }
}

function openFeedbackModal() {
  if (!S.me) {
    toast('登录后才能提交纠错');
    return openAuthModal('login');
  }
  openModal(`
    <h3>内容纠错</h3>
    <p>「${esc(S.current.title)}」· 同一道题未处理完只会保留一条工单</p>
    <div class="radio-list" id="reasonList">
      ${REASONS.map(([code, label], index) =>
        `<div class="radio ${index === 0 ? 'on' : ''}" data-reason="${code}"><i></i>${label}</div>`).join('')}
    </div>
    <div class="field" style="margin-top:12px"><label>补充说明（可选，最长 500 字）</label>
      <input id="feedbackDetail" maxlength="500" placeholder="比如第 2 空的标准答案应该是 30"></div>
    <div class="modal-foot">
      <button class="btn" data-close>取消</button>
      <button class="btn btn-primary" id="sendFeedback">提交</button>
    </div>`);

  $$('#reasonList .radio').forEach(radio => {
    radio.onclick = () => {
      $$('#reasonList .radio').forEach(other => other.classList.remove('on'));
      radio.classList.add('on');
    };
  });
  $('#sendFeedback').onclick = async () => {
    try {
      const created = await api(`/problems/${S.current.id}/feedback`, {
        method: 'POST',
        body: {
          reason: $('#reasonList .radio.on').dataset.reason,
          detail: $('#feedbackDetail').value.trim() || null
        }
      });
      closeModal();
      toast(created.reused ? '你之前提的工单还在处理中，已合并到那一条' : '收到，工单已进入处理队列');
    } catch (error) {
      toast(error.message);
    }
  };
}

/* ========================= 相似题 ========================= */

async function refreshSimilar() {
  if (!S.current) return;
  const list = await api(`/problems/${S.current.id}/similar?limit=5`).catch(() => []);
  $('#recList').innerHTML = list.length
    ? list.map(item => `<a data-id="${item.id}"><span class="dot"></span>${esc(item.title)}
        <small>${LEVEL_NAME[item.difficulty] || ''}</small></a>`).join('')
    : '<p class="hint">暂时没有合适的相似题，换道题看看。</p>';
  $$('#recList a').forEach(link => { link.onclick = () => openProblem(Number(link.dataset.id)); });
}

/* ========================= 计时 ========================= */

$$('#mode .chip').forEach(chip => {
  chip.onclick = () => {
    $$('#mode .chip').forEach(other => other.classList.remove('on'));
    chip.classList.add('on');
    S.timerMode = chip.dataset.mode;
    $('#modeHint').textContent = S.timerMode === 'NONE'
      ? '不计时：界面不显示计时器，提交时不上报耗时。'
      : '单题计时：每换一道题重新计时，只随提交上报耗时，不影响判分。';
    startTimer(true);
  };
});

function startTimer(reset) {
  stopTimer();
  const timer = $('#timer');
  if (!timer) return;
  if (S.timerMode === 'NONE') {
    timer.hidden = true;
    return;
  }
  timer.hidden = false;
  if (reset) S.seconds = 0;
  $('#clock').textContent = formatSeconds(S.seconds);
  S.tick = setInterval(() => {
    S.seconds += 1;
    const clock = $('#clock');
    if (clock) clock.textContent = formatSeconds(S.seconds);
  }, 1000);
}
function stopTimer() {
  clearInterval(S.tick);
  S.tick = null;
}

/* ========================= 统计与我的练习 ========================= */

async function loadStats() {
  if (!S.me) {
    S.today = { done: 0, correct: 0 };
    updateTodayPanel(0);
    return;
  }
  const [submissions, wrongItems] = await Promise.all([
    api('/me/submissions?limit=50').catch(() => ({ items: [] })),
    api('/me/wrong-items?mastered=0&limit=50').catch(() => ({ items: [] }))
  ]);
  const todays = submissions.items.filter(item => isToday(item.createdAt));
  S.today = { done: todays.length, correct: todays.filter(item => item.result === 'CORRECT').length };
  updateTodayPanel(wrongItems.items.length);
}

function updateTodayPanel(wrongCount) {
  const { done, correct } = S.today;
  $('#doneNum').textContent = done;
  $('#rate').textContent = done ? `${Math.round((correct / done) * 100)}%` : '—';
  if (wrongCount !== undefined) $('#wrongNum').textContent = wrongCount;
  $('#ring').setAttribute('stroke-dashoffset', 283 - 283 * Math.min(done / 20, 1));
}

async function loadMyPractice() {
  if (!S.me) {
    $('#wrongList').innerHTML = '<div class="card empty">登录后这里会显示你的错题本和知识点掌握度</div>';
    $('#submissionList').innerHTML = '';
    $('#progressPanel').innerHTML = '<p class="hint">登录后可以看到按知识点聚合的正确率。</p>';
    return;
  }
  await Promise.all([loadWrongItems(), loadProgress(), loadRecentSubmissions()]);
}

$$('#wrongFilters [data-wf]').forEach(chip => {
  chip.onclick = () => {
    const group = chip.dataset.wf;
    $$(`#wrongFilters [data-wf="${group}"]`).forEach(other => other.classList.remove('on'));
    chip.classList.add('on');
    S.wrongFilter[group] = chip.dataset.v;
    refreshWrongItems();
  };
});

function refreshWrongItems() {
  loadWrongItems().catch(error => toast(error.message));
}

async function loadWrongItems() {
  if (!S.me) return;
  const params = new URLSearchParams({ mastered: S.wrongFilter.mastered, since: S.wrongFilter.since, limit: '20' });
  if (S.wrongFilter.tagId) params.set('tagId', S.wrongFilter.tagId);
  const page = await api(`/me/wrong-items?${params}`);

  $('#wrongCount').textContent = `共 ${page.items.length} 题${page.nextCursor ? '（还有更多）' : ''}`;
  $('#meWrong').textContent = S.wrongFilter.mastered === '0' ? page.items.length : $('#meWrong').textContent;
  $('#wrongList').innerHTML = page.items.map(item => `
    <article class="card row">
      <span class="row-idx">${item.wrongCount}</span>
      <div class="row-main">
        <div class="row-title">${esc(item.title)}</div>
        <div class="row-sub">最近做错 ${formatTime(item.lastWrongAt)} · 连续做对 ${item.consecutiveCorrect} 次${
          item.versionChanged ? ' · 这道题已更新，当时做的是旧版本' : ''}</div>
      </div>
      <button class="btn" data-open="${item.problemId}">去重做</button>
      <button class="btn" data-mastered="${item.problemId}" data-to="${item.mastered ? '0' : '1'}">${
        item.mastered ? '移回待复习' : '标记已掌握'}</button>
    </article>`).join('') || `<div class="card empty">${
      S.wrongFilter.mastered === '0' ? '还没有待复习的错题，做错的题会自动收进来' : '还没有标记为已掌握的题'}</div>`;

  $$('#wrongList [data-open]').forEach(button => {
    button.onclick = async () => {
      await openProblem(Number(button.dataset.open));
      go('problems');
    };
  });
  $$('#wrongList [data-mastered]').forEach(button => {
    button.onclick = async () => {
      try {
        await api(`/me/wrong-items/${button.dataset.mastered}`, {
          method: 'PATCH',
          body: { mastered: button.dataset.to === '1' }
        });
        toast(button.dataset.to === '1' ? '已标记为掌握' : '已移回待复习');
        await Promise.all([loadWrongItems(), loadStats()]);
      } catch (error) {
        toast(error.message);
      }
    };
  });
}

async function loadProgress() {
  const rows = await api('/me/progress');
  $('#meTags').textContent = rows.length;
  $('#progressPanel').innerHTML = rows.length
    ? rows.map(row => {
        const rate = Math.round((row.correctCount / row.attemptCount) * 100);
        return `<div class="bar-row">
          <span class="name">${esc(row.tagName)}</span>
          <span class="bar"><i style="width:${rate}%"></i></span>
          <span class="bar-val">${rate}% · ${row.correctCount}/${row.attemptCount}</span>
        </div>`;
      }).join('')
    : '<p class="hint">做几道题之后这里会按知识点显示正确率。</p>';
}

async function loadRecentSubmissions() {
  const page = await api('/me/submissions?limit=10');
  const todays = page.items.filter(item => isToday(item.createdAt));
  $('#meDone').textContent = todays.length;
  $('#meRate').textContent = todays.length
    ? `${Math.round((todays.filter(item => item.result === 'CORRECT').length / todays.length) * 100)}%` : '—';

  $('#submissionList').innerHTML = page.items.map(item => {
    const tone = item.result === 'CORRECT' ? 'ok' : item.result === 'PARTIAL' ? 'wait' : 'bad';
    return `<article class="card row" data-open="${item.problemId}">
      <span class="row-idx">${item.score}</span>
      <div class="row-main">
        <div class="row-title">${esc(item.title)}</div>
        <div class="row-sub">${formatTime(item.createdAt)} · 作答版本 v${item.versionNo}${
          item.versionChanged ? '（题目此后有更新）' : ''}${
          item.durationMs != null ? ' · 用时 ' + formatSeconds(Math.round(item.durationMs / 1000)) : ' · 未计时'}</div>
      </div>
      <span class="state ${tone}">${RESULT_NAME[item.result] || item.result}</span>
    </article>`;
  }).join('') || '<div class="card empty">还没有提交记录</div>';

  $$('#submissionList [data-open]').forEach(row => {
    row.onclick = async () => {
      await openProblem(Number(row.dataset.open));
      go('problems');
    };
  });
}

/* ========================= 管理端录入 ========================= */

/**
 * R16–R17 的录入表单。选项与答案控件随题型变化，所以这一块由 JS 渲染；
 * 校验只挡明显的空字段，语义校验（答案自检、来源闸门）全部交给后端，
 * 前后端各写一套规则最后一定会对不上。
 */
function setupAdmin() {
  $('#apType').onchange = () => {
    const next = $('#apType').value;
    const previous = $('#apAnswerArea').dataset.renderedType;
    const isChoice = kind => kind === 'SINGLE' || kind === 'MULTI';
    // 单选与多选的选项结构完全一样，互相切换时不该把已经填好的选项清掉
    const carry = isChoice(previous) && isChoice(next) ? collectAdminAnswer(previous) : null;
    if (carry && next === 'MULTI' && carry.answerJson?.choice) {
      carry.answerJson = { choices: [carry.answerJson.choice] };
    }
    renderAdminAnswerArea(next, carry);
  };
  $('#apOrigin').onchange = () => renderSourceExtra($('#apOrigin').value, null);
  $('#apStatusFilter').onchange = refreshAdminList;
  $('#apReset').onclick = resetAdminForm;
  $('#apSaveDraft').onclick = () => saveAdminProblem(false);
  $('#apPublish').onclick = () => saveAdminProblem(true);
  resetAdminForm();
}

function optionRow(type, key, textMd, checked) {
  const control = type === 'SINGLE'
    ? `<input type="radio" name="apCorrect" data-option-correct ${checked ? 'checked' : ''}
              aria-label="把选项 ${key} 设为正确答案">`
    : `<input type="checkbox" data-option-correct ${checked ? 'checked' : ''}
              aria-label="把选项 ${key} 设为正确答案">`;
  return `<div class="opt-edit-row" data-option-row data-key="${key}">
    <span class="opt-key">${key}</span>
    <input class="fill" data-option-text value="${esc(textMd)}" aria-label="选项 ${key} 的内容">
    ${control}
  </div>`;
}

function blankRow(index, aliases) {
  const value = Array.isArray(aliases) ? aliases.join(',') : String(aliases ?? '');
  return `<div class="blank-row">
    <label for="apBlank${index}">第 ${index + 1} 空</label>
    <input class="fill" id="apBlank${index}" data-blank-input value="${esc(value)}">
  </div>`;
}

function renderAdminAnswerArea(type, prefill) {
  const area = $('#apAnswerArea');
  const answer = prefill?.answerJson || {};
  const config = prefill?.graderConfig || {};
  area.dataset.renderedType = type;

  if (type === 'SINGLE' || type === 'MULTI') {
    const options = prefill?.options?.length
      ? prefill.options
      : [{ key: 'A', textMd: '' }, { key: 'B', textMd: '' }, { key: 'C', textMd: '' }, { key: 'D', textMd: '' }];
    const correct = new Set(type === 'SINGLE' ? [answer.choice].filter(Boolean) : (answer.choices || []));
    area.innerHTML = `<fieldset class="opt-edit">
      <legend>选项与正确答案</legend>
      <div id="apOptionRows">${options
        .map(option => optionRow(type, option.key, option.textMd, correct.has(option.key))).join('')}</div>
      <button type="button" class="btn" id="apAddOption">添加选项</button>
      <p class="field-hint">${type === 'SINGLE' ? '单选只能勾一个正确答案。' : '多选可以勾多个正确答案。'}留空的选项不会保存。</p>
    </fieldset>`;
    $('#apAddOption').onclick = () => {
      const used = $$('#apOptionRows [data-option-row]').length;
      if (used >= KEYS.length) return toast(`最多 ${KEYS.length} 个选项`);
      $('#apOptionRows').insertAdjacentHTML('beforeend', optionRow(type, KEYS[used], '', false));
    };
    return;
  }

  if (type === 'JUDGE') {
    area.innerHTML = `<fieldset class="opt-edit">
      <legend>标准答案</legend>
      <label class="inline-choice">
        <input type="radio" name="apJudge" data-judge value="true" ${answer.value === true ? 'checked' : ''}>正确
      </label>
      <label class="inline-choice">
        <input type="radio" name="apJudge" data-judge value="false" ${answer.value === false ? 'checked' : ''}>错误
      </label>
    </fieldset>`;
    return;
  }

  if (type === 'NUMERIC') {
    area.innerHTML = `<fieldset class="opt-edit">
      <legend>标准答案</legend>
      <div class="blank-row">
        <label for="apNumericValue">答案</label>
        <input class="fill" id="apNumericValue" value="${esc(answer.value ?? '')}">
      </div>
      <div class="blank-row">
        <label for="apTolerance">容差</label>
        <input class="fill" id="apTolerance" value="${esc(config.tolerance ?? '')}">
      </div>
      <p class="field-hint">容差按绝对误差比较，留空表示必须完全相等。</p>
    </fieldset>`;
    return;
  }

  const blanks = answer.blanks?.length ? answer.blanks : [['']];
  area.innerHTML = `<fieldset class="opt-edit">
    <legend>每个空的标准答案</legend>
    <div id="apBlankRows">${blanks.map((aliases, index) => blankRow(index, aliases)).join('')}</div>
    <button type="button" class="btn" id="apAddBlank">添加一个空</button>
    <label class="inline-choice" style="margin-left:12px">
      <input type="checkbox" id="apOrderIndependent" ${config.orderIndependent ? 'checked' : ''}>不计空的先后顺序
    </label>
    <p class="field-hint">一个空有多种写法时用逗号隔开，例如 <code>16,十六</code>，学生写中哪一个都算对。</p>
  </fieldset>`;
  $('#apAddBlank').onclick = () => {
    const index = $$('#apBlankRows [data-blank-input]').length;
    $('#apBlankRows').insertAdjacentHTML('beforeend', blankRow(index, ['']));
  };
}

function renderSourceExtra(originType, prefill) {
  const spec = SOURCE_EXTRA[originType];
  const field = $('#apSourceExtraField');
  if (!spec) {
    field.hidden = true;
    field.innerHTML = '';
    return;
  }
  const [key, label, hint] = spec;
  field.hidden = false;
  field.innerHTML = `<label for="apSourceExtra">${label}</label>
    <textarea id="apSourceExtra" rows="2" data-source-key="${key}">${esc(prefill?.[key] ?? '')}</textarea>
    <p class="field-hint">${hint}</p>`;
}

function collectAdminSource() {
  const source = {
    originType: $('#apOrigin').value,
    contestName: $('#apContest').value.trim() || null,
    year: $('#apYear').value ? Number($('#apYear').value) : null,
    round: $('#apRound').value.trim() || null
  };
  const extra = $('#apSourceExtra');
  if (extra) source[extra.dataset.sourceKey] = extra.value.trim() || null;
  return source;
}

function collectAdminAnswer(type) {
  if (type === 'SINGLE' || type === 'MULTI') {
    const rows = $$('#apAnswerArea [data-option-row]');
    const options = rows
      .map(row => ({ key: row.dataset.key, textMd: row.querySelector('[data-option-text]').value.trim() }))
      .filter(option => option.textMd);
    const picked = rows
      .filter(row => row.querySelector('[data-option-correct]').checked)
      .map(row => row.dataset.key)
      .filter(key => options.some(option => option.key === key));
    const answerJson = type === 'SINGLE'
      ? (picked.length === 1 ? { choice: picked[0] } : null)
      : (picked.length ? { choices: picked } : null);
    return { options, answerJson, graderConfig: null };
  }

  if (type === 'JUDGE') {
    const picked = $('#apAnswerArea [data-judge]:checked');
    return { options: null, answerJson: picked ? { value: picked.value === 'true' } : null, graderConfig: null };
  }

  if (type === 'NUMERIC') {
    const value = $('#apNumericValue').value.trim();
    const tolerance = $('#apTolerance').value.trim();
    return {
      options: null,
      answerJson: value ? { value } : null,
      graderConfig: tolerance ? { tolerance: Number(tolerance) } : null
    };
  }

  const blanks = $$('#apAnswerArea [data-blank-input]')
    .map(input => input.value.split(/[,，]/).map(alias => alias.trim()).filter(Boolean))
    .filter(aliases => aliases.length);
  return {
    options: null,
    answerJson: blanks.length ? { blanks } : null,
    graderConfig: { orderIndependent: $('#apOrderIndependent').checked }
  };
}

function collectAdminPayload(publish) {
  const type = $('#apType').value;
  const tagIds = [...$('#apTags').selectedOptions].map(option => Number(option.value));
  const { options, answerJson, graderConfig } = collectAdminAnswer(type);

  const title = $('#apTitle').value.trim();
  const stemMd = $('#apStem').value.trim();
  const explanationMd = $('#apExplanation').value.trim();
  if (!title) throw new Error('标题不能为空');
  if (!stemMd) throw new Error('题干不能为空');
  if (!explanationMd) throw new Error('解析必填，V1 要求自己撰写');
  if (!tagIds.length) throw new Error('至少选一个知识点');
  if (!answerJson) throw new Error(type === 'SINGLE' ? '请勾选唯一的正确答案' : '请把标准答案填完整');

  return {
    id: S.admin.editingId,
    title,
    type,
    difficulty: Number($('#apDifficulty').value),
    grade: $('#apGrade').value.trim(),
    stemMd,
    options,
    answerJson,
    explanationMd,
    graderConfig,
    maxScore: Number($('#apMaxScore').value) || 100,
    tagIds,
    source: collectAdminSource(),
    changeNote: $('#apChangeNote').value.trim() || null,
    publish
  };
}

/** 提交失败后把焦点移到错误摘要：只弹 toast 的话，读屏用户和键盘用户根本不知道发生了什么。 */
function showAdminError(message) {
  const box = $('#adminError');
  box.hidden = false;
  box.textContent = message;
  box.focus();
}

function hideAdminError() {
  const box = $('#adminError');
  box.hidden = true;
  box.textContent = '';
}

async function saveAdminProblem(publish) {
  let payload;
  try {
    payload = collectAdminPayload(publish);
  } catch (error) {
    return showAdminError(error.message);
  }

  const button = publish ? $('#apPublish') : $('#apSaveDraft');
  button.disabled = true;
  try {
    const saved = await api('/admin/problems', { method: 'POST', body: payload });
    S.admin.editingId = saved.id;
    hideAdminError();
    markAdminEditing(saved.id, saved.versionNo, saved.status);
    toast(saved.status === 'PUBLISHED' ? `已发布，当前版本 v${saved.versionNo}` : '草稿已保存');
    await loadAdminList();
  } catch (error) {
    showAdminError(error.message);
  } finally {
    button.disabled = false;
  }
}

function markAdminEditing(id, versionNo, status) {
  $('#apEditing').textContent = `正在编辑 #${id} · v${versionNo} · ${STATUS_NAME[status] || status}`;
}

function refreshAdminList() {
  loadAdminList().catch(error => toast(error.message));
}

async function loadAdminList() {
  const status = $('#apStatusFilter').value;
  const rows = await api(`/admin/problems${status ? `?status=${status}` : ''}`);
  $('#apList').innerHTML = rows.length
    ? rows.map(row => `<a data-admin-id="${row.id}">
        <span class="state ${row.status === 'PUBLISHED' ? 'ok' : 'new'}">${STATUS_NAME[row.status] || row.status}</span>
        <span class="admin-row-title">${esc(row.title)}</span>
        <small>v${row.versionNo ?? 1} · ${TYPE_NAME[row.type] || row.type} · ${(row.updatedAt || '').slice(5, 16)}</small>
      </a>`).join('')
    : '<p class="hint">还没有符合条件的题目，填完表单保存草稿就会出现在这里。</p>';

  $$('#apList [data-admin-id]').forEach(link => {
    link.onclick = () => openAdminProblem(Number(link.dataset.adminId));
  });
}

async function openAdminProblem(id) {
  let detail;
  try {
    detail = await api(`/admin/problems/${id}`);
  } catch (error) {
    return toast(error.message);
  }

  S.admin.editingId = detail.id;
  $('#apTitle').value = detail.title;
  $('#apType').value = detail.type;
  $('#apDifficulty').value = String(detail.difficulty);
  $('#apGrade').value = detail.grade;
  $('#apMaxScore').value = detail.maxScore;
  $('#apStem').value = detail.stemMd;
  $('#apExplanation').value = detail.explanationMd;
  $('#apChangeNote').value = '';
  [...$('#apTags').options].forEach(option => {
    option.selected = (detail.tagIds || []).includes(Number(option.value));
  });

  const source = detail.source || { originType: 'ORIGINAL' };
  $('#apOrigin').value = source.originType;
  $('#apContest').value = source.contestName || '';
  $('#apYear').value = source.year ?? '';
  $('#apRound').value = source.round || '';
  renderSourceExtra(source.originType, source);
  renderAdminAnswerArea(detail.type, detail);

  hideAdminError();
  markAdminEditing(detail.id, detail.versionNo, detail.status);
  $('#apTitle').focus();
}

function resetAdminForm() {
  S.admin.editingId = null;
  ['apTitle', 'apStem', 'apExplanation', 'apChangeNote', 'apContest', 'apYear', 'apRound']
    .forEach(id => { $(`#${id}`).value = ''; });
  $('#apGrade').value = '六年级';
  $('#apMaxScore').value = '100';
  $('#apType').value = 'SINGLE';
  $('#apDifficulty').value = '3';
  $('#apOrigin').value = 'ORIGINAL';
  [...$('#apTags').options].forEach(option => { option.selected = false; });
  renderSourceExtra('ORIGINAL', null);
  renderAdminAnswerArea('SINGLE', null);
  $('#apEditing').textContent = '新建题目';
  hideAdminError();
}

/* ========================= 启动 ========================= */

(async function boot() {
  syncAuth();
  try {
    await loadMe();
    await loadTags();
    setupAdmin();
    await reloadList();
    await loadStats();
  } catch (error) {
    $('#solve').innerHTML = `<div class="empty">接口没连上：${esc(error.message)}<br>
      确认后端已启动（默认 http://localhost:8080）。</div>`;
  }
})();
