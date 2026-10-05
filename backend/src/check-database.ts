import { loadConfig } from './config.js';
import { checkDatabase } from './database.js';

async function main() {
  const config=loadConfig();
  try {
    await checkDatabase(config.database);
    console.log('Connection verified: TLS certificate, restricted service user, Phase 1 schema and public profile permissions.');
  } finally { await config.database.close(); }
}
main().catch(error=> {
  const code=(error as {code?:string}).code;
  console.error(code?`Database check failed (${code}). Verify network and run 002_runtime_permissions.sql as administrator.`:error instanceof Error?error.message:'Database check failed.');
  process.exitCode=1;
});
