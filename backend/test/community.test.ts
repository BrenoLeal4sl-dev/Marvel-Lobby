import {before,after,beforeEach,test} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {readFileSync} from 'node:fs';
import WebSocket from 'ws';
import {createApp} from '../src/app.js';
import {TestDatabase} from './database-fixture.js';
import type {SessionResponse} from '../src/contracts.js';

let db:TestDatabase,app:Awaited<ReturnType<typeof createApp>>;
let alice:SessionResponse,bob:SessionResponse,eve:SessionResponse,count=0;
const request=(method:'GET'|'POST'|'DELETE',url:string,who?:SessionResponse,payload?:unknown)=>app.inject({method,url,payload:payload as any,
  headers:who?{authorization:`Bearer ${who.accessToken}`}:{},remoteAddress:`10.20.${Math.floor(++count/250)}.${count%250+1}`});
before(async()=> {
  db=await TestDatabase.create();app=await createApp(db);
  const register=async(username:string)=>(await request('POST','/v1/auth/register',undefined,{name:username,username,
    email:`${username}@example.invalid`,password:'Password2026',bio:'My public bio',avatarId:1440})).json<SessionResponse>();
  alice=await register('alice_hero');bob=await register('bob_hero');eve=await register('eve_hero');
});
after(async()=>{await app?.close();await db?.close();});
beforeEach(async()=>{await db.engine.exec('TRUNCATE marvel_lobby.follows,marvel_lobby.direct_conversations CASCADE');});
const open=async(from:SessionResponse,to:SessionResponse)=>(await request('POST','/v1/community/conversations',from,{userId:to.user.id})).json<{id:string}>().id;
const send=async(id:string,who:SessionResponse,text='Hello',clientId=randomUUID())=>request('POST',`/v1/community/conversations/${id}/messages`,who,{text,clientId});

