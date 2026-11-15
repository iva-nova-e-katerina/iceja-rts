import { useEffect, useRef, useState } from "react";
import { Application, Container, Graphics } from "pixi.js";
import type {
  CreatureRenderPart,
  CreatureRenderView,
  EntitySnapshot,
  GenomeSummary,
  MapDefinition,
  SimEvent,
  Snapshot
} from "../../types";

interface BattlefieldProps {
  map: MapDefinition;
  snapshot: Snapshot | null;
  genomes: GenomeSummary[];
  events?: SimEvent[];
  effectsEnabled?: boolean;
  smoothMotion?: boolean;
  selectedId: number | null;
  onSelect: (entityId: number | null) => void;
}

interface DragState {
  active: boolean;
  moved: boolean;
  pointerId: number;
  startX: number;
  startY: number;
  startWorldX: number;
  startWorldY: number;
}

interface EntityVisual {
  root: Container;
  bodyRoot: Container;
  simpleRoot: Container;
  selectionRing: Graphics;
  attackRing: Graphics;
  hpFill: Graphics;
  bodyRadius: number;
  ownerColor: number;
}

export default function Battlefield({
  map,
  snapshot,
  genomes,
  events = [],
  effectsEnabled = true,
  smoothMotion = true,
  selectedId,
  onSelect
}: BattlefieldProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const appRef = useRef<Application | null>(null);
  const worldRef = useRef<Container | null>(null);
  const hiveLayerRef = useRef<Container | null>(null);
  const corpseLayerRef = useRef<Container | null>(null);
  const entityLayerRef = useRef<Container | null>(null);
  const projectileLayerRef = useRef<Container | null>(null);
  const effectLayerRef = useRef<Container | null>(null);
  const entityVisualsRef = useRef<Map<number, EntityVisual>>(new Map());
  const corpseVisualsRef = useRef<Map<number, Container>>(new Map());
  const matchIdRef = useRef<string | null>(null);
  const batchedModeRef = useRef(false);
  const snapshotRef = useRef<Snapshot | null>(null);
  const currentSnapshotRef = useRef<Snapshot | null>(null);
  const previousEntitiesRef = useRef<Map<number, { x: number; y: number; angle: number }>>(new Map());
  const snapshotAtRef = useRef(0);
  const lastEventIdRef = useRef(0);
  const effectTimersRef = useRef<number[]>([]);
  const effectsEnabledRef = useRef(effectsEnabled);
  const smoothMotionRef = useRef(smoothMotion);
  const onSelectRef = useRef(onSelect);
  const [ready, setReady] = useState(false);

  onSelectRef.current = onSelect;
  effectsEnabledRef.current = effectsEnabled;
  smoothMotionRef.current = smoothMotion;
  snapshotRef.current = snapshot;

  useEffect(() => {
    const host = hostRef.current;
    if (host === null) {
      return;
    }
    let disposed = false;
    const app = new Application();
    const drag: DragState = {
      active: false,
      moved: false,
      pointerId: -1,
      startX: 0,
      startY: 0,
      startWorldX: 0,
      startWorldY: 0
    };

    const boot = async () => {
      await app.init({
        background: map.theme.background,
        antialias: true,
        resizeTo: host,
        resolution: Math.min(window.devicePixelRatio, 2),
        autoDensity: true
      });
      if (disposed) {
        app.destroy(true, { children: true });
        return;
      }
      host.appendChild(app.canvas as HTMLCanvasElement);

      const world = new Container();
      const gridLayer = new Container();
      const corpseLayer = new Container();
      const hiveLayer = new Container();
      const entityLayer = new Container();
      const projectileLayer = new Container();
      const effectLayer = new Container();
      world.addChild(gridLayer, corpseLayer, hiveLayer, entityLayer, projectileLayer, effectLayer);
      app.stage.addChild(world);
      appRef.current = app;
      worldRef.current = world;
      hiveLayerRef.current = hiveLayer;
      corpseLayerRef.current = corpseLayer;
      entityLayerRef.current = entityLayer;
      projectileLayerRef.current = projectileLayer;
      effectLayerRef.current = effectLayer;

      drawGrid(gridLayer, map);

      const fitScale = Math.min(app.screen.width / map.widthMm, app.screen.height / map.heightMm) * 0.94;
      const initialScale = fitScale * 1.25;
      world.scale.set(initialScale);
      world.position.set(
        (app.screen.width - map.widthMm * initialScale) / 2,
        (app.screen.height - map.heightMm * initialScale) / 2
      );

      const renderFrame = () => {
        const current = currentSnapshotRef.current;
        if (current !== null) {
          if (current.entities.length > 48) {
            return;
          }
          const elapsed = performance.now() - snapshotAtRef.current;
          const alpha = Math.max(0, Math.min(1, elapsed / 100));
          const detailed = current.entities.length <= 16
            ? world.scale.x >= 0.006
            : world.scale.x >= 0.0085;
          current.entities.forEach((entity) => {
            const visual = entityVisualsRef.current.get(entity.id);
            if (visual === undefined) {
              return;
            }
            const previous = smoothMotionRef.current
              ? previousEntitiesRef.current.get(entity.id)
              : undefined;
            if (previous === undefined) {
              visual.root.position.set(entity.x, entity.y);
              visual.bodyRoot.rotation = (entity.angle / 65_536) * Math.PI * 2;
            } else {
              visual.root.position.set(
                previous.x + (entity.x - previous.x) * alpha,
                previous.y + (entity.y - previous.y) * alpha
              );
              const angle = lerpAngle(previous.angle, entity.angle, alpha);
              visual.bodyRoot.rotation = (angle / 65_536) * Math.PI * 2;
            }
            visual.bodyRoot.visible = detailed;
            visual.simpleRoot.visible = !detailed;
          });
        }
        app.renderer.render(app.stage);
      };
      app.ticker.maxFPS = 30;
      app.ticker.add(renderFrame);
      app.ticker.start();

      const canvas = app.canvas as HTMLCanvasElement;

      const toWorld = (screenX: number, screenY: number): { x: number; y: number } => {
        const rect = canvas.getBoundingClientRect();
        const localX = screenX - rect.left;
        const localY = screenY - rect.top;
        return {
          x: (localX - world.x) / world.scale.x,
          y: (localY - world.y) / world.scale.y
        };
      };

      const onPointerDown = (event: PointerEvent) => {
        if (event.button !== 0 && event.button !== 1) {
          return;
        }
        drag.active = true;
        drag.moved = false;
        drag.pointerId = event.pointerId;
        drag.startX = event.clientX;
        drag.startY = event.clientY;
        drag.startWorldX = world.x;
        drag.startWorldY = world.y;
        canvas.setPointerCapture(event.pointerId);
      };

      const onPointerMove = (event: PointerEvent) => {
        if (!drag.active || event.pointerId !== drag.pointerId) {
          return;
        }
        const deltaX = event.clientX - drag.startX;
        const deltaY = event.clientY - drag.startY;
        if (Math.abs(deltaX) + Math.abs(deltaY) > 4) {
          drag.moved = true;
        }
        world.position.set(drag.startWorldX + deltaX, drag.startWorldY + deltaY);
        app.renderer.render(app.stage);
      };

      const onPointerUp = (event: PointerEvent) => {
        if (!drag.active) {
          return;
        }
        const wasClick = !drag.moved;
        drag.active = false;
        if (canvas.hasPointerCapture(event.pointerId)) {
          canvas.releasePointerCapture(event.pointerId);
        }
        if (wasClick) {
          const point = toWorld(event.clientX, event.clientY);
          const current = snapshotRef.current;
          if (current === null) {
            return;
          }
          const target = current.entities
            .map((entity) => {
              const dx = entity.x - point.x;
              const dy = entity.y - point.y;
              return { entity, distance: Math.sqrt(dx * dx + dy * dy) };
            })
            .filter(({ distance }) => distance < 5000)
            .sort((left, right) => left.distance - right.distance)[0];
          onSelectRef.current(target?.entity.id ?? null);
        }
      };

      const onWheel = (event: WheelEvent) => {
        event.preventDefault();
        const rect = canvas.getBoundingClientRect();
        const screenX = event.clientX - rect.left;
        const screenY = event.clientY - rect.top;
        const before = {
          x: (screenX - world.x) / world.scale.x,
          y: (screenY - world.y) / world.scale.y
        };
        const minimum = Math.min(app.screen.width / map.widthMm, app.screen.height / map.heightMm) * 0.5;
        const maximum = 0.25;
        const nextScale = Math.min(maximum, Math.max(minimum, world.scale.x * (event.deltaY < 0 ? 1.12 : 0.89)));
        world.scale.set(nextScale);
        world.position.set(screenX - before.x * nextScale, screenY - before.y * nextScale);
        app.renderer.render(app.stage);
      };

      canvas.addEventListener("pointerdown", onPointerDown);
      canvas.addEventListener("pointermove", onPointerMove);
      canvas.addEventListener("pointerup", onPointerUp);
      canvas.addEventListener("wheel", onWheel, { passive: false });

      const resizeObserver = new ResizeObserver(() => {
        world.position.set(
          (app.screen.width - map.widthMm * world.scale.x) / 2,
          (app.screen.height - map.heightMm * world.scale.y) / 2
        );
        app.renderer.render(app.stage);
      });
      resizeObserver.observe(host);

      const cleanup = () => {
        canvas.removeEventListener("pointerdown", onPointerDown);
        canvas.removeEventListener("pointermove", onPointerMove);
        canvas.removeEventListener("pointerup", onPointerUp);
        canvas.removeEventListener("wheel", onWheel);
        resizeObserver.disconnect();
      };
      (host as HTMLDivElement & { __cleanup?: () => void }).__cleanup = cleanup;
      app.renderer.render(app.stage);
      setReady(true);
    };

    void boot();

    return () => {
      disposed = true;
      const cleanup = (host as HTMLDivElement & { __cleanup?: () => void }).__cleanup;
      cleanup?.();
      effectTimersRef.current.forEach((timer) => window.clearTimeout(timer));
      effectTimersRef.current = [];
      appRef.current?.destroy(true, { children: true });
      appRef.current = null;
      worldRef.current = null;
      hiveLayerRef.current = null;
      corpseLayerRef.current = null;
      entityLayerRef.current = null;
      projectileLayerRef.current = null;
      effectLayerRef.current = null;
    };
  }, [map]);

  useEffect(() => {
    if (!ready || snapshot === null) {
      return;
    }
    const genomesById = new Map(genomes.map((genome) => [genome.genomeId, genome]));
    const hives = hiveLayerRef.current;
    const corpses = corpseLayerRef.current;
    const entities = entityLayerRef.current;
    const projectiles = projectileLayerRef.current;
    if (hives === null || corpses === null || entities === null || projectiles === null) {
      return;
    }
    clearLayer(hives);
    clearLayer(projectiles);

    if (snapshot.matchId !== matchIdRef.current) {
      clearLayer(entities);
      clearLayer(corpses);
      entityVisualsRef.current.clear();
      corpseVisualsRef.current.clear();
      previousEntitiesRef.current.clear();
      currentSnapshotRef.current = null;
      matchIdRef.current = snapshot.matchId;
    }

    const previous = new Map<number, { x: number; y: number; angle: number }>();
    currentSnapshotRef.current?.entities.forEach((entity) => {
      previous.set(entity.id, { x: entity.x, y: entity.y, angle: entity.angle });
    });
    previousEntitiesRef.current = previous;
    currentSnapshotRef.current = snapshot;
    snapshotAtRef.current = performance.now();

    const corpseVisuals = corpseVisualsRef.current;
    const seenCorpseIds = new Set<number>();
    (snapshot.corpses ?? []).forEach((corpse) => {
      let visual = corpseVisuals.get(corpse.id);
      if (visual === undefined) {
        visual = createCorpseVisual(corpse.owner);
        corpses.addChild(visual);
        corpseVisuals.set(corpse.id, visual);
      }
      visual.position.set(corpse.x, corpse.y);
      visual.alpha = Math.max(0.25, Math.min(1, corpse.remainingTicks / 900));
      seenCorpseIds.add(corpse.id);
    });
    for (const [corpseId, visual] of corpseVisuals) {
      if (!seenCorpseIds.has(corpseId)) {
        visual.destroy({ children: true });
        corpseVisuals.delete(corpseId);
      }
    }

    snapshot.hives.forEach((hive) => {
      const color = hive.owner === 0 ? 0x1f6f5c : 0x7a3d1e;
      const stroke = hive.owner === 0 ? 0x39e6b0 : 0xff9d5c;
      const body = new Graphics()
        .circle(hive.x, hive.y, 4200)
        .fill(color)
        .stroke({ width: 700, color: stroke });
      hives.addChild(body);
      const hpPercent = hive.maxHp === 0 ? 0 : hive.hp / hive.maxHp;
      const bar = new Graphics()
        .rect(hive.x - 4500, hive.y - 6500, 9000, 900)
        .fill(0x071012)
        .rect(hive.x - 4400, hive.y - 6400, 8800 * hpPercent, 700)
        .fill(stroke);
      hives.addChild(bar);
    });

    const batchMode =
      snapshot.entities.length > 48 && (worldRef.current?.scale.x ?? 0) < 0.0085;
    if (batchMode !== batchedModeRef.current) {
      clearLayer(entities);
      entityVisualsRef.current.clear();
      batchedModeRef.current = batchMode;
    }

    if (batchMode) {
      drawBatchedCreatures(entities, snapshot, genomesById, selectedId);
    } else {
    const entityVisuals = entityVisualsRef.current;
    const seenEntityIds = new Set<number>();
    snapshot.entities.forEach((entity) => {
      const genome = genomesById.get(entity.genomeId);
      const render = genome?.render;
      if (render === undefined) {
        return;
      }
      let visual = entityVisuals.get(entity.id);
      if (visual === undefined) {
        visual = createEntityVisual(entity, render);
        entities.addChild(visual.root);
        entityVisuals.set(entity.id, visual);
      }
      if (snapshot.entities.length > 48) {
        visual.root.position.set(entity.x, entity.y);
        visual.bodyRoot.rotation = (entity.angle / 65_536) * Math.PI * 2;
        const detailed = snapshot.entities.length <= 16
          ? (worldRef.current?.scale.x ?? 0) >= 0.006
          : (worldRef.current?.scale.x ?? 0) >= 0.0085;
        visual.bodyRoot.visible = detailed;
        visual.simpleRoot.visible = !detailed;
      }
      visual.selectionRing.visible = entity.id === selectedId;
      visual.attackRing.visible = entity.state === 2;
      const hpPercent = entity.maxHp === 0 ? 0 : entity.hp / entity.maxHp;
      const bodyRadius = visual.bodyRadius;
      visual.hpFill
        .clear()
        .rect(
          -bodyRadius * 0.9,
          -bodyRadius * 1.45,
          bodyRadius * 1.8 * hpPercent,
          bodyRadius * 0.2
        )
        .fill(visual.ownerColor);
      seenEntityIds.add(entity.id);
    });
    for (const [entityId, visual] of entityVisuals) {
      if (!seenEntityIds.has(entityId)) {
        visual.root.destroy({ children: true });
        entityVisuals.delete(entityId);
      }
    }
    }

    snapshot.projectiles.forEach((projectile) => {
      const color = projectile.kind === 1 ? 0xfff1a8 : 0x6f8c93;
      const angle = (projectile.angle / 65_536) * Math.PI * 2;
      const dirX = Math.cos(angle);
      const dirY = Math.sin(angle);
      const trail = new Graphics()
        .moveTo(projectile.x - dirX * 900, projectile.y - dirY * 900)
        .lineTo(projectile.x, projectile.y)
        .stroke({ width: 180, color, alpha: 0.45 });
      const bullet = new Graphics()
        .circle(projectile.x, projectile.y, 320)
        .fill(color)
        .stroke({ width: 100, color: 0x071012 });
      projectiles.addChild(trail, bullet);
    });

    appRef.current?.renderer.render(appRef.current.stage);
  }, [ready, snapshot, genomes, selectedId]);

  useEffect(() => {
    if (!ready || events.length === 0) {
      return;
    }
    const layer = effectLayerRef.current;
    if (layer === null) {
      return;
    }
    const latestId = events[events.length - 1].id;
    if (lastEventIdRef.current === 0) {
      lastEventIdRef.current = latestId;
      return;
    }
    events.forEach((event) => {
      if (event.id <= lastEventIdRef.current) {
        return;
      }
      if (
        effectsEnabledRef.current &&
        (event.type === "HIT" || event.type === "DEATH" || event.type === "OBJECTIVE")
      ) {
        spawnImpact(layer, event, effectTimersRef.current);
      }
    });
    lastEventIdRef.current = Math.max(lastEventIdRef.current, latestId);
    appRef.current?.renderer.render(appRef.current.stage);
  }, [ready, events]);

  return <div ref={hostRef} className="battlefield" />;
}

