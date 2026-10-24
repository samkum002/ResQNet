/**
 * ResQNet Authentication API
 *
 * Flow:
 * 1. initializeCsrf() calls /public/csrf to set the XSRF-TOKEN cookie.
 * 2. State-mutating requests (POST /user/login, POST /public/register, POST /logout)
 *    include the X-XSRF-TOKEN header read from document.cookie.
 * 3. Login is submitted as application/x-www-form-urlencoded with exact fields
 *    'username' and 'password' to /user/login.
 * 4. getCurrentUser() calls /user/me to fetch { userId, username, roles }.
 */

/**
 * Read the XSRF-TOKEN cookie written by CookieCsrfTokenRepository.
 */
export function getCsrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Build headers for authenticated / state-changing requests.
 */
export function buildHeaders(extra = {}) {
  const token = getCsrfToken();
  return {
    'X-Requested-With': 'XMLHttpRequest',
    ...(token ? { 'X-XSRF-TOKEN': token } : {}),
    ...extra,
  };
}

/**
 * Initialize CSRF by pinging the public CSRF endpoint.
 * This instructs Spring Security to issue the XSRF-TOKEN cookie.
 */
export async function initializeCsrf() {
  try {
    const res = await fetch('/public/csrf', {
      method: 'GET',
      credentials: 'include',
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    });
    if (res.ok) {
      const data = await res.json().catch(() => null);
      return data;
    }
  } catch {
    // Network error will be reported on subsequent requests
  }
  return null;
}

/**
 * Submit Spring Security form login to /user/login.
 * Request format: application/x-www-form-urlencoded
 *
 * Returns: { username: string, roles: string[], redirect: string }
 */
export async function login(username, password) {
  // Ensure we have the CSRF token before POSTing
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const body = new URLSearchParams({ username, password });

  const response = await fetch('/user/login', {
    method: 'POST',
    headers: buildHeaders({
      'Content-Type': 'application/x-www-form-urlencoded',
    }),
    body,
    credentials: 'include',
  });

  if (response.ok) {
    const contentType = response.headers.get('Content-Type') || '';
    if (contentType.includes('application/json')) {
      return await response.json();
    }
    // If redirected to dashboard URL
    return { redirect: response.url || '/dashboard/user' };
  }

  // 401 Unauthorized or failure redirect
  let errorMessage = 'Invalid username or password.';
  try {
    const data = await response.json();
    if (data && data.error) {
      errorMessage = data.error;
    }
  } catch {
    // Non-JSON failure response
    if (response.status === 403) {
      errorMessage = 'Access denied or CSRF token expired. Please refresh.';
    }
  }

  throw new Error(errorMessage);
}

/**
 * Register a new user account via POST /public/register.
 * Backend only grants the USER role during registration.
 *
 * Payload: { email, username, password }
 */
export async function register(email, username, password) {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }

  const response = await fetch('/public/register', {
    method: 'POST',
    headers: buildHeaders({
      'Content-Type': 'application/json',
    }),
    body: JSON.stringify({ email, username, password }),
    credentials: 'include',
  });

  if (response.ok) {
    return await response.text();
  }

  const raw = await response.text();
  try {
    const json = JSON.parse(raw);
    if (json.errors && json.errors.length > 0) {
      throw new Error(json.errors[0].defaultMessage || 'Validation error');
    }
    if (json.message) {
      throw new Error(json.message);
    }
  } catch (e) {
    if (e.message && e.message !== 'Unexpected token') {
      throw e;
    }
  }

  throw new Error(raw || 'Registration failed. Please check your inputs.');
}

/**
 * Fetch authenticated user information from GET /user/me.
 * Returns { userId, username, roles } or null if unauthenticated.
 */
export async function getCurrentUser() {
  try {
    const response = await fetch('/user/me', {
      method: 'GET',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'X-Requested-With': 'XMLHttpRequest',
      },
    });

    if (response.ok) {
      return await response.json();
    }
    return null;
  } catch {
    return null;
  }
}

/**
 * Map authenticated roles to the designated dashboard route.
 */
export function getDashboardRoute(roles = []) {
  if (roles.includes('ADMIN')) return '/dashboard/admin';
  if (roles.includes('STATION_MANAGER')) return '/dashboard/station';
  if (roles.includes('WORKER')) return '/dashboard/worker';
  return '/dashboard/user';
}

/**
 * Log out via POST /logout.
 */
export async function logout() {
  if (!getCsrfToken()) {
    await initializeCsrf();
  }
  try {
    const res = await fetch('/logout', {
      method: 'POST',
      headers: buildHeaders(),
      credentials: 'include',
    });
    return res.ok;
  } catch (err) {
    console.error('Logout error:', err);
    return false;
  }
}
