import {request as httpsRequest} from 'node:https';
import {performance} from 'node:perf_hooks';
import {pathToFileURL} from 'node:url';
import path from 'node:path';

import {pricing, profileFor, requestBody, projectCost} from './profiles.mjs';
import {main} from './runner.mjs';
import {preflightErrorCode} from './preflight-diagnostics.mjs';
export const rates = Object.fromEntries(Object.entries(pricing).map(([id, rate]) => [id, rate.promo]));
const transient = new Set([429, 500, 502, 503, 504]);

export function validateBundle(bundle) {
  if (bundle.schemaVersion !== 1 || bundle.baseUrl !== 'https://generativelanguage.googleapis.com/v1/models') {
    throw Error('Unsupported export; use the actual v1 backend request bundle.');
  }
  if (!Array.isArray(bundle.cases) || bundle.cases.length !== 30) throw Error('Expected 30 synthetic cases.');
  const t = bundle.transport;
  if (t.connectTimeoutMs !== 1500 || t.readTimeoutMs !== 4000 || t.maxAttempts !== 2
      || t.retryDelayMs !== 250 || t.extensionTimeoutMs !== 15000) {
    throw Error('Transport changed; review benchmark parity before evaluation.');
  }
  const ids = new Set();
  for (const c of bundle.cases) {
    if (ids.has(c.id)) throw Error('Duplicate case ID');
    ids.add(c.id);
    const config = c.body.generationConfig;
    if (config.temperature !== 0.3 || config.maxOutputTokens !== 512
        || Object.keys(config).length !== 2 || c.body.tools || c.body.systemInstruction
        || c.body.contents.length !== 1 || c.body.contents[0].role !== 'user'
        || c.body.contents[0].parts.length !== 1) throw Error('Generation request changed; recheck parity.');
    const prompt = c.body.contents[0].parts[0].text;
    const marker = '\n\nDADOS DO ATENDIMENTO (JSON):\n';
    const offset = prompt.indexOf(marker);
    if (offset < 0) throw Error('Missing factual data boundary');
    const facts = JSON.parse(prompt.slice(offset + marker.length));
    if (Object.keys(facts).sort().join(',') !== 'conversation,promptComplement'
        || facts.conversation.length > 16000 || facts.promptComplement.length > 2000) {
      throw Error('Unexpected factual fields or limits');
    }
  }
}

export function authorizePaid(paid, env, models) {
  if (!paid || env.SMART_REPLY_EVAL_AUTHORIZED !== 'YES') throw Error('Separate user authorization and both paid-run guards are required.');
  if (!env.GEMINI_API_KEY) throw Error('GEMINI_API_KEY must be supplied only through the process environment.');
  if (!models.length || new Set(models).size !== models.length || models.some(m => !Object.hasOwn(rates, m))) {
    throw Error('Unknown or duplicate model ID; verify official documentation first.');
  }
}

export function deterministicChecks(output, finishReason, checks = {}) {
  const result = {nonempty: typeof output === 'string' && output.trim().length > 0,
    withinCharacterLimit: typeof output === 'string' && output.length <= 6000,
    complete: finishReason === 'STOP'};
  if (checks.noGreeting) {
    result.noRedundantGreeting = !/^\s*(?:ol[aá]|oi\b|bom dia|boa tarde|boa noite|tudo bem|seja bem.vind[oa]|me chamo|sou (?:o|a) (?:técnic[oa]|atendente))/iu.test(output);
  }
  for (const [i, pattern] of (checks.forbidden ?? []).entries()) {
    result[`forbidden${i}`] = !new RegExp(pattern, 'iu').test(output);
  }
  for (const [i, pattern] of (checks.required ?? []).entries()) {
    result[`required${i}`] = new RegExp(pattern, 'iu').test(output);
  }
  return result;
}

