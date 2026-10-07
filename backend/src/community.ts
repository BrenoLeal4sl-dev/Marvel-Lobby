import type { Query, Row } from './database.js';
import type { Accounts } from './accounts.js';
import type { Principal, PublicProfile } from './contracts.js';
import { iso } from './contracts.js';
import { ApiError, invalid } from './errors.js';
import {sharedContent,extrasReady,type SharedContent} from './social-extras.js';
import {resolveRiftShare,type RiftShareInput} from './rift.js';

export type CommunityEvent={type:'community'|'messages'|'read';conversationId?:string};
export interface PageInput {query?:string;after?:string;before?:string;limit?:string;offset?:string}
function limit(input:PageInput,max=30):number {
  const value=input.limit?Number(input.limit):max;
  if(!Number.isInteger(value)||value<1||value>max)invalid();return value;
}
function sequence(value:string|undefined):string|undefined {
  if(value===undefined)return undefined;
  if(!/^[0-9]{1,19}$/.test(value)||BigInt(value)>9223372036854775807n)invalid();return value;
}
function profile(row:Row):PublicProfile {
  return {id:row.id,name:row.display_name,username:row.username,bio:row.bio,
    avatarId:row.avatar_id==null?null:Number(row.avatar_id),joinedAt:iso(row.joined_at)};
}
function message(row:Row) {
  return {id:String(row.id),conversationId:row.conversation_id,senderId:row.sender_id,
    clientId:row.client_id,text:row.body,sentAt:iso(row.sent_at),shared:row.shared_content??null,rift:row.rift_content??null};
}
export class Community {
  constructor(private readonly accounts:Accounts,private readonly notify:(users:string[],event:CommunityEvent)=>void) {}
  private async run<T>(principal:Principal,action:(query:Query)=>Promise<T>):Promise<T> {
    return this.accounts.authorized(principal,async query=> {
      const version=await query.query('SELECT version FROM marvel_lobby.schema_migrations WHERE version=3');
      if(!version.length)throw new ApiError(503,'COMMUNITY_NOT_READY','The community database migration has not been installed.');
      return action(query);
    });
  }
  async ready(principal:Principal):Promise<void> {await this.run(principal,async()=>{});}
  private async user(query:Query,id:string):Promise<Row> {
    const row=(await query.query('SELECT * FROM marvel_lobby.public_profiles WHERE id=$1',[id]))[0];
    if(!row)throw new ApiError(404,'USER_NOT_FOUND','This profile is unavailable.');return row;
  }
  private async stats(query:Query,viewer:string,id:string) {
    const user=await this.user(query,id);
    const row=(await query.query(`SELECT
      (SELECT count(*) FROM marvel_lobby.follows f JOIN marvel_lobby.public_profiles p ON p.id=f.follower_id WHERE f.followed_id=$1) AS followers,
      (SELECT count(*) FROM marvel_lobby.follows f JOIN marvel_lobby.public_profiles p ON p.id=f.followed_id WHERE f.follower_id=$1) AS following,
      EXISTS(SELECT 1 FROM marvel_lobby.follows WHERE follower_id=$2 AND followed_id=$1) AS is_following,
      EXISTS(SELECT 1 FROM marvel_lobby.follows WHERE follower_id=$1 AND followed_id=$2) AS follows_you`,[id,viewer]))[0]!;
    return {profile:profile(user),followers:Number(row.followers),following:Number(row.following),
      isFollowing:row.is_following,followsYou:row.follows_you,isSelf:viewer===id};
  }
  async publicProfile(principal:Principal,id:string) {return this.run(principal,query=>this.stats(query,principal.userId,id));}
  async people(principal:Principal,input:PageInput,target?:string,direction?:'followers'|'following') {
    const size=limit(input),after=input.after??'',term=(input.query??'').trim().replace(/^@/,'');
    if(term.length>80 || after.length>24)invalid();
    const pattern='%'+term.replace(/[\\%_]/g,'\\$&')+'%';
    return this.run(principal,async query=> {
      if(target)await this.user(query,target);
      const relation=direction==='followers'?'f.followed_id=$4 AND f.follower_id=p.id':'f.follower_id=$4 AND f.followed_id=p.id';
      const rows=await query.query(`SELECT p.* FROM marvel_lobby.public_profiles p
        WHERE p.username>$1 AND (p.username ILIKE $2 ESCAPE '\\' OR p.display_name ILIKE $2 ESCAPE '\\')
        ${target?`AND EXISTS(SELECT 1 FROM marvel_lobby.follows f WHERE ${relation})`:''}
        ORDER BY p.username LIMIT $3`,target?[after,pattern,size+1,target]:[after,pattern,size+1]);
      return {items:rows.slice(0,size).map(profile),next:rows.length>size?rows[size-1]!.username:null};
    });
  }
  async follow(principal:Principal,id:string,add:boolean) {
    if(id===principal.userId)invalid('You cannot follow yourself.');
    const result=await this.run(principal,async query=> {
      await this.user(query,id);
      if(add)await query.query(`INSERT INTO marvel_lobby.follows(follower_id,followed_id) VALUES($1,$2) ON CONFLICT DO NOTHING`,[principal.userId,id]);
      else await query.query('DELETE FROM marvel_lobby.follows WHERE follower_id=$1 AND followed_id=$2',[principal.userId,id]);
      return this.stats(query,principal.userId,id);
    });
    this.notify([principal.userId,id],{type:'community'});return result;
  }
  private async conversation(query:Query,id:string):Promise<Row> {
    const row=(await query.query('SELECT * FROM marvel_lobby.direct_conversations WHERE id=$1',[id]))[0];
    if(!row)throw new ApiError(404,'CONVERSATION_NOT_FOUND','This conversation is unavailable.');return row;
  }
  async open(principal:Principal,target:string) {
    if(principal.userId===target)invalid('Choose another user.');
    return this.run(principal,async query=> {
      const user=await this.user(query,target),[a,b]=[principal.userId,target].sort();
      const rows=await query.query(`INSERT INTO marvel_lobby.direct_conversations(user_a,user_b) VALUES($1,$2)
        ON CONFLICT(user_a,user_b) DO NOTHING RETURNING id`,[a,b]);
      const id=rows[0]?.id??(await query.query('SELECT id FROM marvel_lobby.direct_conversations WHERE user_a=$1 AND user_b=$2',[a,b]))[0]!.id;
      return {id,peer:profile(user)};
    });
  }
  async inbox(principal:Principal,input:PageInput) {
    const size=limit(input),offset=Number(input.offset??0);
    if(!Number.isInteger(offset)||offset<0||offset>100_000)invalid();
    return this.run(principal,async query=> {
      const rows=await query.query(`SELECT c.id AS conversation_id,p.*,last_msg.id AS last_id,last_msg.sender_id AS last_sender,
        last_msg.client_id AS last_client,last_msg.body AS last_body,last_msg.sent_at AS last_at,to_jsonb(last_msg)->'shared_content' AS last_shared,to_jsonb(last_msg)->'rift_content' AS last_rift,
        (SELECT count(*) FROM marvel_lobby.direct_messages m WHERE m.conversation_id=c.id
          AND m.sender_id<>$1 AND m.id>coalesce(r.last_read_id,0)) AS unread
        FROM marvel_lobby.direct_conversations c
        JOIN marvel_lobby.public_profiles p ON p.id=CASE WHEN c.user_a=$1 THEN c.user_b ELSE c.user_a END
        LEFT JOIN marvel_lobby.direct_reads r ON r.conversation_id=c.id AND r.user_id=$1
        LEFT JOIN LATERAL(SELECT * FROM marvel_lobby.direct_messages m WHERE m.conversation_id=c.id ORDER BY m.id DESC LIMIT 1) last_msg ON true
        ORDER BY coalesce(last_msg.sent_at,c.created_at) DESC,c.id DESC LIMIT $2 OFFSET $3`,[principal.userId,size+1,offset]);
      const total=(await query.query(`SELECT count(*) AS unread FROM marvel_lobby.direct_messages m
        LEFT JOIN marvel_lobby.direct_reads r ON r.conversation_id=m.conversation_id AND r.user_id=$1
        WHERE m.sender_id<>$1 AND m.id>coalesce(r.last_read_id,0)`,[principal.userId]))[0]!;
      return {unreadTotal:Number(total.unread),items:rows.slice(0,size).map(row=>({id:row.conversation_id,peer:profile(row),unread:Number(row.unread),
        lastMessage:row.last_id?message({id:row.last_id,conversation_id:row.conversation_id,sender_id:row.last_sender,
          client_id:row.last_client,body:row.last_body,sent_at:row.last_at,shared_content:row.last_shared,rift_content:row.last_rift}):null})),next:rows.length>size?offset+size:null};
    });
  }
  async messages(principal:Principal,id:string,input:PageInput) {
    const size=limit(input,40),after=sequence(input.after),before=sequence(input.before);
    if(after!==undefined && before!==undefined)invalid();
    return this.run(principal,async query=> {
      const conversation=await this.conversation(query,id);
      const peerId=conversation.user_a===principal.userId?conversation.user_b:conversation.user_a;
      const peer=await this.user(query,peerId);
      const rows=await query.query(`SELECT * FROM marvel_lobby.direct_messages WHERE conversation_id=$1
        ${after!==undefined?'AND id>$3':before!==undefined?'AND id<$3':''}
        ORDER BY id ${after!==undefined?'ASC':'DESC'} LIMIT $2`,after!==undefined||before!==undefined?[id,size+1,after??before]:[id,size+1]);
      const selected=rows.slice(0,size);if(after===undefined)selected.reverse();
      const read=(await query.query('SELECT last_read_id FROM marvel_lobby.direct_reads WHERE conversation_id=$1 AND user_id=$2',[id,peerId]))[0];
      return {peer:profile(peer),items:selected.map(message),hasMore:rows.length>size,
        peerLastRead:String(read?.last_read_id??0)};
    });
  }
  async send(principal:Principal,id:string,text:string,clientId:string,shared?:SharedContent,rift?:RiftShareInput) {
    const attachment=sharedContent(shared);
    if(shared&&rift)invalid();
    const body=text.trim();if(Array.from(body).length<1||Array.from(body).length>2000)invalid();
    const result=await this.run(principal,async query=> {
      const conversation=await this.conversation(query,id);
      await this.user(query,conversation.user_a===principal.userId?conversation.user_b:conversation.user_a);
      // Allocate sequence IDs only after the preceding send in this conversation commits.
      // Otherwise an incremental reader could see ID 2 before ID 1 becomes visible.
      await query.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',[id]);
        const arena=rift?await resolveRiftShare(query,principal.userId,rift):null;
      if(attachment)await extrasReady(query);
      const rows=arena?await query.query(`INSERT INTO marvel_lobby.direct_messages(conversation_id,sender_id,client_id,body,rift_content)
        VALUES($1,$2,$3,$4,$5) ON CONFLICT(sender_id,client_id) DO NOTHING RETURNING *`,[id,principal.userId,clientId,body,arena]):attachment?await query.query(`INSERT INTO marvel_lobby.direct_messages(conversation_id,sender_id,client_id,body,shared_content)
        VALUES($1,$2,$3,$4,$5) ON CONFLICT(sender_id,client_id) DO NOTHING RETURNING *`,[id,principal.userId,clientId,body,attachment]):await query.query(`INSERT INTO marvel_lobby.direct_messages(conversation_id,sender_id,client_id,body)
        VALUES($1,$2,$3,$4) ON CONFLICT(sender_id,client_id) DO NOTHING RETURNING *`,[id,principal.userId,clientId,body]);
      const row=rows[0]??(await query.query('SELECT * FROM marvel_lobby.direct_messages WHERE sender_id=$1 AND client_id=$2',[principal.userId,clientId]))[0]!;
      const existing=row.shared_content??null;
      const previousArena=row.rift_content??null;
      if(JSON.stringify(previousArena&&[previousArena.kind,previousArena.id,previousArena.score,previousArena.character])!==JSON.stringify(arena&&[arena.kind,arena.id,arena.score,arena.character]))throw new ApiError(409,'MESSAGE_CONFLICT','This message identifier was already used.');
      if(row.conversation_id!==id||row.body!==body||JSON.stringify(existing && [existing.type,existing.id,existing.name,existing.imageUrl])!==JSON.stringify(attachment && [attachment.type,attachment.id,attachment.name,attachment.imageUrl]))throw new ApiError(409,'MESSAGE_CONFLICT','This message identifier was already used.');
      return {message:message(row),users:[conversation.user_a,conversation.user_b] as string[]};
    });
    this.notify(result.users,{type:'messages',conversationId:id});return result.message;
  }
  async read(principal:Principal,id:string,lastId:string) {
    sequence(lastId);
    const users=await this.run(principal,async query=> {
      const conversation=await this.conversation(query,id);
      if(lastId!=='0' && !(await query.query('SELECT id FROM marvel_lobby.direct_messages WHERE conversation_id=$1 AND id=$2',[id,lastId])).length)invalid();
      await query.query(`INSERT INTO marvel_lobby.direct_reads(conversation_id,user_id,last_read_id) VALUES($1,$2,$3)
        ON CONFLICT(conversation_id,user_id) DO UPDATE SET last_read_id=greatest(marvel_lobby.direct_reads.last_read_id,excluded.last_read_id)`,[id,principal.userId,lastId]);
      return [conversation.user_a,conversation.user_b] as string[];
    });
    this.notify(users,{type:'read',conversationId:id});return {ok:true};
  }
}
