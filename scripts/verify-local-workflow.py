"""Exercise only the loopback development stack; every address ends in .test.
Creates a synthetic tenant, messages, and a private text attachment. No production access.
"""
import json, uuid, time, hashlib, urllib.request, urllib.error, urllib.parse
from pathlib import Path
from email import policy
from email.parser import BytesParser

BASE = 'http://127.0.0.1:8080'
MAILBOX = 'http://127.0.0.1:8025'
STATE = Path(__file__).resolve().parents[1] / 'backend/target/local-workflow-session.json'
TOKEN = None

def request(path, payload=None, method=None, headers=None, base=BASE, raw=False, authenticated=True):
    h = dict(headers or {})
    if TOKEN and authenticated: h['Authorization'] = 'Bearer ' + TOKEN
    data = payload
    if payload is not None and not isinstance(payload, bytes):
        data = json.dumps(payload).encode(); h['Content-Type'] = 'application/json'
    response = urllib.request.urlopen(urllib.request.Request(base + path, data=data, headers=h, method=method), timeout=15)
    result = response.read()
    if raw: return result
    parsed = json.loads(result)
    return parsed.get('data', parsed)

def expect_status(status, path, payload=None, **kwargs):
    try: request(path, payload, **kwargs)
    except urllib.error.HTTPError as e:
        assert e.code == status, (path, e.code, e.read().decode()); return
    raise AssertionError('Expected HTTP ' + str(status) + ' for ' + path)

def wait_for(description, check, seconds=45):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        value=check()
        if value: print('PASS',description,flush=True); return value
        time.sleep(1)
    raise AssertionError('Timed out: '+description)

