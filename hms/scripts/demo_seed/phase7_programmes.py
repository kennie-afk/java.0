"""Programme registers: a handful of people on hypertension, diabetes, TB and HIV follow-up, two of them overdue so the tracing list is not empty."""
import json, os, tempfile, datetime
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'phase1_foundation.py')).read().split("PW='Lakeview-Demo-2026'")[0])
STATE = os.environ.get('HMS_SEED_STATE', os.path.join(tempfile.gettempdir(), 'hms_seed_state.json'))
s = json.load(open(STATE)); FAC = s['FAC']
PW = 'Lakeview-Demo-2026'
login = lambda e: call('POST', '/v1/auth/login', {'email': e, 'password': PW})['token']
N = login('nurse@lakeview.test'); R = login('records@lakeview.test')
today = datetime.date.today()
d = lambda n: (today + datetime.timedelta(days=n)).isoformat()

def person(given, family, sex, years, phone):
    r = call('POST', '/v1/patients', {'facilityId': FAC, 'demographics': {'givenName': given, 'familyName': family, 'sex': sex, 'birthDate': d(-365 * years), 'phone': phone, 'county': 'Kisumu'},
                                      'identifiers': [], 'contacts': [], 'confirmNotDuplicate': True}, R)
    return r['id'] if r else None

def enrol(pid, programme, enrolled_ago, regimen, next_in, visits=()):
    if not pid:
        return
    e = call('POST', '/v1/programmes/enrolments', {'facilityId': FAC, 'patientId': pid, 'programme': programme, 'enrolledOn': d(-enrolled_ago), 'regimen': regimen, 'nextVisitOn': d(next_in)}, N)
    if not e:
        return
    for ago, body in visits:
        call('POST', f"/v1/programmes/enrolments/{e['id']}/visits", dict(body, visitedOn=d(-ago)), N)

enrol(person('Joseph', 'Onyango', 'MALE', 58, '0722100001'), 'HYPERTENSION', 200, 'Amlodipine 10 mg', 25,
      [(90, {'systolic': 158, 'diastolic': 96, 'weightKg': 84, 'adherence': 'FAIR', 'nextVisitOn': d(-60)}), (28, {'systolic': 138, 'diastolic': 88, 'weightKg': 82.5, 'adherence': 'GOOD', 'nextVisitOn': d(25)})])
enrol(person('Mary', 'Atieno', 'FEMALE', 49, '0733100002'), 'DIABETES', 400, 'Metformin 500 mg twice daily', 12,
      [(40, {'glucoseMmol': 9.8, 'weightKg': 77, 'adherence': 'GOOD', 'nextVisitOn': d(12)})])
enrol(person('Peter', 'Odhiambo', 'MALE', 44, '0711100003'), 'TB', 70, 'Standard first-line, continuation phase', 3,
      [(30, {'weightKg': 58, 'adherence': 'GOOD', 'nextVisitOn': d(3)})])
# Overdue: the tracing list should show these.
enrol(person('Grace', 'Awino', 'FEMALE', 36, '0722100004'), 'TB', 120, 'Standard first-line, intensive phase', -21,
      [(50, {'weightKg': 52, 'adherence': 'FAIR', 'nextVisitOn': d(-21)})])
enrol(person('Samson', 'Okello', 'MALE', 41, '0733100005'), 'HYPERTENSION', 300, 'Enalapril 10 mg', -12,
      [(75, {'systolic': 164, 'diastolic': 102, 'adherence': 'POOR', 'nextVisitOn': d(-12)})])
enrol(person('Beatrice', 'Adhiambo', 'FEMALE', 32, '0711100006'), 'HIV', 500, 'First-line, fixed-dose combination', 40,
      [(20, {'weightKg': 61, 'adherence': 'GOOD', 'nextVisitOn': d(40)})])
print('phase 7 programmes done')
