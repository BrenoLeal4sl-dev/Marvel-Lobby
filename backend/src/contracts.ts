export interface PublicProfile {
  id: string;
  name: string;
  username: string;
  bio: string;
  avatarId: number | null;
  joinedAt: string;
}
export interface AccountProfile extends PublicProfile { email: string }
export interface SessionResponse {
  user: AccountProfile;
  accessToken: string;
  refreshToken: string;
  accessExpiresAt: string;
  refreshExpiresAt: string;
}
export interface RegisterInput {
  name: string; email: string; username: string; password: string;
  bio?: string; avatarId?: number | null;
}
export interface ProfileInput { name: string; username: string; bio: string; avatarId: number | null }
export interface CredentialInput { email: string; currentPassword: string; newPassword?: string; profile?: ProfileInput }
export interface Principal { userId: string; sessionId: string }
export function iso(value: Date | string): string { return new Date(value).toISOString(); }
