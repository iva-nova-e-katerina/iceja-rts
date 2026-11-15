import { useEffect, useMemo, useState } from "react";
import { createArmy, createCreature, createMatch, validateGenome } from "../../api";
import { unlockBattleAudio } from "../../audio/battleAudio";
import type {
  Catalog,
  CreatureView,
  DerivedStats,
  Genome,
  PartCategory,
  PartDefinition,
  SocketType,
  UpgradeStat,
  ValidationResult
} from "../../types";
import BehaviorEditor from "./BehaviorEditor";
import CreatureSchematic from "./CreatureSchematic";

const categories: PartCategory[] = [
  "CORE",
  "LOCOMOTION",
  "SENSOR",
  "MELEE",
  "RANGED",
  "DEFENSE",
  "UTILITY"
];

const categoryLabels: Record<PartCategory, string> = {
  CORE: "Тело",
  LOCOMOTION: "Движение",
  SENSOR: "Глаза",
  MELEE: "Ближний бой",
  RANGED: "Дальний бой",
  DEFENSE: "Защита",
  UTILITY: "Полезное"
};

const socketLabels: Record<SocketType, string> = {
  CORE: "Ядро",
  FRONT: "Спереди",
  BACK: "Сзади",
  LEFT: "Слева",
  RIGHT: "Справа",
  TOP: "Сверху",
  UNDER: "Снизу"
};

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

const issueLabels: Record<string, string> = {
  NO_CHASSIS: "выбери шасси",
  NO_BRAIN: "добавь мозг",
  MULTIPLE_BRAINS: "оставь только один мозг",
  NO_METABOLISM: "добавь сердце или реактор",
  NO_LOCOMOTION: "добавь ноги или крылья",
  STATIC_LOCOMOTION: "убери движение у статичного шасси",
  SOCKET_MISMATCH: "орган не подходит к слоту",
  SOCKET_OCCUPIED: "освободи слот",
  MASS_LIMIT: "уменьши массу",
  BUDGET_EXCEEDED: "уменьши стоимость",
  ENERGY_NEGATIVE: "добавь источник энергии",
  CONTROL_OVERLOAD: "упрости управление",
  INVALID_BEHAVIOR: "проверь поведение",
  INVALID_NAME: "введи имя от 3 до 24 символов",
  INVALID_PART_VERSION: "убери устаревший орган",
  CHASSIS_INCOMPATIBLE: "выбери подходящее шасси",
  TOO_MANY_PARTS: "убери лишние органы"
};

const recommendedIds = ["GANGLION", "HEART", "SCUTTLER", "SLUGFOOT", "FACET", "MANDIBLE"];

interface LabScreenProps {
  catalog: Catalog;
  onSaved: (creature: CreatureView) => void;
  onBattle: (matchId: string) => void;
}

