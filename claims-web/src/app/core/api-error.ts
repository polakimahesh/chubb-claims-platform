import { HttpErrorResponse } from '@angular/common/http';

/** An RFC 7807 problem response from any of the services, normalised for display. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    /** Bean-validation failures, as "field: message" (400 only). */
    readonly fieldErrors: string[] = [],
  ) {
    super(message);
  }

  static from(e: unknown): ApiError {
    if (e instanceof ApiError) return e;
    if (!(e instanceof HttpErrorResponse)) return new ApiError(0, 'Something went wrong. Please try again.');
    if (e.status === 0) return new ApiError(0, 'Cannot reach the server. Check that the backend services are running.');
    const body = typeof e.error === 'object' && e.error ? e.error : {};
    const detail: string | undefined = body.detail;
    const errors: string[] = Array.isArray(body.errors) ? body.errors : [];
    return new ApiError(e.status, detail || FALLBACK[e.status] || `Request failed (${e.status})`, errors);
  }

  /** 409: the claim changed underneath us (lifecycle conflict or optimistic lock) - the caller should reload. */
  get isConflict(): boolean {
    return this.status === 409;
  }
}

const FALLBACK: Record<number, string> = {
  400: 'The request was not valid.',
  401: 'Your session has expired. Please sign in again.',
  403: 'You do not have permission to perform this action.',
  404: 'Not found.',
  409: 'This claim was changed by someone else. It has been reloaded.',
  413: 'The file is larger than 5 MB.',
  415: 'Only PDF, PNG and JPEG files are accepted.',
  422: 'This action is not allowed for the claim.',
  500: 'The server hit an unexpected error.',
};
