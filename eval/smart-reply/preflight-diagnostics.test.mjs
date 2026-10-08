import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import https from 'node:https';
import {syncBuiltinESMExports} from 'node:module';
import {EventEmitter} from 'node:events';
import * as benchmark from './benchmark.mjs';
import {main} from './runner.mjs';
import {planRun, validatePlanApproval} from './profiles.mjs';

const model = 'gemini-2.5-flash-lite';
const secret = 'SENSITIVE_SENTINEL';
const bundle = JSON.parse(await readFile('target/smart-reply-eval/requests.json', 'utf8'));
const valid = {name: `models/${model}`, supportedGenerationMethods: ['generateContent'], outputTokenLimit: 65536};

for (const [failure, code] of [
  [{safeCode: 'CONNECT_TIMEOUT'}, 'CONNECT_TIMEOUT'],
  [{cause: {code: 'ENOTFOUND'}}, 'DNS_FAILED'],
  [{code: 'CERT_HAS_EXPIRED'}, 'TLS_FAILED'],
  [{code: 'ECONNREFUSED'}, 'CONNECTION_FAILED'],
  [{safeCode: secret, code: secret, message: secret, stack: secret}, 'NETWORK_ERROR'],
  [null, 'NETWORK_ERROR'],
]) test(`preflight records sanitized ${code} transport failure`, async () => {
  const rows = [];
  await assert.rejects(benchmark.preflight(bundle, [model], secret, async () => {throw failure;}, 512, row => rows.push(row)));
  assert.equal(rows.length, 1);
  assert.equal(rows[0].model, model);
  assert.equal(rows[0].stage, 'preflight_transport');
  assert.equal(rows[0].errorCode, code);
  assert.equal(rows[0].httpStatus, null);
  assert.equal(rows[0].beforeTransport, false);
  assert.equal(rows[0].incompatibleResponse, false);
  assert.ok(rows[0].durationMs >= 0);
  assert.ok(!JSON.stringify(rows).includes(secret));
});

for (const status of [401, 403, 404, 429, 503]) test(`preflight records HTTP ${status} without retry or generation`, async () => {
  const rows = []; let calls = 0;
  await assert.rejects(benchmark.preflight(bundle, [model, 'gemini-3.8-flash'], secret, async request => {
    calls++; assert.equal(request.method, 'GET');
    return {status, body: {error: secret}, headers: {secret}};
  }, 512, row => rows.push(row)));
  assert.equal(calls, 1);
  assert.equal(rows[0].httpStatus, status);
  assert.equal(rows[0].errorCode, 'HTTP_ERROR');
  assert.equal(rows[0].stage, 'preflight_http');
  assert.equal(rows[0].incompatibleResponse, false);
  assert.ok(!JSON.stringify(rows).includes(secret));
});

for (const [body, code] of [[null, 'RESPONSE_INVALID_JSON'], [{}, 'RESPONSE_STRUCTURE_INCOMPATIBLE'],
  [{...valid, supportedGenerationMethods: ['embedContent']}, 'MODEL_CAPABILITY_INCOMPATIBLE'],
  [{...valid, outputTokenLimit: 256}, 'MODEL_CAPABILITY_INCOMPATIBLE'],
  [{...valid, supportedGenerationMethods: 'generateContent'}, 'RESPONSE_STRUCTURE_INCOMPATIBLE']])
test(`preflight distinguishes ${code}`, async () => {
  const rows = [];
  await assert.rejects(benchmark.preflight(bundle, [model], secret, async () => ({status: 200, body}), 512, row => rows.push(row)));
  assert.equal(rows[0].errorCode, code);
  assert.equal(rows[0].httpStatus, 200);
  assert.equal(rows[0].stage, 'preflight_compatibility');
  assert.equal(rows[0].incompatibleResponse, true);
});

test('real transport sanitizes DNS/TLS while preserving generation retry classification; invalid JSON retains HTTP status', async t => {
  for (const [failure, code] of [[{code: 'ENOTFOUND'}, 'DNS_FAILED'], [{code: 'CERT_HAS_EXPIRED'}, 'TLS_FAILED'],
    [{safeCode: 'CONNECT_TIMEOUT', retryable: true}, 'CONNECT_TIMEOUT'], [null, null]]) {
    const request = new EventEmitter();
    request.end = () => queueMicrotask(() => {
      if (failure) request.emit('error', {...failure, message: secret, stack: secret});
      request.emit('close');
    });
    t.mock.method(https, 'request', (_url, _options, onResponse) => {
      if (!failure) {
        const response = new EventEmitter(); response.statusCode = 200; response.headers = {}; response.setEncoding = () => {};
        onResponse(response);
        queueMicrotask(() => {response.emit('data', secret + '{invalid'); response.emit('end');});
      }
      return request;
    });
    syncBuiltinESMExports();
    const rows = [];
    await assert.rejects(benchmark.preflight(bundle, [model], secret, benchmark.transport, 512, row => rows.push(row)));
    assert.equal(rows[0].errorCode, code ?? 'RESPONSE_INVALID_JSON');
    assert.equal(rows[0].httpStatus, failure ? null : 200);
    assert.ok(!JSON.stringify(rows).includes(secret));
    t.mock.restoreAll(); syncBuiltinESMExports();
  }
});

