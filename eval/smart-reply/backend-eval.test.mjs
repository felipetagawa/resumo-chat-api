import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {generate} from './backend-eval.mjs';
const bundle=JSON.parse(await readFile('target/smart-reply-eval/requests.json','utf8'));
test('backend evaluation ignores thoughts and joins visible parts while preserving legacy diagnostic',async()=>{
 const row=await generate(bundle,bundle.cases[0],'gemini-3.8-flash-low','dummy',async()=>({
  status:200,body:{candidates:[{finishReason:'STOP',content:{parts:[
   {thought:true,text:'PRIVATE_THOUGHT'},{text:'Please '},{text:'check the error.'}]}}]}}));
 assert.equal(row.output,'Please check the error.');
 assert.equal(row.legacyParserMismatch,true);assert.equal(row.parserMismatch,false);
 assert.equal(row.success,true);
});
test('backend evaluation rejects partial or blocked responses',async()=>{
 for(const finishReason of ['MAX_TOKENS','SAFETY']){
  const row=await generate(bundle,bundle.cases[0],'gemini-3.8-flash-low','dummy',async()=>({
   status:200,body:{candidates:[{finishReason,content:{parts:[{text:'Partial reply'}]}}]}}));
  assert.equal(row.success,false);assert.equal(row.deterministicPass,false);
 }
});
