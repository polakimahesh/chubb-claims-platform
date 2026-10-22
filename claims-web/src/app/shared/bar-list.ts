import { Component, computed, input } from '@angular/core';
import { MatTooltip } from '@angular/material/tooltip';

export interface BarItem {
  label: string;
  value: number;
  /** Formatted value shown at the end of the bar (defaults to the number). */
  display?: string;
  /** Extra line for the hover tooltip. */
  detail?: string;
}

/**
 * Single-series horizontal bars: one hue, bars start at zero, every bar labelled directly (label + value), with a
 * hover tooltip. Used for magnitudes such as claims by status or exposure by market.
 */
@Component({
  selector: 'app-bar-list',
  imports: [MatTooltip],
  template: `
    <ul class="bars" [attr.aria-label]="ariaLabel()">
      @for (b of items(); track b.label) {
        <li [matTooltip]="b.label + ': ' + (b.display ?? b.value) + (b.detail ? ' · ' + b.detail : '')">
          <span class="label">{{ b.label }}</span>
          <span class="track"><span class="fill" [style.width.%]="pct(b.value)"></span></span>
          <span class="value">{{ b.display ?? b.value }}</span>
        </li>
      }
    </ul>
  `,
  styles: `
    .bars { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
    li { display: grid; grid-template-columns: minmax(90px, 140px) 1fr auto; gap: 12px; align-items: center; padding: 2px 0; }
    .label { font: var(--mat-sys-body-medium); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .track { height: 12px; border-radius: 0 4px 4px 0; }
    .fill { display: block; height: 100%; min-width: 2px; border-radius: 0 4px 4px 0; background: var(--mat-sys-primary); }
    li:hover .fill { background: var(--mat-sys-on-primary-container); }
    .value { font: var(--mat-sys-label-large); font-variant-numeric: tabular-nums; text-align: right; color: var(--mat-sys-on-surface); }
  `,
})
export class BarList {
  readonly items = input.required<BarItem[]>();
  readonly ariaLabel = input('');
  private readonly max = computed(() => Math.max(0, ...this.items().map((i) => i.value)));

  protected pct(v: number): number {
    return this.max() ? (v / this.max()) * 100 : 0;
  }
}
