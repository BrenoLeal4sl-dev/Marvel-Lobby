// PostgreSQL real em memória (PGlite), sem conexão com a instância Aiven.
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
let passed=0;
async function test(name, action) { await action();passed++;console.log(`PASS ${name}`); }
async function rejected(code,action) { await assert.rejects(action,error=>error.code===code); }
async function asUser(id,action) {
    await db.exec('BEGIN; SET LOCAL ROLE marvel_lobby_api;');
    try {
        if(id)await db.query("SELECT set_config('app.user_id',$1,true)",[id]);
        return await action();
    } finally { await db.exec('ROLLBACK'); }
}
async function main() {
    const database=(await db.query('SELECT current_database() AS name')).rows[0].name;
    await db.exec(`CREATE ROLE schema_admin NOLOGIN NOSUPERUSER NOBYPASSRLS;
        GRANT CREATE ON DATABASE "${database.replaceAll('"','""')}" TO schema_admin;
        SET ROLE schema_admin;`);
    await test('single pgAdmin script installs both migrations without superuser',async()=> {
        await db.exec(fs.readFileSync(path.join(root,'database/000_setup_phase1.sql'),'utf8'));
        assert.deepEqual((await db.query('SELECT version FROM marvel_lobby.schema_migrations ORDER BY version')).rows.map(r=>r.version),[1,2]);
    });
    await db.exec('RESET ROLE; CREATE ROLE marvel_lobby_api LOGIN NOSUPERUSER NOBYPASSRLS; SET ROLE schema_admin;');
    await test('permissions can be applied after creating the runtime role',async()=> {
        await db.exec(fs.readFileSync(path.join(root,'database/002_runtime_permissions.sql'),'utf8'));
    });
    await db.exec('RESET ROLE');
    for(const [id,email,username] of [[A,'alice@example.invalid','alice'],[B,'bob@example.invalid','bob']]) {
        await db.query('INSERT INTO marvel_lobby.users(id,email,password_hash) VALUES ($1,$2,$3)',[id,email,'test-only'.padEnd(64,'x')]);
        await db.query('INSERT INTO marvel_lobby.profiles(user_id,display_name,username,avatar_id,bio) VALUES ($1,$2,$3,1440,$4)',[id,username,username,'Marvel fan']);
        await db.query('INSERT INTO marvel_lobby.preferences(user_id) VALUES ($1)',[id]);
    }
    await test('username uniqueness applies to distinct users',()=>rejected('23505',()=>db.query('UPDATE marvel_lobby.profiles SET username=$1 WHERE user_id=$2',['alice',B])));
    await test('uppercase, @, whitespace and punctuation cannot bypass username rules',async()=> {
        for(const value of ['ALICE','@alice','alice smith','alice!','__','___'])
            await rejected('23514',()=>db.query('UPDATE marvel_lobby.profiles SET username=$1 WHERE user_id=$2',[value,B]));
    });
    await test('arbitrary avatars and oversized bios are rejected',async()=> {
        await rejected('23514',()=>db.query('UPDATE marvel_lobby.profiles SET avatar_id=999 WHERE user_id=$1',[A]));
        await rejected('23514',()=>db.query('UPDATE marvel_lobby.profiles SET bio=$1 WHERE user_id=$2',['x'.repeat(281),A]));
    });
    await test('public profile projection exposes only approved public fields',()=>asUser(A,async()=> {
        const rows=(await db.query('SELECT * FROM marvel_lobby.public_profiles ORDER BY username')).rows;
        assert.equal(rows.length,2);
        assert.deepEqual(Object.keys(rows[0]).sort(),['avatar_id','bio','display_name','id','joined_at','username']);
        assert.equal((await db.query('SELECT * FROM marvel_lobby.profiles')).rows.length,1);
        assert.equal((await db.query('UPDATE marvel_lobby.profiles SET bio=$1 WHERE user_id=$2 RETURNING user_id',['Unauthorized',B])).rows.length,0);
    }));
    await test('runtime role cannot update the public view',()=>asUser(A,async()=> {
        const permission=(await db.query("SELECT has_table_privilege(current_user,'marvel_lobby.public_profiles','UPDATE') AS allowed")).rows[0].allowed;
        assert.equal(permission,false);
        await assert.rejects(()=>db.query('UPDATE marvel_lobby.public_profiles SET bio=$1 WHERE id=$2',['Unauthorized',B]),error=>['42501','55000'].includes(error.code));
    }));
    await test('public access is revoked for an unrelated database role',async()=> {
        await db.exec('CREATE ROLE unrelated NOLOGIN; BEGIN; SET LOCAL ROLE unrelated;');
        try { await rejected('42501',()=>db.query('SELECT * FROM marvel_lobby.public_profiles')); }
        finally { await db.exec('ROLLBACK'); }
    });
    await test('disabled accounts disappear from public profiles',async()=> {
        await db.query('UPDATE marvel_lobby.users SET disabled_at=now() WHERE id=$1',[B]);
        await asUser(A,async()=>assert.equal((await db.query('SELECT * FROM marvel_lobby.public_profiles')).rows.length,1));
    });
    await test('activity and favorites start private',async()=> {
        const row=(await db.query('SELECT share_activity,show_favorites FROM marvel_lobby.preferences WHERE user_id=$1',[A])).rows[0];
        assert.deepEqual(row,{share_activity:false,show_favorites:false});
    });
    await test('access token requires a 32-byte hash and a valid expiry pair',async()=> {
        const params=[A,Buffer.alloc(32,1),Buffer.alloc(32,2)];
        await db.query("INSERT INTO marvel_lobby.sessions(user_id,refresh_token_hash,expires_at,access_token_hash,access_expires_at) VALUES ($1,$2,now()+interval '30 days',$3,now()+interval '15 minutes')",params);
        await rejected('23505',()=>db.query("INSERT INTO marvel_lobby.sessions(user_id,refresh_token_hash,expires_at,access_token_hash,access_expires_at) VALUES ($1,$2,now()+interval '30 days',$3,now()+interval '15 minutes')",[A,Buffer.alloc(32,3),params[2]]));
        await rejected('23514',()=>db.query("INSERT INTO marvel_lobby.sessions(user_id,refresh_token_hash,expires_at,access_token_hash,access_expires_at) VALUES ($1,$2,now()+interval '30 days',$3,now()+interval '15 minutes')",[A,Buffer.alloc(32,4),Buffer.alloc(8)]));
        await rejected('23514',()=>db.query("INSERT INTO marvel_lobby.sessions(user_id,refresh_token_hash,expires_at,access_token_hash) VALUES ($1,$2,now()+interval '30 days',$3)",[A,Buffer.alloc(32,5),Buffer.alloc(32,6)]));
    });
    await test('rerunning setup fails rather than deleting existing data',async()=> {
        await rejected('42P06',()=>db.exec(fs.readFileSync(path.join(root,'database/000_setup_phase1.sql'),'utf8')));
        await db.exec('ROLLBACK');
        assert.equal((await db.query('SELECT count(*)::int AS n FROM marvel_lobby.users')).rows[0].n,2);
    });
    await test('upgrade preserves legacy identities and revokes obsolete sessions',async()=> {
        const legacy=new PGlite();
        try {
            await legacy.exec(fs.readFileSync(path.join(root,'database/001_initial.sql'),'utf8'));
            await legacy.exec('CREATE ROLE marvel_lobby_api LOGIN NOSUPERUSER NOBYPASSRLS;');
            await legacy.query('INSERT INTO marvel_lobby.users(id,email,password_hash) VALUES ($1,$2,$3)',[A,'existing@example.invalid','legacy-hash'.padEnd(64,'x')]);
            await legacy.query('INSERT INTO marvel_lobby.profiles(user_id,display_name) VALUES ($1,$2)',[A,'Existing']);
            await legacy.query('INSERT INTO marvel_lobby.preferences(user_id) VALUES ($1)',[A]);
            await legacy.query("INSERT INTO marvel_lobby.sessions(user_id,refresh_token_hash,expires_at) VALUES ($1,$2,now()+interval '30 days')",[A,Buffer.alloc(32,1)]);
            await legacy.query("INSERT INTO marvel_lobby.favorites(user_id,resource_type,comic_vine_id,snapshot) VALUES ($1,'character',1440,$2)",[A,JSON.stringify({id:1440,type:'CHARACTER',name:'Wolverine'})]);
            await legacy.query('INSERT INTO marvel_lobby.conversations(user_id,title) VALUES ($1,$2)',[A,'Existing AI conversation']);
            const before=(await legacy.query('SELECT created_at FROM marvel_lobby.users WHERE id=$1',[A])).rows[0].created_at;
            await legacy.exec(fs.readFileSync(path.join(root,'database/003_social_identity.sql'),'utf8'));
            const profile=(await legacy.query('SELECT * FROM marvel_lobby.profiles WHERE user_id=$1',[A])).rows[0];
            assert.equal(profile.display_name,'Existing');
            assert.match(profile.username,/^[a-z0-9_]{3,24}$/);
            assert.equal((await legacy.query('SELECT created_at FROM marvel_lobby.users WHERE id=$1',[A])).rows[0].created_at.getTime(),before.getTime());
            assert.equal((await legacy.query('SELECT * FROM marvel_lobby.favorites')).rows.length,1);
            assert.equal((await legacy.query('SELECT title FROM marvel_lobby.conversations')).rows[0].title,'Existing AI conversation');
            assert.ok((await legacy.query('SELECT revoked_at FROM marvel_lobby.sessions')).rows[0].revoked_at);
            assert.equal((await legacy.query("SELECT has_table_privilege('marvel_lobby_api','marvel_lobby.public_profiles','SELECT') AS allowed")).rows[0].allowed,true);
        } finally { await legacy.close(); }
    });
    console.log(`\n${passed} social identity checks passed.`);
}
main().then(()=>db.close()).catch(async error=>{console.error(error);await db.close();process.exitCode=1;});
