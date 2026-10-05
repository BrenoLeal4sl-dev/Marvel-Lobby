import { randomUUID } from 'node:crypto';
import type { Database, Query, Row } from './database.js';
import type { AccountProfile, PublicProfile, RegisterInput, ProfileInput, CredentialInput, Principal, SessionResponse } from './contracts.js';
import { iso } from './contracts.js';
import { ApiError, invalid, unauthorized } from './errors.js';
import { AVATARS, normalizeEmail, normalizeUsername, validatePassword, token, tokenHash, type Passwords } from './security.js';

const ACCESS_MS = 15 * 60 * 1000;
const REFRESH_MS = 30 * 24 * 60 * 60 * 1000;
function profile(row: Row): PublicProfile {
  return { id: row.id, name: row.display_name, username: row.username, bio: row.bio,
    avatarId: row.avatar_id == null ? null : Number(row.avatar_id), joinedAt: iso(row.joined_at) };
}
function validateProfile(input: ProfileInput): ProfileInput {
  const name = input.name.trim(), username = normalizeUsername(input.username), bio = input.bio.trim();
  if(name.length < 2 || name.length > 80) invalid('Enter a name with 2–80 characters.');
  if(Array.from(bio).length > 280) invalid('Keep your bio within 280 characters.');
  if(input.avatarId !== null && !AVATARS.includes(input.avatarId)) invalid('Choose an approved Marvel avatar.');
  return { name, username, bio, avatarId: input.avatarId };
}

export class Accounts {
  private dummyHash: string | null = null;
  constructor(private readonly db: Database, private readonly passwords: Passwords) {}
  async initialize(): Promise<void> { this.dummyHash = await this.passwords.hash(token()); }

  async authorized<T>(principal: Principal,action:(query:Query)=>Promise<T>):Promise<T> {
    return this.db.transaction(principal.userId,async query=> {
      await this.lockUser(query,principal.userId);
      await this.lockSession(query,principal);
      return action(query);
    });
  }

