export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string,readonly field?:string) { super(message); }
}
export function invalid(message = 'Check the supplied fields.',field?:string): never {
  throw new ApiError(400, 'INVALID_INPUT', message,field);
}
export function unauthorized(): never {
  throw new ApiError(401, 'SESSION_EXPIRED', 'Sign in again to continue.');
}