export function usageAndCost(usage, model, period = 'promo') {
  const unknown = {inputTokens: null, outputTokens: null, thinkingTokens: null, billedOutputTokens: null, estimatedUsd: null, projectedCosts: null};
  const count = n => Number.isInteger(n) && n >= 0;
  if (!usage || !count(usage.promptTokenCount) || !count(usage.candidatesTokenCount)) return unknown;
  let thinking = usage.thoughtsTokenCount;
  const inferred = thinking === undefined && count(usage.totalTokenCount)
    ? usage.totalTokenCount - usage.promptTokenCount - usage.candidatesTokenCount : undefined;
  if (thinking === undefined && count(inferred)) thinking = inferred;
  if (thinking === undefined && model === 'gemini-2.5-flash-lite') thinking = 0;
  const cache = usage.cachedContentTokenCount ?? 0;
  if (!count(thinking) || !count(cache) || cache > usage.promptTokenCount
      || (usage.totalTokenCount !== undefined && (!count(usage.totalTokenCount)
      || usage.totalTokenCount !== usage.promptTokenCount + usage.candidatesTokenCount + thinking))) {
    return {...unknown, inputTokens: usage.promptTokenCount, outputTokens: usage.candidatesTokenCount};
  }
  const projectedCosts = projectCost(usage.promptTokenCount - 0.9 * cache, usage.candidatesTokenCount, thinking, model);
  return {inputTokens: usage.promptTokenCount, outputTokens: usage.candidatesTokenCount,
    thinkingTokens: thinking, thinkingCountSource: usage.thoughtsTokenCount === undefined ? 'total-reconciled-or-baseline' : 'metadata',
    billedOutputTokens: usage.candidatesTokenCount + thinking,
    totalTokens: usage.totalTokenCount ?? null, cachedTokens: cache,
    estimatedUsd: projectedCosts[period].perResponse, projectedCosts};
}

// New socket for each attempt; no credential in URL, logs or saved artifacts.
export function transport({url, method, body, key, timing, deadlineMs}) {
  return new Promise((resolve, reject) => {
    const payload = body ? JSON.stringify(body) : undefined;
    let responseStatus;
    const req = httpsRequest(url, {method, agent: false,
      headers: {'Content-Type': 'application/json', 'x-goog-api-key': key}}, res => {
      responseStatus = res.statusCode;
      let data = '';
      res.setEncoding('utf8');
      res.on('data', chunk => {data += chunk;});
      res.on('error', () => req.destroy(Object.assign(Error('Read failed'), {safeCode: 'READ_FAILED', httpStatus: res.statusCode})));
      res.on('end', () => {
        let parsed;
        // Never save an external error body; it can echo sensitive information.
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try { parsed = JSON.parse(data); } catch { parsed = null; }
        }
        resolve({status: res.statusCode, headers: {'retry-after': res.headers['retry-after']}, body: parsed});
      });
    });
    const connectTimer = setTimeout(() => req.destroy(Object.assign(Error('Connect timeout'),
      {safeCode: 'CONNECT_TIMEOUT', retryable: true})), timing.connectTimeoutMs);
    const deadlineTimer = setTimeout(() => req.destroy(Object.assign(Error('Interactive deadline'),
      {safeCode: 'INTERACTIVE_DEADLINE', retryable: false})), deadlineMs);
    req.on('socket', socket => socket.once('secureConnect', () => {
      clearTimeout(connectTimer);
      req.setTimeout(timing.readTimeoutMs, () => req.destroy(Object.assign(Error('Read timeout'),
        {safeCode: 'READ_TIMEOUT', retryable: true})));
    }));
    req.on('error', e => reject({...(method === 'GET'
      ? {diagnosticCode: preflightErrorCode(e), httpStatus: responseStatus} : {}), safeCode: e.safeCode ?? 'NETWORK_ERROR',
      retryable: e.retryable ?? e.code === 'ECONNREFUSED'}));
    req.on('close', () => {clearTimeout(connectTimer); clearTimeout(deadlineTimer);});
    if (payload) req.write(payload);
    req.end();
  });
}

export async function preflight(bundle, models, key, send = transport, requiredOutputTokens = 512, recordDiagnostic = () => {}) {
  const verified = [];
  for (const model of models) {
    const started = performance.now();
    let response;
    try {
      response = await send({url: `${bundle.baseUrl}/${model}`, method: 'GET', key,
        timing: bundle.transport, deadlineMs: bundle.transport.extensionTimeoutMs});
    } catch (error) {
      const beforeTransport = error?.beforeTransport === true;
      const errorCode = preflightErrorCode(error);
      const httpStatus = Number.isInteger(error?.httpStatus) && error.httpStatus >= 100 && error.httpStatus <= 599
        ? error.httpStatus : null;
      recordDiagnostic({model, stage: beforeTransport
        ? errorCode === 'PERSISTENCE_FAILED' ? 'preflight_persistence' : 'preflight_guard' : 'preflight_transport',
        durationMs: performance.now() - started, httpStatus, errorCode,
        beforeTransport, incompatibleResponse: false});
      throw Error('Model unavailable during preflight; inspect sanitized diagnostics.');
    }
    const status = Number.isInteger(response?.status) && response.status >= 100 && response.status <= 599
      ? response.status : null;
    const body = response?.body;
    let errorCode = null;
    if (status !== 200) errorCode = 'HTTP_ERROR';
    else if (body === null) errorCode = 'RESPONSE_INVALID_JSON';
    else if (!body || typeof body !== 'object' || body.name !== `models/${model}`
        || !Array.isArray(body.supportedGenerationMethods)
        || !Number.isFinite(body.outputTokenLimit)) errorCode = 'RESPONSE_STRUCTURE_INCOMPATIBLE';
    else if (!body.supportedGenerationMethods.includes('generateContent')
        || body.outputTokenLimit < requiredOutputTokens) errorCode = 'MODEL_CAPABILITY_INCOMPATIBLE';
    recordDiagnostic({model, stage: errorCode === 'HTTP_ERROR' ? 'preflight_http'
      : errorCode ? 'preflight_compatibility' : 'preflight_complete',
      durationMs: performance.now() - started, httpStatus: status, errorCode,
      beforeTransport: false, incompatibleResponse: status === 200 && errorCode !== null});
    if (errorCode) throw Error('Model unavailable or incompatible on v1. No fallback or generation attempted.');
    verified.push({name: response.body.name, version: response.body.version ?? null,
      supportedGenerationMethods: response.body.supportedGenerationMethods,
      outputTokenLimit: response.body.outputTokenLimit, inputTokenLimit: response.body.inputTokenLimit ?? null});
  }
  return verified;
}

