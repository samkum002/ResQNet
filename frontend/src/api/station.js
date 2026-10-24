import { buildHeaders, getCsrfToken, initializeCsrf } from './auth';

/**
 * Fetch station statistics and assigned missions.
 * GET /station/missions
 */
export async function fetchStationMissions() {
  const response = await fetch('/station/missions', {
    method: 'GET',
    headers: buildHeaders({ Accept: 'application/json' }),
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`Failed to load station missions (${response.status})`);
  }

  return await response.json(); // { stats: Stats, dispatches: List<dispatchDto> }
}

/**
 * Approve a station dispatch mission.
 * PATCH /station/{dispatchId}/approve
 */
export async function approveDispatch(dispatchId) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const response = await fetch(`/station/${dispatchId}/approve`, {
    method: 'PATCH',
    headers: buildHeaders(),
    credentials: 'include',
  });

  if (!response.ok) {
    const txt = await response.text();
    throw new Error(txt || 'Failed to approve dispatch');
  }

  return response.ok;
}

/**
 * Reject a station dispatch mission.
 * PATCH /station/{dispatchId}/reject
 */
export async function rejectDispatch(dispatchId) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const response = await fetch(`/station/${dispatchId}/reject`, {
    method: 'PATCH',
    headers: buildHeaders(),
    credentials: 'include',
  });

  if (!response.ok) {
    const txt = await response.text();
    throw new Error(txt || 'Failed to reject dispatch');
  }

  return response.ok;
}

/**
 * Fetch all pending recall requests for the station.
 * GET /station/recall/pending
 */
export async function fetchPendingRecalls() {
  const response = await fetch('/station/recall/pending', {
    method: 'GET',
    headers: buildHeaders({ Accept: 'application/json' }),
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`Failed to load pending recalls (${response.status})`);
  }

  return await response.json(); // List<recallRequestDto>
}

/**
 * Station manager approves a recall request.
 * PATCH /station/recall/{recallId}/approve
 */
export async function approveRecall(recallId) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const response = await fetch(`/station/recall/${recallId}/approve`, {
    method: 'PATCH',
    headers: buildHeaders(),
    credentials: 'include',
  });

  if (!response.ok) {
    const txt = await response.text();
    throw new Error(txt || 'Failed to approve recall');
  }

  return response.ok;
}

/**
 * Station manager rejects a recall request.
 * PATCH /station/recall/{recallId}/reject
 */
export async function rejectRecall(recallId) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const response = await fetch(`/station/recall/${recallId}/reject`, {
    method: 'PATCH',
    headers: buildHeaders(),
    credentials: 'include',
  });

  if (!response.ok) {
    const txt = await response.text();
    throw new Error(txt || 'Failed to reject recall');
  }

  return response.ok;
}

/**
 * Station manager confirms completion of a dispatch.
 * PATCH /station/{dispatchId}/complete
 */
export async function confirmCompleteDispatch(dispatchId) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const response = await fetch(`/station/${dispatchId}/complete`, {
    method: 'PATCH',
    headers: buildHeaders(),
    credentials: 'include',
  });

  if (!response.ok) {
    const txt = await response.text();
    throw new Error(txt || 'Failed to confirm dispatch completion');
  }

  return response.ok;
}

/**
 * Station manager confirms false alarm of a dispatch.
 * PATCH /station/{dispatchId}/fake
 */
export async function confirmFakeDispatch(dispatchId) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const response = await fetch(`/station/${dispatchId}/fake`, {
    method: 'PATCH',
    headers: buildHeaders(),
    credentials: 'include',
  });

  if (!response.ok) {
    const txt = await response.text();
    throw new Error(txt || 'Failed to confirm false alarm');
  }

  return response.ok;
}