test('runner persists a failure before transport without spending and emits sanitized fallback if disk stays unavailable', async () => {
  const old = {...process.env};
  const plan = planRun(bundle, {sample: 3, profileIds: [model, 'gemini-3.8-flash-low'], budgetUsd: 0.2, period: 'promo'});
  assert.equal(plan.approvalSha256, '3e56c1bf42fd624d8038f5af8e2137dfe9940bcbef9ab03777f72dc4808c67e1');
  Object.assign(process.env, {GEMINI_API_KEY: secret, SMART_REPLY_EVAL_AUTHORIZED: 'YES', SMART_REPLY_EVAL_APPROVED_PLAN: plan.approvalSha256});
  try {
    const args = ['--paid', '--sample', '3', '--profiles', `${model},gemini-3.8-flash-low`,
      '--budget-usd', '0.2', '--max-calls', '14', '--output', 'target/smart-reply-eval/mock-diagnostic.json'];
    const api = {...benchmark, generate: async () => assert.fail('Generation must never be reached')};
    for (const transport of [async () => {throw {safeCode: 'CONNECT_TIMEOUT', message: secret, stack: secret};},
      async () => ({status: 401, body: {error: secret}, headers: {secret}})]) {
      const saved = []; let calls = 0;
      await assert.rejects(main(args, {...api, transport: async request => {
        calls++; assert.equal(request.method, 'GET'); return transport();
      }}, {mkdir: async () => {}, writeFile: async (_file, data) => saved.push(JSON.parse(data))}));
      const report = saved.at(-1);
      assert.equal(calls, 1);
      assert.equal(report.preflightDiagnostics.length, 1);
      assert.equal(report.httpCalls, 1);
      assert.equal(report.issuedReservationUsd, 0);
      assert.deepEqual(report.results, []);
      assert.deepEqual(report.verifiedModels, []);
      assert.equal(report.plan.approvalSha256, plan.approvalSha256);
      assert.ok(!JSON.stringify(report).includes(secret));
    }
    for (const missingGuard of ['SMART_REPLY_EVAL_AUTHORIZED', 'SMART_REPLY_EVAL_APPROVED_PLAN', 'GEMINI_API_KEY']) {
      const guard = process.env[missingGuard]; delete process.env[missingGuard];
      try {
        await assert.rejects(main(args, {...api, transport: async () => assert.fail('Unauthorized transport')}, {
          mkdir: async () => assert.fail('Unauthorized persistence'),
        }));
      } finally {process.env[missingGuard] = guard;}
    }
    const initialLogs = [];
    await assert.rejects(main(args, {...api, transport: async () => assert.fail('Transport after failed exclusive creation')}, {
      mkdir: async () => {}, writeFile: async (_file, _data, options) => {
        assert.equal(options.flag, 'wx'); throw Object.assign(Error(secret), {code: 'EEXIST'});
      }, logDiagnostic: row => initialLogs.push(row),
    }));
    assert.equal(initialLogs[0].preflightDiagnostics[0].beforeTransport, true);
    assert.equal(initialLogs[0].preflightDiagnostics[0].errorCode, 'PERSISTENCE_FAILED');
    assert.ok(!JSON.stringify(initialLogs).includes(secret));
    for (const permanently of [false, true]) {
      const saved = []; const logged = []; let writes = 0; let calls = 0;
      const api = {...benchmark, transport: async () => {calls++; throw Error('Unexpected transport');},
        generate: async () => {assert.fail('Generation must never be reached');}};
      await assert.rejects(main(['--paid', '--sample', '3', '--profiles', `${model},gemini-3.8-flash-low`,
        '--budget-usd', '0.2', '--max-calls', '14', '--output', 'target/smart-reply-eval/mock-diagnostic.json'], api, {
        mkdir: async () => {}, writeFile: async (_file, data) => {
          writes++; if (writes === 2 || (permanently && writes > 2)) throw Object.assign(Error(secret), {code: 'ENOSPC', path: secret});
          saved.push(JSON.parse(data));
        }, logDiagnostic: row => logged.push(row),
      }));
      assert.equal(calls, 0);
      const report = permanently ? logged[0] : saved.at(-1);
      assert.equal(report.preflightDiagnostics[0].errorCode, 'PERSISTENCE_FAILED');
      assert.equal(report.preflightDiagnostics[0].beforeTransport, true);
      assert.equal(report.preflightDiagnostics[0].stage, 'preflight_persistence');
      assert.ok(!JSON.stringify(report).includes(secret));
      if (!permanently) {
        assert.equal(report.httpCalls, 1); // existing conservative counter, not proof of invocation
        assert.equal(report.issuedReservationUsd, 0);
        assert.deepEqual(report.results, []);
        assert.equal(report.plan.approvalSha256, plan.approvalSha256);
      } else assert.equal(report.persistenceDiagnostic.errorCode, 'PERSISTENCE_FAILED');
    }
    for (const env of [{}, {...process.env, SMART_REPLY_EVAL_APPROVED_PLAN: secret}])
      assert.throws(() => validatePlanApproval(plan, env, {maxCalls: 14, budgetUsd: 0.2}));
    assert.throws(() => validatePlanApproval(plan, process.env, {maxCalls: 15, budgetUsd: 0.2}));
    assert.throws(() => validatePlanApproval(plan, process.env, {maxCalls: 14, budgetUsd: 2}));
  } finally {
    for (const key of ['GEMINI_API_KEY', 'SMART_REPLY_EVAL_AUTHORIZED', 'SMART_REPLY_EVAL_APPROVED_PLAN']) {
      if (old[key] === undefined) delete process.env[key]; else process.env[key] = old[key];
    }
  }
});

