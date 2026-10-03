"""Patient portal demo: one patient with a portal account, one released result, one withheld result, and a pending appointment request."""
import json, os, tempfile, datetime
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'phase1_foundation.py')).read().split("PW='Lakeview-Demo-2026'")[0])
STATE = os.environ.get('HMS_SEED_STATE', os.path.join(tempfile.gettempdir(), 'hms_seed_state.json'))
s = json.load(open(STATE)); FAC = s['FAC']
PW = 'Lakeview-Demo-2026'
login = lambda e: call('POST', '/v1/auth/login', {'email': e, 'password': PW})['token']
A = login('admin@lakeview.test'); R = login('records@lakeview.test'); D = login('doctor@lakeview.test'); L = login('lab@lakeview.test')
pat = call('POST', '/v1/patients', {'facilityId': FAC, 'demographics': {'givenName': 'Wanjiku', 'familyName': 'Kamau', 'sex': 'FEMALE', 'birthDate': '1992-02-02', 'phone': '0722555000', 'county': 'Kisumu'},
                                    'identifiers': [], 'contacts': [], 'confirmNotDuplicate': True}, R)
if pat:
    pid = pat['id']
    inv = call('POST', '/v1/portal/invitations', {'patientId': pid}, R)
    org_slug = 'lakeview'
    call('POST', '/portal/auth/activate', {'organisation': org_slug, 'code': inv['code'], 'birthDate': '1992-02-02', 'login': 'wanjiku.demo@lakeview.test', 'password': PW})
    tests = call('GET', '/v1/lab/tests', None, A) or []
    done = 0
    for t in tests[:2]:
        o = call('POST', '/v1/lab/orders', {'facilityId': FAC, 'patientId': pid, 'testIds': [t['id']], 'clinicalInfo': 'Routine check'}, D)
        if not o:
            continue
        call('POST', f"/v1/lab/orders/{o['id']}/collect", {}, L)
        item = o['items'][0]['id']
        val = {'numeric': 14.1} if t['resultType'] == 'NUMERIC' else {'text': 'Negative'}
        # Entered by the administrator, validated by the technologist: four eyes.
        call('POST', f'/v1/lab/items/{item}/result', val, A)
        call('POST', f'/v1/lab/items/{item}/validate', {}, L)
        if done == 0:
            call('POST', f'/v1/portal/lab-items/{item}/release', {}, D)   # first one released; the second is validated but withheld
        done += 1
    p_tok = call('POST', '/portal/auth/login', {'organisation': org_slug, 'login': 'wanjiku.demo@lakeview.test', 'password': PW})
    if p_tok:
        call('POST', '/portal/appointment-requests', {'facilityId': FAC, 'preferredDate': (datetime.date.today() + datetime.timedelta(days=4)).isoformat(), 'reason': 'Follow-up on my results'}, p_tok['token'])
    print('portal demo: sign in at /portal/login with organisation "lakeview", wanjiku.demo@lakeview.test and the demo password')
print('phase 9 portal done')