function spawnImpact(layer: Container, event: SimEvent, timers: number[]) {
  const color = impactColor(event);
  const radius = event.type === "DEATH" ? 1_200 : event.type === "OBJECTIVE" ? 1_800 : 420;
  const effect = new Graphics()
    .circle(event.x, event.y, radius)
    .fill({ color, alpha: 0.45 })
    .stroke({ width: radius * 0.22, color, alpha: 0.9 });
  layer.addChild(effect);
  const timer = window.setTimeout(() => {
    if (effect.parent !== null) {
      effect.parent.removeChild(effect);
    }
    effect.destroy();
  }, event.type === "DEATH" ? 320 : 180);
  timers.push(timer);
}

function impactColor(event: SimEvent): number {
  if (event.type === "DEATH") return 0xff5c6c;
  if (event.type === "OBJECTIVE") return 0xffb347;
  switch (event.text) {
    case "PIERCING":
      return 0xffffff;
    case "ACID":
      return 0x9cff6a;
    case "TOXIC":
      return 0xb6ff7a;
    case "ELECTRIC":
      return 0x7cf7ff;
    case "THERMAL":
      return 0xff9d5c;
    default:
      return 0xfff1a8;
  }
}

function lerpAngle(from: number, to: number, alpha: number): number {
  const delta = ((((to - from) + 32_768) % 65_536) + 65_536) % 65_536 - 32_768;
  return (from + delta * alpha + 65_536) % 65_536;
}

