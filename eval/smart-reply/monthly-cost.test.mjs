import test from 'node:test';
import assert from 'node:assert/strict';
import {compareMonthly, summarizeUsage, projectObserved} from './monthly-cost.mjs';
test('selective projection preserves other spending and checks both financial goals', () => {
 const x=compareMonthly({monthlyReplies:1100,baselinePerReply:.00014,premiumPerReply:.001875,otherMonthlyUsd:1,premiumShare:1});
 assert.equal(x.currentUsd,1.154); assert.equal(x.selectiveUsd,3.0625);
 assert.equal(x.withinTwice,false); assert.equal(x.belowFour,true);
 assert.ok(x.maximumPremiumShare < 1);
});
test('unknown charged attempts prevent financial approval and retries count in per-reply costs', () => {
 const row={event:'gemini_usage',feature:'smart_reply',model:'gemini-2.5-flash-lite',attempt:1,
  estimatedUsd2026:.01,estimatedUsd2027:.01,durationMs:50,outcome:'success'};
 const x=summarizeUsage([row,{...row,attempt:2,estimatedUsd2026:null,estimatedUsd2027:null,outcome:'timeout'}],7);
 assert.equal(x.groups[0].logicalRequests,1);
 assert.equal(x.groups[0].attempts,2); assert.equal(x.groups[0].unknownCosts,1);
 assert.equal(x.groups[0].perReply2026,null);
});
test('empty observations cannot imply free usage; invalid options fail', () => {
 assert.equal(summarizeUsage([],7).hasObservations,false);
 assert.throws(()=>summarizeUsage([],0));
 assert.throws(()=>compareMonthly({monthlyReplies:1,baselinePerReply:0,premiumPerReply:1,otherMonthlyUsd:0,premiumShare:2}));
});

test('observed forecasts retain separate prices and require manual activation review', () => {
 const base={event:'gemini_usage',feature:'smart_reply',model:'gemini-2.5-flash-lite',attempt:1,
  estimatedUsd2026:.00014,estimatedUsd2027:.00014,durationMs:50,outcome:'success'};
 const premium={...base,model:'gemini-3.8-flash',estimatedUsd2026:.001875,estimatedUsd2027:.00375};
 const x=projectObserved(summarizeUsage([base,premium],1),.05,1);
 assert.equal(x.financialEvidenceComplete,true);assert.equal(x.activationRecommended,false);
 assert.ok(x.projections['2027'].selectiveUsd>x.projections['2026'].selectiveUsd);
});
test('unexpected Smart Reply models invalidate the paired financial forecast', () => {
 const row={event:'gemini_usage',feature:'smart_reply',attempt:1,estimatedUsd2026:.01,estimatedUsd2027:.01};
 const rows=['gemini-2.5-flash-lite','gemini-3.8-flash','another-model'].map(model=>({...row,model}));
 assert.equal(projectObserved(summarizeUsage(rows,1),.05,1).financialEvidenceComplete,false);
});