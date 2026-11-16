import { useEffect, useState } from "react";
import { getLanguage, installDocumentLocalization, setLanguage, type Language } from "./i18n";
import { ensureSession, getCatalog } from "./api";
import { unlockBattleAudio } from "./audio/battleAudio";
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
  const [language, setLanguageState] = useState<Language>(() => getLanguage());

  useEffect(() => {
    return installDocumentLocalization();
  }, [language]);

  useEffect(() => {
    const unlock = () => unlockBattleAudio();
    window.addEventListener("pointerdown", unlock, { once: true });
    window.addEventListener("keydown", unlock, { once: true });
    return () => {
      window.removeEventListener("pointerdown", unlock);
      window.removeEventListener("keydown", unlock);
    };
  }, []);

  useEffect(() => {
    if (savedCreature === null) {
      return;
    }
    const timer = window.setTimeout(() => setSavedCreature(null), 3000);
    return () => window.clearTimeout(timer);
  }, [savedCreature]);

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
          setError(reason instanceof Error ? reason.message : "Не удалось загрузить данные игры");
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
          Повторить
        </button>
      </main>
    );
  }

  if (session === null || catalog === null) {
    return (
      <main className="app-loading">
        <div className="spinner" />
        <p>Подключаемся к лаборатории…</p>
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
            Лаборатория
          </button>
          <button className={tab === "army" ? "tab active" : "tab"} onClick={() => setTab("army")}>
            Армия
          </button>
          <button
            className={tab === "battle" ? "tab active" : "tab"}
            onClick={() => setTab("battle")}
            disabled={activeMatchId === null}
          >
            Бой
          </button>
          <button className={tab === "replays" ? "tab active" : "tab"} onClick={() => setTab("replays")}>
            Реплеи
          </button>
        </nav>
        <div className="language-switch" aria-label="Language">
          <button
            className={language === "ru" ? "active" : ""}
            onClick={() => {
              setLanguage("ru");
              setLanguageState("ru");
            }}
          >
            RU
          </button>
          <button
            className={language === "en" ? "active" : ""}
            onClick={() => {
              setLanguage("en");
              setLanguageState("en");
            }}
          >
            EN
          </button>
        </div>
        <div className="identity">
          <span className="dot online" />
          {session.nickname}
        </div>
      </header>

      {savedCreature !== null && (
        <div className="toast" onClick={() => setSavedCreature(null)}>
          Сохранено: {savedCreature.genome.name} · {savedCreature.stats.costBm} BM
        </div>
      )}

      <section className="content">
        {tab === "lab" && (
          <LabScreen
            catalog={catalog}
            onSaved={(creature) => {
              setSavedCreature(creature);
            }}
            onBattle={(matchId) => {
              setActiveMatchId(matchId);
              setTab("battle");
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
