import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { signedInAs, testProviders } from '../../../testing';
import { ClaimDetail, InfoRequest } from '../../core/models';
import { ClaimDetailPage } from './claim-detail';

const base: ClaimDetail = {
  id: 'c1', claimNumber: 'CLM-SG-2026-AAAA1111', market: 'SG', claimType: 'MOTOR', status: 'UNDER_REVIEW',
  claimantName: 'Tan Wei', claimantEmail: 'tan@example.com', description: 'Rear-ended', incidentDate: '2026-01-10',
  currency: 'SGD', estimatedAmount: 5000, assessedAmount: null, assignedOfficerId: 'officer-1',
  rejectionReason: null, submittedAt: '2026-01-10T01:00:00Z', closedAt: null,
};
const openRequest: InfoRequest = {
  id: 'r1', question: 'Please send photos', requestedBy: 'officer-1', requestedAt: '2026-01-11T01:00:00Z',
  response: null, respondedAt: null, open: true,
};

describe('ClaimDetailPage', () => {
  let fixture: ComponentFixture<ClaimDetailPage>;
  let http: HttpTestingController;
  const el = () => fixture.nativeElement as HTMLElement;
  const buttons = () => Array.from(el().querySelectorAll('button')).map((b) => b.textContent!.trim());
  const button = (label: string) =>
    Array.from(el().querySelectorAll('button')).find((b) => b.textContent!.trim().includes(label)) as HTMLButtonElement;

  async function open(claim: Partial<ClaimDetail>, info: InfoRequest[] = [], docs: unknown[] = []) {
    TestBed.configureTestingModule({ imports: [ClaimDetailPage], providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ClaimDetailPage);
    fixture.componentRef.setInput('id', 'c1');
    fixture.detectChanges();
    flushLoad({ ...base, ...claim }, info, docs);
    await fixture.whenStable();
  }

  function flushLoad(claim: ClaimDetail, info: InfoRequest[] = [], docs: unknown[] = []) {
    http.expectOne('/api/claims/c1').flush(claim);
    http.expectOne('/api/claims/c1/history').flush([
      { fromStatus: null, toStatus: 'SUBMITTED', actor: 'tan@example.com', note: 'Claim submitted', occurredAt: '2026-01-10T01:00:00Z' },
    ]);
    http.expectOne('/api/claims/c1/info-requests').flush(info);
    http.expectOne('/api/claims/c1/documents').flush(docs);
  }

  afterEach(() => sessionStorage.clear());

  describe('as the assigned officer', () => {
    beforeEach(() => signedInAs('officer-1', 'OFFICER'));

    it('offers the review actions, with approve disabled until assessed', async () => {
      await open({});
      for (const label of ['Assess', 'Request info', 'Approve', 'Reject', 'Reassign']) expect(button(label)).toBeTruthy();
      expect(button('Approve').disabled).toBe(true);
      expect(buttons().some((b) => b.includes('Settle'))).toBe(false);
    });

    it('enables approve once an assessment exists, and offers only settle when approved', async () => {
      await open({ assessedAmount: 4200 });
      expect(button('Approve').disabled).toBe(false);
      expect(button('Re-assess')).toBeTruthy();
      TestBed.resetTestingModule();
      await open({ status: 'APPROVED', assessedAmount: 4200 });
      expect(buttons().filter((b) => /Approve|Reject|Assess|Request info|Reassign/.test(b))).toEqual([]);
      expect(button('Settle')).toBeTruthy();
    });

    it('approves through the confirmation dialog and reloads the claim', async () => {
      await open({ assessedAmount: 4200 });
      vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({ afterClosed: () => of({}) } as never);
      button('Approve').click();
      await fixture.whenStable();
      const approve = http.expectOne('/api/staff/claims/c1/approve');
      expect(approve.request.method).toBe('POST');
      approve.flush({ ...base, status: 'APPROVED', assessedAmount: 4200 });
      await fixture.whenStable();
      flushLoad({ ...base, status: 'APPROVED', assessedAmount: 4200 });
      await fixture.whenStable();
      fixture.detectChanges();
      expect(el().querySelector('.ref-line')!.textContent).toContain('Approved');
    });

    it('sends the rejection reason from the dialog', async () => {
      await open({});
      vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({ afterClosed: () => of({ reason: 'Not covered' }) } as never);
      button('Reject').click();
      await fixture.whenStable();
      expect(http.expectOne('/api/staff/claims/c1/reject').request.body).toEqual({ reason: 'Not covered' });
    });

    it('does nothing when the dialog is cancelled', async () => {
      await open({});
      vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({ afterClosed: () => of(undefined) } as never);
      button('Reject').click();
      await fixture.whenStable();
      http.expectNone('/api/staff/claims/c1/reject');
    });

    it('reloads the claim after a 409 conflict so the screen shows the current state', async () => {
      await open({ assessedAmount: 4200 });
      vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({ afterClosed: () => of({}) } as never);
      button('Approve').click();
      await fixture.whenStable();
      http.expectOne('/api/staff/claims/c1/approve').flush(
        { status: 409, detail: 'Cannot move claim from SETTLED to APPROVED' }, { status: 409, statusText: 'Conflict' });
      await fixture.whenStable();
      flushLoad({ ...base, status: 'SETTLED', assessedAmount: 4200 });
      await fixture.whenStable();
      fixture.detectChanges();
      expect(el().querySelector('.ref-line')!.textContent).toContain('Settled');
    });

    it('while waiting on the claimant shows the open request and only reassignment', async () => {
      await open({ status: 'INFO_REQUESTED' }, [openRequest]);
      expect(el().textContent).toContain('Waiting for the claimant');
      expect(el().textContent).toContain("Awaiting the claimant's answer");
      expect(buttons().filter((b) => /Approve|Reject|Assess|Request info/.test(b))).toEqual([]);
      expect(button('Reassign')).toBeTruthy();
    });
  });

  it('another officer sees the claim read-only', async () => {
    signedInAs('officer-2', 'OFFICER');
    await open({});
    expect(el().textContent).toContain('Only the assigned officer can act on it');
    expect(el().querySelector('.action-bar')).toBeNull();
  });

  it('an officer can pick up a submitted claim', async () => {
    signedInAs('officer-2', 'OFFICER');
    await open({ status: 'SUBMITTED', assignedOfficerId: null });
    expect(button('Pick up')).toBeTruthy();
  });

  it('a manager can only reassign', async () => {
    signedInAs('manager-1', 'MANAGER');
    await open({});
    expect(buttons().filter((b) => /Approve|Reject|Assess|Request info|Settle|Pick up/.test(b))).toEqual([]);
    expect(button('Reassign')).toBeTruthy();
  });

  describe('as the claimant', () => {
    beforeEach(() => signedInAs('tan@example.com', 'CLAIMANT'));

    it('answers an open information request', async () => {
      await open({ status: 'INFO_REQUESTED' }, [openRequest]);
      expect(el().textContent).toContain('We need more information from you');
      const box = el().querySelector('textarea') as HTMLTextAreaElement;
      box.value = '  Photos attached  ';
      box.dispatchEvent(new Event('input'));
      button('Send answer').click();
      await fixture.whenStable();
      const req = http.expectOne('/api/claims/c1/info-requests/r1/response');
      expect(req.request.body).toEqual({ response: 'Photos attached' });
    });

    it('will not send an empty answer', async () => {
      await open({ status: 'INFO_REQUESTED' }, [openRequest]);
      button('Send answer').click();
      await fixture.whenStable();
      http.expectNone('/api/claims/c1/info-requests/r1/response');
    });

    it('sees the rejection reason and cannot add documents to a closed claim', async () => {
      await open({ status: 'REJECTED', rejectionReason: 'Policy lapsed', closedAt: '2026-01-12T00:00:00Z' });
      expect(el().textContent).toContain('Reason: Policy lapsed');
      expect(el().textContent).toContain('Documents cannot be added to a closed claim');
      expect(el().querySelector('app-file-picker')).toBeNull();
    });

    it('has no staff actions and can upload while the claim is open', async () => {
      await open({});
      expect(el().querySelector('.action-bar')).toBeNull();
      expect(el().querySelector('app-file-picker')).not.toBeNull();
    });

    it('cannot upload once the claim has 20 documents', async () => {
      const docs = Array.from({ length: 20 }, (_, i) => ({
        id: `d${i}`, fileName: `f${i}.pdf`, contentType: 'application/pdf', sizeBytes: 10, uploadedBy: 'tan@example.com', uploadedAt: '2026-01-10T01:00:00Z',
      }));
      await open({}, [], docs);
      expect(el().textContent).toContain('20 of 20');
      expect(el().querySelector('app-file-picker')).toBeNull();
    });
  });

  it("shows 'not found' for another claimant's claim (the API answers 404)", async () => {
    signedInAs('lee@example.com', 'CLAIMANT');
    TestBed.configureTestingModule({ imports: [ClaimDetailPage], providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ClaimDetailPage);
    fixture.componentRef.setInput('id', 'c1');
    fixture.detectChanges();
    http.expectOne('/api/claims/c1').flush({ status: 404, detail: 'Claim not found' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(el().textContent).toContain('Claim not found');
  });
});
