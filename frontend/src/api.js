// Dünner Wrapper um fetch: Session-Cookie (same-origin) und CSRF-Header für schreibende Aufrufe.

function csrfToken() {
  const match = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='));
  return match ? decodeURIComponent(match.split('=')[1]) : '';
}

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function request(path, { method = 'GET', body, form } = {}) {
  const headers = {};
  let payload;
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = csrfToken();
  if (form) {
    headers['Content-Type'] = 'application/x-www-form-urlencoded';
    payload = new URLSearchParams(form);
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const res = await fetch(path, { method, headers, body: payload, credentials: 'same-origin' });
  if (!res.ok) {
    let message = `Fehler ${res.status}`;
    try {
      const problem = await res.json();
      if (problem.detail) message = problem.detail;
    } catch { /* keine JSON-Antwort */ }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204) return null;
  const type = res.headers.get('content-type') || '';
  return type.includes('json') ? res.json() : null;
}

export const api = {
  me: () => request('/api/me'),
  login: (username, password) => request('/api/login', { method: 'POST', form: { username, password } }),
  logout: () => request('/api/logout', { method: 'POST' }),
  entries: (from, to) => request(`/api/entries?from=${from}&to=${to}`),
  summary: (month) => request(`/api/summary?month=${month}`),
  createEntry: (data) => request('/api/entries', { method: 'POST', body: data }),
  requestCorrection: (id, data) => request(`/api/entries/${id}/corrections`, { method: 'POST', body: data }),
  myCorrections: () => request('/api/corrections/mine'),
  pending: () => request('/api/corrections/pending'),
  decide: (id, approve, comment) => request(`/api/corrections/${id}/decision`, { method: 'POST', body: { approve, comment } }),
  teamMembers: () => request('/api/team/members'),
  teamEntries: (userId, month) => request(`/api/team/${userId}/entries?month=${month}`),
  users: () => request('/api/admin/users'),
  createUser: (data) => request('/api/admin/users', { method: 'POST', body: data }),
  audit: () => request('/api/admin/audit?limit=100')
};