const backShapes = new Set<CreatureRenderPart["shape"]>([
  "LEG",
  "WING",
  "TAIL",
  "HAMMER",
  "FIN",
  "BURROW_CLAW",
  "SLIME",
  "SKIN"
]);

const shapeColors: Record<CreatureRenderPart["shape"], number> = {
  LEG: 0x75a7ff,
  WING: 0x9fd7ff,
  CLAW: 0xff7d6e,
  FANG: 0xff7d6e,
  SPIKE: 0xe6d36f,
  TAIL: 0xff9d5c,
  HAMMER: 0xff9d5c,
  EYE: 0x75e6d0,
  CHEMO: 0x78d47d,
  WHISKER: 0x75e6d0,
  ORB: 0xffd166,
  PLATE: 0xb088ff,
  NOZZLE: 0xff9d5c,
  MOUTH: 0xff7d6e,
  GLAND: 0x78d47d,
  LURE: 0x7cf7ff,
  VENT: 0xb088ff,
  FIN: 0x75a7ff,
  SLIME: 0x78d47d,
  SKIN: 0x75e6d0,
  HARPOON: 0xe6d36f,
  MORTAR: 0xff9d5c,
  BURROW_CLAW: 0xd0a36a
};

function createEntityVisual(entity: EntitySnapshot, render: CreatureRenderView): EntityVisual {
  const ownerColor = entity.owner === 0 ? 0x39e6b0 : 0xff8a4c;
  const bodyFill = entity.owner === 0 ? 0x123b33 : 0x3b2416;
  const bodyRadius = render.bodyRadiusMm;
  const root = new Container();
  const bodyRoot = new Container();
  const body = new Graphics();
  drawCreatureBody(body, render, ownerColor, bodyFill);
  bodyRoot.addChild(body);
  bodyRoot.visible = false;

  const simpleRoot = new Container();
  const simpleBody = new Graphics();
  drawSimpleBody(simpleBody, bodyRadius, ownerColor, bodyFill);
  simpleRoot.addChild(simpleBody);

  const selectionRing = new Graphics()
    .circle(0, 0, bodyRadius * 1.62)
    .stroke({ width: bodyRadius * 0.07, color: 0xffffff, alpha: 0.95 });
  selectionRing.visible = false;

  const attackRing = new Graphics()
    .circle(0, 0, bodyRadius * 1.42)
    .stroke({ width: bodyRadius * 0.06, color: 0xff5c6c, alpha: 0.9 });
  attackRing.visible = false;

  const hpBar = new Graphics()
    .rect(-bodyRadius * 0.95, -bodyRadius * 1.5, bodyRadius * 1.9, bodyRadius * 0.3)
    .fill(0x071012);
  const hpFill = new Graphics();

  root.addChild(simpleRoot, bodyRoot, selectionRing, attackRing, hpBar, hpFill);
  return {
    root,
    bodyRoot,
    simpleRoot,
    selectionRing,
    attackRing,
    hpFill,
    bodyRadius,
    ownerColor
  };
}

