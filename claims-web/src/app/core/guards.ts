import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from './models';
import { AuthService } from './auth.service';

/** Requires a signed-in user; otherwise goes to sign-in and comes back afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.isSignedIn() || inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/**
 * Hides screens a role cannot use. This is convenience only: the backend's URL and record rules are the real check.
 */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    return auth.hasRole(...roles) || inject(Router).parseUrl(auth.homeUrl());
  };
}

/** Sends '/' to the user's home screen. */
export const homeRedirect: CanActivateFn = () => {
  const auth = inject(AuthService);
  return inject(Router).parseUrl(auth.homeUrl());
};

/** Signed-in users skip the sign-in page. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return !auth.isSignedIn() || inject(Router).parseUrl(auth.homeUrl());
};
