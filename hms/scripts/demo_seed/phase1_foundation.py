import os, tempfile
STATE = os.environ.get('HMS_SEED_STATE', os.path.join(tempfile.gettempdir(), 'hms_seed_state.json'))
import json, urllib.request, urllib.error, datetime, sys
B=os.environ.get('HMS_API_URL','http://localhost:8100')
def call(m, path, body=None, tok=None, hdr=None, quiet=False):
    h={'content-type':'application/json'}
    if tok: h['authorization']='Bearer '+tok
    if hdr: h.update(hdr)
    r=urllib.request.Request(B+path, data=json.dumps(body).encode() if body is not None else None, headers=h, method=m)
    import time
    for attempt in range(6):
        try:
            with urllib.request.urlopen(r, timeout=60) as x:
                t=x.read().decode(); return json.loads(t) if t else {}
        except urllib.error.HTTPError as e:
            t=e.read().decode()
            if e.code == 429 and attempt < 5:
                # The login and portal endpoints rate-limit by address; wait as long as the server says and try again.
                time.sleep(int(e.headers.get('Retry-After') or 15) + 1); continue
            if not quiet: print('FAIL',m,path,e.code,t[:300])
            return None
PW='Lakeview-Demo-2026'
org=call('POST','/v1/organisations',{'organisationName':'Lakeview Community Hospital','slug':'lakeview','facility':{'name':'Lakeview Community Hospital','kephLevel':4,'ownership':'PRIVATE','county':'Kisumu'},'admin':{'fullName':'Amina Otieno','email':'admin@lakeview.test','password':PW}})
print(org)
S=call('POST','/v1/auth/login',{'email':'admin@lakeview.test','password':PW})
A=S['token']; FAC=S['facilities'][0]['id']; print(FAC)
def staff(email,name,cadre,roles,lic=None,body=None):
    return call('POST','/v1/staff',{'email':email,'fullName':name,'cadre':cadre,'licenceBody':lic,'licenceNo':('L'+str(abs(hash(email))%90000+10000)) if lic else None,'temporaryPassword':PW,'roles':roles,'facilityIds':[FAC]},A)
st={}
for k,(e,n,c,r,l) in {
 'doc':('doctor@lakeview.test','Dr. Peter Kamau','DOCTOR',['DOCTOR'],'KMPDC'),
 'doc2':('doctor2@lakeview.test','Dr. Grace Wanjiru','DOCTOR',['DOCTOR'],'KMPDC'),
 'nurse':('nurse@lakeview.test','Joyce Achieng','NURSE',['NURSE'],'NCK'),
 'pharm':('pharmacy@lakeview.test','Samuel Mutiso','PHARMACIST',['PHARMACIST'],'PPB'),
 'lab':('lab@lakeview.test','Faith Chebet','LAB_TECHNOLOGIST',['LAB_TECHNOLOGIST'],'KMLTTB'),
 'cash':('cashier@lakeview.test','Brian Odhiambo','ACCOUNTANT',['CASHIER'],None),
 'claims':('claims@lakeview.test','Mercy Njeri','ADMINISTRATIVE',['CLAIMS_OFFICER'],None),
 'rec':('records@lakeview.test','Hassan Abdi','RECORDS_OFFICER',['RECORDS_OFFICER'],None)}.items():
    r_=staff(e,n,c,r,l); st[k]=r_
    print(k, r_ and r_.get('id'))
def login(e):
    s=call('POST','/v1/auth/login',{'email':e,'password':PW}); return s['token'], s['practitionerId']
D,Did=login('doctor@lakeview.test'); D2,D2id=login('doctor2@lakeview.test')
N,Nid=login('nurse@lakeview.test'); P,Pid=login('pharmacy@lakeview.test'); L,Lid=login('lab@lakeview.test'); C,_=login('cashier@lakeview.test'); CL,_=login('claims@lakeview.test'); R,_=login('records@lakeview.test')
# clinic
sessions=[{'practitionerId':Did,'weekday':w,'startTime':'08:00','endTime':'17:00'} for w in range(1,6)]
clinic=call('POST','/v1/scheduling/clinics',{'facilityId':FAC,'name':'General outpatient','specialty':'General medicine','slotMinutes':20,'sessions':sessions},A)
clinic2=call('POST','/v1/scheduling/clinics',{'facilityId':FAC,'name':'Antenatal and child health','specialty':'Primary care','slotMinutes':20,'sessions':[{'practitionerId':D2id,'weekday':w,'startTime':'08:00','endTime':'16:00'} for w in range(1,6)]},A)
CID=clinic['id']
# patients
pats=[('Wanjiku','Mwangi','FEMALE','1989-03-14','0712345001','Nairobi','12345601'),
('Otieno','Odhiambo','MALE','1975-11-02','0722345002','Kisumu','12345602'),
('Amina','Hassan','FEMALE','1996-07-21','0733345003','Mombasa','12345603'),
('Kipchoge','Rotich','MALE','2008-01-30','0710345004','Uasin Gishu',None),
('Mary','Atieno','FEMALE','1962-05-09','0701345005','Siaya','12345605'),
('Juma','Mutua','MALE','1983-09-17','0723345006','Machakos','12345606'),
('Esther','Njoroge','FEMALE','2019-12-03','0734345007','Kiambu',None),
('Daniel','Kiprono','MALE','1951-04-25','0745345008','Nandi','12345608'),
('Zawadi','Ali','FEMALE','1992-08-11','0756345009','Kilifi','12345609'),
('Collins','Omondi','MALE','1999-02-05','0767345010','Kisumu','12345610'),
('Rehema','Chepkoech','FEMALE','1978-10-19','0778345011','Kericho','12345611'),
('Peter','Waweru','MALE','1969-06-28','0789345012','Nyeri','12345612')]
P_={}
for g,f,sx,bd,ph,co,nid in pats:
    r_=call('POST','/v1/patients',{'facilityId':FAC,'demographics':{'givenName':g,'familyName':f,'sex':sx,'birthDate':bd,'phone':ph,'county':co},'identifiers':([{'system':'NATIONAL_ID','value':nid}] if nid else []),'contacts':[],'confirmNotDuplicate':True},R)
    P_[g]=r_['id'] if r_ else None
