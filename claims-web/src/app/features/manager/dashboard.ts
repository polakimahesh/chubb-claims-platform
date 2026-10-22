import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltip } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ReportsApi } from '../../core/api/reports.api';
import { Exposure, ExposureTotal, Market, ReportSummary } from '../../core/models';
import { ALL_STATUSES, MARKETS, REPORT_CURRENCIES, STATUS_META } from '../../core/reference';
import { ToastService } from '../../core/toast.service';
import { BarItem, BarList } from '../../shared/bar-list';
import { PageHeader } from '../../shared/page-header';
import { ClaimTypePipe, MarketPipe, MoneyPipe } from '../../shared/pipes';

/**
 * Manager overview from reporting-service: claims by status, backlog, open count (summary), outstanding exposure by
 * market/type/currency (exposure) and converted into one currency (exposure/total).
 */
@Component({
  selector: 'app-dashboard',
  imports: [
    DatePipe, FormsModule, RouterLink, PageHeader, BarList, MatCardModule, MatButtonModule, MatIcon, MatProgressBar,
    MatSelectModule, MatFormFieldModule, MatTableModule, MatTooltip, MoneyPipe, MarketPipe, ClaimTypePipe,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './manager.scss',
})
export class Dashboard implements OnInit {
  private readonly reports = inject(ReportsApi);
  private readonly toast = inject(ToastService);
  private readonly money = new MoneyPipe();

  protected readonly markets = MARKETS;
  protected readonly currencies = REPORT_CURRENCIES;
  protected readonly summary = signal<ReportSummary | null>(null);
  protected readonly exposure = signal<Exposure[]>([]);
  protected readonly total = signal<ExposureTotal | null>(null);
  protected readonly market = signal<Market | null>(null);
  protected readonly baseCurrency = signal('USD');
  protected readonly loading = signal(true);
  protected readonly updatedAt = signal<Date | null>(null);

  protected readonly statusBars = computed<BarItem[]>(() => {
    const s = this.summary()?.claimsByStatus ?? {};
    return ALL_STATUSES.map((st) => ({ label: STATUS_META[st].label, value: s[st] ?? 0 }));
  });

  protected readonly totalClaims = computed(() =>
    Object.values(this.summary()?.claimsByStatus ?? {}).reduce((a, b) => a + (b ?? 0), 0));

  protected readonly currencyBars = computed<BarItem[]>(() =>
    (this.total()?.breakdown ?? []).map((b) => ({
      label: b.currency,
      value: b.convertedAmount,
      display: this.money.transform(b.convertedAmount, this.baseCurrency()),
      detail: `${b.openClaims} open · ${this.money.transform(b.originalAmount, b.currency)}`,
    })));

  protected readonly exposureColumns = ['market', 'claimType', 'openClaims', 'totalExposure'];

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    forkJoin({
      summary: this.reports.summary(),
      exposure: this.reports.exposure(this.market()),
      total: this.reports.exposureTotal(this.baseCurrency()),
    }).subscribe({
      next: ({ summary, exposure, total }) => {
        this.summary.set(summary);
        this.exposure.set(exposure);
        this.total.set(total);
        this.updatedAt.set(new Date());
        this.loading.set(false);
      },
      error: (e) => {
        this.loading.set(false);
        this.toast.error(e);
      },
    });
  }

  protected filterMarket(m: Market | null): void {
    this.market.set(m);
    this.reports.exposure(m).subscribe({ next: (x) => this.exposure.set(x), error: (e) => this.toast.error(e) });
  }

  protected changeCurrency(c: string): void {
    this.baseCurrency.set(c);
    this.reports.exposureTotal(c).subscribe({ next: (x) => this.total.set(x), error: (e) => this.toast.error(e) });
  }
}
