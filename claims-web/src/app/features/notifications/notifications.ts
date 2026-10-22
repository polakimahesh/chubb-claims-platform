import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatTooltip } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { NotificationsApi } from '../../core/api/notifications.api';
import { AuthService } from '../../core/auth.service';
import { ClaimEventType, Notification } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { EmptyState } from '../../shared/empty-state';
import { PageHeader } from '../../shared/page-header';

const EVENT_ICON: Partial<Record<ClaimEventType, string>> = {
  CLAIM_SUBMITTED: 'outbox', CLAIM_ASSIGNED: 'manage_search', INFO_REQUESTED: 'help',
  CLAIM_APPROVED: 'verified', CLAIM_REJECTED: 'block', CLAIM_SETTLED: 'paid',
};

/**
 * E-mails sent by notification-service (GET /api/notifications). Claimants see their own; staff see all, or one
 * claimant's by e-mail. They arrive through Kafka, so a new message can take a second or two to appear.
 */
@Component({
  selector: 'app-notifications',
  imports: [
    DatePipe, FormsModule, RouterLink, PageHeader, EmptyState, MatCardModule, MatExpansionModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatIcon, MatPaginatorModule, MatProgressBar, MatTooltip,
  ],
  template: `
    <app-page-header title="Notifications"
                     [subtitle]="isClaimant ? 'Updates we have e-mailed you about your claims.' : 'E-mails sent to claimants about their claims.'">
      <button mat-icon-button (click)="load()" matTooltip="Refresh" aria-label="Refresh"><mat-icon>refresh</mat-icon></button>
    </app-page-header>

    @if (!isClaimant) {
      <form class="filter" (ngSubmit)="search()">
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Claimant e-mail</mat-label>
          <input matInput type="email" name="email" [(ngModel)]="email" placeholder="All claimants" />
          @if (email) {
            <button mat-icon-button matSuffix type="button" (click)="email = ''; search()" aria-label="Clear"><mat-icon>close</mat-icon></button>
          }
        </mat-form-field>
        <button mat-stroked-button type="submit">Search</button>
      </form>
    }

    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (!loading() && items().length === 0) {
      <mat-card appearance="outlined">
        <app-empty-state icon="notifications_none" title="No notifications"
                         message="Messages appear here shortly after a claim changes." />
      </mat-card>
    } @else {
      <mat-accordion multi>
        @for (n of items(); track n.id) {
          <mat-expansion-panel>
            <mat-expansion-panel-header>
              <mat-panel-title>
                <mat-icon class="icon">{{ icon(n.eventType) }}</mat-icon>
                <span class="subject">{{ n.subject }}</span>
              </mat-panel-title>
              <mat-panel-description>
                @if (!isClaimant) {
                  <span class="to">{{ n.recipientEmail }}</span>
                }
                <span>{{ n.createdAt | date: 'd MMM, HH:mm' }}</span>
              </mat-panel-description>
            </mat-expansion-panel-header>
            <p class="body">{{ n.body }}</p>
            <div class="meta">
              <span>Sent by {{ n.channel.toLowerCase() }} to {{ n.recipientEmail }}</span>
              <a mat-button [routerLink]="['/claims', n.claimId]">Open {{ n.claimNumber }}</a>
            </div>
          </mat-expansion-panel>
        }
      </mat-accordion>
      @if (total() > pageSize()) {
        <mat-paginator [length]="total()" [pageIndex]="pageIndex()" [pageSize]="pageSize()" [pageSizeOptions]="[10, 20, 50]"
                       (page)="onPage($event)" />
      }
    }
  `,
  styles: `
    .filter { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
    .filter mat-form-field { min-width: 280px; }
    mat-panel-title { gap: 12px; min-width: 0; }
    .icon { color: var(--mat-sys-primary); flex: none; }
    .subject { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    mat-panel-description { justify-content: flex-end; gap: 16px; flex: none; }
    .body { white-space: pre-wrap; margin-top: 0; }
    .meta { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; color: var(--mat-sys-on-surface-variant); font: var(--mat-sys-body-small); }
    @media (max-width: 599px) { .to { display: none; } }
  `,
})
export class Notifications implements OnInit {
  private readonly api = inject(NotificationsApi);
  private readonly toast = inject(ToastService);

  protected readonly isClaimant = inject(AuthService).hasRole('CLAIMANT');
  protected email = '';
  protected readonly items = signal<Notification[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);
  protected readonly loading = signal(true);

  ngOnInit(): void {
    this.load();
  }

  protected icon(t: ClaimEventType): string {
    return EVENT_ICON[t] ?? 'mail';
  }

  protected search(): void {
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
    this.api.list(this.isClaimant ? null : this.email.trim() || null, this.pageIndex(), this.pageSize()).subscribe({
      next: (p) => {
        this.items.set(p.items);
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
