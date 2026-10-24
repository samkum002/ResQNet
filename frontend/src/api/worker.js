import { buildHeaders, getCsrfToken, initializeCsrf } from './auth';

/**
 * Fetch assigned dispatches and backup requests for the current worker.
 * GET /dispatch/assigned
 */
export async function fetchWorkerDispatches() {
  const response = await fetch('/dispatch/assigned', {
    method: 'GET',
    headers: buildHeaders({ Accept: 'application/json' }),
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`Failed to load assigned missions (${response.status})`);
  }

  return await response.json(); // { dispatch: List<dispatchDto>, backup: List<dtoBackup> }
}

/**
 * Start a dispatch simulation route.
 * POST /dispatch/start/{dispatchId}
 */
export async function startDispatch(dispatchId) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const response = await fetch(`/dispatch/start/${dispatchId}`, {
    method: 'POST',
    headers: buildHeaders(),
    credentials: 'include',
  });

  if (!response.ok) {
    const txt = await response.text();
    throw new Error(txt || 'Failed to start dispatch mission');
  }

  return response.ok;
}

/**
 * Request backup for a mission.
 * POST /dispatch/backup/{dispatchId}/request
 */
export async function requestBackup(dispatchId, { reqVehicles, reqPersonnel }) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const response = await fetch(`/dispatch/backup/${dispatchId}/request`, {
    method: 'POST',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ reqVehicles, reqPersonnel }),
    credentials: 'include',
  });

  if (!response.ok) {
    const txt = await response.text();
    throw new Error(txt || 'Failed to request backup');
  }

  return response.ok;
}

/**
 * Request recall of a force.
 * POST /dispatch/recall/{dispatchId}
 */
export async function requestRecall(dispatchId, reason) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const response = await fetch(`/dispatch/recall/${dispatchId}`, {
    method: 'POST',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ reason: reason || '' }),
    credentials: 'include',
  });

  if (!response.ok) {
    let msg = 'Failed to submit recall request';
    try {
      const err = await response.json();
      if (err.message) msg = err.message;
    } catch {
      const txt = await response.text();
      if (txt) msg = txt;
    }
    throw new Error(msg);
  }

  return await response.text();
}

/**
 * Fetch active recall requests submitted by current worker.
 * GET /dispatch/recall/active
 */
export async function fetchActiveRecalls() {
  const response = await fetch('/dispatch/recall/active', {
    method: 'GET',
    headers: buildHeaders({ Accept: 'application/json' }),
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`Failed to load active recall requests (${response.status})`);
  }

  return await response.json(); // List<recallRequestDto>
}

/**
 * Complete a dispatch mission.
 * PATCH /dispatch/complete/{dispatchId}
 */
export async function completeDispatch(dispatchId) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const response = await fetch(`/dispatch/complete/${dispatchId}`, {
    method: 'PATCH',
    headers: buildHeaders(),
    credentials: 'include',
  });

  if (!response.ok) {
    const txt = await response.text();
    throw new Error(txt || 'Failed to complete mission');
  }

  return response.ok;
}

/**
 * Mark a dispatch as a false alarm.
 * PATCH /dispatch/false/{dispatchId}
 */
export async function markFalseDispatch(dispatchId) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const response = await fetch(`/dispatch/false/${dispatchId}`, {
    method: 'PATCH',
    headers: buildHeaders(),
    credentials: 'include',
  });

  if (!response.ok) {
    const txt = await response.text();
    throw new Error(txt || 'Failed to report false alarm');
  }

  return response.ok;
}
