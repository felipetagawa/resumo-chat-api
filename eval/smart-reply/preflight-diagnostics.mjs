// Only these literal values may cross the diagnostic boundary. Never read messages,
// stacks, URLs, headers, bodies, or arbitrary error properties into a report.
const allowed = new Set(['CONNECT_TIMEOUT', 'READ_TIMEOUT', 'INTERACTIVE_DEADLINE',
  'READ_FAILED', 'NETWORK_ERROR', 'DNS_FAILED', 'TLS_FAILED', 'CONNECTION_FAILED',
  'CALL_LIMIT', 'BUDGET_LIMIT', 'PERSISTENCE_FAILED']);
const dns = new Set(['ENOTFOUND', 'EAI_AGAIN']);
const tls = new Set(['CERT_HAS_EXPIRED', 'CERT_NOT_YET_VALID', 'DEPTH_ZERO_SELF_SIGNED_CERT',
  'SELF_SIGNED_CERT_IN_CHAIN', 'UNABLE_TO_VERIFY_LEAF_SIGNATURE', 'UNABLE_TO_GET_ISSUER_CERT_LOCALLY',
  'ERR_TLS_CERT_ALTNAME_INVALID', 'ERR_TLS_HANDSHAKE_TIMEOUT', 'ERR_SSL_WRONG_VERSION_NUMBER']);
const connection = new Set(['ECONNREFUSED', 'ECONNRESET', 'ECONNABORTED', 'EPIPE', 'ENETUNREACH', 'EHOSTUNREACH']);

export function preflightErrorCode(error) {
  // Bounded cause traversal handles Node's wrapped network failures without copying them.
  for (let depth = 0; error && depth < 3; depth++, error = error.cause) {
    if (allowed.has(error.diagnosticCode)) return error.diagnosticCode;
    if (allowed.has(error.safeCode)) return error.safeCode;
    if (dns.has(error.code)) return 'DNS_FAILED';
    if (tls.has(error.code)) return 'TLS_FAILED';
    if (connection.has(error.code)) return 'CONNECTION_FAILED';
    if (error.code === 'ETIMEDOUT') return 'CONNECT_TIMEOUT';
  }
  return 'NETWORK_ERROR';
}
