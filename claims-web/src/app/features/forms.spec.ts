import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { signedInAs, testProviders } from '../../testing';
import { FilePicker } from '../shared/file-picker';
import { ActionDialog, ActionDialogData } from './claim/action-dialog';
import { NewClaim } from './claimant/new-claim';
import { Login } from './login/login';

afterEach(() => sessionStorage.clear());

describe('Login', () => {
  function create() {
    TestBed.configureTestingModule({ imports: [Login], providers: testProviders() });
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    return { fixture, http: TestBed.inject(HttpTestingController) };
  }

  it('requires both fields before calling the server', async () => {
    const { fixture, http } = create();
    await (fixture.componentInstance as unknown as { submit(): Promise<void> }).submit();
    http.expectNone('/api/me');
  });

  it('shows a clear message for wrong credentials', async () => {
    const { fixture, http } = create();
    const c = fixture.componentInstance as unknown as { form: { setValue(v: object): void }; submit(): Promise<void>; error(): string | null };
    c.form.setValue({ username: 'tan@example.com', password: 'nope' });
    const done = c.submit();
    http.expectOne('/api/me').flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });
    await done;
    expect(c.error()).toBe('Incorrect username or password.');
  });

  it('explains when the server cannot be reached', async () => {
    const { fixture, http } = create();
    const c = fixture.componentInstance as unknown as { form: { setValue(v: object): void }; submit(): Promise<void>; error(): string | null };
    c.form.setValue({ username: 'officer-1', password: 'x' });
    const done = c.submit();
    http.expectOne('/api/me').error(new ProgressEvent('error'), { status: 0 });
    await done;
    expect(c.error()).toContain('Cannot reach the server');
  });

  it('signs in and goes to the role home', async () => {
    const { fixture, http } = create();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const c = fixture.componentInstance as unknown as { form: { setValue(v: object): void }; submit(): Promise<void> };
    c.form.setValue({ username: ' manager-1 ', password: 'pw' });
    const done = c.submit();
    const req = http.expectOne('/api/me');
    expect(req.request.headers.get('Authorization')).toBe(`Basic ${btoa('manager-1:pw')}`); // username trimmed
    req.flush({ username: 'manager-1', roles: ['MANAGER'] });
    await done;
    expect(navigate).toHaveBeenCalledWith('/dashboard');
  });
});

describe('NewClaim (mirrors ClaimRequests.Submit)', () => {
  type Form = {
    incident: { controls: Record<string, { setValue(v: unknown): void; valid: boolean; errors: unknown }>; valid: boolean };
    amount: { controls: Record<string, { setValue(v: unknown): void; valid: boolean; errors: unknown }>; valid: boolean };
    currency(): string; files: { set(f: File[]): void }; submit(): Promise<void>; serverErrors(): string[];
  };

  function create() {
    signedInAs('tan@example.com', 'CLAIMANT');
    TestBed.configureTestingModule({ imports: [NewClaim], providers: testProviders() });
    const fixture = TestBed.createComponent(NewClaim);
    fixture.detectChanges();
    return { c: fixture.componentInstance as unknown as Form, http: TestBed.inject(HttpTestingController) };
  }

  function fill(c: Form) {
    c.incident.controls['claimType'].setValue('PROPERTY');
    c.incident.controls['market'].setValue('ID');
    c.incident.controls['incidentDate'].setValue(new Date(2026, 0, 31, 23, 0));
    c.incident.controls['description'].setValue('  Flooded kitchen  ');
    c.amount.controls['claimantName'].setValue(' Tan Wei ');
    c.amount.controls['estimatedAmount'].setValue(15000000.5);
  }

  it('derives the currency from the market', () => {
    const { c } = create();
    expect(c.currency()).toBe('');
    c.incident.controls['market'].setValue('TH');
    expect(c.currency()).toBe('THB');
    c.incident.controls['market'].setValue('AU');
    expect(c.currency()).toBe('AUD');
  });

  it('validates amount boundaries: > 0, at most 2 decimals, at most 13 integer digits', () => {
    const { c } = create();
    const amount = c.amount.controls['estimatedAmount'];
    for (const [value, ok] of [[0, false], [0.001, false], [0.01, true], [12.345, false], [9999999999999.99, true], [10000000000000, false], [null, false]] as const) {
      amount.setValue(value);
      expect(amount.valid, `amount ${value}`).toBe(ok);
    }
  });

  it('limits text lengths like the backend (name 200, description 4000)', () => {
    const { c } = create();
    c.amount.controls['claimantName'].setValue('x'.repeat(201));
    expect(c.amount.controls['claimantName'].valid).toBe(false);
    c.incident.controls['description'].setValue('x'.repeat(4001));
    expect(c.incident.controls['description'].valid).toBe(false);
    c.incident.controls['description'].setValue('x'.repeat(4000));
    expect(c.incident.controls['description'].valid).toBe(true);
  });

  it('submits the payload with the derived currency and a local date, then uploads the documents', async () => {
    const { c, http } = create();
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fill(c);
    c.files.set([new File(['%PDF'], 'a.pdf', { type: 'application/pdf' })]);
    const done = c.submit();
    const req = http.expectOne('/api/claims');
    expect(req.request.body).toEqual({
      claimType: 'PROPERTY', market: 'ID', incidentDate: '2026-01-31', description: 'Flooded kitchen',
      claimantName: 'Tan Wei', currency: 'IDR', estimatedAmount: 15000000.5,
    });
    req.flush({ id: 'new', claimNumber: 'CLM-ID-2026-12345678' });
    await new Promise((r) => setTimeout(r));
    http.expectOne('/api/claims/new/documents').flush({});
    await done;
  });

  it('shows the server validation errors when the backend refuses', async () => {
    const { c, http } = create();
    fill(c);
    const done = c.submit();
    http.expectOne('/api/claims').flush(
      { status: 400, detail: 'Validation failed', errors: ['incidentDate: must be a date in the past or in the present'] },
      { status: 400, statusText: 'Bad Request' });
    await done;
    expect(c.serverErrors()).toEqual(['incidentDate: must be a date in the past or in the present']);
  });

  it('does not submit an incomplete form', async () => {
    const { c, http } = create();
    await c.submit();
    http.expectNone('/api/claims');
  });
});

