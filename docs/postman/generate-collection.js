// Generates docs/postman/claims-platform.postman_collection.json: node docs/postman/generate-collection.js docs/postman/claims-platform.postman_collection.json
const fs = require('fs');

const basic = (userVar) => ({ type: 'basic', basic: [
  { key: 'username', value: `{{${userVar}}}`, type: 'string' },
  { key: 'password', value: '{{demoPassword}}', type: 'string' }] });
const NOAUTH = { type: 'noauth' };
const JSONH = [{ key: 'Content-Type', value: 'application/json' }];

function req(name, method, url, { user, body, tests = [], pre = [], noauth = false, formdata, query } = {}) {
  const item = { name, request: { method, header: [], url: { raw: url, host: [url.split('/api')[0].split('?')[0]], path: [] } } };
  // keep the raw URL readable and Postman-friendly
  item.request.url = url;
  if (body !== undefined) { item.request.header = JSONH; item.request.body = { mode: 'raw', raw: typeof body === 'string' ? body : JSON.stringify(body, null, 2) }; }
  if (formdata) { item.request.body = { mode: 'formdata', formdata }; }
  if (noauth) item.request.auth = NOAUTH; else if (user) item.request.auth = basic(user);
  const events = [];
  if (pre.length) events.push({ listen: 'prerequest', script: { type: 'text/javascript', exec: pre } });
  if (tests.length) events.push({ listen: 'test', script: { type: 'text/javascript', exec: tests } });
  if (events.length) item.event = events;
  return item;
}
const status = (code) => `pm.test('status is ${code}', () => pm.response.to.have.status(${code}));`;
const problem = (code) => [status(code),
  `pm.test('RFC 7807 problem body', () => { pm.expect(pm.response.headers.get('Content-Type')).to.include('problem+json'); pm.expect(pm.response.json().status).to.eql(${code}); });`];

const submitBody = { claimantName: 'Tan Wei', market: 'SG', claimType: 'MOTOR', description: 'Rear-ended at a traffic light',
  incidentDate: '2026-01-10', currency: 'SGD', estimatedAmount: 5000.0 };

const C = '{{claimsUrl}}', R = '{{reportingUrl}}', N = '{{notificationUrl}}';

