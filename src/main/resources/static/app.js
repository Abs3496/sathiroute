(() => {
  const root = document.querySelector('#app');
  const api = {
    base: localStorage.getItem('sathiroute.apiBase') || '',
    token: localStorage.getItem('sathiroute.token') || '',
    role: localStorage.getItem('sathiroute.role') || '',
    subject: localStorage.getItem('sathiroute.subject') || '',
    demo: localStorage.getItem('sathiroute.mode') === 'demo',
    portal: localStorage.getItem('sathiroute.portal') || '',
    remember: true,
  };
  let page = 'dashboard';
  let categoryFilter = 'all';
  let selectedService = null;
  let bookingFilter = 'all';
  let bookings = [], partners = [], journeys = [], services = [], activity = [], metrics = {};
  const titles = { dashboard: 'Dashboard', bookings: 'Service bookings', partners: 'Care partners', operations: 'Service operations', services: 'Service categories', reports: 'Reports', profile: 'My workspace' };
  const cats = { NURSING: 'Nursing', AYURVEDIC_THERAPY: 'Ayurvedic therapy', COUNSELLING: 'Counselling' };
  const statuses = { NEW: 'New', PENDING: 'Awaiting partner', ASSIGNED: 'Partner assigned', CONFIRMED: 'Confirmed', PARTNER_ON_THE_WAY: 'On the way', PARTNER_NEARBY: 'Nearby', PARTNER_ARRIVED: 'Arrived', IN_PROGRESS: 'In progress', COMPLETED: 'Completed', CANCELLED: 'Cancelled', REJECTED: 'Needs attention' };
  const $ = (s, el = document) => el.querySelector(s);
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const category = value => cats[value] || value || 'Care visit';
  const statusName = value => statuses[value] || value || 'Scheduled';
  const initials = value => String(value || 'SR').split(/[\s@._-]+/).filter(Boolean).slice(0, 2).map(x => x[0]).join('').toUpperCase();
  const dateToday = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
  const dateLabel = value => value ? new Intl.DateTimeFormat('en-IN', { weekday: 'short', month: 'short', day: 'numeric' }).format(new Date(`${value}T00:00:00`)) : 'Date pending';
  const timeLabel = value => { if (!value) return 'Time pending'; const [h, m] = String(value).slice(0, 5).split(':').map(Number); return `${h % 12 || 12}:${String(m).padStart(2, '0')} ${h >= 12 ? 'PM' : 'AM'}`; };
  const isAdmin = () => ['ADMIN', 'DISPATCHER'].includes(api.role);
  const isOps = () => api.role === 'OPERATIONS_MANAGER';
  const portalLabel = () => api.portal === 'admin' ? 'Admin' : 'Employee';
  const brand = `<span class="brand-symbol"><svg viewBox="0 0 40 40" aria-hidden="true"><path d="M20 4.5c-7.5 0-13.5 5.8-13.5 13 0 9.2 13.5 18 13.5 18s13.5-8.8 13.5-18c0-7.2-6-13-13.5-13Z"/><path d="M20 10.5v14M13 17.5h14"/></svg></span><span class="brand-word">Sathi<span>Route</span><small>HEALTHCARE OPERATIONS</small></span>`;

  function jwtRole(token) {
    try {
      const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const claims = JSON.parse(decodeURIComponent(escape(atob(part))));
      const values = [claims.roles, claims.authorities, claims.realm_access?.roles, ...Object.values(claims.resource_access || {}).map(v => v?.roles)].flat().filter(v => typeof v === 'string').map(v => v.toUpperCase().replace(/^ROLE_/, ''));
      return ['ADMIN', 'DISPATCHER', 'OPERATIONS_MANAGER', 'SERVICE_PARTNER'].find(r => values.includes(r)) || (claims.sub === 'dispatch@routemesh.example.test' ? 'ADMIN' : '');
    } catch { return ''; }
  }
  async function request(path, options = {}) {
    const response = await fetch(`${api.base.replace(/\/$/, '')}${path}`, { ...options, headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), Authorization: `Bearer ${api.token}`, ...(options.headers || {}) } });
    if (!response.ok) { let message = `${response.status} ${response.statusText}`; try { const body = await response.json(); message = body.detail || body.title || message; } catch {} const error = new Error(message); error.status = response.status; throw error; }
    return response.status === 204 ? null : response.json();
  }
  async function demoRequest(path, options = {}) {
    const response = await fetch(`/dev/demo${path}`, { ...options, headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(options.headers || {}) } });
    if (!response.ok) { let message = `${response.status} ${response.statusText}`; try { const body = await response.json(); message = body.detail || body.title || message; } catch {} throw new Error(message); }
    return response.status === 204 ? null : response.json();
  }
  async function fetchAll(path) {
    const list = [];
    for (let offset = 0; offset < 10000; offset += 100) {
      const batch = await request(`${path}?limit=100&offset=${offset}`);
      if (!Array.isArray(batch)) throw new Error('Unexpected records response');
      list.push(...batch); if (batch.length < 100) break;
    }
    return list;
  }
  function normalBooking(b) {
    const start = b.appointmentStartAt || b.appointmentStart || b.startsAt || '';
    return { ...b, id: b.id || b.bookingId, customerName: b.customerName || 'Customer', category: b.category || b.serviceCategory, skill: b.requiredSkill || b.skill, date: b.appointmentDate || (start ? String(start).slice(0, 10) : ''), time: b.appointmentStart || (start ? String(start).slice(11, 16) : ''), duration: b.serviceDurationMinutes || b.durationMinutes || 60, partnerName: b.partnerName || b.assignedPartnerName || '', partnerId: b.assignedPartnerId || '', version: b.version ?? 0 };
  }
  async function loadWorkspace() {
    if (api.demo) {
      const data = await demoRequest('/workspace');
      bookings = (data.bookings || []).map(normalBooking); partners = data.partners || []; journeys = data.journeys || []; services = data.services || []; activity = data.activity || []; metrics = data.metrics || {};
      return;
    }
    const ops = isAdmin() || isOps();
    const results = await Promise.allSettled(ops ? [fetchAll('/v1/admin/bookings'), request('/v1/admin/partners?status=APPROVED'), request('/v1/admin/partners?status=PENDING'), request('/v1/admin/tracking/partners'), request('/v1/admin/service-executions'), request('/v1/admin/services')] : [fetchAll('/v1/partners/me/bookings'), request('/v1/partners/me'), request('/v1/partners/me/workload')]);
    const value = i => results[i]?.status === 'fulfilled' ? results[i].value : null;
    if (ops) {
      bookings = (value(0) || []).map(normalBooking); partners = [...(value(1) || []), ...(value(2) || [])]; journeys = value(3) || [];
      const executions = value(4) || [];
      executions.forEach(e => { const b = bookings.find(item => item.id === e.bookingId); if (b) { b.partnerName = e.partnerName || b.partnerName; b.status = e.bookingStatus || b.status; } });
      services = value(5) || [];
    } else {
      bookings = (value(0) || []).map(normalBooking); partners = value(1) ? [{ ...value(1), ...(value(2) || {}) }] : [];
      journeys = bookings.filter(b => ['CONFIRMED', 'PARTNER_ON_THE_WAY', 'PARTNER_NEARBY', 'PARTNER_ARRIVED', 'IN_PROGRESS'].includes(b.status)).map(b => ({ bookingId: b.id, partnerName: 'You', customerName: b.customerName, status: b.status }));
    }
    if (results.every(x => x.status === 'rejected')) throw results[0].reason;
    if (!ops) services = [];
  }
  function saveSession() {
    for (const k of ['token', 'apiBase', 'role', 'subject', 'portal', 'mode']) localStorage.removeItem(`sathiroute.${k}`);
    if (!api.remember) return;
    if (api.token) localStorage.setItem('sathiroute.token', api.token);
    if (api.base) localStorage.setItem('sathiroute.apiBase', api.base);
    if (api.role) localStorage.setItem('sathiroute.role', api.role);
    if (api.subject) localStorage.setItem('sathiroute.subject', api.subject);
    if (api.portal) localStorage.setItem('sathiroute.portal', api.portal);
    if (api.demo) localStorage.setItem('sathiroute.mode', 'demo');
  }
  function openLogin(portal = '') {
    api.portal = portal;
    root.innerHTML = `<main class="auth-shell"><header class="auth-brand">${brand}<span class="auth-caption">Care · Connect · Serve</span></header><section class="auth-content"><div class="auth-intro"><span class="eyebrow">CARE FROM CENTRE TO HOME</span><h1>Welcome to SathiRoute<br><em>Healthcare Operations</em></h1><p>One connected workspace for thoughtful, reliable care at home.</p></div>${portal ? loginForm(portal) : portalCards()}<p class="auth-foot">Secure operations for Nursing, Ayurvedic Therapy and Counselling.</p></section><div class="auth-art"><div class="art-orbit orbit-a"></div><div class="art-orbit orbit-b"></div><div class="art-cross">+</div><div class="art-card"><span class="art-heart">♥</span><strong>Care that moves<br>with people.</strong><small>PERSON-CENTRED HOME CARE</small></div><span class="art-label">SATHI · COMPANION</span></div></main>`;
    bindLogin();
  }
  function portalCards() {
    return `<div class="portal-cards"><button class="portal-card admin-card" data-portal="admin"><span class="portal-icon">▦</span><span class="portal-copy"><strong>Admin Login</strong><small>Complete business operations access</small><i>Users, service monitoring and configuration</i></span><span class="portal-arrow">→</span></button><button class="portal-card employee-card" data-portal="employee"><span class="portal-icon">♧</span><span class="portal-copy"><strong>Employee Login</strong><small>Permission-based operational workspace</small><i>Daily tasks, bookings and service coordination</i></span><span class="portal-arrow">→</span></button><button class="demo-launch" data-demo="admin">Explore admin demo <span>↗</span></button><button class="demo-launch demo-secondary" data-demo="employee">Explore employee demo <span>↗</span></button></div>`;
  }
  function loginForm(portal) {
    const label = portal === 'admin' ? 'Admin' : 'Employee';
    return `<form class="login-card" id="login-form"><button class="back-link" type="button" data-back>← Choose a portal</button><span class="login-badge">${portal === 'admin' ? '▦' : '♧'} ${label.toUpperCase()} PORTAL</span><h2>Sign in to your workspace</h2><p>Use your organization identity provider account.</p><label>API base URL <span>(optional)</span><input name="apiBase" type="url" placeholder="Same origin as this app" value="${esc(api.base)}"></label><label>Bearer access token<input name="token" type="password" placeholder="Paste your OIDC access token" autocomplete="current-password" required></label><div class="login-options"><label class="remember"><input type="checkbox" name="remember" checked> Remember this device</label><a href="#" data-help>Need access?</a></div><p class="form-error" id="login-error"></p><button class="button-primary login-submit" type="submit">Continue as ${label}<span>→</span></button><div class="auth-provider-note"><span class="secure-dot"></span>Authentication is verified by your configured identity provider.</div></form>`;
  }
  function bindLogin() {
    document.querySelectorAll('[data-portal]').forEach(button => button.addEventListener('click', () => openLogin(button.dataset.portal)));
    document.querySelectorAll('[data-demo]').forEach(button => button.addEventListener('click', () => enterDemo(button.dataset.demo)));
    $('[data-back]')?.addEventListener('click', () => openLogin(''));
    $('[data-help]')?.addEventListener('click', e => { e.preventDefault(); toast('Ask your SathiRoute administrator for an account and identity-provider access.'); });
    $('#login-form')?.addEventListener('submit', async e => {
      e.preventDefault(); const form = new FormData(e.currentTarget); const token = String(form.get('token') || '').trim().replace(/^Bearer\s+/i, '');
      const role = jwtRole(token), portal = api.portal;
      const allowed = portal === 'admin' ? ['ADMIN', 'DISPATCHER'].includes(role) : role === 'OPERATIONS_MANAGER';
      if (!allowed) { $('#login-error').textContent = portal === 'admin' ? 'This token does not contain an Admin or Dispatcher role.' : 'Employee workspace currently requires the backend OPERATIONS_MANAGER role.'; return; }
      api.token = token; api.base = String(form.get('apiBase') || '').trim().replace(/\/$/, ''); api.role = role; api.subject = 'Workspace user'; api.demo = false;
      api.remember = Boolean(form.get('remember'));
      try { await loadWorkspace(); saveSession(); renderApp(); toast(`Connected as ${role.replace('_', ' ')}.`); }
      catch (error) { api.token = ''; $('#login-error').textContent = `${error.message}. Check your token, API URL and account permissions.`; }
    });
  }
  async function enterDemo(portal) {
    api.token = ''; api.base = ''; api.demo = true; api.portal = portal; api.remember = true; api.role = portal === 'admin' ? 'ADMIN' : 'OPERATIONS_MANAGER'; api.subject = portal === 'admin' ? 'Demo Administrator' : 'Demo Operations Manager';
    try { await loadWorkspace(); saveSession(); renderApp(); toast('Demo workspace loaded. Changes are temporary showcase data.'); }
    catch (error) { api.demo = false; openLogin(''); toast(`Demo API unavailable: ${error.message}. Start the local showcase stack.`); }
  }
  function navGroups() {
    if (api.portal === 'admin') return [
      ['OVERVIEW', [['dashboard', '◫', 'Dashboard']]],
      ['SERVICE MANAGEMENT', [['bookings', '▤', 'All bookings'], ['services', '✳', 'Service categories']]],
      ['HEALTHCARE OPERATIONS', [['operations', '⌁', 'Service visits'], ['partners', '♧', 'Care partners']]],
      ['INSIGHTS', [['reports', '▥', 'Reports']]],
    ];
    return [
      ['MY WORKSPACE', [['dashboard', '◫', 'My dashboard'], ['bookings', '▤', 'My bookings'], ['operations', '⌁', 'Service visits']]],
      ['COORDINATION', [['partners', '♧', 'Care partners']]],
      ['ACCOUNT', [['profile', '○', 'My profile']]],
    ];
  }
  function renderApp() {
    if (!api.token && !api.demo) return openLogin('');
    root.innerHTML = `<div class="app-shell ${api.portal === 'employee' ? 'employee-shell' : 'admin-shell'}"><aside class="sidebar" id="sidebar"><a class="brand" href="#dashboard">${brand}</a><div class="workspace-chip"><span class="workspace-led"></span>${api.demo ? 'SHOWCASE DATA' : 'CONNECTED WORKSPACE'}</div><nav class="side-nav">${navGroups().map(([group, links]) => `<div class="nav-group"><div class="nav-label">${group}</div>${links.map(([id, icon, label]) => `<button class="nav-item ${page === id ? 'selected' : ''}" data-page="${id}"><span class="nav-symbol">${icon}</span>${label}${id === 'bookings' && bookings.length ? `<i class="nav-count">${bookings.filter(b => ['NEW', 'PENDING', 'REJECTED'].includes(b.status)).length || ''}</i>` : ''}</button>`).join('')}</div>`).join('')}</nav><div class="sidebar-bottom"><div class="care-promo"><span class="promo-heart">♥</span><strong>Care from<br>Centre to Home</strong><small>THOUGHTFUL CARE. EVERY DAY.</small></div><div class="user-card"><span class="avatar">${initials(api.subject)}</span><span><strong>${esc(api.demo ? (api.portal === 'admin' ? 'Demo Admin' : 'Demo Employee') : api.subject)}</strong><small>${api.demo ? `${portalLabel()} · demo` : api.role.replaceAll('_', ' ')}</small></span><button class="logout" data-logout title="Sign out">↗</button></div></div></aside><main class="main-area"><header class="topbar"><button class="mobile-menu" data-menu aria-label="Open navigation">☰</button><div class="breadcrumb"><span>SathiRoute</span><b>/</b>${titles[page] || 'Dashboard'}</div><div class="topbar-right"><span class="mode-tag ${api.demo ? 'demo-tag' : 'live-tag'}"><i></i>${api.demo ? 'DEMO DATA' : 'LIVE API'}</span><span class="today-pill">${new Intl.DateTimeFormat('en-IN', { weekday: 'short', month: 'short', day: 'numeric' }).format(new Date())}</span><button class="icon-button" data-refresh title="Refresh workspace">↻</button><span class="top-avatar">${initials(api.subject)}</span></div></header><section class="page-content" id="page-content"></section></main></div><div class="modal-layer" id="modal-layer"></div>`;
    drawPage(); bindApp();
  }
  const bookCount = () => bookings.length;
  const pendingCount = () => bookings.filter(b => ['NEW', 'PENDING', 'REJECTED'].includes(b.status)).length;
  const activePartners = () => partners.filter(p => p.active && p.registrationStatus === 'APPROVED').length;
  const todayVisits = () => bookings.filter(b => b.date === dateToday() && !['CANCELLED', 'REJECTED'].includes(b.status)).length;
  function drawPage() {
    const target = $('#page-content');
    if (page === 'dashboard') target.innerHTML = drawDashboard();
    else if (page === 'bookings') target.innerHTML = drawBookings();
    else if (page === 'partners') target.innerHTML = drawPartners();
    else if (page === 'operations') target.innerHTML = drawOperations();
    else if (page === 'services') target.innerHTML = drawServices();
    else if (page === 'reports') target.innerHTML = drawReports();
    else target.innerHTML = drawProfile();
  }
  function heading(kicker, title, copy, action = '') { return `<div class="page-heading"><div><div class="eyebrow"><i></i>${kicker}</div><h1>${title}</h1><p>${copy}</p></div>${action}</div>`; }
  function metric(label, value, sub, icon, tint) { return `<article class="metric-card"><div class="metric-head"><span>${label}</span><i class="metric-icon ${tint}">${icon}</i></div><strong>${value}</strong><small>${sub}</small></article>`; }
  function drawDashboard() {
    const admin = api.portal === 'admin';
    const first = api.demo ? (admin ? 'Administrator' : 'Operations Manager') : (api.subject.split('@')[0] || (admin ? 'Administrator' : 'Team member'));
    const needs = bookings.filter(b => ['NEW', 'PENDING', 'REJECTED'].includes(b.status)).slice(0, 5);
    const next = bookings.filter(b => !['COMPLETED', 'CANCELLED', 'REJECTED'].includes(b.status)).sort((a,b) => `${a.date}${a.time}`.localeCompare(`${b.date}${b.time}`)).slice(0, 5);
    return `${heading(admin ? 'ORGANIZATION OVERVIEW' : 'YOUR OPERATIONS DESK', `Good ${new Date().getHours() < 12 ? 'morning' : 'afternoon'}, ${esc(first)} <span class="wave">✦</span>`, admin ? 'A clear view of care operations across your organization.' : 'Here is what needs your attention today.', admin && api.demo ? '<button class="button button-primary" data-new-booking>＋ New booking</button>' : '')}<div class="metric-grid">${metric('Total bookings', bookCount(), 'Records available to this account', '▤', 'blue')}${metric('Active partners', `${activePartners()}<span class="metric-denom"> / ${partners.length}</span>`, 'Approved and active', '♧', 'violet')}${metric('Service visits today', todayVisits(), `${journeys.length} active journeys`, '⌁', 'green')}${metric('Needs attention', pendingCount(), 'New or awaiting allocation', '◷', 'amber')}</div><div class="dashboard-grid"><article class="panel operations-panel"><div class="panel-title"><div><span class="panel-kicker">SERVICE OPERATIONS</span><h2>Visit activity</h2></div><button class="text-link" data-page="operations">View operations →</button></div><div class="service-overview"><div class="service-donut"><div><strong>${journeys.length}</strong><small>IN PROGRESS</small></div></div><div class="service-legend"><span><i class="dot nursing"></i>Nursing <b>${bookings.filter(b => b.category === 'NURSING').length}</b></span><span><i class="dot ayurveda"></i>Ayurvedic therapy <b>${bookings.filter(b => b.category === 'AYURVEDIC_THERAPY').length}</b></span><span><i class="dot counselling"></i>Counselling <b>${bookings.filter(b => b.category === 'COUNSELLING').length}</b></span></div></div><div class="ops-summary"><span><b>${pendingCount()}</b> bookings awaiting action</span><span><b>${partners.filter(p => p.registrationStatus === 'PENDING').length}</b> partner reviews</span></div></article><article class="panel attention-panel"><div class="panel-title"><div><span class="panel-kicker">ACTION QUEUE</span><h2>Needs attention</h2></div><span class="count-chip">${needs.length}</span></div>${needs.length ? needs.map(b => `<div class="queue-item"><span class="queue-mark">!</span><div><strong>${esc(b.customerName)}</strong><small>${esc(category(b.category))} · ${esc(dateLabel(b.date))}</small></div>${pill(b.status)}<button class="row-arrow" data-open-bookings aria-label="View booking">→</button></div>`).join('') : `<div class="empty-inline"><span>✓</span><strong>All caught up</strong><small>No bookings need attention.</small></div>`}</article></div><div class="panel upcoming-panel"><div class="panel-title"><div><span class="panel-kicker">CARE CALENDAR</span><h2>Upcoming service visits</h2></div><button class="text-link" data-page="bookings">All bookings →</button></div>${next.length ? `<div class="table-wrap"><table><thead><tr><th>Customer</th><th>Service</th><th>Appointment</th><th>Care partner</th><th>Status</th></tr></thead><tbody>${next.map(bookingRow).join('')}</tbody></table></div>` : empty('No upcoming visits', 'New service appointments will appear here.')}</div><div class="dashboard-foot"><span>Care · Connect · Serve</span><span>${api.demo ? 'Showcase information is temporary and resets with the demo service.' : 'Figures reflect data returned to your connected account.'}</span></div>`;
  }
  function pill(value) { const cls = ['COMPLETED', 'CONFIRMED', 'APPROVED', 'IN_PROGRESS'].includes(value) ? 'good' : ['REJECTED', 'CANCELLED'].includes(value) ? 'bad' : ['ASSIGNED', 'PARTNER_ARRIVED'].includes(value) ? 'blue' : 'pending'; return `<span class="status-pill ${cls}"><i></i>${esc(statusName(value))}</span>`; }
  function bookingRow(b) { return `<tr><td><div class="person-cell"><span class="avatar small-avatar">${initials(b.customerName)}</span><span><strong>${esc(b.customerName)}</strong><small>${esc(String(b.id || '').slice(0, 12))}</small></span></div></td><td><strong>${esc(category(b.category))}</strong><small class="subline">${esc(b.skill || '')}</small></td><td><strong>${esc(dateLabel(b.date))}</strong><small class="subline">${esc(timeLabel(b.time))}</small></td><td>${b.partnerName ? `<button class="partner-link" data-partner-open="${esc(b.partnerId)}" data-partner-name="${esc(b.partnerName)}">${esc(b.partnerName)} ↗</button>` : '<span class="muted-copy">Unassigned</span>'}</td><td>${pill(b.status)}</td></tr>`; }
  function drawBookings() {
    const allRows = bookings.slice().sort((a,b) => `${a.date}${a.time}`.localeCompare(`${b.date}${b.time}`));
    const rows = allRows.filter(b => categoryFilter === 'all' || b.category === categoryFilter);
    const serviceSummary = Object.entries(cats).map(([id, name]) => {
      const subset = allRows.filter(b => b.category === id), active = subset.filter(b => !['COMPLETED','CANCELLED','REJECTED'].includes(b.status)).length;
      const assigned = new Set(subset.filter(b => b.partnerName).map(b => b.partnerId || b.partnerName)).size;
      return `<button class="service-summary ${categoryFilter === id ? 'chosen' : ''}" data-category-filter="${id}"><span class="category-mark mark-${id.toLowerCase()}">${categoryIcon(id)}</span><span><strong>${esc(name)}</strong><small>${subset.length} bookings · ${assigned} partners</small></span><b>${active}<small>active</small></b></button>`;
    }).join('');
    return `${heading('SERVICE MANAGEMENT', 'Bookings <em>& visits.</em>', 'See partner coverage, service load and each visit in one place.', api.demo ? '<button class="button button-primary" data-new-booking>＋ New booking</button>' : '')}<div class="service-summary-grid"><button class="service-summary ${categoryFilter === 'all' ? 'chosen' : ''}" data-category-filter="all"><span class="category-mark">▤</span><span><strong>All services</strong><small>${allRows.length} bookings · ${new Set(allRows.filter(b => b.partnerName).map(b => b.partnerId || b.partnerName)).size} partners</small></span><b>${allRows.filter(b => !['COMPLETED','CANCELLED','REJECTED'].includes(b.status)).length}<small>active</small></b></button>${serviceSummary}</div><div class="toolbar"><div class="search-field"><span>⌕</span><input id="booking-search" placeholder="Search customer, service or booking ID"></div><div class="filter-tabs"><button class="filter-tab ${bookingFilter === 'all' ? 'active' : ''}" data-filter="all">All <b>${rows.length}</b></button><button class="filter-tab ${bookingFilter === 'open' ? 'active' : ''}" data-filter="open">Needs attention <b>${pendingCount()}</b></button><button class="filter-tab ${bookingFilter === 'active' ? 'active' : ''}" data-filter="active">In progress</button><button class="filter-tab ${bookingFilter === 'done' ? 'active' : ''}" data-filter="done">Completed</button></div><button class="button button-quiet" data-refresh>↻ Refresh</button></div><div class="panel data-panel"><div class="table-wrap"><table><thead><tr><th>Customer / ticket</th><th>Service</th><th>Appointment</th><th>Care partner</th><th>Status</th><th>Actions</th></tr></thead><tbody id="booking-rows">${rows.map(b => `<tr data-search="${esc(`${b.customerName} ${category(b.category)} ${b.id} ${b.partnerName}`.toLowerCase())}" data-category="${esc(b.category)}" data-status="${esc(b.status)}">${bookingRow(b).replace('<tr>', '').replace('</tr>', '')}<td><button class="small-action" data-ticket="${esc(b.id)}">View ticket</button> ${bookingAction(b)}</td></tr>`).join('')}</tbody></table></div>${rows.length ? '' : empty('No bookings in this category', 'Bookings will appear here when they are created.')}</div><div class="booking-live-foot"><span class="live-pulse"></span><span>${api.demo ? 'Showcase data · refresh to see changes from other demo actions.' : 'Live booking and partner activity · auto-refreshes while this page is open.'}</span><button class="text-link" data-refresh>Refresh now</button></div>`;
  }
  function categoryIcon(id) { return ({ NURSING: '✚', AYURVEDIC_THERAPY: '◉', COUNSELLING: '♡' })[id] || '✳'; }
  function bookingAction(b) {
    if (api.demo && ['NEW', 'PENDING', 'REJECTED'].includes(b.status)) return `<button class="small-action" data-action="allocate" data-id="${esc(b.id)}">Start allocation</button>`;
    if (api.token && isAdmin() && ['NEW', 'REJECTED'].includes(b.status)) return `<button class="small-action" data-action="allocate" data-id="${esc(b.id)}" data-version="${b.version}">Start partner search</button>`;
    if (api.token && isAdmin() && b.status === 'PENDING') return `<button class="small-action" data-action="search-partners" data-id="${esc(b.id)}">Search nearest partners</button>`;
    if (api.demo && !['COMPLETED', 'CANCELLED'].includes(b.status)) return `<select class="demo-status" aria-label="Change demo status" data-status-select="${esc(b.id)}">${Object.entries(statuses).map(([k,v]) => `<option value="${k}" ${b.status === k ? 'selected' : ''}>${esc(v)}</option>`).join('')}</select><button class="small-action" data-action="save-status" data-id="${esc(b.id)}">Save</button>`;
    return '';
  }
  function drawPartners() {
    const pending = partners.filter(p => p.registrationStatus === 'PENDING');
    return `${heading('PARTNER MANAGEMENT', 'Care <em>partners.</em>', 'Review qualifications, availability and partner status.', api.token && isAdmin() ? '<button class="button button-primary" data-add-partner>＋ Add partner manually</button>' : '')}<div class="partner-stats">${metric('All partners', partners.length, 'Visible to this workspace', '♧', 'blue')}${metric('Available', activePartners(), 'Approved and active', '✓', 'green')}${metric('Needs review', pending.length, 'Pending verification', '◷', 'amber')}</div><div class="toolbar"><div class="search-field"><span>⌕</span><input id="partner-search" placeholder="Find a care partner"></div><div class="filter-tabs"><button class="filter-tab active" data-partner-filter="all">Everyone</button><button class="filter-tab" data-partner-filter="pending">Needs review</button><button class="filter-tab" data-partner-filter="available">Active</button></div></div><div class="partner-grid" id="partner-grid">${partners.map(partnerCard).join('')}</div>${partners.length ? '' : empty('No partner records returned', api.demo ? 'Try refreshing the showcase data.' : 'The backend only returns partners available to your account.')}`;
  }
  function partnerCard(p) {
    const skills = (p.skills || []).map(s => `${category(s.category)}${s.code ? ` · ${s.code}` : ''}`).join(' / ') || 'Skills not listed';
    const action = api.demo && p.registrationStatus === 'PENDING' ? `<button class="small-action" data-action="verify" data-id="${esc(p.id)}">Approve partner</button>` : api.token && isAdmin() && p.registrationStatus === 'PENDING' ? `<button class="small-action" data-action="verify" data-id="${esc(p.id)}">Approve partner</button>` : '';
    return `<article class="panel partner-card" data-partner="${esc(`${p.name} ${skills}`.toLowerCase())}" data-review="${p.registrationStatus === 'PENDING'}" data-active="${Boolean(p.active)}"><div class="partner-card-head"><span class="avatar partner-avatar">${initials(p.name)}</span><div><h3>${esc(p.name || 'Care partner')}</h3><small>${esc(p.code || String(p.id || '').slice(0, 10))}</small></div>${pill(p.registrationStatus)}</div><div class="partner-skills"><span class="panel-kicker">QUALIFICATIONS</span><p>${esc(skills)}</p></div><div class="partner-card-meta"><span>Completed visits <b>${esc(p.completedBookings ?? '—')}</b></span><span>Upcoming <b>${esc(p.upcomingBookings ?? '—')}</b></span></div>${p.available ? '<span class="availability"><i></i>Available now</span>' : '<span class="availability off"><i></i>Currently unavailable</span>'}${action}</article>`;
  }
  function drawOperations() {
    const open = bookings.filter(b => !['COMPLETED', 'CANCELLED', 'REJECTED'].includes(b.status));
    const active = journeys.length ? journeys : open.filter(b => ['PARTNER_ON_THE_WAY', 'PARTNER_NEARBY', 'PARTNER_ARRIVED', 'IN_PROGRESS'].includes(b.status)).map(b => ({ bookingId: b.id, customerName: b.customerName, partnerName: b.partnerName, status: b.status }));
    return `${heading('HEALTHCARE OPERATIONS', 'Service <em>visits.</em>', 'Track assignments and the progress of care delivered at home.')}<div class="metric-grid compact-metrics">${metric('Open visits', open.length, 'Not completed or cancelled', '⌁', 'blue')}${metric('Active journeys', active.length, 'Partner in progress or en route', '◎', 'green')}${metric('Unassigned', open.filter(b => !b.partnerName).length, 'Awaiting partner assignment', '◷', 'amber')}</div><div class="panel visit-panel"><div class="panel-title"><div><span class="panel-kicker">LIVE SERVICE PROGRESS</span><h2>Active service visits</h2></div><span class="mode-tag ${api.demo ? 'demo-tag' : 'live-tag'}"><i></i>${api.demo ? 'DEMO' : 'API DATA'}</span></div>${active.length ? `<div class="visit-grid">${active.map(j => { const b = bookings.find(x => x.id === j.bookingId); return `<article class="visit-card"><div class="visit-card-top"><span class="visit-icon">⌁</span>${pill(j.status)}</div><h3>${esc(j.customerName || b?.customerName || 'Customer visit')}</h3><p>${esc(b ? category(b.category) : 'Home care visit')} · ${esc(b?.address || 'Service address')}</p><div class="visit-partner"><span class="avatar small-avatar">${initials(j.partnerName || b?.partnerName)}</span><span><small>CARE PARTNER</small><strong>${esc(j.partnerName || b?.partnerName || 'Assignment pending')}</strong></span></div><div class="visit-card-foot"><span>${esc(dateLabel(b?.date))}</span><b>${esc(timeLabel(b?.time))}</b></div></article>`; }).join('')}</div>` : empty('No active service visits', 'Assigned visits will appear as partners accept and progress through the service.')}</div><div class="panel assignment-panel"><div class="panel-title"><div><span class="panel-kicker">SCHEDULING</span><h2>Upcoming appointments</h2></div></div><div class="table-wrap"><table><thead><tr><th>Customer</th><th>Service</th><th>Appointment</th><th>Assignment</th><th>Status</th></tr></thead><tbody>${open.slice(0, 8).map(bookingRow).join('')}</tbody></table></div>${open.length ? '' : empty('No upcoming appointments', 'New bookings will appear here.')}</div>`;
  }
  function drawServices() {
    const list = services.length ? services : Object.entries(cats).map(([id, name]) => ({ id, name, active: true, description: 'Care delivered by a qualified healthcare partner at home.' }));
    return `${heading('SERVICE MANAGEMENT', 'Service <em>categories.</em>', 'The care specialities supported by the SathiRoute platform.')}<div class="notice-card"><span>i</span><p><strong>Platform categories</strong> Booking types are provided by the backend. A managed service catalogue and pricing API are not available in this project yet.</p></div><div class="service-grid">${list.map((s,i) => `<article class="panel service-card"><span class="service-number">0${i+1}</span><span class="service-illustration tint-${i}">${['✚','◉','♡'][i]}</span><span class="panel-kicker">CARE AT HOME</span><h2>${esc(s.name || category(s.id))}</h2><p>${esc(s.description || 'Qualified care and professional support, delivered in the comfort of home.')}</p><span class="service-state"><i></i>${s.active === false ? 'Paused in demo data' : 'Supported service category'}</span></article>`).join('')}</div>`;
  }
  // Operational booking/category views override the basic list renderers above.
  function bookingRow(b) { return `<tr><td><div class="person-cell"><span class="avatar small-avatar">${initials(b.customerName)}</span><span><strong>${esc(b.customerName)}</strong><small>${esc(String(b.id || '').slice(0,12))}</small></span></div></td><td><strong>${esc(category(b.category))}</strong><small class="subline">${esc(b.skill || '')}</small></td><td><strong>${esc(dateLabel(b.date))}</strong><small class="subline">${esc(timeLabel(b.time))}</small></td><td>${b.partnerName ? `<button class="partner-link" data-partner-open="${esc(b.partnerId || '')}" data-partner-name="${esc(b.partnerName)}">${esc(b.partnerName)} ↗</button>` : '<span class="muted-copy">Unassigned</span>'}</td><td>${pill(b.status)}</td></tr>`; }
  function drawBookings() {
    const all=bookings.slice().sort((a,b)=>`${a.date}${a.time}`.localeCompare(`${b.date}${b.time}`)), rows=all.filter(b=>categoryFilter==='all'||b.category===categoryFilter);
    const categoryCards=Object.entries(cats).map(([id,name])=>{const list=all.filter(b=>b.category===id),active=list.filter(b=>!['COMPLETED','CANCELLED','REJECTED'].includes(b.status)).length,partnerCount=new Set(list.filter(b=>b.partnerName).map(b=>b.partnerId||b.partnerName)).size;return `<button class="service-summary ${categoryFilter===id?'chosen':''}" data-category-filter="${id}"><span class="category-mark">${categoryIcon(id)}</span><span><strong>${esc(name)}</strong><small>${list.length} bookings · ${partnerCount} partners</small></span><b>${active}<small>active</small></b></button>`;}).join('');
    const active=all.filter(b=>!['COMPLETED','CANCELLED','REJECTED'].includes(b.status)).length,partnerCount=new Set(all.filter(b=>b.partnerName).map(b=>b.partnerId||b.partnerName)).size;
    return `${heading('SERVICE MANAGEMENT','Bookings <em>& visits.</em>','Service load, partner coverage and each visit in one place.',api.demo?'<button class="button button-primary" data-new-booking>＋ New booking</button>':'')}<div class="service-summary-grid"><button class="service-summary ${categoryFilter==='all'?'chosen':''}" data-category-filter="all"><span class="category-mark">▤</span><span><strong>All services</strong><small>${all.length} bookings · ${partnerCount} partners</small></span><b>${active}<small>active</small></b></button>${categoryCards}</div><div class="toolbar"><div class="search-field"><span>⌕</span><input id="booking-search" placeholder="Search customer, service, partner or ID"></div><div class="filter-tabs"><button class="filter-tab ${bookingFilter==='all'?'active':''}" data-filter="all">All <b>${rows.length}</b></button><button class="filter-tab ${bookingFilter==='open'?'active':''}" data-filter="open">Needs attention</button><button class="filter-tab ${bookingFilter==='active'?'active':''}" data-filter="active">In progress</button><button class="filter-tab ${bookingFilter==='done'?'active':''}" data-filter="done">Completed</button></div><button class="button button-quiet" data-refresh>↻ Refresh</button></div><div class="panel data-panel"><div class="table-wrap"><table><thead><tr><th>Customer / ticket</th><th>Service</th><th>Appointment</th><th>Care partner</th><th>Status</th><th>Actions</th></tr></thead><tbody id="booking-rows">${rows.map(b=>`<tr data-search="${esc(`${b.customerName} ${category(b.category)} ${b.id} ${b.partnerName}`.toLowerCase())}" data-status="${esc(b.status)}">${bookingRow(b).replace('<tr>','').replace('</tr>','')}<td><button class="small-action" data-ticket="${esc(b.id)}">View ticket</button> ${bookingAction(b)}</td></tr>`).join('')}</tbody></table></div>${rows.length?'':empty('No bookings in this category','Choose another service or create a demo booking.')}</div><div class="booking-live-foot"><span class="live-pulse"></span><span>${api.demo?'Showcase records · temporary demo data':'Live bookings, partners and journeys · auto-refreshes while this view is open.'}</span><button class="text-link" data-refresh>Refresh now</button></div>`;
  }
  function categoryIcon(id) { return ({NURSING:'✚',AYURVEDIC_THERAPY:'◉',COUNSELLING:'♡'})[id]||'✳'; }
  function showBookingTicket(id) {
    const b=bookings.find(x=>String(x.id)===String(id));if(!b)return;const j=journeys.find(x=>String(x.bookingId)===String(id)),p=partners.find(x=>(b.partnerId&&String(x.id)===String(b.partnerId))||(b.partnerName&&x.name===b.partnerName)),loc=j?.exactLocation,phone=p?.phone||'';
    const map=loc&&Number.isFinite(Number(loc.lat))&&Number.isFinite(Number(loc.lng))?`https://maps.google.com/?q=${loc.lat},${loc.lng}`:'';
    const steps=['ASSIGNED','CONFIRMED','PARTNER_ON_THE_WAY','PARTNER_ARRIVED','IN_PROGRESS','COMPLETED'],idx=steps.indexOf(b.status);
    $('#modal-layer').innerHTML=`<div class="modal-backdrop" data-modal-close><section class="modal-card ticket-card"><button class="modal-x" data-modal-close>×</button><span class="eyebrow"><i></i>SERVICE TICKET · ${esc(String(b.id).slice(0,15))}</span><h2>${esc(category(b.category))} visit</h2><p>${esc(b.customerName)} · ${esc(b.address||'Home service')}</p><div class="ticket-facts"><span><small>APPOINTMENT</small><b>${esc(dateLabel(b.date))} · ${esc(timeLabel(b.time))}</b></span><span><small>VISIT STATUS</small>${pill(b.status)}</span></div><div class="ticket-section"><span class="panel-kicker">SERVICE PROGRESS</span><div class="progress-steps">${steps.map((s,i)=>`<span class="progress-step ${idx>=i?'passed':''} ${s===b.status?'current':''}"><i>${idx>=i?'✓':i+1}</i><small>${esc(statusName(s))}</small></span>`).join('')}</div></div><div class="ticket-section"><span class="panel-kicker">ASSIGNED CARE PARTNER</span><div class="contact-person"><span class="avatar">${initials(b.partnerName)}</span><div>${b.partnerName?`<button class="partner-link" data-partner-open="${esc(b.partnerId||'')}" data-partner-name="${esc(b.partnerName)}">${esc(b.partnerName)} ↗</button>`:'<strong>Awaiting assignment</strong>'}<small>${esc((p?.skills||[]).map(s=>category(s.category)).join(' · ')||b.skill||'')}</small></div>${phone?`<a class="call-button" href="tel:${esc(phone)}">☎ Call partner</a>`:'<span class="muted-copy">Phone unavailable</span>'}</div></div><div class="ticket-section tracking-box"><div class="tracking-head"><span class="panel-kicker">SERVICE LOCATION TRACKING</span><button class="text-link" data-refresh-ticket="${esc(b.id)}">↻ Update</button></div>${loc?`<div class="location-live"><span class="live-pulse"></span><strong>Partner location received</strong><small>Last update: ${esc(j.partnerSeenAt?new Date(j.partnerSeenAt).toLocaleString('en-IN'):'recently')}</small><span class="location-coords">${Number(loc.lat).toFixed(5)}, ${Number(loc.lng).toFixed(5)}</span>${j.eta?`<span>ETA ${esc(new Date(j.eta).toLocaleTimeString('en-IN',{hour:'2-digit',minute:'2-digit'}))}</span>`:''}${j.distanceKm!=null?`<span>${Number(j.distanceKm).toFixed(1)} km from service location</span>`:''}${map?`<a class="map-link" href="${map}" target="_blank" rel="noopener">Open location in map ↗</a>`:''}</div>`:`<div class="location-empty"><span>◎</span><strong>${api.demo?'Demo location is simulated':'No fresh GPS location'}</strong><small>${api.demo?'Showcase data has service status but no real GPS coordinates.':'Partner GPS appears here during an active visit when a fresh location ping is available.'}</small></div>`}</div><div class="ticket-actions"><button class="button button-quiet" data-modal-close>Close ticket</button>${phone?`<a class="button button-primary" href="tel:${esc(phone)}">☎ Call partner</a>`:''}</div></section></div>`;bindModal();
  }
  function showPartnerTicket(id,name) {
    const p=partners.find(x=>(id&&String(x.id)===String(id))||x.name===name);if(!p){toast('Partner details are not present in this account response.');return;}const assigned=bookings.filter(b=>(b.partnerId&&String(b.partnerId)===String(p.id))||(!b.partnerId&&b.partnerName===p.name)),j=journeys.find(x=>String(x.partnerId)===String(p.id)),loc=j?.exactLocation,phone=p.phone||'',map=loc?`https://maps.google.com/?q=${loc.lat},${loc.lng}`:'';
    $('#modal-layer').innerHTML=`<div class="modal-backdrop" data-modal-close><section class="modal-card ticket-card"><button class="modal-x" data-modal-close>×</button><span class="eyebrow"><i></i>PARTNER SERVICE TICKET</span><div class="partner-ticket-head"><span class="avatar profile-avatar">${initials(p.name)}</span><div><h2>${esc(p.name)}</h2><p>${esc(p.code||String(p.id||'').slice(0,12))} · ${pill(p.registrationStatus)}</p></div></div><div class="ticket-facts"><span><small>AVAILABILITY</small><b>${p.available?'Available now':'Unavailable'}</b></span><span><small>ACTIVE ASSIGNMENTS</small><b>${assigned.filter(b=>!['COMPLETED','CANCELLED','REJECTED'].includes(b.status)).length}</b></span></div><div class="ticket-section"><span class="panel-kicker">SKILLS</span><div class="skill-chip-list">${(p.skills||[]).map(s=>`<span class="skill-chip">${esc(category(s.category))}${s.code?` · ${esc(s.code)}`:''}</span>`).join('')||'<span class="muted-copy">No skills returned</span>'}</div></div><div class="ticket-section"><span class="panel-kicker">DIRECT CONTACT</span><div class="contact-actions">${phone?`<a class="call-button" href="tel:${esc(phone)}">☎ Call ${esc(p.name)}</a><span>${esc(phone)}</span>`:'<span class="muted-copy">Partner phone is not present in this API response.</span>'}</div></div><div class="ticket-section tracking-box"><div class="tracking-head"><span class="panel-kicker">LIVE PARTNER LOCATION</span><button class="text-link" data-refresh-ticket="partner" data-partner-open="${esc(p.id)}" data-partner-name="${esc(p.name)}">↻ Update</button></div>${loc?`<div class="location-live"><span class="live-pulse"></span><strong>Fresh journey location</strong><small>${esc(j.partnerSeenAt?new Date(j.partnerSeenAt).toLocaleString('en-IN'):'Recently updated')}</small><span class="location-coords">${Number(loc.lat).toFixed(5)}, ${Number(loc.lng).toFixed(5)}</span>${map?`<a class="map-link" href="${map}" target="_blank" rel="noopener">Open on map ↗</a>`:''}</div>`:`<div class="location-empty"><span>◎</span><strong>${api.demo?'Demo GPS is simulated':'No active journey location'}</strong><small>Location needs an active service journey and a fresh partner GPS update.</small></div>`}</div><div class="ticket-section"><span class="panel-kicker">RECENT ASSIGNMENTS</span>${assigned.length?`<div class="mini-booking-list">${assigned.slice(0,5).map(b=>`<button data-ticket="${esc(b.id)}"><span><strong>${esc(category(b.category))}</strong><small>${esc(b.customerName)} · ${esc(dateLabel(b.date))}</small></span>${pill(b.status)} →</button>`).join('')}</div>`:empty('No linked bookings','Partner assignments will appear here.')}</div><div class="ticket-actions"><button class="button button-quiet" data-modal-close>Close ticket</button>${phone?`<a class="button button-primary" href="tel:${esc(phone)}">☎ Call partner</a>`:''}</div></section></div>`;bindModal();
  }
  function bindModal() { const modal=$('#modal-layer');modal.querySelectorAll('[data-modal-close]').forEach(x=>x.addEventListener('click',closeModal));modal.querySelectorAll('[data-partner-open]').forEach(x=>x.addEventListener('click',()=>showPartnerTicket(x.dataset.partnerOpen,x.dataset.partnerName)));modal.querySelectorAll('[data-ticket]').forEach(x=>x.addEventListener('click',()=>showBookingTicket(x.dataset.ticket)));modal.querySelectorAll('[data-refresh-ticket]').forEach(x=>x.addEventListener('click',async()=>{try{await loadWorkspace();x.dataset.refreshTicket==='partner'?showPartnerTicket(x.dataset.partnerOpen,x.dataset.partnerName):showBookingTicket(x.dataset.refreshTicket);}catch(e){toast(e.message);}})); }
  function drawServices() {
    if(selectedService)return drawServiceDetail(selectedService);
    const action=(api.demo||(api.token&&isAdmin()))?'<button class="button button-primary" data-add-service>＋ Add service offering</button>':'';
    return `${heading('SERVICE MANAGEMENT','Service <em>categories.</em>','Open a category to inspect bookings, partner coverage and active visits.',action)}<div class="notice-card"><span>i</span><p><strong>Category controls</strong> Manage the active catalogue, service demand and partner coverage from each category.</p></div><div class="service-grid">${Object.entries(cats).map(([id,name],i)=>{const base=services.find(s=>s.id===id||(s.category===id&&s.serviceCode===id))||{id,name,active:true},rows=bookings.filter(b=>b.category===id),active=rows.filter(b=>!['COMPLETED','CANCELLED','REJECTED'].includes(b.status)).length,partnerCount=new Set(rows.filter(b=>b.partnerName).map(b=>b.partnerId||b.partnerName)).size,offerings=services.filter(s=>s.category===id&&s.id!==id&&s.serviceCode!==id);return `<article class="panel service-card service-manage-card"><span class="service-number">0${i+1}</span><span class="service-illustration tint-${i}">${categoryIcon(id)}</span><span class="panel-kicker">CARE CATEGORY</span><h2>${esc(name)}</h2><p>Review service load, qualified partners and appointment progress for this category.</p><div class="category-kpis"><span><b>${rows.length}</b> bookings</span><span><b>${partnerCount||partners.filter(p=>(p.skills||[]).some(s=>s.category===id)).length}</b> partners</span><span><b>${active}</b> active</span></div><div class="service-card-actions"><button class="button button-quiet" data-service-open="${id}">Manage category →</button>${(api.demo||(api.token&&isAdmin()))?`<button class="switch-button ${base.active===false?'':'is-on'}" data-action="service-toggle" data-id="${esc(base.id)}" data-active="${base.active===false}">${base.active===false?'Activate':'Pause'}</button>`:''}</div>${offerings.length?`<small class="offer-count">${offerings.length} custom offering${offerings.length===1?'':'s'}</small>`:''}</article>`;}).join('')}</div>`;
  }
  function drawServiceDetail(id) {
    const name=category(id),base=services.find(s=>s.id===id||(s.category===id&&s.serviceCode===id))||{id,name,active:true},rows=bookings.filter(b=>b.category===id),qualified=partners.filter(p=>(p.skills||[]).some(s=>s.category===id)),offerings=services.filter(s=>s.category===id&&s.id!==id&&s.serviceCode!==id);
    return `${heading('<button class="back-link inline-back" data-service-back>← All categories</button>',`${esc(name)} <em>management.</em>`,'Category bookings, qualified partner coverage and visit progress.',(api.demo||(api.token&&isAdmin()))?'<button class="button button-primary" data-add-service>＋ Add offering</button>':'')}<div class="category-detail-hero panel"><div><span class="panel-kicker">CATEGORY STATUS</span><h2>${esc(name)}</h2><p>${base.active===false?'Paused in the service catalogue.':'Active service category.'}</p></div><span class="status-pill ${base.active===false?'bad':'good'}"><i></i>${base.active===false?'Paused':'Active'}</span>${(api.demo||(api.token&&isAdmin()))?`<button class="button ${base.active===false?'button-primary':'button-quiet'}" data-action="service-toggle" data-id="${esc(base.id)}" data-active="${base.active===false}">${base.active===false?'Activate category':'Pause category'}</button><button class="button button-quiet" data-edit-service="${esc(base.id)}">Edit details</button>`:''}</div><div class="metric-grid">${metric('Bookings',rows.length,'Loaded service records','▤','blue')}${metric('Active visits',rows.filter(b=>!['COMPLETED','CANCELLED','REJECTED'].includes(b.status)).length,'Open visits','⌁','green')}${metric('Qualified partners',qualified.length,'Declared matching skills','♧','violet')}${metric('Available partners',qualified.filter(p=>p.active&&p.available&&p.registrationStatus==='APPROVED').length,'Approved and available','✓','amber')}</div><div class="panel assignment-panel"><div class="panel-title"><div><span class="panel-kicker">VISIT PIPELINE</span><h2>${esc(name)} bookings</h2></div></div>${rows.length?`<div class="table-wrap"><table><thead><tr><th>Customer</th><th>Service</th><th>Appointment</th><th>Partner</th><th>Progress</th><th></th></tr></thead><tbody>${rows.map(b=>`<tr>${bookingRow(b).replace('<tr>','').replace('</tr>','')}<td><button class="small-action" data-ticket="${esc(b.id)}">Ticket / track</button></td></tr>`).join('')}</tbody></table></div>`:empty(`No ${name} bookings yet`,'Appointments in this category will appear here.')}</div><div class="panel assignment-panel"><div class="panel-title"><div><span class="panel-kicker">PARTNER COVERAGE</span><h2>Qualified partners</h2></div></div>${qualified.length?`<div class="partner-grid">${qualified.map(partnerCard).join('')}</div>`:empty('No matching partners','Partners declaring this service skill will appear here.')}</div><div class="panel assignment-panel"><div class="panel-title"><div><span class="panel-kicker">SERVICE OFFERINGS</span><h2>Services in this category</h2></div>${(api.demo||(api.token&&isAdmin()))?'<button class="text-link" data-add-service>Add offering →</button>':''}</div>${offerings.length?`<div class="offering-list">${offerings.map(s=>`<div class="offering-row"><span class="category-mark">✳</span><div><strong>${esc(s.name)}</strong><small>${esc(s.description||'')}</small></div><span class="status-pill ${s.active?'good':'muted'}"><i></i>${s.active?'Active':'Paused'}</span>${(api.demo||(api.token&&isAdmin()))?`<button class="switch-button ${s.active?'is-on':''}" data-action="service-toggle" data-id="${esc(s.id)}" data-active="${!s.active}">${s.active?'Pause':'Activate'}</button><button class="switch-button" data-edit-service="${esc(s.id)}">Edit</button>`:''}</div>`).join('')}`:empty('No custom offerings',(api.demo||(api.token&&isAdmin()))?'Add an offering for this category.':'No additional offerings are registered for this category.')}</div>`;
  }
  function openServiceForm() { if(!api.demo)return toast('Live service-catalogue controls are not available.');$('#modal-layer').innerHTML=`<div class="modal-backdrop" data-modal-close><form class="modal-card" id="service-create"><button type="button" class="modal-x" data-modal-close>×</button><span class="eyebrow"><i></i>SERVICE CATALOGUE</span><h2>Add service offering</h2><p>New demo services are created active in this category.</p><label>Service name<input name="name" required maxlength="100" placeholder="e.g. Post-operative nursing"></label><label>Category<select name="category">${Object.entries(cats).map(([id,n])=>`<option value="${id}" ${selectedService===id?'selected':''}>${esc(n)}</option>`).join('')}</select></label><label>Description<input name="description" maxlength="240" placeholder="Describe the offering"></label><div class="modal-actions"><button type="button" class="button button-quiet" data-modal-close>Cancel</button><button type="submit" class="button button-primary">Create active service →</button></div></form></div>`;bindModal();$('#service-create')?.addEventListener('submit',createOffering); }
  async function createOffering(e) { e.preventDefault();const data=Object.fromEntries(new FormData(e.currentTarget));try{await demoRequest('/services',{method:'POST',body:JSON.stringify(data)});await loadWorkspace();selectedService=data.category;page='services';renderApp();toast('Active demo service created.');}catch(error){toast(error.message);} }
  function partnerCard(p) {
    const skills=(p.skills||[]).map(s=>`${category(s.category)}${s.code?` · ${s.code}`:''}`).join(' / ')||'Skills not listed';
    const action=api.demo&&p.registrationStatus==='PENDING'?`<button class="small-action" data-action="verify" data-id="${esc(p.id)}">Approve partner</button>`:api.token&&isAdmin()&&p.registrationStatus==='PENDING'?`<button class="small-action" data-action="verify" data-id="${esc(p.id)}">Approve partner</button>`:'';
    return `<article class="panel partner-card" data-partner="${esc(`${p.name} ${skills}`.toLowerCase())}" data-review="${p.registrationStatus==='PENDING'}" data-active="${Boolean(p.active)}"><div class="partner-card-head"><span class="avatar partner-avatar">${initials(p.name)}</span><div><h3><button class="partner-name-link" data-partner-open="${esc(p.id)}" data-partner-name="${esc(p.name)}">${esc(p.name||'Care partner')} ↗</button></h3><small>${esc(p.code||String(p.id||'').slice(0,10))}</small></div>${pill(p.registrationStatus)}</div><div class="partner-skills"><span class="panel-kicker">QUALIFICATIONS</span><p>${esc(skills)}</p></div><div class="partner-card-meta"><span>Completed visits <b>${esc(p.completedBookings??'—')}</b></span><span>Upcoming <b>${esc(p.upcomingBookings??'—')}</b></span></div>${p.available?'<span class="availability"><i></i>Available now</span>':'<span class="availability off"><i></i>Currently unavailable</span>'}${action}</article>`;
  }
  function openServiceForm() {
    if(!(api.demo||(api.token&&isAdmin())))return toast('Only an admin can add services.');
    $('#modal-layer').innerHTML=`<div class="modal-backdrop" data-modal-close><form class="modal-card" id="service-create"><button type="button" class="modal-x" data-modal-close>×</button><span class="eyebrow"><i></i>SERVICE CATALOGUE</span><h2>Add service offering</h2><p>New services are created active and can be paused or edited later.</p><label>Service name<input name="name" required maxlength="120" placeholder="e.g. Post-operative nursing"></label><label>Category<select name="category">${Object.entries(cats).map(([id,n])=>`<option value="${id}" ${selectedService===id?'selected':''}>${esc(n)}</option>`).join('')}</select></label><label>Service code <span>(optional, generated from name)</span><input name="serviceCode" maxlength="64" placeholder="e.g. POST_OP_NURSING"></label><label>Description<input name="description" maxlength="1000" placeholder="Describe this service"></label><div class="modal-actions"><button type="button" class="button button-quiet" data-modal-close>Cancel</button><button type="submit" class="button button-primary">Create active service →</button></div></form></div>`;bindModal();$('#service-create')?.addEventListener('submit',createOffering);
  }
  async function createOffering(e) {
    e.preventDefault();const data=Object.fromEntries(new FormData(e.currentTarget));
    try { if(api.demo)await demoRequest('/services',{method:'POST',body:JSON.stringify(data)});else await request('/v1/admin/services',{method:'POST',body:JSON.stringify(data)});await loadWorkspace();selectedService=data.category;page='services';renderApp();toast('Service created active.'); }
    catch(error){toast(error.message);}
  }
  function openServiceEditor(id) {
    const item=services.find(s=>String(s.id)===String(id));if(!item)return toast('Service record is not available.');
    $('#modal-layer').innerHTML=`<div class="modal-backdrop" data-modal-close><form class="modal-card" id="service-edit" data-id="${esc(item.id)}"><button type="button" class="modal-x" data-modal-close>×</button><span class="eyebrow"><i></i>SERVICE CATALOGUE</span><h2>Edit service</h2><p>${esc(category(item.category||item.serviceCode||item.id))} · ${esc(item.serviceCode||'Category service')}</p><label>Service name<input name="name" required maxlength="120" value="${esc(item.name)}"></label><label>Description<input name="description" maxlength="1000" value="${esc(item.description||'')}"></label><div class="modal-actions"><button type="button" class="button button-quiet" data-modal-close>Cancel</button><button type="submit" class="button button-primary">Save service →</button></div></form></div>`;bindModal();$('#service-edit')?.addEventListener('submit',saveServiceEdit);
  }
  async function saveServiceEdit(e) {
    e.preventDefault();const id=encodeURIComponent(e.currentTarget.dataset.id),data=Object.fromEntries(new FormData(e.currentTarget));
    try{if(api.demo)await demoRequest(`/services/${id}`,{method:'PATCH',body:JSON.stringify(data)});else await request(`/v1/admin/services/${id}`,{method:'PATCH',body:JSON.stringify(data)});await loadWorkspace();renderApp();toast('Service details saved.');}catch(error){toast(error.message);}
  }
  function drawReports() {
    const counts = Object.entries(cats).map(([id, name]) => [name, bookings.filter(b => b.category === id).length]);
    const max = Math.max(1, ...counts.map(x => x[1])); const completed = bookings.filter(b => b.status === 'COMPLETED').length;
    return `${heading('BUSINESS INSIGHTS', 'Operations <em>reports.</em>', 'A summary of the booking and service data available in this workspace.')}<div class="metric-grid">${metric('Bookings', bookings.length, 'Visible in current account', '▤', 'blue')}${metric('Completed visits', completed, 'Marked complete', '✓', 'green')}${metric('Completion rate', bookings.length ? `${Math.round(completed * 100 / bookings.length)}%` : '—', 'Based on loaded bookings', '◒', 'violet')}${metric('Revenue', '—', 'No payment or revenue API connected', '₹', 'amber')}</div><div class="report-grid"><article class="panel report-panel"><div class="panel-title"><div><span class="panel-kicker">SERVICE MIX</span><h2>Bookings by category</h2></div></div>${counts.map(([name,value]) => `<div class="bar-row"><span>${name}</span><div class="bar-track"><i style="width:${Math.max(value ? 8 : 0, value / max * 100)}%"></i></div><b>${value}</b></div>`).join('')}</article><article class="panel report-panel"><div class="panel-title"><div><span class="panel-kicker">DATA AVAILABILITY</span><h2>Workspace coverage</h2></div></div><div class="coverage-row"><span>Bookings</span>${pill('COMPLETED')}<small>Available</small></div><div class="coverage-row"><span>Partner roster</span>${partners.length ? pill('APPROVED') : pill('PENDING')}<small>${partners.length ? 'Available' : 'No records'}</small></div><div class="coverage-row"><span>Payments & payouts</span><span class="status-pill muted">Not connected</span><small>API unavailable</small></div><div class="coverage-row"><span>Employee performance</span><span class="status-pill muted">Not connected</span><small>API unavailable</small></div></article></div><p class="data-note">${api.demo ? 'All showcase metrics are calculated from in-memory demo records.' : 'Reports use only records your connected account can read. Revenue metrics are not inferred.'}</p>`;
  }
  function drawProfile() {
    const p = partners[0] || {};
    return `${heading('EMPLOYEE WORKSPACE', 'My <em>profile.</em>', 'Your signed-in identity and available operational scope.') }<article class="panel profile-card"><span class="avatar profile-avatar">${initials(api.subject)}</span><div><span class="panel-kicker">SIGNED-IN ACCOUNT</span><h2>${esc(api.demo ? 'Demo Operations Manager' : api.subject)}</h2><p>${esc(api.role.replaceAll('_', ' '))} · Employee portal</p></div></article><div class="notice-card"><span>i</span><p><strong>Access follows your backend role.</strong> This project supports Admin, Dispatcher, Operations Manager and partner/customer roles. It does not yet provide employee accounts, departments, designations or per-module permission management.</p></div>${api.demo ? '' : p.id ? `<article class="panel profile-details"><h2>Partner profile</h2><p>${esc(p.name || '')} · ${esc(p.registrationStatus || '')}</p></article>` : ''}`;
  }
  function empty(title, copy) { return `<div class="empty-state"><span>⌁</span><strong>${title}</strong><p>${copy}</p></div>`; }
  function bindApp() {
    $('[data-logout]')?.addEventListener('click', logout);
    $('[data-menu]')?.addEventListener('click', () => $('#sidebar').classList.toggle('open'));
    document.querySelectorAll('[data-refresh]').forEach(btn => btn.addEventListener('click', async () => { try { await loadWorkspace(); drawPage(); bindPage(); toast('Workspace refreshed.'); } catch (e) { toast(e.message); } }));
    bindPage();
  }
  function bindPage() {
    document.querySelectorAll('[data-page]').forEach(btn => btn.addEventListener('click', () => { page = btn.dataset.page; renderApp(); }));
    document.querySelectorAll('[data-open-bookings]').forEach(btn => btn.addEventListener('click', () => { page = 'bookings'; renderApp(); }));
    document.querySelectorAll('[data-category-filter]').forEach(btn => btn.addEventListener('click', () => { categoryFilter = btn.dataset.categoryFilter; bookingFilter = 'all'; renderApp(); }));
    document.querySelectorAll('[data-ticket]').forEach(btn => btn.addEventListener('click', () => showBookingTicket(btn.dataset.ticket)));
    document.querySelectorAll('[data-partner-open]').forEach(btn => btn.addEventListener('click', () => showPartnerTicket(btn.dataset.partnerOpen, btn.dataset.partnerName)));
    document.querySelectorAll('[data-service-open]').forEach(btn => btn.addEventListener('click', () => { selectedService = btn.dataset.serviceOpen; renderApp(); }));
    $('[data-service-back]')?.addEventListener('click', () => { selectedService = null; renderApp(); });
    document.querySelectorAll('[data-add-service]').forEach(btn => btn.addEventListener('click', openServiceForm));
    document.querySelectorAll('[data-add-partner]').forEach(btn => btn.addEventListener('click', openManualPartnerForm));
    document.querySelectorAll('[data-edit-service]').forEach(btn => btn.addEventListener('click', () => openServiceEditor(btn.dataset.editService)));
    $('#booking-search')?.addEventListener('input', filterBookings);
    document.querySelectorAll('[data-filter]').forEach(btn => btn.addEventListener('click', () => { document.querySelectorAll('[data-filter]').forEach(x => x.classList.toggle('active', x === btn)); filterBookings(btn.dataset.filter); }));
    $('#partner-search')?.addEventListener('input', filterPartners);
    document.querySelectorAll('[data-partner-filter]').forEach(btn => btn.addEventListener('click', () => { document.querySelectorAll('[data-partner-filter]').forEach(x => x.classList.toggle('active', x === btn)); filterPartners(btn.dataset.partnerFilter); }));
    document.querySelectorAll('[data-action]').forEach(btn => btn.addEventListener('click', () => performAction(btn)));
    document.querySelectorAll('[data-new-booking]').forEach(btn => btn.addEventListener('click', openBookingModal));
    $('#booking-create')?.addEventListener('submit', createBooking);
  }
  function filterBookings(filter = bookingFilter) {
    bookingFilter = filter;
    const q = ($('#booking-search')?.value || '').toLowerCase();
    document.querySelectorAll('#booking-rows tr').forEach(row => { const st = row.dataset.status; const statusMatch = filter === 'all' || filter === 'open' && ['NEW','PENDING','REJECTED'].includes(st) || filter === 'active' && ['ASSIGNED','CONFIRMED','PARTNER_ON_THE_WAY','PARTNER_NEARBY','PARTNER_ARRIVED','IN_PROGRESS'].includes(st) || filter === 'done' && st === 'COMPLETED'; row.hidden = !statusMatch || !row.dataset.search.includes(q); });
  }
  function filterPartners(filter = 'all') {
    const q = ($('#partner-search')?.value || '').toLowerCase();
    document.querySelectorAll('[data-partner]').forEach(card => { card.hidden = !(card.dataset.partner.includes(q) && (filter === 'all' || filter === 'pending' && card.dataset.review === 'true' || filter === 'available' && card.dataset.active === 'true')); });
  }
  async function performAction(btn) {
    btn.disabled = true;
    try {
      const id = encodeURIComponent(btn.dataset.id), action = btn.dataset.action;
      if (action === 'allocate') {
        if (api.demo) await demoRequest(`/bookings/${id}`, { method: 'PATCH', body: JSON.stringify({ status: 'PENDING' }) });
        else await request(`/v1/admin/bookings/${id}/allocation-start`, { method: 'POST' });
        toast('Booking moved to allocation.');
      } else if (action === 'search-partners') {
        await openPartnerRecommendations(id); return;
      } else if (action === 'verify') {
        if (api.demo) await demoRequest(`/partners/${id}`, { method: 'PATCH', body: JSON.stringify({ approved: true }) });
        else await request(`/v1/admin/partners/${id}/verification`, { method: 'PATCH', body: JSON.stringify({ approved: true }) });
        toast('Partner review updated.');
      } else if (action === 'save-status') {
        const status = $(`[data-status-select="${CSS.escape(btn.dataset.id)}"]`).value;
        await demoRequest(`/bookings/${id}`, { method: 'PATCH', body: JSON.stringify({ status }) }); toast('Demo booking status updated.');
      } else if (action === 'service-toggle') {
        const active = btn.dataset.active === 'true';
        if (api.demo) await demoRequest(`/services/${id}`, { method: 'PATCH', body: JSON.stringify({ active }) });
        else await request(`/v1/admin/services/${id}`, { method: 'PATCH', body: JSON.stringify({ active }) });
        toast(btn.dataset.active === 'true' ? 'Service activated in demo catalogue.' : 'Service paused in demo catalogue.');
      }
      await loadWorkspace(); renderApp();
    } catch (error) { toast(error.message); btn.disabled = false; }
  }
  async function openPartnerRecommendations(id) {
    const layer = $('#modal-layer');
    layer.innerHTML = `<div class="modal-backdrop"><section class="modal-card ticket-card"><span class="eyebrow"><i></i>POSTGIS NEAREST-PARTNER SEARCH</span><h2>Matching active partners</h2><p>Checking service skill, current availability, appointment schedule, service radius and fresh location…</p></section></div>`;
    try {
      const matches = await request(`/v1/admin/bookings/${encodeURIComponent(id)}/partner-recommendations`);
      const booking = bookings.find(b => String(b.id) === String(id));
      layer.innerHTML = `<div class="modal-backdrop" data-modal-close><section class="modal-card ticket-card"><button class="modal-x" data-modal-close>×</button><span class="eyebrow"><i></i>POSTGIS NEAREST-PARTNER SEARCH</span><h2>Assign a care partner</h2><p>${esc(booking ? `${booking.customerName} · ${category(booking.category)} · ${booking.address || ''}` : 'Eligible partners sorted by distance')}</p><div class="candidate-list">${matches.length ? matches.map((m,i)=>`<article class="candidate-row"><span class="avatar">${initials(m.name)}</span><div class="candidate-info"><strong>${esc(m.name)}</strong><small>${Number(m.distanceKm).toFixed(1)} km · Active · Available · Fresh location</small></div>${m.phone?`<a class="call-button" href="tel:${esc(m.phone)}">Call</a>`:''}<button class="button ${i===0?'button-primary':'button-quiet'}" data-assign-partner="${esc(m.partnerId)}" data-booking-id="${esc(id)}">${i===0?'Recommended · Assign':'Assign'}</button></article>`).join(''):'<div class="empty-state"><strong>No eligible active partners found</strong><p>Check partner GPS, active/available status, verified skill, working hours and service radius.</p></div>'}</div><div class="modal-actions"><button class="button button-quiet" data-modal-close>Close</button></div></section></div>`;
      bindModal();
      layer.querySelectorAll('[data-assign-partner]').forEach(button => button.addEventListener('click', async () => {
        button.disabled = true;
        try { await request(`/v1/admin/bookings/${encodeURIComponent(button.dataset.bookingId)}/assign`, {method:'PATCH', body:JSON.stringify({partnerId:button.dataset.assignPartner, expectedVersion:booking?.version ?? 0})}); await loadWorkspace(); renderApp(); toast('Partner assigned. Booking notifications queued.'); }
        catch (error) { toast(error.message); button.disabled = false; }
      }));
    } catch (error) { closeModal(); toast(error.message); }
  }
  function openManualPartnerForm() {
    $('#modal-layer').innerHTML = `<div class="modal-backdrop" data-modal-close><form class="modal-card" id="manual-partner-form"><button type="button" class="modal-x" data-modal-close>×</button><span class="eyebrow"><i></i>ADMIN PARTNER INTAKE</span><h2>Add partner manually</h2><p>Saved to the partner database as pending verification. Add an existing hub ID; no password or login is created here.</p><label>Operating hub ID<input name="centreId" required placeholder="Hub UUID"></label><div class="form-row"><label>Full name<input name="name" required></label><label>Phone<input name="phone" type="tel" required></label></div><div class="form-row"><label>Email<input name="email" type="email" required></label><label>Service radius (km)<input name="serviceRadiusKm" type="number" min="1" max="500" value="15" required></label></div><label>Qualification details<input name="qualificationDetails" required placeholder="Qualification / licence details"></label><div class="form-row"><label>Service category<select name="category"><option value="NURSING">Nursing</option><option value="AYURVEDIC_THERAPY">Ayurvedic therapy</option><option value="COUNSELLING">Counselling</option></select></label><label>Skill code<input name="skillCode" required placeholder="e.g. HOME_NURSING"></label></div><label>Skill qualification<input name="skillQualification" required></label><div class="modal-actions"><button type="button" class="button button-quiet" data-modal-close>Cancel</button><button class="button button-primary" type="submit">Save pending partner</button></div></form></div>`;
    bindModal();
    $('#manual-partner-form').addEventListener('submit', async e => {
      e.preventDefault(); const f = new FormData(e.currentTarget);
      const payload = {centreId:f.get('centreId'), name:f.get('name'), email:f.get('email'), phone:f.get('phone'), qualificationDetails:f.get('qualificationDetails'), serviceRadiusKm:Number(f.get('serviceRadiusKm')), skills:[{category:f.get('category'), code:f.get('skillCode'), qualificationDetails:f.get('skillQualification')}]};
      try { await request('/v1/admin/partners',{method:'POST',body:JSON.stringify(payload)}); await loadWorkspace(); page='partners'; renderApp(); toast('Partner profile saved as pending verification.'); }
      catch(error) { toast(error.message); }
    });
  }
  function openBookingModal() {
    if (!api.demo) return toast('Creating a booking requires a CUSTOMER account in the current backend.');
    $('#modal-layer').innerHTML = `<div class="modal-backdrop" data-modal-close><form class="modal-card" id="booking-create"><button type="button" class="modal-x" data-modal-close>×</button><span class="eyebrow"><i></i>NEW SERVICE REQUEST</span><h2>Create a booking</h2><p>Add a showcase appointment to the shared demo workspace.</p><label>Customer name<input name="name" required placeholder="Customer full name"></label><div class="form-row"><label>Service category<select name="category"><option value="NURSING">Nursing</option><option value="AYURVEDIC_THERAPY">Ayurvedic therapy</option><option value="COUNSELLING">Counselling</option></select></label><label>Required skill<input name="skill" required placeholder="e.g. Home care"></label></div><div class="form-row"><label>Appointment date<input name="date" type="date" value="${dateToday()}" required></label><label>Start time<input name="time" type="time" value="10:00" required></label></div><label>Service address<input name="address" required placeholder="Area, city"></label><div class="modal-actions"><button type="button" class="button button-quiet" data-modal-close>Cancel</button><button type="submit" class="button button-primary">Create booking →</button></div></form></div>`;
    $('[data-modal-close]', $('#modal-layer'))?.addEventListener('click', closeModal);
    $('#booking-create')?.addEventListener('submit', createBooking);
  }
  async function createBooking(e) {
    e.preventDefault(); const data = Object.fromEntries(new FormData(e.currentTarget));
    try { await demoRequest('/bookings', { method: 'POST', body: JSON.stringify(data) }); await loadWorkspace(); page = 'bookings'; renderApp(); toast('Demo booking created.'); }
    catch (error) { toast(error.message); }
  }
  function closeModal(e) { if (e?.target?.closest('.modal-card') && !e.target.classList.contains('modal-x') && !e.target.hasAttribute('data-modal-close')) return; $('#modal-layer').innerHTML = ''; }
  async function logout() {
    api.token = ''; api.role = ''; api.subject = ''; api.demo = false; api.portal = ''; page = 'dashboard';
    for (const k of ['token', 'role', 'subject', 'portal', 'mode']) localStorage.removeItem(`sathiroute.${k}`);
    openLogin('');
  }
  let timer;
  function toast(message) { const node = $('#toast'); if (!node) return; node.textContent = message; node.classList.add('show'); clearTimeout(timer); timer = setTimeout(() => node.classList.remove('show'), 3600); }
  async function initialize() {
    if (!api.demo && api.token && api.role && api.portal) {
      try { await loadWorkspace(); renderApp(); return; }
      catch { api.token = ''; api.role = ''; api.subject = ''; api.portal = ''; }
    }
    if (api.demo && api.portal) {
      try { await loadWorkspace(); renderApp(); return; }
      catch { api.demo = false; api.portal = ''; }
    }
    openLogin('');
  }
  window.setInterval(async () => {
    if (api.demo || !api.token || document.visibilityState !== 'visible' || !['bookings','services'].includes(page)) return;
    if ($('#booking-search') && document.activeElement === $('#booking-search')) return;
    try { await loadWorkspace(); drawPage(); bindPage(); } catch { /* Keep the last successfully loaded snapshot on transient errors. */ }
  }, 20000);
  initialize();
})();
