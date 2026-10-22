import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltip } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ReportsApi } from '../../core/api/reports.api';
import { OfficerPerformance, OfficerWorkload } from '../../core/models';
import { LatestRequest } from '../../core/latest';
import { ToastService } from '../../core/toast.service';
import { EmptyState } from '../../shared/empty-state';
import { PageHeader } from '../../shared/page-header';
import { HoursPipe } from '../../shared/pipes';

/** Per-officer open workload (reports/workload) and closed-claim performance (reports/performance). */
@Component({
  selector: 'app-team',
  imports: [DatePipe, RouterLink, PageHeader, EmptyState, MatCardModule, MatTableModule, MatButtonModule, MatIcon, MatProgressBar, MatTooltip, HoursPipe],
  template: `
    <app-page-header title="Team performance" subtitle="Open workload and resolution performance per officer.">
      @if (updatedAt()) {
        <span class="updated" matTooltip="Reports can trail claim changes by a few seconds">
          <mat-icon>schedule</mat-icon>Updated {{ updatedAt() | date: 'HH:mm:ss' }}
        </span>
      }
      <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon>Refresh</button>
    </app-page-header>
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" class="progress" />
    }

    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title>Open workload</mat-card-title>
        <mat-card-subtitle>Claims each officer is working on now</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        @if (workload().length) {
          <div class="table-wrap">
            <table mat-table [dataSource]="workload()">
              <ng-container matColumnDef="officerId">
                <th mat-header-cell *matHeaderCellDef>Officer</th>
                <td mat-cell *matCellDef="let w">{{ w.officerId }}</td>
              </ng-container>
              <ng-container matColumnDef="UNDER_REVIEW">
                <th mat-header-cell *matHeaderCellDef class="num">Under review</th>
                <td mat-cell *matCellDef="let w" class="num">{{ w.byStatus.UNDER_REVIEW ?? 0 }}</td>
              </ng-container>
              <ng-container matColumnDef="INFO_REQUESTED">
                <th mat-header-cell *matHeaderCellDef class="num">Waiting on claimant</th>
                <td mat-cell *matCellDef="let w" class="num">{{ w.byStatus.INFO_REQUESTED ?? 0 }}</td>
              </ng-container>
              <ng-container matColumnDef="APPROVED">
                <th mat-header-cell *matHeaderCellDef class="num">Approved, to settle</th>
                <td mat-cell *matCellDef="let w" class="num">{{ w.byStatus.APPROVED ?? 0 }}</td>
              </ng-container>
              <ng-container matColumnDef="openClaims">
                <th mat-header-cell *matHeaderCellDef class="num">Total open</th>
                <td mat-cell *matCellDef="let w" class="num strong">{{ w.openClaims }}</td>
              </ng-container>
              <ng-container matColumnDef="link">
                <th mat-header-cell *matHeaderCellDef></th>
                <td mat-cell *matCellDef="let w" class="num">
                  <a mat-button routerLink="/workload" [queryParams]="{ officerId: w.officerId }">View claims</a>
                </td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="workloadColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: workloadColumns"></tr>
            </table>
          </div>
        } @else if (!loading()) {
          <app-empty-state icon="groups" title="No open assigned claims" />
        }
      </mat-card-content>
    </mat-card>

    <mat-card appearance="outlined">
      <mat-card-header>
        <mat-card-title>Performance</mat-card-title>
        <mat-card-subtitle>Closed claims and average time from submission to closure</mat-card-subtitle>
      </mat-card-header>
      <mat-card-content>
        @if (performance().length) {
          <div class="table-wrap">
            <table mat-table [dataSource]="performance()">
              <ng-container matColumnDef="officerId">
                <th mat-header-cell *matHeaderCellDef>Officer</th>
                <td mat-cell *matCellDef="let p">{{ p.officerId }}</td>
              </ng-container>
              <ng-container matColumnDef="settled">
                <th mat-header-cell *matHeaderCellDef class="num">Settled</th>
                <td mat-cell *matCellDef="let p" class="num">{{ p.settled }}</td>
              </ng-container>
              <ng-container matColumnDef="rejected">
                <th mat-header-cell *matHeaderCellDef class="num">Rejected</th>
                <td mat-cell *matCellDef="let p" class="num">{{ p.rejected }}</td>
              </ng-container>
              <ng-container matColumnDef="closedClaims">
                <th mat-header-cell *matHeaderCellDef class="num">Closed</th>
                <td mat-cell *matCellDef="let p" class="num strong">{{ p.closedClaims }}</td>
              </ng-container>
              <ng-container matColumnDef="averageResolutionHours">
                <th mat-header-cell *matHeaderCellDef class="num">Avg. resolution</th>
                <td mat-cell *matCellDef="let p" class="num">{{ p.averageResolutionHours | hours }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="performanceColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: performanceColumns"></tr>
            </table>
          </div>
        } @else if (!loading()) {
          <app-empty-state icon="insights" title="No closed claims yet" />
        }
      </mat-card-content>
    </mat-card>
  `,
  styleUrl: './manager.scss',
})
export class Team implements OnInit {
  private readonly reports = inject(ReportsApi);
  private readonly toast = inject(ToastService);
  private readonly requests = new LatestRequest();

  protected readonly workload = signal<OfficerWorkload[]>([]);
  protected readonly performance = signal<OfficerPerformance[]>([]);
  protected readonly loading = signal(true);
  protected readonly updatedAt = signal<Date | null>(null);
  protected readonly workloadColumns = ['officerId', 'UNDER_REVIEW', 'INFO_REQUESTED', 'APPROVED', 'openClaims', 'link'];
  protected readonly performanceColumns = ['officerId', 'settled', 'rejected', 'closedClaims', 'averageResolutionHours'];

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.requests.run(forkJoin({ w: this.reports.workload(), p: this.reports.performance() }), {
      next: ({ w, p }) => {
        this.workload.set(w);
        this.performance.set(p);
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
