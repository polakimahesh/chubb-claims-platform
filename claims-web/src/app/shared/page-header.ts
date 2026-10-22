import { Component, input } from '@angular/core';

/** Page title with an optional subtitle and a slot for actions on the right. */
@Component({
  selector: 'app-page-header',
  template: `
    <header>
      <div>
        <h1>{{ title() }}</h1>
        @if (subtitle()) {
          <p>{{ subtitle() }}</p>
        }
      </div>
      <div class="actions"><ng-content /></div>
    </header>
  `,
  styles: `
    header { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 16px; margin-bottom: 24px; }
    h1 { font: var(--mat-sys-headline-small); margin: 0; }
    p { margin: 4px 0 0; color: var(--mat-sys-on-surface-variant); }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
  `,
})
export class PageHeader {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
}
