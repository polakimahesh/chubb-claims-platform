import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Exposure, ExposureTotal, Market, OfficerPerformance, OfficerWorkload, ReportSummary, SlaBreach } from '../models';
import { params } from './claims.api';

/** reporting-service (MANAGER only). Built from Kafka events, so typically about a second behind claims-service. */
@Injectable({ providedIn: 'root' })
export class ReportsApi {
  private readonly http = inject(HttpClient);

  summary(): Observable<ReportSummary> {
    return this.http.get<ReportSummary>('/api/reports/summary');
  }

  exposure(market?: Market | null): Observable<Exposure[]> {
    return this.http.get<Exposure[]>('/api/reports/exposure', { params: params({ market }) });
  }

  exposureTotal(baseCurrency: string): Observable<ExposureTotal> {
    return this.http.get<ExposureTotal>('/api/reports/exposure/total', { params: { baseCurrency } });
  }

  workload(): Observable<OfficerWorkload[]> {
    return this.http.get<OfficerWorkload[]>('/api/reports/workload');
  }

  performance(): Observable<OfficerPerformance[]> {
    return this.http.get<OfficerPerformance[]>('/api/reports/performance');
  }

  slaBreaches(): Observable<SlaBreach[]> {
    return this.http.get<SlaBreach[]>('/api/reports/sla-breaches');
  }
}
