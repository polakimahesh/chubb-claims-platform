import { Component, computed, input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { ClaimStatus } from '../core/models';
import { STATUS_META } from '../core/reference';

@Component({
  selector: 'app-status-chip',
  imports: [MatIcon],
  template: `
    <span class="chip" [class]="'tone-' + meta().tone">
      <mat-icon aria-hidden="true">{{ meta().icon }}</mat-icon>{{ meta().label }}
    </span>
  `,
  styles: `
    .chip {
      display: inline-flex; align-items: center; gap: 4px; padding: 2px 10px 2px 6px;
      border-radius: 2px; font: var(--mat-sys-label-medium); font-weight: 700; white-space: nowrap;
    }
    mat-icon { font-size: 16px; width: 16px; height: 16px; }
  `,
})
export class StatusChip {
  readonly status = input.required<ClaimStatus>();
  protected readonly meta = computed(() => STATUS_META[this.status()]);
}
