import { loadConfig } from './config.js';
import { checkDatabase } from './database.js';
import { createApp } from './app.js';

async function main() {
  const config=loadConfig();
  try {
    await checkDatabase(config.database);
    const app=await createApp(config.database,{logger:true,trustProxy:config.trustProxy});
    app.addHook('onClose',async()=>config.database.close());
    for(const signal of ['SIGINT','SIGTERM']) process.once(signal,()=>void app.close());
    await app.listen({host:config.host,port:config.port});
  } catch(error) { await config.database.close();throw error; }
}
main().catch(error=> {
  // Config errors are ours; database/network errors may include private connection details.
  const code=(error as {code?:string}).code;
  console.error(code?`Backend could not start (${code}). Check database connectivity and permissions.`:error instanceof Error?error.message:'Backend could not start.');
  process.exitCode=1;
});
