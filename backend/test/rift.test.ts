import {before,after,test} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {createApp} from '../src/app.js';
import {TestDatabase} from './database-fixture.js';
import type {SessionResponse} from '../src/contracts.js';
import {calculateScore,RIFT_HEROES,type RiftResult} from '../src/rift.js';
let db:TestDatabase,app:Awaited<ReturnType<typeof createApp>>,a:SessionResponse,b:SessionResponse,c:SessionResponse;
function version3(method:string,url:string,payload?:unknown) {
 const body=method==='POST'&&['/v1/rift/sessions','/v1/rift/challenges'].includes(url)?{version:'rift-3',...(payload as object)}:payload;
 const path=method==='GET'&&(url.startsWith('/v1/rift/ranking')||url.startsWith('/v1/rift/users/'))?url+(url.includes('?')?'&':'?')+'version=rift-3':url;
 return {url:path,payload:body as any};
}
const req=(method:'GET'|'POST'|'PUT',url:string,who:SessionResponse,payload?:unknown)=>app.inject({method,...version3(method,url,payload),headers:{authorization:`Bearer ${who.accessToken}`}});
before(async()=> {db=await TestDatabase.create();app=await createApp(db,{limit:10000});
 const register=async(username:string)=>(await app.inject({method:'POST',url:'/v1/auth/register',payload:{name:username,username,email:`${username}@example.invalid`,password:'Password2026'},remoteAddress:'10.0.0.1'})).json<SessionResponse>();
 a=await register('rift_a');b=await register('rift_b');c=await register('rift_c');
});


