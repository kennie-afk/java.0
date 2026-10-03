"""Maternal and child health sample data: three pregnancies at different stages and two infants with part-filled cards."""
import json, os, tempfile, datetime
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'phase1_foundation.py')).read().split("PW='Lakeview-Demo-2026'")[0])
STATE = os.environ.get('HMS_SEED_STATE', os.path.join(tempfile.gettempdir(), 'hms_seed_state.json'))
s = json.load(open(STATE)); FAC = s['FAC']
PW = 'Lakeview-Demo-2026'
login = lambda e: call('POST', '/v1/auth/login', {'email': e, 'password': PW})['token']
N = login('nurse@lakeview.test'); R = login('records@lakeview.test')
today = datetime.date.today()
iso = lambda d: d.isoformat()

def person(given, family, sex, birth, phone):
    r = call('POST', '/v1/patients', {'facilityId': FAC, 'demographics': {'givenName': given, 'familyName': family, 'sex': sex, 'birthDate': iso(birth), 'phone': phone, 'county': 'Nairobi'},
                                      'identifiers': [], 'contacts': [], 'confirmNotDuplicate': True}, R)
    return r['id'] if r else None

def pregnancy(given, family, born_years, weeks, gravida, parity, phone, visits):
    pid = person(given, family, 'FEMALE', today - datetime.timedelta(days=365 * born_years), phone)
    if not pid:
        return
    p = call('POST', '/v1/mch/pregnancies', {'facilityId': FAC, 'patientId': pid, 'lmp': iso(today - datetime.timedelta(weeks=weeks)), 'gravida': gravida, 'parity': parity}, N)
    if not p:
        return
    for ago_weeks, body in visits:
        call('POST', f"/v1/mch/pregnancies/{p['id']}/visits", dict(body, visitedOn=iso(today - datetime.timedelta(weeks=ago_weeks))), N)

# Routine, on schedule.
pregnancy('Esther', 'Mwangi', 27, 28, 2, 1, '0722000101', [
    (12, {'weightKg': 62, 'systolic': 112, 'diastolic': 70, 'fetalHeartRate': 144, 'haemoglobin': 12.1, 'hivStatus': 'NEGATIVE', 'syphilis': 'NEGATIVE', 'ironFolateGiven': True}),
    (4, {'weightKg': 65, 'systolic': 116, 'diastolic': 72, 'fundalHeightCm': 27, 'fetalHeartRate': 142, 'presentation': 'CEPHALIC', 'iptpGiven': True, 'ironFolateGiven': True, 'nextVisitOn': iso(today + datetime.timedelta(weeks=3))})])
# Raised blood pressure with protein: the screen should show a danger flag.
pregnancy('Lucy', 'Akinyi', 31, 34, 3, 2, '0733000202', [
    (6, {'weightKg': 70, 'systolic': 118, 'diastolic': 76, 'fetalHeartRate': 140, 'haemoglobin': 10.4}),
    (0, {'weightKg': 74, 'systolic': 152, 'diastolic': 98, 'fetalHeartRate': 138, 'urineProtein': '2+', 'fundalHeightCm': 33, 'nextVisitOn': iso(today + datetime.timedelta(days=3))})])
# Missed her next visit: shows as overdue.
pregnancy('Ruth', 'Chepkoech', 19, 22, 1, 0, '0711000303', [
    (9, {'weightKg': 55, 'systolic': 108, 'diastolic': 66, 'fetalHeartRate': 150, 'nextVisitOn': iso(today - datetime.timedelta(weeks=2))})])

def infant(given, family, months, phone, doses):
    pid = person(given, family, 'MALE', today - datetime.timedelta(days=int(months * 30.4)), phone)
    if not pid:
        return
    for vaccine, ago_days in doses:
        call('POST', f'/v1/mch/immunisation/patients/{pid}/doses', {'facilityId': FAC, 'vaccine': vaccine, 'givenOn': iso(today - datetime.timedelta(days=ago_days)), 'batchNo': 'B-' + vaccine}, N)

infant('Brian', 'Otieno', 4, '0722000404', [('BCG', 120), ('OPV_0', 120), ('OPV_1', 78), ('PENTA_1', 78), ('PCV_1', 78), ('ROTA_1', 78), ('OPV_2', 50), ('PENTA_2', 50), ('PCV_2', 50), ('ROTA_2', 50)])
infant('Kevin', 'Mutua', 2.5, '0733000505', [('BCG', 75), ('OPV_0', 75)])
infant('Samuel', 'Kariuki', 0.3, '0711000606', [('BCG', 8)])

card = call('GET', '/v1/mch/immunisation/due?limit=1', None, N)
print('due list reachable:', card is not None)
print('phase 5 done')
