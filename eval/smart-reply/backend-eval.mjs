import * as benchmark from './benchmark.mjs';
import {main} from './runner.mjs';
import {pathToFileURL} from 'node:url';

// Preserve the historical benchmark. This adapter evaluates the new backend's visible-parts parser.
export async function generate(bundle, scenario, id, key, send, sleep, options={}) {
 const row=await benchmark.generate(bundle,scenario,id,key,send,sleep,options);
 const output=row.providerVisibleOutput;
 const complete=row.finishReason==='STOP';
 const checks=benchmark.deterministicChecks(output,row.finishReason,scenario.checks);
 const success=row.attempts.at(-1)?.status>=200&&row.attempts.at(-1)?.status<300
  &&complete&&checks.nonempty&&checks.withinCharacterLimit&&row.withinExtensionTimeout;
 return {...row,legacyOutput:row.output,legacyParserMismatch:row.parserMismatch,
  evaluator:'backend_visible_parts_v1',output,parserMismatch:false,checks,success,
  deterministicPass:success&&Object.values(checks).every(Boolean)};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href)
 main(process.argv.slice(2),{...benchmark,generate}).catch(()=>{
  console.error('Backend evaluation stopped; verify export and explicit approval.');process.exitCode=1;
 });