after(async()=>{await app?.close();await db?.close();});
const metrics=(seed:number):RiftResult=>({seed,character:'spider-man',version:'rift-3',duration:1,kills:0,elites:0,bosses:0,damage:0,maxCombo:0,level:1,score:10,extracted:false,upgrades:{}});
test('previous-version pending results remain accepted but never enter the new ranking',async()=> {
 const session=(await req('POST','/v1/rift/sessions',c,{clientId:randomUUID()})).json();
 await db.engine.query('UPDATE marvel_lobby.arena_sessions SET game_version=$1 WHERE id=$2',['rift-1',session.id]);
 const submitted=await req('POST',`/v1/rift/sessions/${session.id}/result`,c,{...metrics(session.seed),version:'rift-1'});
 assert.equal(submitted.statusCode,200,submitted.body);
 const ranking=await req('GET','/v1/rift/ranking',c);
 assert.equal(ranking.json().own,null);assert.equal(ranking.json().items.length,0);
});
test('issued sessions are private and idempotent; server checks score and spawn bounds',async()=> {
 const clientId=randomUUID(),path='/v1/rift/sessions';const response=await req('POST',path,a,{clientId});assert.equal(response.statusCode,200,response.body);
 const session=response.json();assert.equal((await req('POST',path,a,{clientId})).json().id,session.id);
 const url=`${path}/${session.id}/result`,result=metrics(session.seed);
 assert.equal((await req('POST',url,b,result)).statusCode,404);
 assert.equal((await req('POST',url,a,{...result,score:999999999})).statusCode,400);
 assert.equal((await req('POST',url,a,{...result,kills:100,score:4010})).statusCode,400);
 assert.equal((await req('POST',url,a,{...result,duration:600,extracted:true,score:6000})).statusCode,400);
 assert.equal((await req('POST',url,a,result)).statusCode,200);
 assert.equal((await req('POST',url,a,result)).statusCode,200);
 assert.equal((await req('POST',url,a,{...result,damage:5,score:11})).statusCode,409);
 assert.deepEqual(await db.transaction(b.user.id,q=>q.query('SELECT * FROM marvel_lobby.arena_sessions')),[]);
 assert.equal((await req('GET',`/v1/rift/users/${a.user.id}`,b)).json().best,10);
});
test('all ranking modes use one best result per user and expose current position',async()=> {
 await req('POST',`/v1/community/users/${a.user.id}/follow`,b);
 for(const mode of ['global','friends','weekly','character']) {
  const r=await req('GET',`/v1/rift/ranking?mode=${mode}`,a);assert.equal(r.statusCode,200,r.body);assert.equal(r.json().own.position,1);assert.equal(r.json().items[0].score,10);
 }
 assert.equal((await req('GET','/v1/rift/ranking?mode=friends',b)).json().items.length,1);
 assert.equal((await req('GET','/v1/rift/ranking?mode=friends',c)).json().items.length,0);
 assert.equal((await req('GET','/v1/rift/ranking?offset=1',a)).json().items.length,0);
});
test('challenges require follows, are participant-only and share one seed',async()=> {
 assert.equal((await req('POST','/v1/rift/challenges',a,{userId:b.user.id,clientId:randomUUID()})).statusCode,409);
 await req('POST',`/v1/community/users/${b.user.id}/follow`,a);
 const clientId=randomUUID(),first=await req('POST','/v1/rift/challenges',a,{userId:b.user.id,clientId});assert.equal(first.statusCode,200,first.body);
 const challenge=first.json();assert.equal((await req('POST','/v1/rift/challenges',a,{userId:b.user.id,clientId})).json().id,challenge.id);
 const sa=(await req('POST','/v1/rift/sessions',a,{clientId:randomUUID(),challengeId:challenge.id})).json();
 const sb=(await req('POST','/v1/rift/sessions',b,{clientId:randomUUID(),challengeId:challenge.id})).json();
 assert.equal(sa.seed,sb.seed);assert.equal(sa.version,sb.version);
 assert.equal((await req('POST','/v1/rift/sessions',c,{clientId:randomUUID(),challengeId:challenge.id})).statusCode,404);
 await req('POST',`/v1/rift/sessions/${sa.id}/result`,a,metrics(sa.seed));await req('POST',`/v1/rift/sessions/${sb.id}/result`,b,metrics(sb.seed));
 const list=await req('GET','/v1/rift/challenges',a);assert.equal(list.statusCode,200,list.body);assert.equal(list.json().items[0].own_score,10);assert.equal(list.json().items[0].peer_score,10);
 assert.deepEqual((await req('GET','/v1/rift/challenges',c)).json().items,[]);
 assert.equal((await req('GET',`/v1/rift/challenges?id=${challenge.id}`,a)).json().items.length,1);
 assert.equal((await req('POST','/v1/rift/sessions',a,{clientId:randomUUID(),challengeId:challenge.id})).statusCode,409);
 assert.equal(calculateScore({...metrics(1),duration:10,kills:5,elites:1,bosses:1,damage:400,maxCombo:40}),2530);
});
test('verified result cards cannot forge scores or leak challenges and milestone activities require consent',async()=> {
 await req('PUT','/v1/community/me/activity-sharing',a,{enabled:true});
 const session=(await req('POST','/v1/rift/sessions',a,{clientId:randomUUID()})).json();
 const result={...metrics(session.seed),duration:2,score:20};
 const submitted=await req('POST',`/v1/rift/sessions/${session.id}/result`,a,result);assert.equal(submitted.statusCode,200,submitted.body);
 await req('POST',`/v1/rift/sessions/${session.id}/result`,a,result);
 const feed=await req('GET','/v1/community/activity',b);assert.equal(feed.statusCode,200,feed.body);
 assert.equal(feed.json().items.filter((i:any)=>i.rift?.achievement==='record').length,1);
 assert.equal(feed.json().items[0].rift.score,20);
 assert.deepEqual((await req('GET','/v1/community/activity',c)).json().items,[]);
 const conversation=(await req('POST','/v1/community/conversations',a,{userId:b.user.id})).json().id;
 const path=`/v1/community/conversations/${conversation}/messages`,payload={text:'Rift Arena',clientId:randomUUID(),rift:{kind:'result',id:session.id}};
 const sent=await req('POST',path,a,payload);assert.equal(sent.statusCode,201,sent.body);assert.equal(sent.json().rift.score,20);
 assert.equal((await req('POST',path,a,payload)).json().id,sent.json().id);
 assert.equal((await req('POST',path,b,{...payload,clientId:randomUUID()})).statusCode,404);
 assert.equal((await req('POST',path,a,{...payload,rift:{...payload.rift,score:999999}})).statusCode,400);
 assert.equal((await req('GET',path,c)).statusCode,404);
 assert.equal((await req('GET','/v1/community/conversations',b)).json().items[0].lastMessage.rift.score,20);
 const challenge=(await req('GET','/v1/rift/challenges',a)).json().items[0];
 const challengeSent=await req('POST',path,a,{text:'Rift Arena',clientId:randomUUID(),rift:{kind:'challenge',id:challenge.id}});
 assert.equal(challengeSent.statusCode,201,challengeSent.body);
 await req('PUT','/v1/community/me/activity-sharing',a,{enabled:false});
 await req('PUT','/v1/community/me/activity-sharing',a,{enabled:true});
 assert.deepEqual((await req('GET','/v1/community/activity',b)).json().items,[]);
});
test('abandonment frees the session without admitting a later result; migration preserves existing runs',async()=> {
 const s=(await req('POST','/v1/rift/sessions',c,{clientId:randomUUID()})).json();
 await req('POST',`/v1/rift/sessions/${s.id}/abandon`,c);
 assert.equal((await req('POST',`/v1/rift/sessions/${s.id}/result`,c,metrics(s.seed))).statusCode,409);
 assert.equal((await req('POST','/v1/rift/sessions',c,{clientId:randomUUID()})).statusCode,200);
 await db.engine.exec('SET ROLE schema_admin');await db.engine.exec(readFileSync(new URL('../../../database/008_rift_arena.sql',import.meta.url),'utf8'));await db.engine.exec('RESET ROLE');
 assert.equal((await req('GET',`/v1/rift/users/${a.user.id}`,a)).json().best,20);
 const publicRows=await db.transaction(c.user.id,q=>q.query('SELECT * FROM marvel_lobby.arena_public_runs'));
 assert.ok(publicRows.length>0);assert.equal(publicRows[0]!.seed,undefined);assert.equal(publicRows[0]!.client_id,undefined);
});

