import websocket from '@fastify/websocket';
import type { WebSocket } from 'ws';
import type { FastifyInstance,FastifyRequest } from 'fastify';
import type { Accounts } from './accounts.js';
import type { Principal } from './contracts.js';
import { Community,type CommunityEvent,type PageInput } from './community.js';
import { unauthorized,ApiError } from './errors.js';

export async function communityRoutes(app:FastifyInstance,accounts:Accounts) {
  await app.register(websocket,{options:{maxPayload:256}});
  const clients=new Map<string,Set<WebSocket>>(),principals=new WeakMap<FastifyRequest,Principal>();
  const notify=(users:string[],event:CommunityEvent)=> {
    for(const user of new Set(users))for(const socket of clients.get(user)??[]) {
      if(socket.readyState===1)socket.send(JSON.stringify(event),error=>{if(error)socket.terminate();});
    }
  };
  const social=new Community(accounts,notify);
  const auth=async(request:FastifyRequest)=> {
    const value=request.headers.authorization;if(!value?.startsWith('Bearer '))unauthorized();
    return accounts.authenticate(value.slice(7));
  };
  const uuid={type:'string',format:'uuid'};
  const params={type:'object',properties:{id:uuid},required:['id'],additionalProperties:false};
  const page={type:'object',properties:{query:{type:'string',maxLength:81},after:{type:'string',maxLength:24},
    before:{type:'string',maxLength:19},limit:{type:'string',pattern:'^[0-9]{1,2}$'},offset:{type:'string',pattern:'^[0-9]{1,6}$'}},additionalProperties:false};
  app.get<{Querystring:PageInput}>('/v1/community/people',{schema:{querystring:page}},async request=>social.people(await auth(request),request.query));
  app.get<{Params:{id:string}}>('/v1/community/users/:id',{schema:{params}},async request=>social.publicProfile(await auth(request),request.params.id.toLowerCase()));
  for(const direction of ['followers','following'] as const)app.get<{Params:{id:string};Querystring:PageInput}>(`/v1/community/users/:id/${direction}`,
    {schema:{params,querystring:page}},async request=>social.people(await auth(request),request.query,request.params.id.toLowerCase(),direction));
  await app.register(async followApi=> {
    // Older Android clients label an empty POST as a form. Accept only an empty
    // form on this parameter-only action; other endpoints still require JSON.
    followApi.addContentTypeParser('application/x-www-form-urlencoded',{parseAs:'string'},(_request,body,done)=> {
      if(body!==''){done(new ApiError(400,'INVALID_INPUT','This action does not accept form fields.'));return;}
      done(null,undefined);
    });
    followApi.post<{Params:{id:string}}>('/v1/community/users/:id/follow',{schema:{params}},async request=>social.follow(await auth(request),request.params.id.toLowerCase(),true));
  });
  app.delete<{Params:{id:string}}>('/v1/community/users/:id/follow',{schema:{params}},async request=>social.follow(await auth(request),request.params.id.toLowerCase(),false));
  app.post<{Body:{userId:string}}>('/v1/community/conversations',{schema:{body:{type:'object',properties:{userId:uuid},required:['userId'],additionalProperties:false}}},
    async request=>social.open(await auth(request),request.body.userId.toLowerCase()));
  app.get<{Querystring:PageInput}>('/v1/community/conversations',{schema:{querystring:page}},async request=>social.inbox(await auth(request),request.query));
  app.get<{Params:{id:string};Querystring:PageInput}>('/v1/community/conversations/:id/messages',{schema:{params,querystring:page}},
    async request=>social.messages(await auth(request),request.params.id.toLowerCase(),request.query));
  app.post<{Params:{id:string};Body:{text:string;clientId:string}}>('/v1/community/conversations/:id/messages',
    {config:{rateLimit:{max:40,timeWindow:'1 minute'}},schema:{params,body:{type:'object',properties:{text:{type:'string',minLength:1,maxLength:4000},clientId:uuid},required:['text','clientId'],additionalProperties:false}}},
    async(request,reply)=>reply.code(201).send(await social.send(await auth(request),request.params.id.toLowerCase(),request.body.text,request.body.clientId.toLowerCase())));
  app.post<{Params:{id:string};Body:{lastId:string}}>('/v1/community/conversations/:id/read',
    {schema:{params,body:{type:'object',properties:{lastId:{type:'string',pattern:'^[0-9]{1,19}$'}},required:['lastId'],additionalProperties:false}}},
    async request=>social.read(await auth(request),request.params.id.toLowerCase(),request.body.lastId));
  app.get('/v1/community/live',{websocket:true,preValidation:async request=> {
    const principal=await auth(request);await social.ready(principal);principals.set(request,principal);
  }},(socket,request)=> {
    const principal=principals.get(request)!;
    const set=clients.get(principal.userId)??new Set<WebSocket>();
    if(set.size>=6){socket.close(1008,'Too many connections');return;}
    set.add(socket);clients.set(principal.userId,set);
    let alive=true,checking=false;
    socket.on('pong',()=>{alive=true;});
    socket.on('message',()=>socket.close(1008,'Use HTTPS endpoints to send messages'));
    socket.on('error',()=>socket.terminate());
    const heartbeat=setInterval(()=> {
      if(!alive){socket.terminate();return;}alive=false;socket.ping();
      if(!checking){checking=true;void auth(request).catch(()=>socket.close(4401,'Session ended')).finally(()=>{checking=false;});}
    },30_000);heartbeat.unref();
    socket.on('close',()=>{clearInterval(heartbeat);set.delete(socket);if(!set.size)clients.delete(principal.userId);});
    socket.send(JSON.stringify({type:'ready'}));
  });
  app.addHook('preClose',async()=>{for(const set of clients.values())for(const socket of set)socket.terminate();clients.clear();});
}
