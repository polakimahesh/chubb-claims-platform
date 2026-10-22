import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { PageEvent } from '@angular/material/paginator';
import { RouterLink } from '@angular/router';
import { ClaimsApi } from '../../core/api/claims.api';
import { ClaimSummary } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ClaimTable } from '../../shared/claim-table';
import { EmptyState } from '../../shared/empty-state';
import { PageHeader } from '../../shared/page-header';

/** Claimant: their own claims, newest first (GET /api/claims). */
@Component({
  selector: 'app-my-claims',
  imports: [PageHeader, ClaimTable, EmptyState, MatButtonModule, MatCardModule, MatIcon, MatProgressBar, RouterLink],
  template: `
    <app-page-header title="My claims" subtitle="Track the progress of every claim you have reported.">
      <a mat-flat-button routerLink="/my-claims/new"><mat-icon>add</mat-icon>Report an incident</a>
    </app-page-header>

    <mat-card appearance="outlined">
      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (!loading() && rows().length === 0) {
        <app-empty-state icon="description" title="No claims yet"
                         message="When you report an incident it will appear here with its status.">
          <a mat-stroked-button routerLink="/my-claims/new">Report an incident</a>
        </app-empty-state>
      } @else {
        <app-claim-table [rows]="rows()" [total]="total()" [pageIndex]="pageIndex()" [pageSize]="pageSize()"
                         [columns]="['claimNumber', 'claimType', 'market', 'status', 'estimatedAmount', 'assessedAmount', 'submittedAt']"
                         (page)="onPage($event)" />
      }
    </mat-card>
  `,
})
export class MyClaims implements OnInit {
  private readonly api = inject(ClaimsApi);
  private readonly toast = inject(ToastService);

  protected readonly rows = signal<ClaimSummary[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);
  protected readonly loading = signal(true);

  ngOnInit(): void {
    this.load();
  }

  protected onPage(e: PageEvent): void {
    this.pageIndex.set(e.pageIndex);
    this.pageSize.set(e.pageSize);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.api.myClaims(this.pageIndex(), this.pageSize()).subscribe({
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
