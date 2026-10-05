import { randomBytes, scrypt, timingSafeEqual, createHash } from 'node:crypto';
import { ApiError, invalid } from './errors.js';

const COST = 131_072; // OWASP scrypt baseline: N=2^17, r=8, p=1.
export const AVATARS = [1440,1443,1455,1442,2268,2267,1444,3200];
export function normalizeUsername(value: string): string {
  const username = value.trim().replace(/^@/, '').toLowerCase();
  if(!/^[a-z0-9_]{3,24}$/.test(username) || !/[a-z0-9]/.test(username)) invalid('Use 3–24 letters, numbers or underscores for your username.');
  return username;
}
export function validatePassword(value: string): void {
  if(value.length < 8 || value.length > 128 || !/\p{L}/u.test(value) || !/\p{N}/u.test(value))
    invalid('Use 8–128 characters, including a letter and a number.');
}
export function normalizeEmail(value: string): string {
  const email = value.trim().toLowerCase();
  if(email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) invalid('Enter a valid email address.');
  return email;
}
export interface Passwords { hash(value: string): Promise<string>; verify(value: string, encoded: string): Promise<boolean> }
export class ScryptPasswords implements Passwords {
  private active = 0;
  private async key(password: string, salt: Buffer): Promise<Buffer> {
    if(this.active >= 2) throw new ApiError(503,'BACKEND_BUSY','The service is busy. Please try again.');
    this.active++;
    try { return await new Promise<Buffer>((resolve,reject)=> {
      scrypt(password,salt,64,{N:COST,r:8,p:1,maxmem:160*1024*1024},(error,key)=>error?reject(error):resolve(key));
    }); }
    finally { this.active--; }
  }
  async hash(value: string): Promise<string> {
    const salt = randomBytes(16);
    return `scrypt$${COST}$8$1$${salt.toString('hex')}$${(await this.key(value,salt)).toString('hex')}`;
  }
  async verify(value: string, encoded: string): Promise<boolean> {
    const parts = encoded.split('$');
    if(parts.length !== 6 || parts[0] !== 'scrypt' || parts[1] !== String(COST) || parts[2] !== '8' || parts[3] !== '1'
      || !/^[a-f0-9]{32}$/.test(parts[4] ?? '') || !/^[a-f0-9]{128}$/.test(parts[5] ?? '')) return false;
    const actual = await this.key(value, Buffer.from(parts[4]!, 'hex'));
    return timingSafeEqual(actual,Buffer.from(parts[5]!, 'hex'));
  }
}
export function token(): string { return randomBytes(32).toString('base64url'); }
export function tokenHash(value: string): Buffer {
  if(!/^[A-Za-z0-9_-]{43}$/.test(value)) throw new ApiError(401,'SESSION_EXPIRED','Sign in again to continue.');
  return createHash('sha256').update(value).digest();
}
