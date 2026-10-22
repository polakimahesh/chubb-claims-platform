import { Component, input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';

@Component({
  selector: 'app-empty-state',
  imports: [MatIcon],
  template: `
    <div class="empty">
      <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>
      <p class="title">{{ title() }}</p>
      @if (message()) {
        <p>{{ message() }}</p>
      }
      <ng-content />
    </div>
  `,
  styles: `
    .empty { text-align: center; padding: 48px 16px; color: var(--mat-sys-on-surface-variant); }
    mat-icon { font-size: 48px; width: 48px; height: 48px; opacity: .6; }
    .title { font: var(--mat-sys-title-medium); color: var(--mat-sys-on-surface); margin: 12px 0 4px; }
    p { margin: 0 0 16px; }
  `,
})
export class EmptyState {
  readonly icon = input('inbox');
  readonly title = input.required<string>();
  readonly message = input<string>();
}