  private async account(query: Query, id: string): Promise<AccountProfile> {
    const row = (await query.query(`SELECT u.id,u.email,p.display_name,p.username,p.bio,p.avatar_id,u.created_at AS joined_at
      FROM marvel_lobby.users u JOIN marvel_lobby.profiles p ON p.user_id=u.id
      WHERE u.id=$1 AND u.disabled_at IS NULL`,[id]))[0];
    if(!row) unauthorized();
    return { ...profile(row), email: row.email };
  }
  private async lockSession(query: Query, principal: Principal): Promise<void> {
    const active = (await query.query(`SELECT id FROM marvel_lobby.sessions
      WHERE id=$1 AND user_id=$2 AND revoked_at IS NULL AND access_expires_at>now() AND expires_at>now()
      FOR SHARE`,[principal.sessionId,principal.userId]))[0];
    if(!active) unauthorized();
  }
  private async lockUser(query: Query,id: string,exclusive=false): Promise<Row> {
    const row = (await query.query(`SELECT * FROM marvel_lobby.users WHERE id=$1 AND disabled_at IS NULL FOR ${exclusive?'UPDATE':'SHARE'}`,[id]))[0];
    if(!row) unauthorized();
    return row;
  }
  private async issue(query: Query,userId: string,familyId=randomUUID(),expires=new Date(Date.now()+REFRESH_MS)): Promise<SessionResponse> {
    if(expires.getTime() <= Date.now()+5000) unauthorized();
    const accessToken=token(),refreshToken=token(),accessExpires=new Date(Math.min(Date.now()+ACCESS_MS,expires.getTime()));
    await query.query(`INSERT INTO marvel_lobby.sessions(user_id,family_id,refresh_token_hash,access_token_hash,access_expires_at,expires_at)
      VALUES ($1,$2,$3,$4,$5,$6)`,[userId,familyId,tokenHash(refreshToken),tokenHash(accessToken),accessExpires,expires]);
    return { user: await this.account(query,userId), accessToken,refreshToken,
      accessExpiresAt: accessExpires.toISOString(),refreshExpiresAt: expires.toISOString() };
  }
  async register(input: RegisterInput): Promise<SessionResponse> {
    const data=validateProfile({ name:input.name,username:input.username,bio:input.bio??'',avatarId:input.avatarId??null });
    const email=normalizeEmail(input.email);
    validatePassword(input.password);
    const hash=await this.passwords.hash(input.password), id=randomUUID();
    return this.db.transaction(id,async query=> {
      await query.query('INSERT INTO marvel_lobby.users(id,email,password_hash) VALUES ($1,$2,$3)',[id,email,hash]);
      await query.query(`INSERT INTO marvel_lobby.profiles(user_id,display_name,username,bio,avatar_id)
        VALUES ($1,$2,$3,$4,$5)`,[id,data.name,data.username,data.bio,data.avatarId]);
      await query.query('INSERT INTO marvel_lobby.preferences(user_id) VALUES ($1)',[id]);
      return this.issue(query,id);
    });
  }
  async login(email: string,password: string): Promise<SessionResponse> {
    const normalized=normalizeEmail(email);
    const initial=(await this.db.query('SELECT id,password_hash,disabled_at FROM marvel_lobby.users WHERE lower(email)=$1',[normalized]))[0];
    // Equal-cost verification for a missing/disabled account; do not disclose existence.
    const valid=await this.passwords.verify(password,initial?.password_hash??this.dummyHash!);
    if(!initial || initial.disabled_at || !valid) throw new ApiError(401,'INVALID_CREDENTIALS','Email or password is incorrect.');
    return this.db.transaction(initial.id,async query=> {
      const current=await this.lockUser(query,initial.id);
      if(current.password_hash!==initial.password_hash) throw new ApiError(401,'INVALID_CREDENTIALS','Email or password is incorrect.');
      return this.issue(query,initial.id);
    });
  }
  async authenticate(accessToken: string): Promise<Principal> {
    const row=(await this.db.query(`SELECT s.id,s.user_id FROM marvel_lobby.sessions s
      JOIN marvel_lobby.users u ON u.id=s.user_id
      WHERE s.access_token_hash=$1 AND s.revoked_at IS NULL AND s.access_expires_at>now()
        AND s.expires_at>now() AND u.disabled_at IS NULL`,[tokenHash(accessToken)]))[0];
    if(!row) unauthorized();
    return { userId:row.user_id,sessionId:row.id };
  }
  async refresh(refreshToken: string): Promise<SessionResponse> {
    const hash=tokenHash(refreshToken);
    const initial=(await this.db.query('SELECT user_id FROM marvel_lobby.sessions WHERE refresh_token_hash=$1',[hash]))[0];
    if(!initial) unauthorized();
    const result=await this.db.transaction(initial.user_id,async query=> {
      await this.lockUser(query,initial.user_id);
      const old=(await query.query('SELECT * FROM marvel_lobby.sessions WHERE refresh_token_hash=$1 FOR UPDATE',[hash]))[0];
      if(!old) return null;
      if(old.revoked_at || new Date(old.expires_at).getTime()<=Date.now()+5000) {
        // Commit family revocation before returning 401. A rollback would preserve stolen descendants.
        await query.query('UPDATE marvel_lobby.sessions SET revoked_at=coalesce(revoked_at,now()) WHERE user_id=$1 AND family_id=$2',[old.user_id,old.family_id]);
        return null;
      }
      await query.query('UPDATE marvel_lobby.sessions SET revoked_at=now(),last_used_at=now() WHERE id=$1',[old.id]);
      return this.issue(query,old.user_id,old.family_id,new Date(old.expires_at));
    });
    if(!result) unauthorized();
    return result;
  }
  async logout(principal: Principal): Promise<void> {
    await this.db.transaction(principal.userId,async query=> {
      await this.lockUser(query,principal.userId);
      await this.lockSession(query,principal);
      await query.query(`UPDATE marvel_lobby.sessions SET revoked_at=coalesce(revoked_at,now())
        WHERE user_id=$1 AND family_id=(SELECT family_id FROM marvel_lobby.sessions WHERE id=$2)`,[principal.userId,principal.sessionId]);
    });
  }
  async me(principal: Principal): Promise<AccountProfile> {
    return this.db.transaction(principal.userId,async query=> {
      await this.lockUser(query,principal.userId);
      await this.lockSession(query,principal);
      return this.account(query,principal.userId);
    });
  }
  async update(principal: Principal,input: ProfileInput): Promise<AccountProfile> {
    const data=validateProfile(input);
    return this.db.transaction(principal.userId,async query=> {
      await this.lockUser(query,principal.userId);
      await this.lockSession(query,principal);
      await query.query(`UPDATE marvel_lobby.profiles SET display_name=$2,username=$3,bio=$4,avatar_id=$5 WHERE user_id=$1`,
        [principal.userId,data.name,data.username,data.bio,data.avatarId]);
      return this.account(query,principal.userId);
    });
  }
  async credentials(principal: Principal,input: CredentialInput): Promise<SessionResponse> {
    const email=normalizeEmail(input.email);
    const data=input.profile?validateProfile(input.profile):null;
    if(input.newPassword) validatePassword(input.newPassword);
    const initial=(await this.db.query('SELECT password_hash FROM marvel_lobby.users WHERE id=$1',[principal.userId]))[0];
    if(!initial || !await this.passwords.verify(input.currentPassword,initial.password_hash))
      throw new ApiError(403,'CURRENT_PASSWORD_INCORRECT','Current password is incorrect.');
    const hash=input.newPassword?await this.passwords.hash(input.newPassword):initial.password_hash;
    return this.db.transaction(principal.userId,async query=> {
      const current=await this.lockUser(query,principal.userId,true);
      await this.lockSession(query,principal);
      if(current.password_hash!==initial.password_hash)
        throw new ApiError(403,'CURRENT_PASSWORD_INCORRECT','Current password is incorrect.');
      await query.query(`UPDATE marvel_lobby.users SET email=$2,password_hash=$3,
        password_changed_at=CASE WHEN password_hash<>$3 THEN now() ELSE password_changed_at END WHERE id=$1`,[principal.userId,email,hash]);
      if(data) await query.query(`UPDATE marvel_lobby.profiles SET display_name=$2,username=$3,bio=$4,avatar_id=$5 WHERE user_id=$1`,
        [principal.userId,data.name,data.username,data.bio,data.avatarId]);
      await query.query('UPDATE marvel_lobby.sessions SET revoked_at=coalesce(revoked_at,now()) WHERE user_id=$1',[principal.userId]);
      return this.issue(query,principal.userId);
    });
  }
  async publicProfile(principal: Principal,id: string): Promise<PublicProfile> {
    return this.db.transaction(principal.userId,async query=> {
      await this.lockUser(query,principal.userId);
      await this.lockSession(query,principal);
      const row=(await query.query('SELECT * FROM marvel_lobby.public_profiles WHERE id=$1',[id]))[0];
      if(!row) throw new ApiError(404,'USER_NOT_FOUND','This profile is unavailable.');
      return profile(row);
    });
  }
}
