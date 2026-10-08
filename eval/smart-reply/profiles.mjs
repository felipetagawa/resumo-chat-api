import {createHash} from 'node:crypto';

// Official text Standard pricing, checked 2026-10-08. No tools/cache/batch.
export const pricing = {
  'gemini-2.5-flash-lite': {promo: {input: 0.10, output: 0.40}, standard: {input: 0.10, output: 0.40}},
  'gemini-3.5-flash-lite': {promo: {input: 0.30, output: 2.50}, standard: {input: 0.30, output: 2.50}},
  'gemini-3.7-flash': {promo: {input: 0.75, output: 3.75}, standard: {input: 1.50, output: 7.50}},
  'gemini-3.8-flash': {promo: {input: 0.75, output: 3.75}, standard: {input: 1.50, output: 7.50}},
};
export const profiles = {
  'gemini-2.5-flash-lite': {model: 'gemini-2.5-flash-lite', thinkingLevel: null, apiVersion: 'v1'},
  'gemini-3.5-flash-lite-minimal': {model: 'gemini-3.5-flash-lite', thinkingLevel: 'MINIMAL', apiVersion: 'v1'},
  'gemini-3.7-flash-low': {model: 'gemini-3.7-flash', thinkingLevel: 'LOW', apiVersion: 'v1'},
  'gemini-3.8-flash-low': {model: 'gemini-3.8-flash', thinkingLevel: 'LOW', apiVersion: 'v1'},
  'gemini-3.8-flash-medium': {model: 'gemini-3.8-flash', thinkingLevel: 'MEDIUM', apiVersion: 'v1'},
};
export function profileFor(id) {
  if (!Object.hasOwn(profiles, id)) throw Error('Unknown profile; no implicit thinking or model fallback.');
  return profiles[id];
}
export function requestBody(scenario, id, maxOutputTokens = 512) {
  const profile = profileFor(id);
  if (!profile.thinkingLevel && maxOutputTokens === 512) return scenario.body;
  return {...scenario.body, generationConfig: profile.thinkingLevel
    ? {maxOutputTokens, thinkingConfig: {thinkingLevel: profile.thinkingLevel}}
    : {...scenario.body.generationConfig, maxOutputTokens}};
}
export function projectCost(input, visible, thoughts, model) {
  const result = {};
  for (const [period, rate] of Object.entries(pricing[model])) {
    const perResponse = (input * rate.input + (visible + thoughts) * rate.output) / 1e6;
    result[period] = {perResponse, per100: 100 * perResponse, per1000: 1000 * perResponse,
      workingDaysPerMonth: 22, monthly: [1, 5, 10, 20].flatMap(technicians =>
        [5, 10, 20].map(suggestionsPerDay => ({technicians, suggestionsPerDay,
          usd: suggestionsPerDay * 22 * technicians * perResponse})))};
  }
  return result;
}
export function planRun(bundle, options = {}) {
  const sample = options.sample ?? 30;
  const profileIds = options.profileIds ?? Object.keys(profiles);
  const maxOutputTokens = options.maxOutputTokens ?? 512;
  const budgetUsd = options.budgetUsd ?? 3;
  if (!Number.isInteger(sample) || sample < 1 || sample > bundle.cases.length) throw Error('Invalid sample.');
  if (!profileIds.length || new Set(profileIds).size !== profileIds.length) throw Error('Empty or duplicate profiles.');
  profileIds.forEach(profileFor);
  if (!Number.isInteger(maxOutputTokens) || maxOutputTokens < 512 || maxOutputTokens > 65536
      || (maxOutputTokens !== 512 && !options.diagnostic)) throw Error('Different output limits require a separate diagnostic experiment.');
  if (!Number.isFinite(budgetUsd) || budgetUsd <= 0 || budgetUsd > 3) throw Error('Budget must be positive and at most USD 3.');
  // Initial three: established conversation, missing evidence, long bounded input.
  // Larger runs spread across the same original sequence. Every profile shares selection.
  const cases = sample === 3 ? [2, 15, 26].map(i => bundle.cases[i])
    : sample === 1 ? [bundle.cases[0]] : Array.from({length: sample}, (_, i) =>
      bundle.cases[Math.round(i * (bundle.cases.length - 1) / (sample - 1))]);
  const period = options.period ?? (new Date().toISOString().slice(0, 10) < '2027-01-01' ? 'promo' : 'standard');
  if (!['promo', 'standard'].includes(period)) throw Error('Unknown pricing period.');
  const reservations = cases.flatMap(c => profileIds.map(id => {
    const model = profileFor(id).model;
    // Conservative local bound: one text token per UTF-8 byte (not chars/4).
    // Reserve 512 additional input tokens for API framing; reject an overrun later.
    const inputTokenBound = Buffer.byteLength(c.body.contents[0].parts[0].text, 'utf8') + 512;
    const rate = pricing[model][period];
    const perAttemptUsd = (inputTokenBound * rate.input + maxOutputTokens * rate.output) / 1e6;
    return {caseId: c.id, profileId: id, inputTokenBound, perAttemptUsd};
  }));
  const reservedUsd = reservations.reduce((sum, r) => sum + r.perAttemptUsd * bundle.transport.maxAttempts, 0);
  if (reservedUsd > budgetUsd) throw Error('Estimate exceeds budget; reduce sample/profiles or diagnostic limit.');
  const logicalGenerations = sample * profileIds.length;
  const metadataCalls = new Set(profileIds.map(id => profileFor(id).model)).size;
  const plan = {schemaVersion: 2, experiment: options.diagnostic ? 'diagnostic' : 'main',
    caseIds: cases.map(c => c.id), profileIds, profiles: Object.fromEntries(profileIds.map(id => [id, profiles[id]])),
    requestsSha256: bundle.requestsSha256 ?? null,
    selectedBodiesSha256: createHash('sha256').update(JSON.stringify(cases.map(c => c.body))).digest('hex'),
    transport: bundle.transport, maxOutputTokens, period, pricing, budgetUsd, reservedUsd,
    logicalGenerations, maxGenerationCalls: logicalGenerations * bundle.transport.maxAttempts,
    metadataCalls, maxHttpCalls: logicalGenerations * bundle.transport.maxAttempts + metadataCalls,
    reservations, estimateAssumptions: 'Text only; UTF-8 bytes + 512 input framing; output cap includes thinking; every retry reserved; no tools, cache, tax or FX. Unknown usage keeps full reservation.'};
  return {...plan, approvalSha256: createHash('sha256').update(JSON.stringify(plan)).digest('hex')};
}
export function validatePlanApproval(plan, env, {maxCalls, budgetUsd}) {
  if (env.SMART_REPLY_EVAL_AUTHORIZED !== 'YES' || env.SMART_REPLY_EVAL_APPROVED_PLAN !== plan.approvalSha256
      || maxCalls !== plan.maxHttpCalls || budgetUsd !== plan.budgetUsd) {
    throw Error('Explicit approval must match the exact dry-run plan, cost and maximum HTTP calls.');
  }
  const currentPeriod = new Date().toISOString().slice(0, 10) < '2027-01-01' ? 'promo' : 'standard';
  if (plan.period !== currentPeriod) throw Error('Paid run cannot use an expired or future pricing period.');
}
