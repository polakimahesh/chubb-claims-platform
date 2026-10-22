import { Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

export interface ActionField {
  key: string;
  label: string;
  kind: 'text' | 'textarea' | 'amount' | 'select';
  required?: boolean;
  maxLength?: number;
  hint?: string;
  prefix?: string;
  options?: string[];
  value?: string | number | null;
}

export interface ActionDialogData {
  title: string;
  message?: string;
  confirm: string;
  danger?: boolean;
  fields?: ActionField[];
}

export type ActionResult = Record<string, string | number | null>;

/**
 * One dialog for every staff action: confirm-only (pick up, approve, settle) or with fields (assess, request info,
 * reject, reassign). Field rules mirror the backend request records (sizes, required, amount digits).
 */
@Component({
  selector: 'app-action-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <form [formGroup]="form" (ngSubmit)="form.valid && close()">
      <mat-dialog-content>
        @if (data.message) {
          <p class="message">{{ data.message }}</p>
        }
        @for (f of fields; track f.key) {
          <mat-form-field appearance="outline">
            <mat-label>{{ f.label }}</mat-label>
            @switch (f.kind) {
              @case ('textarea') {
                <textarea matInput [formControlName]="f.key" rows="4" [attr.maxlength]="f.maxLength"></textarea>
              }
              @case ('amount') {
                @if (f.prefix) {
                  <span matTextPrefix>{{ f.prefix }}&nbsp;</span>
                }
                <input matInput type="number" inputmode="decimal" step="0.01" min="0.01" [formControlName]="f.key" />
              }
              @case ('select') {
                <mat-select [formControlName]="f.key">
                  @for (o of f.options; track o) {
                    <mat-option [value]="o">{{ o }}</mat-option>
                  }
                </mat-select>
              }
              @default {
                <input matInput [formControlName]="f.key" [attr.maxlength]="f.maxLength" />
              }
            }
            @if (f.hint) {
              <mat-hint>{{ f.hint }}</mat-hint>
            }
            @if (f.maxLength && f.kind !== 'select') {
              <mat-hint align="end">{{ (form.get(f.key)?.value ?? '').toString().length }} / {{ f.maxLength }}</mat-hint>
            }
            <mat-error>{{ errorFor(f) }}</mat-error>
          </mat-form-field>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button type="submit" [class.danger]="data.danger" [disabled]="form.invalid">{{ data.confirm }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    mat-form-field { width: 100%; }
    mat-dialog-content { min-width: min(480px, 80vw); }
    .message { margin-top: 0; color: var(--app-text-muted); }
    .danger { background: var(--mat-sys-error); color: var(--mat-sys-on-error); }
  `,
})
export class ActionDialog {
  protected readonly data = inject<ActionDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<ActionDialog, ActionResult>>(MatDialogRef);
  protected readonly fields = this.data.fields ?? [];
  protected readonly form = new FormGroup(
    Object.fromEntries(this.fields.map((f) => [f.key, new FormControl(f.value ?? null, validators(f))])),
  );

  protected close(): void {
    const result: ActionResult = {};
    for (const f of this.fields) {
      const v = this.form.get(f.key)?.value;
      result[f.key] = f.kind === 'amount' ? Number(v) : typeof v === 'string' ? v.trim() || null : (v ?? null);
    }
    this.ref.close(result);
  }

  protected errorFor(f: ActionField): string {
    const c = this.form.get(f.key);
    if (c?.hasError('required') || c?.hasError('blank')) return `${f.label} is required`;
    if (c?.hasError('maxlength')) return `At most ${f.maxLength} characters`;
    if (c?.hasError('min')) return 'Must be greater than 0';
    if (c?.hasError('digits')) return 'Use up to 2 decimal places';
    return '';
  }
}

/** Rejects whitespace-only text, like @NotBlank on the backend. */
const notBlank: ValidatorFn = (c) => (typeof c.value === 'string' && c.value.trim() === '' ? { blank: true } : null);

const amountDigits: ValidatorFn = (c) =>
  c.value === null || c.value === '' || /^\d{1,13}(\.\d{1,2})?$/.test(String(c.value)) ? null : { digits: true };

function validators(f: ActionField): ValidatorFn[] {
  const v: ValidatorFn[] = [];
  if (f.required) v.push(Validators.required);
  if (f.required && f.kind !== 'amount') v.push(notBlank);
  if (f.maxLength) v.push(Validators.maxLength(f.maxLength));
  if (f.kind === 'amount') v.push(Validators.min(0.01), amountDigits);
  return v;
}