export default function LabScreen({ catalog, onSaved, onBattle }: LabScreenProps) {
  const initialTemplate =
    catalog.templates.find((template) => template.name === "Brawler") ??
    catalog.templates.find((template) => template.name === "Scout") ??
    catalog.templates[0];
  const initialGraph =
    catalog.behaviorGraphs.find((item) => item.presetId === initialTemplate.genome.behavior.presetId)?.graph ?? null;
  const [genome, setGenome] = useState<Genome>(() => {
    const clone = cloneGenome(initialTemplate.genome);
    clone.name = templateLabels[initialTemplate.name] ?? clone.name;
    clone.behavior = {
      presetId: clone.behavior.presetId,
      graph: initialGraph
    };
    return clone;
  });
  const [validation, setValidation] = useState<ValidationResult | null>(initialTemplate.validation);
  const [validating, setValidating] = useState(false);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState<PartCategory | "ALL">("ALL");
  const [mirror, setMirror] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [startingBattle, setStartingBattle] = useState(false);
  const [behaviorOpen, setBehaviorOpen] = useState(false);
  const [helpOpen, setHelpOpen] = useState(() => localStorage.getItem("iceja.labHelpV2Seen") !== "1");

  const partsById = useMemo(() => new Map(catalog.parts.map((part) => [part.id, part])), [catalog.parts]);
  const chassis = catalog.chassis.find((item) => item.id === genome.chassisId);
  const stats = validation?.stats;
  const graphForPreset = (presetId: string) =>
    catalog.behaviorGraphs.find((item) => item.presetId === presetId)?.graph ?? null;

  useEffect(() => {
    let cancelled = false;
    setValidating(true);
    const timer = window.setTimeout(() => {
      validateGenome(genome)
        .then((result) => {
          if (!cancelled) {
            setValidation(result);
          }
        })
        .catch(() => {
          if (!cancelled) {
            setMessage("Не удалось проверить существо. Проверь соединение.");
          }
        })
        .finally(() => {
          if (!cancelled) {
            setValidating(false);
          }
        });
    }, 180);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [genome]);

  useEffect(() => {
    if (message === null) {
      return;
    }
    const timer = window.setTimeout(() => setMessage(null), 5000);
    return () => window.clearTimeout(timer);
  }, [message]);

  useEffect(() => {
    localStorage.setItem("iceja.labHelpV2Seen", helpOpen ? "0" : "1");
  }, [helpOpen]);

  const hasBrain = hasFlag(genome, "BRAIN", partsById);
  const hasMetabolism = hasFlag(genome, "HEART", partsById) || hasFlag(genome, "METABOLIC", partsById);
  const hasLocomotion = genome.parts.some((placement) => {
    const part = partsById.get(placement.partId);
    return part !== undefined && part.traction > 0;
  });
  const hasSensor = genome.parts.some((placement) => {
    const part = partsById.get(placement.partId);
    return part !== undefined && part.visionRangeMm > 0;
  });
  const hasWeapon = genome.parts.some((placement) => partsById.get(placement.partId)?.weapon !== null);
  const staticChassis = chassis?.isStatic === true;
  const missingRoles: MissingRole[] = [];
  if (!hasBrain) missingRoles.push("brain");
  if (!hasMetabolism) missingRoles.push("metabolism");
  if (!hasLocomotion && !staticChassis) missingRoles.push("locomotion");
  if (!hasSensor) missingRoles.push("sensor");
  if (!hasWeapon) missingRoles.push("weapon");

  const nextPart = findNextPart(missingRoles[0], genome, chassis, mirror, catalog, partsById, stats);
  const nextHint = missingRoles.length === 0
    ? "Всё готово. Нажми «Сохранить существо»."
    : nextPart !== null
      ? `Следующий шаг: ${roleText(missingRoles[0])} — ${nextPart.name}.`
      : `Не хватает: ${missingRoles.map(roleText).join(", ")}. Но свободных слотов нет — убери лишний орган.`;

  const addableParts = catalog.parts
    .filter((part) => canAddPart(part, genome, chassis, mirror, catalog, partsById, stats).allowed)
    .sort((left, right) => partPriority(left) - partPriority(right) || left.cost - right.cost);

  const filteredParts = addableParts.filter((part) => {
    const categoryMatches = category === "ALL" || part.category === category;
    const needle = search.trim().toLowerCase();
    const searchMatches =
      needle === "" ||
      part.name.toLowerCase().includes(needle) ||
      part.id.toLowerCase().includes(needle) ||
      part.description.toLowerCase().includes(needle);
    return categoryMatches && searchMatches;
  });

  const friendlyIssues = unique(
    (validation?.errors ?? []).map((issue) => issueLabels[issue.code] ?? "проверь комплектацию")
  );
  const canSave = validation?.valid === true && !validating && !saving && !startingBattle;
  const canStartBattle = canSave;
  const cost = stats?.costBm ?? 0;
  const budgetPercent = Math.min(100, (cost / catalog.ruleset.creatureMaxCostBm) * 100);
  const speedMeters = stats === undefined ? 0 : (stats.speedMmPerTick * catalog.ruleset.tickRate) / 1000;
  const estimatedDps = stats === undefined
    ? 0
    : stats.weapons.reduce((total, weapon) => {
        const hitChance = (weapon.accuracy + stats.accuracyBonus) / 100;
        return total + (weapon.damage * hitChance) / (weapon.cooldownTicks / catalog.ruleset.tickRate);
      }, 0);
  const steps = [
    { label: "Основа", done: genome.name.trim().length >= 3 && chassis !== undefined },
    { label: "Мозг и сердце", done: hasBrain && hasMetabolism },
    { label: "Движение", done: staticChassis || hasLocomotion },
    { label: "Глаза и оружие", done: hasSensor && hasWeapon },
    { label: "Готово", done: canSave }
  ];

  function addPart(part: PartDefinition) {
    const check = canAddPart(part, genome, chassis, mirror, catalog, partsById, stats);
    if (!check.allowed) {
      setMessage(check.reason);
      return;
    }
    const shouldMirror = mirror && (part.socket === "LEFT" || part.socket === "RIGHT");
    setGenome((current) => addPartToGenome(current, part, shouldMirror));
    setMessage(null);
  }

  function removePart(index: number) {
    const check = canRemovePart(index, genome, chassis, partsById, stats);
    if (!check.allowed) {
      setMessage(check.reason);
      return;
    }
    setGenome((current) => ({
      ...current,
      parts: current.parts.filter((_, partIndex) => partIndex !== index)
    }));
    setMessage(null);
  }

  function adjustUpgrade(stat: UpgradeStat, delta: number) {
    const rule = catalog.upgrades.find((item) => item.stat === stat);
    if (rule === undefined) {
      return;
    }
    setGenome((current) => {
      const currentLevel = current.upgrades.find((upgrade) => upgrade.stat === stat)?.level ?? 0;
      const nextLevel = Math.max(0, Math.min(rule.maxLevel, currentLevel + delta));
      const otherUpgrades = current.upgrades.filter((upgrade) => upgrade.stat !== stat);
      return {
        ...current,
        upgrades: nextLevel === 0 ? otherUpgrades : [...otherUpgrades, { stat, level: nextLevel }]
      };
    });
  }

  function levelOf(stat: UpgradeStat): number {
    return genome.upgrades.find((upgrade) => upgrade.stat === stat)?.level ?? 0;
  }

  function loadTemplate(templateGenome: Genome) {
    const clone = cloneGenome(templateGenome);
    clone.name = templateLabels[templateGenome.name] ?? clone.name;
    clone.behavior = {
      presetId: clone.behavior.presetId,
      graph: graphForPreset(clone.behavior.presetId)
    };
    setGenome(clone);
    const template = catalog.templates.find((item) => item.genome.name === templateGenome.name);
    setValidation(template?.validation ?? null);
    setMessage(`Загружен шаблон «${templateLabels[templateGenome.name] ?? templateGenome.name}».`);
  }

  function changeChassis(chassisId: string) {
    if (genome.parts.length > 0 && !window.confirm("Сменить шасси? Все органы будут убраны.")) {
      return;
    }
    setGenome((current) => ({ ...current, chassisId, parts: [] }));
    setMessage("Шасси изменено. Собери существо заново или нажми «Собрать базовое».");
  }

  function startBlank() {
    setGenome({
      ...genome,
      name: "Моя тварь",
      parts: [],
      upgrades: [],
      behavior: {
        presetId: "BERSERK",
        graph: graphForPreset("BERSERK")
      }
    });
    setMessage("Пустое существо. Нажми «Собрать базовое» или добавь органы вручную.");
  }

  function buildBase() {
    let next = cloneGenome(genome);
    const addRole = (role: MissingRole) => {
      const part = findNextPart(role, next, chassis, mirror, catalog, partsById, undefined);
      if (part !== null) {
        next = addPartToGenome(next, part, mirror && (part.socket === "LEFT" || part.socket === "RIGHT"));
      }
    };
    if (!hasFlag(next, "BRAIN", partsById)) addRole("brain");
    if (!(hasFlag(next, "HEART", partsById) || hasFlag(next, "METABOLIC", partsById))) addRole("metabolism");
    if (!chassis?.isStatic && !hasLocomotionIn(next, partsById)) addRole("locomotion");
    if (!hasSensorIn(next, partsById)) addRole("sensor");
    if (!hasWeaponIn(next, partsById)) addRole("weapon");
    if (next.name.trim().length < 3) {
      next.name = "Моя тварь";
    }
    setGenome(next);
    setMessage("Базовые органы добавлены. Можно сохранять или настроить детали.");
  }

  async function quickBattle() {
    unlockBattleAudio();
    if (!canStartBattle) {
      setMessage("Сначала заверши сборку: добавь недостающие органы.");
      return;
    }
    setStartingBattle(true);
    setMessage(null);
    try {
      const creature = await createCreature(genome);
      const map = catalog.maps.find((item) => item.id === "duel-arena") ?? catalog.maps[0];
      const army = await createArmy("Быстрый бой", [
        { creatureId: creature.id, x: map.spawnA.x + 4_000, y: map.spawnA.y }
      ]);
      const match = await createMatch(army.id, map.id);
      onBattle(match.id);
    } catch (reason: unknown) {
      setMessage(reason instanceof Error ? reason.message : "Не удалось начать бой");
    } finally {
      setStartingBattle(false);
    }
  }

  async function saveCreature() {
    setSaving(true);
    setMessage(null);
    try {
      const creature = await createCreature(genome);
      onSaved(creature);
    } catch (reason: unknown) {
      setMessage(reason instanceof Error ? reason.message : "Не удалось сохранить существо");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="lab-simple">
      <header className="lab-intro">
        <div>
          <span className="eyebrow">Лаборатория</span>
          <h1>Собери существо</h1>
          <p>
            По умолчанию уже собран боец. Нажми «Сразу в бой» или измени органы, если хочешь собрать своего.
          </p>
        </div>
        <div className="lab-intro-actions">
          <button
            className="primary"
            onClick={() => void quickBattle()}
            disabled={!canStartBattle}
            title={canStartBattle ? "Создать существо, армию и сразу открыть бой" : "Сначала заверши сборку"}
          >
            {startingBattle ? "Готовим бой…" : canStartBattle ? "Сразу в бой" : "Заверши сборку"}
          </button>
          <button className="secondary" onClick={() => setHelpOpen(true)}>
            Как это работает
          </button>
        </div>
      </header>

      <div className="lab-steps">
        {steps.map((step, index) => (
          <div key={step.label} className={step.done ? "lab-step done" : "lab-step"}>
            <span className="lab-step-number">{step.done ? "✓" : index + 1}</span>
            <span>{step.label}</span>
          </div>
        ))}
      </div>

      <div className="lab-simple-grid">
        <aside className="panel add-panel">
          <div className="panel-header">
            <h2>Что добавить</h2>
            <span className="muted">{filteredParts.length}</span>
          </div>
          <div className="next-hint" title={nextHint}>
            <span>{nextHint}</span>
            {nextPart !== null && (
              <button className="primary small" onClick={() => addPart(nextPart)}>
                + {nextPart.name}
              </button>
            )}
          </div>
          <input
            className="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Найти орган…"
          />
          <div className="chip-row">
            <button className={category === "ALL" ? "chip active" : "chip"} onClick={() => setCategory("ALL")}>
              Все
            </button>
            {categories.map((item) => (
              <button
                key={item}
                className={category === item ? "chip active" : "chip"}
                onClick={() => setCategory(item)}
              >
                {categoryLabels[item]}
              </button>
            ))}
          </div>
          <p className="muted small-text">Показаны только органы, которые подходят прямо сейчас.</p>
          <div className="part-list">
            {filteredParts.length === 0 && (
              <p className="muted">Подходящих органов нет. Убери лишний орган или измени шасси.</p>
            )}
            {filteredParts.map((part) => (
              <button
                key={part.id}
                className="part-card addable"
                onClick={() => addPart(part)}
                title={`Добавить «${part.name}» в слот «${socketLabels[part.socket]}»`}
              >
                <span className={`part-color ${part.category.toLowerCase()}`} />
                <span className="part-name">
                  {part.name}
                  {part.flags.includes("BRAIN") && <span className="part-badge">мозг</span>}
                  {part.flags.includes("HEART") && <span className="part-badge">сердце</span>}
                </span>
                <span className="part-meta">
                  {socketLabels[part.socket]} · {part.cost} BM · {part.massKg} кг
                </span>
                <span className="part-description">{part.description}</span>
                <span className="part-add">+ Добавить</span>
              </button>
            ))}
          </div>
          <label className="toggle" title="Парные конечности добавляются слева и справа">
            <input type="checkbox" checked={mirror} onChange={(event) => setMirror(event.target.checked)} />
            Симметричные конечности
          </label>
        </aside>

        <section className="panel build-panel">
          <div className="build-quick-actions">
            <button className="secondary small" onClick={startBlank} title="Очистить сборку и начать заново">
              Новое существо
            </button>
            <button className="secondary small" onClick={buildBase} title="Добавить обязательные органы автоматически">
              Собрать базовое
            </button>
          </div>
          <div className="build-top">
            <label>
              Имя существа
              <input
                value={genome.name}
                maxLength={24}
                onChange={(event) => setGenome({ ...genome, name: event.target.value })}
                placeholder="Например, Шип"
              />
            </label>
            <label>
              Шасси
              <select value={genome.chassisId} onChange={(event) => changeChassis(event.target.value)}>
                {catalog.chassis.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name} · {item.cost} BM
                  </option>
                ))}
              </select>
            </label>
            <label>
              Готовый шаблон
              <select
                value=""
                onChange={(event) => {
                  const template = catalog.templates.find((item) => item.genome.name === event.target.value);
                  if (template !== undefined) {
                    loadTemplate(template.genome);
                  }
                }}
              >
                <option value="">Загрузить шаблон</option>
                {catalog.templates.map((template) => (
                  <option key={template.id} value={template.genome.name}>
                    {templateLabels[template.name] ?? template.name}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <div className="schematic-wrap simple">
            <CreatureSchematic
              genome={genome}
              chassis={chassis}
              partsById={partsById}
              onRemove={removePart}
            />
            <p className="hint">Нажми на орган на схеме, чтобы убрать его. Пустые кружки — свободные слоты.</p>
          </div>

          <div className="socket-pills">
            {chassis?.sockets.map((socket) => {
              const used = socketUsage(genome, socket.type);
              return (
                <span key={socket.type} className={used > socket.count ? "socket-pill full" : "socket-pill"}>
                  {socketLabels[socket.type]} {used}/{socket.count}
                </span>
              );
            })}
          </div>

          <details className="advanced">
            <summary>Поведение и улучшения (необязательно)</summary>
            <div className="advanced-body">
              <label>
                Характер
                <select
                  value={genome.behavior.presetId}
                  onChange={(event) =>
                    setGenome({
                      ...genome,
                      behavior: {
                        presetId: event.target.value,
                        graph: graphForPreset(event.target.value)
                      }
                    })
                  }
                >
                  {catalog.behaviorPresets.map((preset) => (
                    <option key={preset.id} value={preset.id}>
                      {preset.name}
                    </option>
                  ))}
                </select>
              </label>
              <button className="secondary" onClick={() => setBehaviorOpen(true)}>
                Настроить граф поведения
              </button>
              <div className="upgrade-grid">
                {catalog.upgrades.map((rule) => {
                  const level = levelOf(rule.stat);
                  return (
                    <div key={rule.stat} className="upgrade-row">
                      <span>{upgradeLabel(rule.stat)}</span>
                      <div className="upgrade-controls">
                        <button
                          className="upgrade-button"
                          onClick={() => adjustUpgrade(rule.stat, -1)}
                          disabled={level === 0}
                          title={`Уменьшить улучшение «${upgradeLabel(rule.stat)}»`}
                        >
                          −
                        </button>
                        <strong>{level}</strong>
                        <button
                          className="upgrade-button"
                          onClick={() => adjustUpgrade(rule.stat, 1)}
                          disabled={level >= rule.maxLevel}
                          title={`Увеличить улучшение «${upgradeLabel(rule.stat)}»`}
                        >
                          +
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </details>
        </section>

        <aside className="panel summary-panel">
          <div className="panel-header">
            <h2>Результат</h2>
            <span className={validation?.valid === true ? "badge good" : "badge live"}>
              {validation?.valid === true ? "Готово" : "Собираем"}
            </span>
          </div>

          <div className="big-stats">
            <div>
              <span>Здоровье</span>
              <strong>{stats?.maxHp ?? 0}</strong>
            </div>
            <div>
              <span>Скорость</span>
              <strong>{speedMeters.toFixed(1)} м/с</strong>
            </div>
            <div>
              <span>Урон</span>
              <strong>{estimatedDps.toFixed(0)}</strong>
            </div>
            <div>
              <span>Цена</span>
              <strong>{cost} BM</strong>
            </div>
          </div>

          <div className="readiness">
            <h3>Что уже есть</h3>
            <ReadinessRow label="Мозг" done={hasBrain} hint="Нужен, чтобы существо принимало решения" />
            <ReadinessRow label="Сердце" done={hasMetabolism} hint="Даёт энергию" />
            <ReadinessRow label="Движение" done={staticChassis || hasLocomotion} hint="Ноги, лапы или крылья" />
            <ReadinessRow label="Глаза" done={hasSensor} hint="Помогают находить врага" />
            <ReadinessRow label="Оружие" done={hasWeapon} hint="Без него существо не атакует" />
          </div>

          {friendlyIssues.length > 0 && (
            <div className="notice">Чтобы сохранить: {friendlyIssues.join(" · ")}</div>
          )}
          {validating && <p className="muted">Проверяем…</p>}
          {message !== null && <div className="notice">{message}</div>}

          <div className="budget-block">
            <div className="budget-header">
              <span>Бюджет существа</span>
              <strong>
                {cost} / {catalog.ruleset.creatureMaxCostBm} BM
              </strong>
            </div>
            <div className="bar">
              <span
                className={budgetPercent > 90 ? "bar-fill danger" : "bar-fill"}
                style={{ width: `${budgetPercent}%` }}
              />
            </div>
          </div>

          <button
            className="primary wide"
            disabled={!canStartBattle}
            onClick={() => void quickBattle()}
            title={canStartBattle ? "Создать существо, армию и сразу открыть бой" : "Сначала заверши сборку"}
          >
            {startingBattle ? "Готовим бой…" : canStartBattle ? "Сразу в бой" : "Заверши сборку"}
          </button>
          <button
            className="secondary wide"
            disabled={!canSave}
            onClick={() => void saveCreature()}
            title="Сохранить существо в коллекцию и перейти к армии"
          >
            {saving ? "Сохраняем…" : "Сохранить в коллекцию"}
          </button>
          <button className="secondary wide" onClick={buildBase} title="Добавить недостающие базовые органы">
            Собрать базовое
          </button>

          <details className="advanced">
            <summary>Подробные характеристики</summary>
            <div className="advanced-body">
              <div className="stat-grid compact">
                <Stat label="Масса" value={`${stats?.massKg ?? 0} кг`} />
                <Stat label="Энергия" value={stats?.maxEnergy ?? 0} />
                <Stat label="Контроль" value={`${stats?.controlLoad ?? 0}/${stats?.controlCapacity ?? 0}`} />
                <Stat label="Обзор" value={`${((stats?.visionRangeMm ?? 0) / 1000).toFixed(0)} м`} />
              </div>
              <h3>Броня</h3>
              <div className="armor-grid">
                {stats !== undefined &&
                  Object.entries(stats.armor).map(([type, value]) => (
                    <div key={type} className="armor-row">
                      <span>{armorLabel(type)}</span>
                      <strong>{value}</strong>
                    </div>
                  ))}
              </div>
              <h3>Оружие</h3>
              {stats?.weapons.length === 0 && <p className="muted">Оружия пока нет.</p>}
              {stats?.weapons.map((weapon, index) => (
                <div key={`${weapon.partId}-${index}`} className="weapon-row">
                  <span>{partsById.get(weapon.partId)?.name ?? weapon.partId}</span>
                  <span className="muted">
                    {weapon.damage} урона · {(weapon.rangeMm / 1000).toFixed(1)} м
                  </span>
                </div>
              ))}
            </div>
          </details>
        </aside>
      </div>

      {helpOpen && (
        <div className="help-modal" role="dialog" aria-modal="true">
          <div className="help-card">
            <span className="eyebrow">Как это работает</span>
            <h2>Существо за четыре шага</h2>
            <ol>
              <li><strong>Выбери шасси.</strong> Это тело: от него зависят слоты и прочность.</li>
              <li><strong>Добавь мозг, сердце и движение.</strong> Без них существо не работает.</li>
              <li><strong>Добавь глаза и оружие.</strong> Глаза ищут врага, оружие наносит урон.</li>
              <li><strong>Нажми «Сохранить существо».</strong> Потом собери армию и запусти бой.</li>
            </ol>
            <p className="muted">
              Не хочешь ничего менять? Закрой подсказку и нажми «Сразу в бой». Если начнёшь с нуля,
              кнопка «Собрать базовое» добавит обязательные органы сама.
            </p>
            <button className="primary" onClick={() => setHelpOpen(false)}>
              Понятно
            </button>
          </div>
        </div>
      )}

      {behaviorOpen && (
        <BehaviorEditor
          open={behaviorOpen}
          presetId={genome.behavior.presetId}
          graph={genome.behavior.graph}
          presets={catalog.behaviorPresets}
          presetGraphs={catalog.behaviorGraphs}
          validationErrors={validation?.errors ?? []}
          onApply={(graph) =>
            setGenome((current) => ({
              ...current,
              behavior: { ...current.behavior, graph }
            }))
          }
          onClose={() => setBehaviorOpen(false)}
        />
      )}
    </div>
  );
}

type MissingRole = "brain" | "metabolism" | "locomotion" | "sensor" | "weapon";

type AddCheck = {
  allowed: boolean;
  reason: string;
};

function canAddPart(
  part: PartDefinition,
  genome: Genome,
  chassis: Catalog["chassis"][number] | undefined,
  mirror: boolean,
  catalog: Catalog,
  partsById: Map<string, PartDefinition>,
  stats: DerivedStats | undefined
): AddCheck {
  if (chassis === undefined) {
    return { allowed: false, reason: "Сначала выбери шасси." };
  }
  if (genome.parts.length >= 24) {
    return { allowed: false, reason: "У существа уже максимум органов." };
  }
  if (chassis.isStatic && part.category === "LOCOMOTION") {
    return { allowed: false, reason: "Статичному шасси не нужны ноги." };
  }
  if (part.flags.includes("FLIGHT") && !chassis.canFly && chassis.id !== "TITAN") {
    return { allowed: false, reason: "Крылья подходят только шасси «Летун» или «Титан»." };
  }
  if (part.flags.includes("BURROW") && !chassis.canBurrow && chassis.id !== "TITAN") {
    return { allowed: false, reason: "Копание подходит только шасси «Копатель» или «Титан»." };
  }
  if (part.flags.includes("BRAIN") && hasFlag(genome, "BRAIN", partsById)) {
    return { allowed: false, reason: "Мозг уже есть. У существа может быть только один мозг." };
  }

  const shouldMirror = mirror && (part.socket === "LEFT" || part.socket === "RIGHT");
  const usage = socketUsageMap(genome);
  const requiredSockets: SocketType[] = shouldMirror ? ["LEFT", "RIGHT"] : [part.socket];
  for (const socketType of requiredSockets) {
    const capacity = chassis.sockets.find((socket) => socket.type === socketType)?.count ?? 0;
    if ((usage.get(socketType) ?? 0) >= capacity) {
      return { allowed: false, reason: `Нет свободного слота «${socketLabels[socketType]}».` };
    }
  }

  if (stats !== undefined) {
    const copies = shouldMirror ? 2 : 1;
    if (stats.massKg + part.massKg * copies > chassis.hardMassKg) {
      return { allowed: false, reason: "Существо станет слишком тяжёлым для этого шасси." };
    }
    if (stats.costBm + part.cost * copies > catalog.ruleset.creatureMaxCostBm) {
      return { allowed: false, reason: "Существо станет слишком дорогим." };
    }
    const controlLoad = stats.controlLoad + part.controlCost * copies + (part.weapon?.complexity ?? 0) * copies;
    const controlCapacity = stats.controlCapacity + part.controlBonus * copies;
    if (controlLoad > controlCapacity + 50) {
      return { allowed: false, reason: "Управление станет слишком сложным." };
    }
    const regen = stats.regenMilliPerSecond + part.regenPerSecond * copies * 1000;
    const upkeep =
      stats.upkeepMilliPerSecond + part.massKg * copies * 20 + part.energyDrainMilliPerSecond * copies;
    if (regen - upkeep < -5_000) {
      return { allowed: false, reason: "Существу не хватит энергии." };
    }
  }

  return { allowed: true, reason: "" };
}

function canRemovePart(
  index: number,
  genome: Genome,
  chassis: Catalog["chassis"][number] | undefined,
  partsById: Map<string, PartDefinition>,
  stats: DerivedStats | undefined
): AddCheck {
  const placement = genome.parts[index];
  const part = placement === undefined ? undefined : partsById.get(placement.partId);
  if (part !== undefined && stats !== undefined) {
    const copies = placement?.mirrored === true ? 2 : 1;
    const controlLoad =
      stats.controlLoad - part.controlCost * copies - (part.weapon?.complexity ?? 0) * copies;
    const controlCapacity = stats.controlCapacity - part.controlBonus * copies;
    if (controlLoad > controlCapacity + 50) {
      return { allowed: false, reason: "Нельзя убрать: управление станет слишком сложным." };
    }
    const regen = stats.regenMilliPerSecond - part.regenPerSecond * copies * 1000;
    const upkeep =
      stats.upkeepMilliPerSecond - part.massKg * copies * 20 - part.energyDrainMilliPerSecond * copies;
    if (regen - upkeep < -5_000) {
      return { allowed: false, reason: "Нельзя убрать: существу не хватит энергии." };
    }
  }
  const remaining = genome.parts.filter((_, partIndex) => partIndex !== index);
  if (!remaining.some((placement) => partsById.get(placement.partId)?.flags.includes("BRAIN") === true)) {
    return { allowed: false, reason: "Нельзя убрать единственный мозг." };
  }
  const hasHeart = remaining.some((placement) => {
    const flags = partsById.get(placement.partId)?.flags ?? [];
    return flags.includes("HEART") || flags.includes("METABOLIC");
  });
  if (!hasHeart) {
    return { allowed: false, reason: "Нельзя убрать единственный источник энергии." };
  }
  const hasLegs = remaining.some((placement) => (partsById.get(placement.partId)?.traction ?? 0) > 0);
  if (chassis?.isStatic !== true && !hasLegs) {
    return { allowed: false, reason: "Нельзя убрать последнюю ногу или крылья." };
  }
  return { allowed: true, reason: "" };
}

function findNextPart(
  role: MissingRole | undefined,
  genome: Genome,
  chassis: Catalog["chassis"][number] | undefined,
  mirror: boolean,
  catalog: Catalog,
  partsById: Map<string, PartDefinition>,
  stats: DerivedStats | undefined
): PartDefinition | null {
  if (role === undefined) {
    return null;
  }
  const candidates = catalog.parts.filter((part) => {
    if (role === "brain") return part.flags.includes("BRAIN");
    if (role === "metabolism") return part.flags.includes("HEART") || part.flags.includes("METABOLIC");
    if (role === "locomotion") return part.traction > 0;
    if (role === "sensor") return part.visionRangeMm > 0;
    return part.weapon !== null;
  });
  const preferred = recommendedIds
    .map((id) => candidates.find((part) => part.id === id))
    .filter((part): part is PartDefinition => part !== undefined);
  const ordered = [...preferred, ...candidates.filter((part) => !preferred.includes(part))];
  return (
    ordered.find((part) => canAddPart(part, genome, chassis, mirror, catalog, partsById, stats).allowed) ??
    null
  );
}

function addPartToGenome(genome: Genome, part: PartDefinition, mirrored: boolean): Genome {
  return {
    ...genome,
    parts: [
      ...genome.parts,
      {
        partId: part.id,
        socketId: part.socket,
        offset: { x: 0, y: 0 },
        rotation: 0,
        mirrored
      }
    ]
  };
}

function socketUsageMap(genome: Genome): Map<SocketType, number> {
  const usage = new Map<SocketType, number>();
  genome.parts.forEach((placement) => {
    if (placement.mirrored && (placement.socketId === "LEFT" || placement.socketId === "RIGHT")) {
      usage.set("LEFT", (usage.get("LEFT") ?? 0) + 1);
      usage.set("RIGHT", (usage.get("RIGHT") ?? 0) + 1);
    } else {
      usage.set(placement.socketId, (usage.get(placement.socketId) ?? 0) + 1);
    }
  });
  return usage;
}

function socketUsage(genome: Genome, socketType: SocketType): number {
  return socketUsageMap(genome).get(socketType) ?? 0;
}

function hasFlag(genome: Genome, flag: "BRAIN" | "HEART" | "METABOLIC", partsById: Map<string, PartDefinition>): boolean {
  return genome.parts.some((placement) => partsById.get(placement.partId)?.flags.includes(flag) === true);
}

function hasLocomotionIn(genome: Genome, partsById: Map<string, PartDefinition>): boolean {
  return genome.parts.some((placement) => (partsById.get(placement.partId)?.traction ?? 0) > 0);
}

function hasSensorIn(genome: Genome, partsById: Map<string, PartDefinition>): boolean {
  return genome.parts.some((placement) => (partsById.get(placement.partId)?.visionRangeMm ?? 0) > 0);
}

function hasWeaponIn(genome: Genome, partsById: Map<string, PartDefinition>): boolean {
  return genome.parts.some((placement) => partsById.get(placement.partId)?.weapon !== null);
}

function roleText(role: MissingRole): string {
  return {
    brain: "мозг",
    metabolism: "сердце",
    locomotion: "движение",
    sensor: "глаза",
    weapon: "оружие"
  }[role];
}

function partPriority(part: PartDefinition): number {
  const index = recommendedIds.indexOf(part.id);
  return index === -1 ? 100 : index;
}

function upgradeLabel(stat: UpgradeStat): string {
  return {
    HP: "Здоровье",
    DAMAGE: "Урон",
    ARMOR: "Броня",
    SPEED: "Скорость",
    ENERGY: "Энергия",
    ACCURACY: "Точность",
    CONTROL: "Контроль"
  }[stat];
}

function armorLabel(type: string): string {
  return {
    kinetic: "Кинетика",
    piercing: "Пробитие",
    thermal: "Термо",
    acid: "Кислота",
    electric: "Электро",
    toxic: "Яд"
  }[type] ?? type;
}

function unique(values: string[]): string[] {
  return Array.from(new Set(values));
}

function ReadinessRow({ label, done, hint }: { label: string; done: boolean; hint: string }) {
  return (
    <div className={done ? "readiness-row done" : "readiness-row"} title={hint}>
      <span className="readiness-mark">{done ? "✓" : "—"}</span>
      <span>{label}</span>
      <span className="muted">{done ? "есть" : hint}</span>
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

function cloneGenome(genome: Genome): Genome {
  return JSON.parse(JSON.stringify(genome)) as Genome;
}