function drawSimpleBody(
  g: Graphics,
  bodyRadius: number,
  ownerColor: number,
  bodyFill: number
) {
  const bodyRy = bodyRadius * 0.72;
  g.ellipse(0, 0, bodyRadius * 0.9, bodyRy)
    .fill(bodyFill)
    .stroke({ width: bodyRadius * 0.08, color: ownerColor });
  g.ellipse(bodyRadius * 0.5, 0, bodyRadius * 0.45, bodyRy * 0.62)
    .fill(bodyFill)
    .stroke({ width: bodyRadius * 0.055, color: ownerColor });
  g.moveTo(-bodyRadius * 0.45, 0)
    .quadraticCurveTo(-bodyRadius * 0.9, -bodyRy * 0.36, -bodyRadius * 1.35, 0)
    .quadraticCurveTo(-bodyRadius * 0.9, bodyRy * 0.36, -bodyRadius * 0.45, 0)
    .closePath()
    .fill(bodyFill)
    .stroke({ width: bodyRadius * 0.05, color: ownerColor });
}

function drawCreatureBody(
  g: Graphics,
  render: CreatureRenderView,
  ownerColor: number,
  bodyFill: number
) {
  const bodyRadius = render.bodyRadiusMm;
  const bodyRy = bodyRadius * 0.72;

  render.parts
    .filter((part) => backShapes.has(part.shape))
    .forEach((part) => drawPart(g, part, render, bodyRadius, bodyRy));

  const hasTail = render.parts.some((part) => part.shape === "TAIL" || part.shape === "HAMMER");
  if (!hasTail) {
    drawDefaultTail(g, bodyRadius, bodyRy, ownerColor, bodyFill);
  }

  drawBody(g, render, bodyRadius, bodyRy, ownerColor, bodyFill);

  render.parts
    .filter((part) => !backShapes.has(part.shape))
    .forEach((part) => drawPart(g, part, render, bodyRadius, bodyRy));
}

