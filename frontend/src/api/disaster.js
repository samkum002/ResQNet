import { buildHeaders, getCsrfToken, initializeCsrf } from './auth';

/**
 * Report a disaster with multipart upload to POST /disaster/report.
 *
 * @param {Object} params
 * @param {File} params.imageFile
 * @param {number} params.latitude
 * @param {number} params.longitude
 * @param {string} params.userReport - Single alphabetic word (^[A-Za-z]+$)
 * @returns {Promise<{disasterId: string, msg: string, status: string}>}
 */
export async function reportDisaster({ imageFile, latitude, longitude, userReport }) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const formData = new FormData();
  formData.append('image', imageFile);
  formData.append('latitude', latitude);
  formData.append('longitude', longitude);
  formData.append('userReport', userReport);

  const response = await fetch('/disaster/report', {
    method: 'POST',
    headers: buildHeaders(),
    body: formData,
    credentials: 'include',
  });

  if (!response.ok) {
    let message = 'Failed to report incident.';
    try {
      const err = await response.json();
      if (err.message) message = err.message;
    } catch {
      const txt = await response.text();
      if (txt) message = txt;
    }
    throw new Error(message);
  }

  return await response.json();
}

/**
 * Fetch the authenticated user's reported disasters from GET /disaster/my-reports.
 * @returns {Promise<Array<{disasterId: string, msg: string, status: string, cause: string, latitude: number, longitude: number, createdAt: string}>>}
 */
export async function fetchUserReports() {
  const response = await fetch('/disaster/my-reports', {
    method: 'GET',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    credentials: 'include',
  });

  if (!response.ok) {
    return [];
  }

  return await response.json();
}

/**
 * Fetch station coordinates for map rendering from GET /user/stations.
 * @returns {Promise<Array<{stationId: string, stationName: string, forceType: string, state: string, latitude: number, longitude: number}>>}
 */
export async function fetchStations() {
  const response = await fetch('/user/stations', {
    method: 'GET',
    headers: buildHeaders({ 'Content-Type': 'application/json' }),
    credentials: 'include',
  });

  if (!response.ok) {
    return [];
  }

  return await response.json();
}
