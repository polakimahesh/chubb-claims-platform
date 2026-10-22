import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { LatestRequest } from './latest';

describe('LatestRequest', () => {
  it('ignores a slow older response once a newer request has started', () => {
    const latest = TestBed.runInInjectionContext(() => new LatestRequest());
    const unfiltered = new Subject<string>();
    const filtered = new Subject<string>();
    const seen: string[] = [];

    latest.run(unfiltered, { next: (v) => seen.push(v) });
    latest.run(filtered, { next: (v) => seen.push(v) });
    filtered.next('lee@example.com only');
    unfiltered.next('everyone'); // arrives late: must not overwrite the filtered result

    expect(seen).toEqual(['lee@example.com only']);
  });
});
