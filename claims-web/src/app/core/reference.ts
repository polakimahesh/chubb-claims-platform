import { ClaimDetail, ClaimStatus, ClaimType, CurrentUser, Market } from './models';

/** Mirrors the backend `Market` enum: each market settles in exactly one currency (a different one is a 400). */
export const MARKETS: ReadonlyArray<{ code: Market; name: string; currency: string }> = [
  { code: 'SG', name: 'Singapore', currency: 'SGD' },
  { code: 'HK', name: 'Hong Kong', currency: 'HKD' },
  { code: 'MY', name: 'Malaysia', currency: 'MYR' },
  { code: 'TH', name: 'Thailand', currency: 'THB' },
  { code: 'ID', name: 'Indonesia', currency: 'IDR' },
  { code: 'AU', name: 'Australia', currency: 'AUD' },
];

export function marketName(code: Market): string {
  return MARKETS.find((m) => m.code === code)?.name ?? code;
}

export function currencyFor(code: Market): string {
  return MARKETS.find((m) => m.code === code)!.currency;
}

export const CLAIM_TYPES: ReadonlyArray<{ code: ClaimType; name: string; icon: string }> = [
  { code: 'MOTOR', name: 'Motor', icon: 'directions_car' },
  { code: 'PROPERTY', name: 'Property', icon: 'home' },
];

/** Base currencies the reporting service has an indicative rate for (reporting.fx.rates-to-usd). */
export const REPORT_CURRENCIES = ['USD', 'SGD', 'HKD', 'MYR', 'THB', 'IDR', 'AUD'];

export type StatusTone = 'neutral' | 'info' | 'warn' | 'success' | 'danger';

export const STATUS_META: Record<ClaimStatus, { label: string; tone: StatusTone; icon: string; claimantHint: string }> = {
  SUBMITTED: { label: 'Submitted', tone: 'neutral', icon: 'outbox', claimantHint: 'Waiting for a claims officer to pick it up.' },
  UNDER_REVIEW: { label: 'Under review', tone: 'info', icon: 'manage_search', claimantHint: 'A claims officer is reviewing your claim.' },
  INFO_REQUESTED: { label: 'Info requested', tone: 'warn', icon: 'help', claimantHint: 'We need more information from you to continue.' },
  APPROVED: { label: 'Approved', tone: 'success', icon: 'verified', claimantHint: 'Your claim is approved. Settlement will follow.' },
  REJECTED: { label: 'Rejected', tone: 'danger', icon: 'block', claimantHint: 'Your claim has been declined.' },
  SETTLED: { label: 'Settled', tone: 'success', icon: 'paid', claimantHint: 'Your claim has been settled.' },
};

export const ALL_STATUSES = Object.keys(STATUS_META) as ClaimStatus[];

/** Happy-path order used by the progress tracker. */
export const LIFECYCLE: ClaimStatus[] = ['SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'SETTLED'];

/** Open = not REJECTED / SETTLED (ClaimStatus.isTerminal on the backend). */
export function isTerminal(status: ClaimStatus): boolean {
  return status === 'REJECTED' || status === 'SETTLED';
}

/**
 * Which staff actions to offer, mirroring the rules in the backend `Claim` entity and `ClaimService`:
 * any officer picks up a SUBMITTED claim; only the assigned officer acts on it afterwards; the assigned officer or
 * any manager may reassign while UNDER_REVIEW / INFO_REQUESTED; managers never decide claims.
 * The backend stays authoritative - this only decides which buttons to show.
 */
export function staffActions(claim: ClaimDetail, user: CurrentUser) {
  const officer = user.roles.includes('OFFICER');
  const manager = user.roles.includes('MANAGER');
  const mine = officer && claim.assignedOfficerId === user.username;
  const s = claim.status;
  const inReview = s === 'UNDER_REVIEW' || s === 'INFO_REQUESTED';
  return {
    pickUp: officer && s === 'SUBMITTED',
    requestInfo: mine && s === 'UNDER_REVIEW',
    assess: mine && s === 'UNDER_REVIEW',
    approve: mine && s === 'UNDER_REVIEW',
    approveBlockedReason: claim.assessedAmount == null ? 'Record an assessment before approving' : null,
    reject: mine && s === 'UNDER_REVIEW',
    settle: mine && s === 'APPROVED',
    reassign: inReview && (mine || manager),
    assignedToSomeoneElse: officer && !!claim.assignedOfficerId && !mine,
  };
}

export const DOCUMENT_RULES = {
  maxBytes: 5 * 1024 * 1024,
  maxPerClaim: 20,
  accept: '.pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg',
  types: ['application/pdf', 'image/png', 'image/jpeg'],
};

/** A LocalDate string for a Date picked in the browser's time zone (avoids the UTC shift of toISOString). */
export function toLocalDate(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
