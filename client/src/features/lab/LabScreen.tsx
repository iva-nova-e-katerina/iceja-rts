import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { createArmy, createCreature, createMatch, listCreatures, validateGenome } from "../../api";
import { nextCreatureName } from "../../utils/creatureName";
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
  NO_WEAPON: "добавь оружие",
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
    clone.name = nextCreatureName(templateLabels[initialTemplate.name] ?? clone.name);
    clone.behavior = {
      presetId: clone.behavior.presetId,
      graph: initialGraph
    };
    return clone;
  });
  const [validation, setValidation] = useState<ValidationResult | null>(initialTemplate.validation);
  const [validating, setValidating] = useState(false);
  const [savedTemplates, setSavedTemplates] = useState<CreatureView[]>([]);
  const [search, setSearch] = useState("");
  const [partCategory, setPartCategory] = useState<PartCategory>("SENSOR");
  const [selectedSlot, setSelectedSlot] = useState<{ socket: SocketType; index: number } | null>(null);
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

  useEffect(() => {
    let cancelled = false;
    listCreatures()
      .then((creatures) => {
        if (!cancelled) {
          setSavedTemplates(creatures.filter((creature) => !creature.isTemplate));
        }
      })
      .catch(() => {
        if (!cancelled) {
          setMessage("Не удалось загрузить мои шаблоны.");
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const hasBrain = hasFlag(genome, "BRAIN", partsById);
  const hasMetabolism = hasFlag(genome, "HEART", partsById) || hasFlag(genome, "METABOLIC", partsById);
  const hasLocomotion = genome.parts.some((placement) => (partsById.get(placement.partId)?.traction ?? 0) > 0);
  const hasSensor = genome.parts.some((placement) => (partsById.get(placement.partId)?.visionRangeMm ?? 0) > 0);
  const hasWeapon = genome.parts.some((placement) => partsById.get(placement.partId)?.weapon !== null);
  const staticChassis = chassis?.isStatic === true;

  const addableParts = catalog.parts
    .filter((part) => canAddPart(part, genome, chassis, mirror, catalog, partsById, stats).allowed)
    .sort((left, right) => partPriority(left) - partPriority(right) || left.cost - right.cost);
  const matchesSearch = (part: PartDefinition): boolean => {
    const needle = search.trim().toLowerCase();
    return (
      needle === "" ||
      part.name.toLowerCase().includes(needle) ||
      part.id.toLowerCase().includes(needle) ||
      part.description.toLowerCase().includes(needle)
    );
  };
  const socketCompatible = (part: PartDefinition, socket: SocketType): boolean => {
    if (socket === "RIGHT") {
      return mirror && part.socket === "LEFT";
    }
    if (socket === "LEFT") {
      return part.socket === "LEFT" || part.socket === "RIGHT";
    }
    return part.socket === socket;
  };
  const filteredParts = selectedSlot === null
    ? addableParts.filter((part) => part.category === partCategory && matchesSearch(part))
    : addableParts.filter(
        (part) => socketCompatible(part, selectedSlot.socket) && matchesSearch(part)
      );
  const slotCards = chassis === undefined ? [] : buildSlotCards(genome, chassis, partsById);
  const freeSlotCards = slotCards.filter((card) => card.placementIndex === null);
  const partCategories: PartCategory[] = [
    "CORE",
    "LOCOMOTION",
    "SENSOR",
    "MELEE",
    "RANGED",
    "DEFENSE",
    "UTILITY"
  ];

  const friendlyIssues = unique(
    (validation?.errors ?? []).map((issue) => issueLabels[issue.code] ?? "проверь комплектацию")
  );
  const canSave = validation?.valid === true && !validating && !saving && !startingBattle;
  const canAttemptSave = !validating && !saving && !startingBattle;
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

  function addPart(part: PartDefinition) {
    const check = canAddPart(part, genome, chassis, mirror, catalog, partsById, stats);
    if (!check.allowed) {
      setMessage(check.reason);
      return;
    }
    const shouldMirror = mirror && (part.socket === "LEFT" || part.socket === "RIGHT");
    const targetSocket = selectedSlot?.socket ?? part.socket;
    const socketForPlacement = shouldMirror ? part.socket : targetSocket;
    const slotIndex = shouldMirror ? null : selectedSlot?.index ?? null;
    setGenome((current) => addPartToGenome(current, part, shouldMirror, socketForPlacement, slotIndex));
    setSelectedSlot(null);
    setMessage(null);
  }

  function removePart(index: number) {
    const check = canRemovePart();
    if (!check.allowed) {
      setMessage(check.reason);
      return;
    }
    const removedPart = partsById.get(genome.parts[index]?.partId ?? "");
    const remaining = genome.parts.filter((_, partIndex) => partIndex !== index);
    const lastLocomotion =
      (removedPart?.traction ?? 0) > 0 &&
      !remaining.some((placement) => (partsById.get(placement.partId)?.traction ?? 0) > 0);
    setGenome((current) => ({
      ...current,
      parts: current.parts.filter((_, partIndex) => partIndex !== index)
    }));
    if (lastLocomotion) {
      setPartCategory("LOCOMOTION");
      setMessage("Локомоция снята. Добавь крылья или новые ноги — без них существо не сможет двигаться.");
    } else {
      setMessage(null);
    }
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

  function applyGenome(templateGenome: Genome, templateValidation: ValidationResult | null, label: string) {
    const clone = cloneGenome(templateGenome);
    clone.name = nextCreatureName(label);
    clone.behavior = {
      presetId: clone.behavior.presetId,
      graph: graphForPreset(clone.behavior.presetId)
    };
    setGenome(clone);
    setValidation(templateValidation);
    setSelectedSlot(null);
    setMessage(`Загружен шаблон «${label}». Его можно менять и сохранить как новую тварь.`);
  }

  function selectTemplate(value: string) {
    if (value.startsWith("builtin:")) {
      const name = value.slice("builtin:".length);
      const template = catalog.templates.find((item) => item.name === name);
      if (template !== undefined) {
        loadTemplate(template.genome);
      }
      return;
    }
    if (value.startsWith("saved:")) {
      const id = value.slice("saved:".length);
      const template = savedTemplates.find((item) => item.id === id);
      if (template !== undefined) {
        loadSavedTemplate(template);
      }
    }
  }

  function loadTemplate(templateGenome: Genome) {
    const template = catalog.templates.find((item) => item.genome.name === templateGenome.name);
    applyGenome(
      templateGenome,
      template?.validation ?? null,
      templateLabels[templateGenome.name] ?? templateGenome.name
    );
  }

  function loadSavedTemplate(creature: CreatureView) {
    applyGenome(creature.genome, creature.validation, creature.genome.name);
  }

  function changeChassis(chassisId: string) {
    if (chassisId === genome.chassisId) {
      return;
    }
    if (genome.parts.length > 0 && !window.confirm("Сменить шасси? Все органы будут убраны.")) {
      return;
    }
    setGenome((current) => ({ ...current, chassisId, parts: [] }));
    setSelectedSlot(null);
    setMessage("Шасси изменено. Собери существо заново.");
  }

  function startBlank() {
    setSelectedSlot(null);
    setGenome({
      ...genome,
      name: nextCreatureName("Тварь"),
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
      next.name = nextCreatureName("Тварь");
    }
    setGenome(next);
    setSelectedSlot(null);
    setMessage("Базовые органы добавлены. Теперь можно доработать конструкцию и сохранить.");
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
    if (validation?.valid !== true) {
      setMessage(
        friendlyIssues.length > 0
          ? `Нельзя сохранить: ${friendlyIssues.join(" · ")}`
          : "Нельзя сохранить неполноценную тварь: проверь мозг, источник энергии и остальные обязательные органы."
      );
      return;
    }
    setSaving(true);
    setMessage(null);
    try {
      const creature = await createCreature(genome);
      setSavedTemplates((current) => [creature, ...current.filter((item) => item.id !== creature.id)]);
      onSaved(creature);
      setMessage(`Сохранено: ${creature.genome.name}`);
    } catch (reason: unknown) {
      setMessage(reason instanceof Error ? reason.message : "Не удалось сохранить существо");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="lab-workspace">
      <header className="lab-workspace-header">
        <div className="lab-workspace-title">
          <span className="eyebrow">Лаборатория</span>
          <h1>Собери существо</h1>
          <p>Шаблоны и органы слева, установленная конструкция в центре, настройки справа.</p>
        </div>
        <div className="lab-stat-chips">
          <span title="Здоровье">HP {stats?.maxHp ?? 0}</span>
          <span title="Оценка урона в секунду">Урон {estimatedDps.toFixed(0)}</span>
          <span title="Скорость">Скорость {speedMeters.toFixed(1)} м/с</span>
          <span title="Стоимость">Цена {cost} BM</span>
          <span className={validation?.valid === true ? "ready-chip done" : "ready-chip"}>
            {validation?.valid === true ? "Готово" : "Собираем"}
          </span>
        </div>
        <button className="secondary" onClick={() => setHelpOpen(true)}>
          Как это работает
        </button>
      </header>

      {message !== null && <div className="notice lab-message">{message}</div>}

      <div className="lab-three-columns">
        <aside className="panel lab-column lab-library">
          <div className="lab-column-head">
            <h2>Шаблоны</h2>
            <span className="muted">{catalog.templates.length + savedTemplates.length}</span>
          </div>
          <select
            className="lab-template-select"
            value=""
            onChange={(event) => {
              selectTemplate(event.target.value);
              event.currentTarget.value = "";
            }}
            title="Выбрать готовый шаблон как основу"
          >
            <option value="">Выбрать шаблон…</option>
            <optgroup label="Базовые">
              {catalog.templates.map((template) => (
                <option key={template.id} value={`builtin:${template.name}`}>
                  {templateLabels[template.name] ?? template.name} · {template.validation.stats.costBm} BM
                </option>
              ))}
            </optgroup>
            {savedTemplates.length > 0 && (
              <optgroup label="Мои шаблоны">
                {savedTemplates.map((creature) => (
                  <option key={creature.id} value={`saved:${creature.id}`}>
                    {creature.genome.name} · {creature.stats.costBm} BM
                  </option>
                ))}
              </optgroup>
            )}
          </select>
          <p className="muted small-text">Загруженный шаблон станет основой новой твари.</p>

          <div className="lab-column-head lab-organs-head">
            <h2>Органы</h2>
            <span className="muted">{filteredParts.length} доступно</span>
          </div>
          {selectedSlot === null ? (
            <div className="lab-category-tabs">
              {partCategories.map((category) => (
                <button
                  key={category}
                  className={partCategory === category ? "chip active" : "chip"}
                  onClick={() => setPartCategory(category)}
                >
                  {categoryLabels[category]}
                </button>
              ))}
            </div>
          ) : (
            <div className="lab-slot-selection">
              <span>
                Выбран слот: <strong>{socketLabels[selectedSlot.socket]}</strong>
              </span>
              <button className="icon-button" onClick={() => setSelectedSlot(null)} title="Сбросить слот">
                ×
              </button>
            </div>
          )}
          <input
            className="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Найти орган…"
          />
          <label className="toggle" title="Парные конечности добавляются слева и справа">
            <input type="checkbox" checked={mirror} onChange={(event) => setMirror(event.target.checked)} />
            Симметричные конечности
          </label>
          <PartList
            parts={filteredParts}
            onAdd={addPart}
            tickRate={catalog.ruleset.tickRate}
            emptyText={
              selectedSlot === null
                ? "Подходящих органов в этой категории нет. Освободи слот или выбери другую категорию."
                : "Для выбранного слота нет подходящих органов. Освободи слот или выбери другой."
            }
          />
        </aside>

        <section className="panel lab-column lab-construction">
          <div className="lab-column-head">
            <h2>Конструкция</h2>
            <span className="muted">{genome.parts.length} органов</span>
          </div>
          <p className="muted small-text">
            Установленные органы и их слоты. Убери орган, чтобы заменить его другим.
          </p>
          <div className="lab-free-slots">
            <div className="lab-section-title">
              <h3>Свободные слоты</h3>
              <span className="muted">{freeSlotCards.length}</span>
            </div>
            {freeSlotCards.length === 0 && <p className="muted small-text">Все слоты шасси заняты.</p>}
            <div className="lab-slot-grid">
              {freeSlotCards.map((card) => {
                const active =
                  selectedSlot?.socket === card.socket && selectedSlot.index === card.index;
                return (
                  <button
                    key={`${card.socket}-${card.index}`}
                    className={active ? "lab-slot-card free active" : "lab-slot-card free"}
                    onClick={() => setSelectedSlot({ socket: card.socket, index: card.index })}
                    title={`Выбрать свободный слот «${socketLabels[card.socket]}»`}
                  >
                    <span>Свободный слот</span>
                    <small>{socketLabels[card.socket]}</small>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="lab-section-title lab-occupied-title">
            <h3>Установленные органы</h3>
            <span className="muted">{genome.parts.length}</span>
          </div>
          <div className="lab-slot-groups">
            {chassis?.sockets.map((socket) => {
              const cards = slotCards.filter(
                (card) => card.socket === socket.type && card.placementIndex !== null
              );
              if (cards.length === 0) {
                return null;
              }
              return (
                <div key={socket.type} className="lab-slot-group">
                  <div className="lab-slot-group-head">
                    <span>{socketLabels[socket.type]}</span>
                    <span className="muted">{cards.length}/{socket.count}</span>
                  </div>
                  <div className="lab-slot-grid">
                    {cards.map((card) => {
                      const part = card.part;
                      return (
                        <div key={`${socket.type}-${card.index}`} className="lab-slot-card occupied">
                          <span className="installed-slot">{socketLabels[socket.type]}</span>
                          <strong>{part?.name ?? "Неизвестный орган"}</strong>
                          <span className="muted">
                            {part === undefined ? "" : `${part.cost} BM · ${part.massKg} кг`}
                            {card.mirrored ? " · ×2" : ""}
                          </span>
                          <button
                            className="icon-button"
                            onClick={() => removePart(card.placementIndex ?? 0)}
                            title="Убрать орган из слота"
                          >
                            ×
                          </button>
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
          <div className="lab-readiness">
            <ReadinessRow label="Мозг" done={hasBrain} hint="Нужен для решений" />
            <ReadinessRow label="Сердце" done={hasMetabolism} hint="Даёт энергию" />
            <ReadinessRow label="Движение" done={staticChassis || hasLocomotion} hint="Ноги или крылья" />
            <ReadinessRow label="Глаза" done={hasSensor} hint="Ищут врага" />
            <ReadinessRow label="Оружие" done={hasWeapon} hint="Наносит урон" />
          </div>
          <div className="lab-sockets">
            {chassis?.sockets.map((socket) => {
              const used = socketUsage(genome, socket.type);
              return (
                <span key={socket.type} className={used > socket.count ? "socket-pill full" : "socket-pill"}>
                  {socketLabels[socket.type]} {used}/{socket.count}
                </span>
              );
            })}
          </div>
          {friendlyIssues.length > 0 && (
            <div className="notice">Чтобы сохранить: {friendlyIssues.join(" · ")}</div>
          )}
          {validating && <p className="muted">Проверяем…</p>}
        </section>

        <aside className="panel lab-column lab-controls">
          <div className="lab-column-head">
            <h2>Настройки</h2>
            <span className="muted">
              {validating ? "проверяем" : canSave ? "готово" : "есть замечания"}
            </span>
          </div>
          <div className="lab-controls-scroll">
          <label>
            Имя нового шаблона
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

          <div className="lab-section">
            <h3>Поведение</h3>
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
          </div>

          <div className="lab-section">
            <h3>Улучшения</h3>
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

          <div className="budget-block">
            <div className="budget-header">
              <span>Бюджет существа</span>
              <strong>{cost} / {catalog.ruleset.creatureMaxCostBm} BM</strong>
            </div>
            <div className="bar">
              <span
                className={budgetPercent > 90 ? "bar-fill danger" : "bar-fill"}
                style={{ width: `${budgetPercent}%` }}
              />
            </div>
          </div>

          </div>

          <div className="lab-actions">
            <button className="primary wide" disabled={!canAttemptSave} onClick={() => void saveCreature()}>
              {saving ? "Сохраняем…" : "Сохранить тварь"}
            </button>
            <button className="primary wide" disabled={!canStartBattle} onClick={() => void quickBattle()}>
              {startingBattle ? "Готовим бой…" : "Сразу в бой"}
            </button>
            <button className="secondary wide" onClick={startBlank}>
              Новое существо
            </button>
            <button className="secondary wide" onClick={buildBase}>
              Собрать базовое
            </button>
          </div>
        </aside>
      </div>

      {helpOpen && (
        <div className="help-modal-backdrop" onClick={() => setHelpOpen(false)}>
          <div className="help-modal" onClick={(event) => event.stopPropagation()}>
            <span className="eyebrow">Лаборатория</span>
            <h2>Как собрать тварь</h2>
            <p className="muted">
              Слева выбери готовый шаблон или добавь органы. В центре видно, что уже установлено и в какой слот.
              Справа — имя, шасси, поведение, улучшения и кнопки.
            </p>
            <ul>
              <li><strong>Шаблоны.</strong> Загружают готовую конструкцию как основу для новой твари.</li>
              <li><strong>Органы.</strong> Добавляй их из любой категории; парные конечности можно ставить сразу с двух сторон.</li>
              <li><strong>Конструкция.</strong> Убирай установленные органы, чтобы заменить их другими.</li>
              <li><strong>Сохранение.</strong> «Сохранить тварь» добавляет новый шаблон в коллекцию, не уводя из лаборатории.</li>
            </ul>
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

interface LabSlotCard {
  socket: SocketType;
  index: number;
  placementIndex: number | null;
  part?: PartDefinition;
  mirrored: boolean;
}

function buildSlotCards(
  genome: Genome,
  chassis: Catalog["chassis"][number],
  partsById: Map<string, PartDefinition>
): LabSlotCard[] {
  const bySocket = new Map<
    SocketType,
    Array<{
      placementIndex: number;
      part?: PartDefinition;
      mirrored: boolean;
      slotIndex: number | null;
    }>
  >();
  genome.parts.forEach((placement, placementIndex) => {
    const part = partsById.get(placement.partId);
    const sockets = placement.mirrored ? ["LEFT", "RIGHT"] : [placement.socketId];
    sockets.forEach((socket) => {
      const entries = bySocket.get(socket as SocketType) ?? [];
      entries.push({
        placementIndex,
        part,
        mirrored: placement.mirrored,
        slotIndex: placement.slotIndex ?? null
      });
      bySocket.set(socket as SocketType, entries);
    });
  });
  const cards: LabSlotCard[] = [];
  chassis.sockets.forEach((socket) => {
    const entries = bySocket.get(socket.type) ?? [];
    const occupied: Array<(typeof entries)[number] | null> = Array(socket.count).fill(null);
    const overflow: typeof entries = [];
    entries.forEach((entry) => {
      const preferred = !entry.mirrored &&
        entry.slotIndex !== null &&
        entry.slotIndex >= 0 &&
        entry.slotIndex < socket.count
        ? entry.slotIndex
        : null;
      if (preferred !== null && occupied[preferred] === null) {
        occupied[preferred] = entry;
      } else {
        overflow.push(entry);
      }
    });
    overflow.forEach((entry) => {
      const free = occupied.findIndex((item) => item === null);
      if (free !== -1) {
        occupied[free] = entry;
      }
    });
    for (let index = 0; index < socket.count; index += 1) {
      const entry = occupied[index];
      cards.push({
        socket: socket.type,
        index,
        placementIndex: entry?.placementIndex ?? null,
        part: entry?.part,
        mirrored: entry?.mirrored ?? false
      });
    }
  });
  return cards;
}

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

function canRemovePart(): AddCheck {
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

function addPartToGenome(
  genome: Genome,
  part: PartDefinition,
  mirrored: boolean,
  socketId: SocketType = part.socket,
  slotIndex: number | null = null
): Genome {
  return {
    ...genome,
    parts: [
      ...genome.parts,
      {
        partId: part.id,
        socketId,
        offset: { x: 0, y: 0 },
        rotation: 0,
        mirrored,
        slotIndex
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


function unique(values: string[]): string[] {
  return Array.from(new Set(values));
}

function PartList({
  parts,
  onAdd,
  tickRate,
  emptyText
}: {
  parts: PartDefinition[];
  onAdd: (part: PartDefinition) => void;
  tickRate: number;
  emptyText: string;
}) {
  const [tooltip, setTooltip] = useState<{ part: PartDefinition; x: number; y: number } | null>(null);
  return (
    <>
      <div className="wizard-part-list">
        {parts.length === 0 && <p className="muted">{emptyText}</p>}
        {parts.map((part) => (
          <button
            key={part.id}
            className="part-card addable"
            onClick={() => onAdd(part)}
            onMouseEnter={(event) => setTooltip({ part, x: event.clientX, y: event.clientY })}
            onMouseMove={(event) => {
              if (tooltip !== null) {
                setTooltip({ part, x: event.clientX, y: event.clientY });
              }
            }}
            onMouseLeave={() => setTooltip(null)}
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
            <span className="part-visibility-row">
              <span
                className={
                  part.visual.visibility === "EXTERNAL"
                    ? "part-visibility external"
                    : "part-visibility internal"
                }
              >
                {part.visual.visibility === "EXTERNAL" ? "снаружи" : "внутри"}
              </span>
              <span className="part-add">+ Добавить</span>
            </span>
          </button>
        ))}
      </div>
      {tooltip !== null && createPortal(
        <div
          className="part-tooltip"
          style={{
            left: Math.min(tooltip.x + 14, window.innerWidth - 340),
            top: Math.min(tooltip.y + 14, window.innerHeight - 360)
          }}
        >
          <h4>{tooltip.part.name}</h4>
          <div className="part-tooltip-sub">
            {categoryLabels[tooltip.part.category]} · {socketLabels[tooltip.part.socket]} ·{" "}
            {tooltip.part.cost} BM · {tooltip.part.massKg} кг
          </div>
          <p>{tooltip.part.description}</p>
          {partDetailLines(tooltip.part, tickRate).length > 0 && (
            <ul>
              {partDetailLines(tooltip.part, tickRate).map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          )}
        </div>,
        document.body
      )}
    </>
  );
}

const damageTypeLabels: Record<string, string> = {
  KINETIC: "кинетика",
  PIERCING: "пронзание",
  THERMAL: "термика",
  ACID: "кислота",
  ELECTRIC: "электричество",
  TOXIC: "токсины"
};

const flagLabels: Record<string, string> = {
  BRAIN: "мозг",
  HEART: "сердце",
  METABOLIC: "метаболизм",
  NECTAR_CROP: "нектарный орган",
  SYMBIOTE: "симбионт",
  SCAVENGER: "падальщик",
  BURROW: "копание",
  FLIGHT: "полёт",
  CAMOUFLAGE: "маскировка",
  STICKY: "липкость",
  PHEROMONE: "феромоны",
  ADRENAL: "адреналин",
  NEUROLINK: "нейросвязь",
  TELEPATHIC: "телепатия",
  LURE: "приманка",
  OVIPOSITOR: "яйцеклад"
};

function partDetailLines(part: PartDefinition, tickRate: number): string[] {
  const lines: string[] = [];
  if (part.hpBonus > 0) lines.push(`Здоровье: +${part.hpBonus}`);
  if (part.energyBonus > 0) lines.push(`Энергия: +${part.energyBonus}`);
  if (part.regenPerSecond > 0) lines.push(`Регенерация энергии: +${part.regenPerSecond}/с`);
  if (part.visionRangeMm > 0) {
    lines.push(`Обзор: ${(part.visionRangeMm / 1000).toFixed(1)} м, ${part.visionAngleDeg}°`);
  }
  if (part.weapon !== null) {
    const weapon = part.weapon;
    const cooldownSeconds = weapon.cooldownTicks / Math.max(1, tickRate);
    const dps = weapon.damage / Math.max(0.1, cooldownSeconds);
    lines.push(`Оружие: ${weapon.ranged ? "дальний бой" : "ближний бой"}, ${weapon.damage} урона`);
    lines.push(`Дистанция: ${(weapon.rangeMm / 1000).toFixed(1)} м · ${cooldownSeconds.toFixed(1)} с · ${dps.toFixed(1)} DPS`);
    lines.push(`Точность: ${weapon.accuracy}% · тип: ${damageTypeLabels[weapon.damageType]}`);
    if (weapon.splashRadiusMm > 0) lines.push(`Взрыв: ${(weapon.splashRadiusMm / 1000).toFixed(1)} м`);
    if (weapon.dotDamagePerSecond > 0) lines.push(`Урон по времени: ${weapon.dotDamagePerSecond}/с`);
    if (weapon.penetration > 0) lines.push(`Пробитие брони: ${weapon.penetration}%`);
  }
  if (part.armor.kinetic > 0) lines.push(`Броня от кинетики: ${part.armor.kinetic}`);
  if (part.armor.piercing > 0) lines.push(`Броня от пронзания: ${part.armor.piercing}`);
  if (part.armor.thermal > 0) lines.push(`Броня от термики: ${part.armor.thermal}`);
  if (part.armor.acid > 0) lines.push(`Броня от кислоты: ${part.armor.acid}`);
  if (part.armor.electric > 0) lines.push(`Броня от электричества: ${part.armor.electric}`);
  if (part.armor.toxic > 0) lines.push(`Броня от токсинов: ${part.armor.toxic}`);
  if (part.evasionBonus > 0) lines.push(`Уклонение: +${part.evasionBonus}%`);
  if (part.accuracyBonus > 0) lines.push(`Точность: +${part.accuracyBonus}%`);
  if (part.traction > 0) lines.push(`Тяга: ${part.traction}`);
  if (part.speedMultiplierPct !== 100) lines.push(`Скорость: ${part.speedMultiplierPct}%`);
  if (part.turnMultiplierPct !== 100) lines.push(`Поворот: ${part.turnMultiplierPct}%`);
  if (part.energyDrainMilliPerSecond > 0) {
    lines.push(`Расход энергии: ${(part.energyDrainMilliPerSecond / 1000).toFixed(1)}/с`);
  }
  if (part.flags.length > 0) {
    lines.push(`Свойства: ${part.flags.map((flag) => flagLabels[flag] ?? flag).join(", ")}`);
  }
  return lines;
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


function cloneGenome(genome: Genome): Genome {
  return JSON.parse(JSON.stringify(genome)) as Genome;
}
