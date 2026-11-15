const base = 'http://127.0.0.1:8080';
const playerId = `e2e-${Date.now()}`;
const headers = { 'X-Player-Id': playerId, 'Content-Type': 'application/json' };

async function request(path, options = {}) {
  const response = await fetch(`${base}${path}`, {
    ...options,
    headers: { ...headers, ...(options.headers ?? {}) }
  });
  if (!response.ok) {
    throw new Error(`${path} -> ${response.status} ${await response.text()}`);
  }
  if (response.status === 204) return null;
  return response.json();
}

const catalog = await request('/api/v1/catalog');
const template = catalog.templates.find((item) => item.name === 'Scout');
if (!template) throw new Error('Scout template missing');

const validation = await request('/api/v1/creatures/validate', {
  method: 'POST',
  body: JSON.stringify({ genome: template.genome })
});
if (!validation.valid) throw new Error(`template invalid: ${JSON.stringify(validation.errors)}`);

const creature = await request('/api/v1/creatures', {
  method: 'POST',
  body: JSON.stringify({ genome: template.genome })
});
const army = await request('/api/v1/armies', {
  method: 'POST',
  body: JSON.stringify({ name: 'E2E swarm', units: [{ creatureId: creature.id, x: 0, y: 0 }] })
});
const match = await request('/api/v1/matches', {
  method: 'POST',
  body: JSON.stringify({ armyId: army.id, mapId: 'duel-arena', opponent: 'bot' })
});
const ticket = await request('/api/v1/ws/ticket', { method: 'POST', body: '{}' });

const result = await new Promise((resolve, reject) => {
  const ws = new WebSocket(`ws://127.0.0.1:8080/ws/matches/${match.id}?ticket=${encodeURIComponent(ticket.ticket)}`);
  let start = null;
  let snapshots = 0;
  ws.onopen = () => console.log('WS OPEN', match.id);
  ws.onclose = (event) => console.log('WS CLOSE', event.code, event.reason);
  const timer = setTimeout(() => {
    ws.close();
    if (start !== null && snapshots > 0) {
      resolve({ start, snapshots });
    } else {
      reject(new Error(`timeout: start=${start !== null} snapshots=${snapshots}`));
    }
  }, 6000);
  ws.onmessage = (event) => {
    const message = JSON.parse(event.data);
    console.log('WS MESSAGE', message.type);
    if (message.type === 'match_start') start = message.payload;
    if (message.type === 'snapshot') snapshots += 1;
  };
  ws.onerror = () => {
    clearTimeout(timer);
    reject(new Error('websocket error'));
  };
});

const replay = await request(`/api/v1/replays/${match.id}`);
if (!Array.isArray(replay.snapshots) || replay.snapshots.length === 0) {
  throw new Error('replay has no snapshots');
}

console.log(JSON.stringify({
  playerId,
  matchId: match.id,
  genomes: result.start.genomes.length,
  snapshotsReceived: result.snapshots,
  replaySnapshots: replay.snapshots.length
}, null, 2));
