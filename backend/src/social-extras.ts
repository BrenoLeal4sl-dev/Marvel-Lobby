import type {Accounts} from './accounts.js';
import type {Principal} from './contracts.js';
import {iso} from './contracts.js';
import type {Query,Row} from './database.js';
import {ApiError,invalid} from './errors.js';
import {FAVORITE_TYPES,catalogImage} from './public-favorites.js';

export interface SharedContent {type:string;id:number;name:string;imageUrl:string|null}
export function sharedContent(value:SharedContent|undefined):SharedContent|null {
 if(value===undefined)return null;
 if(!FAVORITE_TYPES.includes(value.type as typeof FAVORITE_TYPES[number]) || !Number.isInteger(value.id) || value.id<1 || value.id>2147483647 ||
  Array.from(value.name.trim()).length<1 || Array.from(value.name.trim()).length>200)invalid();
 return {type:value.type,id:value.id,name:value.name.trim(),imageUrl:catalogImage(value.imageUrl)};
}
export async function extrasReady(query:Query) {
 if(!(await query.query('SELECT version FROM marvel_lobby.schema_migrations WHERE version=5')).length)
  throw new ApiError(503,'SOCIAL_NOT_READY','The community update is not installed yet.');
}
function person(row:Row) {return {id:row.actor_id,name:row.display_name,username:row.username,bio:row.bio,
 avatarId:row.avatar_id==null?null:Number(row.avatar_id),joinedAt:iso(row.joined_at)};}
// Invoker queries respect message RLS. No notification stores a copy of a private message.
const events=`WITH events AS (
 SELECT 'follow:'||f.follower_id::text||':'||extract(epoch FROM f.created_at)::text AS event_key,
 'follow' AS kind,f.follower_id AS actor_id,f.created_at AS happened_at,NULL::uuid AS conversation_id,NULL::bigint AS message_id,
 false AS conversation_read FROM marvel_lobby.follows f WHERE f.followed_id=$1
 UNION ALL
 SELECT 'message:'||m.id::text,'message',m.sender_id,m.sent_at,m.conversation_id,m.id,
 m.id<=coalesce(r.last_read_id,0) FROM marvel_lobby.direct_messages m
 LEFT JOIN marvel_lobby.direct_reads r ON r.conversation_id=m.conversation_id AND r.user_id=$1 WHERE m.sender_id<>$1
), visible AS (SELECT e.*,p.*,n.read_at IS NOT NULL OR e.conversation_read AS is_read FROM events e
 JOIN marvel_lobby.public_profiles p ON p.id=e.actor_id LEFT JOIN marvel_lobby.notification_reads n ON n.user_id=$1 AND n.event_key=e.event_key)
`;
export class SocialExtras {
 constructor(private readonly accounts:Accounts,private readonly notify:(users:string[],event:{type:'community'})=>void) {}
 private run<T>(who:Principal,action:(q:Query)=>Promise<T>) {return this.accounts.authorized(who,async q=>{await extrasReady(q);return action(q);});}
 privacy(who:Principal) {return this.run(who,async q=>({enabled:(await q.query('SELECT share_activity FROM marvel_lobby.preferences WHERE user_id=$1',[who.userId]))[0]?.share_activity===true}));}
 async share(who:Principal,enabled:boolean) {
  const result=await this.run(who,async q=> {
   await q.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',[`favorites:${who.userId}`]);
   await q.query('UPDATE marvel_lobby.preferences SET share_activity=$2 WHERE user_id=$1',[who.userId,enabled]);
   if(!enabled)await q.query('DELETE FROM marvel_lobby.activity WHERE user_id=$1',[who.userId]);
   const followers=await q.query('SELECT follower_id FROM marvel_lobby.follows WHERE followed_id=$1',[who.userId]);
   return followers.map(r=>r.follower_id as string);
  });
  this.notify([who.userId,...result],{type:'community'});return {enabled};
 }
 feed(who:Principal,offset:number) {
  if(!Number.isInteger(offset)||offset<0||offset>100000)invalid();
  return this.run(who,async q=> {
   const rows=await q.query(`SELECT a.*,p.*,a.user_id AS actor_id FROM marvel_lobby.activity a JOIN marvel_lobby.public_profiles p ON p.id=a.user_id
    JOIN marvel_lobby.follows f ON f.followed_id=a.user_id AND f.follower_id=$1
    ORDER BY a.created_at DESC,a.user_id,a.resource_type,a.comic_vine_id LIMIT 31 OFFSET $2`,[who.userId,offset]);
   return {items:rows.slice(0,30).map(r=>({actor:person(r),content:{type:r.resource_type,id:r.comic_vine_id,name:r.name,imageUrl:r.image_url},at:iso(r.created_at)})),next:rows.length>30?offset+30:null};
  });
 }
 notifications(who:Principal,offset:number) {
  if(!Number.isInteger(offset)||offset<0||offset>100000)invalid();
  return this.run(who,async q=> {
   const rows=await q.query(events+'SELECT * FROM visible ORDER BY happened_at DESC,event_key DESC LIMIT 31 OFFSET $2',[who.userId,offset]);
   const count=(await q.query(events+'SELECT count(*) AS total FROM visible WHERE NOT is_read',[who.userId]))[0]!;
   return {items:rows.slice(0,30).map(r=>({key:r.event_key,kind:r.kind,actor:person(r),at:iso(r.happened_at),read:r.is_read,conversationId:r.conversation_id})),
    next:rows.length>30?offset+30:null,unread:Number(count.total)};
  });
 }
 async read(who:Principal,keys:string[]) {
  await this.run(who,async q=> {
   await q.query(events+`INSERT INTO marvel_lobby.notification_reads(user_id,event_key)
    SELECT $1,event_key FROM visible WHERE event_key=ANY($2::text[]) ON CONFLICT DO NOTHING`,[who.userId,keys]);
  });
  this.notify([who.userId],{type:'community'});return {ok:true};
 }
}