test('public search is paginated, escapes wildcards and never searches or reveals emails',async()=> {
  assert.equal((await request('GET','/v1/community/people')).statusCode,401);
  const first=await request('GET','/v1/community/people?limit=1',alice);
  assert.equal(first.statusCode,200,first.body);assert.equal(first.json().items.length,1);
  const second=await request('GET',`/v1/community/people?limit=1&after=${first.json().next}`,alice);
  assert.notEqual(second.json().items[0].id,first.json().items[0].id);
  const found=(await request('GET','/v1/community/people?query=%40bob_hero',alice)).json().items;
  assert.equal(found[0].bio,'My public bio');assert.equal(found[0].email,undefined);
  assert.deepEqual((await request('GET','/v1/community/people?query=%25',alice)).json().items,[]);
  assert.deepEqual((await request('GET','/v1/community/people?query=example.invalid',alice)).json().items,[]);
});
test('following is unilateral and repeated follow/unfollow is idempotent',async()=> {
  const path=`/v1/community/users/${bob.user.id}/follow`;
  assert.equal((await request('POST',path,alice)).json().followers,1);
  assert.equal((await request('POST',path,alice)).json().followers,1);
  const mine=(await request('GET',`/v1/community/users/${alice.user.id}`,bob)).json();
  assert.equal(mine.following,1);assert.equal(mine.followers,0);assert.equal(mine.followsYou,true);
  const list=(await request('GET',`/v1/community/users/${bob.user.id}/followers`,bob)).json();
  assert.equal(list.items[0].id,alice.user.id);
  assert.equal((await request('DELETE',path,alice)).json().followers,0);
  assert.equal((await request('DELETE',path,alice)).json().isFollowing,false);
  assert.equal((await request('POST',`/v1/community/users/${alice.user.id}/follow`,alice)).statusCode,400);
  assert.equal((await request('POST',`/v1/community/users/${randomUUID()}/follow`,alice)).statusCode,404);
});
test('Android empty form POST can follow while JSON and authentication remain required elsewhere',async()=> {
  const path=`/v1/community/users/${bob.user.id}/follow`;
  const headers={authorization:`Bearer ${alice.accessToken}`,'content-type':'application/x-www-form-urlencoded'};
  const legacy=await app.inject({method:'POST',url:path,headers,payload:''});
  assert.equal(legacy.statusCode,200,legacy.body);assert.equal(legacy.json().isFollowing,true);
  assert.equal(legacy.json().followers,1);
  const json=await request('POST',path,alice,{});
  assert.equal(json.statusCode,200,json.body);assert.equal(json.json().followers,1);
  assert.equal((await app.inject({method:'POST',url:path,headers:{'content-type':'application/x-www-form-urlencoded'},payload:''})).statusCode,401);
  assert.equal((await app.inject({method:'POST',url:path,headers,payload:'follower_id=someone_else'})).statusCode,400);
  const account=await app.inject({method:'POST',url:'/v1/auth/login',headers,payload:'email=fake&password=fake'});
  assert.equal(account.statusCode,415);assert.equal(account.json().error.code,'INVALID_INPUT');
  assert.equal((await request('DELETE',path,alice)).json().isFollowing,false);
});
test('RLS prevents another user from creating or deleting someone else follow',async()=> {
  await request('POST',`/v1/community/users/${bob.user.id}/follow`,alice);
  await assert.rejects(db.transaction(eve.user.id,q=>q.query('INSERT INTO marvel_lobby.follows(follower_id,followed_id) VALUES($1,$2)',[bob.user.id,alice.user.id])),{code:'42501'});
  await db.transaction(eve.user.id,q=>q.query('DELETE FROM marvel_lobby.follows WHERE follower_id=$1',[alice.user.id]));
  assert.equal((await request('GET',`/v1/community/users/${bob.user.id}`,eve)).json().followers,1);
});
test('only one private conversation exists for a pair in either opening direction',async()=> {
  const id=await open(alice,bob);assert.equal(await open(bob,alice),id);
  assert.equal((await request('POST','/v1/community/conversations',alice,{userId:alice.user.id})).statusCode,400);
  assert.equal((await request('GET',`/v1/community/conversations/${id}/messages`,eve)).statusCode,404);
  assert.deepEqual((await request('GET','/v1/community/conversations',eve)).json().items,[]);
});
test('message retry cannot duplicate a send and cannot reuse its identity for another text',async()=> {
  const id=await open(alice,bob),client=randomUUID();
  const first=await send(id,alice,'  Hello  ',client);assert.equal(first.statusCode,201,first.body);
  const retry=await send(id,alice,'Hello',client);assert.equal(retry.json().id,first.json().id);
  assert.equal((await send(id,alice,'Different',client)).statusCode,409);
  const received=(await request('GET',`/v1/community/conversations/${id}/messages`,bob)).json();
  assert.equal(received.items.length,1);assert.equal(received.items[0].senderId,alice.user.id);
  assert.equal((await send(id,eve)).statusCode,404);
  assert.equal((await send(id,alice,'  ')).statusCode,400);
  assert.equal((await send(id,alice,'x'.repeat(2001))).statusCode,400);
});
test('message pagination and read receipts cannot skip, move backwards or mark a different conversation',async()=> {
  const id=await open(alice,bob),other=await open(alice,eve);
  const one=(await send(id,alice,'One')).json(),two=(await send(id,alice,'Two')).json(),three=(await send(id,bob,'Three')).json();
  const foreign=(await send(other,alice,'Other conversation')).json();
  const recent=(await request('GET',`/v1/community/conversations/${id}/messages?limit=2`,bob)).json();
  assert.deepEqual(recent.items.map((m:any)=>m.id),[two.id,three.id]);assert.equal(recent.hasMore,true);
  const older=(await request('GET',`/v1/community/conversations/${id}/messages?before=${two.id}`,bob)).json();
  assert.deepEqual(older.items.map((m:any)=>m.id),[one.id]);
  const newer=(await request('GET',`/v1/community/conversations/${id}/messages?after=${one.id}`,bob)).json();
  assert.deepEqual(newer.items.map((m:any)=>m.id),[two.id,three.id]);
  const unreadInbox=(await request('GET','/v1/community/conversations',bob)).json();
  assert.equal(unreadInbox.items[0].unread,2);assert.equal(unreadInbox.unreadTotal,2);
  assert.equal((await request('GET','/v1/community/conversations',eve)).json().unreadTotal,1);
  assert.equal((await request('POST',`/v1/community/conversations/${id}/read`,bob,{lastId:foreign.id})).statusCode,400);
  assert.equal((await request('POST',`/v1/community/conversations/${id}/read`,bob,{lastId:two.id})).statusCode,200);
  await request('POST',`/v1/community/conversations/${id}/read`,bob,{lastId:one.id});
  assert.equal((await request('GET',`/v1/community/conversations/${id}/messages`,alice)).json().peerLastRead,two.id);
  assert.equal((await request('GET','/v1/community/conversations',bob)).json().items[0].unread,0);
});
test('RLS hides direct texts and receipts from a third party even through raw SQL',async()=> {
  const id=await open(alice,bob);const sent=(await send(id,alice)).json();
  await request('POST',`/v1/community/conversations/${id}/read`,bob,{lastId:sent.id});
  for(const table of ['direct_conversations','direct_messages','direct_reads'])assert.deepEqual(await db.transaction(eve.user.id,q=>q.query(`SELECT * FROM marvel_lobby.${table}`)),[]);
  await assert.rejects(db.transaction(eve.user.id,q=>q.query('INSERT INTO marvel_lobby.direct_messages(conversation_id,sender_id,client_id,body) VALUES($1,$2,$3,$4)',[id,eve.user.id,randomUUID(),'Intrusion'])),{code:'42501'});
});
test('schema migration can be rerun without erasing community or AI data',async()=> {
  const id=await open(alice,bob);await send(id,alice);
  await db.engine.exec(readFileSync(new URL('../../../database/004_community.sql',import.meta.url),'utf8'));
  assert.equal((await request('GET',`/v1/community/conversations/${id}/messages`,bob)).json().items.length,1);
});
test('live events go only to participants and websocket requires an authenticated session',async()=> {
  await app.ready();
  await assert.rejects(app.injectWS('/v1/community/live'));
  const sockets=await Promise.all([alice,bob,eve].map(who=>app.injectWS('/v1/community/live',{headers:{authorization:`Bearer ${who.accessToken}`}})));
  const events: any[][]=sockets.map(()=>[]);
  sockets.forEach((socket,index)=>socket.on('message',body=>events[index]!.push(JSON.parse(body.toString()))));
  const id=await open(alice,bob);await send(id,alice);
  await new Promise(resolve=>setTimeout(resolve,40));
  assert.ok(events[0]!.some(e=>e.type==='messages'&&e.conversationId===id));
  assert.ok(events[1]!.some(e=>e.type==='messages'&&e.conversationId===id));
  assert.ok(!events[2]!.some(e=>e.conversationId===id));
  sockets.forEach(socket=>socket.terminate());
});

