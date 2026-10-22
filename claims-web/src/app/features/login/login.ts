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

/** Demo users created by the backend's claims-security module (the password is APP_SECURITY_DEMO_PASSWORD). */
const DEMO_USERS = [
  { username: 'tan@example.com', role: 'Claimant' },
  { username: 'lee@example.com', role: 'Claimant' },
  { username: 'officer-1', role: 'Officer' },
  { username: 'officer-2', role: 'Officer' },
  { username: 'manager-1', role: 'Manager' },
];

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
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly demoUsers = DEMO_USERS;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly hidePassword = signal(true);

  protected readonly form = inject(NonNullableFormBuilder).group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  protected pick(username: string): void {
    this.form.controls.username.setValue(username);
    document.getElementById('password')?.focus();
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
