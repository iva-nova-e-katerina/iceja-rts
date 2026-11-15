import asyncio, json, urllib.request

BASE = 'http://127.0.0.1:8080'
PLAYER = 'py-e2e'
HEADERS = {'X-Player-Id': PLAYER, 'Content-Type': 'application/json'}

def req(path, method='GET', body=None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(BASE + path, data=data, headers=HEADERS, method=method)
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read().decode())

catalog = req('/api/v1/catalog')
template = next(item for item in catalog['templates'] if item['name'] == 'Scout')
creature = req('/api/v1/creatures', 'POST', {'genome': template['genome']})
army = req('/api/v1/armies', 'POST', {'name': 'Py swarm', 'units': [{'creatureId': creature['id'], 'x': 0, 'y': 0}]})
match = req('/api/v1/matches', 'POST', {'armyId': army['id'], 'mapId': 'duel-arena', 'opponent': 'bot'})
ticket = req('/api/v1/ws/ticket', 'POST', {})
print('match', match['id'])

async def main():
    import websockets
    uri = f"ws://127.0.0.1:8080/ws/matches/{match['id']}?ticket={ticket['ticket']}"
    try:
        async with websockets.connect(uri) as ws:
            print('connected')
            for _ in range(4):
                message = await asyncio.wait_for(ws.recv(), timeout=3)
                parsed = json.loads(message)
                print('message', parsed['type'])
                if parsed['type'] == 'match_start':
                    print('genomes', len(parsed['payload']['genomes']))
    except Exception as exc:
        print('error', type(exc).__name__, exc)

asyncio.run(main())
