import { Client } from '@stomp/stompjs';

/**
 * Read the XSRF-TOKEN cookie set by CookieCsrfTokenRepository.
 */
function getCsrfToken() {
  if (typeof document === 'undefined') return null;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Creates and activates a STOMP WebSocket client configured for ResQNet.
 * Spring Boot STOMP endpoint: /resqnet
 * User destination prefix: /user
 *
 * @param {Object} options
 * @param {Function} [options.onConnect] - Called with frame when connection is established
 * @param {Function} [options.onError] - Called when a STOMP protocol error occurs
 * @param {Function} [options.onDisconnect] - Called when the connection closes
 * @returns {Client} active STOMP client
 */
export function createStompClient({ onConnect, onError, onDisconnect } = {}) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const brokerURL = `${protocol}//${window.location.host}/resqnet`;
  const csrfToken = getCsrfToken();

  const client = new Client({
    brokerURL,
    connectHeaders: csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {},
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: (frame) => {
      console.log('[STOMP] Connected to ResQNet broker');
      if (onConnect) onConnect(frame);
    },
    onStompError: (frame) => {
      console.warn('[STOMP] protocol error:', frame);
      if (onError) onError(frame);
    },
    onWebSocketClose: (evt) => {
      console.log('[STOMP] connection closed:', evt);
      if (onDisconnect) onDisconnect();
    },
  });

  client.activate();
  return client;
}
