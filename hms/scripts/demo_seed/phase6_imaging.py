"""Imaging sample data: a procedure catalogue, two radiology staff, and orders at each stage (ordered, performed, reported, signed, critical)."""
import json, os, tempfile
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'phase1_foundation.py')).read().split("PW='Lakeview-Demo-2026'")[0])
STATE = os.environ.get('HMS_SEED_STATE', os.path.join(tempfile.gettempdir(), 'hms_seed_state.json'))
s = json.load(open(STATE)); FAC = s['FAC']
PW = 'Lakeview-Demo-2026'
login = lambda e: call('POST', '/v1/auth/login', {'email': e, 'password': PW})['token']
A = login('admin@lakeview.test')
def staff(email, name, cadre, roles, lic=None):
    return call('POST', '/v1/staff', {'email': email, 'fullName': name, 'cadre': cadre, 'licenceBody': lic, 'licenceNo': ('L' + str(abs(hash(email)) % 90000 + 10000)) if lic else None,
                                       'temporaryPassword': PW, 'roles': roles, 'facilityIds': [FAC]}, A)
staff('radiographer@lakeview.test', 'Dennis Kiprop', 'RADIOGRAPHER', ['RADIOGRAPHER'], 'OTHER')
staff('radiologist@lakeview.test', 'Dr. Naomi Wekesa', 'DOCTOR', ['RADIOLOGIST'], 'KMPDC')
staff('radiologist2@lakeview.test', 'Dr. Ibrahim Noor', 'DOCTOR', ['RADIOLOGIST'], 'KMPDC')
D = login('doctor@lakeview.test'); RG = login('radiographer@lakeview.test'); R1 = login('radiologist@lakeview.test'); R2 = login('radiologist2@lakeview.test')

proc = {}
for code, name, mod, region, price in [('CXR', 'Chest X-ray, PA', 'XR', 'Chest', 1200), ('XR-LS', 'Lumbar spine X-ray', 'XR', 'Spine', 1800), ('USG-AB', 'Ultrasound, abdomen', 'US', 'Abdomen', 2500),
                                       ('USG-OB', 'Ultrasound, obstetric', 'US', 'Pelvis', 2000), ('CT-H', 'CT head, plain', 'CT', 'Head', 9500)]:
    r = call('POST', '/v1/imaging/procedures', {'code': code, 'name': name, 'modality': mod, 'bodyRegion': region, 'price': price}, R1)
    proc[code] = r['id'] if r else None

patients = (call('GET', '/v1/patients?limit=8', None, D) or {}).get('data', [])
def order(i, code, priority, info):
    if i >= len(patients) or not proc.get(code):
        return None
    r = call('POST', '/v1/imaging/orders', {'facilityId': FAC, 'patientId': patients[i]['id'], 'procedureId': proc[code], 'priority': priority, 'clinicalInfo': info}, D)
    return r['id'] if r else None
def perform(o):
    call('POST', f'/v1/imaging/orders/{o}/perform', {'techniqueNote': 'Standard views'}, RG)

order(0, 'USG-AB', 'ROUTINE', 'Epigastric pain, 2 weeks')                       # ordered
o = order(1, 'XR-LS', 'ROUTINE', 'Low back pain after lifting')                 # performed, no report yet
if o: perform(o)
o = order(2, 'CXR', 'URGENT', 'Productive cough, night sweats')                 # reported, awaiting signature
if o:
    perform(o); call('POST', f'/v1/imaging/orders/{o}/report', {'findings': 'Right upper zone cavitating opacity. No effusion. Heart size normal.', 'impression': 'Cavitating right upper zone lesion, in keeping with pulmonary tuberculosis; sputum correlation advised.'}, R1)
o = order(3, 'CXR', 'ROUTINE', 'Pre-employment screening')                      # signed, normal
if o:
    perform(o); call('POST', f'/v1/imaging/orders/{o}/report', {'findings': 'Lungs clear. Normal cardiac silhouette.', 'impression': 'Normal chest radiograph.'}, R1); call('POST', f'/v1/imaging/orders/{o}/sign', {}, R2)
o = order(4, 'CT-H', 'STAT', 'Head injury, falling GCS')                         # critical, signed, not acknowledged
if o:
    perform(o); call('POST', f'/v1/imaging/orders/{o}/report', {'findings': 'Crescentic hyperdense extra-axial collection on the left with 8 mm midline shift.', 'impression': 'Acute left subdural haematoma with mass effect.', 'critical': True, 'criticalNote': 'Acute subdural haematoma, midline shift'}, R1)
    call('POST', f'/v1/imaging/orders/{o}/sign', {}, R2)

# Attach images to two studies. They are generated test patterns, labelled as such: there is no real scan in the demo data.
import struct, zlib, uuid
def test_pattern(w, h, phase):
    rows = b''.join(b'\x00' + b''.join(bytes([(x * 255 // w + phase) % 256, (y * 255 // h) % 256, 90 + phase % 80]) for x in range(w)) for y in range(h))
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b'')
def attach(order_id, tok, phase):
    boundary = uuid.uuid4().hex
    parts = (f'--{boundary}\r\nContent-Disposition: form-data; name="caption"\r\n\r\nSynthetic test pattern (demo data, not a patient scan)\r\n').encode()
    parts += (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="demo-{phase}.png"\r\nContent-Type: image/png\r\n\r\n').encode() + test_pattern(640, 480, phase) + f'\r\n--{boundary}--\r\n'.encode()
    req = urllib.request.Request(B + f'/v1/imaging/orders/{order_id}/attachments', data=parts, method='POST', headers={'authorization': 'Bearer ' + tok, 'content-type': 'multipart/form-data; boundary=' + boundary})
    try:
        urllib.request.urlopen(req, timeout=60).read()
    except urllib.error.HTTPError as e:
        print('FAIL attach', e.code, e.read().decode()[:200])
listing = (call('GET', '/v1/imaging/orders?limit=20', None, R1) or {}).get('data', [])
for number, phase in (('IMG-000002', 20), ('IMG-000003', 90)):
    hit = next((x for x in listing if x.get('orderNumber') == number), None)
    if hit:
        attach(hit['id'], RG, phase)
print('phase 6 imaging done')
