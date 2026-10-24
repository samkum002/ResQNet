import { buildHeaders, getCsrfToken, initializeCsrf } from './auth';

/**
 * Fetch admin statistics and assigned disasters.
 * GET /admin/disasters
 */
export async function fetchAdminDisasters() {
  const response = await fetch('/admin/disasters', {
    method: 'GET',
    headers: buildHeaders({ Accept: 'application/json' }),
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`Failed to load admin data (${response.status})`);
  }

  return await response.json(); // { stats: Stats, disasters: List<disasterDto> }
}

/**
 * Update disaster report details (severity, forces, disasterType, suspicious, userReport).
 * PATCH /admin/{disasterId}/update (fallback: /disaster/{disasterId}/update)
 */
export async function updateDisaster(disasterId, data) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  let response = await fetch(`/admin/${disasterId}/update`, {
    method: 'PATCH',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(data),
    credentials: 'include',
  });

  if (!response.ok && response.status === 404) {
    response = await fetch(`/disaster/${disasterId}/update`, {
      method: 'PATCH',
      headers: buildHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(data),
      credentials: 'include',
    });
  }

  if (!response.ok) {
    const txt = await response.text();
    throw new Error(txt || 'Failed to update disaster');
  }

  return await response.json();
}

/**
 * Approve a disaster assignment.
 * PATCH /admin/{disasterId}/approve
 */
export async function approveDisaster(disasterId, overrides = null) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const response = await fetch(`/admin/${disasterId}/approve`, {
    method: 'PATCH',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: overrides ? JSON.stringify(overrides) : undefined,
    credentials: 'include',
  });

  if (!response.ok) {
    const txt = await response.text();
    throw new Error(txt || 'Failed to approve disaster');
  }

  return response.ok;
}

/**
 * Reject a disaster.
 * POST /admin/{disasterId}/reject
 */
export async function rejectDisaster(disasterId) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const response = await fetch(`/admin/${disasterId}/reject`, {
    method: 'POST',
    headers: buildHeaders(),
    credentials: 'include',
  });

  if (!response.ok) {
    const txt = await response.text();
    throw new Error(txt || 'Failed to reject disaster');
  }

  return response.ok;
}
