import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatTooltip } from '@angular/material/tooltip';
import { Location } from '@angular/common';
import { firstValueFrom, forkJoin, Observable } from 'rxjs';
import { ApiError } from '../../core/api-error';
import { ClaimsApi } from '../../core/api/claims.api';
import { AuthService } from '../../core/auth.service';
import { ClaimDetail as Claim, DocumentInfo, HistoryEntry, InfoRequest } from '../../core/models';
import { DOCUMENT_RULES, isTerminal, LIFECYCLE, STATUS_META, staffActions } from '../../core/reference';
import { ToastService } from '../../core/toast.service';
import { EmptyState } from '../../shared/empty-state';
import { FilePicker } from '../../shared/file-picker';
import { ClaimTypePipe, FileSizePipe, MarketPipe, MoneyPipe } from '../../shared/pipes';
import { StatusChip } from '../../shared/status-chip';
import { ActionDialog, ActionDialogData, ActionResult } from './action-dialog';

/**
 * One claim, for every role (GET /api/claims/{id} plus history, info requests and documents).
 * Claimants answer information requests and attach documents; staff get the actions their role and the claim's state
 * allow (see staffActions). After any change the whole claim is reloaded, so the screen always shows server state.
 */
@Component({
  selector: 'app-claim-detail',
  imports: [
    DatePipe, ReactiveFormsModule, MatCardModule, MatButtonModule, MatIcon, MatProgressBar, MatTooltip, MatFormFieldModule,
    MatInputModule, StatusChip, EmptyState, FilePicker, MoneyPipe, MarketPipe, ClaimTypePipe, FileSizePipe,
  ],
  templateUrl: './claim-detail.html',
  styleUrl: './claim-detail.scss',
})
export class ClaimDetailPage implements OnInit {
  /** Route parameter (withComponentInputBinding). */
  readonly id = input.required<string>();

  private readonly api = inject(ClaimsApi);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly toast = inject(ToastService);
  private readonly location = inject(Location);
  private readonly money = new MoneyPipe();

