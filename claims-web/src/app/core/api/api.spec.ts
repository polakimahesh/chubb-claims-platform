import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { testProviders } from '../../../testing';
import { ClaimsApi, params } from './claims.api';
import { NotificationsApi } from './notifications.api';
import { ReportsApi } from './reports.api';

/** The API clients call exactly the backend's endpoints, methods and bodies (the contract in the OpenAPI files). */
describe('API clients', () => {
  let http: HttpTestingController;
  let claims: ClaimsApi;
  let reports: ReportsApi;
  let notifications: NotificationsApi;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    claims = TestBed.inject(ClaimsApi);
    reports = TestBed.inject(ReportsApi);
    notifications = TestBed.inject(NotificationsApi);
  });

  afterEach(() => http.verify());

  const expectCall = (method: string, url: string, body?: unknown) => {
    const req = http.expectOne((r) => r.urlWithParams === url);
    expect(req.request.method).toBe(method);
    if (body !== undefined) expect(req.request.body).toEqual(body);
    req.flush({});
    return req;
  };

  it('claimant endpoints', () => {
    const submit = {
      claimantName: 'Tan Wei', market: 'SG' as const, claimType: 'MOTOR' as const, description: 'd',
      incidentDate: '2026-01-10', currency: 'SGD', estimatedAmount: 5000,
    };
    claims.submit(submit).subscribe();
    expectCall('POST', '/api/claims', submit);
    claims.myClaims(2, 10).subscribe();
    expectCall('GET', '/api/claims?page=2&size=10');
    claims.get('c1').subscribe();
    expectCall('GET', '/api/claims/c1');
    claims.history('c1').subscribe();
    expectCall('GET', '/api/claims/c1/history');
    claims.infoRequests('c1').subscribe();
    expectCall('GET', '/api/claims/c1/info-requests');
    claims.respond('c1', 'r1', 'answer').subscribe();
    expectCall('POST', '/api/claims/c1/info-requests/r1/response', { response: 'answer' });
  });

  it('documents: multipart part "file", list, download as a blob', () => {
    const file = new File(['%PDF'], 'report.pdf', { type: 'application/pdf' });
    claims.upload('c1', file).subscribe();
    const up = http.expectOne('/api/claims/c1/documents');
    expect(up.request.method).toBe('POST');
    expect((up.request.body as FormData).get('file')).toBeInstanceOf(File);
    up.flush({});
    claims.documents('c1').subscribe();
    expectCall('GET', '/api/claims/c1/documents');
    claims.download('c1', 'd1').subscribe();
    const dl = http.expectOne('/api/claims/c1/documents/d1');
    expect(dl.request.responseType).toBe('blob');
    dl.flush(new Blob(['x']));
  });

  it('staff endpoints and bodies', () => {
    claims.queue({ market: 'SG', claimType: null }, 0, 20).subscribe();
    expectCall('GET', '/api/staff/claims/queue?market=SG&page=0&size=20');
    claims.workload({ officerId: 'officer-2', status: 'APPROVED' }).subscribe();
    expectCall('GET', '/api/staff/claims?officerId=officer-2&status=APPROVED&page=0&size=20');
    claims.officers().subscribe();
    expectCall('GET', '/api/staff/officers');
    claims.assign('c1').subscribe();
    expectCall('POST', '/api/staff/claims/c1/assign');
    claims.reassign('c1', 'officer-2', '').subscribe();
    expectCall('POST', '/api/staff/claims/c1/reassign', { toOfficerId: 'officer-2', reason: null });
    claims.requestInfo('c1', 'Photos?').subscribe();
    expectCall('POST', '/api/staff/claims/c1/info-requests', { question: 'Photos?' });
    claims.assess('c1', 4200, null).subscribe();
    expectCall('POST', '/api/staff/claims/c1/assess', { assessedAmount: 4200, note: null });
    claims.approve('c1').subscribe();
    expectCall('POST', '/api/staff/claims/c1/approve');
    claims.reject('c1', 'Not covered').subscribe();
    expectCall('POST', '/api/staff/claims/c1/reject', { reason: 'Not covered' });
    claims.settle('c1').subscribe();
    expectCall('POST', '/api/staff/claims/c1/settle');
  });

  it('report endpoints', () => {
    reports.summary().subscribe();
    expectCall('GET', '/api/reports/summary');
    reports.exposure('AU').subscribe();
    expectCall('GET', '/api/reports/exposure?market=AU');
    reports.exposure(null).subscribe();
    expectCall('GET', '/api/reports/exposure');
    reports.exposureTotal('SGD').subscribe();
    expectCall('GET', '/api/reports/exposure/total?baseCurrency=SGD');
    reports.workload().subscribe();
    expectCall('GET', '/api/reports/workload');
    reports.performance().subscribe();
    expectCall('GET', '/api/reports/performance');
    reports.slaBreaches().subscribe();
    expectCall('GET', '/api/reports/sla-breaches');
  });

  it('notifications: own, or filtered by claimant e-mail for staff', () => {
    notifications.list(null).subscribe();
    expectCall('GET', '/api/notifications?page=0&size=20');
    notifications.list('lee@example.com', 1, 10).subscribe();
    expectCall('GET', '/api/notifications?claimantEmail=lee@example.com&page=1&size=10');
  });

  it('params() drops null, undefined and empty values', () => {
    expect(params({ a: 1, b: null, c: undefined, d: '', e: 'x' }).toString()).toBe('a=1&e=x');
  });
});
