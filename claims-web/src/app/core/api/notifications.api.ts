import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Notification, Page } from '../models';
import { params } from './claims.api';

/** notification-service: claimants get their own messages; staff get all, or one claimant's via claimantEmail. */
@Injectable({ providedIn: 'root' })
export class NotificationsApi {
  private readonly http = inject(HttpClient);

  list(claimantEmail: string | null, page = 0, size = 20): Observable<Page<Notification>> {
    return this.http.get<Page<Notification>>('/api/notifications', { params: params({ claimantEmail, page, size }) });
  }
}
