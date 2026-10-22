import { DatePipe } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { ClaimSummary } from '../core/models';
import { ClaimTypePipe, MarketPipe, MoneyPipe } from './pipes';
import { StatusChip } from './status-chip';

export type ClaimColumn = 'claimNumber' | 'claimType' | 'market' | 'status' | 'estimatedAmount' | 'assessedAmount'
  | 'assignedOfficerId' | 'submittedAt' | 'age' | 'action';

/** Paged list of claim summaries; a row opens the claim. Optional per-row action button (e.g. "Pick up"). */
@Component({
  selector: 'app-claim-table',
  imports: [MatTableModule, MatPaginatorModule, MatButtonModule, MatIcon, RouterLink, DatePipe, StatusChip, MoneyPipe, MarketPipe, ClaimTypePipe],
  template: `
    <div class="table-wrap">
      <table mat-table [dataSource]="rows()">
        <ng-container matColumnDef="claimNumber">
          <th mat-header-cell *matHeaderCellDef>Claim</th>
          <td mat-cell *matCellDef="let c"><a class="ref" [routerLink]="['/claims', c.id]">{{ c.claimNumber }}</a></td>
        </ng-container>
        <ng-container matColumnDef="claimType">
          <th mat-header-cell *matHeaderCellDef>Type</th>
          <td mat-cell *matCellDef="let c">
            <span class="type"><mat-icon>{{ c.claimType === 'MOTOR' ? 'directions_car' : 'home' }}</mat-icon>{{ c.claimType | claimType }}</span>
          </td>
        </ng-container>
        <ng-container matColumnDef="market">
          <th mat-header-cell *matHeaderCellDef>Market</th>
          <td mat-cell *matCellDef="let c">{{ c.market | market }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let c"><app-status-chip [status]="c.status" /></td>
        </ng-container>
        <ng-container matColumnDef="estimatedAmount">
          <th mat-header-cell *matHeaderCellDef class="num">Estimate</th>
          <td mat-cell *matCellDef="let c" class="num">{{ c.estimatedAmount | money: c.currency }}</td>
        </ng-container>
        <ng-container matColumnDef="assessedAmount">
          <th mat-header-cell *matHeaderCellDef class="num">Assessed</th>
          <td mat-cell *matCellDef="let c" class="num">{{ c.assessedAmount | money: c.currency }}</td>
        </ng-container>
        <ng-container matColumnDef="assignedOfficerId">
          <th mat-header-cell *matHeaderCellDef>Officer</th>
          <td mat-cell *matCellDef="let c">{{ c.assignedOfficerId ?? 'Unassigned' }}</td>
        </ng-container>
        <ng-container matColumnDef="submittedAt">
          <th mat-header-cell *matHeaderCellDef>Submitted</th>
          <td mat-cell *matCellDef="let c">{{ c.submittedAt | date: 'd MMM y, HH:mm' }}</td>
        </ng-container>
        <ng-container matColumnDef="age">
          <th mat-header-cell *matHeaderCellDef>Waiting</th>
          <td mat-cell *matCellDef="let c">{{ age(c.submittedAt) }}</td>
        </ng-container>
        <ng-container matColumnDef="action">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let c" class="num">
            <button mat-flat-button (click)="action.emit(c); $event.stopPropagation()" [disabled]="busyId() === c.id">
              {{ actionLabel() }}
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="columns()"></tr>
        <tr mat-row *matRowDef="let row; columns: columns()" class="row" [routerLink]="['/claims', row.id]"></tr>
      </table>
    </div>
    @if (total() > pageSizeOptions[0]) {
      <mat-paginator [length]="total()" [pageIndex]="pageIndex()" [pageSize]="pageSize()"
                     [pageSizeOptions]="pageSizeOptions" (page)="page.emit($event)" aria-label="Select page" />
    }
  `,
  styles: `
    .table-wrap { overflow-x: auto; }
    table { min-width: 720px; background: transparent; }
    .row { cursor: pointer; }
    .row:hover { background: var(--mat-sys-surface-container); }
    .ref { font-weight: 600; color: var(--mat-sys-primary); text-decoration: none; }
    .num { text-align: right; }
    .type { display: inline-flex; gap: 6px; align-items: center; }
    .type mat-icon { font-size: 18px; width: 18px; height: 18px; color: var(--mat-sys-on-surface-variant); }
  `,
})
export class ClaimTable {
  readonly rows = input.required<ClaimSummary[]>();
  readonly columns = input.required<ClaimColumn[]>();
  readonly total = input(0);
  readonly pageIndex = input(0);
  readonly pageSize = input(20);
  readonly actionLabel = input('');
  readonly busyId = input<string | null>(null);
  readonly action = output<ClaimSummary>();
  readonly page = output<PageEvent>();

  protected readonly pageSizeOptions = [10, 20, 50];

  protected age(submittedAt: string): string {
    const hours = (Date.now() - new Date(submittedAt).getTime()) / 36e5;
    if (hours < 1) return `${Math.max(1, Math.round(hours * 60))}m`;
    if (hours < 48) return `${Math.round(hours)}h`;
    return `${Math.round(hours / 24)}d`;
  }
}