function drawBody(
  g: Graphics,
  render: CreatureRenderView,
  bodyRadius: number,
  bodyRy: number,
  ownerColor: number,
  bodyFill: number
) {
  g.ellipse(0, 0, bodyRadius, bodyRy)
    .fill(bodyFill)
    .stroke({ width: bodyRadius * 0.08, color: ownerColor });

  g.ellipse(bodyRadius * 0.55, 0, bodyRadius * 0.5, bodyRy * 0.66)
    .fill(bodyFill)
    .stroke({ width: bodyRadius * 0.055, color: ownerColor });

  g.moveTo(bodyRadius * 0.95, -bodyRy * 0.26)
    .lineTo(bodyRadius * 1.18, 0)
    .lineTo(bodyRadius * 0.95, bodyRy * 0.26)
    .stroke({ width: bodyRadius * 0.045, color: ownerColor, alpha: 0.95 });

  g.moveTo(-bodyRadius * 0.72, 0)
    .lineTo(bodyRadius * 0.55, 0)
    .stroke({ width: bodyRadius * 0.025, color: ownerColor, alpha: 0.35 });

  if (render.sizeClass === "L") {
    g.ellipse(0, 0, bodyRadius * 0.62, bodyRy * 0.55)
      .stroke({ width: bodyRadius * 0.03, color: ownerColor, alpha: 0.4 });
  }
  if (render.sizeClass === "M") {
    g.moveTo(-bodyRadius * 0.25, -bodyRy * 0.55)
      .lineTo(-bodyRadius * 0.25, bodyRy * 0.55)
      .stroke({ width: bodyRadius * 0.02, color: ownerColor, alpha: 0.3 });
  }
}

function drawDefaultTail(
  g: Graphics,
  bodyRadius: number,
  bodyRy: number,
  ownerColor: number,
  bodyFill: number
) {
  g.moveTo(-bodyRadius * 0.55, 0)
    .quadraticCurveTo(-bodyRadius * 0.98, -bodyRy * 0.38, -bodyRadius * 1.48, 0)
    .quadraticCurveTo(-bodyRadius * 0.98, bodyRy * 0.38, -bodyRadius * 0.55, 0)
    .closePath()
    .fill(bodyFill)
    .stroke({ width: bodyRadius * 0.055, color: ownerColor });
}

function drawPart(
  g: Graphics,
  part: CreatureRenderPart,
  render: CreatureRenderView,
  bodyRadius: number,
  bodyRy: number
) {
  const sameSocket = render.parts.filter((candidate) => candidate.socketId === part.socketId);
  const index = sameSocket.indexOf(part);
  const slotSpread = sameSocket.length <= 1
    ? 0
    : (index - (sameSocket.length - 1) / 2) * bodyRadius * 0.42;
  const mirroredSides = part.mirrored && (part.socketId === "LEFT" || part.socketId === "RIGHT")
    ? [-1, 1]
    : [part.socketId === "LEFT" ? -1 : part.socketId === "RIGHT" ? 1 : 0];

  mirroredSides.forEach((side) => {
    const anchor = partAnchor(part.socketId, slotSpread, bodyRadius, bodyRy, side);
    drawPartShape(g, part, anchor.x, anchor.y, anchor.angle, bodyRadius);
  });
}

function partAnchor(
  socketId: CreatureRenderPart["socketId"],
  spread: number,
  bodyRadius: number,
  bodyRy: number,
  side: number
): { x: number; y: number; angle: number } {
  switch (socketId) {
    case "FRONT":
      return { x: bodyRadius * 0.82, y: spread, angle: 0 };
    case "BACK":
      return { x: -bodyRadius * 0.82, y: spread, angle: Math.PI };
    case "LEFT":
      return {
        x: spread * 0.65,
        y: (side === 0 ? -1 : side) * bodyRy * 0.78,
        angle: (side === 0 ? -1 : side) * Math.PI / 2
      };
    case "RIGHT":
      return {
        x: spread * 0.65,
        y: (side === 0 ? 1 : side) * bodyRy * 0.78,
        angle: (side === 0 ? 1 : side) * Math.PI / 2
      };
    case "TOP":
      return { x: spread * 0.7, y: -bodyRy * 0.48, angle: -Math.PI / 2 };
    case "UNDER":
      return { x: spread * 0.7, y: bodyRy * 0.48, angle: Math.PI / 2 };
    default:
      return { x: 0, y: 0, angle: 0 };
  }
}

