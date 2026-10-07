import Fastify, { LogController } from 'fastify';
import rateLimit from '@fastify/rate-limit';
import type { FastifyRequest } from 'fastify';
import type { Database } from './database.js';
import { Accounts } from './accounts.js';
import { ScryptPasswords, AVATARS, type Passwords } from './security.js';
import { ApiError, unauthorized } from './errors.js';
import type { RegisterInput, ProfileInput, CredentialInput } from './contracts.js';
import { communityRoutes } from './community-routes.js';
import {RIFT_VERSION} from './rift.js';
import {PrivateSync,type ArchiveInput} from './private-sync.js';

const string=(minLength=1,maxLength=128)=>({type:'string',minLength,maxLength});
const profileFields={name:string(2,80),username:string(3,25),bio:string(0,560),avatarId:{type:['integer','null'],enum:[...AVATARS,null]}};
const object=(properties: Record<string,unknown>,required: string[])=>({type:'object',properties,required,additionalProperties:false});
const limited={rateLimit:{max:10,timeWindow:'1 minute'}};
export async function createApp(db: Database,options: {passwords?:Passwords;logger?:boolean;trustProxy?:false|string[];limit?:number}={}) {
  const app=Fastify({logger:options.logger??false,bodyLimit:12_000,trustProxy:options.trustProxy??false,
    ajv:{customOptions:{removeAdditional:false,coerceTypes:false}},logController:new LogController({disableRequestLogging:true})});
  await app.register(rateLimit,{max:options.limit??120,timeWindow:'1 minute'});
  const accounts=new Accounts(db,options.passwords??new ScryptPasswords());
  await accounts.initialize();
  app.addHook('onRequest',async (_request,reply)=> {
    reply.header('Cache-Control','no-store').header('X-Content-Type-Options','nosniff');
  });
  app.setErrorHandler((error,request,reply)=> {
    if(error instanceof ApiError) return reply.code(error.status).send({error:{code:error.code,message:error.message,...(error.field?{field:error.field}:{})}});
    const failure=error as {validation?:unknown;code?:string;constraint?:string;statusCode?:number};
    if(failure.validation) {
      const validation=failure.validation as Array<{instancePath?:string;params?:{missingProperty?:string}}>;
      const candidate=validation[0]?.params?.missingProperty ?? validation[0]?.instancePath?.split('/').pop();
      const field=candidate && ['name','username','email','password'].includes(candidate)?candidate:undefined;
      return reply.code(400).send({error:{code:'INVALID_INPUT',message:'Check the supplied fields.',...(field?{field}:{})}});
    }
    if(failure.code==='23505') {
      const username=failure.constraint==='profiles_username_unique';
      return reply.code(409).send({error:{code:username?'USERNAME_TAKEN':'EMAIL_TAKEN',message:username?'That username is already in use.':'An account with this email already exists.'}});
    }
    if(failure.statusCode===429) return reply.code(429).send({error:{code:'RATE_LIMITED',message:'Too many attempts. Please try again later.'}});
    if(failure.statusCode===413) return reply.code(413).send({error:{code:'INVALID_INPUT',message:'The request is too large.'}});
    if(failure.statusCode===400) return reply.code(400).send({error:{code:'INVALID_INPUT',message:'Check the supplied fields.'}});
    if(failure.statusCode===415) return reply.code(415).send({error:{code:'INVALID_INPUT',message:'Use the supported request format.'}});
    // Do not log SQL, bodies, passwords, tokens or connection strings.
    app.log.error({failureCode:failure.code??'INTERNAL',requestId:request.id},'API operation failed');
    return reply.code(500).send({error:{code:'SERVICE_UNAVAILABLE',message:'The service could not complete this request. Please try again.'}});
  });
  const principal=async (request:FastifyRequest)=> {
    const authorization=request.headers.authorization;
    if(!authorization?.startsWith('Bearer ')) unauthorized();
    return accounts.authenticate(authorization.slice(7));
  };
  await communityRoutes(app,accounts);
  const archive=new PrivateSync(accounts);
  app.get('/v1/archive/settings',async request=>archive.status(await principal(request)));
  app.put<{Body:{enabled:boolean}}>('/v1/archive/settings',{schema:{body:object({enabled:{type:'boolean'}},['enabled'])}},async request=>archive.choose(await principal(request),request.body.enabled));
  app.get<{Querystring:{after?:string}}>('/v1/archive',{schema:{querystring:object({after:{type:'string',pattern:'^[0-9]{1,19}$'}},[])}},async request=>archive.pull(await principal(request),request.query.after??'0'));
  app.put<{Body:ArchiveInput}>('/v1/archive',{bodyLimit:1_000_000,schema:{body:object({key:string(1,100),scope:{type:'string',enum:['history','chat']},base:{type:'string',pattern:'^[0-9]{1,19}$'},nonce:{type:'string',format:'uuid'},payload:{type:['object','null']}},['key','scope','base','nonce','payload'])}},async request=>archive.put(await principal(request),request.body));
  app.get('/health',async()=>({status:'ok',service:'Marvel Lobby',riftVersion:RIFT_VERSION}));
  app.post<{Body:RegisterInput}>('/v1/auth/register',{config:limited,schema:{body:object({
    ...profileFields,email:string(3,254),password:string(8,128)},['name','username','email','password'])}},
    async(request,reply)=>reply.code(201).send(await accounts.register(request.body)));
  app.post<{Body:{email:string;password:string}}>('/v1/auth/login',{config:limited,schema:{body:object({email:string(3,254),password:string(1,128)},['email','password'])}},
    async request=>accounts.login(request.body.email,request.body.password));
  app.post<{Body:{refreshToken:string}}>('/v1/auth/refresh',{config:limited,schema:{body:object({refreshToken:string(43,43)},['refreshToken'])}},
    async request=>accounts.refresh(request.body.refreshToken));
  app.post('/v1/auth/logout',async(request,reply)=> { await accounts.logout(await principal(request));return reply.code(204).send(); });
  app.get('/v1/me',async request=>accounts.me(await principal(request)));
  app.patch<{Body:{bio:string}}>('/v1/me/bio',{schema:{body:object({bio:string(0,560)},['bio'])}},
    async request=>accounts.bio(await principal(request),request.body.bio));
  app.patch<{Body:ProfileInput}>('/v1/me',{schema:{body:object(profileFields,['name','username','bio','avatarId'])}},
    async request=>accounts.update(await principal(request),request.body));
  app.patch<{Body:CredentialInput}>('/v1/me/credentials',{config:limited,schema:{body:object({email:string(3,254),currentPassword:string(1,128),newPassword:string(0,128),profile:object(profileFields,['name','username','bio','avatarId'])},['email','currentPassword'])}},
    async request=>accounts.credentials(await principal(request),request.body));
  app.get<{Params:{id:string}}>('/v1/users/:id',{schema:{params:object({id:{type:'string',format:'uuid'}},['id'])}},
    async request=>accounts.publicProfile(await principal(request),request.params.id));
  return app;
}