export async function generate(bundle, scenario, configurationId, key, send = transport, sleep = ms => new Promise(r => setTimeout(r, ms)), options = {}) {
  const {model, thinkingLevel} = profileFor(configurationId);
  const body = requestBody(scenario, configurationId, options.maxOutputTokens ?? 512);
  const period = options.period ?? 'promo';
  const start = performance.now();
  const attempts = [];
  let response;
  for (let i = 0; i < bundle.transport.maxAttempts; i++) {
    const remaining = bundle.transport.extensionTimeoutMs - (performance.now() - start);
    if (remaining <= 0) break;
    const attemptStart = performance.now();
    let retryable = false;
    try {
      response = await send({url: `${bundle.baseUrl}/${model}:generateContent`, method: 'POST',
        body, key, timing: bundle.transport, deadlineMs: remaining});
      attempts.push({status: response.status, durationMs: performance.now() - attemptStart,
        ...usageAndCost(response.body?.usageMetadata, model, period)});
      if (response.status >= 200 && response.status < 300) break;
      retryable = transient.has(response.status);
    } catch (e) {
      response = undefined;
      // Do not propagate raw errors/URLs/response bodies, even from a custom transport.
      const allowedCodes = ['CONNECT_TIMEOUT', 'READ_TIMEOUT', 'INTERACTIVE_DEADLINE', 'READ_FAILED', 'NETWORK_ERROR', 'CALL_LIMIT', 'BUDGET_LIMIT'];
      attempts.push({error: allowedCodes.includes(e.safeCode) ? e.safeCode : 'NETWORK_ERROR',
        durationMs: performance.now() - attemptStart, ...usageAndCost(null, model)});
      retryable = e.retryable === true;
    }
    if (!retryable || i + 1 >= bundle.transport.maxAttempts) break;
    // Default initial delay 500ms; interactive clamp 250ms. Positive Retry-After also clamps to 250ms.
    await sleep(bundle.transport.retryDelayMs);
  }
  const candidate = response?.body?.candidates?.[0];
  // Matches current GeminiService: first text part, then SmartReplyService trim and length validation.
  const raw = candidate?.content?.parts?.[0]?.text;
  const output = typeof raw === 'string' ? raw.trim() : '';
  const providerVisibleOutput = (candidate?.content?.parts ?? []).filter(p => !p.thought && typeof p.text === 'string').map(p => p.text).join('').trim();
  const parserMismatch = output !== providerVisibleOutput;
  const incomplete = response?.status >= 200 && response?.status < 300 && candidate?.finishReason !== 'STOP';
  const timeout = attempts.some(a => ['CONNECT_TIMEOUT', 'READ_TIMEOUT', 'INTERACTIVE_DEADLINE'].includes(a.error));
  const checks = deterministicChecks(output, candidate?.finishReason, scenario.checks);
  const latencyMs = performance.now() - start;
  const knownCost = attempts.reduce((sum, a) => sum + (a.estimatedUsd ?? 0), 0);
  const unknownCostAttempts = attempts.filter(a => a.estimatedUsd === null).length;
  const success = response?.status >= 200 && response?.status < 300 && checks.nonempty && checks.withinCharacterLimit && latencyMs < bundle.transport.extensionTimeoutMs;
  return {caseId: scenario.id, model, configurationId, thinkingLevel, period, generationConfig: body.generationConfig, profile: scenario.profile, contextMode: scenario.contextMode,
    output, providerVisibleOutput, parserMismatch, incomplete, timeout, promptBlockReason: response?.body?.promptFeedback?.blockReason ?? null, finishReason: candidate?.finishReason ?? null, modelVersion: response?.body?.modelVersion ?? null,
    success, latencyMs, withinExtensionTimeout: latencyMs < bundle.transport.extensionTimeoutMs,
    attempts, knownEstimatedUsd: knownCost, unknownCostAttempts,
    estimatedUsd: unknownCostAttempts ? null : knownCost,
    checks, deterministicPass: success && !parserMismatch && Object.values(checks).every(Boolean),
    review: scenario.review, humanScores: null};
}

