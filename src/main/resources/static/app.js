(() => {
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const api = { base: localStorage.getItem('sathiroute.apiBase') || '', token: localStorage.getItem('sathiroute.token') || '', role: localStorage.getItem('sathiroute.role') || '', subject: localStorage.getItem('sathiroute.subject') || '' };
  const today = new Date();
  let currentPage = 'overview';
  let bookings = seedBookings();
  let partners = seedPartners();
  let journeys = seedJourneys();
  let routes = seedRoutes();
  let bookingFilter = 'all';
  let partnerFilter = 'all';
  let routeDay = new Date();

  function isoDate(date) { return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10); }
  function prettyDate(date, options = { weekday: 'long', month: 'long', day: 'numeric' }) { return new Intl.DateTimeFormat('en-IN', options).format(date); }
  function seedBookings() {
    const d = isoDate(today);
    return [
      { id: 'BK-2841', customerName: 'Meera Krishnan', email: 'meera.k@example.com', category: 'NURSING', skill: 'RN · Post-operative care', date: d, time: '09:30', duration: 60, partnerName: 'Asha Kulkarni', partnerId: 'p1', status: 'CONFIRMED', address: 'Indiranagar, Bengaluru', avatar: 'MK', color: 'rose' },
      { id: 'BK-2840', customerName: 'Rohan Menon', email: 'rohan.m@example.com', category: 'AYURVEDIC_THERAPY', skill: 'Panchakarma', date: d, time: '10:00', duration: 90, partnerName: 'Vikram Nair', partnerId: 'p2', status: 'PARTNER_ON_THE_WAY', address: 'Domlur, Bengaluru', avatar: 'RM', color: 'blue' },
      { id: 'BK-2839', customerName: 'Nandita Rao', email: 'nandita.r@example.com', category: 'COUNSELLING', skill: 'Family counselling', date: d, time: '11:30', duration: 45, partnerName: 'Dr. Kavya Shah', partnerId: 'p3', status: 'ASSIGNED', address: 'Koramangala, Bengaluru', avatar: 'NR', color: 'gold' },
      { id: 'BK-2838', customerName: 'Arjun Shetty', email: 'arjun.s@example.com', category: 'NURSING', skill: 'Elder care', date: d, time: '12:00', duration: 60, partnerName: 'Anjali Rao', partnerId: 'p4', status: 'PENDING', address: 'Jayanagar, Bengaluru', avatar: 'AS', color: 'lilac' },
      { id: 'BK-2837', customerName: 'Latha Iyer', email: 'latha.i@example.com', category: 'AYURVEDIC_THERAPY', skill: 'Therapeutic massage', date: d, time: '14:00', duration: 90, partnerName: 'Vikram Nair', partnerId: 'p2', status: 'CONFIRMED', address: 'Ulsoor, Bengaluru', avatar: 'LI', color: 'mint' },
      { id: 'BK-2836', customerName: 'Devika Das', email: 'devika.d@example.com', category: 'NURSING', skill: 'Wound dressing', date: d, time: '15:30', duration: 60, partnerName: 'Asha Kulkarni', partnerId: 'p1', status: 'COMPLETED', address: 'Whitefield, Bengaluru', avatar: 'DD', color: 'rose' },
      { id: 'BK-2835', customerName: 'Sameer Khan', email: 'sameer.k@example.com', category: 'COUNSELLING', skill: 'Individual therapy', date: d, time: '16:00', duration: 50, partnerName: 'Dr. Kavya Shah', partnerId: 'p3', status: 'CONFIRMED', address: 'HSR Layout, Bengaluru', avatar: 'SK', color: 'blue' },
      { id: 'BK-2834', customerName: 'Priya Narayan', email: 'priya.n@example.com', category: 'NURSING', skill: 'Medication support', date: d, time: '17:00', duration: 45, partnerName: 'Unassigned', partnerId: '', status: 'PENDING', address: 'Malleshwaram, Bengaluru', avatar: 'PN', color: 'gold' }
    ];
  }
  function seedPartners() {
    return [
      { id: 'p1', name: 'Asha Kulkarni', code: 'SR-1082', email: 'asha.k@example.com', phone: '+91 98450 22341', skills: [{ category: 'NURSING', code: 'RN', verified: true }, { category: 'NURSING', code: 'Elder care', verified: true }], active: true, available: true, registrationStatus: 'APPROVED', latitude: 12.978, longitude: 77.641, completed: 68, upcoming: 3, avatar: 'AK', tint: 'mint' },
      { id: 'p2', name: 'Vikram Nair', code: 'SR-1091', email: 'vikram.n@example.com', phone: '+91 99001 83726', skills: [{ category: 'AYURVEDIC_THERAPY', code: 'Panchakarma', verified: true }, { category: 'AYURVEDIC_THERAPY', code: 'Massage', verified: true }], active: true, available: false, registrationStatus: 'APPROVED', latitude: 12.961, longitude: 77.615, completed: 52, upcoming: 2, avatar: 'VN', tint: 'sand' },
      { id: 'p3', name: 'Dr. Kavya Shah', code: 'SR-1116', email: 'kavya.s@example.com', phone: '+91 98451 55309', skills: [{ category: 'COUNSELLING', code: 'Family counselling', verified: true }, { category: 'COUNSELLING', code: 'Individual therapy', verified: true }], active: true, available: true, registrationStatus: 'APPROVED', latitude: 12.934, longitude: 77.625, completed: 41, upcoming: 2, avatar: 'KS', tint: 'lilac' },
      { id: 'p4', name: 'Anjali Rao', code: 'SR-1140', email: 'anjali.r@example.com', phone: '+91 98803 17462', skills: [{ category: 'NURSING', code: 'Elder care', verified: true }], active: true, available: true, registrationStatus: 'APPROVED', latitude: 12.927, longitude: 77.583, completed: 37, upcoming: 1, avatar: 'AR', tint: 'blue' },
      { id: 'p5', name: 'Leela Hegde', code: 'SR-1152', email: 'leela.h@example.com', phone: '+91 98443 91502', skills: [{ category: 'AYURVEDIC_THERAPY', code: 'Abhyanga', verified: true }], active: false, available: false, registrationStatus: 'PENDING', latitude: 12.995, longitude: 77.61, completed: 0, upcoming: 0, avatar: 'LH', tint: 'peach' },
      { id: 'p6', name: 'Dr. Saira Qureshi', code: 'SR-1158', email: 'saira.q@example.com', phone: '+91 99860 45421', skills: [{ category: 'COUNSELLING', code: 'Child counselling', verified: true }], active: false, available: false, registrationStatus: 'PENDING', latitude: 12.943, longitude: 77.67, completed: 0, upcoming: 0, avatar: 'SQ', tint: 'gold' }
    ];
  }
  function seedJourneys() {
    return [
      { bookingId: 'BK-2840', partnerId: 'p2', partnerName: 'Vikram Nair', status: 'PARTNER_ON_THE_WAY', distanceKm: 1.8, eta: new Date(Date.now() + 11 * 60000).toISOString(), customerName: 'Rohan Menon', service: 'Ayurvedic Therapy · Domlur' },
      { bookingId: 'BK-2839', partnerId: 'p3', partnerName: 'Dr. Kavya Shah', status: 'PARTNER_NEARBY', distanceKm: .4, eta: new Date(Date.now() + 4 * 60000).toISOString(), customerName: 'Nandita Rao', service: 'Counselling · Koramangala' },
      { bookingId: 'BK-2841', partnerId: 'p1', partnerName: 'Asha Kulkarni', status: 'PARTNER_ARRIVED', distanceKm: 0, eta: null, customerName: 'Meera Krishnan', service: 'Nursing · Indiranagar' }
    ];
  }
  function seedRoutes() {
    return [
      { partner: 'Asha Kulkarni', initials: 'AK', skills: 'Nursing · SR-1082', km: 18.4, visits: bookings.filter(b => b.partnerId === 'p1' && b.status !== 'COMPLETED'), tint: 'mint' },
      { partner: 'Vikram Nair', initials: 'VN', skills: 'Ayurvedic Therapy · SR-1091', km: 12.7, visits: bookings.filter(b => b.partnerId === 'p2'), tint: 'sand' },
      { partner: 'Dr. Kavya Shah', initials: 'KS', skills: 'Counselling · SR-1116', km: 9.2, visits: bookings.filter(b => b.partnerId === 'p3'), tint: 'lilac' }
    ];
  }
  const categoryLabel = value => ({ NURSING: 'Nursing', AYURVEDIC_THERAPY: 'Ayurvedic Therapy', COUNSELLING: 'Counselling' }[value] || value || 'Care visit');
  const statusLabel = value => ({ NEW: 'New', PENDING: 'Awaiting partner', ASSIGNED: 'Partner assigned', CONFIRMED: 'Confirmed', PARTNER_ON_THE_WAY: 'On the way', PARTNER_NEARBY: 'Nearby', PARTNER_ARRIVED: 'Arrived', IN_PROGRESS: 'In progress', COMPLETED: 'Completed', CANCELLED: 'Cancelled', REJECTED: 'Rejected', 'Needs partner': 'Awaiting partner' }[value] || value || 'Scheduled');
  const statusClass = value => ({ PENDING: 'pending', NEW: 'pending', ASSIGNED: 'assigned', CONFIRMED: 'confirmed', PARTNER_ON_THE_WAY: 'assigned', PARTNER_NEARBY: 'assigned', PARTNER_ARRIVED: 'confirmed', IN_PROGRESS: 'in-progress', COMPLETED: 'completed', CANCELLED: 'cancelled', REJECTED: 'rejected' }[value] || 'pending');
  const initials = name => (name || 'SP').split(/\s+/).filter(Boolean).slice(0, 2).map(s => s[0]).join('').toUpperCase();
  const categoryClass = category => category === 'AYURVEDIC_THERAPY' ? 'ayurvedic' : category === 'COUNSELLING' ? 'counselling' : '';
  const fmtTime = value => { if (!value) return '—'; const [h, m] = String(value).slice(0, 5).split(':').map(Number); if (Number.isNaN(h)) return value; return `${(h % 12) || 12}:${String(m).padStart(2, '0')} ${h >= 12 ? 'PM' : 'AM'}`; };
  const timeFrom = value => { try { return new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit' }).format(new Date(value)); } catch { return '—'; } };
  const dateFromIso = value => value ? new Date(`${value}T00:00:00`) : today;
  const statusPill = status => `<span class="status status-${statusClass(status)}">${escapeHtml(statusLabel(status))}</span>`;
  function bookingAction(b) {
    if (!api.token) return '';
    if (api.role === 'SERVICE_PARTNER') {
      if (b.status === 'ASSIGNED') return `<button class="inline-action-button" data-booking-action="accept" data-id="${escapeHtml(b.id)}" data-version="${b.version}">Accept</button>`;
      if (b.status === 'CONFIRMED') return `<button class="inline-action-button" data-booking-action="arrive" data-id="${escapeHtml(b.id)}" data-version="${b.version}">Arrived</button>`;
      if (b.status === 'PARTNER_ARRIVED') return `<button class="inline-action-button" data-booking-action="start" data-id="${escapeHtml(b.id)}" data-version="${b.version}">Start visit</button>`;
      if (b.status === 'IN_PROGRESS') return `<button class="inline-action-button" data-booking-action="complete" data-id="${escapeHtml(b.id)}" data-version="${b.version}">Complete</button>`;
    }
    if (api.role === 'CUSTOMER' && b.completionPending) return `<button class="inline-action-button" data-booking-action="confirm" data-id="${escapeHtml(b.id)}">Confirm</button>`;
    if (['ADMIN','DISPATCHER','OPERATIONS_MANAGER'].includes(api.role) && ['NEW','REJECTED'].includes(b.status)) return `<button class="inline-action-button" data-booking-action="allocate" data-id="${escapeHtml(b.id)}">Find partner</button>`;
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
  function setLive(live) {
    document.body.classList.toggle('live-mode', live);
    $('#demo-banner').classList.toggle('hidden', live);
    $('#connect-label').textContent = live ? `${api.role || 'API'} connected` : 'Connect API';
    $('.connect-button').classList.toggle('connected', live);
    $('#metric-visits').dataset.live = live ? '1' : '';
  }
  function normalizeBooking(b, index = 0) {
    const start = b.appointmentStartAt || b.appointmentStart || b.startsAt || b.appointmentStartAt;
    const partner = b.partnerName || b.assignedPartnerName || (b.assignedPartnerId ? partners.find(p => p.id === b.assignedPartnerId)?.name : '') || 'Unassigned';
    const date = b.appointmentDate || (start ? isoDate(new Date(start)) : isoDate(today));
    const time = b.appointmentStart || (start ? new Date(start).toTimeString().slice(0, 5) : '10:00');
    return { id: b.id || b.bookingId || `live-${index}`, customerName: b.customerName || 'Customer', email: b.customerEmail || '', category: b.category || b.serviceCategory || 'NURSING', skill: b.requiredSkill || b.skill || 'Home care', date, time, duration: b.durationMinutes || b.serviceDurationMinutes || 60, partnerName: partner, partnerId: b.assignedPartnerId || '', status: String(b.status || 'NEW'), version: b.version ?? 0, address: b.address || 'Address on booking', avatar: initials(b.customerName), color: 'mint' };
  }
  function normalizePartner(p) { return { ...p, name: p.name || 'Care partner', registrationStatus: p.registrationStatus || 'PENDING', skills: p.skills || [], avatar: initials(p.name), tint: 'mint', upcoming: p.upcomingBookings ?? 0, completed: p.completedBookings ?? 0 }; }
  function normalizeJourney(j) { return { ...j, customerName: j.customerName || 'Customer visit', service: `${statusLabel(j.status)} · ${j.distanceKm == null ? 'Location updating' : `${Number(j.distanceKm).toFixed(1)} km away`}` }; }
  async function connectLive() {
    if (!api.role) api.role = jwtRole(api.token);
    setLive(true); bookings = []; partners = []; journeys = []; routes = [];
    let results;
    if (['ADMIN','DISPATCHER','OPERATIONS_MANAGER'].includes(api.role)) {
      results = await Promise.allSettled([
        request('/v1/admin/bookings?limit=100&offset=0'), request('/v1/admin/partners?status=APPROVED'),
        request('/v1/admin/partners?status=PENDING'), request('/v1/admin/tracking/partners'),
        request('/v1/admin/service-executions')
      ]);
      const [adminBookings, approved, pending, liveJourneys, executions] = results;
      if (adminBookings.status === 'fulfilled' && Array.isArray(adminBookings.value)) bookings = adminBookings.value.map(normalizeBooking);
      if (approved.status === 'fulfilled' && Array.isArray(approved.value)) partners = approved.value.map(normalizePartner);
      if (pending.status === 'fulfilled' && Array.isArray(pending.value)) partners = [...partners, ...pending.value.map(normalizePartner)];
      if (liveJourneys.status === 'fulfilled' && Array.isArray(liveJourneys.value)) journeys = liveJourneys.value.map(normalizeJourney);
      if (executions.status === 'fulfilled' && Array.isArray(executions.value)) {
        for (const execution of executions.value) {
          const booking = bookings.find(b => b.id === execution.bookingId);
          if (booking) { booking.partnerName = execution.partnerName || booking.partnerName; booking.status = execution.bookingStatus || booking.status; }
        }
      }
      if (approved.status === 'fulfilled') await loadLiveRoutes();
    } else if (api.role === 'SERVICE_PARTNER') {
      results = await Promise.allSettled([request('/v1/partners/me'), request('/v1/partners/me/bookings?limit=100&offset=0'), request('/v1/partners/me/workload')]);
      const [profile, assigned] = results;
      if (profile.status === 'fulfilled') partners = [normalizePartner(profile.value)];
      if (assigned.status === 'fulfilled' && Array.isArray(assigned.value)) bookings = assigned.value.map(normalizeBooking);
      const active = bookings.filter(b => ['CONFIRMED','PARTNER_ON_THE_WAY','PARTNER_NEARBY','PARTNER_ARRIVED','IN_PROGRESS'].includes(b.status));
      journeys = active.map(b => ({ bookingId: b.id, partnerId: partners[0]?.id, partnerName: partners[0]?.name || 'You', customerName: b.customerName, service: `${categoryLabel(b.category)} · ${b.address}`, status: b.status, distanceKm: null, eta: null }));
      if (partners[0]?.id) {
        const routeResult = await Promise.allSettled([request(`/v1/partners/${encodeURIComponent(partners[0].id)}/routes/${isoDate(routeDay)}`)]);
        if (routeResult[0].status === 'fulfilled') routes = [routeResult[0].value].filter(Boolean).map(route => ({ partner: partners[0].name, initials: partners[0].avatar, skills: partners[0].skills.map(s => categoryLabel(s.category)).join(' · '), km: route.km || 0, visits: (route.stops || []).map((s, i) => ({ ...s, customerName: `Visit ${i + 1}`, time: s.plannedArrival ? timeFrom(s.plannedArrival) : '—' })), tint: 'mint' }));
      }
    } else {
      results = await Promise.allSettled([request('/v1/customers/me'), request('/v1/bookings?limit=100&offset=0')]);
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
  async function loadLiveRoutes() {
    const date = isoDate(routeDay);
    const settled = await Promise.allSettled(partners.filter(p => p.registrationStatus === 'APPROVED').slice(0, 20).map(p => request(`/v1/partners/${encodeURIComponent(p.id)}/routes/${date}`).then(route => ({ partner: p, route }))));
    routes = settled.filter(x => x.status === 'fulfilled' && x.value.route).map((x, index) => {
      const { partner, route } = x.value;
      const stops = route.stops || [];
      return { partner: partner.name, initials: initials(partner.name), skills: (partner.skills || []).map(s => categoryLabel(s.category)).join(' · ') || partner.code, km: route.distanceKm ?? route.km ?? 0, visits: stops.map((s, i) => ({ id: s.bookingId, customerName: s.customerName || `Visit ${i + 1}`, category: s.category || '', skill: s.requiredSkill || '', time: s.plannedArrival ? timeFrom(s.plannedArrival) : '—', duration: s.durationMinutes || 60 })), tint: ['mint', 'sand', 'lilac'][index % 3] };
    });
  }

  function renderAll() { renderVisits(); renderBookings(); renderPartners(); renderRoutes(); renderJourneys(); renderCategories(); renderActivity(); updateMetrics(); }
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
    $('#booking-count').textContent = `Showing ${filtered.length} ${filtered.length === 1 ? 'visit' : 'visits'}`;
    $('#booking-nav-count').textContent = String(bookings.filter(b => ['NEW','PENDING'].includes(b.status)).length || '');
  }
  function renderPartners() {
    const query = ($('#partner-search')?.value || '').toLowerCase().trim();
    const filtered = partners.filter(p => (partnerFilter === 'all' || (partnerFilter === 'available' ? p.available && p.active : p.registrationStatus === 'PENDING')) && `${p.name} ${p.code} ${(p.skills || []).map(s => s.code).join(' ')}`.toLowerCase().includes(query));
    const approved = partners.filter(p => p.registrationStatus === 'APPROVED');
    $('#partner-summary').innerHTML = `<div class="summary-tile"><span>Verified care partners</span><strong>${api.token ? approved.length : 18}</strong></div><div class="summary-tile"><span>Available right now</span><strong>${api.token ? approved.filter(p => p.available && p.active).length : 4}</strong></div><div class="summary-tile"><span>Awaiting verification</span><strong>${partners.filter(p => p.registrationStatus === 'PENDING').length}</strong></div>`;
    $('#partner-grid').innerHTML = filtered.length ? filtered.map(p => `<article class="partner-card"><div class="partner-card-top"><span class="avatar avatar-${escapeHtml(p.tint || 'mint')}">${escapeHtml(p.avatar || initials(p.name))}</span><div><h3>${escapeHtml(p.name)}</h3><span class="partner-meta">${escapeHtml(p.code || p.id || 'Care partner')}</span></div><span class="partner-state">${p.registrationStatus === 'PENDING' ? '<i class="partner-review">Needs review</i>' : `<i class="status ${p.available && p.active ? 'status-completed' : 'status-pending'}">${p.available && p.active ? 'Available' : p.active ? 'On a visit' : 'Inactive'}</i>`}</span></div><div class="skill-tags">${(p.skills || []).map(s => `<span class="skill-tag">${escapeHtml(categoryLabel(s.category))}${s.code ? ` · ${escapeHtml(s.code)}` : ''}${s.verified ? ' ✓' : ''}</span>`).join('') || '<span class="skill-tag">Skills not listed</span>'}</div><div class="partner-card-bottom"><span>Completed <strong>${p.completed ?? 0} visits</strong></span><span>Upcoming <strong>${p.upcoming ?? 0}</strong></span></div>${['ADMIN','DISPATCHER'].includes(api.role) && p.registrationStatus === 'PENDING' ? `<button class="inline-action-button partner-action" data-partner-action="verify" data-id="${escapeHtml(p.id)}">Verify & activate</button>` : ['ADMIN','DISPATCHER'].includes(api.role) && p.registrationStatus === 'APPROVED' && !p.active ? `<button class="inline-action-button partner-action" data-partner-action="activate" data-id="${escapeHtml(p.id)}">Activate partner</button>` : ''}</article>`).join('') : `<div class="empty-state"><strong>No care partners found</strong><p>${api.token ? 'No partner records are available to this account yet.' : 'Connect an admin token to load your live partner roster.'}</p></div>`;
  }
  function renderRoutes() {
    $('#route-date').textContent = prettyDate(routeDay, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
    const visits = routes.reduce((sum, r) => sum + r.visits.length, 0);
    $('#route-overview').innerHTML = `<div class="route-stat"><span>Partners on route</span><strong>${routes.length.toString().padStart(2, '0')}</strong></div><div class="route-stat"><span>Visits planned</span><strong>${visits.toString().padStart(2, '0')}</strong></div><div class="route-stat"><span>Estimated distance</span><strong>${routes.reduce((sum, r) => sum + Number(r.km || 0), 0).toFixed(1)} <small style="font:500 10px 'DM Sans';color:#9aa59d">km</small></strong></div>`;
    $('#route-list').innerHTML = routes.length ? routes.map(r => `<article class="route-card"><div class="route-card-header"><span class="avatar avatar-${escapeHtml(r.tint)}">${escapeHtml(r.initials)}</span><div><h3>${escapeHtml(r.partner)}</h3><p>${escapeHtml(r.skills)}</p></div><div class="route-meta"><span><strong>${r.visits.length}</strong> visits</span><span><strong>${Number(r.km || 0).toFixed(1)} km</strong> estimated</span></div></div><div class="route-stops">${r.visits.length ? r.visits.map((v, i) => `<div class="route-stop"><span class="stop-number">${i + 1}</span><span class="stop-time">${escapeHtml(v.time || fmtTime(v.appointmentStart))}</span><span class="stop-copy">${escapeHtml(v.customerName || 'Customer visit')}<small>${escapeHtml(categoryLabel(v.category))}${v.skill ? ` · ${escapeHtml(v.skill)}` : ''}${v.duration ? ` · ${v.duration} min` : ''}</small></span></div>`).join('') : '<div class="route-stop"><span class="stop-copy">No visits scheduled for this date.</span></div>'}</div></article>`).join('') : `<div class="panel empty-state"><span>⌁</span><strong>No route plans for this date</strong><p>Connect an admin token to load partner routes.</p></div>`;
  }
  function renderJourneys() {
    $('#journey-list').innerHTML = journeys.length ? journeys.map((j, i) => { const person = partners.find(p => p.id === j.partnerId); const eta = j.eta ? `${Math.max(1, Math.round((new Date(j.eta) - Date.now()) / 60000))} min` : (j.status === 'PARTNER_ARRIVED' ? 'Arrived' : 'Updating'); return `<article class="journey-card"><div class="journey-person"><span class="avatar avatar-${escapeHtml(person?.tint || ['sand','lilac','mint'][i % 3])}">${escapeHtml(person?.avatar || initials(j.partnerName))}</span><div><strong>${escapeHtml(j.partnerName)}</strong><small>${escapeHtml(j.service || statusLabel(j.status))}</small></div><span class="eta-chip">${escapeHtml(eta)}</span></div><div class="journey-detail"><span>${escapeHtml(j.customerName || `Booking ${j.bookingId?.slice(0, 8) || ''}`)}</span><strong>${j.distanceKm == null ? 'Location updating' : j.distanceKm < .1 ? 'At the door' : `${Number(j.distanceKm).toFixed(1)} km away`}</strong></div><div class="journey-line"><i style="left:${Math.min(88, 22 + i * 20)}%"></i></div></article>`; }).join('') : `<div class="empty-state"><span>◎</span><strong>No active journeys</strong><p>Partners heading to a visit will show here.</p></div>`;
    $('#last-updated').textContent = `Updated ${api.token ? 'just now' : 'with preview data'}`;
  }
  function renderCategories() {
    const counts = ['NURSING', 'AYURVEDIC_THERAPY', 'COUNSELLING'].map(category => bookings.filter(b => b.category === category).length);
    const max = Math.max(1, ...counts);
    $('#category-rows').innerHTML = ['NURSING', 'AYURVEDIC_THERAPY', 'COUNSELLING'].map((category, i) => `<div class="category-row"><span class="category-name">${categoryLabel(category)}</span><div class="category-track"><div class="category-fill" style="width:${Math.max(9, counts[i] / max * 100)}%"></div></div><span class="category-count">${counts[i].toString().padStart(2, '0')}</span></div>`).join('');
  }
  function renderActivity() {
    const entries = bookings.slice(0, 3).map((b, i) => ({ icon: ['✦','↗','✓'][i], cls: ['','orange','purple'][i], title: b.status === 'COMPLETED' ? 'Visit completed' : b.status === 'PENDING' ? 'Partner needed' : 'Visit confirmed', text: `${b.customerName} · ${categoryLabel(b.category)}`, ago: ['Just now', '18 min ago', '42 min ago'][i] }));
    $('#activity-list').innerHTML = entries.map(a => `<div class="activity-item"><span class="activity-icon ${a.cls}">${a.icon}</span><div class="activity-copy"><strong>${escapeHtml(a.title)}</strong> · ${escapeHtml(a.text)}<small>${a.ago}</small></div></div>`).join('');
  }
  function updateMetrics() {
    if (api.token) {
      $('#metric-visits').textContent = String(bookings.filter(b => b.date === isoDate(today)).length);
      $('#metric-partners').innerHTML = `${partners.filter(p => p.active && p.registrationStatus === 'APPROVED').length}<span class="metric-denom"> / ${partners.length}</span>`;
      $('#metric-attention').textContent = String(bookings.filter(b => ['NEW','PENDING'].includes(b.status)).length + partners.filter(p => p.registrationStatus === 'PENDING').length).padStart(2, '0');
    }
  }
  function showPage(page) {
    const target = document.getElementById(`page-${page}`); if (!target) return;
    currentPage = page;
    $$('.page').forEach(el => el.classList.toggle('active', el === target));
    $$('.nav-link').forEach(el => el.classList.toggle('active', el.dataset.page === page));
    const names = { overview: 'Overview', bookings: 'Bookings', partners: 'Care partners', routes: 'Routes & visits', tracking: 'Live tracking' };
    $('#crumb').textContent = names[page]; document.title = `${names[page]} · SathiRoute`;
    $('#sidebar').classList.remove('open');
    if (page === 'routes' && api.token) loadLiveRoutes().then(renderRoutes).catch(() => {});
  }
  let toastTimer;
  function toast(message) { const el = $('#toast'); el.textContent = message; el.classList.add('show'); clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.remove('show'), 3600); }
  function openModal(id) { const modal = document.getElementById(id); modal.classList.add('open'); modal.setAttribute('aria-hidden', 'false'); const first = $('input,select,textarea', modal); setTimeout(() => first?.focus(), 60); }
  function closeModal(id) { const modal = document.getElementById(id); modal.classList.remove('open'); modal.setAttribute('aria-hidden', 'true'); }
  function dateDefaults() { const field = $('#booking-form [name="date"]'); field.min = isoDate(today); const tomorrow = new Date(today); tomorrow.setDate(today.getDate() + 1); field.value = isoDate(tomorrow); }
  function bindEvents() {
    $$('.nav-link').forEach(button => button.addEventListener('click', () => showPage(button.dataset.page)));
    $$('[data-go]').forEach(button => button.addEventListener('click', () => showPage(button.dataset.go)));
    $$('[data-action="new-booking"]').forEach(button => button.addEventListener('click', () => { $('#booking-message').textContent = ''; dateDefaults(); openModal('booking-modal'); }));
    $$('[data-action="connect"]').forEach(button => button.addEventListener('click', () => { $('#connect-message').textContent = ''; $('#connect-form [name="token"]').value = api.token; $('#connect-form [name="apiBase"]').value = api.base; openModal('connect-modal'); }));
    $$('[data-close]').forEach(button => button.addEventListener('click', () => closeModal(button.dataset.close)));
    $$('.modal-backdrop').forEach(backdrop => backdrop.addEventListener('click', e => { if (e.target === backdrop) closeModal(backdrop.id); }));
    document.addEventListener('keydown', e => { if (e.key === 'Escape') $$('.modal-backdrop.open').forEach(el => closeModal(el.id)); });
    $('#mobile-menu').addEventListener('click', () => $('#sidebar').classList.toggle('open'));
    $('#booking-search').addEventListener('input', renderBookings);
    $('#partner-search').addEventListener('input', renderPartners);
    $$('[data-filter]').forEach(button => button.addEventListener('click', () => { bookingFilter = button.dataset.filter.toLowerCase() === 'all' ? 'all' : button.dataset.filter; $$('[data-filter]').forEach(b => b.classList.toggle('selected', b === button)); renderBookings(); }));
    $$('[data-partner-filter]').forEach(button => button.addEventListener('click', () => { partnerFilter = button.dataset.partnerFilter; $$('[data-partner-filter]').forEach(b => b.classList.toggle('selected', b === button)); renderPartners(); }));
    $('#refresh-bookings').addEventListener('click', () => api.token ? connectLive().catch(e => toast(e.message)) : toast('Connect an API token to refresh live bookings.'));
    $('#route-prev').addEventListener('click', () => { routeDay.setDate(routeDay.getDate() - 1); renderRoutes(); if (api.token) loadLiveRoutes().then(renderRoutes).catch(e => toast(e.message)); });
    $('#route-next').addEventListener('click', () => { routeDay.setDate(routeDay.getDate() + 1); renderRoutes(); if (api.token) loadLiveRoutes().then(renderRoutes).catch(e => toast(e.message)); });
    $('#connect-form').addEventListener('submit', async e => {
      e.preventDefault(); const form = new FormData(e.currentTarget); api.base = String(form.get('apiBase') || '').trim().replace(/\/$/, ''); api.token = String(form.get('token') || '').trim().replace(/^Bearer\s+/i, '');
      if (!api.token) { $('#connect-message').textContent = 'Paste a valid access token to connect.'; return; }
      localStorage.setItem('sathiroute.apiBase', api.base); localStorage.setItem('sathiroute.token', api.token); $('#connect-message').textContent = 'Checking access…';
      try { await connectLive(); closeModal('connect-modal'); } catch (error) { $('#connect-message').textContent = error.message; }
    });
    $('#disconnect-api').addEventListener('click', () => { localStorage.removeItem('sathiroute.token'); localStorage.removeItem('sathiroute.apiBase'); api.token = ''; api.base = ''; bookings = seedBookings(); partners = seedPartners(); journeys = seedJourneys(); routes = seedRoutes(); setLive(false); renderAll(); closeModal('connect-modal'); toast('Preview data restored.'); });
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
  async function submitBooking(event) {
    event.preventDefault(); const message = $('#booking-message'); const button = $('#submit-booking'); message.textContent = '';
    if (!api.token) { message.textContent = 'Connect a customer access token before creating a live booking.'; openModal('connect-modal'); return; }
    const data = Object.fromEntries(new FormData(event.currentTarget)); button.disabled = true; button.textContent = 'Creating…';
    try {
      let profile = await request('/v1/customers/me').catch(async error => { if (!String(error.message).includes('404')) throw error; return request('/v1/customers', { method: 'POST', body: JSON.stringify({ name: data.name, email: data.email, phone: data.phone }) }); });
      if (!profile) throw new Error('Could not create customer profile.');
      const booking = await request('/v1/bookings', { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ serviceCategory: data.category, requiredSkill: data.skill, appointmentDate: data.date, appointmentStart: data.time, serviceDurationMinutes: Number(data.duration), address: data.address, latitude: Number(data.latitude), longitude: Number(data.longitude), notes: data.notes || '' }) });
      const newItem = normalizeBooking({ ...booking, customerName: data.name, customerEmail: data.email, category: data.category, requiredSkill: data.skill, appointmentDate: data.date, appointmentStart: data.time, serviceDurationMinutes: Number(data.duration) });
      bookings.unshift(newItem); renderAll(); closeModal('booking-modal'); event.currentTarget.reset(); dateDefaults(); showPage('bookings'); toast('Booking created. We’re finding the right care partner.');
    } catch (error) { message.textContent = `${error.message}. Check that this token has the CUSTOMER role and the API is reachable.`; }
    finally { button.disabled = false; button.innerHTML = 'Create booking <span>→</span>'; }
  }
  function initialize() {
    $('#today-label').textContent = prettyDate(today, { weekday: 'short', month: 'short', day: 'numeric' });
    $('#schedule-date').textContent = prettyDate(today, { month: 'long', day: 'numeric' });
    $('#route-date').textContent = prettyDate(routeDay, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
    bindEvents(); renderAll(); setLive(Boolean(api.token));
    if (api.token) connectLive().catch(error => { setLive(false); toast(`Preview mode: ${error.message}`); });
    $('#booking-form [name="category"]').addEventListener('change', e => { const skill = $('#booking-form [name="skill"]'); skill.value = ''; skill.placeholder = { NURSING: 'e.g. RN', AYURVEDIC_THERAPY: 'e.g. Panchakarma', COUNSELLING: 'e.g. Family counselling' }[e.target.value]; });
  }
  initialize();
})();
