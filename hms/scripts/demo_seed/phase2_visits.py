import os, tempfile
STATE = os.environ.get('HMS_SEED_STATE', os.path.join(tempfile.gettempdir(), 'hms_seed_state.json'))
import json,sys
sys.argv=['x']
exec(open(__import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)),'phase1_foundation.py')).read().split("PW='Lakeview-Demo-2026'")[0])
s=json.load(open(STATE)); T=s['tok']; FAC=s['FAC']; P_=s['P']; dr=s['drugs']; ts=s['tests']; ch=s['chg']; CID=s['clinic']
A,D,D2,N,Ph,L,C,CL,R=[T[k] for k in ['A','D','D2','N','P','L','C','CL','R']]
Did=s['ids']['Did']
def visit(name, typ, cat, complaint, vit, note, dx, meds, labs, payer='CASH', close=True, walk=True, doc=D, appt=True):
    pid=P_[name]
    ap=None
    if appt:
        ap=call('POST','/v1/scheduling/walk-ins',{'clinicId':CID,'patientId':pid,'priority':cat,'reason':complaint},R)
    enc=call('POST','/v1/clinical/encounters',{'facilityId':FAC,'patientId':pid,'type':typ,'appointmentId':ap['id'] if ap else None,'chiefComplaint':complaint},doc)
    if not enc: return None
    e=enc['id']
    call('POST',f'/v1/clinical/encounters/{e}/triage',{'category':cat,'chiefComplaint':complaint},N)
    call('POST',f'/v1/clinical/encounters/{e}/vitals',vit,N)
    if note: call('POST',f'/v1/clinical/encounters/{e}/notes',{'kind':'SOAP','body':note},doc)
    for code,title,kind,cert in dx: call('POST',f'/v1/clinical/encounters/{e}/diagnoses',{'icd11Code':code,'title':title,'kind':kind,'certainty':cert},doc)
    for dn,dose,route,freq,days,qty in meds:
        call('POST',f'/v1/clinical/encounters/{e}/orders',{'kind':'MEDICATION','priority':'ROUTINE','description':f'{dn} {dose}','drugId':dr[dn],'drugName':dn,'dose':dose,'route':route,'frequency':freq,'durationDays':days,'quantity':qty},doc)
    labord=None
    if labs:
        labord=call('POST','/v1/lab/orders',{'facilityId':FAC,'patientId':pid,'encounterId':e,'priority':'ROUTINE','clinicalInfo':complaint,'testIds':[ts[t] for t in labs]},doc)
    return e,labord,pid
def labflow(lo, vals, validate=True):
    if not lo: return
    full=call('GET',f"/v1/lab/orders/{lo['id']}",None,L)
    items=full['items']
    call('POST',f"/v1/lab/orders/{lo['id']}/collect",{'itemIds':[i['id'] for i in items]},L)
    for it in items:
        v=vals.get(it['testCode'])
        if v is None: continue
        call('POST',f"/v1/lab/items/{it['id']}/result",({'text':v} if isinstance(v,str) else {'numeric':v}),L)
        if validate: call('POST',f"/v1/lab/items/{it['id']}/validate",None,A)
def dispense_all(pid):
    q=call('GET','/v1/pharmacy/queue?facilityId='+FAC,None,Ph) or {}
    for r in q.get('data',[]):
        if r['patientId']==pid:
            call('POST','/v1/pharmacy/dispense',{'orderId':r['orderId'],'quantity':r['quantity']-r['dispensed'],'drugId':r['drugId']},Ph)
def bill(pid, e, payer='CASH', pay=True, issue=True, consult='CONS-OPD'):
    inv=call('POST','/v1/billing/invoices',{'facilityId':FAC,'patientId':pid,'encounterId':e,'payerType':payer},C)
    if not inv: return None
    i=inv['id']
    call('POST',f'/v1/billing/invoices/{i}/lines',{'chargeId':ch[consult],'quantity':1},C)
    call('POST',f'/v1/billing/invoices/{i}/import-encounter',None,C)
    if issue: call('POST',f'/v1/billing/invoices/{i}/issue',None,C)
    return i
