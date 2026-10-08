import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {authorizePaid, deterministicChecks, usageAndCost, generate, preflight, validateBundle, summary} from './benchmark.mjs';

const bundle = {transport: {connectTimeoutMs: 1500, readTimeoutMs: 4000, maxAttempts: 2,
  retryDelayMs: 250, extensionTimeoutMs: 15000}, baseUrl: 'https://generativelanguage.googleapis.com/v1/models'};
const scenario = {id: 'synthetic', profile: 'DIRECT', contextMode: 'RECENT',
  body: {contents: [{role: 'user', parts: [{text: 'synthetic'}]}], generationConfig: {temperature: 0.3, maxOutputTokens: 512}},
  checks: {noGreeting: true, forbidden: ['já corrigi']}, review: 'Review evidence'};
const ok = {status: 200, body: {modelVersion: 'synthetic-version', candidates: [{finishReason: 'STOP',
  content: {parts: [{text: ' Pode informar a mensagem de erro? '}]}}],
  usageMetadata: {promptTokenCount: 1000, candidatesTokenCount: 100, thoughtsTokenCount: 40, totalTokenCount: 1140}}};

test('paid mode requires explicit guards and process-only key', () => {
  for (const [paid, env] of [[false, {}], [true, {GEMINI_API_KEY: 'dummy'}], [true, {SMART_REPLY_EVAL_AUTHORIZED: 'YES'}]]) {
    assert.throws(() => authorizePaid(paid, env, ['gemini-2.5-flash-lite']));
  }
  assert.doesNotThrow(() => authorizePaid(true, {GEMINI_API_KEY: 'dummy', SMART_REPLY_EVAL_AUTHORIZED: 'YES'}, ['gemini-2.5-flash-lite']));
  assert.throws(() => authorizePaid(true, {GEMINI_API_KEY: 'dummy', SMART_REPLY_EVAL_AUTHORIZED: 'YES'}, ['invented-model']));
});

test('checks catch redundant greeting, invented action and truncated answer without blocking first-contact greeting', () => {
  assert.equal(deterministicChecks('Olá, tudo bem? Já corrigi o problema.', 'STOP', scenario.checks).noRedundantGreeting, false);
  assert.equal(deterministicChecks('Já corrigi o problema.', 'STOP', scenario.checks).forbidden0, false);
  assert.ok(Object.values(deterministicChecks('Pode informar a mensagem de erro?', 'STOP', scenario.checks)).every(Boolean));
  assert.equal(deterministicChecks('Olá! Em qual cadastro precisa de ajuda?', 'STOP', {noGreeting: false}).nonempty, true);
  assert.equal(deterministicChecks('Resposta parcial', 'MAX_TOKENS').complete, false);
  assert.equal(deterministicChecks('   ', 'STOP').nonempty, false);
});

test('cost includes thinking; missing usage is unknown rather than free', () => {
  const cost = usageAndCost(ok.body.usageMetadata, 'gemini-3.5-flash-lite');
  assert.equal(cost.estimatedUsd, (1000 * 0.30 + 140 * 2.50) / 1e6);
  assert.equal(cost.outputTokens, 100);
  assert.equal(cost.thinkingTokens, 40);
  assert.equal(usageAndCost(null, 'gemini-3.5-flash-lite').estimatedUsd, null);
});

test('normal request uses exact exported body once and records metrics', async () => {
  let calls = 0;
  const row = await generate(bundle, scenario, 'gemini-2.5-flash-lite', 'dummy', async request => {
    calls++;
    assert.strictEqual(request.body, scenario.body);
    assert.equal(request.method, 'POST');
    assert.equal(request.timing.readTimeoutMs, 4000);
    assert.ok(!request.url.includes('dummy'));
    return ok;
  });
  assert.equal(calls, 1);
  assert.equal(row.output, 'Pode informar a mensagem de erro?');
  assert.equal(row.deterministicPass, true);
  assert.equal(row.attempts[0].inputTokens, 1000);
  assert.equal(row.unknownCostAttempts, 0);
  assert.equal(summary([row])[0].successful, 1);
});

test('transient failure retries once, keeps identical request and marks unknown attempt cost', async () => {
  let calls = 0;
  const delays = [];
  const row = await generate(bundle, scenario, 'gemini-3.5-flash-lite-minimal', 'dummy', async () => ++calls === 1 ? {status: 503} : ok,
    async ms => delays.push(ms));
  assert.equal(calls, 2);
  assert.deepEqual(delays, [250]);
  assert.equal(row.success, true);
  assert.equal(row.unknownCostAttempts, 1);
  assert.equal(row.estimatedUsd, null);
  assert.ok(row.knownEstimatedUsd > 0);
});