describe('FilePicker', () => {
  function create(capacity = 20) {
    TestBed.configureTestingModule({ imports: [FilePicker] });
    const fixture = TestBed.createComponent(FilePicker);
    fixture.componentRef.setInput('capacity', capacity);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input[type=file]') as HTMLInputElement;
    const choose = (...files: File[]) => {
      Object.defineProperty(input, 'files', { value: files, configurable: true });
      input.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    };
    return { fixture, choose };
  }
  const pdf = (name = 'a.pdf') => new File(['%PDF'], name, { type: 'application/pdf' });

  it('keeps valid files and explains rejected ones', () => {
    const { fixture, choose } = create();
    choose(pdf(), new File(['x'], 'b.exe', { type: 'application/x-msdownload' }));
    expect(fixture.componentInstance.files().map((f) => f.name)).toEqual(['a.pdf']);
    expect(fixture.nativeElement.textContent).toContain('b.exe: only PDF, PNG and JPEG files are accepted');
  });

  it('stops at the claim document limit', () => {
    const { fixture, choose } = create(2);
    choose(pdf('1.pdf'), pdf('2.pdf'), pdf('3.pdf'));
    expect(fixture.componentInstance.files()).toHaveLength(2);
    expect(fixture.nativeElement.textContent).toContain('3.pdf: a claim can have at most 20 documents');
  });

  it('removes a chosen file', () => {
    const { fixture, choose } = create();
    choose(pdf('1.pdf'), pdf('2.pdf'));
    (fixture.nativeElement.querySelector('button[aria-label="Remove 1.pdf"]') as HTMLButtonElement).click();
    expect(fixture.componentInstance.files().map((f) => f.name)).toEqual(['2.pdf']);
  });
});

describe('ActionDialog', () => {
  function create(data: ActionDialogData) {
    const close = vi.fn();
    TestBed.configureTestingModule({
      imports: [ActionDialog],
      providers: [{ provide: MAT_DIALOG_DATA, useValue: data }, { provide: MatDialogRef, useValue: { close } }],
    });
    const fixture = TestBed.createComponent(ActionDialog);
    fixture.detectChanges();
    const c = fixture.componentInstance as unknown as { form: { get(k: string): { setValue(v: unknown): void; valid: boolean } | null; valid: boolean }; close(): void };
    return { c, close };
  }

  it('requires a non-blank reason (like @NotBlank) and trims it', () => {
    const { c, close } = create({
      title: 'Reject', confirm: 'Reject claim',
      fields: [{ key: 'reason', label: 'Reason', kind: 'textarea', required: true, maxLength: 1000 }],
    });
    c.form.get('reason')!.setValue('   ');
    expect(c.form.valid).toBe(false);
    c.form.get('reason')!.setValue('x'.repeat(1001));
    expect(c.form.valid).toBe(false);
    c.form.get('reason')!.setValue('  Not covered  ');
    expect(c.form.valid).toBe(true);
    c.close();
    expect(close).toHaveBeenCalledWith({ reason: 'Not covered' });
  });

  it('validates the assessed amount and turns optional blanks into null', () => {
    const { c, close } = create({
      title: 'Assess', confirm: 'Save',
      fields: [
        { key: 'amount', label: 'Amount', kind: 'amount', required: true, value: 5000 },
        { key: 'note', label: 'Note', kind: 'textarea', maxLength: 2000 },
      ],
    });
    c.form.get('amount')!.setValue(0);
    expect(c.form.valid).toBe(false);
    c.form.get('amount')!.setValue(10.123);
    expect(c.form.valid).toBe(false);
    c.form.get('amount')!.setValue(4200.5);
    c.form.get('note')!.setValue('   ');
    expect(c.form.valid).toBe(true);
    c.close();
    expect(close).toHaveBeenCalledWith({ amount: 4200.5, note: null });
  });
});