test('preflight preserves HTTP status during read failure and keeps diagnostics for earlier models', async () => {
  const rows = []; let calls = 0;
  await assert.rejects(benchmark.preflight(bundle, [model, 'gemini-3.8-flash'], secret, async () => {
    if (++calls === 1) return {status: 200, body: valid};
    throw {safeCode: 'READ_FAILED', httpStatus: 200, message: secret};
  }, 512, row => rows.push(row)));
  assert.equal(rows[0].stage, 'preflight_complete');
  assert.equal(rows[0].errorCode, null);
  assert.equal(rows[1].model, 'gemini-3.8-flash');
  assert.equal(rows[1].httpStatus, 200);
  assert.equal(rows[1].errorCode, 'READ_FAILED');
  assert.ok(!JSON.stringify(rows).includes(secret));
});

test('connection timer diagnoses failure at the unchanged 1500ms boundary without retry', async t => {
  const request = new EventEmitter(); let calls = 0;
  request.end = () => {};
  request.destroy = error => {request.emit('error', error); request.emit('close');};
  t.mock.timers.enable({apis: ['setTimeout']});
  t.mock.method(https, 'request', () => {calls++; return request;});
  syncBuiltinESMExports();
  try {
    const rows = [];
    const pending = assert.rejects(benchmark.preflight(bundle, [model], secret, benchmark.transport, 512, row => rows.push(row)));
    t.mock.timers.tick(1499);
    assert.equal(rows.length, 0);
    t.mock.timers.tick(1);
    await pending;
    assert.equal(rows[0].errorCode, 'CONNECT_TIMEOUT');
    assert.equal(rows[0].beforeTransport, false);
    assert.equal(calls, 1);
  } finally {t.mock.restoreAll(); syncBuiltinESMExports();}
});

test('diagnostic metadata does not change the transport errors consumed by generation', async t => {
  try {
    for (const method of ['GET', 'POST']) {
      const request = new EventEmitter();
      request.end = () => queueMicrotask(() => {request.emit('error', {code: 'ECONNREFUSED', message: secret}); request.emit('close');});
      t.mock.method(https, 'request', () => request); syncBuiltinESMExports();
      await assert.rejects(benchmark.transport({url: bundle.baseUrl, method, key: secret,
        timing: bundle.transport, deadlineMs: 15000}), error => {
        assert.equal(error.safeCode, 'NETWORK_ERROR');
        assert.equal(error.retryable, true);
        if (method === 'GET') assert.equal(error.diagnosticCode, 'CONNECTION_FAILED');
        else assert.deepEqual(error, {safeCode: 'NETWORK_ERROR', retryable: true});
        assert.ok(!JSON.stringify(error).includes(secret));
        return true;
      });
      t.mock.restoreAll(); syncBuiltinESMExports();
    }
  } finally {t.mock.restoreAll(); syncBuiltinESMExports();}
});
