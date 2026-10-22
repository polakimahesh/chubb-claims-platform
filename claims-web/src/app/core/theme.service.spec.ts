import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  const root = () => document.documentElement;

  afterEach(() => {
    localStorage.clear();
    root().removeAttribute('data-theme');
  });

  it('follows the system by default (no data-theme attribute)', () => {
    const theme = TestBed.inject(ThemeService);
    expect(theme.mode()).toBe('system');
    expect(root().hasAttribute('data-theme')).toBe(false);
  });

  it('forces dark or light, remembers the choice, and updates the browser theme colour', () => {
    const meta = document.createElement('meta');
    meta.name = 'theme-color';
    document.head.appendChild(meta);

    const theme = TestBed.inject(ThemeService);
    theme.set('dark');
    expect(root().getAttribute('data-theme')).toBe('dark');
    expect(theme.effective()).toBe('dark');
    expect(localStorage.getItem('claims.theme')).toBe('dark');
    expect(meta.content).toBe('#000000');

    theme.toggle();
    expect(theme.mode()).toBe('light');
    expect(root().getAttribute('data-theme')).toBe('light');

    theme.set('system');
    expect(root().hasAttribute('data-theme')).toBe(false);
    expect(localStorage.getItem('claims.theme')).toBeNull();
    meta.remove();
  });

  it('restores the saved choice', () => {
    localStorage.setItem('claims.theme', 'dark');
    expect(TestBed.inject(ThemeService).mode()).toBe('dark');
  });

  it('ignores an invalid saved value', () => {
    localStorage.setItem('claims.theme', 'purple');
    expect(TestBed.inject(ThemeService).mode()).toBe('system');
  });
});
