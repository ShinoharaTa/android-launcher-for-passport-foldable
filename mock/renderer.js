/* Fold8 launcher UI mock renderer.
 * テーマ定義ファイル(themes/*.json)を読み、カバー画面とメイン画面を実解像度 px で組み立てる。
 * メイン画面の片側はカバーの内容をそのまま再利用する(アンカー同期のデモ)。 */

const THEMES = ['amber-terminal', 'paper-white'];

/* 実機スクリーンショット(2026-08-21)を元にしたモックデータ */
const DATA = {
  apps: [
    { n: 'Amazon', c: '#e8a33d' }, { n: 'メルカリ', c: '#e6362e' },
    { n: 'Bluesky', c: '#1185fe' }, { n: 'Notion', c: '#26262b' },
    { n: 'ニュース', c: '#d8d8dc', b: 1 }, { n: 'Kindle', c: '#17323f', b: 2 },
    { n: 'Instagram', c: '#d6349c' }, { n: 'X', c: '#101014' },
    { n: 'Discord', c: '#5865f2', b: 1 }, { n: 'Nostr', c: '#1c1c22' },
    null, { n: 'LINE', c: '#06c755', b: 124 },
    { n: 'Threads', c: '#101014' }, { n: 'Facebook', c: '#1877f2', b: 3 },
    null, { n: 'Slack', c: '#4a154b' },
    { n: 'ジャンプ+', c: '#d3382c', b: 2 }, { n: 'Cosmos', c: '#232338', b: 9 },
  ],
  folders: [
    { name: 'MEDIA', apps: [
      { n: 'YT Music', c: '#f00' }, { n: 'YouTube', c: '#f00' }, { n: 'Netflix', c: '#8a0f13' },
      { n: 'Spotify', c: '#1db954' }, { n: 'Rakuten', c: '#3d7fd6' }, { n: 'Prime', c: '#00a8e1' },
      { n: 'torne', c: '#d8d8dc' } ] },
    { name: 'FIELD', apps: [
      { n: '御朱印', c: '#8a5a2b' }, { n: '切手', c: '#7fb069' }, { n: 'ツール', c: '#2b6cb0' },
      { n: 'むらさき', c: '#9f7aea' }, { n: '肉球', c: '#5a5a60' }, { n: 'マップ', c: '#6b46c1' },
      { n: '鹿', c: '#e8963c', b: 2 } ] },
    { name: 'BANK', apps: [
      { n: 'Vpass', c: '#0f9d58', b: 2 }, { n: '三井住友', c: '#26262b' },
      { n: 'MUFG', c: '#e60012' }, { n: 'Sony Bank', c: '#0a6e6e' } ] },
  ],
  dock: [
    { n: 'カメラ', c: '#c9c9cf' }, { n: 'Chrome', c: '#4285f4' },
    { n: 'Gemini', c: '#7c62d6' }, { n: 'WN', c: '#e8963c', b: 1 },
    { n: 'GitHub', c: '#1b1f23' }, { n: 'Gmail', c: '#ea4335', b: 7 },
  ],
  extensionApps: [
    { n: '電話', c: '#34c759' }, { n: 'メッセージ', c: '#3478f6' },
    { n: 'SmartEX', c: '#c0392b' }, { n: 'ChatGPT', c: '#10a37f' },
    { n: 'マーケット', c: '#2b6cb0' }, { n: 'Teams', c: '#5059c9', b: 2 },
    { n: '謎解き', c: '#b9ab8a' }, { n: '写真', c: '#e91e63' },
    { n: 'てつどう', c: '#6a7a6d' }, { n: 'Termux', c: '#3a3f44' },
  ],
};

/* ---------- helpers ---------- */

function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
}

