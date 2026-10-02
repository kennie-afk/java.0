"""Makes the screens worth looking at: an active allergy on a chart and a prescription waiting in the pharmacy."""
import json, os, tempfile
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'phase1_foundation.py')).read().split("PW='Lakeview-Demo-2026'")[0])
STATE = os.environ.get('HMS_SEED_STATE', os.path.join(tempfile.gettempdir(), 'hms_seed_state.json'))
s = json.load(open(STATE)); T = s['tok']; P_ = s['P']; dr = s['drugs']
PW = 'Lakeview-Demo-2026'
login = lambda e: call('POST', '/v1/auth/login', {'email': e, 'password': PW})['token']
D = login('doctor@lakeview.test')
FAC = s['FAC']
# Fresh tokens: the ones saved by phase 1 may have expired.
mary = P_['Mary']
if not [a for a in (call('GET', f'/v1/clinical/patients/{mary}/allergies', None, D) or []) if a['substance'] == 'Penicillin']:
    call('POST', f'/v1/clinical/patients/{mary}/allergies', {'substance': 'Penicillin', 'category': 'DRUG', 'severity': 'SEVERE', 'reaction': 'Anaphylaxis in 2014'}, D)
enc = (call('GET', f'/v1/clinical/encounters?patientId={P_["Otieno"]}', None, D) or {}).get('data', [])
open_enc = next((e for e in enc if e['status'] == 'OPEN'), None)
queue = (call('GET', f'/v1/pharmacy/queue?facilityId={FAC}', None, D) or {}).get('data', [])
if open_enc and not any(q['patientId'] == P_['Otieno'] for q in queue):
    call('POST', f'/v1/clinical/encounters/{open_enc["id"]}/orders', {'kind': 'MEDICATION', 'priority': 'ROUTINE', 'description': 'Paracetamol 500 mg', 'drugId': dr['Paracetamol'], 'drugName': 'Paracetamol', 'dose': '500 mg', 'route': 'ORAL', 'frequency': 'QDS', 'durationDays': 5, 'quantity': 20}, D)
print('phase 4 done')