function drawPartShape(
  g: Graphics,
  part: CreatureRenderPart,
  x: number,
  y: number,
  angle: number,
  bodyRadius: number
) {
  const scale = part.scale / 100;
  const dirX = Math.cos(angle);
  const dirY = Math.sin(angle);
  const perpX = -dirY;
  const perpY = dirX;
  const color = shapeColors[part.shape];
  const out = (distance: number) => ({ x: x + dirX * distance, y: y + dirY * distance });

  switch (part.shape) {
    case "LEG": {
      const knee = out(bodyRadius * 0.58 * scale);
      const foot = out(bodyRadius * 1.08 * scale);
      g.moveTo(x, y)
        .lineTo(knee.x + perpX * bodyRadius * 0.18, knee.y + perpY * bodyRadius * 0.18)
        .lineTo(foot.x - perpX * bodyRadius * 0.12, foot.y - perpY * bodyRadius * 0.12)
        .stroke({ width: bodyRadius * 0.11, color, alpha: 0.95 });
      g.circle(knee.x, knee.y, bodyRadius * 0.07).fill(color);
      break;
    }
    case "WING": {
      [-1, 1].forEach((wingSide) => {
        const wake = bodyRadius * 0.16 * wingSide;
        g.moveTo(-bodyRadius * 0.15, wake)
          .quadraticCurveTo(
            -bodyRadius * 0.75,
            bodyRadius * 1.35 * wingSide,
            -bodyRadius * 1.55,
            bodyRadius * 0.85 * wingSide
          )
          .quadraticCurveTo(-bodyRadius * 0.45, bodyRadius * 0.38 * wingSide, -bodyRadius * 0.15, wake)
          .closePath()
          .fill({ color, alpha: 0.28 })
          .stroke({ width: bodyRadius * 0.035, color, alpha: 0.8 });
      });
      break;
    }
    case "TAIL": {
      const mid = out(bodyRadius * 0.75 * scale);
      const tip = {
        x: -bodyRadius * 1.65,
        y: bodyRadius * 0.22
      };
      g.moveTo(-bodyRadius * 0.65, 0)
        .lineTo(mid.x, mid.y)
        .lineTo(tip.x, tip.y)
        .stroke({ width: bodyRadius * 0.12, color, alpha: 0.95 });
      drawSpike(g, tip.x, tip.y, -0.35, bodyRadius * 0.5, color);
      drawSpike(g, tip.x, tip.y, -0.75, bodyRadius * 0.35, color);
      break;
    }
    case "HAMMER": {
      const end = out(bodyRadius * 0.95 * scale);
      g.moveTo(-bodyRadius * 0.6, 0)
        .lineTo(end.x, end.y)
        .stroke({ width: bodyRadius * 0.13, color, alpha: 0.95 });
      g.roundRect(end.x - bodyRadius * 0.28, end.y - bodyRadius * 0.2, bodyRadius * 0.48, bodyRadius * 0.4, bodyRadius * 0.08)
        .fill(color)
        .stroke({ width: bodyRadius * 0.03, color: 0x071012 });
      break;
    }
    case "SPIKE": {
      [-0.28, 0.08, 0.44].forEach((offset, index) => {
        const baseX = x + bodyRadius * offset;
        const baseY = y - bodyRadius * 0.18;
        drawSpike(g, baseX, baseY, -Math.PI / 2 + (index - 1) * 0.18, bodyRadius * 0.42 * scale, color);
      });
      break;
    }
    case "CLAW":
    case "FANG": {
      drawSpike(g, x, y, angle - 0.2, bodyRadius * 0.38 * scale, color);
      drawSpike(g, x, y, angle + 0.2, bodyRadius * 0.32 * scale, color);
      break;
    }
    case "EYE": {
      const eyeRadius = bodyRadius * 0.12 * scale;
      g.circle(x, y, eyeRadius).fill(color).stroke({ width: bodyRadius * 0.03, color: 0x071012 });
      g.circle(x + dirX * eyeRadius * 0.25, y + dirY * eyeRadius * 0.25, eyeRadius * 0.42).fill(0x071012);
      break;
    }
    case "CHEMO":
    case "WHISKER": {
      for (let index = -1; index <= 1; index += 1) {
        const sway = perpX * bodyRadius * 0.2 * index;
        const swayY = perpY * bodyRadius * 0.2 * index;
        const tip = out(bodyRadius * (0.55 + index * 0.08) * scale);
        g.moveTo(x, y)
          .quadraticCurveTo(x + sway * 0.4, y + swayY * 0.4, tip.x + sway, tip.y + swayY)
          .stroke({ width: bodyRadius * 0.025, color, alpha: 0.85 });
        g.circle(tip.x + sway, tip.y + swayY, bodyRadius * 0.03).fill(color);
      }
      break;
    }
    case "PLATE": {
      g.ellipse(0, 0, bodyRadius * 0.72, bodyRadius * 0.48)
        .fill({ color, alpha: 0.22 })
        .stroke({ width: bodyRadius * 0.035, color, alpha: 0.8 });
      g.moveTo(-bodyRadius * 0.5, 0)
        .lineTo(bodyRadius * 0.5, 0)
        .stroke({ width: bodyRadius * 0.02, color, alpha: 0.6 });
      break;
    }
    case "NOZZLE": {
      const tip = out(bodyRadius * 0.65 * scale);
      g.moveTo(x, y)
        .lineTo(tip.x, tip.y)
        .stroke({ width: bodyRadius * 0.14, color, alpha: 0.9 });
      g.circle(tip.x, tip.y, bodyRadius * 0.09).fill(0x071012).stroke({ width: bodyRadius * 0.03, color });
      break;
    }
    case "MOUTH": {
      const jaw = bodyRadius * 0.36 * scale;
      g.moveTo(x, y)
        .lineTo(x + dirX * jaw + perpX * jaw * 0.55, y + dirY * jaw + perpY * jaw * 0.55)
        .lineTo(x + dirX * jaw * 0.5, y + dirY * jaw * 0.5)
        .closePath()
        .fill(color);
      g.moveTo(x, y)
        .lineTo(x + dirX * jaw - perpX * jaw * 0.55, y + dirY * jaw - perpY * jaw * 0.55)
        .lineTo(x + dirX * jaw * 0.5, y + dirY * jaw * 0.5)
        .closePath()
        .fill({ color, alpha: 0.8 });
      break;
    }
    case "GLAND": {
      g.circle(x, y, bodyRadius * 0.14 * scale).fill({ color, alpha: 0.75 }).stroke({ width: bodyRadius * 0.025, color });
      g.circle(x + perpX * bodyRadius * 0.08, y + perpY * bodyRadius * 0.08, bodyRadius * 0.06).fill(color);
      break;
    }
    case "LURE": {
      const tip = { x: x - bodyRadius * 0.1, y: y - bodyRadius * 0.85 * scale };
      g.moveTo(x, y)
        .quadraticCurveTo(x - bodyRadius * 0.3, y - bodyRadius * 0.5, tip.x, tip.y)
        .stroke({ width: bodyRadius * 0.025, color, alpha: 0.8 });
      g.circle(tip.x, tip.y, bodyRadius * 0.16 * scale).fill({ color, alpha: 0.35 });
      g.circle(tip.x, tip.y, bodyRadius * 0.07 * scale).fill(color);
      break;
    }
    case "VENT": {
      [-1, 0, 1].forEach((offset) => {
        g.moveTo(x + offset * bodyRadius * 0.14, y)
          .lineTo(x + offset * bodyRadius * 0.14, y + bodyRadius * 0.25)
          .stroke({ width: bodyRadius * 0.025, color, alpha: 0.8 });
      });
      break;
    }
    case "FIN": {
      g.moveTo(x, y)
        .lineTo(x - bodyRadius * 0.6 * scale, y - bodyRadius * 0.45 * scale)
        .lineTo(x - bodyRadius * 0.2 * scale, y + bodyRadius * 0.35 * scale)
        .closePath()
        .fill({ color, alpha: 0.55 })
        .stroke({ width: bodyRadius * 0.025, color });
      break;
    }
    case "SLIME": {
      g.ellipse(0, 0, bodyRadius * 1.08, bodyRadius * 0.8)
        .fill({ color, alpha: 0.1 })
        .stroke({ width: bodyRadius * 0.03, color, alpha: 0.45 });
      break;
    }
    case "SKIN": {
      g.ellipse(0, 0, bodyRadius * 0.8, bodyRadius * 0.56)
        .stroke({ width: bodyRadius * 0.02, color, alpha: 0.5 });
      g.moveTo(-bodyRadius * 0.4, -bodyRadius * 0.2)
        .quadraticCurveTo(0, bodyRadius * 0.25, bodyRadius * 0.45, -bodyRadius * 0.15)
        .stroke({ width: bodyRadius * 0.02, color, alpha: 0.35 });
      break;
    }
    case "HARPOON": {
      const tip = out(bodyRadius * 1.35 * scale);
      g.moveTo(x, y)
        .lineTo(tip.x, tip.y)
        .stroke({ width: bodyRadius * 0.06, color, alpha: 0.95 });
      g.moveTo(tip.x, tip.y)
        .lineTo(tip.x - dirX * bodyRadius * 0.2 + perpX * bodyRadius * 0.1, tip.y - dirY * bodyRadius * 0.2 + perpY * bodyRadius * 0.1)
        .lineTo(tip.x - dirX * bodyRadius * 0.2 - perpX * bodyRadius * 0.1, tip.y - dirY * bodyRadius * 0.2 - perpY * bodyRadius * 0.1)
        .closePath()
        .fill(color);
      break;
    }
    case "MORTAR": {
      const tip = out(bodyRadius * 0.55 * scale);
      g.moveTo(x, y)
        .lineTo(tip.x, tip.y)
        .stroke({ width: bodyRadius * 0.24, color, alpha: 0.9 });
      g.circle(tip.x, tip.y, bodyRadius * 0.13).fill(0x071012).stroke({ width: bodyRadius * 0.03, color });
      break;
    }
    case "ORB": {
      g.circle(x, y, bodyRadius * 0.16 * scale).fill({ color, alpha: 0.8 }).stroke({ width: bodyRadius * 0.03, color, alpha: 0.9 });
      g.moveTo(x - bodyRadius * 0.12, y - bodyRadius * 0.2)
        .lineTo(x + bodyRadius * 0.05, y)
        .lineTo(x - bodyRadius * 0.04, y + bodyRadius * 0.18)
        .stroke({ width: bodyRadius * 0.025, color: 0xffffff, alpha: 0.9 });
      break;
    }
    case "BURROW_CLAW": {
      const tip = out(bodyRadius * 0.7 * scale);
      g.moveTo(x, y)
        .quadraticCurveTo(x + perpX * bodyRadius * 0.3, y + perpY * bodyRadius * 0.3, tip.x, tip.y)
        .stroke({ width: bodyRadius * 0.16, color, alpha: 0.9 });
      g.circle(tip.x, tip.y, bodyRadius * 0.08).fill(color);
      break;
    }
    default:
      g.circle(x, y, bodyRadius * 0.1 * scale).fill({ color, alpha: 0.6 });
  }
}

