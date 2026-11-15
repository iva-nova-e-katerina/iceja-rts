import { useEffect, useState } from "react";
import { ensureSession, getCatalog } from "./api";
import type { Catalog, CreatureView, GuestSession } from "./types";
import LabScreen from "./features/lab/LabScreen";
import ArmyScreen from "./features/army/ArmyScreen";
import BattleScreen from "./features/battle/BattleScreen";
import ReplayScreen from "./features/replays/ReplayScreen";

type Tab = "lab" | "army" | "battle" | "replays";

export default function App() {
  const [session, setSession] = useState<GuestSession | null>(null);
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [tab, setTab] = useState<Tab>("lab");
  const [activeMatchId, setActiveMatchId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [savedCreature, setSavedCreature] = useState<CreatureView | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([ensureSession(), getCatalog()])
      .then(([sessionValue, catalogValue]) => {
        if (!cancelled) {
          setSession(sessionValue);
          setCatalog(catalogValue);
        }
      })
      .catch((reason: unknown) => {
        if (!cancelled) {
          setError(reason instanceof Error ? reason.message : "Cannot load client data");
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (error !== null) {
    return (
      <main className="app-loading">
        <h1>ICEJA: Autonomy</h1>
        <p className="error-text">{error}</p>
        <button className="primary" onClick={() => window.location.reload()}>
          Retry
        </button>
      </main>
    );
  }

  if (session === null || catalog === null) {
    return (
      <main className="app-loading">
        <div className="spinner" />
        <p>Connecting to the autonomy lab…</p>
      </main>
    );
  }

  return (
    <main className="app">
      <header className="topbar">
        <div className="brand">
          <span className="eyebrow">ICEJA</span>
          <h1>Autonomy</h1>
        </div>
        <nav className="tabs">
          <button className={tab === "lab" ? "tab active" : "tab"} onClick={() => setTab("lab")}>
            Laboratory
          </button>
          <button className={tab === "army" ? "tab active" : "tab"} onClick={() => setTab("army")}>
            Army
          </button>
          <button
            className={tab === "battle" ? "tab active" : "tab"}
            onClick={() => setTab("battle")}
            disabled={activeMatchId === null}
          >
            Battle
          </button>
          <button className={tab === "replays" ? "tab active" : "tab"} onClick={() => setTab("replays")}>
            Replays
          </button>
        </nav>
        <div className="identity">
          <span className="dot online" />
          {session.nickname}
        </div>
      </header>

      {savedCreature !== null && (
        <div className="toast" onClick={() => setSavedCreature(null)}>
          Saved {savedCreature.genome.name} · {savedCreature.stats.costBm} BM
        </div>
      )}

      <section className="content">
        {tab === "lab" && (
          <LabScreen
            catalog={catalog}
            onSaved={(creature) => {
              setSavedCreature(creature);
              setTab("army");
            }}
          />
        )}
        {tab === "army" && (
          <ArmyScreen
            catalog={catalog}
            onMatchCreated={(matchId) => {
              setActiveMatchId(matchId);
              setTab("battle");
            }}
          />
        )}
        {tab === "battle" && activeMatchId !== null && (
          <BattleScreen
            catalog={catalog}
            matchId={activeMatchId}
            onExit={() => setTab("army")}
          />
        )}
        {tab === "replays" && <ReplayScreen catalog={catalog} />}
      </section>
    </main>
  );
}
