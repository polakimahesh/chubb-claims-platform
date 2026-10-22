import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { ReportsApi } from '../../core/api/reports.api';
import { SlaBreach } from '../../core/models';
import { LatestRequest } from '../../core/latest';
import { ToastService } from '../../core/toast.service';
import { EmptyState } from '../../shared/empty-state';
import { PageHeader } from '../../shared/page-header';
import { ClaimTypePipe, HoursPipe, MarketPipe } from '../../shared/pipes';
import { StatusChip } from '../../shared/status-chip';

/**
 * SLA breaches (reports/sla-breaches), oldest first: claims not picked up within the unassigned threshold, or open
 * for longer than the open threshold. Claims waiting on the claimant do not count against the officer.
 */
@Component({
  selector: 'app-sla',
  imports: [DatePipe, RouterLink, PageHeader, EmptyState, StatusChip, MatCardModule, MatTableModule, MatButtonModule, MatIcon, MatProgressBar, HoursPipe, MarketPipe, ClaimTypePipe],
  template: `
    <app-page-header title="SLA breaches" subtitle="Claims that have exceeded their service level, oldest first.">
      @if (updatedAt()) {
        <span class="updated"><mat-icon>schedule</mat-icon>Updated {{ updatedAt() | date: 'HH:mm:ss' }}</span>
      }
      <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon>Refresh</button>
    </app-page-header>
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" class="progress" />
    }

    <section class="tiles">
      <mat-card appearance="outlined" class="tile">
        <span class="tile-label"><mat-icon class="inline">inbox</mat-icon> Not picked up in time</span>
        <span class="tile-value">{{ unassigned() }}</span>
        <span class="tile-sub">Submitted and unassigned beyond the threshold</span>
      </mat-card>
      <mat-card appearance="outlined" class="tile">
        <span class="tile-label"><mat-icon class="inline">hourglass_bottom</mat-icon> Open too long</span>
        <span class="tile-value">{{ openTooLong() }}</span>
        <span class="tile-sub">In review or approved beyond the threshold</span>
      </mat-card>
    </section>

    <mat-card appearance="outlined">
      <mat-card-content>
        @if (rows().length) {
          <div class="table-wrap">
            <table mat-table [dataSource]="rows()">
              <ng-container matColumnDef="claimNumber">
                <th mat-header-cell *matHeaderCellDef>Claim</th>
                <td mat-cell *matCellDef="let b"><a class="ref" [routerLink]="['/claims', b.claimId]">{{ b.claimNumber }}</a></td>
              </ng-container>
              <ng-container matColumnDef="claim">
                <th mat-header-cell *matHeaderCellDef>Type · Market</th>
                <td mat-cell *matCellDef="let b">{{ b.claimType | claimType }} · {{ b.market | market }}</td>
              </ng-container>
              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Status</th>
                <td mat-cell *matCellDef="let b"><app-status-chip [status]="b.status" /></td>
              </ng-container>
              <ng-container matColumnDef="officer">
                <th mat-header-cell *matHeaderCellDef>Officer</th>
                <td mat-cell *matCellDef="let b">{{ b.assignedOfficerId ?? 'Unassigned' }}</td>
              </ng-container>
              <ng-container matColumnDef="breach">
                <th mat-header-cell *matHeaderCellDef>Breach</th>
                <td mat-cell *matCellDef="let b">
                  <span class="breach"><mat-icon>warning</mat-icon>{{ b.breachType === 'UNASSIGNED_TOO_LONG' ? 'Not picked up' : 'Open too long' }}</span>
                </td>
              </ng-container>
              <ng-container matColumnDef="age">
                <th mat-header-cell *matHeaderCellDef class="num">Age / limit</th>
                <td mat-cell *matCellDef="let b" class="num"><strong>{{ b.ageHours | hours }}</strong> / {{ b.thresholdHours | hours }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let row; columns: columns"></tr>
            </table>
          </div>
        } @else if (!loading()) {
          <app-empty-state icon="verified" title="No SLA breaches" message="Every open claim is within its service level." />
        }
      </mat-card-content>
    </mat-card>
  `,
  styleUrl: './manager.scss',
  styles: `
    .tiles { grid-template-columns: repeat(2, 1fr); }
    .ref { font-weight: 600; color: var(--mat-sys-primary); text-decoration: none; }
    .breach { display: inline-flex; gap: 4px; align-items: center; color: var(--mat-sys-error); }
    .breach mat-icon { font-size: 18px; width: 18px; height: 18px; }
  `,
})
export class Sla implements OnInit {
  private readonly reports = inject(ReportsApi);
  private readonly toast = inject(ToastService);
  private readonly requests = new LatestRequest();

  protected readonly rows = signal<SlaBreach[]>([]);
  protected readonly loading = signal(true);
  protected readonly updatedAt = signal<Date | null>(null);
  protected readonly unassigned = computed(() => this.rows().filter((r) => r.breachType === 'UNASSIGNED_TOO_LONG').length);
  protected readonly openTooLong = computed(() => this.rows().filter((r) => r.breachType === 'OPEN_TOO_LONG').length);
  protected readonly columns = ['claimNumber', 'claim', 'status', 'officer', 'breach', 'age'];

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.requests.run(this.reports.slaBreaches(), {
      next: (r) => {
        this.rows.set(r);
        this.updatedAt.set(new Date());
        this.loading.set(false);
      },
      error: (e) => {
        this.loading.set(false);
        this.toast.error(e);
      },
    });
  }
}
