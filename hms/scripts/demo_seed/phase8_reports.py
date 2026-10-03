"""A sample custom report so the Custom reports screen is not empty."""
import json, os, tempfile
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'phase1_foundation.py')).read().split("PW='Lakeview-Demo-2026'")[0])
PW = 'Lakeview-Demo-2026'
A = call('POST', '/v1/auth/login', {'email': 'admin@lakeview.test', 'password': PW})['token']
el = lambda code, label, measure, disagg='NONE', filt=None: dict(code=code, label=label, measure=measure, disaggregation=disagg, **({'filter': filt} if filt else {}))
call('POST', '/v1/report-definitions', {'code': 'CLINICAL-SUMMARY', 'name': 'Clinical summary', 'description': 'Outpatient visits, admissions, laboratory and imaging output for the period.', 'elements': [
    el('OPD', 'Outpatient and emergency visits', 'OPD_VISITS'), el('OPD-SEX', 'Visits by sex', 'OPD_VISITS', 'SEX'), el('OPD-AGE', 'Visits by age', 'OPD_VISITS', 'AGE_BAND'),
    el('ADM', 'Admissions', 'ADMISSIONS'), el('DIS', 'Discharges', 'DISCHARGES'), el('LAB', 'Laboratory tests validated', 'LAB_TESTS_VALIDATED'),
    el('IMG', 'Imaging studies signed, by modality', 'IMAGING_STUDIES_SIGNED', 'MODALITY'), el('TB', 'New TB enrolments', 'PROGRAMME_ENROLMENTS', 'NONE', 'TB')]}, A)
FAC = json.load(open(os.environ.get('HMS_SEED_STATE', os.path.join(tempfile.gettempdir(), 'hms_seed_state.json'))))['FAC']
call('POST', '/v1/staff', {'email': 'fhir@lakeview.test', 'fullName': 'Integration account (FHIR)', 'cadre': 'ADMINISTRATIVE', 'temporaryPassword': PW, 'roles': ['FHIR_CLIENT'], 'facilityIds': [FAC]}, A)
print('phase 8 reports done')
