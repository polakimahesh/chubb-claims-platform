import { ClaimDetail, CurrentUser } from './models';
import { currencyFor, isTerminal, staffActions, toLocalDate } from './reference';

const claim = (over: Partial<ClaimDetail> = {}): ClaimDetail => ({
  id: 'c1', claimNumber: 'CLM-SG-2026-ABCDEF12', market: 'SG', claimType: 'MOTOR', status: 'UNDER_REVIEW',
  claimantName: 'Tan Wei', claimantEmail: 'tan@example.com', description: 'x', incidentDate: '2026-01-10',
  currency: 'SGD', estimatedAmount: 5000, assessedAmount: null, assignedOfficerId: 'officer-1',
  rejectionReason: null, submittedAt: '2026-01-10T00:00:00Z', closedAt: null, ...over,
});
const officer1: CurrentUser = { username: 'officer-1', roles: ['OFFICER'] };
const officer2: CurrentUser = { username: 'officer-2', roles: ['OFFICER'] };
const manager: CurrentUser = { username: 'manager-1', roles: ['MANAGER'] };

describe('staffActions (mirrors the Claim entity / ClaimService rules)', () => {
  it('any officer can pick up a submitted claim; managers cannot', () => {
    const c = claim({ status: 'SUBMITTED', assignedOfficerId: null });
    expect(staffActions(c, officer2).pickUp).toBe(true);
    expect(staffActions(c, manager).pickUp).toBe(false);
  });

  it('only the assigned officer decides; approval needs an assessment', () => {
    const mine = staffActions(claim(), officer1);
    expect(mine).toMatchObject({ assess: true, requestInfo: true, approve: true, reject: true, reassign: true, settle: false });
    expect(mine.approveBlockedReason).toBeTruthy();
    expect(staffActions(claim({ assessedAmount: 4200 }), officer1).approveBlockedReason).toBeNull();

    const other = staffActions(claim(), officer2);
    expect(other).toMatchObject({ assess: false, approve: false, reject: false, reassign: false, assignedToSomeoneElse: true });
  });

  it('while waiting on the claimant only reassignment is possible', () => {
    const a = staffActions(claim({ status: 'INFO_REQUESTED' }), officer1);
    expect(a).toMatchObject({ requestInfo: false, assess: false, approve: false, reject: false, reassign: true });
  });

  it('approved claims can only be settled', () => {
    const a = staffActions(claim({ status: 'APPROVED', assessedAmount: 4200 }), officer1);
    expect(a).toMatchObject({ settle: true, approve: false, reject: false, reassign: false });
  });

  it('managers reassign any claim in review but never decide', () => {
    const a = staffActions(claim(), manager);
    expect(a).toMatchObject({ reassign: true, approve: false, reject: false, assess: false, settle: false });
    expect(staffActions(claim({ status: 'APPROVED' }), manager).reassign).toBe(false);
  });

  it('closed claims offer nothing', () => {
    for (const status of ['REJECTED', 'SETTLED'] as const) {
      const a = staffActions(claim({ status }), officer1);
      expect(Object.entries(a).filter(([k, v]) => k !== 'approveBlockedReason' && v === true)).toEqual([]);
    }
  });
});

describe('reference data', () => {
  it('each market settles in its own currency', () => {
    expect(currencyFor('SG')).toBe('SGD');
    expect(currencyFor('ID')).toBe('IDR');
    expect(currencyFor('AU')).toBe('AUD');
  });

  it('terminal statuses are rejected and settled only', () => {
    expect(isTerminal('REJECTED')).toBe(true);
    expect(isTerminal('SETTLED')).toBe(true);
    expect(isTerminal('APPROVED')).toBe(false);
  });

  it('formats a picked date as a local LocalDate (no UTC shift)', () => {
    expect(toLocalDate(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05');
  });
});
