import type {FastifyInstance,FastifyRequest} from 'fastify';
import type {Accounts} from './accounts.js';
import {unauthorized} from './errors.js';
import {Rift,RIFT_HEROES,type RiftResult} from './rift.js';
const uuid={type:'string',format:'uuid'};
const version={type:'string',enum:['rift-2','rift-3']};
const object=(properties:Record<string,unknown>,required:string[]=[])=>({type:'object',properties,required,additionalProperties:false});
const page=object({offset:{type:'string',pattern:'^[0-9]{1,6}$'},id:uuid});
export async function riftRoutes(app:FastifyInstance,accounts:Accounts,notify:(users:string[])=>void=()=>{}) {
 const rift=new Rift(accounts,notify);
 const auth=async(r:FastifyRequest)=> {if(!r.headers.authorization?.startsWith('Bearer '))unauthorized();return accounts.authenticate(r.headers.authorization.slice(7));};
 app.post<{Body:{clientId:string;challengeId?:string;character?:string;version?:string}}>('/v1/rift/sessions',{config:{rateLimit:{max:15,timeWindow:'1 minute'}},schema:{body:object({clientId:uuid,challengeId:uuid,version,character:{type:'string',enum:RIFT_HEROES}},['clientId'])}},async r=>rift.session(await auth(r),r.body.clientId,r.body.challengeId,r.body.character,r.body.version));
 const result=object({seed:{type:'integer'},character:{type:'string',enum:RIFT_HEROES},version:{type:'string',enum:['rift-1','rift-2','rift-3']},duration:{type:'number'},kills:{type:'integer'},elites:{type:'integer'},bosses:{type:'integer'},damage:{type:'number'},maxCombo:{type:'integer'},level:{type:'integer'},score:{type:'integer'},extracted:{type:'boolean'},upgrades:{type:'object',maxProperties:12,additionalProperties:{type:'integer'}}},['seed','character','version','duration','kills','elites','bosses','damage','maxCombo','level','score','extracted','upgrades']);
 app.post<{Params:{id:string};Body:RiftResult}>('/v1/rift/sessions/:id/result',{schema:{params:object({id:uuid},['id']),body:result}},async r=>rift.submit(await auth(r),r.params.id,r.body));
 app.post<{Params:{id:string}}>('/v1/rift/sessions/:id/abandon',{schema:{params:object({id:uuid},['id'])}},async r=>rift.abandon(await auth(r),r.params.id));
 app.get<{Querystring:{mode?:string;offset?:string;character?:string;version?:string}}>('/v1/rift/ranking',{schema:{querystring:object({version,mode:{type:'string',enum:['global','friends','weekly','character']},offset:{type:'string',pattern:'^[0-9]{1,6}$'},character:{type:'string',enum:RIFT_HEROES}})}},async r=>rift.leaderboard(await auth(r),r.query.mode??'global',Number(r.query.offset??0),r.query.character,r.query.version));
 app.get<{Params:{id:string};Querystring:{version?:string}}>('/v1/rift/users/:id',{schema:{params:object({id:uuid},['id']),querystring:object({version})}},async r=>rift.profile(await auth(r),r.params.id,r.query.version));
 app.post<{Body:{userId:string;clientId:string;character?:string;version?:string}}>('/v1/rift/challenges',{config:{rateLimit:{max:10,timeWindow:'1 minute'}},schema:{body:object({userId:uuid,clientId:uuid,version,character:{type:'string',enum:RIFT_HEROES}},['userId','clientId'])}},async r=>rift.challenge(await auth(r),r.body.userId,r.body.clientId,r.body.character,r.body.version));
 app.get<{Querystring:{offset?:string;id?:string}}>('/v1/rift/challenges',{schema:{querystring:page}},async r=>rift.challenges(await auth(r),Number(r.query.offset??0),r.query.id));
}
