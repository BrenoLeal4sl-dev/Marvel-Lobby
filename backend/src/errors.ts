export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) { super(message); }
}
export function invalid(message = 'Check the supplied fields.'): never {
  throw new ApiError(400, 'INVALID_INPUT', message);
}
export function unauthorized(): never {
  throw new ApiError(401, 'SESSION_EXPIRED', 'Sign in again to continue.');
}
