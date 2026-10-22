import { Component, input, model, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { DOCUMENT_RULES } from '../core/reference';
import { FileSizePipe } from './pipes';

/** Why a file would be refused by DocumentService (type, 5 MB, count), or null if it is acceptable. */
export function fileProblem(file: File): string | null {
  if (!DOCUMENT_RULES.types.includes(file.type)) return `${file.name}: only PDF, PNG and JPEG files are accepted`;
  if (file.size === 0) return `${file.name}: the file is empty`;
  if (file.size > DOCUMENT_RULES.maxBytes) return `${file.name}: larger than 5 MB`;
  return null;
}

/** Drag-and-drop or browse; keeps a validated list of files (two-way bound through `files`). */
@Component({
  selector: 'app-file-picker',
  imports: [MatButtonModule, MatIcon, FileSizePipe],
  template: `
    <div class="drop" [class.over]="over()" (dragover)="$event.preventDefault(); over.set(true)"
         (dragleave)="over.set(false)" (drop)="drop($event)">
      <mat-icon aria-hidden="true">upload_file</mat-icon>
      <p>Drag files here or <button mat-button type="button" (click)="input.click()" [disabled]="remaining() <= 0">browse</button></p>
      <p class="muted">PDF, PNG or JPEG · up to 5 MB each · {{ remaining() }} more allowed</p>
      <input #input type="file" multiple hidden [accept]="accept" (change)="choose(input)" data-testid="file-input" />
    </div>
    @for (p of problems(); track p) {
      <p class="problem" role="alert"><mat-icon>warning</mat-icon>{{ p }}</p>
    }
    @if (files().length) {
      <ul class="files">
        @for (f of files(); track f.name + f.size; let i = $index) {
          <li>
            <mat-icon>{{ f.type === 'application/pdf' ? 'picture_as_pdf' : 'image' }}</mat-icon>
            <span class="name">{{ f.name }}</span><span class="muted">{{ f.size | fileSize }}</span>
            <button mat-icon-button type="button" (click)="remove(i)" [attr.aria-label]="'Remove ' + f.name">
              <mat-icon>close</mat-icon>
            </button>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .drop {
      border: 1px dashed var(--app-border-strong); border-radius: 0; padding: 20px; text-align: center;
      background: var(--app-surface-muted); transition: background .15s;
    }
    .drop.over { background: var(--mat-sys-primary-container); }
    .drop mat-icon { font-size: 32px; width: 32px; height: 32px; color: var(--mat-sys-primary); }
    .drop p { margin: 4px 0; }
    .muted { color: var(--app-text-muted); font: var(--mat-sys-body-small); }
    .problem { display: flex; gap: 6px; align-items: center; color: var(--mat-sys-error); margin: 8px 0 0; font: var(--mat-sys-body-small); }
    .problem mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .files { list-style: none; padding: 0; margin: 12px 0 0; }
    .files li { display: flex; align-items: center; gap: 10px; padding: 4px 0; }
    .name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  `,
})
export class FilePicker {
  readonly files = model<File[]>([]);
  /** How many documents the claim can still take (20 minus those already attached). */
  readonly capacity = input(DOCUMENT_RULES.maxPerClaim);

  protected readonly accept = DOCUMENT_RULES.accept;
  protected readonly over = signal(false);
  protected readonly problems = signal<string[]>([]);

  protected remaining(): number {
    return this.capacity() - this.files().length;
  }

  protected choose(el: HTMLInputElement): void {
    this.add(Array.from(el.files ?? []));
    el.value = '';
  }

  protected drop(e: DragEvent): void {
    e.preventDefault();
    this.over.set(false);
    this.add(Array.from(e.dataTransfer?.files ?? []));
  }

  protected remove(i: number): void {
    this.files.update((f) => f.filter((_, idx) => idx !== i));
  }

  private add(incoming: File[]): void {
    const problems: string[] = [];
    const ok: File[] = [];
    for (const f of incoming) {
      const p = fileProblem(f);
      if (p) problems.push(p);
      else if (this.remaining() - ok.length <= 0) problems.push(`${f.name}: a claim can have at most ${DOCUMENT_RULES.maxPerClaim} documents`);
      else ok.push(f);
    }
    this.problems.set(problems);
    this.files.update((f) => [...f, ...ok]);
  }
}