res={}
v=visit('Wanjiku','OPD','ROUTINE','Fever and headache for three days',{'tempC':38.6,'pulse':96,'respRate':20,'systolic':118,'diastolic':76,'spo2':97,'weightKg':64},'S: three days of fever, chills, headache. O: febrile, no neck stiffness. A: suspected malaria. P: confirm with smear, treat if positive.',[('1F40','Malaria, Plasmodium falciparum','PRIMARY','CONFIRMED')],[('Artemether-lumefantrine','4 tablets twice daily','ORAL','BD',3,24),('Paracetamol','1 g','ORAL','TDS',3,18)],['MP','HB'])
labflow(v[1],{'MP':'Plasmodium falciparum trophozoites seen','HB':11.2}); dispense_all(v[2]); res['w']=bill(v[2],v[0],'SHA')
v=visit('Otieno','OPD','PRIORITY','Chest pain on exertion, known hypertension',{'tempC':36.8,'pulse':104,'respRate':22,'systolic':168,'diastolic':102,'spo2':95,'weightKg':88,'heightCm':176},'S: exertional chest tightness two weeks. O: BP 168/102. A: uncontrolled hypertension. P: start amlodipine, review in two weeks.',[('BA00','Essential hypertension','PRIMARY','CONFIRMED')],[('Amlodipine','5 mg','ORAL','OD',30,30)],['GLU','CREAT'])
labflow(v[1],{'GLU':7.4,'CREAT':96}); dispense_all(v[2]); res['o']=bill(v[2],v[0],'SHA')
v=visit('Amina','OPD','ROUTINE','Cough and sore throat',{'tempC':37.9,'pulse':88,'respRate':18,'systolic':110,'diastolic':70,'spo2':98,'weightKg':58},'S: productive cough five days. O: pharyngeal erythema, chest clear. A: upper respiratory tract infection. P: amoxicillin, fluids.',[('CA07','Acute upper respiratory infection','PRIMARY','PROVISIONAL')],[('Amoxicillin','500 mg','ORAL','TDS',5,15)],[])
dispense_all(v[2]); res['a']=bill(v[2],v[0],'CASH')
v=visit('Kipchoge','OPD','ROUTINE','Wheeze at night',{'tempC':36.7,'pulse':92,'respRate':24,'systolic':105,'diastolic':65,'spo2':96,'weightKg':41},'S: nocturnal wheeze, exercise-triggered. A: asthma. P: salbutamol as needed.',[('CA23','Asthma','PRIMARY','CONFIRMED')],[('Salbutamol','2 puffs','INHALED','PRN',30,1)],[])
dispense_all(v[2]); res['k']=bill(v[2],v[0],'CASH')
v=visit('Mary','OPD','ROUTINE','Thirst and frequent urination',{'tempC':36.6,'pulse':80,'respRate':16,'systolic':142,'diastolic':88,'spo2':97,'weightKg':79,'heightCm':158,'glucoseMmol':14.2},'S: polyuria, polydipsia. A: type 2 diabetes mellitus. P: metformin, diet, review with labs.',[('5A11','Type 2 diabetes mellitus','PRIMARY','CONFIRMED')],[('Metformin','500 mg','ORAL','BD',30,60)],['GLU','HB'])
labflow(v[1],{'GLU':2.1,'HB':13.4}); dispense_all(v[2]); res['m']=bill(v[2],v[0],'SHA')
# unvalidated lab: still waiting
v=visit('Juma','OPD','ROUTINE','Fatigue',{'tempC':36.9,'pulse':78,'respRate':16,'systolic':122,'diastolic':80,'spo2':98,'weightKg':72},'S: fatigue one month.',[],[],['HB','WBC'])
labflow(v[1],{'HB':9.1,'WBC':6.4},validate=False)
# queue only (open)
for nm,cat,why in [('Esther','PRIORITY','Diarrhoea and vomiting, one day'),('Zawadi','ROUTINE','Booked review'),('Collins','EMERGENCY','Road traffic injury, laceration to forearm'),('Rehema','ROUTINE','Back pain')]:
    call('POST','/v1/scheduling/walk-ins',{'clinicId':CID,'patientId':P_[nm],'priority':cat,'reason':why},R)
# inpatient: ED encounter -> admission
w=call('GET','/v1/inpatient/wards?facilityId='+FAC,None,A)
wl=w if isinstance(w,list) else w.get('items',[])
print([ (x['name'],x['id']) for x in wl])
def bed(wid,label):
    bs=call('GET',f'/v1/inpatient/wards/{wid}/beds',None,A); return [b for b in bs if b['label']==label][0]['id']
wm=[x for x in wl if x['name'].startswith('Male')][0]['id']; wf=[x for x in wl if x['name'].startswith('Female')][0]['id']; wp=[x for x in wl if x['name'].startswith('Paed')][0]['id']
for pn,wid,lb,dx in [('Daniel',wm,'M2','Community-acquired pneumonia'),('Peter',wm,'M4','Decompensated heart failure'),('Esther',wp,'C1','Severe dehydration')]:
    a=call('POST','/v1/inpatient/admissions',{'facilityId':FAC,'patientId':P_[pn],'bedId':bed(wid,lb),'admittingDiagnosis':dx},D)
    print(pn, a and a.get('admissionNumber'))
call('PUT','/v1/inpatient/beds/'+bed(wf,'F3')+'/status',{'status':'CLEANING'},N)
# pay some invoices
for k in ['a','k']:
    i=res.get(k)
    if i:
        inv=call('GET',f'/v1/billing/invoices/{i}',None,C)
        call('POST',f'/v1/billing/invoices/{i}/payments',{'method':'CASH','amount':inv['total'],'idempotencyKey':'seed-'+i},C)
# claims
for k in ['w','o','m']:
    if res.get(k): 
        c=call('POST','/v1/claims',{'invoiceId':res[k]},CL); print('claim',k,c and (c['claimNumber'],c['status']))
print(res)
