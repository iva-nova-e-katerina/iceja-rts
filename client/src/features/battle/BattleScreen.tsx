import { useEffect, useRef, useState } from "react";
import { getWsTicket, matchWebSocketUrl } from "../../api";
import type {
  Catalog,
  EventBatchPayload,
  GenomeSummary,
  MatchEndPayload,
  MatchResult,
  MatchStartPayload,
  SimEvent,
  Snapshot
} from "../../types";
import Battlefield from "./Battlefield";

interface BattleScreenProps {
  catalog: Catalog;
  matchId: string;
  onExit: () => void;
}

export default function BattleScreen({ catalog, matchId, onExit }: BattleScreenProps) {
  const [start, setStart] = useState<MatchStartPayload | null>(null);
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [events, setEvents] = useState<SimEvent[]>([]);
  const [result, setResult] = useState<MatchResult | null>(null);
  const [status, setStatus] = useState("CONNECTING");
  const [error, setError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const resultRef = useRef<MatchResult | null>(null);
  const genomesRef = useRef<GenomeSummary[]>([]);

  useEffect(() => {
    let disposed = false;
    let socket: WebSocket | null = null;
    let reconnectTimer: number | null = null;
    let attempts = 0;

    const connect = async () => {
      try {
        const ticket = await getWsTicket();
        if (disposed) {
          return;
        }
        socket = new WebSocket(matchWebSocketUrl(matchId, ticket.ticket));
        socket.onopen = () => {
          attempts = 0;
          setStatus("LIVE");
        };
        socket.onmessage = (messageEvent) => {
          const message = JSON.parse(messageEvent.data as string) as {
            type: string;
            payload?: unknown;
          };
          if (message.type === "match_start") {
            const payload = message.payload as MatchStartPayload;
            genomesRef.current = payload.genomes;
            setStart(payload);
            setSnapshot(payload.snapshot);
            setStatus("LIVE");
            setError(null);
          } else if (message.type === "snapshot") {
            setSnapshot(message.payload as Snapshot);
          } else if (message.type === "event_batch") {
            const payload = message.payload as EventBatchPayload;
            setEvents((current) => [...current, ...payload.events].slice(-300));
          } else if (message.type === "match_end") {
            const payload = message.payload as MatchEndPayload;
            resultRef.current = payload.result;
            setResult(payload.result);
            setStatus("FINISHED");
          } else if (message.type === "error") {
            const payload = message.payload as { message?: string };
            setError(payload.message ?? "Server error");
          }
        };
        socket.onclose = () => {
          if (disposed || resultRef.current !== null) {
            return;
          }
          if (attempts < 3) {
            attempts += 1;
            setStatus("RECONNECTING");
            reconnectTimer = window.setTimeout(() => void connect(), 800 * attempts);
          } else {
            setStatus("DISCONNECTED");
          }
        };
        socket.onerror = () => {
          setError("WebSocket connection failed");
        };
      } catch (reason: unknown) {
        if (!disposed) {
          setError(reason instanceof Error ? reason.message : "Cannot connect to match");
          setStatus("DISCONNECTED");
        }
      }
    };

    void connect();

    return () => {
      disposed = true;
      if (reconnectTimer !== null) {
        window.clearTimeout(reconnectTimer);
      }
      socket?.close();
    };
  }, [matchId]);

  const map = catalog.maps.find((item) => item.id === start?.mapId) ?? catalog.maps[0];
  const players = start?.players ?? [];
  const genomes = start?.genomes ?? [];
  const hiveA = snapshot?.hives.find((hive) => hive.owner === 0);
  const hiveB = snapshot?.hives.find((hive) => hive.owner === 1);
  const countA = snapshot?.entities.filter((entity) => entity.owner === 0).length ?? 0;
  const countB = snapshot?.entities.filter((entity) => entity.owner === 1).length ?? 0;
  const selectedEntity = snapshot?.entities.find((entity) => entity.id === selectedId) ?? null;
  const selectedGenome = selectedEntity === null ? undefined : genomes.find((genome) => genome.genomeId === selectedEntity.genomeId);

  return (
    <div className="battle-layout">
      <div className="battle-topbar">
        <div>
          <span className="eyebrow">Live spectator</span>
          <h2>{start?.mapId ?? "Loading arena"}</h2>
        </div>
        <div className="battle-timer">
          <span className={`status-dot ${status.toLowerCase()}`} />
          {formatTime(snapshot?.tick ?? 0, catalog.ruleset.tickRate)}
        </div>
        <button className="secondary" onClick={onExit}>
          Leave match
        </button>
      </div>

      <div className="battle-scorebar">
        <PlayerHud
          name={players[0]?.nickname ?? "Alpha"}
          hive={hiveA}
          count={countA}
          score={snapshot?.scores[0] ?? 0}
          color="alpha"
        />
        <div className="versus">VS</div>
        <PlayerHud
          name={players[1]?.nickname ?? "Beta"}
          hive={hiveB}
          count={countB}
          score={snapshot?.scores[1] ?? 0}
          color="beta"
        />
      </div>

      <div className="battle-main">
        <div className="battlefield-wrap">
          <Battlefield
            map={map}
            snapshot={snapshot}
            genomes={genomes}
            selectedId={selectedId}
            onSelect={setSelectedId}
          />
          {snapshot === null && (
            <div className="battle-loading">
              <div className="spinner" />
              <p>Awaiting first snapshot…</p>
            </div>
          )}
          {error !== null && <div className="battle-error">{error}</div>}
        </div>

        <aside className="battle-side">
          <div className="panel battle-inspector">
            <div className="panel-header">
              <h3>Inspector</h3>
              <span className="muted">{selectedEntity === null ? "no selection" : `#${selectedEntity.id}`}</span>
            </div>
            {selectedEntity === null && <p className="muted">Click a creature to inspect its genome and decision state.</p>}
            {selectedEntity !== null && (
              <div className="inspector-body">
                <h4>{selectedGenome?.name ?? `Unit ${selectedEntity.id}`}</h4>
                <p className="muted">
                  {selectedEntity.owner === 0 ? "Alpha" : "Beta"} · {selectedGenome?.chassisId ?? "unknown"} ·{" "}
                  {selectedGenome?.behaviorPresetId ?? "unknown"}
                </p>
                <div className="stat-grid compact">
                  <Stat label="HP" value={`${selectedEntity.hp}/${selectedEntity.maxHp}`} />
                  <Stat label="Energy" value={selectedEntity.energy} />
                  <Stat label="Mode" value={selectedEntity.mode} />
                  <Stat label="Reason" value={selectedEntity.reason} />
                  <Stat label="Target" value={selectedEntity.targetId === 0 ? "none" : `#${selectedEntity.targetId}`} />
                  <Stat label="State" value={stateLabel(selectedEntity.state)} />
                </div>
              </div>
            )}
          </div>

          <div className="panel event-panel">
            <div className="panel-header">
              <h3>Event feed</h3>
              <span className="muted">{events.length}</span>
            </div>
            <div className="event-list">
              {events.length === 0 && <p className="muted">No events yet.</p>}
              {events
                .slice(-14)
                .reverse()
                .map((event) => (
                  <div key={event.id} className={`event-row ${event.type.toLowerCase()}`}>
                    <span className="event-time">{formatTime(event.tick, catalog.ruleset.tickRate)}</span>
                    <span>{eventText(event, snapshot, genomes)}</span>
                  </div>
                ))}
            </div>
          </div>
        </aside>
      </div>

      {result !== null && (
        <div className="result-overlay">
          <div className="result-card">
            <span className="eyebrow">Match finished</span>
            <h2>{result.winner === -1 ? "Draw" : `${players[result.winner]?.nickname ?? "Winner"} wins`}</h2>
            <p className="muted">{result.reason}</p>
            <div className="result-stats">
              {result.stats.map((stats) => (
                <div key={stats.playerId} className="result-stat">
                  <strong>{stats.nickname}</strong>
                  <span>{stats.damageDealt} damage</span>
                  <span>{stats.kills} kills</span>
                  <span>{stats.survivingCreatures} survivors</span>
                  <span>{stats.hiveHp} hive HP</span>
                </div>
              ))}
            </div>
            <p className="muted">Replay is available in the Replays tab.</p>
            <button className="primary" onClick={onExit}>
              Back to army
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function PlayerHud({
  name,
  hive,
  count,
  score,
  color
}: {
  name: string;
  hive: { hp: number; maxHp: number } | undefined;
  count: number;
  score: number;
  color: "alpha" | "beta";
}) {
  const percent = hive === undefined || hive.maxHp === 0 ? 0 : (hive.hp / hive.maxHp) * 100;
  return (
    <div className={`player-hud ${color}`}>
      <div className="player-hud-title">
        <strong>{name}</strong>
        <span>{count} creatures</span>
      </div>
      <div className="bar">
        <span className="bar-fill" style={{ width: `${percent}%` }} />
      </div>
      <div className="player-hud-footer">
        <span>Hive {hive?.hp ?? 0}/{hive?.maxHp ?? 0}</span>
        <span>{score} damage</span>
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="stat">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function formatTime(tick: number, tickRate: number): string {
  const totalSeconds = Math.floor(tick / tickRate);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;
}

function stateLabel(state: number): string {
  return ["IDLE", "MOVING", "ATTACKING", "HARVESTING", "FLEEING", "STUNNED", "BURROWED", "DEAD"][state] ?? "UNKNOWN";
}

function eventText(event: SimEvent, snapshot: Snapshot | null, genomes: GenomeSummary[]): string {
  const target = snapshot?.entities.find((entity) => entity.id === event.targetId);
  const targetGenome = target === undefined ? undefined : genomes.find((genome) => genome.genomeId === target.genomeId);
  const targetName = targetGenome?.name ?? `#${event.targetId}`;
  switch (event.type) {
    case "SPAWN":
      return `${genomes.find((genome) => genome.genomeId === event.value)?.name ?? "Creature"} spawned`;
    case "ATTACK":
      return `Attack on ${targetName}`;
    case "HIT":
      return `${targetName} takes ${event.value} damage`;
    case "MISS":
      return `Attack missed ${targetName}`;
    case "DEATH":
      return `${targetName} destroyed`;
    case "HEAL":
      return `${targetName} healed ${event.value}`;
    case "OBJECTIVE":
      return `Hive #${event.targetId} takes ${event.value} damage`;
    case "MATCH_END":
      return event.text || "Match ended";
    default:
      return event.type;
  }
}
