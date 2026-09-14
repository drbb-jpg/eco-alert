import http from 'node:http';
import {mkdir,readFile,writeFile,rename} from 'node:fs/promises';
import {join} from 'node:path';
import WebSocket from 'ws';
import {initializeApp,applicationDefault} from 'firebase-admin/app';
import {getMessaging} from 'firebase-admin/messaging';
import {normalize,reminderDue,initialRelease,dateRange} from './calendar.mjs';

const credential=process.env.TE_CREDENTIAL;
if(!credential)throw new Error('TE_CREDENTIAL is required. No simulated data is served.');
const pollMs=Math.max(15000,Number(process.env.POLL_MS)||60000);
const staleAfter=Math.max(180000,pollMs*2);
const dataDir=process.env.DATA_DIR||'./data';
await mkdir(dataDir,{recursive:true});
const statePath=join(dataDir,'state.json');
let state={events:{},sent:{},updatedAt:null};
try{state=JSON.parse(await readFile(statePath,'utf8'));}catch(e){if(e.code!=='ENOENT')throw e;}
if(!state.events||!state.sent)throw new Error('Invalid state file');
state.pending ||= {};
const push=process.env.ENABLE_PUSH==='true';
if(push)initializeApp({credential:applicationDefault()});
let snapshotHealthy=false,streamOpen=false;
async function persist(){
  await writeFile(statePath+'.tmp',JSON.stringify(state),{mode:0o600});
  await rename(statePath+'.tmp',statePath);
}
let chain=Promise.resolve();
function serial(fn){chain=chain.then(fn).catch(()=>console.error('Calendar operation failed; retaining last valid snapshot'));return chain;}
async function sendOnce(key,topic,data,ttl){
  if(!push||state.sent[key])return;
  // Mark before dispatch: avoid duplicate alerts after restart; failed sends are retried within their window.
  state.sent[key]=Date.now();await persist();
  try {
    await getMessaging().send({topic,data:{...data,key,expires:String(Date.now()+ttl)},
      android:{priority:'high',ttl}});
  } catch(e) {delete state.sent[key];await persist();throw e;}
}
async function apply(next,notify){
  const previous=state.events[next.id];
  state.events[next.id]=next;
  if(notify&&initialRelease(previous,next,Date.now())){
    state.pending[next.id]={event:next,expires:Date.now()+300000};
    await persist();
    await dispatchResults();
  }
}
async function dispatchResults(){
  for(const [id, pending] of Object.entries(state.pending)){
    if(pending.expires<=Date.now()){delete state.pending[id];continue;}
    const next=pending.event;
    try{
      await sendOnce('result:'+id,'eco-results',{
        kind:'result',title:next.title+' · résultat',
        body:'Réel : '+next.actual+' | Prévu : '+(next.forecast??'—')+' | Précédent : '+(next.previous??'—')
      },pending.expires-Date.now());
      delete state.pending[id];
    }catch{console.error('Result delivery pending; will retry within expiry');}
  }
  await persist();
}
async function snapshot(){
  const [from,to]=dateRange();
  const url=new URL('https://api.tradingeconomics.com/calendar/country/united%20states,euro%20area/'+from+'/'+to);
  url.searchParams.set('c',credential);url.searchParams.set('f','json');
  const response=await fetch(url,{signal:AbortSignal.timeout(15000)});
  if(!response.ok){snapshotHealthy=false;throw new Error('Provider unavailable');}
  const rows=await response.json();
  if(!Array.isArray(rows)){snapshotHealthy=false;throw new Error('Invalid provider response');}
  const nextRows=rows.map(normalize).filter(Boolean);
  // Replace the scheduled set so canceled/removed events cannot keep generating reminders.
  const wanted=new Set(nextRows.map(e=>e.id));
  for(const id of Object.keys(state.events))if(!wanted.has(id))delete state.events[id];
  const first=!state.updatedAt;
  for(const event of nextRows)await apply(event,!first);
  for(const [key,time]of Object.entries(state.sent))if(Date.now()-time>14*86400000)delete state.sent[key];
  state.updatedAt=new Date().toISOString();snapshotHealthy=true;await persist();
}
let polling=false;
async function poll(){
  if(polling)return;polling=true;
  try{await serial(async()=>{try{await snapshot();}catch(e){snapshotHealthy=false;throw e;}});}
  finally{polling=false;}
}
await poll();
setInterval(poll,pollMs);
function isStale(){return !snapshotHealthy||!state.updatedAt||Date.now()-Date.parse(state.updatedAt)>staleAfter;}
setInterval(()=>serial(async()=>{
  await dispatchResults();
  if(isStale())return;
  for(const e of Object.values(state.events))for(const m of [5,15,30])
    if(reminderDue(e,m,Date.now()))await sendOnce('before:'+e.id+':'+e.time+':'+m,'eco-before-'+m,
      {kind:'reminder',minutes:String(m),title:e.title+' · dans '+m+' min',
       body:'Prévu : '+(e.forecast??'—')+' | Précédent : '+(e.previous??'—')},60000);
}),10000);

let reconnect=1000;
function connectStream(){
  const url=new URL('wss://stream.tradingeconomics.com/');
  url.searchParams.set('client',credential);
  const ws=new WebSocket(url,{handshakeTimeout:15000,maxPayload:2000000});
  let alive=true;
  const heartbeat=setInterval(()=>{if(!alive){ws.terminate();return;}alive=false;ws.ping();},30000);
  ws.on('pong',()=>{alive=true;});
  ws.on('open',()=>{streamOpen=true;reconnect=1000;ws.send(JSON.stringify({topic:'subscribe',to:'calendar'}));});
  ws.on('message',data=>{
    let row;try{row=JSON.parse(data.toString());}catch{return;}
    if(row.topic!=='calendar')return;
    serial(async()=>{
      const id=String(row.calendarId??row.CalendarId??'');
      const old=state.events[id];
      if(!old)return; // Snapshot supplies the authoritative schedule and country scope.
      const patch=normalize({...row,DateSpan:old.tentative?1:0});
      if(!patch)return;
      // Streaming nulls mean absent metadata: preserve known consensus and previous values.
      for(const key of ['forecast','previous','revised','actual'])if(patch[key]===null)patch[key]=old[key];
      if(!patch.reference)patch.reference=old.reference;
      await apply(patch,true);await persist();
    });
  });
  ws.on('error',()=>{streamOpen=false;ws.terminate();});
  ws.on('close',()=>{streamOpen=false;clearInterval(heartbeat);setTimeout(connectStream,reconnect);reconnect=Math.min(reconnect*2,60000);});
}
if(process.env.ENABLE_STREAM!=='false')connectStream();

http.createServer((req,res)=>{
  res.setHeader('Content-Type','application/json; charset=utf-8');
  res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
  if(req.method!=='GET'){res.writeHead(405);res.end('{}');return;}
  if(req.url==='/health'){res.writeHead(isStale()?503:200);res.end(JSON.stringify({healthy:!isStale(),streamOpen,pushEnabled:push}));return;}
  if(req.url!=='/events'){res.writeHead(404);res.end('{}');return;}
  if(!state.updatedAt){res.writeHead(503);res.end('{"error":"Calendar not ready"}');return;}
  res.end(JSON.stringify({updatedAt:state.updatedAt,stale:isStale(),streamOpen,
    events:Object.values(state.events).sort((a,b)=>a.time.localeCompare(b.time))}));
}).listen(Number(process.env.PORT)||8080,'0.0.0.0');
console.log('Eco Alert data service started; push '+(push?'enabled':'disabled'));
