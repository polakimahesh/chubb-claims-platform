import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentProviders, Provider } from '@angular/core';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideRouter, Routes } from '@angular/router';
import { authInterceptor } from './app/core/auth.interceptor';
import { Role } from './app/core/models';

/** Test helpers shared by the specs. */

/** Starts a test as a signed-in user (AuthService restores the session from sessionStorage when created). */
export function signedInAs(username: string, ...roles: Role[]): void {
  sessionStorage.setItem('claims.session', JSON.stringify({ user: { username, roles }, token: btoa(`${username}:pw`) }));
}

/** HttpClient with the real interceptor against HttpTestingController, a router, and the date adapter. */
export function testProviders(routes: Routes = []): (Provider | EnvironmentProviders)[] {
  return [
    provideHttpClient(withInterceptors([authInterceptor])),
    provideHttpClientTesting(),
    provideRouter(routes),
    provideNativeDateAdapter(),
  ];
}

/** Text content with whitespace normalised (Intl puts a non-breaking space between currency code and amount). */
export function text(el: Element): string {
  return (el.textContent ?? '').replace(/\s+/g, ' ');
}

// Rendering the first Material component of a file under jsdom can take several seconds.
vi.setConfig({ testTimeout: 30_000 });
