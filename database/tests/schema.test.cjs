// Banco descartável em memória. Nenhuma conexão com Aiven ou credenciais reais.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');
const root = path.resolve(__dirname, '../..');
const localRequire = createRequire(path.join(root, 'build/database-check/package.json'));
const { PGlite } = localRequire('@electric-sql/pglite');
const db = new PGlite();
const A = '11111111-1111-4111-8111-111111111111';
const B = '22222222-2222-4222-8222-222222222222';
const CA = '33333333-3333-4333-8333-333333333333';
const CB = '44444444-4444-4444-8444-444444444444';
const snapshot = JSON.stringify({ id: 1440, type: 'CHARACTER', name: 'Test character' });
let passed = 0;
async function test(name, action) { await action(); passed++; console.log(`PASS ${name}`); }
async function rejected(code, action) {
  await assert.rejects(action, error => error.code === code);
}
async function asUser(userId, action) {
  await db.exec('BEGIN; SET LOCAL ROLE marvel_lobby_api;');
  try {
    if (userId) await db.query("SELECT set_config('app.user_id',$1,true)", [userId]);
    return await action();
  } finally { await db.exec('ROLLBACK'); }
}
async function main() {
  const database = (await db.query('SELECT current_database() AS name')).rows[0].name;
  await db.exec(`CREATE ROLE schema_admin NOLOGIN NOSUPERUSER NOBYPASSRLS;
    CREATE ROLE marvel_lobby_api LOGIN NOSUPERUSER NOBYPASSRLS;
    GRANT CREATE ON DATABASE "${database.replaceAll('"', '""')}" TO schema_admin;
    SET ROLE schema_admin;`);
  await test('initial migration without superuser privileges', async () => {
    await db.exec(fs.readFileSync(path.join(root, 'database/001_initial.sql'), 'utf8'));
    assert.equal((await db.query('SELECT count(*)::int AS n FROM marvel_lobby.schema_migrations')).rows[0].n, 1);
  });
  await test('runtime permission script', async () => {
    await db.exec(fs.readFileSync(path.join(root, 'database/002_runtime_permissions.sql'), 'utf8'));
  });
  await db.exec('RESET ROLE');
  for (const [id, email] of [[A, 'alice@example.invalid'], [B, 'bob@example.invalid']]) {
    await db.query('INSERT INTO marvel_lobby.users(id,email,password_hash) VALUES ($1,$2,$3)',
      [id, email, 'test-only-not-a-real-password-hash'.padEnd(64, 'x')]);
    await db.query('INSERT INTO marvel_lobby.profiles(user_id,display_name) VALUES ($1,$2)', [id, 'Test profile']);
    await db.query('INSERT INTO marvel_lobby.preferences(user_id) VALUES ($1)', [id]);
    await db.query("INSERT INTO marvel_lobby.favorites(user_id,resource_type,comic_vine_id,snapshot) VALUES ($1,'character',1440,$2)", [id, snapshot]);
  }
  await db.query('INSERT INTO marvel_lobby.conversations(id,user_id) VALUES ($1,$2),($3,$4)', [CA,A,CB,B]);
  await db.query("INSERT INTO marvel_lobby.view_history(user_id,resource_type,comic_vine_id,snapshot) VALUES ($1,'character',1440,$2)", [A,snapshot]);
  await test('email uniqueness ignores case', () => rejected('23505', () =>
    db.query('INSERT INTO marvel_lobby.users(email,password_hash) VALUES ($1,$2)', ['ALICE@example.invalid','x'.repeat(64)])));
  await test('no user context means no private rows', () => asUser(null, async () => {
    assert.equal((await db.query('SELECT * FROM marvel_lobby.favorites')).rows.length, 0);
    assert.equal((await db.query('SELECT * FROM marvel_lobby.profiles')).rows.length, 0);
  }));
  await test('own rows are visible; another account is hidden', () => asUser(A, async () => {
    const rows = (await db.query('SELECT user_id FROM marvel_lobby.favorites')).rows;
    assert.deepEqual(rows.map(row => row.user_id), [A]);
    assert.equal((await db.query('UPDATE marvel_lobby.profiles SET display_name=$1 WHERE user_id=$2 RETURNING user_id', ['Wrong',B])).rows.length, 0);
  }));
  await test('cannot write another account favorite', () => asUser(A, () => rejected('42501', () =>
    db.query("INSERT INTO marvel_lobby.favorites(user_id,resource_type,comic_vine_id,snapshot) VALUES ($1,'character',1440,$2)", [B,snapshot]))));
  await test('snapshots must match record identity', () => asUser(A, () => rejected('23514', () =>
    db.query("INSERT INTO marvel_lobby.favorites(user_id,resource_type,comic_vine_id,snapshot) VALUES ($1,'character',999,$2)", [A,snapshot]))));
  await test('favorite duplicates are rejected', () => asUser(A, () => rejected('23505', () =>
    db.query("INSERT INTO marvel_lobby.favorites(user_id,resource_type,comic_vine_id,snapshot) VALUES ($1,'character',1440,$2)", [A,snapshot]))));
  await test('update trigger advances version and preserves creation date', () => asUser(A, async () => {
    const before = (await db.query('SELECT version,created_at FROM marvel_lobby.profiles WHERE user_id=$1', [A])).rows[0];
    const after = (await db.query("UPDATE marvel_lobby.profiles SET display_name='Changed',version=99,created_at='2000-01-01' WHERE user_id=$1 RETURNING version,created_at", [A])).rows[0];
    assert.equal(BigInt(after.version), BigInt(before.version)+1n);
    assert.equal(String(after.created_at), String(before.created_at));
  }));
  await test('clearing history preserves favorites', () => asUser(A, async () => {
    await db.query('UPDATE marvel_lobby.view_history SET deleted_at=now() WHERE user_id=$1', [A]);
    assert.equal((await db.query('SELECT * FROM marvel_lobby.favorites WHERE deleted_at IS NULL')).rows.length, 1);
    assert.equal((await db.query('SELECT * FROM marvel_lobby.view_history WHERE deleted_at IS NULL')).rows.length, 0);
  }));
  await test('message owner must match conversation owner', () => asUser(A, () => rejected('23503', () =>
    db.query("INSERT INTO marvel_lobby.messages(conversation_id,user_id,role,content) VALUES ($1,$2,'user','Test')", [CB,A]))));
  await test('own chat messages can be inserted using the identity sequence', () => asUser(A, async () => {
    const rows = (await db.query("INSERT INTO marvel_lobby.messages(conversation_id,user_id,role,content) VALUES ($1,$2,'user','Test') RETURNING ordinal", [CA,A])).rows;
    assert.equal(rows.length, 1);
  }));
  await test('incomplete conversation context is rejected', () => asUser(A, () => rejected('23514', () =>
    db.query('INSERT INTO marvel_lobby.conversations(user_id,context_id) VALUES ($1,1440)', [A]))));
  await test('runtime cannot change migration metadata', () => asUser(A, () => rejected('42501', () =>
    db.query("INSERT INTO marvel_lobby.schema_migrations(version,description) VALUES (2,'Wrong')"))));
  await test('runtime cannot create database objects in application schema', () => asUser(A, () => rejected('42501', () =>
    db.exec('CREATE TABLE marvel_lobby.forbidden(id int)'))));
  await test('cache deletion leaves saved snapshots intact', async () => {
    await db.query("INSERT INTO marvel_lobby.catalog_cache(resource_type,comic_vine_id,snapshot,expires_at) VALUES ('character',1440,$1,now()+interval '1 day')", [snapshot]);
    await db.exec('DELETE FROM marvel_lobby.catalog_cache');
    assert.equal((await db.query('SELECT count(*)::int AS n FROM marvel_lobby.favorites')).rows[0].n, 2);
  });
  await test('deleting an account cascades without affecting other accounts', async () => {
    await db.query('DELETE FROM marvel_lobby.users WHERE id=$1', [A]);
    assert.equal((await db.query('SELECT count(*)::int AS n FROM marvel_lobby.favorites WHERE user_id=$1', [A])).rows[0].n, 0);
    assert.equal((await db.query('SELECT count(*)::int AS n FROM marvel_lobby.favorites WHERE user_id=$1', [B])).rows[0].n, 1);
  });
  console.log(`Completed: ${passed} checks. Engine: ${(await db.query('SELECT version()')).rows[0].version}`);
}
main().catch(error => { console.error(error.message, error.code || ''); process.exitCode=1; }).finally(() => db.close());
