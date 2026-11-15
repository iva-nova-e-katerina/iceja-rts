import { useEffect, useMemo, useRef, useState } from "react";
import { createArmy, createCreatureFromTemplate, createMatch, listArmies, listCreatures } from "../../api";
import { unlockBattleAudio } from "../../audio/battleAudio";
import { nextCreatureName } from "../../utils/creatureName";
import type { ArmyUnitInput, ArmyView, Catalog, CreatureView, MapDefinition, TemplateView } from "../../types";

interface ArmyScreenProps {
  catalog: Catalog;
  onMatchCreated: (matchId: string) => void;
}

export default function ArmyScreen({ catalog, onMatchCreated }: ArmyScreenProps) {
  const [creatures, setCreatures] = useState<CreatureView[]>([]);
  const [templateCreatureViews, setTemplateCreatureViews] = useState<CreatureView[]>([]);
  const [armies, setArmies] = useState<ArmyView[]>([]);
  const [units, setUnits] = useState<ArmyUnitInput[]>([]);
  const [armyName, setArmyName] = useState("Моя стая");
  const [mapId, setMapId] = useState(catalog.maps[0]?.id ?? "duel-arena");
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [addingTemplateId, setAddingTemplateId] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const templateCreatures = useRef(new Map<string, CreatureView>());

  const creaturesById = useMemo(
    () => new Map([...creatures, ...templateCreatureViews].map((creature) => [creature.id, creature])),
    [creatures, templateCreatureViews]
  );
  const collectionCreatures = creatures.filter((creature) => !creature.isTemplate);
  const map = catalog.maps.find((item) => item.id === mapId) ?? catalog.maps[0];
  const totalCost = units.reduce((sum, unit) => sum + (creaturesById.get(unit.creatureId)?.stats.costBm ?? 0), 0);
  const budgetPercent = Math.min(100, (totalCost / catalog.ruleset.armyBudgetBm) * 100);

  useEffect(() => {
    let cancelled = false;
    Promise.all([listCreatures(), listArmies()])
      .then(([creatureList, armyList]) => {
        if (!cancelled) {
          setCreatures(creatureList);
          setArmies(armyList);
        }
      })
      .catch((reason: unknown) => {
        if (!cancelled) {
          setMessage(reason instanceof Error ? reason.message : "Не удалось загрузить армию");
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

  function canAfford(cost: number): string | null {
    if (units.length >= catalog.ruleset.maxCreaturesPerArmy) {
      return "В армии уже максимум существ";
    }
    if (totalCost + cost > catalog.ruleset.armyBudgetBm) {
      return "Бюджет армии превышен";
    }
    return null;
  }

  function addCreature(creature: CreatureView) {
    const problem = canAfford(creature.stats.costBm);
    if (problem !== null) {
      setMessage(problem);
      return;
    }
    const position = defaultPosition(units.length, map);
    setUnits((current) => [
      ...current,
      { creatureId: creature.id, x: position.x, y: position.y }
    ]);
    setMessage(null);
  }

  async function addTemplate(template: TemplateView) {
    const problem = canAfford(template.validation.stats.costBm);
    if (problem !== null) {
      setMessage(problem);
      return;
    }
    setAddingTemplateId(template.id);
    setMessage(null);
    try {
      let creature = templateCreatures.current.get(template.id);
      if (creature === undefined) {
        creature = await createCreatureFromTemplate(
          template.name,
          nextCreatureName(templateLabels[template.name] ?? template.genome.name)
        );
        templateCreatures.current.set(template.id, creature);
        setTemplateCreatureViews((current) => [creature as CreatureView, ...current]);
      }
      addCreature(creature);
    } catch (reason: unknown) {
      setMessage(reason instanceof Error ? reason.message : "Не удалось добавить шаблон");
    } finally {
      setAddingTemplateId(null);
    }
  }

  function removeUnit(index: number) {
    setUnits((current) => current.filter((_, unitIndex) => unitIndex !== index));
  }

  function autoPlace() {
    setUnits((current) =>
      current.map((unit, index) => {
        const position = defaultPosition(index, map);
        return { ...unit, x: position.x, y: position.y };
      })
    );
  }

  async function startBattle() {
    unlockBattleAudio();
    if (units.length === 0) {
      setMessage("Добавь хотя бы одно существо");
      return;
    }
    setStarting(true);
    setMessage(null);
    try {
      const army = await createArmy(armyName, units);
      const match = await createMatch(army.id, mapId);
      onMatchCreated(match.id);
    } catch (reason: unknown) {
      setMessage(reason instanceof Error ? reason.message : "Не удалось начать бой");
    } finally {
      setStarting(false);
    }
  }

  async function startSavedArmy(army: ArmyView) {
    unlockBattleAudio();
    setStarting(true);
    setMessage(null);
    try {
      const match = await createMatch(army.id, mapId);
      onMatchCreated(match.id);
    } catch (reason: unknown) {
      setMessage(reason instanceof Error ? reason.message : "Не удалось начать бой");
    } finally {
      setStarting(false);
    }
  }

  if (loading) {
    return <div className="screen-loading">Загружаем коллекцию…</div>;
  }

  return (
    <div className="army-layout">
      <aside className="panel collection-panel">
        <div className="panel-header">
          <h2>Коллекция</h2>
          <span className="muted">{collectionCreatures.length} сохранено</span>
        </div>
        <div className="template-army-section">
          <div className="panel-header compact">
            <h3>Шаблоны</h3>
            <span className="muted">1 клик в армию</span>
          </div>
          <div className="template-army-list">
            {catalog.templates.map((template) => {
              const cost = template.validation.stats.costBm;
              const problem = canAfford(cost);
              return (
                <div key={template.id} className="template-army-card">
                  <div>
                    <strong>{templateLabels[template.name] ?? template.name}</strong>
                    <span className="muted">{cost} BM</span>
                  </div>
                  <button
                    className="primary small"
                    disabled={problem !== null || addingTemplateId !== null}
                    onClick={() => void addTemplate(template)}
                    title={problem ?? `Добавить «${templateLabels[template.name] ?? template.name}» в армию`}
                  >
                    {addingTemplateId === template.id ? "…" : "В армию"}
                  </button>
                </div>
              );
            })}
          </div>
        </div>
        <h3 className="collection-subtitle">Мои существа</h3>
        {collectionCreatures.length === 0 && (
          <div className="empty-state">
            <p>Пока нет сохранённых существ.</p>
            <p className="muted">Собери существо в Лаборатории.</p>
          </div>
        )}
        <div className="creature-list">
          {collectionCreatures.map((creature) => {
            const canAdd =
              units.length < catalog.ruleset.maxCreaturesPerArmy &&
              totalCost + creature.stats.costBm <= catalog.ruleset.armyBudgetBm;
            return (
              <div key={creature.id} className="creature-card">
                <div>
                  <strong>{displayCreatureName(creature.genome.name)}</strong>
                  <span className="muted">
                    {catalog.chassis.find((item) => item.id === creature.genome.chassisId)?.name ?? creature.genome.chassisId} ·{" "}
                    {catalog.behaviorPresets.find((item) => item.id === creature.genome.behavior.presetId)?.name ??
                      creature.genome.behavior.presetId}
                  </span>
                  <span className="muted">
                    Опыт {creature.experience}/{catalog.ruleset.maxExperience} ·{" "}
                    +{Math.round((creature.experience / Math.max(1, catalog.ruleset.maxExperience)) * 25)}% HP/урон
                  </span>
                </div>
                <div className="creature-card-footer">
                  <span>{creature.stats.costBm} BM</span>
                  <button className="secondary" disabled={!canAdd} onClick={() => addCreature(creature)}>
                    Добавить
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </aside>

      <section className="planner-panel">
        <div className="stage-header">
          <div>
            <span className="eyebrow">План армии</span>
            <h2>{armyName}</h2>
          </div>
          <div className="stage-actions">
            <select value={mapId} onChange={(event) => setMapId(event.target.value)}>
              {catalog.maps.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
            <button className="secondary" onClick={autoPlace} disabled={units.length === 0}>
              Авто-расстановка
            </button>
            <button className="primary" onClick={() => void startBattle()} disabled={starting || units.length === 0}>
              {starting ? "Запускаем…" : "В бой"}
            </button>
          </div>
        </div>

        <div className="army-builder-grid">
          <ArmyMap map={map} units={units} creaturesById={creaturesById} />
          <div className="army-form">
            <label>
              Название армии
              <input value={armyName} maxLength={32} onChange={(event) => setArmyName(event.target.value)} />
            </label>
            <p className="muted small-text">
              Противник подбирается под ваш бюджет и количество существ.
            </p>
            <div className="budget-block">
              <div className="budget-header">
                <span>Бюджет армии</span>
                <strong>
                  {totalCost} / {catalog.ruleset.armyBudgetBm} BM
                </strong>
              </div>
              <div className="bar">
                <span
                  className={budgetPercent > 90 ? "bar-fill danger" : "bar-fill"}
                  style={{ width: `${budgetPercent}%` }}
                />
              </div>
              <p className="muted">
                {units.length} / {catalog.ruleset.maxCreaturesPerArmy} существ
              </p>
            </div>
            {message !== null && <div className="notice">{message}</div>}
            <div className="unit-list">
              {units.map((unit, index) => {
                const creature = creaturesById.get(unit.creatureId);
                return (
                  <div key={`${unit.creatureId}-${index}`} className="unit-row">
                    <span>
                      {displayCreatureName(creature?.genome.name ?? "Неизвестно")} · {creature?.stats.costBm ?? 0} BM
                    </span>
                    <button className="icon-button" onClick={() => removeUnit(index)} title="Убрать">
                      ×
                    </button>
                  </div>
                );
              })}
              {units.length === 0 && <p className="muted">Добавь существ из коллекции.</p>}
            </div>
          </div>
        </div>
      </section>

      <aside className="panel saved-panel">
        <div className="panel-header">
          <h2>Сохранённые армии</h2>
          <span className="muted">{armies.length}</span>
        </div>
        {armies.length === 0 && <p className="muted">Сохранённых армий пока нет.</p>}
        <div className="saved-list">
          {armies.map((army) => (
            <div key={army.id} className="saved-army">
              <div>
                <strong>{army.name}</strong>
                <span className="muted">
                  {army.creatureCount} существ · {army.totalCostBm} BM
                </span>
              </div>
              <button className="secondary" disabled={starting} onClick={() => void startSavedArmy(army)}>
                В бой
              </button>
            </div>
          ))}
        </div>
      </aside>
    </div>
  );
}

function ArmyMap({
  map,
  units,
  creaturesById
}: {
  map: MapDefinition;
  units: ArmyUnitInput[];
  creaturesById: Map<string, CreatureView>;
}) {
  return (
    <div className="army-map-wrap">
      <svg
        className="army-map"
        viewBox={`0 0 ${map.widthMm} ${map.heightMm}`}
        role="img"
        aria-label="Army deployment map"
      >
        <rect x="0" y="0" width={map.widthMm} height={map.heightMm} fill="#081013" />
        {Array.from({ length: Math.ceil(map.widthMm / 8000) + 1 }, (_, index) => (
          <line
            key={`v-${index}`}
            x1={index * 8000}
            y1="0"
            x2={index * 8000}
            y2={map.heightMm}
            stroke="#12242a"
            strokeWidth="350"
          />
        ))}
        {Array.from({ length: Math.ceil(map.heightMm / 8000) + 1 }, (_, index) => (
          <line
            key={`h-${index}`}
            x1="0"
            y1={index * 8000}
            x2={map.widthMm}
            y2={index * 8000}
            stroke="#12242a"
            strokeWidth="350"
          />
        ))}
        {map.hazards.map((hazard) => (
          <ellipse
            key={`hazard-${hazard.id}`}
            cx={hazard.x}
            cy={hazard.y}
            rx={hazard.radiusMm}
            ry={hazard.radiusMm * 0.68}
            fill="rgba(111, 156, 63, 0.22)"
            stroke="rgba(159, 218, 107, 0.45)"
            strokeWidth="450"
          />
        ))}
        {map.objectives.map((objective) => (
          <g key={`objective-${objective.id}`}>
            <circle
              cx={objective.x}
              cy={objective.y}
              r={objective.radiusMm}
              fill="rgba(124, 147, 153, 0.09)"
              stroke="rgba(124, 147, 153, 0.55)"
              strokeWidth="450"
            />
            <circle cx={objective.x} cy={objective.y} r={objective.radiusMm * 0.28} fill="rgba(124, 147, 153, 0.25)" />
          </g>
        ))}
        {map.obstacles.map((obstacle, index) => (
          <rect
            key={index}
            x={obstacle.x}
            y={obstacle.y}
            width={obstacle.width}
            height={obstacle.height}
            fill="#1d2c30"
            stroke="#2f4a50"
            strokeWidth="500"
          />
        ))}
        <rect
          x={map.spawnA.x - map.deployRadiusMm}
          y={map.spawnA.y - map.deployRadiusMm}
          width={map.deployRadiusMm * 2}
          height={map.deployRadiusMm * 2}
          fill="rgba(57, 230, 176, 0.08)"
          stroke="#39e6b0"
          strokeWidth="600"
          strokeDasharray="3000 1800"
        />
        <circle cx={map.spawnA.x} cy={map.spawnA.y} r={4000} fill="#12362f" stroke="#39e6b0" strokeWidth="700" />
        <circle cx={map.spawnB.x} cy={map.spawnB.y} r={4000} fill="#3b2317" stroke="#ff9d5c" strokeWidth="700" />
        {units.map((unit, index) => {
          const creature = creaturesById.get(unit.creatureId);
          const x = unit.x === 0 && unit.y === 0 ? defaultPosition(index, map).x : unit.x;
          const y = unit.x === 0 && unit.y === 0 ? defaultPosition(index, map).y : unit.y;
          const radius = Math.max(1200, creature?.stats.radiusMm ?? 500);
          return (
            <circle
              key={`${unit.creatureId}-${index}`}
              cx={x}
              cy={y}
              r={radius}
              fill="#39e6b0"
              fillOpacity="0.8"
              stroke="#071012"
              strokeWidth="500"
            />
          );
        })}
      </svg>
    </div>
  );
}

function displayCreatureName(name: string): string {
  return templateLabels[name] ?? name;
}

const templateLabels: Record<string, string> = {
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
};

function defaultPosition(index: number, map: MapDefinition): { x: number; y: number } {
  const column = index % 6;
  const row = Math.floor(index / 6);
  return {
    x: Math.min(map.widthMm - 1000, Math.max(1000, map.spawnA.x + 4000 + column * 2500)),
    y: Math.min(map.heightMm - 1000, Math.max(1000, map.spawnA.y + (row - 2) * 3000))
  };
}