test('six heroes issue private sessions, preserve character results and filter rankings',async()=> {
 const player=(await app.inject({method:'POST',url:'/v1/auth/register',payload:{name:'Hero player',username:'hero_player',email:'heroes@example.invalid',password:'Password2026'},remoteAddress:'10.0.0.2'})).json<SessionResponse>();
 const heroReq=(method:'GET'|'POST',url:string,payload?:unknown)=>app.inject({method,...version3(method,url,payload),headers:{authorization:`Bearer ${player.accessToken}`},remoteAddress:'10.0.0.25'});
 for(const character of RIFT_HEROES) {
  const clientId=randomUUID();const issued=await heroReq('POST','/v1/rift/sessions',{clientId,character});
  assert.equal(issued.statusCode,200,issued.body);const session=issued.json();assert.equal(session.character,character);
  assert.equal((await heroReq('POST','/v1/rift/sessions',{clientId,character})).json().id,session.id);
  if(character!=='spider-man')assert.equal((await heroReq('POST',`/v1/rift/sessions/${session.id}/result`,metrics(session.seed))).statusCode,400);
  const submitted=await heroReq('POST',`/v1/rift/sessions/${session.id}/result`,{...metrics(session.seed),character});assert.equal(submitted.statusCode,200,submitted.body);
  const ranking=await heroReq('GET',`/v1/rift/ranking?mode=character&character=${character}`);
  assert.equal(ranking.statusCode,200,ranking.body);assert.ok(ranking.json().items.every((r:any)=>r.character===character));assert.ok(ranking.json().own);
 }
 assert.equal((await heroReq('POST','/v1/rift/sessions',{clientId:randomUUID(),character:'invented'})).statusCode,400);
 await heroReq('POST',`/v1/community/users/${a.user.id}/follow`);
 const challenge=await heroReq('POST','/v1/rift/challenges',{userId:a.user.id,clientId:randomUUID(),character:'hulk'});
 assert.equal(challenge.statusCode,200,challenge.body);
 const id=challenge.json().id;
 const issued=await heroReq('POST','/v1/rift/sessions',{clientId:randomUUID(),challengeId:id,character:'iron-man'});
 assert.equal(issued.statusCode,200,issued.body);assert.equal(issued.json().character,'hulk');
 const before=(await db.engine.query<{count:string}>('SELECT count(*) FROM marvel_lobby.arena_runs')).rows[0]!.count;
 await db.engine.exec('SET ROLE schema_admin');
 await db.engine.exec(readFileSync(new URL('../../../database/009_rift_heroes.sql',import.meta.url),'utf8'));
 await db.engine.exec('RESET ROLE');
 assert.equal((await db.engine.query<{count:string}>('SELECT count(*) FROM marvel_lobby.arena_runs')).rows[0]!.count,before);
});

