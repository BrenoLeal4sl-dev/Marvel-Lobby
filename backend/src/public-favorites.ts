import type {Accounts} from './accounts.js';
import type {Principal} from './contracts.js';
import type {Query} from './database.js';
import {ApiError,invalid} from './errors.js';

export const FAVORITE_TYPES=['character','power','team','story_arc'] as const;
export interface FavoriteInput {type:string;id:number;name:string;imageUrl:string|null;favorite:boolean}
function image(value:string|null):string|null {
  if(value===null)return null;
  try {
    const url=new URL(value);
    if(url.protocol==='https:' && !url.username && !url.password && !url.port &&
      /^(comicvine\.gamespot\.com|comicvine\d*\.cbsistatic\.com|static\.comicvine\.com)$/.test(url.hostname))return url.href;
  } catch { /* Invalid images are omitted; the catalog identity remains usable. */ }
  return null;
}
export class PublicFavorites {
  constructor(private readonly accounts:Accounts) {}
  private run<T>(who:Principal,action:(query:Query)=>Promise<T>) {
    return this.accounts.authorized(who,async query=> {
      if(!(await query.query('SELECT version FROM marvel_lobby.schema_migrations WHERE version=4')).length)
        throw new ApiError(503,'FAVORITES_NOT_READY','The favorites database migration has not been installed.');
      return action(query);
    });
  }
  status(who:Principal) {return this.run(who,async query=> {
    const row=(await query.query('SELECT show_favorites FROM marvel_lobby.preferences WHERE user_id=$1',[who.userId]))[0];
    return {enabled:row?.show_favorites===true};
  });}
  share(who:Principal,enabled:boolean) {return this.run(who,async query=> {
    await query.query('UPDATE marvel_lobby.preferences SET show_favorites=$2 WHERE user_id=$1',[who.userId,enabled]);
    return {enabled};
  });}
  save(who:Principal,items:FavoriteInput[]) {
    const keys=new Set<string>();
    for(const item of items) {
      if(!FAVORITE_TYPES.includes(item.type as typeof FAVORITE_TYPES[number]) || !Number.isInteger(item.id) || item.id<1 || item.id>2147483647 ||
        Array.from(item.name.trim()).length<1 || Array.from(item.name.trim()).length>200 || keys.has(`${item.type}:${item.id}`))invalid();
      keys.add(`${item.type}:${item.id}`);
    }
    return this.run(who,async query=> {
      await query.query('SELECT pg_advisory_xact_lock(hashtextextended($1,0))',[`favorites:${who.userId}`]);
      for(const item of items) {
        if(item.favorite)await query.query(`INSERT INTO marvel_lobby.public_favorites(user_id,resource_type,comic_vine_id,name,image_url)
          VALUES($1,$2,$3,$4,$5) ON CONFLICT(user_id,resource_type,comic_vine_id) DO UPDATE SET name=excluded.name,image_url=excluded.image_url`,
          [who.userId,item.type,item.id,item.name.trim(),image(item.imageUrl)]);
        else await query.query('DELETE FROM marvel_lobby.public_favorites WHERE user_id=$1 AND resource_type=$2 AND comic_vine_id=$3',[who.userId,item.type,item.id]);
      }
      return {ok:true};
    });
  }
  list(who:Principal,id:string,type:string,offset:number,size:number) {
    if(!FAVORITE_TYPES.includes(type as typeof FAVORITE_TYPES[number]) || !Number.isInteger(offset) || offset<0 || offset>100000 ||
      !Number.isInteger(size) || size<1 || size>20)invalid();
    return this.run(who,async query=> {
      if(!(await query.query('SELECT id FROM marvel_lobby.public_profiles WHERE id=$1',[id])).length)
        throw new ApiError(404,'USER_NOT_FOUND','This profile is unavailable.');
      const visible=(await query.query('SELECT user_id FROM marvel_lobby.favorite_sharing_profiles WHERE user_id=$1',[id])).length>0;
      if(!visible)return {visible:false,items:[],next:null};
      const rows=await query.query(`SELECT resource_type,comic_vine_id,name,image_url FROM marvel_lobby.public_favorites
        WHERE user_id=$1 AND resource_type=$2 ORDER BY created_at DESC,comic_vine_id DESC LIMIT $3 OFFSET $4`,[id,type,size+1,offset]);
      return {visible:true,items:rows.slice(0,size).map(row=>({type:row.resource_type,id:Number(row.comic_vine_id),name:row.name,imageUrl:row.image_url})),
        next:rows.length>size?offset+size:null};
    });
  }
}
