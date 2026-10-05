import { before,after,test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { createApp } from '../src/app.js';
import { checkDatabase } from '../src/database.js';
import { TestDatabase } from './database-fixture.js';
import type { SessionResponse } from '../src/contracts.js';

let db:TestDatabase;
let app:Awaited<ReturnType<typeof createApp>>;
let counter=0,requestCounter=0;
const password='Password2026';
before(async()=>{db=await TestDatabase.create();await checkDatabase(db);app=await createApp(db);});
after(async()=>{await app?.close();await db?.close();});
async function request(method:'GET'|'POST'|'PATCH',url:string,payload?:unknown,token?:string,remoteAddress?:string) {
  return app.inject({method,url,payload:payload as any,
    headers:token?{authorization:`Bearer ${token}`}:{},remoteAddress:remoteAddress??`10.0.${Math.floor(++requestCounter/250)}.${requestCounter%250+1}`});
}
async function register():Promise<SessionResponse>{
  const suffix=++counter;
  const response=await request('POST','/v1/auth/register',{name:`Hero ${suffix}`,username:`hero_${suffix}`,email:`hero${suffix}@example.invalid`,password,avatarId:1440,bio:'Marvel fan'});
  assert.equal(response.statusCode,201,response.body);
  return response.json();
}
test('registration stores a password hash and exposes a complete own profile',async()=>{
  const session=await register();
  assert.match(session.user.id,/^[0-9a-f-]{36}$/);
  assert.equal(session.user.avatarId,1440);
  assert.equal(session.user.bio,'Marvel fan');
  assert.ok(Date.parse(session.user.joinedAt));
  const me=await request('GET','/v1/me',undefined,session.accessToken);
  assert.equal(me.statusCode,200);assert.deepEqual(me.json(),session.user);
  const saved=(await db.engine.query<{password_hash:string}>('SELECT password_hash FROM marvel_lobby.users WHERE id=$1',[session.user.id])).rows[0]!;
  assert.match(saved.password_hash,/^scrypt\$131072\$8\$1\$/);
  assert.ok(!saved.password_hash.includes(password));
  const tokenRow=(await db.engine.query<{access_token_hash:Uint8Array}>('SELECT access_token_hash FROM marvel_lobby.sessions WHERE user_id=$1',[session.user.id])).rows[0]!;
  assert.deepEqual(Buffer.from(tokenRow.access_token_hash),createHash('sha256').update(session.accessToken).digest());
  assert.equal(me.headers['cache-control'],'no-store');
});
test('login rejects incorrect credentials without disclosing account existence',async()=>{
  const session=await register();
  const login=await request('POST','/v1/auth/login',{email:session.user.email.toUpperCase(),password});
  assert.equal(login.statusCode,200);assert.equal(login.json().user.id,session.user.id);
  const wrong=await request('POST','/v1/auth/login',{email:session.user.email,password:'WrongPassword2026'});
  const missing=await request('POST','/v1/auth/login',{email:'missing@example.invalid',password});
  assert.equal(wrong.statusCode,401);assert.deepEqual(wrong.json(),missing.json());
});
test('duplicate username/email roll back the whole registration',async()=>{
  const session=await register();
  const duplicateUsername=await request('POST','/v1/auth/register',{name:'Duplicate',username:`@${session.user.username.toUpperCase()}`,email:'duplicate@example.invalid',password});
  assert.equal(duplicateUsername.statusCode,409);assert.equal(duplicateUsername.json().error.code,'USERNAME_TAKEN');
  assert.equal((await db.engine.query('SELECT id FROM marvel_lobby.users WHERE email=$1',['duplicate@example.invalid'])).rows.length,0);
  const duplicateEmail=await request('POST','/v1/auth/register',{name:'Duplicate',username:'another_handle',email:session.user.email.toUpperCase(),password});
  assert.equal(duplicateEmail.statusCode,409);assert.equal(duplicateEmail.json().error.code,'EMAIL_TAKEN');
});
test('public profiles contain no account credentials and require authentication',async()=>{
  const alice=await register(),bob=await register();
  assert.equal((await request('GET',`/v1/users/${bob.user.id}`)).statusCode,401);
  const response=await request('GET',`/v1/users/${bob.user.id}`,undefined,alice.accessToken);
  assert.equal(response.statusCode,200);
  assert.deepEqual(Object.keys(response.json()).sort(),['avatarId','bio','id','joinedAt','name','username']);
  assert.equal(response.json().id,bob.user.id);
  const privateProfiles=await db.transaction(alice.user.id,query=>query.query('SELECT user_id FROM marvel_lobby.profiles'));
  assert.deepEqual(privateProfiles.map(row=>row.user_id),[alice.user.id]);
});
test('editing a profile preserves UUID/join date and cannot impersonate another account',async()=>{
  const alice=await register(),bob=await register();
  const edit={name:'Updated hero',username:'updated_handle',bio:'X-Men fan',avatarId:1443};
  const response=await request('PATCH','/v1/me',edit,alice.accessToken);
  assert.equal(response.statusCode,200);assert.equal(response.json().id,alice.user.id);assert.equal(response.json().joinedAt,alice.user.joinedAt);
  assert.equal((await request('PATCH','/v1/me',{...edit,userId:bob.user.id},alice.accessToken)).statusCode,400);
  assert.equal((await request('PATCH','/v1/me',{...edit,username:bob.user.username},alice.accessToken)).statusCode,409);
  assert.equal((await request('GET','/v1/me',undefined,bob.accessToken)).json().name,bob.user.name);
});
test('invalid avatars, bio, JSON, tokens and UUIDs are rejected',async()=>{
  const session=await register();
  const data={name:'Hero',username:'valid_handle',bio:'',avatarId:1440};
  assert.equal((await request('PATCH','/v1/me',{...data,avatarId:999},session.accessToken)).statusCode,400);
  assert.equal((await request('PATCH','/v1/me',{...data,bio:'x'.repeat(281)},session.accessToken)).statusCode,400);
  assert.equal((await request('GET','/v1/me',undefined,'invalid-token')).statusCode,401);
  assert.equal((await request('GET','/v1/users/not-a-uuid',undefined,session.accessToken)).statusCode,400);
  const malformed=await app.inject({method:'POST',url:'/v1/auth/login',headers:{'content-type':'application/json'},payload:'{'});
  assert.equal(malformed.statusCode,400);assert.ok(!malformed.body.includes('SyntaxError'));
});
test('refresh rotates tokens and reuse commits family revocation',async()=>{
  const initial=await register();
  const refresh=await request('POST','/v1/auth/refresh',{refreshToken:initial.refreshToken});
  assert.equal(refresh.statusCode,200);
  const next=refresh.json<SessionResponse>();assert.notEqual(next.accessToken,initial.accessToken);
  assert.equal(next.refreshExpiresAt,initial.refreshExpiresAt);
  assert.equal((await request('GET','/v1/me',undefined,initial.accessToken)).statusCode,401);
  assert.equal((await request('GET','/v1/me',undefined,next.accessToken)).statusCode,200);
  assert.equal((await request('POST','/v1/auth/refresh',{refreshToken:initial.refreshToken})).statusCode,401);
  assert.equal((await request('GET','/v1/me',undefined,next.accessToken)).statusCode,401);
  assert.equal((await request('POST','/v1/auth/refresh',{refreshToken:next.refreshToken})).statusCode,401);
});
test('logout revokes its session but leaves other devices signed in',async()=>{
  const first=await register();
  const second=(await request('POST','/v1/auth/login',{email:first.user.email,password})).json<SessionResponse>();
  assert.equal((await request('POST','/v1/auth/logout',undefined,first.accessToken)).statusCode,204);
  assert.equal((await request('GET','/v1/me',undefined,first.accessToken)).statusCode,401);
  assert.equal((await request('POST','/v1/auth/refresh',{refreshToken:first.refreshToken})).statusCode,401);
  assert.equal((await request('GET','/v1/me',undefined,second.accessToken)).statusCode,200);
});
test('credential changes require password, keep UUID and revoke previous sessions',async()=>{
  const original=await register();
  const data={email:`changed${++counter}@example.invalid`,currentPassword:'Wrong2026',newPassword:'NewPassword2026'};
  assert.equal((await request('PATCH','/v1/me/credentials',data,original.accessToken)).statusCode,403);
  const changed=await request('PATCH','/v1/me/credentials',{...data,currentPassword:password},original.accessToken);
  assert.equal(changed.statusCode,200);
  const next=changed.json<SessionResponse>();assert.equal(next.user.id,original.user.id);assert.equal(next.user.username,original.user.username);
  assert.equal((await request('GET','/v1/me',undefined,original.accessToken)).statusCode,401);
  assert.equal((await request('GET','/v1/me',undefined,next.accessToken)).statusCode,200);
  assert.equal((await request('POST','/v1/auth/login',{email:data.email,password})).statusCode,401);
  assert.equal((await request('POST','/v1/auth/login',{email:data.email,password:data.newPassword})).statusCode,200);
});
test('combined profile and credential changes roll back together on a duplicate username',async()=>{
  const alice=await register(),bob=await register();
  const data={email:`atomic${++counter}@example.invalid`,currentPassword:password,newPassword:'AtomicPassword2026',
    profile:{name:'Atomic hero',username:bob.user.username,bio:'New biography',avatarId:1443}};
  const rejected=await request('PATCH','/v1/me/credentials',data,alice.accessToken);
  assert.equal(rejected.statusCode,409);
  const untouched=(await request('GET','/v1/me',undefined,alice.accessToken)).json();
  assert.equal(untouched.email,alice.user.email);assert.equal(untouched.name,alice.user.name);
  assert.equal((await request('POST','/v1/auth/login',{email:alice.user.email,password})).statusCode,200);
  const accepted=await request('PATCH','/v1/me/credentials',{...data,profile:{...data.profile,username:`atomic_${counter}`}},alice.accessToken);
  assert.equal(accepted.statusCode,200);
  assert.equal(accepted.json().user.name,'Atomic hero');assert.equal(accepted.json().user.email,data.email);
  assert.equal((await request('GET','/v1/me',undefined,alice.accessToken)).statusCode,401);
});

test('expired tokens and disabled users cannot access private or public profiles',async()=>{
  const first=await register();
  await db.engine.query("UPDATE marvel_lobby.sessions SET access_expires_at=created_at+interval '1 millisecond' WHERE user_id=$1",[first.user.id]);
  assert.equal((await request('GET','/v1/me',undefined,first.accessToken)).statusCode,401);
  const other=await register();
  await db.engine.query('UPDATE marvel_lobby.users SET disabled_at=now() WHERE id=$1',[other.user.id]);
  assert.equal((await request('GET','/v1/me',undefined,other.accessToken)).statusCode,401);
  assert.equal((await request('POST','/v1/auth/login',{email:other.user.email,password})).statusCode,401);
});
test('authentication rate limit cannot be bypassed with an untrusted forwarded IP',async()=>{
  const address='192.0.2.15';
  for(let i=0;i<10;i++){
    const response=await app.inject({method:'POST',url:'/v1/auth/login',remoteAddress:address,headers:{'x-forwarded-for':`192.0.2.${100+i}`},payload:{email:'bad',password:'x'}});
    assert.equal(response.statusCode,400);
  }
  const limited=await request('POST','/v1/auth/login',{email:'bad',password:'x'},undefined,address);
  assert.equal(limited.statusCode,429);assert.equal(limited.json().error.code,'RATE_LIMITED');
  assert.ok(limited.headers['retry-after']);
});
