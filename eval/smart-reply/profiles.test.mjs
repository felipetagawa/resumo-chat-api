import test from 'node:test';
import assert from 'node:assert/strict';
import {profiles, requestBody, planRun, validatePlanApproval, projectCost} from './profiles.mjs';

const scenario = {id: 'synthetic', body: {contents: [{role: 'user', parts: [{text: 'synthetic'}]}], generationConfig: {temperature: 0.3, maxOutputTokens: 512}}};
const bundle = {cases: Array.from({length: 30}, (_, i) => ({...scenario, id: String(i)})), transport: {maxAttempts: 2}};
test('five configurations preserve facts and specialize sampling/thinking without mutating export', () => {
  assert.equal(Object.keys(profiles).length, 5);
  assert.strictEqual(requestBody(scenario, 'gemini-2.5-flash-lite'), scenario.body);
  for (const [id, profile] of Object.entries(profiles).slice(1)) {
    const body = requestBody(scenario, id);
    assert.deepEqual(body.contents, scenario.body.contents);
    assert.deepEqual(body.generationConfig, {maxOutputTokens: 512, thinkingConfig: {thinkingLevel: profile.thinkingLevel}});
    assert.equal(body.generationConfig.temperature, undefined);
  }
  assert.equal(scenario.body.generationConfig.temperature, 0.3);
  assert.throws(() => requestBody(scenario, 'gemini-3.8-flash'), /profile/i);
});
test('same sample across profiles, retries included in maximum; total budget is conservative', () => {
  const plan = planRun(bundle, {sample: 3});
  assert.equal(plan.logicalGenerations, 15);
  assert.equal(plan.maxGenerationCalls, 30);
  assert.equal(plan.metadataCalls, 4);
  assert.equal(plan.maxHttpCalls, 34);
  assert.ok(plan.reservedUsd < 3);
  assert.equal(plan.caseIds.length, 3);
  assert.equal(planRun(bundle, {sample: 30}).maxGenerationCalls, 300);
  assert.throws(() => planRun(bundle, {sample: 0}));
  assert.throws(() => planRun(bundle, {sample: 31}));
  assert.throws(() => planRun(bundle, {sample: 1.5}));
  assert.throws(() => planRun(bundle, {profileIds: ['gemini-3.8-flash-low', 'gemini-3.8-flash-low']}));
  assert.throws(() => planRun(bundle, {maxOutputTokens: 2048}), /diagnostic/i);
  const diagnostic = planRun(bundle, {sample: 3, diagnostic: true, maxOutputTokens: 2048});
  assert.equal(diagnostic.experiment, 'diagnostic');
  assert.notEqual(diagnostic.approvalSha256, plan.approvalSha256);
  assert.throws(() => planRun(bundle, {budgetUsd: 0.00001}));
});
test('approval binds cases, prices, output cap, budget and maximum calls before any network', () => {
  const plan = planRun(bundle, {sample: 3});
  const env = {SMART_REPLY_EVAL_AUTHORIZED: 'YES', SMART_REPLY_EVAL_APPROVED_PLAN: plan.approvalSha256};
  const limits = {maxCalls: 34, budgetUsd: 3};
  assert.doesNotThrow(() => validatePlanApproval(plan, env, limits));
  assert.throws(() => validatePlanApproval(plan, {}, limits));
  assert.throws(() => validatePlanApproval(plan, env, {maxCalls: 33, budgetUsd: 3}));
  assert.throws(() => validatePlanApproval(plan, env, {maxCalls: 34, budgetUsd: 2}));
  assert.throws(() => validatePlanApproval(planRun(bundle, {sample: 30}), env, {maxCalls: 304, budgetUsd: 3}));
});
test('projections explicitly separate promotional from later prices and scale technicians', () => {
  const costs = projectCost(1000, 100, 400, 'gemini-3.8-flash');
  assert.equal(costs.standard.perResponse, 2 * costs.promo.perResponse);
  assert.equal(costs.promo.per1000, 1000 * costs.promo.perResponse);
  assert.equal(costs.promo.monthly.find(p => p.suggestionsPerDay === 20 && p.technicians === 10).usd,
    20 * 22 * 10 * costs.promo.perResponse);
});