test('real websocket connections deliver message/read signals and recover missed messages after reconnect',async()=> {
  const origin=await app.listen({port:0,host:'127.0.0.1'});
  const sockets:WebSocket[]=[];
  const connect=async(who:SessionResponse)=> {
    const socket=new WebSocket(origin.replace('http://','ws://')+'/v1/community/live',
      {headers:{authorization:`Bearer ${who.accessToken}`}});
    sockets.push(socket);const events:any[]=[];
    await new Promise<void>((resolve,reject)=> {
      const timer=setTimeout(()=>reject(new Error('WebSocket ready signal timed out')),3000);
      socket.once('error',error=>{clearTimeout(timer);reject(error);});
      socket.on('message',body=> {
        const event=JSON.parse(body.toString());events.push(event);
        if(event.type==='ready'){clearTimeout(timer);resolve();}
      });
    });
    return {socket,events};
  };
  const waitSignal=async(events:any[],type:string,id:string)=> {
    const deadline=Date.now()+3000;
    while(!events.some(event=>event.type===type&&event.conversationId===id)) {
      assert.ok(Date.now()<deadline,`Missing live ${type} signal`);
      await new Promise(resolve=>setTimeout(resolve,10));
    }
  };
  const post=async(path:string,who:SessionResponse,body:unknown)=> {
    const response=await fetch(origin+path,{method:'POST',headers:{authorization:`Bearer ${who.accessToken}`,'content-type':'application/json'},body:JSON.stringify(body)});
    assert.ok(response.ok,`HTTP operation failed: ${response.status}`);return response.json() as Promise<any>;
  };
  try {
    const a=await connect(alice),b=await connect(bob),outsider=await connect(eve);
    const id=await open(alice,bob);
    const first=await post(`/v1/community/conversations/${id}/messages`,alice,{text:'Live hello',clientId:randomUUID()});
    await Promise.all([waitSignal(a.events,'messages',id),waitSignal(b.events,'messages',id)]);
    assert.ok(!outsider.events.some(event=>event.conversationId===id));
    assert.ok(b.events.every(event=>!('text' in event)));
    const page=await fetch(`${origin}/v1/community/conversations/${id}/messages`,{headers:{authorization:`Bearer ${bob.accessToken}`}});
    assert.equal((await page.json() as any).items[0].text,'Live hello');
    await post(`/v1/community/conversations/${id}/read`,bob,{lastId:first.id});
    await waitSignal(a.events,'read',id);
    const closed=new Promise(resolve=>b.socket.once('close',resolve));b.socket.terminate();await closed;
    const missed=await post(`/v1/community/conversations/${id}/messages`,alice,{text:'While disconnected',clientId:randomUUID()});
    await connect(bob);
    const recovered=await fetch(`${origin}/v1/community/conversations/${id}/messages?after=${first.id}`,{headers:{authorization:`Bearer ${bob.accessToken}`}});
    assert.deepEqual((await recovered.json() as any).items.map((message:any)=>message.id),[missed.id]);
  } finally { sockets.forEach(socket=>socket.terminate()); }
});
