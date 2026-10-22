import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiError } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { ThemeService } from '../../core/theme.service';
import { HttpClient } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';

/** Demo accounts, loaded from the resource file public/config/demo-accounts.json (not kept in source code). */
export interface DemoAccounts {
  password: string;
  users: { username: string; role: string }[];
}

export const DEMO_ACCOUNTS_URL = 'config/demo-accounts.json';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIcon, MatChipsModule, MatProgressBar,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** Demo accounts from the resource file; null until loaded (the hint is simply hidden if it cannot be loaded). */
  protected readonly demo = toSignal(
    inject(HttpClient).get<DemoAccounts>(DEMO_ACCOUNTS_URL).pipe(catchError(() => of(null))),
    { initialValue: null },
  );
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly hidePassword = signal(true);

  protected readonly form = inject(NonNullableFormBuilder).group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  /** Fills in a demo account (username and the shared demo password); the user then presses Sign in. */
  protected pick(username: string): void {
    this.form.setValue({ username, password: this.demo()?.password ?? '' });
    this.error.set(null);
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const { username, password } = this.form.getRawValue();
    try {
      await this.auth.signIn(username.trim(), password);
      const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
      await this.router.navigateByUrl(returnUrl && returnUrl.startsWith('/') ? returnUrl : this.auth.homeUrl());
    } catch (e) {
      const err = ApiError.from(e);
      this.error.set(err.status === 401 ? 'Incorrect username or password.' : err.message);
    } finally {
      this.busy.set(false);
    }
  }
}