const happy = [
  req('1. Submit claim (claimant)', 'POST', `${C}/api/claims`, { user: 'claimantUser', body: submitBody, tests: [
    status(201), 'const b = pm.response.json();', "pm.collectionVariables.set('claimId', b.id);", "pm.collectionVariables.set('claimNumber', b.claimNumber);",
    "pm.test('SUBMITTED, owned by the authenticated claimant', () => { pm.expect(b.status).to.eql('SUBMITTED'); pm.expect(b.claimantEmail).to.eql(pm.collectionVariables.get('claimantUser')); });"] }),
  req('2. Upload document (claimant, PDF/PNG/JPEG, max 5 MB)', 'POST', `${C}/api/claims/{{claimId}}/documents`, { user: 'claimantUser',
    formdata: [{ key: 'file', type: 'file', src: 'sample-police-report.pdf' }], tests: [
    status(201), "pm.collectionVariables.set('documentId', pm.response.json().id);",
    "pm.test('metadata returned', () => { const d = pm.response.json(); pm.expect(d.contentType).to.eql('application/pdf'); pm.expect(d.sizeBytes).to.be.above(0); });"] }),
  req('3. List documents (officer)', 'GET', `${C}/api/claims/{{claimId}}/documents`, { user: 'officerUser', tests: [status(200), "pm.test('one document', () => pm.expect(pm.response.json().length).to.eql(1));"] }),
  req('4. Download document (officer)', 'GET', `${C}/api/claims/{{claimId}}/documents/{{documentId}}`, { user: 'officerUser', tests: [status(200),
    "pm.test('served as attachment, nosniff', () => { pm.expect(pm.response.headers.get('Content-Disposition')).to.include('attachment'); pm.expect(pm.response.headers.get('X-Content-Type-Options')).to.eql('nosniff'); });"] }),
  req('5. Intake queue (officer)', 'GET', `${C}/api/staff/claims/queue?market=SG&claimType=MOTOR`, { user: 'officerUser', tests: [status(200),
    "pm.test('claim is in the queue', () => pm.expect(pm.response.json().items.map(i => i.id)).to.include(pm.collectionVariables.get('claimId')));"] }),
  req('6. Assign / pick up (officer-1)', 'POST', `${C}/api/staff/claims/{{claimId}}/assign`, { user: 'officerUser', tests: [status(200),
    "pm.test('UNDER_REVIEW and assigned', () => { const b = pm.response.json(); pm.expect(b.status).to.eql('UNDER_REVIEW'); pm.expect(b.assignedOfficerId).to.eql(pm.collectionVariables.get('officerUser')); });"] }),
  req('7. My workload (officer-1)', 'GET', `${C}/api/staff/claims?status=UNDER_REVIEW`, { user: 'officerUser', tests: [status(200),
    "pm.test('claim is in my workload', () => pm.expect(pm.response.json().items.map(i => i.id)).to.include(pm.collectionVariables.get('claimId')));"] }),
  req('8. Reassign to officer-2 (current officer or manager)', 'POST', `${C}/api/staff/claims/{{claimId}}/reassign`, { user: 'officerUser',
    body: { toOfficerId: '{{officer2User}}', reason: 'Workload balancing' }, tests: [status(200),
    "pm.test('now assigned to officer-2, still UNDER_REVIEW', () => { const b = pm.response.json(); pm.expect(b.assignedOfficerId).to.eql(pm.collectionVariables.get('officer2User')); pm.expect(b.status).to.eql('UNDER_REVIEW'); });"] }),
  req('9. Request information from claimant (officer-2)', 'POST', `${C}/api/staff/claims/{{claimId}}/info-requests`, { user: 'officer2User',
    body: { question: 'Please provide the police report number' }, tests: [status(200), "pm.collectionVariables.set('infoRequestId', pm.response.json().id);",
    "pm.test('request is open', () => pm.expect(pm.response.json().open).to.eql(true));"] }),
  req('10. List information requests (claimant)', 'GET', `${C}/api/claims/{{claimId}}/info-requests`, { user: 'claimantUser', tests: [status(200), "pm.test('one open request', () => pm.expect(pm.response.json()[0].open).to.eql(true));"] }),
  req('11. Provide the information (claimant)', 'POST', `${C}/api/claims/{{claimId}}/info-requests/{{infoRequestId}}/response`, { user: 'claimantUser',
    body: { response: 'Police report no. 12345' }, tests: [status(200), "pm.test('request closed', () => pm.expect(pm.response.json().open).to.eql(false));"] }),
  req('12. Assess liability (officer-2)', 'POST', `${C}/api/staff/claims/{{claimId}}/assess`, { user: 'officer2User', body: { assessedAmount: 4200.0, note: 'Repair estimate verified' }, tests: [status(200),
    "pm.test('assessed amount stored', () => pm.expect(pm.response.json().assessedAmount).to.eql(4200));"] }),
  req('13. Approve (officer-2)', 'POST', `${C}/api/staff/claims/{{claimId}}/approve`, { user: 'officer2User', tests: [status(200), "pm.test('APPROVED', () => pm.expect(pm.response.json().status).to.eql('APPROVED'));"] }),
  req('14. Settle (officer-2)', 'POST', `${C}/api/staff/claims/{{claimId}}/settle`, { user: 'officer2User', tests: [status(200), "pm.test('SETTLED', () => pm.expect(pm.response.json().status).to.eql('SETTLED'));"] }),
  req('15. Track claim (claimant)', 'GET', `${C}/api/claims/{{claimId}}`, { user: 'claimantUser', tests: [status(200), "pm.test('SETTLED', () => pm.expect(pm.response.json().status).to.eql('SETTLED'));"] }),
  req('16. Status timeline (claimant)', 'GET', `${C}/api/claims/{{claimId}}/history`, { user: 'claimantUser', tests: [status(200),
    "pm.test('8 audit entries (submitted, assigned, reassigned, info requested/provided, assessed, approved, settled)', () => pm.expect(pm.response.json().length).to.eql(8));"] }),
  req('17. My claims (claimant)', 'GET', `${C}/api/claims?page=0&size=20`, { user: 'claimantUser', tests: [status(200),
    "pm.test('contains the claim', () => pm.expect(pm.response.json().items.map(i => i.id)).to.include(pm.collectionVariables.get('claimId')));"] }),
  req('18. Reject a claim (alternative to approve: submit, assign, then reject)', 'POST', `${C}/api/claims`, { user: 'claimantUser', body: submitBody, tests: [status(201),
    "pm.collectionVariables.set('claimId2', pm.response.json().id);"] }),
  req('19. Assign the second claim (officer-1)', 'POST', `${C}/api/staff/claims/{{claimId2}}/assign`, { user: 'officerUser', tests: [status(200)] }),
  req('20. Reject the second claim with a reason (officer-1)', 'POST', `${C}/api/staff/claims/{{claimId2}}/reject`, { user: 'officerUser', body: { reason: 'Policy lapsed at date of incident' }, tests: [status(200),
    "pm.test('REJECTED with reason', () => { const b = pm.response.json(); pm.expect(b.status).to.eql('REJECTED'); pm.expect(b.rejectionReason).to.include('lapsed'); });"] }),
];