test('missing hero migration keeps Spider-Man available and returns an actionable service error for new heroes',async()=> {
 const player=(await app.inject({method:'POST',url:'/v1/auth/register',payload:{name:'Migration player',username:'migration_player',email:'migration@example.invalid',password:'Password2026'},remoteAddress:'10.0.0.26'})).json<SessionResponse>();
 await db.engine.exec('SET ROLE schema_admin; DELETE FROM marvel_lobby.schema_migrations WHERE version=8; RESET ROLE;');
 try {
  const refused=await req('POST','/v1/rift/sessions',player,{clientId:randomUUID(),character:'hulk'});
  assert.equal(refused.statusCode,503,refused.body);assert.equal(refused.json().error.code,'ARENA_NOT_READY');
  const legacy=await req('POST','/v1/rift/sessions',player,{clientId:randomUUID(),character:'spider-man'});
  assert.equal(legacy.statusCode,200,legacy.body);
 }finally {
  await db.engine.exec("SET ROLE schema_admin; INSERT INTO marvel_lobby.schema_migrations(version,description) VALUES(8,'six-hero test'); RESET ROLE;");
 }
});

test('older clients keep version-2 sessions and rankings while version-3 clients stay isolated',async()=> {
 const player=(await app.inject({method:'POST',url:'/v1/auth/register',payload:{name:'Legacy player',username:'legacy_player',email:'legacy@example.invalid',password:'Password2026'},remoteAddress:'10.0.0.27'})).json<SessionResponse>();
 const old=(method:'GET'|'POST',url:string,payload?:unknown)=>app.inject({method,url,payload:payload as any,headers:{authorization:`Bearer ${player.accessToken}`},remoteAddress:'10.0.0.27'});
 const issued=await old('POST','/v1/rift/sessions',{clientId:randomUUID()});assert.equal(issued.statusCode,200,issued.body);
 const session=issued.json();assert.equal(session.version,'rift-2');assert.equal(session.character,'spider-man');
 const submitted=await old('POST',`/v1/rift/sessions/${session.id}/result`,{...metrics(session.seed),version:'rift-2'});assert.equal(submitted.statusCode,200,submitted.body);
 assert.equal((await old('GET','/v1/rift/ranking')).json().own.score,10);
 assert.equal((await old('GET',`/v1/rift/users/${player.user.id}`)).json().runs,1);
 assert.equal((await req('GET','/v1/rift/ranking',player)).json().own,null);
 assert.equal((await old('POST','/v1/rift/sessions',{clientId:randomUUID(),character:'hulk'})).statusCode,400);
});