test('nontransient failure, bad draft and truncation do not trigger paid quality retries', async () => {
  for (const response of [{status: 400}, {status: 200, body: {candidates: [{content: {parts: [{text: ''}]}}]}},
    {...ok, body: {...ok.body, candidates: [{finishReason: 'MAX_TOKENS', content: {parts: [{text: 'Partial'}]}}]}}]) {
    let calls = 0;
    const row = await generate(bundle, scenario, 'gemini-3.5-flash-lite-minimal', 'dummy', async () => {calls++; return response;});
    assert.equal(calls, 1);
    assert.equal(row.deterministicPass, false);
  }
});

test('timeout retry is capped at two and raw errors cannot leak credentials', async () => {
  let calls = 0;
  const row = await generate(bundle, scenario, 'gemini-3.5-flash-lite-minimal', 'dummy', async () => {
    calls++; throw {safeCode: 'dummy-secret', message: 'dummy-secret', retryable: true};
  }, async () => {});
  assert.equal(calls, 2);
  assert.equal(row.success, false);
  assert.ok(!JSON.stringify(row).includes('dummy-secret'));
});

test('preflight rejects unavailable models on v1 without generating or changing endpoint', async () => {
  const calls = [];
  await assert.rejects(preflight(bundle, ['gemini-3.5-flash-lite'], 'dummy', async request => {
    calls.push(request); return {status: 404};
  }), /unavailable/);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].method, 'GET');
  assert.equal(calls[0].url, 'https://generativelanguage.googleapis.com/v1/models/gemini-3.5-flash-lite');
});

test('preflight confirms exact name, generateContent and output capability', async () => {
  const valid = {status: 200, body: {name: 'models/gemini-3.5-flash-lite', outputTokenLimit: 65536,
    supportedGenerationMethods: ['generateContent']}};
  assert.equal((await preflight(bundle, ['gemini-3.5-flash-lite'], 'dummy', async () => valid)).length, 1);
  for (const body of [{...valid.body, supportedGenerationMethods: ['embedContent']}, {...valid.body, outputTokenLimit: 256},
    {...valid.body, name: 'models/other'}]) {
    await assert.rejects(preflight(bundle, ['gemini-3.5-flash-lite'], 'dummy', async () => ({status: 200, body})), /incompatible/);
  }
});

test('actual Java export validates and CLI default remains offline even with authorization env', async () => {
  const data = JSON.parse(await readFile('target/smart-reply-eval/requests.json', 'utf8'));
  validateBundle(data);
  const child = spawnSync(process.execPath, ['eval/smart-reply/benchmark.mjs'], {
    encoding: 'utf8', env: {...process.env, GEMINI_API_KEY: 'dummy', SMART_REPLY_EVAL_AUTHORIZED: 'YES'}});
  assert.equal(child.status, 0, child.stderr);
  const output = JSON.parse(child.stdout);
  assert.equal(output.mode, 'offline');
  assert.equal(output.logicalGenerationsIfAuthorized, 150);
  assert.ok(!child.stdout.includes('dummy'));
});

test('bundle rejects extra factual fields and altered generation limits', async () => {
  const data = JSON.parse(await readFile('target/smart-reply-eval/requests.json', 'utf8'));
  const changed = structuredClone(data);
  changed.cases[0].body.generationConfig.maxOutputTokens = 2048;
  assert.throws(() => validateBundle(changed), /Generation/);
  const changedFacts = structuredClone(data);
  const p = changedFacts.cases[0].body.contents[0].parts[0];
  const marker = '\n\nDADOS DO ATENDIMENTO (JSON):\n';
  const index = p.text.indexOf(marker);
  p.text = p.text.slice(0, index + marker.length) + JSON.stringify({conversation: 'x', promptComplement: '', privateNote: 'secret'});
  assert.throws(() => validateBundle(changedFacts), /factual/);
});

