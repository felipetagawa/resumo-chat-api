import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile, rm} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {main} from './runner.mjs';
import {planRun} from './profiles.mjs';
import {validateBundle, authorizePaid, preflight, generate, summary} from './benchmark.mjs';

async function mockRun(response) {
  const bundle = JSON.parse(await readFile('target/smart-reply-eval/requests.json', 'utf8'));
  const plan = planRun(bundle, {sample: 3});
  const output = `target/smart-reply-eval/mock-${randomUUID()}.json`;
  const saved = Object.fromEntries(['GEMINI_API_KEY', 'SMART_REPLY_EVAL_AUTHORIZED', 'SMART_REPLY_EVAL_APPROVED_PLAN'].map(k => [k, process.env[k]]));
  Object.assign(process.env, {GEMINI_API_KEY: 'offline-dummy', SMART_REPLY_EVAL_AUTHORIZED: 'YES', SMART_REPLY_EVAL_APPROVED_PLAN: plan.approvalSha256});
  const requests = [];
  const transport = async req => {
    requests.push(req);
    assert.equal(req.key, 'offline-dummy');
    if (req.method === 'GET') return {status: 200, body: {name: `models/${req.url.split('/').at(-1)}`,
      outputTokenLimit: 65536, supportedGenerationMethods: ['generateContent']}};
    return response;
  };
  let error;
  try {
    await main(['--paid', '--sample', '3', '--budget-usd', '3', '--max-calls', '34', '--output', output],
      {validateBundle, authorizePaid, preflight, generate, summary, transport});
  } catch (e) { error = e; }
  finally {
    for (const [key, value] of Object.entries(saved)) {
      if (value === undefined) delete process.env[key]; else process.env[key] = value;
    }
  }
  try {return {error, requests, report: JSON.parse(await readFile(output, 'utf8'))};}
  finally {await rm(output);}
}
test('complete authorized path uses only injected mocks and persists limits, costs and five groups', async () => {
  const result = await mockRun({status: 200, body: {candidates: [{finishReason: 'STOP', content: {parts: [{text: 'Pode informar o erro?'}]}}],
    usageMetadata: {promptTokenCount: 1000, candidatesTokenCount: 100, thoughtsTokenCount: 40, totalTokenCount: 1140}}});
  assert.equal(result.error, undefined);
  assert.equal(result.report.complete, true);
  assert.equal(result.requests.length, 19); // 4 unique model GETs + 15 generations
  assert.equal(result.report.httpCalls, 19);
  assert.ok(result.report.issuedReservationUsd <= result.report.plan.reservedUsd);
  assert.equal(result.report.results.length, 15);
  assert.equal(result.report.summary.length, 5);
  assert.ok(!JSON.stringify(result.report).includes('offline-dummy'));
  assert.ok(!JSON.stringify(result.report).includes('DADOS DO ATENDIMENTO'));
  assert.deepEqual(result.report.plan.caseIds, ['03-established', '16-no-technical-evidence', '27-long-bounded']);
});
test('quota exhausted stops the run after two attempts without additional profiles', async () => {
  const result = await mockRun({status: 429});
  assert.ok(result.error);
  assert.equal(result.report.complete, false);
  assert.equal(result.requests.length, 6);
  assert.equal(result.report.results.length, 1);
  assert.equal(result.report.results[0].unknownCostAttempts, 2);
});
test('unexpected reasoning/output consumption stops subsequent requests and records paid usage', async () => {
  const result = await mockRun({status: 200, body: {candidates: [{finishReason: 'STOP', content: {parts: [{text: 'Resposta'}]}}],
    usageMetadata: {promptTokenCount: 1000, candidatesTokenCount: 100, thoughtsTokenCount: 600, totalTokenCount: 1700}}});
  assert.ok(result.error);
  assert.equal(result.report.stopReason, 'USAGE_BOUND_EXCEEDED');
  assert.equal(result.requests.length, 5);
  assert.ok(result.report.results[0].knownEstimatedUsd > 0);
});
