import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseEnv } from 'node:util';
import pg from 'pg';
import { PgDatabase } from './database.js';

export function loadConfig() {
  const configPath = resolve(process.env.BACKEND_ENV_FILE ?? fileURLToPath(new URL('../../../database/.env', import.meta.url)));
  // Run compiled dist/src modules; resolve configuration independently of the working directory.
  let file: Record<string,string|undefined> = {};
  try { file = parseEnv(readFileSync(configPath, 'utf8')); }
  catch(error) { if(!process.env.PGHOST) throw new Error('Configure database/.env or BACKEND_ENV_FILE before starting the API.'); }
  const env = { ...file, ...process.env };
  if(!env.PGHOST || !env.PGUSER || !env.PGPASSWORD || !env.PGDATABASE)
    throw new Error('Fill the PG connection fields in the local configuration.');
  if(env.PGUSER === 'avnadmin') throw new Error('Do not run the API with the administrative database account.');
  if(env.PGSSLMODE !== 'verify-full') throw new Error('PGSSLMODE must be verify-full. Save the Aiven CA certificate locally.');
  const certificatePath = resolve(dirname(configPath), env.PGSSLROOTCERT ?? './secrets/ca.pem');
  let certificate: string;
  try { certificate = readFileSync(certificatePath, 'utf8'); }
  catch { throw new Error('Save the Aiven CA certificate at database/secrets/ca.pem.'); }
  const port = Number(env.PGPORT);
  const httpPort = Number(env.PORT ?? 4100);
  if(!Number.isInteger(port) || port < 1 || port > 65535 || !Number.isInteger(httpPort) || httpPort < 1 || httpPort > 65535)
    throw new Error('Configure valid database and API ports.');
  return {
    host: env.HOST ?? '127.0.0.1', port: httpPort,
    trustProxy: env.TRUST_PROXY ? env.TRUST_PROXY.split(',').map(value => value.trim()) : false as false,
    database: new PgDatabase(new pg.Pool({
      host: env.PGHOST, port, database: env.PGDATABASE, user: env.PGUSER,
      password: env.PGPASSWORD, max: 5, connectionTimeoutMillis: 8_000, idleTimeoutMillis: 30_000,
      ssl: { ca: certificate, rejectUnauthorized: true },
      options: '-c statement_timeout=8000 -c lock_timeout=3000',
      application_name: 'marvel-lobby-api'
    }))
  };
}
