(() => {
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const api = { base: localStorage.getItem('sathiroute.apiBase') || '', token: localStorage.getItem('sathiroute.token') || '', role: localStorage.getItem('sathiroute.role') || '', subject: localStorage.getItem('sathiroute.subject') || '', demo: false };
  const today = new Date();
  let currentPage = 'overview';
  let bookings = [];
  let partners = [];
  let journeys = [];
  let routes = [];
  let shipments = [];
  let services = [];
  let demoActivity = [];
  let demoMetrics = null;
  let bookingFilter = 'all';
  let partnerFilter = 'all';
  let routeDay = new Date();

  function isoDate(date) { return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10); }
  function prettyDate(date, options = { weekday: 'long', month: 'long', day: 'numeric' }) { return new Intl.DateTimeFormat('en-IN', options).format(date); }
  const categoryLabel = value => ({ NURSING: 'Nursing', AYURVEDIC_THERAPY: 'Ayurvedic Therapy', COUNSELLING: 'Counselling' }[value] || value || 'Care visit');
  const statusLabel = value => ({ NEW: 'New', PENDING: 'Awaiting partner', ASSIGNED: 'Partner assigned', CONFIRMED: 'Confirmed', PARTNER_ON_THE_WAY: 'On the way', PARTNER_NEARBY: 'Nearby', PARTNER_ARRIVED: 'Arrived', IN_PROGRESS: 'In progress', COMPLETED: 'Completed', CANCELLED: 'Cancelled', REJECTED: 'Rejected', PLANNED: 'Planned', IN_TRANSIT: 'In transit', OUT_FOR_DELIVERY: 'Out for delivery', DELIVERED: 'Delivered', FAILED_ATTEMPT: 'Failed attempt', 'Needs partner': 'Awaiting partner' }[value] || value || 'Scheduled');
  const statusClass = value => ({ PENDING: 'pending', NEW: 'pending', ASSIGNED: 'assigned', CONFIRMED: 'confirmed', PARTNER_ON_THE_WAY: 'assigned', PARTNER_NEARBY: 'assigned', PARTNER_ARRIVED: 'confirmed', IN_PROGRESS: 'in-progress', COMPLETED: 'completed', CANCELLED: 'cancelled', REJECTED: 'rejected' }[value] || 'pending');
  const initials = name => (name || 'SP').split(/\s+/).filter(Boolean).slice(0, 2).map(s => s[0]).join('').toUpperCase();
  const categoryClass = category => category === 'AYURVEDIC_THERAPY' ? 'ayurvedic' : category === 'COUNSELLING' ? 'counselling' : '';
  const fmtTime = value => { if (!value) return '—'; const [h, m] = String(value).slice(0, 5).split(':').map(Number); if (Number.isNaN(h)) return value; return `${(h % 12) || 12}:${String(m).padStart(2, '0')} ${h >= 12 ? 'PM' : 'AM'}`; };
  const timeFrom = value => { try { return new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit' }).format(new Date(value)); } catch { return '—'; } };
  const dateFromIso = value => value ? new Date(`${value}T00:00:00`) : today;
  const statusPill = status => `<span class="status status-${statusClass(status)}">${escapeHtml(statusLabel(status))}</span>`;
  function bookingAction(b) {
    if (!api.token && !api.demo) return '';
    if (api.role === 'SERVICE_PARTNER') {
      if (b.status === 'ASSIGNED') return `<button class="inline-action-button" data-booking-action="accept" data-id="${escapeHtml(b.id)}" data-version="${b.version}">Accept</button><button class="inline-action-button" data-booking-action="reject" data-id="${escapeHtml(b.id)}" data-version="${b.version}">Decline</button>`;
      if (b.status === 'CONFIRMED') return `<button class="inline-action-button" data-booking-action="arrive" data-id="${escapeHtml(b.id)}" data-version="${b.version}">Arrived</button>`;
      if (b.status === 'PARTNER_ARRIVED') return `<button class="inline-action-button" data-booking-action="start" data-id="${escapeHtml(b.id)}" data-version="${b.version}">Start visit</button>`;
      if (b.status === 'IN_PROGRESS') return `<button class="inline-action-button" data-booking-action="complete" data-id="${escapeHtml(b.id)}" data-version="${b.version}">Complete</button>`;
    }
    if (api.role === 'CUSTOMER' && b.completionPending) return `<button class="inline-action-button" data-booking-action="confirm" data-id="${escapeHtml(b.id)}">Confirm</button>`;
    if (['ADMIN','DISPATCHER','OPERATIONS_MANAGER'].includes(api.role) && ['NEW','REJECTED'].includes(b.status)) return `<button class="inline-action-button" data-booking-action="allocate" data-id="${escapeHtml(b.id)}">Queue allocation</button>`;
    return '';
  }
  function escapeHtml(value) { return String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }

  function apiUrl(path) { return `${api.base.replace(/\/$/, '')}${path}`; }
  function jwtClaims(token) { try { return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))); } catch { return {}; } }
  function jwtRole(token) {
    const c = jwtClaims(token); const values = [c.roles, c.authorities, c.realm_access?.roles, ...Object.values(c.resource_access || {}).map(v => v?.roles)].flat().filter(v => typeof v === 'string').map(v => v.toUpperCase().replace(/^ROLE_/, ''));
    return ['ADMIN','DISPATCHER','OPERATIONS_MANAGER','SERVICE_PARTNER','CUSTOMER'].find(r => values.includes(r)) || (c.sub === 'dispatch@routemesh.example.test' ? 'ADMIN' : 'CUSTOMER');
  }
  async function request(path, options = {}) {
    if (!api.token) throw new Error('Connect an API token to load live data.');
    const response = await fetch(apiUrl(path), { ...options, headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), Authorization: `Bearer ${api.token}`, ...(options.headers || {}) } });
    if (!response.ok) { let detail = `${response.status} ${response.statusText}`; try { const problem = await response.json(); detail = problem.detail || problem.title || detail; } catch {} const error = new Error(detail); error.status = response.status; throw error; }
    if (response.status === 204) return null;
    return response.json();
  }
  async function demoRequest(path, options = {}) {
    const response = await fetch(`/dev/demo${path}`, { ...options, headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(options.headers || {}) } });
    if (!response.ok) { let detail = `${response.status} ${response.statusText}`; try { const problem = await response.json(); detail = problem.detail || problem.title || detail; } catch {} throw new Error(detail); }
    if (response.status === 204) return null;
    return response.json();
  }
  async function fetchAllBookings(path) {
    const all = []; const seen = new Set();
    for (let offset = 0; offset < 100000; offset += 100) {
      const page = await request(`${path}?limit=100&offset=${offset}`);
      if (!Array.isArray(page)) throw new Error('Booking API returned an unexpected response.');
      let added = 0;
      page.forEach(item => { const id = String(item.id || item.bookingId || ''); if (!id || !seen.has(id)) { if (id) seen.add(id); all.push(item); added += 1; } });
      if (page.length < 100 || added === 0) break;
    }
    return all;
  }
  function setLive(live) {
    document.body.classList.toggle('live-mode', live);
    document.body.classList.toggle('demo-mode', api.demo);
    $('#demo-mode-indicator').hidden = !api.demo;
    $('#demo-banner').classList.toggle('hidden', live);
    $('#connect-label').textContent = api.demo ? 'Demo Admin' : live ? `${api.role || 'API'} connected` : 'Connect API';
    $('#sidebar-account-name').textContent = api.demo ? 'Demo Admin' : live ? (api.subject || api.role || 'API account') : 'Signed out';
    $('#sidebar-account-role').textContent = api.demo ? 'Showcase workspace' : live ? `${api.role || 'Connected'} account` : 'Connect to API';
    $('#sidebar-avatar').textContent = api.demo ? 'DA' : live ? initials(api.subject || api.role) : 'SR';
    $('#settings-connection-state').textContent = api.demo ? 'Demo API connected · in-memory data' : live ? `Connected as ${api.role}` : 'Not connected';
    $('.connect-button').classList.toggle('connected', live);
    $$('.live-pill').forEach(pill => {
      const label = [...pill.childNodes].find(node => node.nodeType === Node.TEXT_NODE);
      if (label) label.textContent = api.demo ? ' DEMO' : live ? ' LIVE DATA' : ' CONNECT API';
      pill.classList.toggle('offline', !live);
    });
    $('#metric-total-bookings').dataset.live = live ? '1' : '';
  }
  function normalizeBooking(b, index = 0) {
    const start = b.appointmentStartAt || b.appointmentStart || b.startsAt || b.appointmentStartAt;
    const partner = b.partnerName || b.assignedPartnerName || (b.assignedPartnerId ? partners.find(p => p.id === b.assignedPartnerId)?.name : '') || 'Unassigned';
    const date = b.appointmentDate || (start ? isoDate(new Date(start)) : isoDate(today));
    const time = b.appointmentStart || (start ? new Date(start).toTimeString().slice(0, 5) : '10:00');
    return { id: b.id || b.bookingId || `live-${index}`, customerName: b.customerName || 'Customer', email: b.customerEmail || '', category: b.category || b.serviceCategory || 'NURSING', skill: b.requiredSkill || b.skill || 'Home care', date, time, duration: b.durationMinutes || b.serviceDurationMinutes || 60, partnerName: partner, partnerId: b.assignedPartnerId || '', status: String(b.status || 'NEW'), version: b.version ?? 0, address: b.address || 'Address on booking', createdAt: b.createdAt, updatedAt: b.updatedAt, avatar: initials(b.customerName), color: 'mint' };
  }
  function normalizePartner(p) { return { ...p, name: p.name || 'Care partner', registrationStatus: p.registrationStatus || 'PENDING', skills: p.skills || [], avatar: initials(p.name), tint: 'mint', upcoming: p.upcomingBookings ?? null, completed: p.completedBookings ?? null }; }
  function normalizeJourney(j) { return { ...j, customerName: j.customerName || 'Customer visit', service: `${statusLabel(j.status)} · ${j.distanceKm == null ? 'Location updating' : `${Number(j.distanceKm).toFixed(1)} km away`}` }; }
  async function connectLive() {
    if (api.demo) return connectDemo(false);
    if (!api.role) api.role = jwtRole(api.token);
    setLive(true); bookings = []; partners = []; journeys = []; routes = [];
    let results;
    if (['ADMIN','DISPATCHER','OPERATIONS_MANAGER'].includes(api.role)) {
      results = await Promise.allSettled([
        fetchAllBookings('/v1/admin/bookings'), request('/v1/admin/partners?status=APPROVED'),
        request('/v1/admin/partners?status=PENDING'), request('/v1/admin/tracking/partners'),
        request('/v1/admin/service-executions')
      ]);
      const [adminBookings, approved, pending, liveJourneys, executions] = results;
      if (approved.status === 'fulfilled' && Array.isArray(approved.value)) partners = approved.value.map(normalizePartner);
      if (pending.status === 'fulfilled' && Array.isArray(pending.value)) partners = [...partners, ...pending.value.map(normalizePartner)];
      if (adminBookings.status === 'fulfilled' && Array.isArray(adminBookings.value)) bookings = adminBookings.value.map(normalizeBooking);
      if (liveJourneys.status === 'fulfilled' && Array.isArray(liveJourneys.value)) journeys = liveJourneys.value.map(j => { const booking = bookings.find(b => b.id === j.bookingId); return normalizeJourney({ ...j, customerName: booking?.customerName, service: booking ? `${categoryLabel(booking.category)} - ${booking.address}` : statusLabel(j.status) }); });
      if (executions.status === 'fulfilled' && Array.isArray(executions.value)) {
        for (const execution of executions.value) {
          const booking = bookings.find(b => b.id === execution.bookingId);
          if (booking) { booking.partnerName = execution.partnerName || booking.partnerName; booking.status = execution.bookingStatus || booking.status; }
        }
      }
      if (approved.status === 'fulfilled') await loadLiveRoutes();
    } else if (api.role === 'SERVICE_PARTNER') {
      results = await Promise.allSettled([request('/v1/partners/me'), fetchAllBookings('/v1/partners/me/bookings'), request('/v1/partners/me/workload')]);
      const [profile, assigned, workload] = results;
      if (profile.status === 'fulfilled') partners = [normalizePartner({ ...profile.value, ...(workload.status === 'fulfilled' ? workload.value : {}) })];
      if (assigned.status === 'fulfilled' && Array.isArray(assigned.value)) bookings = assigned.value.map(normalizeBooking);
      const active = bookings.filter(b => ['CONFIRMED','PARTNER_ON_THE_WAY','PARTNER_NEARBY','PARTNER_ARRIVED','IN_PROGRESS'].includes(b.status));
      journeys = active.map(b => ({ bookingId: b.id, partnerId: partners[0]?.id, partnerName: partners[0]?.name || 'You', customerName: b.customerName, service: `${categoryLabel(b.category)} · ${b.address}`, status: b.status, distanceKm: null, eta: null }));
      if (partners[0]?.id) {
        const routeResult = await Promise.allSettled([request(`/v1/partners/${encodeURIComponent(partners[0].id)}/routes/${isoDate(routeDay)}`)]);
        if (routeResult[0].status === 'fulfilled') routes = [routeResult[0].value].filter(Boolean).map(route => ({ partner: partners[0].name, initials: partners[0].avatar, skills: partners[0].skills.map(s => categoryLabel(s.category)).join(' · '), km: route.distanceKm || 0, visits: (route.stops || []).map(s => { const booking = bookings.find(b => b.id === s.bookingId); return { ...s, customerName: booking?.customerName || s.bookingId, category: booking?.category, skill: booking?.skill, duration: booking?.duration, time: s.plannedArrival ? timeFrom(s.plannedArrival) : '—' }; }), tint: 'mint' }));
      }
    } else {
      results = await Promise.allSettled([request('/v1/customers/me'), fetchAllBookings('/v1/bookings')]);
      const [profile, ownBookings] = results;
      if (profile.status === 'fulfilled') api.subject = profile.value.subject || api.subject;
      if (ownBookings.status === 'fulfilled' && Array.isArray(ownBookings.value)) bookings = ownBookings.value.map(normalizeBooking);
      const completionChecks = await Promise.allSettled(bookings.filter(b => b.status === 'IN_PROGRESS').map(b => request(`/v1/bookings/${encodeURIComponent(b.id)}/service-execution`)));
      completionChecks.forEach((result, i) => { if (result.status === 'fulfilled') { const execution = result.value?.execution; const booking = bookings.filter(b => b.status === 'IN_PROGRESS')[i]; if (booking) booking.completionPending = Boolean(execution?.completionSubmittedAt && !execution?.customerConfirmedAt); } });
      const active = bookings.filter(b => !['COMPLETED','CANCELLED','REJECTED','NEW','PENDING'].includes(b.status));
      const tracking = await Promise.allSettled(active.map(b => request(`/v1/bookings/${encodeURIComponent(b.id)}/tracking`)));
      journeys = tracking.flatMap((r, i) => r.status === 'fulfilled' ? [normalizeJourney({ ...r.value, customerName: active[i].customerName, service: `${categoryLabel(active[i].category)} · ${active[i].address}` })] : []);
    }
    const errors = results.filter(r => r.status === 'rejected').map(r => r.reason?.message).filter(Boolean);
    const validTokenResponse = results.some(r => r.status === 'fulfilled' || r.reason?.status === 404);
    if (!validTokenResponse) { setLive(false); throw new Error(errors[0] || 'Could not load live API data. Check your account role.'); }
    renderAll();
    if (errors.length) toast(`Connected as ${api.role}; some role-restricted panels are unavailable.`);
    else toast(`Connected to live SathiRoute data as ${api.role}.`);
  }
  async function connectDemo(announce = true) {
    const data = await demoRequest('/workspace');
    api.demo = true; api.token = ''; api.role = 'ADMIN'; api.subject = 'Demo Admin';
    localStorage.removeItem('sathiroute.token'); localStorage.setItem('sathiroute.role', 'ADMIN'); localStorage.setItem('sathiroute.mode', 'demo');
    bookings = (data.bookings || []).map(normalizeBooking);
    partners = (data.partners || []).map(normalizePartner);
    journeys = (data.journeys || []).map(normalizeJourney);
    routes = data.routes || []; shipments = data.shipments || []; services = data.services || [];
    demoActivity = data.activity || []; demoMetrics = data.metrics || null;
    setLive(true); renderAll();
    if (announce) { closeModal('connect-modal'); toast('Demo workspace loaded. All changes are temporary showcase data.'); }
  }
  async function loadLiveRoutes() {
    const date = isoDate(routeDay);
    const settled = await Promise.allSettled(partners.filter(p => p.registrationStatus === 'APPROVED').slice(0, 20).map(p => request(`/v1/partners/${encodeURIComponent(p.id)}/routes/${date}`).then(route => ({ partner: p, route }))));
    routes = settled.filter(x => x.status === 'fulfilled' && x.value.route).map((x, index) => {
      const { partner, route } = x.value;
      const stops = route.stops || [];
      return { partner: partner.name, initials: initials(partner.name), skills: (partner.skills || []).map(s => categoryLabel(s.category)).join(' · ') || partner.code, km: route.distanceKm ?? route.km ?? 0, visits: stops.map(s => { const booking = bookings.find(b => b.id === s.bookingId); return { id: s.bookingId, customerName: booking?.customerName || s.bookingId, category: booking?.category, skill: booking?.skill, time: s.plannedArrival ? timeFrom(s.plannedArrival) : '—', duration: booking?.duration }; }), tint: ['mint', 'sand', 'lilac'][index % 3] };
    });
  }

  function renderAll() {
    const canCreateBooking = api.demo || api.role === 'CUSTOMER';
    $$('[data-action="new-booking"]').forEach(button => { button.hidden = !canCreateBooking; });
    $$('[data-action="add-partner"], [data-action="create-shipment"]').forEach(button => { button.hidden = !api.demo; });
    renderVisits(); renderBookings(); renderPartners(); renderRoutes(); renderJourneys(); renderActivity();
    renderShipments(); renderServices(); renderOperations(); renderReports(); renderOverviewPreviews(); updateMetrics();
  }
  function renderVisits() {
    const next = bookings.filter(b => b.date === isoDate(today) && !['COMPLETED', 'CANCELLED', 'REJECTED'].includes(b.status)).sort((a, b) => a.time.localeCompare(b.time)).slice(0, 4);
    $('#visit-list').innerHTML = next.length ? next.map(b => `<div class="visit-item"><div class="visit-time">${fmtTime(b.time)}<small>${b.duration} min</small></div><i class="visit-rail"></i><div class="visit-info"><strong>${escapeHtml(b.customerName)}</strong><span>${escapeHtml(categoryLabel(b.category))} · ${escapeHtml(b.partnerName)}</span></div><div class="visit-avatar ${categoryClass(b.category) === 'ayurvedic' ? 'therapist' : categoryClass(b.category) === 'counselling' ? 'counsel' : ''}">${escapeHtml(initials(b.partnerName))}</div></div>`).join('') : `<div class="empty-state"><strong>No upcoming visits</strong><p>New appointments will appear here.</p></div>`;
  }
  function renderBookings() {
    const query = ($('#booking-search')?.value || '').trim().toLowerCase();
    let filtered = bookings.filter(b => {
      const s = statusLabel(b.status);
      const matchesFilter = bookingFilter === 'all' || (bookingFilter === 'Assigned' ? ['ASSIGNED','CONFIRMED','PARTNER_ON_THE_WAY','PARTNER_NEARBY','PARTNER_ARRIVED','IN_PROGRESS'].includes(b.status) : bookingFilter === 'Pending' ? ['NEW','PENDING'].includes(b.status) : b.status === 'COMPLETED');
      return matchesFilter && `${b.customerName} ${b.partnerName} ${b.skill} ${categoryLabel(b.category)} ${b.id}`.toLowerCase().includes(query) && (s || true);
    });
    $('#bookings-body').innerHTML = filtered.map(b => `<tr><td><div class="customer-cell"><span class="avatar avatar-${escapeHtml(b.color || 'mint')}">${escapeHtml(b.avatar || initials(b.customerName))}</span><span>${escapeHtml(b.customerName)}<small>${escapeHtml(b.id)}</small></span></div></td><td><div class="service-cell"><i class="service-swatch ${categoryClass(b.category)}"></i>${escapeHtml(categoryLabel(b.category))}<small style="display:block;color:#a0aaa3;margin-left:0">${escapeHtml(b.skill || '')}</small></div></td><td>${escapeHtml(fmtTime(b.time))}<small style="display:block;color:#a0aaa3;margin-top:3px">${escapeHtml(prettyDate(dateFromIso(b.date), { month: 'short', day: 'numeric' }))} · ${b.duration} min</small></td><td>${escapeHtml(b.partnerName || 'Unassigned')}</td><td>${statusPill(b.status)}</td><td>${bookingAction(b)}</td></tr>`).join('');
    $('#bookings-empty').classList.toggle('hidden', filtered.length > 0);
    $('#booking-count').textContent = `Showing ${filtered.length} loaded ${filtered.length === 1 ? 'visit' : 'visits'}`;
    $('#booking-nav-count').textContent = String(bookings.filter(b => ['NEW','PENDING'].includes(b.status)).length || '');
  }
  function renderPartners() {
    const query = ($('#partner-search')?.value || '').toLowerCase().trim();
    const filtered = partners.filter(p => (partnerFilter === 'all' || (partnerFilter === 'available' ? p.available && p.active : p.registrationStatus === 'PENDING')) && `${p.name} ${p.code} ${(p.skills || []).map(s => s.code).join(' ')}`.toLowerCase().includes(query));
    const approved = partners.filter(p => p.registrationStatus === 'APPROVED');
    $('#partner-summary').innerHTML = `<div class="summary-tile"><span>Verified care partners</span><strong>${approved.length}</strong></div><div class="summary-tile"><span>Available right now</span><strong>${approved.filter(p => p.available && p.active).length}</strong></div><div class="summary-tile"><span>Awaiting verification</span><strong>${partners.filter(p => p.registrationStatus === 'PENDING').length}</strong></div>`;
    $('#partner-grid').innerHTML = filtered.length ? filtered.map(p => `<article class="partner-card"><div class="partner-card-top"><span class="avatar avatar-${escapeHtml(p.tint || 'mint')}">${escapeHtml(p.avatar || initials(p.name))}</span><div><h3>${escapeHtml(p.name)}</h3><span class="partner-meta">${escapeHtml(p.code || p.id || 'Care partner')}</span></div><span class="partner-state">${p.registrationStatus === 'PENDING' ? '<i class="partner-review">Needs review</i>' : `<i class="status ${p.available && p.active ? 'status-completed' : 'status-pending'}">${p.available && p.active ? 'Available' : p.active ? 'On a visit' : 'Inactive'}</i>`}</span></div><div class="skill-tags">${(p.skills || []).map(s => `<span class="skill-tag">${escapeHtml(categoryLabel(s.category))}${s.code ? ` · ${escapeHtml(s.code)}` : ''}${s.verified ? ' ✓' : ''}</span>`).join('') || '<span class="skill-tag">Skills not listed</span>'}</div><div class="partner-card-bottom"><span>Completed <strong>${p.completed ?? 'N/A'} visits</strong></span><span>Upcoming <strong>${p.upcoming ?? 'N/A'}</strong></span></div>${['ADMIN','DISPATCHER'].includes(api.role) && p.registrationStatus === 'PENDING' ? `<button class="inline-action-button partner-action" data-partner-action="verify" data-id="${escapeHtml(p.id)}">Verify partner</button>` : ['ADMIN','DISPATCHER'].includes(api.role) && p.registrationStatus === 'APPROVED' && !p.active ? `<button class="inline-action-button partner-action" data-partner-action="activate" data-id="${escapeHtml(p.id)}">Activate partner</button>` : ''}</article>`).join('') : `<div class="empty-state"><strong>No care partners found</strong><p>${api.token ? 'No partner records are available to this account yet.' : 'Connect an admin token to load your live partner roster.'}</p></div>`;
  }
  function renderRoutes() {
    $('#route-date').textContent = prettyDate(routeDay, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
    const visibleRoutes = api.demo && isoDate(routeDay) !== isoDate(today) ? [] : routes;
    const visits = visibleRoutes.reduce((sum, r) => sum + r.visits.length, 0);
    $('#route-overview').innerHTML = `<div class="route-stat"><span>Partners on route</span><strong>${visibleRoutes.length.toString().padStart(2, '0')}</strong></div><div class="route-stat"><span>Visits planned</span><strong>${visits.toString().padStart(2, '0')}</strong></div><div class="route-stat"><span>Estimated distance</span><strong>${visibleRoutes.reduce((sum, r) => sum + Number(r.km || 0), 0).toFixed(1)} <small style="font:500 10px 'DM Sans';color:#9aa59d">km</small></strong></div>`;
    $('#route-list').innerHTML = visibleRoutes.length ? visibleRoutes.map(r => `<article class="route-card"><div class="route-card-header"><span class="avatar avatar-${escapeHtml(r.tint)}">${escapeHtml(r.initials)}</span><div><h3>${escapeHtml(r.partner)}</h3><p>${escapeHtml(r.skills)}</p></div><div class="route-meta"><span><strong>${r.visits.length}</strong> visits</span><span><strong>${Number(r.km || 0).toFixed(1)} km</strong> estimated</span></div></div><div class="route-stops">${r.visits.length ? r.visits.map((v, i) => `<div class="route-stop"><span class="stop-number">${i + 1}</span><span class="stop-time">${escapeHtml(v.time || fmtTime(v.appointmentStart))}</span><span class="stop-copy">${escapeHtml(v.customerName || 'Customer visit')}<small>${escapeHtml(categoryLabel(v.category))}${v.skill ? ` · ${escapeHtml(v.skill)}` : ''}${v.duration ? ` · ${v.duration} min` : ''}</small></span></div>`).join('') : '<div class="route-stop"><span class="stop-copy">No visits scheduled for this date.</span></div>'}</div></article>`).join('') : `<div class="panel empty-state"><span>→</span><strong>No route plans for this date</strong><p>${api.demo ? 'No demo route is planned for this date.' : 'Connect an admin token to load partner routes.'}</p></div>`;
  }
  function renderJourneys() {
    $('#journey-list').innerHTML = journeys.length ? journeys.map((j, i) => { const person = partners.find(p => p.id === j.partnerId); const eta = j.eta ? `${Math.max(1, Math.round((new Date(j.eta) - Date.now()) / 60000))} min` : (j.status === 'PARTNER_ARRIVED' ? 'Arrived' : 'Updating'); return `<article class="journey-card"><div class="journey-person"><span class="avatar avatar-${escapeHtml(person?.tint || ['sand','lilac','mint'][i % 3])}">${escapeHtml(person?.avatar || initials(j.partnerName))}</span><div><strong>${escapeHtml(j.partnerName)}</strong><small>${escapeHtml(j.service || statusLabel(j.status))}</small></div><span class="eta-chip">${escapeHtml(eta)}</span></div><div class="journey-detail"><span>${escapeHtml(j.customerName || `Booking ${j.bookingId?.slice(0, 8) || ''}`)}</span><strong>${j.distanceKm == null ? 'Location updating' : j.distanceKm < .1 ? 'At the door' : `${Number(j.distanceKm).toFixed(1)} km away`}</strong></div><div class="journey-line"></div></article>`; }).join('') : `<div class="empty-state"><span>◉</span><strong>No active journeys</strong><p>Partners heading to a visit will show here.</p></div>`;
    $('#tracking-demo-visual').hidden = !(api.demo && journeys.length);
    $('#tracking-no-data').hidden = api.demo && journeys.length > 0;
    if (api.demo && journeys.length) $('#tracking-demo-visual').innerHTML = `<div class="demo-map-road road-one"></div><div class="demo-map-road road-two"></div><span class="demo-map-pin pin-one">⌂</span><span class="demo-map-pin pin-two">▣</span><span class="demo-map-pin pin-three">●</span><div class="demo-map-label"><strong>${escapeHtml(journeys[0].partnerName || 'Demo partner')}</strong><small>${escapeHtml(journeys[0].distanceKm == null ? 'Simulated demo location' : `${Number(journeys[0].distanceKm).toFixed(1)} km from visit`)}</small></div><span class="demo-map-caption">DEMO MAP · SIMULATED LOCATIONS</span>`;
    $('#operations-map-title').textContent = journeys.length ? `${journeys.length} active ${journeys.length === 1 ? 'journey' : 'journeys'}` : 'Live journey feed';
    $('#operations-map-copy').textContent = journeys.length ? (api.demo ? 'Simulated showcase journey details. The demo route sketch is not a live GPS position.' : 'Current partner and visit details from the authenticated tracking feed.') : 'Authenticated partner locations appear here when the tracking API reports an active journey. No sample routes or map points are displayed.';
    $('#operations-journey-list').innerHTML = journeys.slice(0, 2).map(j => `<div class="overview-journey"><strong>${escapeHtml(j.partnerName || 'Care partner')}</strong><span>${escapeHtml(j.customerName || `Booking ${j.bookingId?.slice(0, 8) || ''}`)}</span><small>${escapeHtml(statusLabel(j.status))} · ${j.distanceKm == null ? 'Location updating' : `${Number(j.distanceKm).toFixed(1)} km away`}</small></div>`).join('');
    $('.map-orbit').hidden = journeys.length > 0;
    $('#operations-track-button').textContent = journeys.length ? 'View all live journeys →' : 'Open live tracking →';
    $('#last-updated').textContent = api.token ? `Updated ${new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })}` : 'Connect to load live locations';
  }
  function renderActivity() {
    const entries = api.demo && demoActivity.length ? demoActivity.slice(0, 4).map((a, i) => ({ icon: ['✓','▣','♧','•'][i % 4], cls: ['', 'orange', 'purple'][i % 3], title: a.title, customer: a.subject, category: a.detail, when: a.at })) : bookings.slice(0, 3).map((b, i) => ({ icon: ['*','>','+'][i], cls: ['', 'orange', 'purple'][i], title: statusLabel(b.status), customer: b.customerName, category: categoryLabel(b.category), when: b.updatedAt || b.createdAt }));
    $('#activity-list').innerHTML = entries.length ? entries.map(a => `<div class="activity-item"><span class="activity-icon ${a.cls}">${a.icon}</span><div class="activity-copy"><strong>${escapeHtml(a.title)}</strong> &middot; ${escapeHtml(a.customer)} &middot; ${escapeHtml(a.category)}<small>${a.when ? escapeHtml(new Date(a.when).toLocaleString('en-IN')) : 'Booking record'}</small></div></div>`).join('') : '<div class="empty-state"><strong>No activity yet</strong><p>Bookings and status changes will appear here after data is added.</p></div>';
  }
  function renderShipments() {
    $('#shipment-heading-copy').textContent = api.demo ? 'Create showcase shipments and advance their delivery status.' : 'The connected API does not currently provide a shipment list for this dashboard.';
    const query = ($('#shipment-search')?.value || '').trim().toLowerCase();
    const filter = $('#shipment-filter')?.value || 'all';
    const rows = shipments.filter(s => (filter === 'all' || s.status === filter) && `${s.reference} ${s.recipientName} ${s.routeName} ${s.address}`.toLowerCase().includes(query));
    $('#shipment-body').innerHTML = rows.map(s => `<tr><td><strong>${escapeHtml(s.reference || s.id)}</strong><small style="display:block;color:#8290aa;margin-top:4px">${escapeHtml(s.id)}</small></td><td>${escapeHtml(s.recipientName || 'Recipient')}</td><td>${escapeHtml(s.address || s.routeName || '—')}</td><td>${escapeHtml(s.serviceDate || '—')}</td><td>${statusPill(s.status)}</td><td>${api.demo ? `<div class="shipment-action"><select class="demo-status-select" aria-label="New status for ${escapeHtml(s.reference)}">${["PLANNED","IN_TRANSIT","OUT_FOR_DELIVERY","DELIVERED","FAILED_ATTEMPT"].map(status => `<option value="${status}" ${s.status === status ? "selected" : ""}>${statusLabel(status)}</option>`).join("")}</select><button class="inline-action-button" data-shipment-action="status" data-id="${escapeHtml(s.id)}">Save</button></div>` : 'Listing not provided by connected API'}</td></tr>`).join('');
    $('#shipments-empty').classList.toggle('hidden', rows.length > 0);
    $('#shipments-empty-copy').textContent = api.demo ? 'Create a demo shipment to see it here.' : 'The connected API does not provide a shipment list endpoint.';
    $('#shipment-count').textContent = `${rows.length} ${api.demo ? 'demo ' : ''}${rows.length === 1 ? 'shipment' : 'shipments'}`;
  }
  function renderServices() {
    $('#service-grid').innerHTML = services.length ? services.map(s => `<article class="panel service-card"><span class="service-symbol">✚</span><div><h3>${escapeHtml(s.name)}</h3><p>${escapeHtml(s.description || 'Home-care service')}</p></div><span class="status ${s.active ? 'status-completed' : 'status-pending'}">${s.active ? 'Available' : 'Paused'}</span><button class="button ${s.active ? 'button-outline' : 'button-primary'}" data-service-action="toggle" data-id="${escapeHtml(s.id)}" data-active="${!s.active}">${s.active ? 'Pause service' : 'Enable service'}</button></article>`).join('') : '<div class="panel empty-state"><strong>No service catalog returned</strong><p>Connect the demo workspace or an API that exposes services.</p></div>';
  }
  function renderOperations() {
    $('#operations-hint').textContent = api.demo ? 'Review open demo bookings and partner applications; changes stay in demo memory.' : 'Review records returned by the connected API.';
    const pendingBookings = bookings.filter(b => ['NEW','PENDING','REJECTED'].includes(b.status));
    const pendingPartners = partners.filter(p => p.registrationStatus === 'PENDING');
    const activeShipments = shipments.filter(s => ['PLANNED','IN_TRANSIT','OUT_FOR_DELIVERY'].includes(s.status));
    $('#operations-summary').innerHTML = `<div class="summary-tile"><span>Bookings awaiting action</span><strong>${pendingBookings.length}</strong></div><div class="summary-tile"><span>Partner applications</span><strong>${pendingPartners.length}</strong></div><div class="summary-tile"><span>Open shipments</span><strong>${activeShipments.length}</strong></div>`;
    const rows = [
      ...pendingBookings.map(b => `<div class="queue-row"><div><strong>Booking ${escapeHtml(b.id)}</strong><small>${escapeHtml(b.customerName)} · ${escapeHtml(categoryLabel(b.category))} · ${escapeHtml(b.date)}</small></div><span>${statusPill(b.status)}</span><button class="inline-action-button" data-booking-action="allocate" data-id="${escapeHtml(b.id)}" data-version="${b.version}">Allocate</button></div>`),
      ...pendingPartners.map(p => `<div class="queue-row"><div><strong>Partner application</strong><small>${escapeHtml(p.name)} · ${escapeHtml(p.code || p.id)}</small></div><span class="partner-review">Needs review</span><button class="inline-action-button" data-partner-action="verify" data-id="${escapeHtml(p.id)}">Approve</button></div>`)
    ];
    $('#operations-queue').innerHTML = rows.length ? rows.join('') : '<div class="empty-state"><strong>All caught up</strong><p>There are no bookings or partner applications waiting for review.</p></div>';
  }
  function renderReports() {
    $('#report-heading-copy').textContent = api.demo ? 'Summaries below are calculated from local showcase demo records.' : 'Summaries use records returned by the connected API; unsupported metrics stay unavailable.';
    const period = $('#report-period').value;
    const periodStart = new Date(today);
    let periodEnd;
    if (period === 'This week') {
      periodStart.setDate(today.getDate() - ((today.getDay() + 6) % 7)); periodStart.setHours(0, 0, 0, 0);
      periodEnd = new Date(periodStart); periodEnd.setDate(periodStart.getDate() + 7);
    } else {
      periodStart.setDate(1); periodStart.setHours(0, 0, 0, 0);
      periodEnd = new Date(periodStart); periodEnd.setMonth(periodStart.getMonth() + 1);
    }
    const inPeriod = value => { const date = dateFromIso(value); return date >= periodStart && date < periodEnd; };
    const periodBookings = bookings.filter(b => inPeriod(b.date));
    const periodShipments = shipments.filter(s => inPeriod(s.serviceDate));
    const counts = ['NURSING','AYURVEDIC_THERAPY','COUNSELLING'].map(category => ({ name: categoryLabel(category), value: periodBookings.filter(b => b.category === category).length }));
    const max = Math.max(1, ...counts.map(c => c.value));
    const completed = periodBookings.filter(b => b.status === 'COMPLETED').length;
    const successRate = periodBookings.length ? Math.round(100 * completed / periodBookings.length) : 0;
    const revenue = period === 'This month' ? Number(demoMetrics?.revenueThisMonth || 0) : completed * 1800;
    $('#report-kpis').innerHTML = `<div class="summary-tile"><span>${escapeHtml(period)} bookings</span><strong>${periodBookings.length}</strong></div><div class="summary-tile"><span>Completed</span><strong>${completed}</strong></div><div class="summary-tile"><span>Revenue (demo only)</span><strong>${api.demo ? `₹${revenue.toLocaleString('en-IN')}` : 'Not available'}</strong></div><div class="summary-tile"><span>Success rate</span><strong>${api.demo ? `${successRate}%` : 'Not available'}</strong></div>`;
    $('#report-bars').innerHTML = counts.map(c => `<div class="report-bar-row"><span>${escapeHtml(c.name)}</span><div><i style="width:${c.value ? Math.max(4, 100 * c.value / max) : 0}%"></i></div><strong>${c.value}</strong></div>`).join('');
    const statuses = ['PLANNED','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED_ATTEMPT'].map(status => ({ name: statusLabel(status), value: periodShipments.filter(s => s.status === status).length }));
    const shipmentMax = Math.max(1, ...statuses.map(s => s.value));
    $('#shipment-report-bars').innerHTML = statuses.map(s => `<div class="report-bar-row"><span>${escapeHtml(s.name)}</span><div><i style="width:${s.value ? Math.max(4, 100 * s.value / shipmentMax) : 0}%"></i></div><strong>${s.value}</strong></div>`).join('');
    $('#report-booking-copy').textContent = api.demo ? 'Bookings in selected demo period' : 'Bookings returned by connected API';
    $('#report-shipment-copy').textContent = api.demo ? 'Shipments in selected demo period' : 'Shipment list is unavailable';
  }
  function renderOverviewPreviews() {
    $('#dashboard-shipment-list').innerHTML = shipments.length ? shipments.slice(0, 3).map(s => `<div class="shipment-preview-row"><span class="shipment-box">▰</span><div><strong>${escapeHtml(s.reference || s.id)}</strong><small>${escapeHtml(s.routeName || s.address || 'Delivery')}</small></div>${statusPill(s.status)}</div>`).join('') : '<div class="empty-state compact-empty"><strong>No shipments yet</strong><p>Create one to see delivery activity.</p></div>';
    const counts = ['NURSING','AYURVEDIC_THERAPY','COUNSELLING'].map(category => ({ name: categoryLabel(category), value: bookings.filter(b => b.category === category).length }));
    const max = Math.max(1, ...counts.map(item => item.value));
    $('#overview-analytics').innerHTML = counts.map(item => `<div class="overview-analytic-row"><span>${escapeHtml(item.name)}</span><div><i style="width:${item.value ? Math.max(5, item.value / max * 100) : 0}%"></i></div><strong>${item.value}</strong></div>`).join('');
  }
  function updateMetrics() {
    $('#metric-total-bookings').textContent = String(bookings.length);
    $('#metric-partners').innerHTML = `${partners.filter(p => p.active && p.registrationStatus === 'APPROVED').length}<span class="metric-denom"> / ${partners.length}</span>`;
    $('#metric-deliveries').textContent = String(shipments.filter(s => s.serviceDate === isoDate(today)).length);
    $('#metric-revenue').textContent = api.demo && demoMetrics ? `₹${(Number(demoMetrics.revenueThisMonth || 0) / 1000).toFixed(1)}k` : '—';
    $('#metric-success').textContent = api.demo && demoMetrics ? `${Number(demoMetrics.successRate || 0)}%` : '—';
    $('#shipment-nav-count').textContent = String(shipments.filter(s => !['DELIVERED','CANCELLED'].includes(s.status)).length || '');
  }
  function showPage(page) {
    const target = document.getElementById(`page-${page}`); if (!target) return;
    currentPage = page;
    $$('.page').forEach(el => el.classList.toggle('active', el === target));
    $$('.nav-link').forEach(el => el.classList.toggle('active', el.dataset.page === page));
    const names = { overview: 'Dashboard', bookings: 'Bookings', partners: 'Partners', services: 'Services', shipments: 'Shipments', routes: 'Routes', tracking: 'Live tracking', operations: 'Operations', reports: 'Reports', settings: 'Settings' };
    $('#crumb').textContent = names[page]; document.title = `${names[page]} · SathiRoute`;
    $('#sidebar').classList.remove('open');
    if (page === 'routes' && api.token && !api.demo) loadLiveRoutes().then(renderRoutes).catch(() => {});
  }
  let toastTimer;
  function toast(message) { const el = $('#toast'); el.textContent = message; el.classList.add('show'); clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.remove('show'), 3600); }
  function openModal(id) { const modal = document.getElementById(id); modal.classList.add('open'); modal.setAttribute('aria-hidden', 'false'); const first = $('input,select,textarea', modal); setTimeout(() => first?.focus(), 60); }
  function closeModal(id) { const modal = document.getElementById(id); modal.classList.remove('open'); modal.setAttribute('aria-hidden', 'true'); }
  function dateDefaults() { const field = $('#booking-form [name="date"]'); field.min = isoDate(today); const tomorrow = new Date(today); tomorrow.setDate(today.getDate() + 1); field.value = isoDate(tomorrow); }
  function disconnectWorkspace() {
    ['token', 'apiBase', 'role', 'subject', 'mode'].forEach(key => localStorage.removeItem(`sathiroute.${key}`));
    api.token = ''; api.base = ''; api.role = ''; api.subject = ''; api.demo = false;
    bookings = []; partners = []; journeys = []; routes = []; shipments = []; services = []; demoMetrics = null; demoActivity = [];
    setLive(false); renderAll(); closeModal('connect-modal'); toast('Account disconnected.');
  }
  function bindEvents() {
    $$('.nav-link').forEach(button => button.addEventListener('click', () => showPage(button.dataset.page)));
    $$('[data-go]').forEach(button => button.addEventListener('click', () => showPage(button.dataset.go)));
    $$('[data-action="new-booking"]').forEach(button => button.addEventListener('click', async () => {
      $('#booking-message').textContent = ''; dateDefaults();
      $('#booking-form [name="latitude"]').required = !api.demo;
      $('#booking-form [name="longitude"]').required = !api.demo;
      try {
        if (api.demo) throw Object.assign(new Error('demo workspace'), { status: 404 });
        const profile = await request('/v1/customers/me');
        for (const [field, key] of [['name', 'name'], ['email', 'email'], ['phone', 'phone']]) $('#booking-form [name="' + field + '"]').value = profile[key] || '';
      } catch (error) { if (error.status !== 404) { toast(error.message); return; } }
      openModal('booking-modal');
    }));
    $$('[data-action="add-partner"]').forEach(button => button.addEventListener('click', () => { $('#partner-demo-form').reset(); openModal('partner-modal'); }));
    $$('[data-action="create-shipment"]').forEach(button => button.addEventListener('click', () => { $('#shipment-demo-form').reset(); openModal('shipment-modal'); }));
    $$('[data-action="connect"]').forEach(button => button.addEventListener('click', () => { $('#connect-message').textContent = ''; $('#connect-form [name="token"]').value = api.token; $('#connect-form [name="apiBase"]').value = api.base; $('#connect-form [name="apiRole"]').value = api.role || 'AUTO'; openModal('connect-modal'); }));
    $$('[data-close]').forEach(button => button.addEventListener('click', () => closeModal(button.dataset.close)));
    $$('.modal-backdrop').forEach(backdrop => backdrop.addEventListener('click', e => { if (e.target === backdrop) closeModal(backdrop.id); }));
    document.addEventListener('keydown', e => { if (e.key === 'Escape') $$('.modal-backdrop.open').forEach(el => closeModal(el.id)); });
    document.addEventListener('click', handleApiAction);
    $('#mobile-menu').addEventListener('click', () => $('#sidebar').classList.toggle('open'));
    $('#open-demo-workspace').addEventListener('click', () => connectDemo(true).catch(error => { $('#demo-api-message').textContent = `Demo API unavailable: ${error.message}. Start the local Compose app with demo API enabled.`; }));
    $('#settings-demo-button').addEventListener('click', () => connectDemo(true).catch(error => toast(`Demo API unavailable: ${error.message}`)));
    $('#reset-demo-button').addEventListener('click', async () => { try { await demoRequest('/reset', { method: 'POST' }); await connectDemo(false); toast('Demo records have been reset.'); } catch (error) { toast(`Demo API unavailable: ${error.message}`); } });
    $('#partner-demo-form').addEventListener('submit', async e => {
      e.preventDefault(); const data = Object.fromEntries(new FormData(e.currentTarget));
      try { await demoRequest('/partners', { method: 'POST', body: JSON.stringify(data) }); await connectDemo(false); closeModal('partner-modal'); showPage('partners'); toast('Demo partner added to the review queue.'); }
      catch (error) { toast(error.message); }
    });
    $('#shipment-demo-form').addEventListener('submit', async e => {
      e.preventDefault(); const data = Object.fromEntries(new FormData(e.currentTarget));
      try { await demoRequest('/shipments', { method: 'POST', body: JSON.stringify(data) }); await connectDemo(false); closeModal('shipment-modal'); showPage('shipments'); toast('Demo shipment created.'); }
      catch (error) { toast(error.message); }
    });
    $('#booking-search').addEventListener('input', renderBookings);
    $('#global-search').addEventListener('keydown', e => {
      if (e.key !== 'Enter') return;
      const query = e.currentTarget.value.trim();
      if (!query) return;
      const bookingMatch = bookings.some(b => `${b.customerName} ${b.partnerName} ${b.skill} ${categoryLabel(b.category)} ${b.id}`.toLowerCase().includes(query.toLowerCase()));
      const partnerMatch = partners.some(p => `${p.name} ${p.code || ''} ${(p.skills || []).map(skill => skill.code || skill.category).join(' ')}`.toLowerCase().includes(query.toLowerCase()));
      const shipmentMatch = shipments.some(s => `${s.reference} ${s.recipientName} ${s.routeName} ${s.address}`.toLowerCase().includes(query.toLowerCase()));
      if (shipmentMatch && !bookingMatch && !partnerMatch) {
        $('#shipment-search').value = query; showPage('shipments'); renderShipments();
      } else if (partnerMatch && !bookingMatch) {
        $('#partner-search').value = query;
        showPage('partners'); renderPartners();
      } else {
        $('#booking-search').value = query;
        bookingFilter = 'all';
        $$('[data-filter]').forEach(button => button.classList.toggle('selected', button.dataset.filter === 'all'));
        showPage('bookings'); renderBookings();
      }
    });
    $('#partner-search').addEventListener('input', renderPartners);
    $('#shipment-search').addEventListener('input', renderShipments);
    $('#shipment-filter').addEventListener('change', renderShipments);
    $('#report-period').addEventListener('change', renderReports);
    $('.notification-button').addEventListener('click', () => { showPage('operations'); toast(`${bookings.filter(b => ['NEW','PENDING'].includes(b.status)).length + partners.filter(p => p.registrationStatus === 'PENDING').length} booking or partner items need attention.`); });
    $('#settings-disconnect-api').addEventListener('click', disconnectWorkspace);
    $$('[data-filter]').forEach(button => button.addEventListener('click', () => { bookingFilter = button.dataset.filter.toLowerCase() === 'all' ? 'all' : button.dataset.filter; $$('[data-filter]').forEach(b => b.classList.toggle('selected', b === button)); renderBookings(); }));
    $$('[data-partner-filter]').forEach(button => button.addEventListener('click', () => { partnerFilter = button.dataset.partnerFilter; $$('[data-partner-filter]').forEach(b => b.classList.toggle('selected', b === button)); renderPartners(); }));
    $('#refresh-bookings').addEventListener('click', () => api.token ? connectLive().catch(e => toast(e.message)) : toast('Connect an API token to refresh live bookings.'));
    $('#route-prev').addEventListener('click', () => { routeDay.setDate(routeDay.getDate() - 1); renderRoutes(); if (api.token && !api.demo) loadLiveRoutes().then(renderRoutes).catch(e => toast(e.message)); });
    $('#route-next').addEventListener('click', () => { routeDay.setDate(routeDay.getDate() + 1); renderRoutes(); if (api.token && !api.demo) loadLiveRoutes().then(renderRoutes).catch(e => toast(e.message)); });
    $('#refresh-workspace').addEventListener('click', () => {
      if (api.demo) connectDemo(false).catch(e => toast(e.message));
      else if (api.token) connectLive().catch(e => toast(e.message));
      else openModal('connect-modal');
    });
    $('#connect-form').addEventListener('submit', async e => {
      e.preventDefault(); const form = new FormData(e.currentTarget); api.base = String(form.get('apiBase') || '').trim().replace(/\/$/, ''); api.token = String(form.get('token') || '').trim().replace(/^Bearer\s+/i, '');
      if (!api.token) { $('#connect-message').textContent = 'Paste a valid access token to connect.'; return; }
      api.demo = false; localStorage.removeItem('sathiroute.mode');
      api.role = form.get('apiRole') === 'AUTO' ? jwtRole(api.token) : String(form.get('apiRole') || 'CUSTOMER');
      localStorage.setItem('sathiroute.role', api.role);
      localStorage.setItem('sathiroute.apiBase', api.base); localStorage.setItem('sathiroute.token', api.token); $('#connect-message').textContent = 'Checking access…';
      try { await connectLive(); closeModal('connect-modal'); } catch (error) { $('#connect-message').textContent = error.message; }
    });
    $('#disconnect-api').addEventListener('click', disconnectWorkspace);
    $('#demo-login-form').addEventListener('submit', async e => {
      e.preventDefault(); const form = new FormData(e.currentTarget); const message = $('#demo-login-message'); message.textContent = 'Signing in…';
      try {
        const response = await fetch(apiUrl('/dev/session'), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ role: form.get('role'), subject: form.get('subject') }) });
        const session = await response.json();
        if (!response.ok) throw new Error(session.detail || session.message || `Sign-in failed (${response.status})`);
        api.token = session.accessToken; api.role = session.role; api.subject = session.subject;
        localStorage.setItem('sathiroute.token', api.token); localStorage.setItem('sathiroute.role', api.role); localStorage.setItem('sathiroute.subject', api.subject);
        await connectLive(); closeModal('connect-modal'); toast(`Connected as ${api.role}.`);
      } catch (error) { message.textContent = `${error.message}. Check local demo auth settings and API connection.`; }
    });
    $('#booking-form').addEventListener('submit', submitBooking);
    $('#use-location').addEventListener('click', () => {
      const message = $('#booking-message');
      if (!navigator.geolocation) { message.textContent = 'Location is not available in this browser. Enter the coordinates manually.'; return; }
      message.textContent = 'Finding your location…';
      navigator.geolocation.getCurrentPosition(position => {
        $('#booking-form [name="latitude"]').value = position.coords.latitude.toFixed(6);
        $('#booking-form [name="longitude"]').value = position.coords.longitude.toFixed(6);
        message.textContent = 'Location added. Please confirm the service address.';
      }, error => { message.textContent = error.code === 1 ? 'Location permission was declined. You can enter coordinates manually.' : 'Could not read location. Please enter coordinates manually.'; }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 });
    });
  }
  async function handleApiAction(event) {
    const bookingButton = event.target.closest('[data-booking-action]');
    const partnerButton = event.target.closest('[data-partner-action]');
    const shipmentButton = event.target.closest('[data-shipment-action]');
    const serviceButton = event.target.closest('[data-service-action]');
    const button = bookingButton || partnerButton || shipmentButton || serviceButton;
    if (!button || (!api.token && !api.demo)) return;
    button.disabled = true;
    try {
      if (api.demo && bookingButton) {
        const nextStatus = { allocate: 'ASSIGNED', accept: 'CONFIRMED', reject: 'REJECTED', arrive: 'PARTNER_ARRIVED', start: 'IN_PROGRESS', complete: 'COMPLETED', confirm: 'COMPLETED' }[button.dataset.bookingAction];
        if (nextStatus) await demoRequest(`/bookings/${encodeURIComponent(button.dataset.id)}`, { method: 'PATCH', body: JSON.stringify({ status: nextStatus }) });
      } else if (api.demo && partnerButton) {
        const body = button.dataset.partnerAction === 'verify' ? { approved: true } : { active: true };
        await demoRequest(`/partners/${encodeURIComponent(button.dataset.id)}`, { method: 'PATCH', body: JSON.stringify(body) });
      } else if (api.demo && shipmentButton) {
        const row = button.closest('tr'); const status = $('.demo-status-select', row).value;
        await demoRequest(`/shipments/${encodeURIComponent(button.dataset.id)}`, { method: 'PATCH', body: JSON.stringify({ status }) });
      } else if (api.demo && serviceButton) {
        await demoRequest(`/services/${encodeURIComponent(button.dataset.id)}`, { method: 'PATCH', body: JSON.stringify({ active: button.dataset.active === 'true' }) });
      } else if (bookingButton) {
        const id = encodeURIComponent(button.dataset.id); const version = Number(button.dataset.version || 0);
        const action = button.dataset.bookingAction;
        if (action === 'allocate') await request(`/v1/admin/bookings/${id}/allocation-start`, { method: 'POST' });
        else if (action === 'accept' || action === 'reject') await request(`/v1/partners/me/bookings/${id}/${action}`, { method: 'POST', body: JSON.stringify({ expectedVersion: version }) });
        else if (action === 'arrive' || action === 'start') await request(`/v1/partners/me/bookings/${id}/${action}`, { method: 'POST', body: JSON.stringify({ expectedVersion: version }) });
        else if (action === 'complete') await request(`/v1/partners/me/bookings/${id}/complete`, { method: 'POST', body: JSON.stringify({ expectedVersion: version, completionNotes: '' }) });
        else if (action === 'confirm') {
          const otp = window.prompt('Enter the customer completion code. Leave blank only when OTP confirmation is disabled.');
          if (otp === null) { button.disabled = false; return; }
          await request(`/v1/bookings/${id}/completion-confirmation`, { method: 'POST', body: JSON.stringify(otp ? { otp: otp.trim() } : { confirmed: true }) });
        }
      } else {
        const id = encodeURIComponent(button.dataset.id);
        if (button.dataset.partnerAction === 'verify') await request(`/v1/admin/partners/${id}/verification`, { method: 'PATCH', body: JSON.stringify({ approved: true }) });
        if (button.dataset.partnerAction === 'activate') await request(`/v1/admin/partners/${id}/status`, { method: 'PATCH', body: JSON.stringify({ active: true }) });
      }
      if (api.demo) await connectDemo(false); else await connectLive();
      toast(api.demo ? 'Saved to demo workspace.' : 'Saved. The workspace has been refreshed from the API.');
    } catch (error) { toast(error.message); button.disabled = false; }
  }
  async function submitBooking(event) {
    event.preventDefault(); const message = $('#booking-message'); const button = $('#submit-booking'); message.textContent = '';
    if (!api.token && !api.demo) { message.textContent = 'Open the demo workspace or connect a customer API account before creating a booking.'; openModal('connect-modal'); return; }
    const formElement = event.currentTarget;
    const data = Object.fromEntries(new FormData(formElement)); button.disabled = true; button.textContent = 'Creating…';
    try {
      if (api.demo) {
        await demoRequest('/bookings', { method: 'POST', body: JSON.stringify(data) });
        await connectDemo(false); closeModal('booking-modal'); formElement.reset(); dateDefaults(); showPage('bookings'); toast('Demo booking created.'); return;
      }
      let profile = await request('/v1/customers/me').catch(async error => { if (error.status !== 404) throw error; return request('/v1/customers', { method: 'POST', body: JSON.stringify({ name: data.name, email: data.email, phone: data.phone }) }); });
      if (!profile) throw new Error('Could not create customer profile.');
      if (profile.name !== data.name || profile.email !== data.email || profile.phone !== data.phone) {
        profile = await request('/v1/customers/me', { method: 'PATCH', body: JSON.stringify({ name: data.name, email: data.email, phone: data.phone }) });
      }
      const booking = await request('/v1/bookings', { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ serviceCategory: data.category, requiredSkill: data.skill, appointmentDate: data.date, appointmentStart: data.time, serviceDurationMinutes: Number(data.duration), address: data.address, latitude: Number(data.latitude), longitude: Number(data.longitude), notes: data.notes || '' }) });
      const newItem = normalizeBooking({ ...booking, customerName: profile.name, customerEmail: profile.email, category: data.category, requiredSkill: data.skill, appointmentDate: data.date, appointmentStart: data.time, serviceDurationMinutes: Number(data.duration) });
      bookings.unshift(newItem); renderAll(); closeModal('booking-modal'); formElement.reset(); dateDefaults(); showPage('bookings'); toast('Booking created. We’re finding the right care partner.');
    } catch (error) { message.textContent = `${error.message}. Check that this token has the CUSTOMER role and the API is reachable.`; }
    finally { button.disabled = false; button.innerHTML = 'Create booking <span>→</span>'; }
  }
  function initialize() {
    $('#today-label').textContent = prettyDate(today, { weekday: 'short', month: 'short', day: 'numeric' });
    $('#schedule-date').textContent = prettyDate(today, { month: 'long', day: 'numeric' });
    $('#route-date').textContent = prettyDate(routeDay, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
    bindEvents(); renderAll(); setLive(Boolean(api.token));
    if (api.token) connectLive().catch(error => { setLive(false); toast(`Live data could not load: ${error.message}`); });
    else connectDemo(false).catch(() => setLive(false));
    $('#booking-form [name="category"]').addEventListener('change', e => { const skill = $('#booking-form [name="skill"]'); skill.value = ''; skill.placeholder = { NURSING: 'e.g. RN', AYURVEDIC_THERAPY: 'e.g. Panchakarma', COUNSELLING: 'e.g. Family counselling' }[e.target.value]; });
  }
  initialize();
  window.setInterval(() => { if ((api.token || api.demo) && document.visibilityState === 'visible') (api.demo ? connectDemo(false) : connectLive()).catch(() => {}); }, 30000);
})();
