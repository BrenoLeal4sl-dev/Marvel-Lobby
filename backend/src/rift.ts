import {randomInt,randomUUID} from 'node:crypto';
import type {Accounts} from './accounts.js';
import type {Principal} from './contracts.js';
import type {Query,Row} from './database.js';
import {ApiError,invalid} from './errors.js';

export const RIFT_VERSION='rift-2';
const caps:Record<string,number>={DAMAGE:4,SPEED:3,ATTACK_SPEED:3,HEALTH:3,ARMOR:3,DASH:3,CRITICAL:3,PIERCE:2,RICOCHET:1,EXPLOSION:1,SLOW:1,SPECIAL:3};
export interface RiftResult {seed:number;character:string;duration:number;kills:number;elites:number;bosses:number;damage:number;maxCombo:number;level:number;score:number;extracted:boolean;upgrades:Record<string,number>;version:string}
export function calculateScore(r:RiftResult) { return Math.floor(r.duration)*10+r.kills*40+r.elites*150+r.bosses*1200+Math.floor(Math.floor(Math.min(r.damage,200000))/5)+Math.min(r.maxCombo,200)*20; }
export interface RiftShareInput {kind:'result'|'challenge';id:string}
export async function resolveRiftShare(q:Query,userId:string,input:RiftShareInput) {
 if(!(await q.query('SELECT version FROM marvel_lobby.schema_migrations WHERE version=7')).length)throw new ApiError(503,'ARENA_NOT_READY','Install the Rift Arena migration.');
 if(input.kind==='result') {
  const row=(await q.query('SELECT score,character_key FROM marvel_lobby.arena_runs WHERE session_id=$1 AND user_id=$2',[input.id,userId]))[0];
  if(!row)throw new ApiError(404,'ARENA_SESSION_MISSING','Only your validated results can be shared.');
  return {kind:input.kind,id:input.id,score:row.score,character:row.character_key};
 }
 const row=(await q.query('SELECT id FROM marvel_lobby.arena_challenges WHERE id=$1 AND expires_at>now()',[input.id]))[0];
 if(!row)throw new ApiError(404,'CHALLENGE_UNAVAILABLE','This challenge is unavailable.');
 return {kind:input.kind,id:input.id,score:null,character:'spider-man'};
}
export class Rift {
 constructor(private readonly accounts:Accounts,private readonly notify:(users:string[])=>void=()=>{}) {}
 private async run<T>(p:Principal,action:(q:Query)=>Promise<T>) {return this.accounts.authorized(p,async q=> {
  if(!(await q.query('SELECT version FROM marvel_lobby.schema_migrations WHERE version=7')).length)throw new ApiError(503,'ARENA_NOT_READY','Install the Rift Arena migration.');
  return action(q);
 });}
 async session(p:Principal,clientId:string,challengeId?:string) {return this.run(p,async q=> {
  await q.query('SELECT pg_advisory_xact_lock(hashtextextended($1,77))',[p.userId]);
  const existing=(await q.query('SELECT * FROM marvel_lobby.arena_sessions WHERE user_id=$1 AND client_id=$2',[p.userId,clientId]))[0];
  if(existing) { if(existing.abandoned_at||(existing.challenge_id??undefined)!==challengeId)throw new ApiError(409,'ARENA_CONFLICT','Session retry changed its challenge.');return this.sessionDto(existing); }
  let seed=randomInt(-2147483648,2147483647);
  if(challengeId) {
   const challenge=(await q.query('SELECT * FROM marvel_lobby.arena_challenges WHERE id=$1 AND expires_at>now()',[challengeId]))[0];
   if(!challenge||challenge.game_version!==RIFT_VERSION)throw new ApiError(404,'CHALLENGE_UNAVAILABLE','This challenge is unavailable.');
   const prior=(await q.query('SELECT * FROM marvel_lobby.arena_sessions WHERE user_id=$1 AND challenge_id=$2',[p.userId,challengeId]))[0];
   if(prior) {
    if(prior.abandoned_at||(await q.query('SELECT session_id FROM marvel_lobby.arena_runs WHERE session_id=$1',[prior.id])).length)throw new ApiError(409,'CHALLENGE_ALREADY_PLAYED','Your attempt has already been used.');
    return this.sessionDto(prior);
   }seed=challenge.seed;
  }
  const active=(await q.query(`SELECT id FROM marvel_lobby.arena_sessions s WHERE user_id=$1 AND abandoned_at IS NULL AND expires_at>now() AND started_at>now()-interval '12 minutes'
   AND NOT EXISTS(SELECT 1 FROM marvel_lobby.arena_runs r WHERE r.session_id=s.id)`,[p.userId]))[0];
  if(active)throw new ApiError(409,'ARENA_ACTIVE','Finish the previous competitive run or play offline practice.');
  const row=(await q.query(`INSERT INTO marvel_lobby.arena_sessions(id,user_id,client_id,seed,character_key,game_version,challenge_id,expires_at)
   VALUES($1,$2,$3,$4,'spider-man',$5,$6,now()+interval '24 hours') RETURNING *`,[randomUUID(),p.userId,clientId,seed,RIFT_VERSION,challengeId??null]))[0]!;
  return this.sessionDto(row);
 });}
 private sessionDto(row:Row) {return {id:row.id,seed:row.seed,character:row.character_key,version:row.game_version,startedAt:row.started_at,expiresAt:row.expires_at,challengeId:row.challenge_id};}
 async submit(p:Principal,id:string,r:RiftResult) {const response=await this.run(p,async q=> {
  await q.query('SELECT pg_advisory_xact_lock(hashtextextended($1,77))',[p.userId]);
  const session=(await q.query('SELECT *,extract(epoch FROM now()-started_at) AS wall FROM marvel_lobby.arena_sessions WHERE id=$1 AND user_id=$2 FOR UPDATE',[id,p.userId]))[0];
  if(!session)throw new ApiError(404,'ARENA_SESSION_MISSING','This run does not belong to your account.');
  if(session.abandoned_at)throw new ApiError(409,'ARENA_CONFLICT','This session was abandoned.');
  if(!['rift-1',RIFT_VERSION].includes(r.version)||r.version!==session.game_version||r.character!==session.character_key||r.seed!==session.seed)invalid('The run does not match its issued session.');
  this.validate(r,Number(session.wall));
  const existing=(await q.query('SELECT * FROM marvel_lobby.arena_runs WHERE session_id=$1',[id]))[0];
  if(existing) {
   const same=existing.score===r.score&&Math.abs(Number(existing.duration)-r.duration)<0.001&&existing.kills===r.kills&&existing.elites===r.elites&&existing.bosses===r.bosses&&existing.max_combo===r.maxCombo&&existing.level===r.level&&Math.abs(Number(existing.damage)-r.damage)<0.05&&existing.extracted===r.extracted&&Object.keys(caps).every(k=>(existing.upgrades[k]??0)===(r.upgrades[k]??0));
   if(!same)throw new ApiError(409,'ARENA_CONFLICT','This session already has a different result.');return {id,score:existing.score,accepted:true};
  }
  if(new Date(session.expires_at).getTime()<Date.now())throw new ApiError(409,'ARENA_EXPIRED','This competitive session expired. Your local result is preserved.');
  const prior=(await q.query('SELECT coalesce(max(score),0) AS best,coalesce(sum(bosses),0) AS bosses FROM marvel_lobby.arena_runs WHERE user_id=$1 AND game_version=$2',[p.userId,r.version]))[0]!;
  await q.query(`INSERT INTO marvel_lobby.arena_runs(session_id,user_id,character_key,game_version,score,duration,kills,elites,bosses,damage,max_combo,level,extracted,upgrades)
   VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)`,[id,p.userId,r.character,r.version,calculateScore(r),r.duration,r.kills,r.elites,r.bosses,r.damage,r.maxCombo,r.level,r.extracted,JSON.stringify(r.upgrades)]);
  await q.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',[`favorites:${p.userId}`]);
  const sharing=(await q.query('SELECT share_activity FROM marvel_lobby.preferences WHERE user_id=$1',[p.userId]))[0]?.share_activity;
  if(sharing)for(const kind of [...(r.score>Number(prior.best)?['record']:[]),...(r.bosses>0&&Number(prior.bosses)===0?['first_boss']:[])])
   await q.query('INSERT INTO marvel_lobby.arena_achievements(user_id,session_id,kind,score) VALUES($1,$2,$3,$4)',[p.userId,id,kind,r.score]);
  return {id,score:calculateScore(r),accepted:true};
 });const followers=await this.run(p,q=>q.query('SELECT follower_id FROM marvel_lobby.follows WHERE followed_id=$1',[p.userId]));this.notify([p.userId,...followers.map(r=>String(r.follower_id))]);return response;}
 async abandon(p:Principal,id:string) {return this.run(p,async q=> {
  await q.query('UPDATE marvel_lobby.arena_sessions SET abandoned_at=coalesce(abandoned_at,now()) WHERE id=$1 AND user_id=$2 AND NOT EXISTS(SELECT 1 FROM marvel_lobby.arena_runs WHERE session_id=$1)',[id,p.userId]);
  return {ok:true};
 });}
 private validate(r:RiftResult,wall:number) {
  for(const key of ['duration','damage','seed','kills','elites','bosses','maxCombo','level','score'] as const)if(!Number.isFinite(r[key]))invalid();
  for(const key of ['seed','kills','elites','bosses','maxCombo','level','score'] as const)if(!Number.isInteger(r[key]))invalid();
  if(r.duration<0||r.duration>600.01||r.duration>wall+3||r.damage<0||r.damage>1000000||r.kills<0||r.elites<0||r.bosses<0||r.bosses>5||r.bosses>Math.floor(r.duration/90)||r.maxCombo<0||r.maxCombo>r.kills||r.elites+r.bosses>r.kills||r.level<1||r.level>80||r.score!==calculateScore(r)||r.extracted!==(r.duration>=600))invalid('The run metrics are inconsistent.');
  // Integrate the deterministic director's bounded spawn budget, rather than trusting kill totals.
  let spawned=0,timer=1,time=0;for(let tick=1;tick<=Math.floor(r.duration*60)+1;tick++) {time=tick/60;timer-=1/60;if(timer<=0){spawned++;timer=Math.max(0.24,1.55-time/145);}}
  if(r.kills>spawned+Math.floor(r.duration/90)||r.elites>Math.max(0,spawned-40)||r.damage>(spawned*600+Math.floor(r.duration/90)*1700))invalid('The run exceeds its spawn budget.');
  const levels=r.level-1;const neededXp=levels*12+4*levels*(levels-1);
  if(neededXp>r.kills*8+r.elites*7+r.bosses*52)invalid('The level exceeds its XP budget.');
  if(!r.upgrades||Object.getPrototypeOf(r.upgrades)!==Object.prototype)invalid();
  let selected=0;for(const [key,value] of Object.entries(r.upgrades)) {if(!caps[key]||!Number.isInteger(value)||value<1||value>caps[key]!)invalid();selected+=value;}
  if(selected!==Math.min(r.level-1,Object.values(caps).reduce((a,b)=>a+b,0)))invalid('Upgrade choices do not match the achieved level.');
 }
 async leaderboard(p:Principal,mode:string,offset:number,character='spider-man') {return this.run(p,async q=> {
  if(!['global','friends','weekly','character'].includes(mode)||!Number.isInteger(offset)||offset<0||offset>100000||character!=='spider-man')invalid();
  const where=`r.game_version=$1 ${mode==='weekly'?"AND r.submitted_at>=date_trunc('week',now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'":''}
   ${mode==='character'?'AND r.character_key=$3':''} ${mode==='friends'?'AND (r.user_id=$2 OR EXISTS(SELECT 1 FROM marvel_lobby.follows f WHERE f.follower_id=$2 AND f.followed_id=r.user_id))':''}`;
  const cte=`WITH best AS(SELECT DISTINCT ON(r.user_id) r.* FROM marvel_lobby.arena_public_runs r WHERE ${where} ORDER BY r.user_id,r.score DESC,r.submitted_at,r.session_id),
   ranked AS(SELECT b.*,row_number() OVER(ORDER BY score DESC,submitted_at,user_id) AS position FROM best b)`;
  // Every mode binds all three common parameters, even when a condition is inactive.
  const fixedCte=cte.replace('WHERE r.game_version=$1','WHERE ($2::uuid IS NOT NULL) AND ($3::text IS NOT NULL) AND r.game_version=$1');
  const rows=await q.query(`${fixedCte} SELECT r.*,p.username,p.display_name,p.avatar_id FROM ranked r JOIN marvel_lobby.public_profiles p ON p.id=r.user_id ORDER BY position LIMIT 31 OFFSET $4`,[RIFT_VERSION,p.userId,character,offset]);
  const own=(await q.query(`${fixedCte} SELECT position,score FROM ranked WHERE user_id=$2`,[RIFT_VERSION,p.userId,character]))[0];
  return {items:rows.slice(0,30).map(r=>({id:r.session_id,userId:r.user_id,username:r.username,name:r.display_name,avatarId:r.avatar_id,score:r.score,position:Number(r.position),character:r.character_key})),next:rows.length>30?offset+30:null,own:own?{position:Number(own.position),score:own.score}:null};
 });}
 async profile(p:Principal,id:string) {return this.run(p,async q=> {
  const row=(await q.query(`SELECT count(*) AS runs,coalesce(max(score),0) AS best,coalesce(max(duration),0) AS survival,coalesce(sum(kills),0) AS kills,coalesce(sum(bosses),0) AS bosses
   FROM marvel_lobby.arena_public_runs WHERE user_id=$1 AND game_version=$2`,[id,RIFT_VERSION]))[0]!;
  const position=(await q.query(`WITH best AS(SELECT DISTINCT ON(user_id) user_id,score,submitted_at FROM marvel_lobby.arena_public_runs WHERE game_version=$2 ORDER BY user_id,score DESC,submitted_at,session_id),
   ranked AS(SELECT user_id,row_number() OVER(ORDER BY score DESC,submitted_at,user_id) AS position FROM best) SELECT position FROM ranked WHERE user_id=$1`,[id,RIFT_VERSION]))[0];
  return {runs:Number(row.runs),best:Number(row.best),survival:Number(row.survival),kills:Number(row.kills),bosses:Number(row.bosses),globalPosition:position?Number(position.position):null,main:Number(row.runs)?'spider-man':null};
 });}
 async challenge(p:Principal,target:string,clientId:string) {return this.run(p,async q=> {
  await q.query('SELECT pg_advisory_xact_lock(hashtextextended($1,77))',[p.userId]);
  if(target===p.userId)invalid();
  if(!(await q.query('SELECT id FROM marvel_lobby.public_profiles WHERE id=$1',[target])).length)throw new ApiError(404,'USER_NOT_FOUND','This profile is unavailable.');
  if(!(await q.query('SELECT 1 FROM marvel_lobby.follows WHERE follower_id=$1 AND followed_id=$2',[p.userId,target])).length)throw new ApiError(409,'ARENA_FOLLOW_REQUIRED','Follow this player before challenging them.');
  const existing=(await q.query('SELECT * FROM marvel_lobby.arena_challenges WHERE challenger_id=$1 AND client_id=$2',[p.userId,clientId]))[0];
  if(existing) {if(existing.challenged_id!==target)throw new ApiError(409,'ARENA_CONFLICT','Challenge retry changed its player.');return existing;}
  return (await q.query(`INSERT INTO marvel_lobby.arena_challenges(id,challenger_id,challenged_id,client_id,seed,character_key,game_version,expires_at)
   VALUES($1,$2,$3,$4,$5,'spider-man',$6,now()+interval '7 days') RETURNING *`,[randomUUID(),p.userId,target,clientId,randomInt(-2147483648,2147483647),RIFT_VERSION]))[0];
 });}
 async challenges(p:Principal,offset:number,id?:string) {return this.run(p,async q=> {
  const rows=await q.query(`SELECT c.*,a.username AS challenger,b.username AS challenged,
   (SELECT r.score FROM marvel_lobby.arena_public_runs r WHERE r.challenge_id=c.id AND r.user_id=$1) AS own_score,
   (SELECT r.score FROM marvel_lobby.arena_public_runs r WHERE r.challenge_id=c.id AND r.user_id<>$1) AS peer_score,
   EXISTS(SELECT 1 FROM marvel_lobby.arena_sessions s WHERE s.challenge_id=c.id AND s.user_id=$1) AS attempted
   FROM marvel_lobby.arena_challenges c JOIN marvel_lobby.public_profiles a ON a.id=c.challenger_id JOIN marvel_lobby.public_profiles b ON b.id=c.challenged_id
   WHERE ($3::uuid IS NULL OR c.id=$3) ORDER BY c.created_at DESC,c.id LIMIT 31 OFFSET $2`,[p.userId,offset,id??null]);
  return {items:rows.slice(0,30),next:rows.length>30?offset+30:null};
 });}
}
