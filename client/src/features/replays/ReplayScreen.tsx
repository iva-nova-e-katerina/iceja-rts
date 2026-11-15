import { useEffect, useState } from "react";
import { getReplay, listMatches } from "../../api";
import type { Catalog, MatchView, ReplayView, Snapshot } from "../../types";
import Battlefield from "../battle/Battlefield";

interface ReplayScreenProps {
  catalog: Catalog;
}

export default function ReplayScreen({ catalog }: ReplayScreenProps) {
  const [matches, setMatches] = useState<MatchView[]>([]);
  const [replay, setReplay] = useState<ReplayView | null>(null);
  const [frameIndex, setFrameIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    listMatches()
      .then((value) => {
        if (!cancelled) {
          setMatches(value);
        }
      })
      .catch((reason: unknown) => {
        if (!cancelled) {
          setMessage(reason instanceof Error ? reason.message : "Cannot load matches");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!playing || replay === null) {
      return;
    }
    const timer = window.setInterval(() => {
      setFrameIndex((current) => {
        if (current >= replay.snapshots.length - 1) {
          setPlaying(false);
          return current;
        }
        return current + 1;
      });
    }, 100 / speed);
    return () => window.clearInterval(timer);
  }, [playing, speed, replay]);

  async function openReplay(match: MatchView) {
    setMessage(null);
    try {
      const value = await getReplay(match.replayId);
      setReplay(value);
      setFrameIndex(0);
      setPlaying(false);
    } catch (reason: unknown) {
      setMessage(reason instanceof Error ? reason.message : "Cannot load replay");
    }
  }

  const snapshot: Snapshot | null = replay?.snapshots[frameIndex] ?? replay?.snapshots.at(-1) ?? null;
  const map = catalog.maps.find((item) => item.id === replay?.mapId) ?? catalog.maps[0];
  const selectedGenomes = replay?.genomes ?? [];

  return (
    <div className="replay-layout">
      <aside className="panel replay-list-panel">
        <div className="panel-header">
          <h2>Матчи</h2>
          <span className="muted">{matches.length}</span>
        </div>
        {loading && <p className="muted">Загружаем историю…</p>}
        {message !== null && <p className="error-text">{message}</p>}
        {!loading && matches.length === 0 && (
          <div className="empty-state">
            <p>Матчей пока нет.</p>
            <p className="muted">Запусти бой на вкладке «Армия».</p>
          </div>
        )}
        <div className="match-list">
          {matches.map((match) => (
            <button
              key={match.id}
              className={replay?.matchId === match.id ? "match-card active" : "match-card"}
              onClick={() => void openReplay(match)}
            >
              <div>
                <strong>{catalog.maps.find((item) => item.id === match.mapId)?.name ?? match.mapId}</strong>
                <span className="muted">
                  {statusLabel(match.status)} · {new Date(match.createdAt).toLocaleString()}
                </span>
              </div>
              <span className={`badge ${match.status === "FINISHED" ? "good" : "live"}`}>{statusLabel(match.status)}</span>
            </button>
          ))}
        </div>
      </aside>

      <section className="replay-player">
        {replay === null && (
          <div className="empty-state large">
            <h2>Просмотр реплеев</h2>
            <p className="muted">Выбери матч, чтобы посмотреть кадры и события.</p>
          </div>
        )}
        {replay !== null && (
          <>
            <div className="replay-topbar">
              <div>
                <span className="eyebrow">Реплей</span>
                <h2>{catalog.maps.find((item) => item.id === replay.mapId)?.name ?? replay.mapId}</h2>
              </div>
              <div className="replay-timer">
                {formatTime(snapshot?.tick ?? 0, catalog.ruleset.tickRate)} /{" "}
                {formatTime(replay.durationTicks, catalog.ruleset.tickRate)}
              </div>
            </div>
            <div className="replay-viewport">
              <Battlefield
                map={map}
                snapshot={snapshot}
                genomes={selectedGenomes}
                selectedId={null}
                onSelect={() => undefined}
              />
            </div>
            <div className="replay-controls">
              <button className="primary" onClick={() => setPlaying((value) => !value)}>
                {playing ? "Пауза" : "Смотреть"}
              </button>
              <input
                type="range"
                min={0}
                max={Math.max(0, replay.snapshots.length - 1)}
                value={frameIndex}
                onChange={(event) => setFrameIndex(Number(event.target.value))}
              />
              <div className="speed-row">
                {[0.5, 1, 2, 4].map((value) => (
                  <button
                    key={value}
                    className={speed === value ? "chip active" : "chip"}
                    onClick={() => setSpeed(value)}
                  >
                    {value}×
                  </button>
                ))}
              </div>
            </div>
            <div className="replay-bottom">
              <div className="panel">
                <div className="panel-header">
                  <h3>Snapshot</h3>
                  <span className="muted">#{snapshot?.tick ?? 0}</span>
                </div>
                <div className="replay-stats">
                  <span>Существ {snapshot?.entities.length ?? 0}</span>
                  <span>Снарядов {snapshot?.projectiles.length ?? 0}</span>
                  <span>Контрольная сумма {snapshot?.checksum ?? 0}</span>
                </div>
              </div>
              <div className="panel">
                <div className="panel-header">
                  <h3>Результат</h3>
                </div>
                {replay.result === null && <p className="muted">Матч ещё идёт.</p>}
                {replay.result !== null && (
                  <div className="replay-result">
                    <strong>
                      {replay.result.winner === -1
                        ? "Ничья"
                        : `${replay.players[replay.result.winner]?.nickname ?? "Победитель"} победил`}
                    </strong>
                    <span className="muted">{resultReasonLabel(replay.result.reason)}</span>
                    {replay.result.stats.map((stats) => (
                      <div key={stats.playerId} className="replay-result-row">
                        <span>{stats.nickname}</span>
                        <span>{stats.damageDealt} урона</span>
                        <span>{stats.kills} убийств</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </>
        )}
      </section>
    </div>
  );
}

function resultReasonLabel(reason: string): string {
  return {
    HIVE_DESTROYED: "Улей уничтожен",
    ANNIHILATION: "Армия уничтожена",
    TIMEOUT_HIVE_HP: "Время вышло: больше HP улья",
    TIMEOUT_CREATURE_HP: "Время вышло: больше HP существ",
    TIMEOUT_DAMAGE: "Время вышло: больше урона",
    TIMEOUT_DRAW: "Время вышло: ничья",
    SIMULTANEOUS_HIVE_DESTRUCTION: "Оба улья уничтожены",
    TIMEOUT: "Время вышло"
  }[reason] ?? reason;
}

function statusLabel(status: string): string {
  return status === "FINISHED" ? "ЗАВЕРШЁН" : status === "LIVE" ? "ИДЁТ" : status;
}

function formatTime(tick: number, tickRate: number): string {
  const totalSeconds = Math.floor(tick / tickRate);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;
}