const waitScript = ["// reports and notifications are built from Kafka events: give the consumers a moment", 'setTimeout(function () {}, 5000);'];
const reports = [
  req('Summary (manager)', 'GET', `${R}/api/reports/summary`, { user: 'managerUser', pre: waitScript, tests: [status(200),
    "pm.test('has settled and rejected claims', () => { const b = pm.response.json(); pm.expect(b.claimsByStatus.SETTLED).to.be.at.least(1); pm.expect(b.claimsByStatus.REJECTED).to.be.at.least(1); });"] }),
  req('Liability exposure by market/type/currency (manager)', 'GET', `${R}/api/reports/exposure`, { user: 'managerUser', tests: [status(200), "pm.test('array', () => pm.expect(pm.response.json()).to.be.an('array'));"] }),
  req('Exposure total in one currency (manager)', 'GET', `${R}/api/reports/exposure/total?baseCurrency=USD`, { user: 'managerUser', tests: [status(200),
    "pm.test('base currency and rate source', () => { const b = pm.response.json(); pm.expect(b.baseCurrency).to.eql('USD'); pm.expect(b.rateSource).to.include('not live'); });"] }),
  req('Officer workload (manager)', 'GET', `${R}/api/reports/workload`, { user: 'managerUser', tests: [status(200)] }),
  req('Officer performance (manager)', 'GET', `${R}/api/reports/performance`, { user: 'managerUser', tests: [status(200),
    "pm.test('officer-2 settled a claim, officer-1 rejected one', () => { const rows = pm.response.json(); const o2 = rows.find(r => r.officerId === pm.collectionVariables.get('officer2User')); const o1 = rows.find(r => r.officerId === pm.collectionVariables.get('officerUser')); pm.expect(o2.settled).to.be.at.least(1); pm.expect(o1.rejected).to.be.at.least(1); });"] }),
  req('SLA breaches (manager)', 'GET', `${R}/api/reports/sla-breaches`, { user: 'managerUser', tests: [status(200), "pm.test('array', () => pm.expect(pm.response.json()).to.be.an('array'));"] }),
];

const notifications = [
  req('My notifications (claimant)', 'GET', `${N}/api/notifications?size=50`, { user: 'claimantUser', pre: waitScript, tests: [status(200),
    "pm.test('received the milestone e-mails for the settled claim', () => { const mine = pm.response.json().items.filter(n => n.claimNumber === pm.collectionVariables.get('claimNumber')).map(n => n.eventType); ['CLAIM_SUBMITTED','CLAIM_ASSIGNED','INFO_REQUESTED','CLAIM_APPROVED','CLAIM_SETTLED'].forEach(t => pm.expect(mine).to.include(t)); pm.expect(mine).to.not.include('CLAIM_ASSESSED'); });"] }),
  req('All notifications for a claimant (manager)', 'GET', `${N}/api/notifications?claimantEmail={{claimantUser}}`, { user: 'managerUser', tests: [status(200), "pm.test('has items', () => pm.expect(pm.response.json().totalItems).to.be.above(0));"] }),
];