function luminance(hex) {
  const v = hex.replace('#', '');
  const [r, g, b] = [0, 2, 4].map(i => parseInt(v.slice(i, i + 2), 16) / 255);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function glyph(name) {
  const m = name.match(/^[A-Za-z0-9+]+/);
  if (m) return m[0].length > 1 ? m[0].slice(0, 2) : m[0];
  return name.slice(0, 1);
}

function appEl(app, theme) {
  const wrap = el('div', 'app');
  if (!app) return wrap;
  const icon = el('div', 'icon', glyph(app.n));
  icon.style.background = app.c;
  icon.style.color = luminance(app.c) > 0.55 ? '#20232a' : '#f5f5f7';
  if (app.b) icon.appendChild(el('span', 'badge', String(app.b)));
  wrap.appendChild(icon);
  if (theme.icon.labels) wrap.appendChild(el('div', 'label', app.n));
  return wrap;
}

function zoneHead(text, theme) {
  return theme.decor.zoneHeaders ? el('div', 'zone-head', text) : null;
}

function module(title, theme) {
  const m = el('div', 'module');
  if (title && theme.decor.zoneHeaders) m.appendChild(el('div', 'mod-title', title));
  return m;
}

/* ---------- widgets ---------- */

const WIDGETS = {
  weatherRadar(theme) {
    const m = module('RAIN RADAR // さいたま市緑区', theme);
    const map = el('div', 'radar-map');
    m.appendChild(map);
    m.appendChild(el('div', 'radar-cross', '✛'));
    const msg = el('div', null, '15時頃に雨が降りはじめます');
    msg.style.cssText = 'font-size:30px;position:relative;';
    m.appendChild(msg);
    const hourly = el('div', 'hourly');
    [['12', '30%'], ['15', '20%'], ['18', '20%'], ['21', '20%']].forEach(([h, p]) => {
      const d = el('div');
      d.appendChild(el('b', null, h));
      d.appendChild(document.createTextNode(p));
      hourly.appendChild(d);
    });
    m.appendChild(hourly);
    return m;
  },
  weatherNow(theme) {
    const m = module('WX // NOW', theme);
    const row = el('div', 'w-row');
    const t = el('span', 'w-big', '32°');
    t.style.fontSize = '96px';
    row.appendChild(t);
    m.appendChild(row);
    m.appendChild(el('div', 'w-dim', 'どんより曇り ☂47%'));
    return m;
  },
  calendar(theme) {
    const m = module('SCHEDULE // 8.21 FRI', theme);
    const ev = el('div', 'cal-event');
    ev.appendChild(el('b', null, '江古田'));
    ev.appendChild(el('span', null, '19:30 – 21:00'));
    m.appendChild(ev);
    m.appendChild(el('div', 'w-dim', 'このあとの予定はありません'));
    m.lastChild.style.marginTop = 'auto';
    return m;
  },
  alarm(theme) {
    const m = module('ALARM', theme);
    const line = el('div', 'stat-line');
    line.appendChild(el('b', null, '--:--'));
    line.appendChild(el('span', 'u', '未設定'));
    m.appendChild(line);
    return m;
  },
  health(theme) {
    const m = module('VITALS', theme);
    [['🔥', '28', '/400 kcal'], ['👣', '72', '/6000'], ['⏱', '0', '/30 min']].forEach(([ic, v, u]) => {
      const line = el('div', 'stat-line');
      line.appendChild(el('span', null, ic));
      line.appendChild(el('b', null, v));
      line.appendChild(el('span', 'u', u));
      m.appendChild(line);
    });
    return m;
  },
  steps(theme) {
    const m = module('STEPS', theme);
    const line = el('div', 'stat-line');
    line.appendChild(el('b', null, '72'));
    line.appendChild(el('span', 'u', '/6000 歩'));
    m.appendChild(line);
    const meter = el('div', 'meter');
    const fill = el('i');
    fill.style.width = '2%';
    meter.appendChild(fill);
    m.appendChild(meter);
    return m;
  },
  sleep(theme) {
    const m = module('SLEEP', theme);
    const line = el('div', 'stat-line');
    line.appendChild(el('b', null, '6h 23m'));
    m.appendChild(line);
    const meter = el('div', 'meter');
    const fill = el('i');
    fill.style.width = '76%';
    meter.appendChild(fill);
    m.appendChild(meter);
    return m;
  },
  transit(theme) {
    const m = module('TRANSIT // 武蔵野線', theme);
    const line = el('div', 'stat-line');
    line.appendChild(el('b', null, '13:18'));
    line.appendChild(el('span', 'u', '次発 → 南浦和'));
    m.appendChild(line);
    m.appendChild(el('div', 'w-dim', '13:32 / 13:47 平常運転'));
    return m;
  },
  sysmon(theme) {
    const m = module('SYS', theme);
    const line = el('div', 'stat-line');
    line.appendChild(el('b', null, '83%'));
    line.appendChild(el('span', 'u', 'BATT ・ 421.4 K/s ・ 5G+'));
    m.appendChild(line);
    const meter = el('div', 'meter');
    const fill = el('i');
    fill.style.width = '83%';
    meter.appendChild(fill);
    m.appendChild(meter);
    return m;
  },
};

/* ---------- zones ---------- */

function statusBar() {
  const s = el('div', 'status');
  s.appendChild(el('span', 'clock', '13:05'));
  const right = el('div', 'right');
  right.appendChild(el('span', null, '421.4 K/s'));
  right.appendChild(el('span', null, '5G+ ▲▼'));
  right.appendChild(el('span', null, '▂▄▆█'));
  right.appendChild(el('span', 'batt', '[▮▮▮▮▮▮▮▮░░] 83'));
  s.appendChild(right);
  return s;
}

function widgetsZone(slots, theme) {
  const g = el('div', 'widgets-grid');
  slots.forEach(slot => {
    const render = WIDGETS[slot.widget];
    const w = render ? render(theme) : module(slot.widget.toUpperCase(), theme);
    w.style.gridColumn = `span ${slot.w || 2}`;
    w.style.gridRow = `span ${slot.h || 1}`;
    g.appendChild(w);
  });
  return g;
}

function foldersZone(theme) {
  const g = el('div', 'folders-grid');
  DATA.folders.forEach(f => {
    const m = module(f.name, theme);
    m.classList.add('folder');
    const mini = el('div', 'mini');
    f.apps.forEach(a => {
      const i = el('i');
      i.style.background = a.c;
      if (a.b) i.appendChild(el('span', 'badge', String(a.b)));
      mini.appendChild(i);
    });
    m.appendChild(mini);
    m.appendChild(el('div', 'count', `${f.apps.length} APPS`));
    g.appendChild(m);
  });
  return g;
}

function appsZone(list, theme) {
  const g = el('div', 'apps-grid');
  list.forEach(a => g.appendChild(appEl(a, theme)));
  return g;
}

function searchBar() {
  const s = el('div', 'search');
  s.appendChild(el('span', null, '⌕'));
  s.appendChild(el('span', null, 'ファインダー'));
  s.appendChild(el('span', 'cursor', '▊'));
  s.appendChild(el('span', 'mic', '🎙'));
  return s;
}

function dockZone(theme) {
  const d = el('div', 'dock');
  DATA.dock.forEach(a => d.appendChild(appEl(a, theme)));
  return d;
}

/* カバーのホーム面(ウィジェット+フォルダ+アプリ)。
 * カバー画面本体と、メイン画面のアンカーゾーンの両方がこれを呼ぶ = 配置同期のデモ。 */
function coverContent(theme, container) {
  container.appendChild(widgetsZone(theme.screens.cover.widgets, theme));
  container.appendChild(foldersZone(theme));
  const apps = appsZone(DATA.apps, theme);
  apps.style.flex = '1';
  container.appendChild(apps);
}

/* ---------- screens ---------- */

function renderCover(theme) {
  const scr = document.getElementById('cover');
  scr.innerHTML = '';
  scr.appendChild(statusBar());
  const body = el('div', 'cover-body');
  coverContent(theme, body);
  body.appendChild(searchBar());
  body.appendChild(dockZone(theme));
  scr.appendChild(body);
  scr.appendChild(el('div', 'gesture-bar'));
}

function renderMain(theme) {
  const scr = document.getElementById('mainscr');
  scr.innerHTML = '';
  scr.appendChild(statusBar());

  const body = el('div', 'main-body');
  const ext = el('div', 'pane');
  const head1 = zoneHead('EXT.PANEL // UNFOLD+', theme);
  if (head1) ext.appendChild(head1);
  ext.appendChild(widgetsZone(theme.screens.main.extensionWidgets, theme));
  const extApps = appsZone(DATA.extensionApps, theme);
  extApps.style.flex = '1';
  extApps.style.alignContent = 'end';
  ext.appendChild(extApps);

  const hinge = el('div', 'hinge');
  if (theme.decor.hingeMarker) {
    hinge.appendChild(el('div', 'rule'));
    hinge.appendChild(el('div', 'tag', 'HINGE'));
    hinge.appendChild(el('div', 'rule'));
  }

  const anchor = el('div', 'pane');
  const head2 = zoneHead('PRIMARY // SYNC:COVER', theme);
  if (head2) anchor.appendChild(head2);
  coverContent(theme, anchor);

  const side = theme.screens.main.anchorSide;
  const panes = side === 'left' ? [anchor, hinge, ext] : [ext, hinge, anchor];
  panes.forEach(p => body.appendChild(p));
  scr.appendChild(body);

  const bottom = el('div', 'main-bottom');
  bottom.appendChild(searchBar());
  bottom.appendChild(dockZone(theme));
  scr.appendChild(bottom);
  scr.appendChild(el('div', 'gesture-bar'));
}

/* ---------- theme plumbing ---------- */

function applyTheme(theme) {
  const radius = { squircle: '28%', circle: '50%', hex: '18%' }[theme.icon.shape] || '28%';
  [document.getElementById('cover'), document.getElementById('mainscr')].forEach(scr => {
    const p = theme.palette;
    const set = (k, v) => scr.style.setProperty(k, v);
    set('--wallpaper', theme.wallpaper);
    set('--surface', p.surface);
    set('--line', p.line);
    set('--accent', p.accent);
    set('--accent2', p.accent2);
    set('--warn', p.warn);
    set('--text', p.text);
    set('--text-dim', p.textDim);
    set('--badge-fg', luminance(p.accent) > 0.55 ? '#20232a' : '#fff');
    set('--font-ui', theme.typography.ui);
    set('--font-mono', theme.typography.mono);
    set('--icon-size', theme.icon.size + 'px');
    set('--icon-radius', radius);
    set('--mod-radius', theme.moduleRadius + 'px');
    scr.classList.toggle('scanlines', !!theme.decor.scanlines);
    scr.classList.toggle('griddots', !!theme.decor.gridDots);
    scr.classList.toggle('brackets', !!theme.decor.cornerBrackets);
    scr.classList.toggle('mono-icons', !!theme.icon.monochrome);
  });
  renderCover(theme);
  renderMain(theme);
}

async function loadTheme(name) {
  const res = await fetch(`themes/${name}.json`);
  if (!res.ok) throw new Error(`theme load failed: ${name}`);
  return res.json();
}

function buildControls(current) {
  const tc = document.getElementById('theme-ctrl');
  tc.innerHTML = '';
  THEMES.forEach(name => {
    const b = el('button', name === current ? 'on' : '', name);
    b.onclick = () => init(name);
    tc.appendChild(b);
  });
  const zc = document.getElementById('zoom-ctrl');
  zc.innerHTML = '';
  [0.3, 0.38, 0.5].forEach(z => {
    const b = el('button', null, `${Math.round(z * 100)}%`);
    b.onclick = () => document.documentElement.style.setProperty('--zoom', z);
    zc.appendChild(b);
  });
}

async function init(name) {
  try {
    const theme = await loadTheme(name);
    buildControls(name);
    applyTheme(theme);
  } catch (e) {
    document.body.insertAdjacentHTML('beforeend',
      `<p style="padding:24px;color:#ff8">テーマを読めませんでした(${e.message})。` +
      `file:// では fetch がブロックされるので <code>python3 -m http.server</code> で開いてください。</p>`);
  }
}

init(THEMES[0]);
