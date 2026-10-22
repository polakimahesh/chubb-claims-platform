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
    header { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 16px; margin-bottom: 28px; }
    h1 { font-family: var(--app-font-serif); font-size: 34px; line-height: 42px; font-weight: 400; letter-spacing: -.01em; margin: 0; }
    p { margin: 8px 0 0; color: var(--app-text-muted); font-size: 16px; }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
  `,
})
export class PageHeader {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
}
