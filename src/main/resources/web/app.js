'use strict';
const $ = (id) => document.getElementById(id);
const state = { zone: '', selected: null, series: [], param: null };

const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = (v) => (v == null ? '—' : Number(v).toLocaleString('es-ES', { maximumFractionDigits: 1 }));

async function api(path) {
  const r = await fetch(path);
  const body = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(body.error || `Error ${r.status}`);
  return body;
}

function showError(msg) {
  const el = $('error');
  el.hidden = !msg;
  el.textContent = msg || '';
}

// Nivel del ICA (0 = mejor) a partir del texto de la categoría; -1 si no se reconoce.
function icaLevel(text) {
  const t = String(text || '').toLowerCase();
  if (t.includes('extremad')) return 5;
  if (t.includes('muy desfav')) return 4;
  if (t.includes('desfav')) return 3;
  if (t.includes('regular')) return 2;
  if (t.includes('razonable')) return 1;
  if (t.includes('buena')) return 0;
  return -1;
}

function worstIca(list) {
  let best = null;
  for (const p of list || []) {
    const lvl = icaLevel(p.ica);
    if (!best || lvl > best.lvl) best = { lvl, text: p.ica };
  }
  return best;
}

async function loadZones() {
  const zones = await api('/api/zones');
  $('zone').innerHTML = '<option value="">Todas</option>' +
    zones.map((z) => `<option value="${esc(z.id)}">${esc(z.name)}</option>`).join('');
}

async function loadStations() {
  const list = await api('/api/overview' + (state.zone ? `?zone=${encodeURIComponent(state.zone)}` : ''));
  const box = $('stations');
  if (!list.length) { box.innerHTML = '<p class="empty">No hay estaciones para esta zona.</p>'; return; }
  box.innerHTML = list.map((o) => {
    const w = worstIca(o.ica);
    const lvl = w && w.lvl >= 0 ? ` lvl-${w.lvl}` : '';
    const badge = w ? `<span class="badge${lvl}">${esc(w.text || 'Sin dato')}</span>` : '<span class="badge">Sin ICA</span>';
    return `<button type="button" class="card${lvl}" data-id="${esc(o.station.id)}" aria-pressed="${state.selected === o.station.id}">
      <span class="name">${esc(o.station.name)}</span>
      <span class="zone">${esc(o.zoneName)}${o.station.active ? '' : ' · inactiva'}</span>
      ${badge}</button>`;
  }).join('');
}

async function selectStation(id) {
  state.selected = id;
  document.querySelectorAll('.card').forEach((c) => c.setAttribute('aria-pressed', String(Number(c.dataset.id) === id)));
  $('detail').className = 'empty';
  $('detail').textContent = 'Cargando…';
  try {
    const [view, series] = await Promise.all([api(`/api/stations/${id}`), api(`/api/stations/${id}/history`)]);
    state.series = series;
    state.param = (series.find((s) => s.points.length) || series[0] || {}).parameterId ?? null;
    renderDetail(view);
    showError('');
  } catch (e) {
    $('detail').textContent = 'No se pudo cargar el detalle.';
    showError(e.message);
  }
}

function renderDetail(view) {
  const s = view.station;
  const rows = view.readings.map((r) => `<tr><td>${esc(r.name)}</td>
    <td class="num">${fmt(r.value)} <small>${esc(r.unit)}</small></td>
    <td>${esc(r.date)} · ${esc(r.period)}</td><td>${esc(r.flag || '')}</td></tr>`).join('');
  $('detail').className = '';
  $('detail').innerHTML = `
    <h3>${esc(s.name)}</h3>
    <p class="meta">${esc(view.zoneName)} · ${esc(s.coordinates.latitude.toFixed(4))}, ${esc(s.coordinates.longitude.toFixed(4))} · ${esc(s.coordinates.altitude)} m</p>
    <table><thead><tr><th>Parámetro</th><th>Valor</th><th>Fecha · periodo</th><th>Estado</th></tr></thead>
    <tbody>${rows || '<tr><td colspan="4" class="empty">Sin mediciones recientes.</td></tr>'}</tbody></table>
    <div class="chart-head" id="chips"></div><div id="chart"></div>`;
  $('chips').innerHTML = state.series.map((p) =>
    `<button type="button" class="chip" data-p="${esc(p.parameterId)}" aria-pressed="${p.parameterId === state.param}">${esc(p.name)}</button>`).join('');
  drawChart();
}

function drawChart() {
  const s = state.series.find((p) => p.parameterId === state.param);
  const pts = s ? s.points.filter((p) => p.value != null) : [];
  if (!pts.length) { $('chart').innerHTML = '<p class="empty">Sin datos de las últimas 24 h.</p>'; return; }
  const W = 640, H = 220, L = 44, R = 12, T = 12, B = 28;
  const max = Math.max(...pts.map((p) => p.value)) || 1;
  const min = Math.min(0, ...pts.map((p) => p.value));
  const x = (i) => L + (pts.length === 1 ? (W - L - R) / 2 : (i * (W - L - R)) / (pts.length - 1));
  const y = (v) => T + (H - T - B) * (1 - (v - min) / (max - min || 1));
  const path = pts.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ');
  const grid = [0, 0.5, 1].map((f) => {
    const v = min + (max - min) * f;
    return `<line class="grid-line" x1="${L}" x2="${W - R}" y1="${y(v)}" y2="${y(v)}"/><text x="${L - 6}" y="${y(v) + 4}" text-anchor="end">${fmt(v)}</text>`;
  }).join('');
  const step = Math.max(1, Math.ceil(pts.length / 6));
  const labels = pts.map((p, i) => (i % step === 0 ? `<text x="${x(i)}" y="${H - 8}" text-anchor="middle">${esc(p.period)} h</text>` : '')).join('');
  const dots = pts.map((p, i) => `<circle class="dot" r="2.5" cx="${x(i)}" cy="${y(p.value)}"><title>${esc(p.date)} · ${esc(p.period)} h: ${fmt(p.value)} ${esc(s.unit)}</title></circle>`).join('');
  $('chart').innerHTML = `<svg class="chart" viewBox="0 0 ${W} ${H}" role="img" aria-label="Evolución de ${esc(s.name)} en las últimas 24 horas (${esc(s.unit)})">${grid}${labels}<path class="line" d="${path}"/>${dots}</svg>`;
}

async function reloadAll() {
  try {
    await loadStations();
    if (state.selected) await selectStation(state.selected);
    showError('');
  } catch (e) { showError(e.message); }
}

document.addEventListener('click', (e) => {
  const card = e.target.closest('.card');
  if (card) selectStation(Number(card.dataset.id));
  const chip = e.target.closest('.chip');
  if (chip) {
    state.param = Number(chip.dataset.p);
    document.querySelectorAll('.chip').forEach((c) => c.setAttribute('aria-pressed', String(c === chip)));
    drawChart();
  }
});
$('zone').addEventListener('change', (e) => { state.zone = e.target.value; loadStations().catch((er) => showError(er.message)); });
$('refresh').addEventListener('click', async () => { await api('/api/refresh').catch(() => {}); reloadAll(); });

(async function init() {
  try {
    const st = await api('/api/status');
    $('source').textContent = 'Fuente: ' + st.source;
    await loadZones();
    await loadStations();
  } catch (e) {
    $('source').textContent = 'Sin conexión con la fuente de datos';
    showError(e.message);
  }
})();
