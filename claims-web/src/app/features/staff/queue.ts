import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { PageEvent } from '@angular/material/paginator';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltip } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { ClaimsApi } from '../../core/api/claims.api';
import { AuthService } from '../../core/auth.service';
import { ClaimSummary, ClaimType, Market } from '../../core/models';
import { CLAIM_TYPES, MARKETS } from '../../core/reference';
import { ToastService } from '../../core/toast.service';
import { ClaimColumn, ClaimTable } from '../../shared/claim-table';
import { EmptyState } from '../../shared/empty-state';
import { PageHeader } from '../../shared/page-header';

/**
 * Intake queue: unassigned (SUBMITTED) claims, oldest first, filterable by market and type
 * (GET /api/staff/claims/queue). Officers pick claims up from here; managers see it read-only.
 */
@Component({
  selector: 'app-queue',
  imports: [
    FormsModule, PageHeader, ClaimTable, EmptyState, MatCardModule, MatFormFieldModule, MatSelectModule, MatButtonModule,
    MatIcon, MatProgressBar, MatTooltip,
  ],
  template: `
    <app-page-header title="Intake queue" subtitle="New claims waiting for an officer, oldest first.">
      <button mat-icon-button (click)="load()" matTooltip="Refresh" aria-label="Refresh"><mat-icon>refresh</mat-icon></button>
    </app-page-header>

    <mat-card appearance="outlined">
      <div class="filters">
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Market</mat-label>
          <mat-select [ngModel]="market()" (ngModelChange)="market.set($event); reset()">
            <mat-option [value]="null">All markets</mat-option>
            @for (m of markets; track m.code) {
              <mat-option [value]="m.code">{{ m.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Claim type</mat-label>
          <mat-select [ngModel]="claimType()" (ngModelChange)="claimType.set($event); reset()">
            <mat-option [value]="null">All types</mat-option>
            @for (t of claimTypes; track t.code) {
              <mat-option [value]="t.code">{{ t.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <span class="count">{{ total() }} waiting</span>
      </div>
      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (!loading() && rows().length === 0) {
        <app-empty-state icon="task_alt" title="The queue is clear" message="No unassigned claims match these filters." />
      } @else {
        <app-claim-table [rows]="rows()" [columns]="columns()" [total]="total()" [pageIndex]="pageIndex()"
                         [pageSize]="pageSize()" actionLabel="Pick up" [busyId]="pickingUp()"
                         (action)="pickUp($event)" (page)="onPage($event)" />
      }
    </mat-card>
  `,
  styles: `
    .filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; padding: 16px; }
    .count { margin-left: auto; color: var(--mat-sys-on-surface-variant); }
  `,
})
export class Queue implements OnInit {
  private readonly api = inject(ClaimsApi);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly markets = MARKETS;
  protected readonly claimTypes = CLAIM_TYPES;
  protected readonly market = signal<Market | null>(null);
  protected readonly claimType = signal<ClaimType | null>(null);
  protected readonly rows = signal<ClaimSummary[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);
  protected readonly loading = signal(true);
  protected readonly pickingUp = signal<string | null>(null);

  /** Only officers can pick up (POST /api/staff/** is OFFICER-only); managers get the list without the button. */
  protected readonly columns = computed<ClaimColumn[]>(() => {
    const base: ClaimColumn[] = ['claimNumber', 'claimType', 'market', 'estimatedAmount', 'submittedAt', 'age'];
    return this.auth.hasRole('OFFICER') ? [...base, 'action'] : base;
  });

  ngOnInit(): void {
    this.load();
  }

  protected reset(): void {
    this.pageIndex.set(0);
    this.load();
  }

  protected onPage(e: PageEvent): void {
    this.pageIndex.set(e.pageIndex);
    this.pageSize.set(e.pageSize);
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.queue({ market: this.market(), claimType: this.claimType() }, this.pageIndex(), this.pageSize()).subscribe({
      next: (p) => {
        this.rows.set(p.items);
        this.total.set(p.totalItems);
        this.loading.set(false);
      },
      error: (e) => {
        this.loading.set(false);
        this.toast.error(e);
      },
    });
  }

  protected pickUp(c: ClaimSummary): void {
    this.pickingUp.set(c.id);
    this.api.assign(c.id).subscribe({
      next: () => {
        this.toast.success(`${c.claimNumber} is now assigned to you.`);
        void this.router.navigate(['/claims', c.id]);
      },
      error: (e) => {
        // 409: someone else picked it up first - refresh so it disappears from the queue
        this.pickingUp.set(null);
        this.toast.error(e);
        this.load();
      },
    });
  }
}
