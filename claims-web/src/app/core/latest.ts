import { DestroyRef, inject } from '@angular/core';
import { Observable, Observer, Subscription } from 'rxjs';

/**
 * Keeps only the newest request of one kind: starting a request cancels the one still in flight, so a slow, older
 * response (e.g. the unfiltered first page) can never overwrite a newer one (the filtered page). Also cancels on
 * destroy. Create it in a field initialiser (injection context).
 */
export class LatestRequest {
  private sub?: Subscription;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.sub?.unsubscribe());
  }

  run<T>(source: Observable<T>, observer: Partial<Observer<T>>): void {
    this.sub?.unsubscribe();
    this.sub = source.subscribe(observer);
  }
}
