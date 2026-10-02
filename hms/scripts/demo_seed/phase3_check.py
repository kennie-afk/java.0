import os, tempfile
STATE = os.environ.get('HMS_SEED_STATE', os.path.join(tempfile.gettempdir(), 'hms_seed_state.json'))
import json
exec(open('seed_hms.py').read().split("PW='Lakeview-Demo-2026'")[0])
s=json.load(open(STATE)); T=s['tok']; FAC=s['FAC']; P_=s['P']; CID=s['clinic']
A,Ph,C,R,CL=[T[k] for k in ['A','P','C','R','CL']]
q=call('GET','/v1/pharmacy/queue?facilityId='+FAC,None,Ph)
print(json.dumps(q)[:600])
rows=q if isinstance(q,list) else q.get('items',[])
for r in rows:
    print(r)
    out=call('POST','/v1/pharmacy/dispense',{'orderId':r['orderId'],'quantity':r['quantity'] if 'quantity' in r else r.get('remaining',1),'drugId':r['drugId']},Ph)
    print(out and out.get('quantity'))
for nm,cat,why in [('Esther','PRIORITY','Diarrhoea and vomiting, one day'),('Zawadi','ROUTINE','Booked review'),('Collins','EMERGENCY','Road traffic injury, laceration to forearm'),('Rehema','ROUTINE','Back pain')]:
    call('POST','/v1/scheduling/walk-ins',{'clinicId':CID,'patientId':P_[nm],'priority':cat,'reason':why},R)
inv=call('GET','/v1/billing/invoices?facilityId='+FAC,None,C)
for i in (inv if isinstance(inv,list) else inv.get('items',[])):
    print(i['invoiceNumber'],i['status'],i['payerType'],i['total'],i['amountPaid'])
    if i['payerType']=='CASH' and i['status']=='ISSUED':
        call('POST',f"/v1/billing/invoices/{i['id']}/payments",{'method':'CASH','amount':i['total'],'idempotencyKey':'seed-'+i['invoiceNumber']},C)