test('Gemini 3 configuration and parser limitations are observable without altering prompt', async () => {
  for (const id of ['gemini-3.5-flash-lite-minimal', 'gemini-3.7-flash-low', 'gemini-3.8-flash-low', 'gemini-3.8-flash-medium']) {
    const row = await generate(bundle, scenario, id, 'dummy', async req => {
      assert.deepEqual(req.body.contents, scenario.body.contents);
      assert.equal(req.body.generationConfig.temperature, undefined);
      assert.equal(req.body.generationConfig.maxOutputTokens, 512);
      assert.match(req.url, /v1\/models\/gemini-3/);
      return {...ok, body: {...ok.body, candidates: [{finishReason: 'STOP', content: {parts: [{thought: true, text: 'internal'}, {text: 'Actual answer'}]}}]}};
    });
    assert.equal(row.output, 'internal'); // exact existing backend extraction
    assert.equal(row.providerVisibleOutput, 'Actual answer');
    assert.equal(row.parserMismatch, true);
  }
});
test('unknown reasoning is never treated as zero; totals can reconcile omitted reasoning', () => {
  assert.equal(usageAndCost({promptTokenCount: 100, candidatesTokenCount: 10}, 'gemini-3.8-flash').estimatedUsd, null);
  assert.equal(usageAndCost({promptTokenCount: 100, candidatesTokenCount: 10, totalTokenCount: 150}, 'gemini-3.8-flash').thinkingTokens, 40);
  assert.equal(usageAndCost({promptTokenCount: 100, candidatesTokenCount: 10, thoughtsTokenCount: -1}, 'gemini-3.8-flash').estimatedUsd, null);
  const promo = usageAndCost(ok.body.usageMetadata, 'gemini-3.8-flash', 'promo');
  assert.equal(usageAndCost(ok.body.usageMetadata, 'gemini-3.8-flash', 'standard').estimatedUsd, 2 * promo.estimatedUsd);
});
test('empty token-limit output, blocked prompts, timeouts and errors remain distinct', async () => {
  const truncated = await generate(bundle, scenario, 'gemini-3.8-flash-medium', 'dummy', async () => ({status: 200,
    body: {usageMetadata: {promptTokenCount: 100, candidatesTokenCount: 0, thoughtsTokenCount: 512, totalTokenCount: 612},
      candidates: [{finishReason: 'MAX_TOKENS', content: {parts: []}}]}}));
  assert.equal(truncated.incomplete, true);
  assert.equal(truncated.success, false);
  assert.ok(truncated.estimatedUsd > 0);
  const timeout = await generate(bundle, scenario, 'gemini-3.8-flash-low', 'dummy', async () => {throw {safeCode: 'READ_TIMEOUT', retryable: true};}, async () => {});
  assert.equal(timeout.timeout, true);
  assert.equal(timeout.attempts.length, 2);
  const grouped = summary([truncated, {...truncated, configurationId: 'gemini-3.8-flash-low'}]);
  assert.equal(grouped.length, 2);
  assert.equal(grouped[0].incompleteRate, 1);
  const blocked = await generate(bundle, scenario, 'gemini-3.8-flash-low', 'dummy', async () => ({status: 200, body: {promptFeedback: {blockReason: 'SAFETY'}}}));
  assert.equal(blocked.promptBlockReason, 'SAFETY');
  assert.equal(blocked.success, false);
});
test('CLI rejects conflicting/duplicate flags and paid runs without exact plan before output/network', () => {
  for (const args of [['--paid', '--dry-run'], ['--sample', '3', '--sample', '3'], ['--paid'],
    ['--paid', '--sample', '3', '--budget-usd', '3', '--max-calls', '34', '--output', 'target/smart-reply-eval/not-approved.json']]) {
    const child = spawnSync(process.execPath, ['eval/smart-reply/benchmark.mjs', ...args], {encoding: 'utf8',
      env: {...process.env, GEMINI_API_KEY: 'dummy', SMART_REPLY_EVAL_AUTHORIZED: 'YES', SMART_REPLY_EVAL_APPROVED_PLAN: ''}});
    assert.equal(child.status, 1);
    assert.ok(!child.stderr.includes('dummy'));
  }
});

test('diagnostic output capability is checked before generation', async () => {
  await assert.rejects(preflight(bundle, ['gemini-3.8-flash'], 'dummy', async () => ({status: 200,
    body: {name: 'models/gemini-3.8-flash', outputTokenLimit: 1024, supportedGenerationMethods: ['generateContent']}}), 2048), /incompatible/);
});
test('cache discounts and human utility remain separate from deterministic checks', async () => {
  const full = usageAndCost(ok.body.usageMetadata, 'gemini-3.8-flash');
  const cached = usageAndCost({...ok.body.usageMetadata, cachedContentTokenCount: 500}, 'gemini-3.8-flash');
  assert.ok(cached.estimatedUsd < full.estimatedUsd);
  const row = await generate(bundle, scenario, 'gemini-3.8-flash-low', 'dummy', async () => ok);
  assert.equal(summary([row])[0].humanReviewPending, true);
  const reviewed = summary([{...row, humanScores: {category: 'ready', critical: false}}])[0];
  assert.equal(reviewed.humanCategories.ready, 1);
  assert.equal(reviewed.costPerReadyResponse, row.estimatedUsd);
  assert.equal(reviewed.humanReviewPending, false);
});
