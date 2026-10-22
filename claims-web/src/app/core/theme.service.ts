import { DOCUMENT } from '@angular/common';
import { computed, inject, Injectable, signal } from '@angular/core';

export type ThemeMode = 'system' | 'light' | 'dark';

const STORAGE_KEY = 'claims.theme';

/**
 * Light / dark / system theme. The stylesheet is built with `color-scheme`, so switching is just
 * `<html data-theme>`; "system" removes the attribute and follows the OS. The choice is a per-browser preference
 * (localStorage). index.html applies the saved choice before Angular starts, so there is no flash on load.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly doc = inject(DOCUMENT);
  private readonly media = this.doc.defaultView?.matchMedia?.('(prefers-color-scheme: dark)') ?? null;
  private readonly systemDark = signal(this.media?.matches ?? false);

  readonly mode = signal<ThemeMode>(readStored());
  /** The theme actually shown. */
  readonly effective = computed<'light' | 'dark'>(() =>
    this.mode() === 'system' ? (this.systemDark() ? 'dark' : 'light') : (this.mode() as 'light' | 'dark'));

  constructor() {
    this.media?.addEventListener?.('change', (e) => {
      this.systemDark.set(e.matches);
      this.apply();
    });
    this.apply();
  }

  set(mode: ThemeMode): void {
    this.mode.set(mode);
    try {
      if (mode === 'system') localStorage.removeItem(STORAGE_KEY);
      else localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      // storage unavailable (private mode): the choice lasts for this page only
    }
    this.apply();
  }

  /** Flips between light and dark (from "system", flips away from what is currently shown). */
  toggle(): void {
    this.set(this.effective() === 'dark' ? 'light' : 'dark');
  }

  private apply(): void {
    const root = this.doc.documentElement;
    if (this.mode() === 'system') root.removeAttribute('data-theme');
    else root.setAttribute('data-theme', this.mode());
    this.doc.querySelector('meta[name="theme-color"]')?.setAttribute('content', this.effective() === 'dark' ? '#0d1016' : '#f4f6fa');
  }
}

function readStored(): ThemeMode {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return v === 'light' || v === 'dark' ? v : 'system';
  } catch {
    return 'system';
  }
}