function drawSpike(
  g: Graphics,
  x: number,
  y: number,
  angle: number,
  length: number,
  color: number
) {
  const width = length * 0.32;
  const tipX = x + Math.cos(angle) * length;
  const tipY = y + Math.sin(angle) * length;
  const perpX = -Math.sin(angle) * width;
  const perpY = Math.cos(angle) * width;
  g.moveTo(x + perpX, y + perpY)
    .lineTo(tipX, tipY)
    .lineTo(x - perpX, y - perpY)
    .closePath()
    .fill(color);
}

function createCorpseVisual(owner: number): Container {
  const ownerTint = owner === 0 ? 0x1f6f5c : 0x7a3d1e;
  const container = new Container();
  const body = new Graphics();
  body
    .ellipse(0, 0, 1_100, 560)
    .fill({ color: 0x2a2420, alpha: 0.55 })
    .stroke({ width: 160, color: 0x6f5a45, alpha: 0.8 });
  body
    .moveTo(-850, -320)
    .lineTo(850, 320)
    .stroke({ width: 110, color: 0x6f5a45, alpha: 0.65 });
  body
    .moveTo(-850, 320)
    .lineTo(850, -320)
    .stroke({ width: 110, color: 0x6f5a45, alpha: 0.65 });
  body
    .circle(-330, -70, 95)
    .fill({ color: ownerTint, alpha: 0.65 });
  body
    .circle(-330, -70, 95)
    .stroke({ width: 60, color: 0x071012, alpha: 0.7 });
  container.addChild(body);
  return container;
}

