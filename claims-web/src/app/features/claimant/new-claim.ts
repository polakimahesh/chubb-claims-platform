import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatStepperModule } from '@angular/material/stepper';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ApiError } from '../../core/api-error';
import { ClaimsApi } from '../../core/api/claims.api';
import { AuthService } from '../../core/auth.service';
import { ClaimType, Market } from '../../core/models';
import { CLAIM_TYPES, currencyFor, MARKETS, toLocalDate } from '../../core/reference';
import { ToastService } from '../../core/toast.service';
import { FilePicker } from '../../shared/file-picker';
import { PageHeader } from '../../shared/page-header';
import { ClaimTypePipe, MarketPipe, MoneyPipe } from '../../shared/pipes';

/** At most 13 integer digits and 2 decimals (backend: @Digits(integer = 13, fraction = 2)). */
function moneyDigits(c: AbstractControl): ValidationErrors | null {
  const v = c.value;
  if (v === null || v === '' || v === undefined) return null;
  return /^\d{1,13}(\.\d{1,2})?$/.test(String(v)) ? null : { digits: true };
}

/**
 * Claimant: report an incident (POST /api/claims), then attach documents (POST /api/claims/{id}/documents).
 * The validators mirror ClaimRequests.Submit so most mistakes are caught before the request is sent.
 */
@Component({
  selector: 'app-new-claim',
  imports: [
    ReactiveFormsModule, PageHeader, FilePicker, MatCardModule, MatStepperModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatDatepickerModule, MatButtonModule, MatButtonToggleModule, MatIcon, MoneyPipe, MarketPipe, ClaimTypePipe,
  ],
  templateUrl: './new-claim.html',
  styleUrl: './new-claim.scss',
})
export class NewClaim {
  private readonly api = inject(ClaimsApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly markets = MARKETS;
  protected readonly claimTypes = CLAIM_TYPES;
  protected readonly today = new Date();
  protected readonly submitting = signal(false);
  protected readonly serverErrors = signal<string[]>([]);
  protected readonly files = signal<File[]>([]);

  protected readonly incident = this.fb.group({
    claimType: this.fb.control<ClaimType | null>(null, Validators.required),
    market: this.fb.control<Market | null>(null, Validators.required),
    incidentDate: this.fb.control<Date | null>(null, Validators.required),
    description: ['', [Validators.required, Validators.maxLength(4000)]],
  });

  protected readonly amount = this.fb.group({
    claimantName: ['', [Validators.required, Validators.maxLength(200)]],
    estimatedAmount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01), moneyDigits]),
  });

  private readonly market = toSignal(this.incident.controls.market.valueChanges, { initialValue: null });
  /** Each market settles in one currency, so it is derived rather than chosen. */
  protected readonly currency = computed(() => {
    const m = this.market();
    return m ? currencyFor(m) : '';
  });

  protected readonly descriptionLength = toSignal(this.incident.controls.description.valueChanges, { initialValue: '' });

  /** The claim is filed under the signed-in e-mail; the backend takes it from the credentials, never the form. */
  protected readonly email = inject(AuthService).user()?.username ?? '';

  protected async submit(): Promise<void> {
    if (this.incident.invalid || this.amount.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.serverErrors.set([]);
    const i = this.incident.getRawValue();
    const a = this.amount.getRawValue();
    try {
      const claim = await firstValueFrom(this.api.submit({
        claimType: i.claimType!,
        market: i.market!,
        incidentDate: toLocalDate(i.incidentDate!),
        description: i.description.trim(),
        claimantName: a.claimantName.trim(),
        currency: this.currency(),
        estimatedAmount: Number(a.estimatedAmount),
      }));
      const failed = await this.uploadAll(claim.id);
      this.toast.success(failed.length
        ? `Claim ${claim.claimNumber} submitted. ${failed.length} document(s) could not be attached; try again from the claim page.`
        : `Claim ${claim.claimNumber} submitted.`);
      await this.router.navigate(['/claims', claim.id]);
    } catch (e) {
      const err = ApiError.from(e);
      this.serverErrors.set(err.fieldErrors.length ? err.fieldErrors : [err.message]);
    } finally {
      this.submitting.set(false);
    }
  }

  /** Uploads the chosen files one by one; returns the names that failed. */
  private async uploadAll(claimId: string): Promise<string[]> {
    const failed: string[] = [];
    for (const file of this.files()) {
      try {
        await firstValueFrom(this.api.upload(claimId, file));
      } catch {
        failed.push(file.name);
      }
    }
    return failed;
  }
}
