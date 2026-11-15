import { useEffect, useMemo, useState } from "react";
import { createCreature, validateGenome } from "../../api";
import type {
  Catalog,
  CreatureView,
  Genome,
  PartCategory,
  PartDefinition,
  ValidationIssue,
  ValidationResult
} from "../../types";
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

interface LabScreenProps {
  catalog: Catalog;
  onSaved: (creature: CreatureView) => void;
}

export default function LabScreen({ catalog, onSaved }: LabScreenProps) {
  const initialTemplate = catalog.templates.find((template) => template.name === "Scout") ?? catalog.templates[0];
  const [genome, setGenome] = useState<Genome>(() => cloneGenome(initialTemplate.genome));
  const [validation, setValidation] = useState<ValidationResult | null>(initialTemplate.validation);
  const [validating, setValidating] = useState(false);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState<PartCategory | "ALL">("ALL");
  const [mirror, setMirror] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const partsById = useMemo(() => new Map(catalog.parts.map((part) => [part.id, part])), [catalog.parts]);
  const chassis = catalog.chassis.find((item) => item.id === genome.chassisId);

  useEffect(() => {
    let cancelled = false;
    setValidating(true);
    const timer = window.setTimeout(() => {
      validateGenome(genome)
        .then((result) => {
          if (!cancelled) {
            setValidation(result);
            setMessage(null);
          }
        })
        .catch((reason: unknown) => {
          if (!cancelled) {
            setMessage(reason instanceof Error ? reason.message : "Validation failed");
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

  const filteredParts = catalog.parts.filter((part) => {
    const categoryMatches = category === "ALL" || part.category === category;
    const needle = search.trim().toLowerCase();
    const searchMatches =
      needle === "" ||
      part.name.toLowerCase().includes(needle) ||
      part.id.toLowerCase().includes(needle) ||
      part.description.toLowerCase().includes(needle);
    return categoryMatches && searchMatches;
  });

  function addPart(part: PartDefinition) {
    const shouldMirror = mirror && (part.socket === "LEFT" || part.socket === "RIGHT");
    setGenome((current) => ({
      ...current,
      parts: [
        ...current.parts,
        {
          partId: part.id,
          socketId: part.socket,
          offset: { x: 0, y: 0 },
          rotation: 0,
          mirrored: shouldMirror
        }
      ]
    }));
  }

  function removePart(index: number) {
    setGenome((current) => ({
      ...current,
      parts: current.parts.filter((_, partIndex) => partIndex !== index)
    }));
  }

  function loadTemplate(templateGenome: Genome) {
    setGenome(cloneGenome(templateGenome));
    const template = catalog.templates.find((item) => item.genome.name === templateGenome.name);
    setValidation(template?.validation ?? null);
  }

  async function saveCreature() {
    setSaving(true);
    setMessage(null);
    try {
      const creature = await createCreature(genome);
      onSaved(creature);
    } catch (reason: unknown) {
      setMessage(reason instanceof Error ? reason.message : "Cannot save creature");
    } finally {
      setSaving(false);
    }
  }

  const stats = validation?.stats;
  const cost = stats?.costBm ?? 0;
  const budgetPercent = Math.min(100, (cost / catalog.ruleset.creatureMaxCostBm) * 100);
  const speedMeters = stats === undefined ? 0 : (stats.speedMmPerTick * catalog.ruleset.tickRate) / 1000;

  return (
    <div className="lab-layout">
      <aside className="panel catalog-panel">
        <div className="panel-header">
          <h2>Catalog</h2>
          <span className="muted">{filteredParts.length} parts</span>
        </div>
        <input
          className="search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Search organs…"
        />
        <div className="chip-row">
          <button className={category === "ALL" ? "chip active" : "chip"} onClick={() => setCategory("ALL")}>
            ALL
          </button>
          {categories.map((item) => (
            <button
              key={item}
              className={category === item ? "chip active" : "chip"}
              onClick={() => setCategory(item)}
            >
              {item}
            </button>
          ))}
        </div>
        <div className="part-list">
          {filteredParts.map((part) => (
            <button key={part.id} className="part-card" onClick={() => addPart(part)}>
              <span className={`part-color ${part.category.toLowerCase()}`} />
              <span className="part-name">{part.name}</span>
              <span className="part-meta">
                {part.socket} · {part.cost} BM · {part.massKg} kg
              </span>
              <span className="part-description">{part.description}</span>
            </button>
          ))}
        </div>
      </aside>

      <section className="stage-panel">
        <div className="stage-header">
          <div>
            <span className="eyebrow">Bio-constructor</span>
            <h2>{genome.name || "Unnamed creature"}</h2>
          </div>
          <div className="stage-actions">
            <label className="toggle">
              <input type="checkbox" checked={mirror} onChange={(event) => setMirror(event.target.checked)} />
              Mirror limbs
            </label>
            <button
              className="primary"
              disabled={validation?.valid !== true || saving}
              onClick={() => void saveCreature()}
            >
              {saving ? "Saving…" : "Save creature"}
            </button>
          </div>
        </div>

        <div className="constructor-grid">
          <div className="schematic-wrap">
            <CreatureSchematic
              genome={genome}
              chassis={chassis}
              partsById={partsById}
              onRemove={removePart}
            />
            <p className="hint">Click a placed organ to remove it.</p>
          </div>

          <div className="form-stack">
            <label>
              Name
              <input
                value={genome.name}
                maxLength={24}
                onChange={(event) => setGenome({ ...genome, name: event.target.value })}
              />
            </label>
            <label>
              Chassis
              <select
                value={genome.chassisId}
                onChange={(event) => setGenome({ ...genome, chassisId: event.target.value })}
              >
                {catalog.chassis.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name} · {item.sizeClass} · {item.cost} BM
                  </option>
                ))}
              </select>
            </label>
            <label>
              Behavior
              <select
                value={genome.behavior.presetId}
                onChange={(event) =>
                  setGenome({
                    ...genome,
                    behavior: { presetId: event.target.value, graph: null }
                  })
                }
              >
                {catalog.behaviorPresets.map((preset) => (
                  <option key={preset.id} value={preset.id}>
                    {preset.name} · {preset.cp} CP
                  </option>
                ))}
              </select>
            </label>
            <div className="template-block">
              <span className="label">Templates</span>
              <div className="template-grid">
                {catalog.templates.map((template) => (
                  <button
                    key={template.id}
                    className="template-button"
                    onClick={() => loadTemplate(template.genome)}
                    title={template.genome.name}
                  >
                    {template.name}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>

        <div className="budget-block">
          <div className="budget-header">
            <span>Creature budget</span>
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

        <div className="validation-block">
          <ValidationGroup title="Errors" items={validation?.errors ?? []} kind="error" />
          <ValidationGroup title="Warnings" items={validation?.warnings ?? []} kind="warning" />
          {validating && <p className="muted">Analyzing genome…</p>}
          {message !== null && <p className="error-text">{message}</p>}
        </div>
      </section>

      <aside className="panel stats-panel">
        <div className="panel-header">
          <h2>Characteristics</h2>
          <span className={validation?.valid === true ? "badge good" : "badge bad"}>
            {validation?.valid === true ? "VALID" : "INVALID"}
          </span>
        </div>

        <div className="stat-grid">
          <Stat label="HP" value={stats?.maxHp ?? 0} />
          <Stat label="Mass" value={`${stats?.massKg ?? 0} kg`} />
          <Stat label="Speed" value={`${speedMeters.toFixed(2)} m/s`} />
          <Stat label="Energy" value={stats?.maxEnergy ?? 0} />
          <Stat label="Control" value={`${stats?.controlLoad ?? 0}/${stats?.controlCapacity ?? 0}`} />
          <Stat label="Vision" value={`${((stats?.visionRangeMm ?? 0) / 1000).toFixed(0)} m`} />
          <Stat label="Turn" value={`${stats?.turnRateDegPerSecond ?? 0}°/s`} />
          <Stat label="Radius" value={`${((stats?.radiusMm ?? 0) / 1000).toFixed(2)} m`} />
        </div>

        <h3>Armor</h3>
        <div className="armor-grid">
          {stats !== undefined &&
            Object.entries(stats.armor).map(([type, value]) => (
              <div key={type} className="armor-row">
                <span>{type}</span>
                <strong>{value}</strong>
              </div>
            ))}
        </div>

        <h3>Weapons</h3>
        <div className="weapon-list">
          {stats?.weapons.length === 0 && <p className="muted">No weapons installed.</p>}
          {stats?.weapons.map((weapon, index) => (
            <div key={`${weapon.partId}-${index}`} className="weapon-row">
              <span>{partsById.get(weapon.partId)?.name ?? weapon.partId}</span>
              <span className="muted">
                {weapon.damage} {weapon.damageType} · {(weapon.rangeMm / 1000).toFixed(1)} m
              </span>
            </div>
          ))}
        </div>

        <h3>Parts</h3>
        <div className="socket-list">
          {stats?.sockets.map((socket) => (
            <div key={socket.type} className="socket-row">
              <span>{socket.type}</span>
              <span className={socket.used > socket.capacity ? "error-text" : "muted"}>
                {socket.used}/{socket.capacity}
              </span>
            </div>
          ))}
        </div>
      </aside>
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

function ValidationGroup({
  title,
  items,
  kind
}: {
  title: string;
  items: ValidationIssue[];
  kind: "error" | "warning";
}) {
  if (items.length === 0) {
    return null;
  }
  return (
    <div className={`validation-group ${kind}`}>
      <h3>
        {title} <span>{items.length}</span>
      </h3>
      {items.map((item) => (
        <p key={`${item.code}-${item.nodeId}-${item.message}`}>
          <code>{item.code}</code> {item.message}
        </p>
      ))}
    </div>
  );
}

function cloneGenome(genome: Genome): Genome {
  return JSON.parse(JSON.stringify(genome)) as Genome;
}
