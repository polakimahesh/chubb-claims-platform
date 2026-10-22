import { Pipe, PipeTransform } from '@angular/core';
import { ClaimType, Market } from '../core/models';
import { CLAIM_TYPES, marketName } from '../core/reference';

/** Amount in its claim currency, e.g. "SGD 5,000.00". IDR and others keep the currency code for clarity. */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined, currency: string): string {
    if (value === null || value === undefined) return '—';
    return new Intl.NumberFormat('en-SG', { style: 'currency', currency, currencyDisplay: 'code' }).format(value);
  }
}

@Pipe({ name: 'market' })
export class MarketPipe implements PipeTransform {
  transform(code: Market): string {
    return marketName(code);
  }
}

@Pipe({ name: 'claimType' })
export class ClaimTypePipe implements PipeTransform {
  transform(code: ClaimType): string {
    return CLAIM_TYPES.find((t) => t.code === code)?.name ?? code;
  }
}

@Pipe({ name: 'fileSize' })
export class FileSizePipe implements PipeTransform {
  transform(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }
}

/** "3h", "2d 4h" from a number of hours. */
@Pipe({ name: 'hours' })
export class HoursPipe implements PipeTransform {
  transform(hours: number): string {
    if (hours < 1) return `${Math.round(hours * 60)}m`;
    if (hours < 24) return `${Math.round(hours * 10) / 10}h`;
    const d = Math.floor(hours / 24);
    const h = Math.round(hours % 24);
    return h ? `${d}d ${h}h` : `${d}d`;
  }
}
