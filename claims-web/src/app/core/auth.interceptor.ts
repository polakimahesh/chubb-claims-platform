import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ApiError } from './api-error';
import { AuthService } from './auth.service';

/**
 * Adds the Basic credentials to API calls, converts every failure into an {@link ApiError}, and sends the user back
 * to sign-in when the server answers 401 (expired or changed credentials).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token;
  const isApi = req.url.startsWith('/api/');
  const authed = isApi && token && !req.headers.has('Authorization')
    ? req.clone({ setHeaders: { Authorization: `Basic ${token}` } })
    : req;

  return next(authed).pipe(
    catchError((e: unknown) => {
      if (e instanceof HttpErrorResponse && e.status === 401 && req.url !== '/api/me' && auth.isSignedIn()) {
        auth.signOut(router.url);
      }
      return throwError(() => ApiError.from(e));
    }),
  );
};