export function summary(rows) {
  return [...new Set(rows.map(r => r.configurationId ?? r.model))].map(configurationId => {
    const data = rows.filter(r => (r.configurationId ?? r.model) === configurationId);
    const successes = data.filter(r => r.success);
    const sorted = data.map(r => r.latencyMs).sort((a, b) => a - b);
    const successfulSorted = successes.map(r => r.latencyMs).sort((a, b) => a - b);
    const percentile = (values, p) => values.length ? values[Math.ceil(p * values.length) - 1] : null;
    const attempts = data.flatMap(r => r.attempts);
    const knownEstimatedUsd = data.reduce((sum, r) => sum + r.knownEstimatedUsd, 0);
    const unknownCostAttempts = data.reduce((sum, r) => sum + r.unknownCostAttempts, 0);
    const mean = key => attempts.length && attempts.every(a => Number.isFinite(a[key]))
      ? attempts.reduce((s, a) => s + a[key], 0) / data.length : null;
    const completeCost = !unknownCostAttempts;
    const input = mean('inputTokens'), visible = mean('outputTokens'), thoughts = mean('thinkingTokens');
    const model = data[0].model;
    const reviewed = data.filter(r => ['ready', 'adjust', 'generic', 'incorrect'].includes(r.humanScores?.category));
    return {configurationId, model, responses: data.length, successful: successes.length,
      usableComplete: data.filter(r => r.success && !r.incomplete && !r.parserMismatch).length,
      deterministicPasses: data.filter(r => r.deterministicPass).length,
      errorRate: data.filter(r => !r.success).length / data.length,
      timeoutRate: data.filter(r => r.timeout).length / data.length,
      incompleteRate: data.filter(r => r.incomplete).length / data.length,
      maxTokensRate: data.filter(r => r.finishReason === 'MAX_TOKENS').length / data.length,
      parserMismatchRate: data.filter(r => r.parserMismatch).length / data.length,
      meanMs: sorted.reduce((s, n) => s + n, 0) / sorted.length,
      medianMs: sorted.length % 2 ? sorted[(sorted.length - 1) / 2] : (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2,
      p95Ms: percentile(sorted, 0.95), p50SuccessfulMs: percentile(successfulSorted, 0.50), p95SuccessfulMs: percentile(successfulSorted, 0.95),
      attempts: attempts.length, knownEstimatedUsd, unknownCostAttempts,
      meanInputTokensPerSuggestion: input, meanVisibleTokensPerSuggestion: visible, meanThinkingTokensPerSuggestion: thoughts, meanBilledOutputTokensPerSuggestion: mean('billedOutputTokens'),
      meanUsdPerSuggestion: completeCost ? knownEstimatedUsd / data.length : null,
      projectedCosts: completeCost && input !== null && visible !== null && thoughts !== null
        ? projectCost(input - 0.9 * (mean('cachedTokens') ?? 0), visible, thoughts, model) : null,
      humanReviewPending: reviewed.length !== data.length, humanReviewed: reviewed.length,
      humanCategories: Object.fromEntries(['ready', 'adjust', 'generic', 'incorrect'].map(category => [category, reviewed.filter(r => r.humanScores.category === category).length])),
      criticalFailures: reviewed.filter(r => r.humanScores.critical === true).length,
      costPerReadyResponse: completeCost && reviewed.length === data.length && reviewed.some(r => r.humanScores.category === 'ready')
        ? knownEstimatedUsd / reviewed.filter(r => r.humanScores.category === 'ready').length : null};
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main(process.argv.slice(2), {validateBundle, authorizePaid, transport, preflight, generate, summary}).catch(() => {
    console.error('Evaluation stopped. Check offline export, explicit authorization, environment key, model availability and unique output path. No credentials or external error bodies printed.');
    process.exitCode = 1;
  });
}