def main():
    global TOKEN
    health=request('/actuator/health', authenticated=False)
    assert health['status']=='UP',health
    print('PASS backend, database, RabbitMQ, Redis and Mailpit health',flush=True)
    suffix=uuid.uuid4().hex[:10]
    login={'email':f'local-review-{suffix}@example.test','password':'LocalReview-'+uuid.uuid4().hex}
    session=request('/api/v1/auth/register',{'organization':'Local workflow '+suffix,**login})
    TOKEN=session['token']
    STATE.parent.mkdir(exist_ok=True,parents=True)
    STATE.write_text(json.dumps({'login':login,'session':session}),encoding='utf-8')
    assert request('/api/v1/auth/me')['email']==login['email']
    expect_status(401,'/api/v1/auth/me',authenticated=False)
    expect_status(401,'/api/v1/auth/login',{'email':login['email'],'password':'Wrong-test-password'})
    print('PASS registration, sign-in/session and authentication rejection',flush=True)
    sender='sender@'+session['user']['tenantSlug']+'.texto.test'
    content=b'Texto local attachment verification\r\nNo real recipient or private customer data.\r\n'
    boundary='LocalBoundary'+suffix
    multipart=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="local-receipt.txt"\r\nContent-Type: text/plain\r\n\r\n'.encode()+content+f'\r\n--{boundary}--\r\n'.encode())
    doc=request('/api/v1/attachments',multipart,headers={'Content-Type':'multipart/form-data; boundary='+boundary})
    assert doc['checksumSha256']==hashlib.sha256(content).hexdigest()
    assert request('/api/v1/attachments/'+doc['id']+'/download',raw=True)==content
    expect_status(401,'/api/v1/attachments/'+doc['id']+'/download',authenticated=False)
    print('PASS private attachment upload, checksum and authenticated download',flush=True)
    ids=[]
    for name,company in [('Ada','Acme'),('Lin','Acme'),('Sam','Other')]:
        c=request('/api/v1/contacts',{'email':name.lower()+'-'+suffix+'@example.test','firstName':name,'company':company,'properties':'{"city":"Mumbai"}'})
        ids.append(c['id'])
    definition={'operator':'AND','conditions':[{'field':'company','operator':'EQUALS','value':'Acme'}]}
    segment=request('/api/v1/segments',{'name':'Acme '+suffix,'definition':json.dumps(definition)})
    draft=request('/api/v1/campaigns/drafts',{'name':'Local workflow '+suffix,'sender':sender,'subject':'Local workflow '+suffix+' {{first_name}}','htmlBody':'<p>Hello {{first_name}}, your receipt is attached.</p>','textBody':'Hello {{first_name}}, your receipt is attached.','attachmentIds':[doc['id']]})
    campaign=draft['id']
    audience=request('/api/v1/campaigns/'+campaign+'/audience',{'segmentId':segment['id']})
    assert audience['added']==2,audience
    assert request('/api/v1/campaigns/'+campaign+'/audience',{'segmentId':segment['id']})['added']==0
    preview=request('/api/v1/campaigns/'+campaign+'/preview')
    assert 'Hello Ada' in preview['html'] or 'Hello Lin' in preview['html'],preview
    print('PASS segment matching, draft, audience snapshot/deduplication and personalization',flush=True)
    test=request('/api/v1/campaigns/'+campaign+'/test',{'to':'preview-'+suffix+'@example.test'})
    test_detail=wait_for('preview test traverses real queue and SMTP',lambda: (lambda m: m if m['status']=='DELIVERED' else None)(request('/api/v1/emails/'+test['id'])))
    assert test_detail['attemptCount']==1
    request('/api/v1/campaigns/'+campaign+'/send',{},headers={'Idempotency-Key':'local-'+suffix})
    report=wait_for('campaign dispatch accepts both recipients',lambda: (lambda r:r if r['accepted']==2 else None)(request('/api/v1/campaigns/'+campaign+'/report')))
    def captured():
        result=request('/api/v1/search?query='+urllib.parse.quote('subject:"Local workflow '+suffix+'"'),base=MAILBOX,authenticated=False)
        return result['messages'] if len(result.get('messages',[]))>=3 else None
    captured_messages=wait_for('three emails captured by local Mailpit',captured)
    campaign_messages=[]
    for message in captured_messages:
        raw=request('/api/v1/message/'+message['ID']+'/raw',base=MAILBOX,authenticated=False,raw=True)
        parsed=BytesParser(policy=policy.default).parsebytes(raw)
        files=list(parsed.iter_attachments())
        assert len(files)==1 and files[0].get_payload(decode=True)==content
        assert files[0].get_filename()=='local-receipt.txt'
        assert parsed['DKIM-Signature']
        if parsed['List-Unsubscribe']:campaign_messages.append(parsed)
    assert len(campaign_messages)==2
    print('PASS captured MIME attachments preserve filename/bytes; DKIM and unsubscribe headers present',flush=True)
    url=str(campaign_messages[0]['List-Unsubscribe']).strip('<> ')
    assert url.startswith('http://localhost:8080/api/v1/public/unsubscribe/'),url
    public_path=urllib.parse.urlsplit(url).path
    request(public_path,raw=True,authenticated=False)  # GET must not unsubscribe.
    assert request('/api/v1/contacts/'+ids[0])['subscriptionStatus']=='SUBSCRIBED'
    for _ in range(2):request(public_path,b'List-Unsubscribe=One-Click',method='POST',headers={'Content-Type':'application/x-www-form-urlencoded'},raw=True,authenticated=False)
    contacts=request('/api/v1/contacts?size=20')['items']
    assert sum(c['subscriptionStatus']=='UNSUBSCRIBED' for c in contacts)==1
    print('PASS local unsubscribe GET confirmation, idempotent POST and contact suppression',flush=True)
    expect_status(400,'/api/v1/emails',{'from':sender,'to':['bad-email'],'subject':'Invalid '+suffix,'text':'Validation check'})
    expect_status(403,'/api/v1/emails',{'from':'unknown@example.test','to':['test@example.test'],'subject':'Invalid sender '+suffix,'text':'Validation check'})
    saved=json.loads(STATE.read_text(encoding='utf-8'));saved.update({'campaignId':campaign,'attachmentId':doc['id'],'mailpitMessages':len(captured_messages),'suffix':suffix});STATE.write_text(json.dumps(saved),encoding='utf-8')
    print('PASS invalid recipients and unverified sender rejection',flush=True)
    print('PASS complete local workflow; synthetic login/session retained only in ignored backend/target/local-workflow-session.json',flush=True)

if __name__=='__main__':main()
