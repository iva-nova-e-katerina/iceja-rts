import { useEffect, useRef, useState } from "react";
import { getWsTicket, matchWebSocketUrl } from "../../api";
import { BattleAudio, unlockBattleAudio } from "../../audio/battleAudio";
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
import Minimap from "./Minimap";

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
  const [soundEnabled, setSoundEnabled] = useState(() => localStorage.getItem("iceja.sound") !== "off");
  const [audioState, setAudioState] = useState("suspended");
  const [cleanMode, setCleanMode] = useState(false);
  const [visualSettings, setVisualSettings] = useState(() => {
    try {
      const stored = localStorage.getItem("iceja.visualSettings");
      if (stored !== null) {
        return JSON.parse(stored) as { effects: boolean; smooth: boolean; minimap: boolean };
      }
    } catch {
      return { effects: true, smooth: true, minimap: true };
    }
    return { effects: true, smooth: true, minimap: true };
  });
  const resultRef = useRef<MatchResult | null>(null);
  const genomesRef = useRef<GenomeSummary[]>([]);
  const audioRef = useRef<BattleAudio | null>(null);
  const matchActiveRef = useRef(false);

  useEffect(() => {
    const audio = new BattleAudio();
    audioRef.current = audio;
    audio.setEnabled(soundEnabled);
    const refreshState = () => setAudioState(audio.state());
    const stateTimer = window.setTimeout(refreshState, 250);
    const unlock = () => {
      unlockBattleAudio();
      audio.resume();
      window.setTimeout(() => {
        refreshState();
        if (audio.state() === "running") {
          if (matchActiveRef.current) {
            audio.startAmbient();
          }
          window.removeEventListener("pointerdown", unlock);
        }
      }, 200);
    };
    window.addEventListener("pointerdown", unlock);
    return () => {
      window.clearTimeout(stateTimer);
      window.removeEventListener("pointerdown", unlock);
      audio.dispose();
      audioRef.current = null;
    };
  }, []);

  useEffect(() => {
    localStorage.setItem("iceja.visualSettings", JSON.stringify(visualSettings));
  }, [visualSettings]);

  useEffect(() => {
    localStorage.setItem("iceja.sound", soundEnabled ? "on" : "off");
    audioRef.current?.setEnabled(soundEnabled);
    const timer = window.setTimeout(() => {
      setAudioState(audioRef.current?.state() ?? "unavailable");
    }, 200);
    return () => window.clearTimeout(timer);
  }, [soundEnabled]);

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
            matchActiveRef.current = true;
            audioRef.current?.startAmbient();
            audioRef.current?.playReady();
          } else if (message.type === "snapshot") {
            setSnapshot(message.payload as Snapshot);
          } else if (message.type === "event_batch") {
            const payload = message.payload as EventBatchPayload;
            audioRef.current?.playEvents(payload.events);
            setEvents((current) => [...current, ...payload.events].slice(-300));
          } else if (message.type === "match_end") {
            const payload = message.payload as MatchEndPayload;
            resultRef.current = payload.result;
            matchActiveRef.current = false;
            audioRef.current?.stopAmbient();
            setResult(payload.result);
            setStatus("FINISHED");
            audioRef.current?.playMatchEnd(payload.result.winner);
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
    <div className={cleanMode ? "battle-layout clean" : "battle-layout"}>
      <div className="battle-topbar">
        <div>
          <span className="eyebrow">Живой бой</span>
          <h2>{catalog.maps.find((item) => item.id === start?.mapId)?.name ?? "Загрузка арены"}</h2>
        </div>
        <div className="battle-timer">
          <span className={`status-dot ${status.toLowerCase()}`} />
          {formatTime(snapshot?.tick ?? 0, catalog.ruleset.tickRate)}
        </div>
        <div className="battle-actions">
          <details className="battle-settings">
            <summary className="secondary">Настройки</summary>
            <div className="battle-settings-popover">
              <label>
                <input
                  type="checkbox"
                  checked={visualSettings.effects}
                  onChange={() =>
                    setVisualSettings((current) => ({ ...current, effects: !current.effects }))
                  }
                />
                Эффекты попаданий
              </label>
              <label>
                <input
                  type="checkbox"
                  checked={visualSettings.smooth}
                  onChange={() =>
                    setVisualSettings((current) => ({ ...current, smooth: !current.smooth }))
                  }
                />
                Плавное движение
              </label>
              <label>
                <input
                  type="checkbox"
                  checked={visualSettings.minimap}
                  onChange={() =>
                    setVisualSettings((current) => ({ ...current, minimap: !current.minimap }))
                  }
                />
                Мини-карта
              </label>
            </div>
          </details>
          <button className="secondary" onClick={() => setCleanMode((value) => !value)}>
            {cleanMode ? "Показать интерфейс" : "Чистый режим"}
          </button>
          <button
            className={soundEnabled && audioState !== "running" ? "primary" : "secondary"}
            data-audio-state={audioState}
            onClick={() => {
              const next = !soundEnabled;
              setSoundEnabled(next);
              if (next) {
                audioRef.current?.resume();
                window.setTimeout(() => {
                  if (matchActiveRef.current) {
                    audioRef.current?.startAmbient();
                  }
                  audioRef.current?.playTest();
                  setAudioState(audioRef.current?.state() ?? "unavailable");
                }, 150);
              } else {
                audioRef.current?.stopAmbient();
              }
            }}
            title="Включить или выключить звуки боя"
          >
            {soundEnabled ? (audioState === "running" ? "Звук: вкл" : "Включить звук") : "Звук: выкл"}
          </button>
          <button
            className="secondary"
            onClick={() => {
              setSoundEnabled(true);
              audioRef.current?.resume();
              window.setTimeout(() => {
                if (matchActiveRef.current) {
                  audioRef.current?.startAmbient();
                }
                audioRef.current?.playDeathTest();
                setAudioState(audioRef.current?.state() ?? "unavailable");
              }, 150);
            }}
            title="Проверить звук: сигнал и звук гибели"
          >
            Тест звука
          </button>
          <button className="secondary" onClick={onExit}>
            Выйти
          </button>
        </div>
      </div>

      {soundEnabled && audioState !== "running" && (
        <div className="audio-hint">
          Браузер пока не разрешил звук. Нажми «Включить звук» или «Тест».
        </div>
      )}

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
            events={events}
            effectsEnabled={visualSettings.effects}
            smoothMotion={visualSettings.smooth}
            selectedId={selectedId}
            onSelect={(entityId) => {
              audioRef.current?.resume();
              setSelectedId(entityId);
            }}
          />
          {visualSettings.minimap && <Minimap map={map} snapshot={snapshot} />}
          <div className="battle-camera-hint">Колесо — зум · перетаскивание — камера · клик — инспектор</div>
          {snapshot === null && (
            <div className="battle-loading">
              <div className="spinner" />
              <p>Ждём первый кадр…</p>
            </div>
          )}
          {error !== null && <div className="battle-error">{error}</div>}
        </div>

        <aside className="battle-side">
          <div className="panel battle-inspector">
            <div className="panel-header">
              <h3>Инспектор</h3>
              <span className="muted">{selectedEntity === null ? "ничего не выбрано" : `#${selectedEntity.id}`}</span>
            </div>
            {selectedEntity === null && <p className="muted">Нажми на существо, чтобы увидеть его состояние и решение.</p>}
            {selectedEntity !== null && (
              <div className="inspector-body">
                <h4>{selectedGenome === undefined ? `Существо #${selectedEntity.id}` : displayGenomeName(selectedGenome.name)}</h4>
                <p className="muted">
                  {ownerName(selectedEntity.owner)} ·{" "}
                  {catalog.chassis.find((chassis) => chassis.id === selectedGenome?.chassisId)?.name ?? "неизвестно"} ·{" "}
                  {catalog.behaviorPresets.find((preset) => preset.id === selectedGenome?.behaviorPresetId)?.name ??
                    "неизвестно"}
                </p>
                <div className="stat-grid compact">
                  <Stat label="Здоровье" value={`${selectedEntity.hp}/${selectedEntity.maxHp}`} />
                  <Stat label="Энергия" value={selectedEntity.energy} />
                  <Stat label="Режим" value={modeLabel(selectedEntity.mode)} />
                  <Stat label="Причина" value={reasonLabel(selectedEntity.reason)} />
                  <Stat label="Узел" value={selectedEntity.nodeId || "по пресету"} />
                  <Stat label="Цель" value={selectedEntity.targetId === 0 ? "нет" : `#${selectedEntity.targetId}`} />
                  <Stat label="Состояние" value={stateLabel(selectedEntity.state)} />
                </div>
                <p className="muted small-text">
                  Оружие: {selectedGenome === undefined || selectedGenome.weapons.length === 0
                    ? "нет"
                    : selectedGenome.weapons
                        .map((partId) => catalog.parts.find((definition) => definition.id === partId)?.name ?? partId)
                        .join(", ")}
                </p>
                <p className="muted small-text">На карте видны только внешние органы.</p>
                <div className="inspector-parts">
                  {selectedGenome?.render.parts.map((part, index) => (
                    <span key={`${part.partId}-${index}`} className="inspector-part">
                      {catalog.parts.find((definition) => definition.id === part.partId)?.name ?? part.partId}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>

          <div className="panel event-panel">
            <div className="panel-header">
              <h3>События</h3>
              <span className="muted">{events.length}</span>
            </div>
            <div className="event-list">
              {events.length === 0 && <p className="muted">Событий пока нет.</p>}
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
            <span className="eyebrow">Бой окончен</span>
            <h2>{result.winner === -1 ? "Ничья" : `${players[result.winner]?.nickname ?? "Победитель"} победил`}</h2>
            <p className="muted">{resultReasonLabel(result.reason)}</p>
            <div className="result-stats">
              {result.stats.map((stats) => (
                <div key={stats.playerId} className="result-stat">
                  <strong>{stats.nickname}</strong>
                  <span>{stats.damageDealt} урона</span>
                  <span>{stats.kills} убийств</span>
                  <span>{stats.survivingCreatures} выжило</span>
                  <span>{stats.hiveHp} HP улья</span>
                </div>
              ))}
            </div>
            <p className="muted">Реплей доступен во вкладке «Реплеи».</p>
            <button className="primary" onClick={onExit}>
              Вернуться к армии
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
        <span>{count} существ</span>
      </div>
      <div className="bar">
        <span className="bar-fill" style={{ width: `${percent}%` }} />
      </div>
      <div className="player-hud-footer">
        <span>Улей {hive?.hp ?? 0}/{hive?.maxHp ?? 0}</span>
        <span>{score} урона</span>
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

function displayGenomeName(name: string): string {
  return {
    Scout: "Разведчик",
    Brawler: "Боец",
    Spitter: "Плеватель",
    Tank: "Танк",
    Swarm: "Рой",
    Artillery: "Артиллерия",
    Healer: "Лекарь",
    Burrower: "Копатель",
    Flyer: "Летун",
    Harvester: "Сборщик"
  }[name] ?? name;
}

function ownerName(owner: number): string {
  return owner === 0 ? "Альфа" : "Бета";
}

function formatTime(tick: number, tickRate: number): string {
  const totalSeconds = Math.floor(tick / tickRate);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;
}

function stateLabel(state: number): string {
  return ["Стоит", "Идёт", "Атакует", "Собирает", "Убегает", "Оглушено", "В норе", "Мёртво"][state] ?? "Неизвестно";
}

function modeLabel(mode: string): string {
  return {
    SURVIVE: "Выживание",
    REST: "Отдых",
    DEFEND_HIVE: "Защита улья",
    ATTACK: "Атака",
    HUNT: "Охота",
    HARVEST: "Сбор",
    REGROUP: "Сбор группы",
    PATROL: "Патруль",
    IDLE: "Ожидание",
    SIEGE: "Осада",
    SCAVENGE: "Поиск падали"
  }[mode] ?? mode;
}

function reasonLabel(reason: string): string {
  return {
    TARGET_IN_RANGE: "Цель в радиусе",
    TARGET_VISIBLE: "Видит цель",
    NO_TARGET: "Нет цели",
    NO_ACTION: "Нет действия",
    NO_SENSORS: "Нет глаз",
    GUARD_POINT: "Охраняет точку",
    HIVE_UNDER_ATTACK: "Улей атакован",
    REGROUPING: "Собирается с группой",
    KEEP_DISTANCE: "Держит дистанцию",
    LOW_HP: "Мало здоровья",
    LOW_ENERGY: "Мало энергии",
    SIEGE_ORDER: "Атакует улей",
    PATROL: "Патрулирует",
    HOLD_POSITION: "Держит позицию",
    RESOURCE_SEARCH: "Ищет ресурсы",
    CORPSE_SEARCH: "Ищет труп",
    WAITING: "Ждёт",
    MOVE_TO_POINT: "Идёт в точку",
    RETURN_TO_HIVE: "Возвращается к улью",
    HIVE_TARGET: "Идёт к улью",
    ABILITY_UNAVAILABLE: "Способность недоступна",
    LAST_STAND: "Сопротивляется"
  }[reason] ?? reason;
}

function eventText(event: SimEvent, snapshot: Snapshot | null, genomes: GenomeSummary[]): string {
  const target = snapshot?.entities.find((entity) => entity.id === event.targetId);
  const targetGenome = target === undefined ? undefined : genomes.find((genome) => genome.genomeId === target.genomeId);
  const targetName = targetGenome === undefined ? `#${event.targetId}` : displayGenomeName(targetGenome.name);
  switch (event.type) {
    case "SPAWN":
      return `${displayGenomeName(genomes.find((genome) => genome.genomeId === event.value)?.name ?? "Существо")} появилось`;
    case "ATTACK":
      return `Атака по ${targetName}`;
    case "HIT":
      return `${targetName} получает ${event.value} урона`;
    case "MISS":
      return `Промах по ${targetName}`;
    case "DEATH":
      return `${targetName} уничтожен`;
    case "HEAL":
      return `${targetName} восстановил ${event.value}`;
    case "RESOURCE":
      return event.text === "CORPSE" ? `Труп съеден (+${event.value} энергии)` : "Сбор ресурса";
    case "OBJECTIVE":
      return `Улей #${event.targetId} получает ${event.value} урона`;
    case "MATCH_END":
      return event.text || "Бой окончен";
    default:
      return event.type;
  }
}
