import { HttpErrorResponse } from '@angular/common/http';
import { fileProblem } from '../shared/file-picker';
import { HoursPipe, MoneyPipe } from '../shared/pipes';
import { ApiError } from './api-error';

describe('ApiError.from (RFC 7807 problem responses)', () => {
  it('uses the problem detail and validation errors', () => {
    const e = ApiError.from(new HttpErrorResponse({
      status: 400, error: { status: 400, detail: 'Validation failed', errors: ['estimatedAmount: must be greater than or equal to 0.01'] },
    }));
    expect(e.status).toBe(400);
    expect(e.message).toBe('Validation failed');
    expect(e.fieldErrors).toEqual(['estimatedAmount: must be greater than or equal to 0.01']);
  });

  it('flags conflicts so the screen reloads', () => {
    const e = ApiError.from(new HttpErrorResponse({ status: 409, error: { detail: 'Cannot move claim from SETTLED to APPROVED' } }));
    expect(e.isConflict).toBe(true);
    expect(e.message).toContain('SETTLED');
  });

  it('falls back to a readable message without a body, and explains an unreachable server', () => {
    expect(ApiError.from(new HttpErrorResponse({ status: 413 })).message).toContain('5 MB');
    expect(ApiError.from(new HttpErrorResponse({ status: 0 })).message).toContain('Cannot reach the server');
  });
});

describe('fileProblem (DocumentService limits)', () => {
  const file = (type: string, size: number) => new File([new Uint8Array(size)], 'f', { type });

  it('accepts PDF, PNG and JPEG up to 5 MB', () => {
    expect(fileProblem(file('application/pdf', 10))).toBeNull();
    expect(fileProblem(file('image/jpeg', 5 * 1024 * 1024))).toBeNull();
  });

  it('refuses other types, empty files and files over 5 MB', () => {
    expect(fileProblem(file('text/plain', 10))).toContain('only PDF, PNG and JPEG');
    expect(fileProblem(file('image/png', 0))).toContain('empty');
    expect(fileProblem(file('image/png', 5 * 1024 * 1024 + 1))).toContain('5 MB');
  });
});

describe('pipes', () => {
  it('formats money with its currency code', () => {
    expect(new MoneyPipe().transform(5000, 'SGD').replace(/\s/g, ' ')).toBe('SGD 5,000.00');
    expect(new MoneyPipe().transform(null, 'SGD')).toBe('—');
  });

  it('formats durations', () => {
    expect(new HoursPipe().transform(0.5)).toBe('30m');
    expect(new HoursPipe().transform(5)).toBe('5h');
    expect(new HoursPipe().transform(50)).toBe('2d 2h');
  });
});
