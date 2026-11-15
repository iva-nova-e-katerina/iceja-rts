import json, urllib.request, sys
BASE='http://127.0.0.1:8080'
H={'X-Player-Id':'wscat-test','Content-Type':'application/json'}
def req(path, method='GET', body=None):
    data=None if body is None else json.dumps(body).encode()
    r=urllib.request.Request(BASE+path,data=data,headers=H,method=method)
    with urllib.request.urlopen(r,timeout=10) as resp: return json.loads(resp.read().decode())
c=req('/api/v1/catalog'); t=next(x for x in c['templates'] if x['name']=='Scout')
cr=req('/api/v1/creatures','POST',{'genome':t['genome']})
a=req('/api/v1/armies','POST',{'name':'w','units':[{'creatureId':cr['id'],'x':0,'y':0}]})
m=req('/api/v1/matches','POST',{'armyId':a['id'],'mapId':'duel-arena','opponent':'bot'})
tk=req('/api/v1/ws/ticket','POST',{})
print(m['id'])
print(tk['ticket'])
