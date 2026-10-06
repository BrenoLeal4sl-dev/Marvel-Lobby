import {before,after,test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createApp} from '../src/app.js';
import {TestDatabase} from './database-fixture.js';
import type {SessionResponse} from '../src/contracts.js';

let db:TestDatabase,app:Awaited<ReturnType<typeof createApp>>,alice:SessionResponse,bob:SessionResponse,count=0;
const request=(method:'GET'|'POST'|'PUT'|'PATCH',url:string,who?:SessionResponse,payload?:unknown)=>app.inject({method,url,payload:payload as any,
  headers:who?{authorization:`Bearer ${who.accessToken}`}:{},remoteAddress:`10.30.0.${++count}`});
const item=(id:number,type='character',favorite=true)=>({id,type,name:`Hero ${id}`,imageUrl:'https://comicvine.gamespot.com/a.png',favorite});
before(async()=> {
  db=await TestDatabase.create();app=await createApp(db);
  const register=async(username:string)=>(await request('POST','/v1/auth/register',undefined,{name:username,username,
    email:`${username}@example.invalid`,password:'Password2026',bio:'Original bio',avatarId:1440})).json<SessionResponse>();
  alice=await register('alice_favorites');bob=await register('bob_favorites');
});
after(async()=>{await app?.close();await db?.close();});

test('favorites are private by default and sharing exposes catalog projections without private data',async()=> {
  assert.equal((await request('GET','/v1/community/me/favorite-sharing')).statusCode,401);
  assert.equal((await request('GET','/v1/community/me/favorite-sharing',alice)).json().enabled,false);
  assert.equal((await request('POST','/v1/community/me/favorites',alice,{items:[item(1)]})).statusCode,200);
  const path=`/v1/community/users/${alice.user.id}/favorites?type=character`;
  assert.deepEqual((await request('GET',path,bob)).json(),{visible:false,items:[],next:null});
  assert.deepEqual(await db.transaction(bob.user.id,q=>q.query('SELECT * FROM marvel_lobby.public_favorites')),[]);
  assert.equal((await request('PUT','/v1/community/me/favorite-sharing',alice,{enabled:true})).json().enabled,true);
  const publicPage=(await request('GET',path,bob)).json();
  assert.equal(publicPage.items[0].id,1);assert.equal(publicPage.visible,true);
  assert.deepEqual(Object.keys(publicPage.items[0]).sort(),['id','imageUrl','name','type']);
  await request('PUT','/v1/community/me/favorite-sharing',alice,{enabled:false});
  assert.deepEqual((await request('GET',path,bob)).json(),{visible:false,items:[],next:null});
  assert.equal((await db.transaction(alice.user.id,q=>q.query('SELECT * FROM marvel_lobby.public_favorites'))).length,1);
});
test('categories paginate independently, retries do not duplicate and removals are synchronized',async()=> {
  const saved=await request('POST','/v1/community/me/favorites',alice,{items:[...Array.from({length:15},(_,i)=>item(i+10)),item(10,'power'),item(10,'team'),item(10,'story_arc')]});
  assert.equal(saved.statusCode,200,saved.body);
  await request('PUT','/v1/community/me/favorite-sharing',alice,{enabled:true});
  const path=`/v1/community/users/${alice.user.id}/favorites?type=character&limit=12`;
  const first=(await request('GET',path,bob)).json(),second=(await request('GET',path+`&offset=${first.next}`,bob)).json();
  assert.equal(first.items.length,12);assert.equal(first.next,12);assert.equal(second.next,null);
  assert.equal(new Set([...first.items,...second.items].map((r:any)=>r.id)).size,16);
  const retry=await request('POST','/v1/community/me/favorites',alice,{items:[item(10),item(10,'power',false)]});assert.equal(retry.statusCode,200);
  assert.deepEqual((await request('GET',`/v1/community/users/${alice.user.id}/favorites?type=power`,bob)).json().items,[]);
  assert.equal((await request('GET',`/v1/community/users/${alice.user.id}/favorites?type=team`,bob)).json().items.length,1);
  assert.equal((await request('GET',`/v1/community/users/${alice.user.id}/favorites?type=story_arc`,bob)).json().items.length,1);
});
test('another account cannot write public rows or change another persons visibility even through SQL',async()=> {
  await assert.rejects(db.transaction(bob.user.id,q=>q.query(`INSERT INTO marvel_lobby.public_favorites(user_id,resource_type,comic_vine_id,name) VALUES($1,'character',99,'Intrusion')`,[alice.user.id])),{code:'42501'});
  await db.transaction(bob.user.id,q=>q.query('DELETE FROM marvel_lobby.public_favorites WHERE user_id=$1',[alice.user.id]));
  await db.transaction(bob.user.id,q=>q.query('UPDATE marvel_lobby.preferences SET show_favorites=false WHERE user_id=$1',[alice.user.id]));
  assert.equal((await request('GET','/v1/community/me/favorite-sharing',alice)).json().enabled,true);
  assert.equal((await request('POST','/v1/community/me/favorites',bob,{items:[{...item(20),userId:alice.user.id}]})).statusCode,400);
  assert.equal((await request('GET',`/v1/community/users/${alice.user.id}/favorites?type=issue`,bob)).statusCode,400);
  assert.equal((await request('POST','/v1/community/me/favorites',alice,{items:[item(1),item(1)]})).statusCode,400);
});
test('arbitrary external images are omitted and SQL migration is idempotent without erasing favorites',async()=> {
  await request('POST','/v1/community/me/favorites',bob,{items:[{...item(77),imageUrl:'https://example.invalid/tracker.png'}]});
  await request('PUT','/v1/community/me/favorite-sharing',bob,{enabled:true});
  await db.engine.exec(readFileSync(new URL('../../../database/005_public_favorites.sql',import.meta.url),'utf8'));
  const page=(await request('GET',`/v1/community/users/${bob.user.id}/favorites?type=character`,alice)).json();
  assert.equal(page.items[0].id,77);assert.equal(page.items[0].imageUrl,null);
});
test('quick biography update preserves name, username, avatar, credentials and accepts clearing',async()=> {
  const before=(await request('GET','/v1/me',alice)).json();
  const updated=await request('PATCH','/v1/me/bio',alice,{bio:'  Marvel é minha história 🕷️  '});
  assert.equal(updated.statusCode,200,updated.body);assert.equal(updated.json().bio,'Marvel é minha história 🕷️');
  for(const field of ['name','username','avatarId','email','id'])assert.deepEqual(updated.json()[field],before[field]);
  assert.equal((await request('PATCH','/v1/me/bio',alice,{bio:'🕷'.repeat(281)})).statusCode,400);
  assert.equal((await request('PATCH','/v1/me/bio',alice,{bio:'',email:'fake@example.invalid'})).statusCode,400);
  assert.equal((await request('PATCH','/v1/me/bio',alice,{bio:''})).json().bio,'');
});
