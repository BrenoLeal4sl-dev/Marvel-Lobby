import { PGlite } from '@electric-sql/pglite';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import type { Database, Query, Row } from '../src/database.js';

export class TestDatabase implements Database {
  private constructor(readonly engine: PGlite) {}
  static async create(): Promise<TestDatabase> {
    const engine=new PGlite();
    const database=(await engine.query<{name:string}>('SELECT current_database() AS name')).rows[0]!.name;
    await engine.exec(`CREATE ROLE schema_admin NOLOGIN NOSUPERUSER NOBYPASSRLS;
      CREATE ROLE marvel_lobby_api LOGIN NOSUPERUSER NOBYPASSRLS;
      GRANT CREATE ON DATABASE "${database.replaceAll('"','""')}" TO schema_admin;
      SET ROLE schema_admin;`);
    const root=new URL('../../../database/',import.meta.url);
    await engine.exec(readFileSync(fileURLToPath(new URL('000_setup_phase1.sql',root)),'utf8'));
    await engine.exec(readFileSync(fileURLToPath(new URL('002_runtime_permissions.sql',root)),'utf8'));
    await engine.exec('RESET ROLE');
    return new TestDatabase(engine);
  }
  async transaction<T>(id:string|null,action:(query:Query)=>Promise<T>):Promise<T> {
    return this.engine.transaction(async tx=> {
      await tx.exec('SET LOCAL ROLE marvel_lobby_api');
      if(id) await tx.query("SELECT set_config('app.user_id',$1,true)",[id]);
      const query:Query={query:async(sql,values=[])=> (await tx.query<Row>(sql,values)).rows};
      return action(query);
    });
  }
  async query(sql:string,values:unknown[]=[]):Promise<Row[]> {
    return this.transaction(null,query=>query.query(sql,values));
  }
  async close():Promise<void>{ await this.engine.close(); }
}
