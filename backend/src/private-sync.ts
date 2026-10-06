import type {Accounts} from './accounts.js';
import type {Principal} from './contracts.js';
import type {Query,Row} from './database.js';
import {ApiError,invalid} from './errors.js';

export interface ArchiveInput {key:string;scope:'history'|'chat';base:string;nonce:string;payload:Record<string,unknown>|null}
const types=['CHARACTER','TEAM','POWER','STORY_ARC','ISSUE','VOLUME','PUBLISHER','LOCATION'];
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
function entity(value:unknown) {
 const e=value as Record<string,unknown>|null;
 if(!e || !Number.isInteger(e.id) || (e.id as number)<1 || (e.id as number)>2147483647 || !types.includes(e.type as string) || typeof e.name!=='string' || e.name.length>1000)invalid();
}
function validate(input:ArchiveInput) {
 if(!/^[0-9]{1,19}$/.test(input.base)||BigInt(input.base)>9223372036854775807n)invalid();
 if(input.scope==='history') {
  if(!/^history:(CHARACTER|TEAM|POWER|STORY_ARC|ISSUE|VOLUME|PUBLISHER|LOCATION):[1-9][0-9]*$/.test(input.key))invalid();
  if(input.payload) {
   entity(input.payload.entity);const e=input.payload.entity as {type:string;id:number};
   if(input.key!==`history:${e.type}:${e.id}` || !Number.isSafeInteger(input.payload.viewedAt) || (input.payload.viewedAt as number)<1)invalid();
  }
 } else if(input.scope==='chat') {
  if(!input.key.startsWith('chat:')||!uuid.test(input.key.slice(5)))invalid();
  if(input.payload) {
   const p=input.payload;
   if(p.id!==input.key.slice(5) || typeof p.title!=='string' || p.title.length<1 || p.title.length>500 || !Array.isArray(p.messages) || p.messages.length>2000 || !Array.isArray(p.sources) || p.sources.length>100)invalid();
   for(const m of p.messages as Array<Record<string,unknown>>)if(!m || !['user','model'].includes(m.role as string) || typeof m.text!=='string' || m.text.length>64000)invalid();
   if(p.context)entity(p.context);for(const source of p.sources)entity(source);
  }
 } else invalid();
 if(JSON.stringify(input.payload).length>800000)invalid('This conversation is too large to sync.');
}
function record(row:Row) {return {key:row.record_key,scope:row.scope,payload:row.payload,revision:String(row.revision),nonce:row.nonce};}
export class PrivateSync {
 constructor(private readonly accounts:Accounts) {}
 private run<T>(who:Principal,action:(q:Query)=>Promise<T>) {return this.accounts.authorized(who,async q=> {
  if(!(await q.query('SELECT version FROM marvel_lobby.schema_migrations WHERE version=6')).length)throw new ApiError(503,'SYNC_NOT_READY','The synchronization update is not installed yet.');
  return action(q);
 });}
 status(who:Principal) {return this.run(who,async q=>({enabled:(await q.query('SELECT cloud_sync FROM marvel_lobby.preferences WHERE user_id=$1',[who.userId]))[0]?.cloud_sync===true}));}
 choose(who:Principal,enabled:boolean) {return this.run(who,async q=> {
  await q.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',[`archive:${who.userId}`]);
  await q.query('UPDATE marvel_lobby.preferences SET cloud_sync=$2 WHERE user_id=$1',[who.userId,enabled]);return {enabled};
 });}
 private async enabled(q:Query,id:string) {if((await q.query('SELECT cloud_sync FROM marvel_lobby.preferences WHERE user_id=$1',[id]))[0]?.cloud_sync!==true)throw new ApiError(409,'SYNC_PAUSED','Cloud synchronization is paused.');}
 pull(who:Principal,after:string) {
  if(!/^[0-9]{1,19}$/.test(after)||BigInt(after)>9223372036854775807n)invalid();
  return this.run(who,async q=> {
   await this.enabled(q,who.userId);
   // One record keeps mobile responses bounded even for large AI conversations.
   const rows=await q.query('SELECT * FROM marvel_lobby.private_archive WHERE user_id=$1 AND revision>$2 ORDER BY revision LIMIT 2',[who.userId,after]);
   return {items:rows.slice(0,1).map(record),more:rows.length>1};
  });
 }
 put(who:Principal,input:ArchiveInput) {
  validate(input);
  return this.run(who,async q=> {
   // Per-owner serialization also prevents incremental cursors from skipping uncommitted revisions.
   await q.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',[`archive:${who.userId}`]);await this.enabled(q,who.userId);
   const prior=(await q.query('SELECT * FROM marvel_lobby.private_archive WHERE user_id=$1 AND record_key=$2',[who.userId,input.key]))[0];
   if(prior?.nonce===input.nonce) {
    if(JSON.stringify(prior.payload)!==JSON.stringify(input.payload)) {
     // jsonb key ordering differs; compare canonical values rather than the transport's order.
     const same=(await q.query('SELECT payload IS NOT DISTINCT FROM $3::jsonb AS same FROM marvel_lobby.private_archive WHERE user_id=$1 AND record_key=$2',[who.userId,input.key,input.payload]))[0]!.same;
     if(!same)throw new ApiError(409,'SYNC_NONCE_CONFLICT','This synchronization identifier was already used.');
    }
    return {accepted:true,record:record(prior)};
   }
   if(String(prior?.revision??0)!==input.base)return {accepted:false,record:prior?record(prior):null};
   const rows=prior?await q.query(`UPDATE marvel_lobby.private_archive SET payload=$3,nonce=$4,revision=DEFAULT,updated_at=now()
    WHERE user_id=$1 AND record_key=$2 RETURNING *`,[who.userId,input.key,input.payload,input.nonce]):await q.query(`INSERT INTO marvel_lobby.private_archive(user_id,record_key,scope,payload,nonce) VALUES($1,$2,$3,$4,$5) RETURNING *`,[who.userId,input.key,input.scope,input.payload,input.nonce]);
   return {accepted:true,record:record(rows[0]!)};
  });
 }
}
