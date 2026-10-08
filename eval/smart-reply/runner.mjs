import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import path from 'node:path';
import {performance} from 'node:perf_hooks';
import {profiles, pricing, planRun, profileFor, validatePlanApproval} from './profiles.mjs';

export async function main(args, api, io = {}) {
  const persist = io.writeFile ?? writeFile;
  const createDirectory = io.mkdir ?? mkdir;
  const logDiagnostic = io.logDiagnostic ?? (diagnostic => console.error(JSON.stringify(diagnostic)));
  const valueFlags = new Set(['--bundle', '--profiles', '--output', '--sample', '--max-calls', '--budget-usd', '--max-output-tokens', '--pricing-period']);
  const switchFlags = new Set(['--paid', '--dry-run', '--diagnostic']);
  const values = {};
  const switches = new Set();
  for (let i = 0; i < args.length; i++) {
    const flag = args[i];
    if (Object.hasOwn(values, flag) || switches.has(flag)) throw Error('Duplicate flag.');
    if (switchFlags.has(flag)) switches.add(flag);
    else if (valueFlags.has(flag) && args[i + 1] && !args[i + 1].startsWith('--')) values[flag] = args[++i];
    else throw Error('Unknown flag or missing value.');
  }
  if (switches.has('--paid') && switches.has('--dry-run')) throw Error('Conflicting execution modes.');
  const bytes = await readFile(values['--bundle'] ?? 'target/smart-reply-eval/requests.json');
  const bundle = JSON.parse(bytes);
  api.validateBundle(bundle);
  const options = {sample: values['--sample'] === undefined ? 30 : Number(values['--sample']),
    profileIds: values['--profiles']?.split(',') ?? Object.keys(profiles),
    maxOutputTokens: values['--max-output-tokens'] === undefined ? 512 : Number(values['--max-output-tokens']),
    diagnostic: switches.has('--diagnostic'),
    budgetUsd: values['--budget-usd'] === undefined ? 3 : Number(values['--budget-usd']),
    period: values['--pricing-period']};
  const plan = planRun(bundle, options);
  if (!switches.has('--paid')) {
    console.log(JSON.stringify({mode: 'offline', cases: plan.caseIds.length,
      logicalGenerationsIfAuthorized: plan.logicalGenerations,
      maxHttpGenerationsWithTransientRetries: plan.maxGenerationCalls,
      networkCalls: 0, ...plan}, null, 2));
    return;
  }
  if (!values['--max-calls'] || !values['--budget-usd'] || !values['--output']) throw Error('Paid execution requires explicit calls, budget and unique output.');
  validatePlanApproval(plan, process.env, {maxCalls: Number(values['--max-calls']), budgetUsd: Number(values['--budget-usd'])});
  const models = [...new Set(plan.profileIds.map(id => profileFor(id).model))];
  api.authorizePaid(true, process.env, models);
  const output = values['--output'];
  // Resolve final output within ignored target; prevent accidentally versioning results or replacing sources.
  const targetRoot = path.resolve('target/smart-reply-eval') + path.sep;
  if (!path.resolve(output).startsWith(targetRoot) || !output.endsWith('.json')) throw Error('Results must be JSON within target/smart-reply-eval.');
  const report = {schemaVersion: 2, startedAt: new Date().toISOString(), runtime: process.version,
    bundleSha256: createHash('sha256').update(bytes).digest('hex'), plan, pricingSource: 'https://ai.google.dev/gemini-api/docs/pricing',
    pricingCheckedAt: '2026-10-08', pricing, evaluatorLlmCalls: 0, verifiedModels: [], preflightDiagnostics: [], results: [],
    httpCalls: 0, issuedReservationUsd: 0, complete: false, stopReason: null};
  let perAttemptReservation = 0;
  const send = async request => {
    if (report.httpCalls >= plan.maxHttpCalls) throw {safeCode: 'CALL_LIMIT', retryable: false,
      ...(request.method === 'GET' ? {beforeTransport: true} : {})};
    if (request.method === 'POST') {
      if (report.issuedReservationUsd + perAttemptReservation > plan.budgetUsd) throw {safeCode: 'BUDGET_LIMIT', retryable: false};
      report.issuedReservationUsd += perAttemptReservation;
    }
    report.httpCalls++;
    // Persist before sending, so a killed process does not hide issued requests.
    try {
      await persist(output, JSON.stringify(report, null, 2) + '\n');
    } catch (error) {
      if (request.method === 'GET') throw {safeCode: 'PERSISTENCE_FAILED', beforeTransport: true};
      throw error;
    }
    return api.transport(request);
  };
  const persistenceStarted = performance.now();
  try {
    await createDirectory(path.dirname(output), {recursive: true});
    await persist(output, '{}\n', {flag: 'wx'});
  } catch {
    logDiagnostic({preflightDiagnostics: [{model: models[0], stage: 'preflight_persistence',
      durationMs: performance.now() - persistenceStarted, httpStatus: null,
      errorCode: 'PERSISTENCE_FAILED', beforeTransport: true, incompatibleResponse: false}]});
    throw Error('Preflight persistence failed; no transport invoked.');
  }
  try {
    report.verifiedModels = await api.preflight(bundle, models, process.env.GEMINI_API_KEY, send, plan.maxOutputTokens,
      diagnostic => report.preflightDiagnostics.push(diagnostic));
    for (const [index, caseId] of plan.caseIds.entries()) {
      const scenario = bundle.cases.find(c => c.id === caseId);
      const ordered = [...plan.profileIds.slice(index % plan.profileIds.length), ...plan.profileIds.slice(0, index % plan.profileIds.length)];
      for (const id of ordered) {
        const reservation = plan.reservations.find(r => r.caseId === caseId && r.profileId === id);
        perAttemptReservation = reservation.perAttemptUsd;
        const row = await api.generate(bundle, scenario, id, process.env.GEMINI_API_KEY, send, undefined,
          {maxOutputTokens: plan.maxOutputTokens, period: plan.period});
        report.results.push(row);
        report.summary = api.summary(report.results);
        await persist(output, JSON.stringify(report, null, 2) + '\n');
        console.log(`${caseId}: ${id} success=${row.success} complete=${row.checks.complete} attempts=${row.attempts.length}`);
        const last = row.attempts.at(-1);
        const overrun = row.attempts.some(a => (a.inputTokens ?? 0) > reservation.inputTokenBound
          || (a.billedOutputTokens ?? 0) > plan.maxOutputTokens || (a.estimatedUsd ?? 0) > reservation.perAttemptUsd);
        if (overrun || last?.error === 'BUDGET_LIMIT' || last?.error === 'CALL_LIMIT'
            || (last?.status && last.status !== 200)) {
          report.stopReason = overrun ? 'USAGE_BOUND_EXCEEDED' : 'TRANSPORT_OR_COMPATIBILITY_ERROR';
          throw Error('Further generation stopped; review recorded usage or provider status.');
        }
      }
    }
    report.complete = true;
  } finally {
    report.finishedAt = new Date().toISOString();
    report.summary = api.summary(report.results);
    if (!report.complete && !report.stopReason) report.stopReason = 'PREFLIGHT_OR_EXECUTION_FAILED';
    const started = performance.now();
    try {
      await persist(output, JSON.stringify(report, null, 2) + '\n');
    } catch {
      if (report.results.length === 0) {
        logDiagnostic({preflightDiagnostics: report.preflightDiagnostics,
          persistenceDiagnostic: {stage: 'preflight_report_persistence', durationMs: performance.now() - started,
            errorCode: 'PERSISTENCE_FAILED'}});
      }
      throw Error('Evaluation report persistence failed.');
    }
  }
}
