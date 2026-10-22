import { HttpClient } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { signedInAs, testProviders } from '../../testing';
import { ApiError } from './api-error';
import { AuthService } from './auth.service';
import { authGuard, guestGuard, homeRedirect, roleGuard } from './guards';

@Component({ template: '' })
class Blank {}

function setup() {
  TestBed.configureTestingModule({
    providers: testProviders([{ path: '**', component: Blank }]),
  });
  return {
    auth: TestBed.inject(AuthService),
    http: TestBed.inject(HttpClient),
    httpMock: TestBed.inject(HttpTestingController),
    router: TestBed.inject(Router),
  };
}

afterEach(() => sessionStorage.clear());

describe('AuthService', () => {
  it('verifies credentials with GET /api/me using HTTP Basic, then keeps the session', async () => {
    const { auth, httpMock } = setup();
    const signIn = auth.signIn('officer-1', 'secret');
    const req = httpMock.expectOne('/api/me');
    expect(req.request.headers.get('Authorization')).toBe(`Basic ${btoa('officer-1:secret')}`);
    req.flush({ username: 'officer-1', roles: ['OFFICER'] });

    expect(await signIn).toEqual({ username: 'officer-1', roles: ['OFFICER'] });
    expect(auth.isSignedIn()).toBe(true);
    expect(auth.hasRole('OFFICER')).toBe(true);
    expect(auth.hasRole('MANAGER')).toBe(false);
    expect(JSON.parse(sessionStorage.getItem('claims.session')!).user.username).toBe('officer-1');
  });

  it('encodes non-ASCII credentials as UTF-8', async () => {
    const { auth, httpMock } = setup();
    const signIn = auth.signIn('tan@example.com', 'pässwörd');
    const req = httpMock.expectOne('/api/me');
    const header = req.request.headers.get('Authorization')!;
    expect(new TextDecoder().decode(Uint8Array.from(atob(header.slice(6)), (c) => c.charCodeAt(0)))).toBe('tan@example.com:pässwörd');
    req.flush({ username: 'tan@example.com', roles: ['CLAIMANT'] });
    await signIn;
  });

  it('rejects wrong credentials with a 401 ApiError and stays signed out', async () => {
    const { auth, httpMock } = setup();
    const signIn = auth.signIn('officer-1', 'wrong');
    httpMock.expectOne('/api/me').flush({ status: 401, detail: 'Authentication is required with valid credentials.' }, { status: 401, statusText: 'Unauthorized' });
    const err = await signIn.catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(auth.isSignedIn()).toBe(false);
    expect(sessionStorage.getItem('claims.session')).toBeNull();
  });

  it('restores a session after a page reload and sends each role to its home', () => {
    signedInAs('manager-1', 'MANAGER');
    expect(setup().auth.homeUrl()).toBe('/dashboard');
    TestBed.resetTestingModule();
    signedInAs('officer-2', 'OFFICER');
    expect(setup().auth.homeUrl()).toBe('/queue');
    TestBed.resetTestingModule();
    signedInAs('lee@example.com', 'CLAIMANT');
    expect(setup().auth.homeUrl()).toBe('/my-claims');
  });

  it('ignores a corrupt stored session', () => {
    sessionStorage.setItem('claims.session', '{not json');
    expect(setup().auth.isSignedIn()).toBe(false);
  });

  it('signs out: clears the session and returns to sign-in with the return URL', async () => {
    signedInAs('officer-1', 'OFFICER');
    const { auth, router } = setup();
    auth.signOut('/queue');
    await new Promise((r) => setTimeout(r));
    expect(auth.isSignedIn()).toBe(false);
    expect(sessionStorage.getItem('claims.session')).toBeNull();
    expect(router.url).toBe('/login?returnUrl=%2Fqueue');
  });
});

describe('authInterceptor', () => {
  it('adds the Basic credentials to API calls only', () => {
    signedInAs('officer-1', 'OFFICER');
    const { http, httpMock } = setup();
    http.get('/api/staff/claims/queue').subscribe();
    http.get('/assets/logo.svg').subscribe();
    expect(httpMock.expectOne('/api/staff/claims/queue').request.headers.get('Authorization')).toBe(`Basic ${btoa('officer-1:pw')}`);
    expect(httpMock.expectOne('/assets/logo.svg').request.headers.has('Authorization')).toBe(false);
  });

  it('sends no credentials when signed out', () => {
    const { http, httpMock } = setup();
    http.get('/api/claims').subscribe({ error: () => undefined });
    expect(httpMock.expectOne('/api/claims').request.headers.has('Authorization')).toBe(false);
  });

  it('turns problem responses into ApiError with the server detail and field errors', async () => {
    signedInAs('tan@example.com', 'CLAIMANT');
    const { http, httpMock } = setup();
    const call = firstValueFrom(http.post('/api/claims', {}));
    httpMock.expectOne('/api/claims').flush(
      { status: 400, detail: 'Validation failed', errors: ['description: must not be blank'] },
      { status: 400, statusText: 'Bad Request' },
    );
    const err = await call.catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.message).toBe('Validation failed');
    expect(err.fieldErrors).toEqual(['description: must not be blank']);
  });

  it('signs out on 401 from an API call (expired or changed credentials)', async () => {
    signedInAs('officer-1', 'OFFICER');
    const { http, httpMock, auth } = setup();
    const call = firstValueFrom(http.get('/api/staff/claims'));
    httpMock.expectOne('/api/staff/claims').flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });
    await call.catch(() => undefined);
    expect(auth.isSignedIn()).toBe(false);
  });

  it('does not sign out on 403 (signed in, but not allowed)', async () => {
    signedInAs('tan@example.com', 'CLAIMANT');
    const { http, httpMock, auth } = setup();
    const call = firstValueFrom(http.get('/api/staff/claims/queue'));
    httpMock.expectOne('/api/staff/claims/queue').flush({ status: 403, detail: 'You do not have permission' }, { status: 403, statusText: 'Forbidden' });
    expect((await call.catch((e) => e)).status).toBe(403);
    expect(auth.isSignedIn()).toBe(true);
  });
});

describe('route guards', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = (url: string) => ({ url }) as RouterStateSnapshot;
  const run = (guard: typeof authGuard, url = '/x') => TestBed.runInInjectionContext(() => guard(route, state(url)));
  const target = (r: unknown) => (r instanceof UrlTree ? TestBed.inject(Router).serializeUrl(r) : r);

  it('authGuard sends anonymous users to sign-in with a return URL', () => {
    setup();
    expect(target(run(authGuard, '/claims/1'))).toBe('/login?returnUrl=%2Fclaims%2F1');
  });

  it('authGuard lets signed-in users through', () => {
    signedInAs('tan@example.com', 'CLAIMANT');
    setup();
    expect(run(authGuard)).toBe(true);
  });

  it('roleGuard redirects other roles to their own home', () => {
    signedInAs('tan@example.com', 'CLAIMANT');
    setup();
    expect(target(run(roleGuard('OFFICER', 'MANAGER')))).toBe('/my-claims');
    expect(run(roleGuard('CLAIMANT'))).toBe(true);
  });

  it('homeRedirect and guestGuard use the role home', () => {
    signedInAs('manager-1', 'MANAGER');
    setup();
    expect(target(run(homeRedirect))).toBe('/dashboard');
    expect(target(run(guestGuard))).toBe('/dashboard');
  });

  it('guestGuard shows sign-in to anonymous users', () => {
    setup();
    expect(run(guestGuard)).toBe(true);
  });
});
