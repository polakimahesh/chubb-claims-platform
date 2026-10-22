import { HttpClient, HttpHeaders } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { CurrentUser, Role } from './models';

const STORAGE_KEY = 'claims.session';

interface Session {
  user: CurrentUser;
  /** base64(username:password) for HTTP Basic. */
  token: string;
}

/**
 * Holds the signed-in user. The backend is stateless HTTP Basic, so the credentials are sent on every request.
 * They are kept in sessionStorage (cleared when the tab closes) so a page refresh does not sign the user out.
 * With an OIDC provider this would hold a short-lived bearer token instead; nothing else in the app would change.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isSignedIn = computed(() => this.session() !== null);

  get token(): string | null {
    return this.session()?.token ?? null;
  }

  hasRole(...roles: Role[]): boolean {
    const user = this.user();
    return !!user && roles.some((r) => user.roles.includes(r));
  }

  /** Verifies the credentials against GET /api/me and learns the roles. Throws ApiError-compatible errors. */
  async signIn(username: string, password: string): Promise<CurrentUser> {
    const token = btoa(String.fromCharCode(...new TextEncoder().encode(`${username}:${password}`)));
    const user = await firstValueFrom(
      this.http.get<CurrentUser>('/api/me', { headers: new HttpHeaders({ Authorization: `Basic ${token}` }) }),
    );
    const session = { user, token };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    this.session.set(session);
    return user;
  }

  signOut(returnUrl?: string): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
    void this.router.navigate(['/login'], { queryParams: returnUrl ? { returnUrl } : {} });
  }

  /** Landing page for the user's role. */
  homeUrl(): string {
    if (this.hasRole('MANAGER')) return '/dashboard';
    if (this.hasRole('OFFICER')) return '/queue';
    return '/my-claims';
  }
}

function restore(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}