  protected readonly claim = signal<Claim | null>(null);
  protected readonly history = signal<HistoryEntry[]>([]);
  protected readonly infoRequests = signal<InfoRequest[]>([]);
  protected readonly documents = signal<DocumentInfo[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly notFound = signal(false);
  protected readonly newFiles = signal<File[]>([]);
  protected readonly answers = new Map<string, FormControl<string>>();

  protected readonly isClaimant = computed(() => this.auth.hasRole('CLAIMANT'));
  protected readonly meta = computed(() => STATUS_META[this.claim()?.status ?? 'SUBMITTED']);
  protected readonly actions = computed(() => {
    const c = this.claim();
    const u = this.auth.user();
    return c && u && !this.isClaimant() ? staffActions(c, u) : null;
  });
  protected readonly hasStaffActions = computed(() => {
    const a = this.actions();
    return !!a && (a.pickUp || a.requestInfo || a.assess || a.approve || a.reject || a.settle || a.reassign);
  });
  protected readonly closed = computed(() => !!this.claim() && isTerminal(this.claim()!.status));
  protected readonly openRequests = computed(() => this.infoRequests().filter((r) => r.open));
  protected readonly canUpload = computed(() => {
    const c = this.claim();
    return this.isClaimant() && !!c && !isTerminal(c.status) && this.documents().length < DOCUMENT_RULES.maxPerClaim;
  });
  protected readonly uploadCapacity = computed(() => DOCUMENT_RULES.maxPerClaim - this.documents().length);

  /** Progress tracker: the happy path, with REJECTED replacing the tail when it applies. */
  protected readonly steps = computed(() => {
    const c = this.claim();
    if (!c) return [];
    const path = c.status === 'REJECTED' ? ['SUBMITTED', 'UNDER_REVIEW', 'REJECTED'] as const : LIFECYCLE;
    const current = c.status === 'INFO_REQUESTED' ? 'UNDER_REVIEW' : c.status;
    const at = path.indexOf(current as never);
    return path.map((s, i) => ({
      status: s,
      label: STATUS_META[s].label,
      state: i < at || (i === at && isTerminal(c.status)) ? 'done' : i === at ? 'current' : 'todo',
    }));
  });

  ngOnInit(): void {
    this.load();
  }

  protected back(): void {
    this.location.back();
  }

  protected load(): void {
    const id = this.id();
    this.loading.set(true);
    forkJoin({
      claim: this.api.get(id),
      history: this.api.history(id),
      info: this.api.infoRequests(id),
      docs: this.api.documents(id),
    }).subscribe({
      next: ({ claim, history, info, docs }) => {
        this.claim.set(claim);
        this.history.set([...history].reverse());
        this.infoRequests.set(info);
        this.documents.set(docs);
        for (const r of info.filter((x) => x.open)) {
          if (!this.answers.has(r.id)) {
            this.answers.set(r.id, new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(4000)] }));
          }
        }
        this.loading.set(false);
      },
      error: (e) => {
        this.loading.set(false);
        if (ApiError.from(e).status === 404) this.notFound.set(true);
        else this.toast.error(e);
      },
    });
  }

  // ---------- claimant ----------

  protected async answer(request: InfoRequest): Promise<void> {
    const control = this.answers.get(request.id)!;
    if (control.invalid || !control.value.trim()) {
      control.markAsTouched();
      return;
    }
    const lastOpen = this.openRequests().length === 1;
    const ok = await this.run(this.api.respond(this.id(), request.id, control.value.trim()),
      lastOpen ? 'Thank you. Your claim is back with the claims officer.' : 'Answer sent.');
    if (ok) this.answers.delete(request.id);
  }

  protected async uploadFiles(): Promise<void> {
    const files = this.newFiles();
    if (!files.length) return;
    this.busy.set(true);
    let uploaded = 0;
    for (const file of files) {
      try {
        await firstValueFrom(this.api.upload(this.id(), file));
        uploaded++;
      } catch (e) {
        this.toast.error(e);
      }
    }
    this.busy.set(false);
    this.newFiles.set([]);
    if (uploaded) this.toast.success(`${uploaded} document${uploaded > 1 ? 's' : ''} attached.`);
    this.load();
  }

  protected download(doc: DocumentInfo): void {
    this.api.download(this.id(), doc.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = doc.fileName;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: (e) => this.toast.error(e),
    });
  }

  // ---------- staff ----------

  protected pickUp(): void {
    void this.confirm({ title: 'Pick up this claim?', message: 'It will be assigned to you and move to Under review.', confirm: 'Pick up' },
      () => this.api.assign(this.id()), 'Claim assigned to you.');
  }

  protected requestInfo(): void {
    void this.confirm({
      title: 'Request information', confirm: 'Send request',
      message: 'The claimant is notified, and the claim waits for their answer.',
      fields: [{ key: 'question', label: 'What do you need from the claimant?', kind: 'textarea', required: true, maxLength: 2000 }],
    }, (r) => this.api.requestInfo(this.id(), String(r['question'])), 'Information requested from the claimant.');
  }

  protected assess(): void {
    const c = this.claim()!;
    void this.confirm({
      title: 'Record assessment', confirm: 'Save assessment',
      message: `Claimant estimate: ${this.money.transform(c.estimatedAmount, c.currency)}. The assessed amount becomes the claim's liability exposure.`,
      fields: [
        { key: 'amount', label: 'Assessed amount', kind: 'amount', required: true, prefix: c.currency, value: c.assessedAmount ?? c.estimatedAmount },
        { key: 'note', label: 'Assessment note (optional)', kind: 'textarea', maxLength: 2000 },
      ],
    }, (r) => this.api.assess(this.id(), Number(r['amount']), r['note'] as string | null), 'Assessment recorded.');
  }

  protected approve(): void {
    const c = this.claim()!;
    void this.confirm({
      title: 'Approve claim?', confirm: 'Approve',
      message: `Approve ${c.claimNumber} at the assessed amount of ${this.money.transform(c.assessedAmount, c.currency)}. The claimant is notified.`,
    }, () => this.api.approve(this.id()), 'Claim approved.');
  }

  protected reject(): void {
    void this.confirm({
      title: 'Reject claim', confirm: 'Reject claim', danger: true,
      message: 'This closes the claim. The reason is shared with the claimant.',
      fields: [{ key: 'reason', label: 'Reason for rejection', kind: 'textarea', required: true, maxLength: 1000 }],
    }, (r) => this.api.reject(this.id(), String(r['reason'])), 'Claim rejected.');
  }

  protected settle(): void {
    void this.confirm({ title: 'Settle claim?', confirm: 'Mark as settled', message: 'Confirms that payment has been made. This closes the claim.' },
      () => this.api.settle(this.id()), 'Claim settled.');
  }

  protected async reassign(): Promise<void> {
    const current = this.claim()!.assignedOfficerId;
    let officers: string[];
    try {
      officers = (await firstValueFrom(this.api.officers())).filter((o) => o !== current);
    } catch (e) {
      this.toast.error(e);
      return;
    }
    void this.confirm({
      title: 'Reassign claim', confirm: 'Reassign',
      message: `Currently assigned to ${current}. The status does not change.`,
      fields: [
        { key: 'officer', label: 'New officer', kind: 'select', required: true, options: officers },
        { key: 'reason', label: 'Reason (optional)', kind: 'text', maxLength: 500 },
      ],
    }, (r) => this.api.reassign(this.id(), String(r['officer']), r['reason'] as string | null), 'Claim reassigned.');
  }

  // ---------- helpers ----------

  private async confirm(data: ActionDialogData, call: (r: ActionResult) => Observable<unknown>, success: string): Promise<void> {
    const result = await firstValueFrom(
      this.dialog.open<ActionDialog, ActionDialogData, ActionResult>(ActionDialog, { data, autoFocus: 'first-tabbable' }).afterClosed(),
    );
    if (result) await this.run(call(result), success);
  }

  /** Runs a command, reports the outcome and reloads (also after a 409, so a stale screen corrects itself). */
  private async run(call: Observable<unknown>, success: string): Promise<boolean> {
    this.busy.set(true);
    try {
      await firstValueFrom(call);
      this.toast.success(success);
      return true;
    } catch (e) {
      this.toast.error(e);
      return false;
    } finally {
      this.busy.set(false);
      this.load();
    }
  }
}
