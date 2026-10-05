import pg from 'pg';

export type Row = Record<string, any>;
export interface Query { query(sql: string, values?: unknown[]): Promise<Row[]> }
export interface Database extends Query {
  transaction<T>(userId: string | null, action: (query: Query) => Promise<T>): Promise<T>;
  close(): Promise<void>;
}
export class PgDatabase implements Database {
  constructor(private readonly pool: pg.Pool) {}
  async query(sql: string, values: unknown[] = []): Promise<Row[]> {
    return (await this.pool.query(sql, values)).rows;
  }
  async transaction<T>(userId: string | null, action: (query: Query) => Promise<T>): Promise<T> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      if(userId) await client.query("SELECT set_config('app.user_id',$1,true)", [userId]);
      const query: Query = { query: async (sql, values = []) => (await client.query(sql, values)).rows };
      const result = await action(query);
      await client.query('COMMIT');
      return result;
    } catch(error) {
      await client.query('ROLLBACK');
      throw error;
    } finally { client.release(); }
  }
  async close(): Promise<void> { await this.pool.end(); }
}
export async function checkDatabase(db: Database): Promise<void> {
  const role = (await db.query(`SELECT current_user AS name, r.rolsuper, r.rolbypassrls,
    pg_has_role(current_user,n.nspowner,'MEMBER') AS owns_schema
    FROM pg_catalog.pg_roles r JOIN pg_catalog.pg_namespace n ON n.nspname='marvel_lobby'
    WHERE r.rolname=current_user`))[0];
  if(!role) throw new Error('The configured database has no marvel_lobby schema. Check PGDATABASE and execute the setup script in that database.');
  if(role.rolsuper || role.rolbypassrls || role.owns_schema)
    throw new Error('Use the restricted marvel_lobby_api service user and execute the permission script.');
  const versions = await db.query('SELECT version FROM marvel_lobby.schema_migrations ORDER BY version');
  if(!versions.some(row => row.version === 2)) throw new Error('Execute the Phase 1 identity migration first.');
  await db.query('SELECT id, username FROM marvel_lobby.public_profiles LIMIT 0');
  if(versions.some(row=>row.version===3)) {
    await db.query('SELECT follower_id,followed_id FROM marvel_lobby.follows LIMIT 0');
    await db.query('SELECT id,user_a,user_b FROM marvel_lobby.direct_conversations LIMIT 0');
    await db.query('SELECT id,body FROM marvel_lobby.direct_messages LIMIT 0');
    await db.query('SELECT last_read_id FROM marvel_lobby.direct_reads LIMIT 0');
  }
}