print(len(P_),'patients')
# formulary
drugs={}
for gn,st_,fm,un,pr,ctl in [('Amoxicillin','500 mg','Capsule','capsule',12,False),('Paracetamol','500 mg','Tablet','tablet',2,False),('Artemether-lumefantrine','20/120 mg','Tablet','tablet',45,False),('Metformin','500 mg','Tablet','tablet',5,False),('Amlodipine','5 mg','Tablet','tablet',6,False),('Oral rehydration salts','','Sachet','sachet',25,False),('Morphine sulfate','10 mg/mL','Injection','ampoule',150,True),('Salbutamol','100 mcg','Inhaler','inhaler',320,False)]:
    r_=call('POST','/v1/pharmacy/drugs',{'genericName':gn,'strength':st_ or None,'form':fm,'unit':un,'controlled':ctl,'unitPrice':pr,'reorderLevel':50,'active':True},P)
    drugs[gn]=r_['id'] if r_ else None
    call('POST','/v1/pharmacy/stock/receipts',{'facilityId':FAC,'drugId':drugs[gn],'batchNo':'B'+gn[:3].upper()+'26','expiryDate':'2028-03-31' if gn!='Oral rehydration salts' else '2026-12-15','quantity':(40 if ctl else 600),'unitCost':pr*0.6,'supplier':'Kisumu Medical Supplies'},P)
# a near-expiry second batch for amoxicillin
call('POST','/v1/pharmacy/stock/receipts',{'facilityId':FAC,'drugId':drugs['Amoxicillin'],'batchNo':'BAMO25','expiryDate':'2026-11-30','quantity':120,'unitCost':7,'supplier':'Kisumu Medical Supplies'},P)
# lab catalogue
tests={}
for code,name,loinc,spec,unit,lo,hi,cl,ch,pr in [('HB','Haemoglobin','718-7','Blood','g/dL',12,17,7,None,300),('GLU','Fasting glucose','1558-6','Blood','mmol/L',3.9,5.6,2.5,20,200),('MP','Malaria parasites','32700-7','Blood',None,None,None,None,None,250),('CREAT','Creatinine','2160-0','Blood','umol/L',45,110,None,None,400),('WBC','White cell count','6690-2','Blood','x10^9/L',4,11,None,None,300)]:
    r_=call('POST','/v1/lab/tests',{'code':code,'name':name,'loincCode':loinc,'specimenType':spec,'resultType':'TEXT' if code=='MP' else 'NUMERIC','unit':unit,'refLow':lo,'refHigh':hi,'criticalLow':cl,'criticalHigh':ch,'price':pr,'active':True},A)
    tests[code]=r_['id'] if r_ else None
# charges
chg={}
for code,name,cat,price in [('CONS-OPD','Outpatient consultation','CONSULTATION',500),('CONS-ED','Emergency consultation','CONSULTATION',1500),('BED-GEN','General ward bed, per day','BED',2500),('PROC-DRESS','Wound dressing','PROCEDURE',600)]:
    r_=call('POST','/v1/billing/charges',{'code':code,'name':name,'category':cat,'price':price,'active':True},A); chg[code]=r_['id'] if r_ else None
# wards
ward=call('POST','/v1/inpatient/wards',{'facilityId':FAC,'name':'Male medical ward','kind':'MEDICAL','bedLabels':['M1','M2','M3','M4','M5','M6']},A)
ward2=call('POST','/v1/inpatient/wards',{'facilityId':FAC,'name':'Female general ward','kind':'GENERAL','bedLabels':['F1','F2','F3','F4','F5']},A)
ward3=call('POST','/v1/inpatient/wards',{'facilityId':FAC,'name':'Paediatric ward','kind':'PAEDIATRIC','bedLabels':['C1','C2','C3','C4']},A)
json.dump({'FAC':FAC,'P':P_,'drugs':drugs,'tests':tests,'chg':chg,'clinic':CID,'tok':dict(A=A,D=D,D2=D2,N=N,P=P,L=L,C=C,CL=CL,R=R),'ids':dict(Did=Did,D2id=D2id,Pid=Pid,Lid=Lid),'wards':[ward and ward['id'],ward2 and ward2['id'],ward3 and ward3['id']]},open(STATE,'w'))
print('phase1 done')
