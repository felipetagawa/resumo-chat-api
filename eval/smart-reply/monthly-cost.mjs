import {readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';

// Offline calculator. It never imports transport, reads a key or sends network requests.
export function compareMonthly({monthlyReplies, baselinePerReply, premiumPerReply, otherMonthlyUsd, premiumShare}) {
 for (const x of [monthlyReplies,baselinePerReply,premiumPerReply,otherMonthlyUsd,premiumShare])
  if (!Number.isFinite(x) || x < 0) throw Error('Nonnegative finite inputs required.');
 if (premiumShare > 1) throw Error('Premium share must be within 0..1.');
 const currentUsd = otherMonthlyUsd + monthlyReplies * baselinePerReply;
 const selectiveUsd = otherMonthlyUsd + monthlyReplies * ((1-premiumShare)*baselinePerReply+premiumShare*premiumPerReply);
 const extra = monthlyReplies*(premiumPerReply-baselinePerReply);
 const maximumPremiumShare = extra > 0 ? Math.max(0,Math.min(1,(Math.min(2*currentUsd,4)-currentUsd)/extra)) : 1;
 return {currentUsd,selectiveUsd,ratio:currentUsd > 0 ? selectiveUsd/currentUsd : null,
  withinTwice:selectiveUsd <= 2*currentUsd,belowFour:selectiveUsd < 4,maximumPremiumShare,
  note:'Boundary share is a mathematical ceiling; use headroom. No spending guarantee.'};
}
export function summarizeUsage(records, days) {
 if (!Number.isFinite(days) || days <= 0) throw Error('Positive observation days required.');
 const groups = new Map();
 for (const r of records) {
  if (r.event !== 'gemini_usage') continue;
  if (!['smart_reply','report','ask','docs','classification','file_search'].includes(r.feature)
   || !/^[a-zA-Z0-9._-]{1,100}$/.test(r.model ?? '') || !Number.isInteger(r.attempt) || r.attempt < 1)
   throw Error('Invalid aggregate metadata record.');
  const key=r.feature+':'+r.model;
  const g=groups.get(key) ?? {feature:r.feature,model:r.model,logicalRequests:0,attempts:0,
   unknownCosts:0,cost2026:0,cost2027:0,timeouts:0,errors:0,durationMs:0};
  g.attempts++; if(r.attempt===1)g.logicalRequests++;
  if(r.outcome==='timeout')g.timeouts++; if(r.outcome==='error')g.errors++;
  if(Number.isFinite(r.durationMs))g.durationMs+=r.durationMs;
  if(!Number.isFinite(r.estimatedUsd2026)||!Number.isFinite(r.estimatedUsd2027)
   ||r.estimatedUsd2026<0||r.estimatedUsd2027<0)g.unknownCosts++;
  else {g.cost2026+=r.estimatedUsd2026;g.cost2027+=r.estimatedUsd2027;}
  groups.set(key,g);
 }
 return {hasObservations:groups.size>0,observationDays:days,monthDays:30,
  groups:[...groups.values()].map(g=>({...g,
   perReply2026:g.logicalRequests && !g.unknownCosts?g.cost2026/g.logicalRequests:null,
   perReply2027:g.logicalRequests && !g.unknownCosts?g.cost2027/g.logicalRequests:null,
   projectedReplies:g.logicalRequests*30/days,meanAttemptMs:g.durationMs/g.attempts})),
  note:'Aggregate all instances and complete requests. Reconcile unknown costs and billing before approval.'};
}
export function projectObserved(summary, premiumShare, extraMonthlyUsd) {
 const baseline=summary.groups.find(g=>g.feature==='smart_reply'&&g.model==='gemini-2.5-flash-lite');
 const premium=summary.groups.find(g=>g.feature==='smart_reply'&&g.model==='gemini-3.8-flash');
 const others=summary.groups.filter(g=>g.feature!=='smart_reply');
 if(!baseline || !premium || summary.groups.some(g=>g.unknownCosts || (g.feature==='smart_reply' && !['gemini-2.5-flash-lite','gemini-3.8-flash'].includes(g.model))) || !baseline.logicalRequests || !premium.logicalRequests)
  return {financialEvidenceComplete:false,activationRecommended:false,reason:'Missing baseline/premium measurements or unknown attempt costs.'};
 // Both model samples must represent the same traffic distribution. Combine their volume.
 const monthlyReplies=baseline.projectedReplies+premium.projectedReplies;
 return {financialEvidenceComplete:true,activationRecommended:false,
  reason:'Model in production, representative quality/latency and non-token costs still require review.',
  projections:Object.fromEntries(['2026','2027'].map(period=>[period,compareMonthly({
   monthlyReplies,baselinePerReply:baseline['perReply'+period],premiumPerReply:premium['perReply'+period],
   otherMonthlyUsd:extraMonthlyUsd+others.reduce((sum,g)=>sum+g['cost'+period]*30/summary.observationDays,0),premiumShare})]))};
}
async function main(args) {
 if(!args.length) {
  console.log(JSON.stringify({mode:'hypothetical',networkCalls:0,
   assumptions:{inputTokens:1000,baselineVisibleTokens:100,premiumVisibleTokens:100,premiumThinkingTokens:200,
    attemptsPerReply:1,otherMonthlyUsd:1,premiumShare:1,workingDays:22},
   scenarios:[220,1100,2200].map(monthlyReplies=>({monthlyReplies,
    promo2026:compareMonthly({monthlyReplies,baselinePerReply:.00014,premiumPerReply:.001875,otherMonthlyUsd:1,premiumShare:1}),
    standard2027:compareMonthly({monthlyReplies,baselinePerReply:.00014,premiumPerReply:.00375,otherMonthlyUsd:1,premiumShare:1})})),
   activationRecommended:false},null,2));return;
 }
 const options={};
 for(let i=0;i<args.length;i+=2){
  if(!['--usage','--days','--premium-share','--extra-monthly-usd'].includes(args[i])||args[i+1]===undefined||options[args[i]]!==undefined)
   throw Error('Invalid or duplicate flags.');
  options[args[i]]=args[i+1];
 }
 if(!options['--usage']||!options['--days']||options['--extra-monthly-usd']===undefined)throw Error('Usage, days and explicit extra monthly spending are required.');
 const extra=Number(options['--extra-monthly-usd']),share=Number(options['--premium-share']??0);
 if(!Number.isFinite(extra)||extra<0||!Number.isFinite(share)||share<0||share>1)throw Error('Invalid spending or share.');
 const records=(await readFile(options['--usage'],'utf8')).split(/\r?\n/).filter(line=>line.trim()).map(line=>JSON.parse(line));
 const summary=summarizeUsage(records,Number(options['--days']));
 console.log(JSON.stringify({mode:'observed',networkCalls:0,summary,...projectObserved(summary,share,extra)},null,2));
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href)
 main(process.argv.slice(2)).catch(()=>{console.error('Monthly projection failed: verify aggregate input and flags.');process.exitCode=1;});