function drawBatchedCreatures(
  layer: Container,
  snapshot: Snapshot,
  genomesById: Map<number, GenomeSummary>,
  selectedId: number | null
) {
  clearLayer(layer);
  const bodies = new Graphics();
  const hpBars = new Graphics();
  const rings = new Graphics();

  snapshot.entities.forEach((entity) => {
    const render = genomesById.get(entity.genomeId)?.render;
    if (render === undefined) {
      return;
    }
    const radius = render.bodyRadiusMm;
    const angle = (entity.angle / 65_536) * Math.PI * 2;
    const dirX = Math.cos(angle);
    const dirY = Math.sin(angle);
    const perpX = -dirY;
    const perpY = dirX;
    const ownerColor = entity.owner === 0 ? 0x39e6b0 : 0xff8a4c;
    const bodyFill = entity.owner === 0 ? 0x123b33 : 0x3b2416;

    bodies.circle(entity.x, entity.y, radius * 0.82).fill(bodyFill).stroke({
      width: radius * 0.075,
      color: ownerColor
    });
    const headX = entity.x + dirX * radius * 1.05;
    const headY = entity.y + dirY * radius * 1.05;
    bodies
      .moveTo(headX, headY)
      .lineTo(
        entity.x + dirX * radius * 0.45 + perpX * radius * 0.45,
        entity.y + dirY * radius * 0.45 + perpY * radius * 0.45
      )
      .lineTo(
        entity.x + dirX * radius * 0.45 - perpX * radius * 0.45,
        entity.y + dirY * radius * 0.45 - perpY * radius * 0.45
      )
      .closePath()
      .fill(ownerColor);
    const tailX = entity.x - dirX * radius * 1.35;
    const tailY = entity.y - dirY * radius * 1.35;
    bodies
      .moveTo(tailX, tailY)
      .lineTo(
        entity.x - dirX * radius * 0.5 + perpX * radius * 0.3,
        entity.y - dirY * radius * 0.5 + perpY * radius * 0.3
      )
      .lineTo(
        entity.x - dirX * radius * 0.5 - perpX * radius * 0.3,
        entity.y - dirY * radius * 0.5 - perpY * radius * 0.3
      )
      .closePath()
      .fill(bodyFill);

    const hpPercent = entity.maxHp === 0 ? 0 : entity.hp / entity.maxHp;
    const barWidth = radius * 1.8;
    const barX = entity.x - barWidth / 2;
    const barY = entity.y - radius * 1.45;
    hpBars.rect(barX, barY, barWidth, radius * 0.25).fill(0x071012);
    hpBars.rect(barX + radius * 0.04, barY + radius * 0.04, (barWidth - radius * 0.08) * hpPercent, radius * 0.17).fill(ownerColor);

    if (entity.id === selectedId) {
      rings.circle(entity.x, entity.y, radius * 1.62).stroke({ width: radius * 0.07, color: 0xffffff });
    }
    if (entity.state === 2) {
      rings.circle(entity.x, entity.y, radius * 1.42).stroke({ width: radius * 0.06, color: 0xff5c6c, alpha: 0.9 });
    }
  });

  layer.addChild(bodies, hpBars, rings);
}

function drawGrid(layer: Container, map: MapDefinition) {
  const grid = new Graphics();
  for (let x = 0; x <= map.widthMm; x += 8_000) {
    grid.moveTo(x, 0).lineTo(x, map.heightMm);
  }
  for (let y = 0; y <= map.heightMm; y += 8_000) {
    grid.moveTo(0, y).lineTo(map.widthMm, y);
  }
  grid.stroke({ width: 250, color: map.theme.grid, alpha: 0.9 });
  layer.addChild(grid);

  map.obstacles.forEach((obstacle) => {
    const rock = new Graphics()
      .rect(obstacle.x, obstacle.y, obstacle.width, obstacle.height)
      .fill(map.theme.obstacle)
      .stroke({ width: 500, color: map.theme.obstacleStroke });
    layer.addChild(rock);
  });

  const border = new Graphics()
    .rect(0, 0, map.widthMm, map.heightMm)
    .stroke({ width: 700, color: map.theme.border });
  layer.addChild(border);
}

function clearLayer(layer: Container) {
  const children = layer.removeChildren();
  children.forEach((child) => {
    child.destroy({ children: true });
  });
}