const negative = [
  req('No credentials -> 401', 'GET', `${C}/api/claims`, { noauth: true, tests: [...problem(401), "pm.test('Basic challenge', () => pm.expect(pm.response.headers.get('WWW-Authenticate')).to.include('Basic'));"] }),
  req('Claimant calling staff API -> 403', 'GET', `${C}/api/staff/claims/queue`, { user: 'claimantUser', tests: problem(403) }),
  req('Officer cannot submit a claim -> 403', 'POST', `${C}/api/claims`, { user: 'officerUser', body: submitBody, tests: problem(403) }),
  req('Another claimant cannot see the claim -> 404', 'GET', `${C}/api/claims/{{claimId}}`, { user: 'otherClaimantUser', tests: problem(404) }),
  req('Officer cannot read reports -> 403', 'GET', `${R}/api/reports/summary`, { user: 'officerUser', tests: problem(403) }),
  req('Manager is read-only on claims (assign) -> 403', 'POST', `${C}/api/staff/claims/{{claimId}}/assign`, { user: 'managerUser', tests: problem(403) }),
  req('Validation error -> 400 with field errors', 'POST', `${C}/api/claims`, { user: 'claimantUser', body: { estimatedAmount: -1 }, tests: [...problem(400), "pm.test('lists field errors', () => pm.expect(pm.response.json().errors).to.be.an('array').that.is.not.empty);"] }),
  req('Currency must match the market -> 400', 'POST', `${C}/api/claims`, { user: 'claimantUser', body: { ...submitBody, currency: 'USD' }, tests: problem(400) }),
  req('Malformed JSON -> 400', 'POST', `${C}/api/claims`, { user: 'claimantUser', body: '{not json', tests: problem(400) }),
  req('Invalid transition (settle a settled claim) -> 409', 'POST', `${C}/api/staff/claims/{{claimId}}/settle`, { user: 'officer2User', tests: problem(409) }),
  req('Approve without assessment -> 422', 'POST', `${C}/api/claims`, { user: 'claimantUser', body: submitBody, tests: [status(201), "pm.collectionVariables.set('claimId3', pm.response.json().id);"] }),
  req('  ...assign it', 'POST', `${C}/api/staff/claims/{{claimId3}}/assign`, { user: 'officerUser', tests: [status(200)] }),
  req('  ...approve before assessing -> 422', 'POST', `${C}/api/staff/claims/{{claimId3}}/approve`, { user: 'officerUser', tests: problem(422) }),
  req('Another officer cannot act on an assigned claim -> 422', 'POST', `${C}/api/staff/claims/{{claimId3}}/assess`, { user: 'officer2User', body: { assessedAmount: 10 }, tests: problem(422) }),
  req('Unknown claim -> 404', 'GET', `${C}/api/claims/00000000-0000-0000-0000-000000000000`, { user: 'claimantUser', tests: problem(404) }),
  req('Unsupported document type -> 415', 'POST', `${C}/api/claims/{{claimId3}}/documents`, { user: 'claimantUser',
    formdata: [{ key: 'file', type: 'file', src: 'claims-platform.postman_collection.json' }], tests: problem(415) }),
  req('Unknown report market -> 400', 'GET', `${R}/api/reports/exposure?market=MARS`, { user: 'managerUser', tests: problem(400) }),
];

const health = ['claims', 'reporting', 'notification'].map((s, i) => req(`${s}-service health (public)`, 'GET', `${[C, R, N][i]}/actuator/health`, { noauth: true, tests: [status(200)] }));
const docs = ['claims', 'reporting', 'notification'].map((s, i) => req(`${s}-service OpenAPI (public)`, 'GET', `${[C, R, N][i]}/v3/api-docs`, { noauth: true, tests: [status(200)] }));

const collection = {
  info: {
    name: 'Claims Platform',
    description: 'End-to-end collection for the three services. Set the collection variable `demoPassword` to APP_SECURITY_DEMO_PASSWORD from your .env (it is intentionally not stored here), then run the folders in order: 0 -> 4. Requests save claimId / infoRequestId / documentId automatically and every request asserts its status and shape. Run headless with: newman run claims-platform.postman_collection.json --working-dir docs/postman --env-var demoPassword=<password>',
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json' },
  variable: [
    { key: 'claimsUrl', value: 'http://localhost:8081' }, { key: 'reportingUrl', value: 'http://localhost:8082' }, { key: 'notificationUrl', value: 'http://localhost:8083' },
    { key: 'demoPassword', value: '' },
    { key: 'claimantUser', value: 'tan@example.com' }, { key: 'otherClaimantUser', value: 'lee@example.com' },
    { key: 'officerUser', value: 'officer-1' }, { key: 'officer2User', value: 'officer-2' }, { key: 'managerUser', value: 'manager-1' },
    { key: 'claimId', value: '' }, { key: 'claimId2', value: '' }, { key: 'claimId3', value: '' }, { key: 'claimNumber', value: '' }, { key: 'infoRequestId', value: '' }, { key: 'documentId', value: '' }],
  item: [
    { name: '0. Health & API docs (public)', item: [...health, ...docs] },
    { name: '1. Claim lifecycle (claimant + officers)', item: happy },
    { name: '2. Manager reports', item: reports },
    { name: '3. Claimant notifications', item: notifications },
    { name: '4. Negative scenarios (errors, authN/authZ)', item: negative },
  ],
};
fs.writeFileSync(process.argv[2], JSON.stringify(collection, null, 2) + '\n');
console.log('written', process.argv[2], 'requests:', JSON.stringify(collection).split('"method"').length - 1);
