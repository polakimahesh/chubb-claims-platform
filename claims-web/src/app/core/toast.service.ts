import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiError } from './api-error';

/** Short confirmations and error messages. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.snackBar.open(message, 'OK', { duration: 4000, panelClass: 'toast-success' });
  }

  error(e: unknown): void {
    const err = ApiError.from(e);
    const message = err.fieldErrors.length ? `${err.message}: ${err.fieldErrors.join('; ')}` : err.message;
    this.snackBar.open(message, 'Dismiss', { duration: 8000, panelClass: 'toast-error' });
  }
}
