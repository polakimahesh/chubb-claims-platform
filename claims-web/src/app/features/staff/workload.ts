import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
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
import { ClaimStatus, ClaimSummary } from '../../core/models';
import { ALL_STATUSES, STATUS_META } from '../../core/reference';
import { ToastService } from '../../core/toast.service';
import { ClaimTable } from '../../shared/claim-table';
import { EmptyState } from '../../shared/empty-state';
import { PageHeader } from '../../shared/page-header';

/**
 * Workload (GET /api/staff/claims): an officer's own claims; a manager chooses any officer (officerId).
 * The status filter covers closed claims too, so it doubles as the officer's history.
 */
@Component({
  selector: 'app-workload',
  imports: [
    FormsModule, PageHeader, ClaimTable, EmptyState, MatCardModule, MatFormFieldModule, MatSelectModule, MatButtonModule,
    MatIcon, MatProgressBar, MatTooltip,
  ],
  template: `
    <app-page-header [title]="isManager ? 'Officer workload' : 'My workload'"
                     [subtitle]="isManager ? 'Claims assigned to an officer, oldest first.' : 'Claims assigned to you, oldest first.'">
      <button mat-icon-button (click)="load()" matTooltip="Refresh" aria-label="Refresh"><mat-icon>refresh</mat-icon></button>
    </app-page-header>

    <mat-card appearance="outlined">
      <div class="filters">
        @if (isManager) {
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>Officer</mat-label>
            <mat-select [ngModel]="officer()" (ngModelChange)="selectOfficer($event)">
              @for (o of officers(); track o) {
                <mat-option [value]="o">{{ o }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Status</mat-label>
          <mat-select [ngModel]="status()" (ngModelChange)="status.set($event); reset()">
            <mat-option [value]="null">All statuses</mat-option>
            @for (s of statuses; track s) {
              <mat-option [value]="s">{{ statusMeta[s].label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <span class="count">{{ total() }} claim(s)</span>
      </div>
      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (isManager && !officer()) {
        <app-empty-state icon="person_search" title="Choose an officer" message="Select an officer to see the claims assigned to them." />
      } @else if (!loading() && rows().length === 0) {
        <app-empty-state icon="assignment_turned_in" title="Nothing here"
                         [message]="isManager ? 'No claims match these filters.' : 'Pick up a claim from the intake queue to start working on it.'" />
      } @else {
        <app-claim-table [rows]="rows()" [total]="total()" [pageIndex]="pageIndex()" [pageSize]="pageSize()"
                         [columns]="['claimNumber', 'claimType', 'market', 'status', 'estimatedAmount', 'assessedAmount', 'submittedAt']"
                         (page)="onPage($event)" />
      }
    </mat-card>
  `,
  styles: `
    .filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; padding: 16px; }
    .count { margin-left: auto; color: var(--mat-sys-on-surface-variant); }
  `,
})
export class Workload implements OnInit {
  /** ?officerId= (managers arriving from the team report). */
  readonly officerId = input<string>();

  private readonly api = inject(ClaimsApi);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  protected readonly isManager = inject(AuthService).hasRole('MANAGER');
  protected readonly statuses = ALL_STATUSES;
  protected readonly statusMeta = STATUS_META;
  protected readonly officers = signal<string[]>([]);
  protected readonly officer = signal<string | null>(null);
  protected readonly status = signal<ClaimStatus | null>(null);
  protected readonly rows = signal<ClaimSummary[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);
  protected readonly loading = signal(false);

  protected readonly requested = computed(() => this.officerId() ?? null);

  ngOnInit(): void {
    if (!this.isManager) {
      this.load();
      return;
    }
    this.officer.set(this.requested());
    this.api.officers().subscribe({
      next: (o) => this.officers.set(o),
      error: (e) => this.toast.error(e),
    });
    if (this.officer()) this.load();
  }

  protected selectOfficer(o: string): void {
    this.officer.set(o);
    void this.router.navigate([], { queryParams: { officerId: o }, replaceUrl: true });
    this.reset();
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
    if (this.isManager && !this.officer()) return;
    this.loading.set(true);
    this.api.workload({ officerId: this.isManager ? this.officer() : null, status: this.status() }, this.pageIndex(), this.pageSize())
      .subscribe({
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
}
