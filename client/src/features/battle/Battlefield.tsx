import { useEffect, useRef, useState } from "react";
import { Application, Container, Graphics } from "pixi.js";
import type {
  CreatureRenderPart,
  CreatureRenderView,
  EntitySnapshot,
  GenomeSummary,
  MapDefinition,
  Snapshot
} from "../../types";

interface BattlefieldProps {
  map: MapDefinition;
  snapshot: Snapshot | null;
  genomes: GenomeSummary[];
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

export default function Battlefield({ map, snapshot, genomes, selectedId, onSelect }: BattlefieldProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const appRef = useRef<Application | null>(null);
  const worldRef = useRef<Container | null>(null);
  const hiveLayerRef = useRef<Container | null>(null);
  const entityLayerRef = useRef<Container | null>(null);
  const projectileLayerRef = useRef<Container | null>(null);
  const snapshotRef = useRef<Snapshot | null>(null);
  const onSelectRef = useRef(onSelect);
  const [ready, setReady] = useState(false);

  onSelectRef.current = onSelect;
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
        background: "#070b0d",
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
      const hiveLayer = new Container();
      const entityLayer = new Container();
      const projectileLayer = new Container();
      world.addChild(gridLayer, hiveLayer, entityLayer, projectileLayer);
      app.stage.addChild(world);
      appRef.current = app;
      worldRef.current = world;
      hiveLayerRef.current = hiveLayer;
      entityLayerRef.current = entityLayer;
      projectileLayerRef.current = projectileLayer;

      drawGrid(gridLayer, map);

      const fitScale = Math.min(app.screen.width / map.widthMm, app.screen.height / map.heightMm) * 0.94;
      world.scale.set(fitScale);
      world.position.set(
        (app.screen.width - map.widthMm * fitScale) / 2,
        (app.screen.height - map.heightMm * fitScale) / 2
      );

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
      setReady(true);
    };

    void boot();

    return () => {
      disposed = true;
      const cleanup = (host as HTMLDivElement & { __cleanup?: () => void }).__cleanup;
      cleanup?.();
      appRef.current?.destroy(true, { children: true });
      appRef.current = null;
      worldRef.current = null;
      hiveLayerRef.current = null;
      entityLayerRef.current = null;
      projectileLayerRef.current = null;
    };
  }, [map]);

  useEffect(() => {
    if (!ready || snapshot === null) {
      return;
    }
    const genomesById = new Map(genomes.map((genome) => [genome.genomeId, genome]));
    const hives = hiveLayerRef.current;
    const entities = entityLayerRef.current;
    const projectiles = projectileLayerRef.current;
    if (hives === null || entities === null || projectiles === null) {
      return;
    }
    clearLayer(hives);
    clearLayer(entities);
    clearLayer(projectiles);

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

    snapshot.entities.forEach((entity) => {
      const genome = genomesById.get(entity.genomeId);
      const render = genome?.render;
      if (render === undefined) {
        drawFallbackCreature(entities, entity, genome, selectedId);
        return;
      }
      const creature = new Graphics();
      drawCreature(creature, entity, render, selectedId);
      entities.addChild(creature);

      const bodyRadius = render.bodyRadiusMm;
      const hpPercent = entity.maxHp === 0 ? 0 : entity.hp / entity.maxHp;
      const barWidth = bodyRadius * 1.9;
      const barTop = -bodyRadius * 1.5;
      const hpBar = new Graphics();
      hpBar
        .rect(entity.x - barWidth / 2, entity.y + barTop, barWidth, bodyRadius * 0.3)
        .fill(0x071012);
      hpBar
        .rect(
          entity.x - barWidth / 2 + bodyRadius * 0.05,
          entity.y + barTop + bodyRadius * 0.05,
          (barWidth - bodyRadius * 0.1) * hpPercent,
          bodyRadius * 0.2
        )
        .fill(entity.owner === 0 ? 0x39e6b0 : 0xff9d5c);
      entities.addChild(hpBar);
    });

    snapshot.projectiles.forEach((projectile) => {
      const color = projectile.kind === 1 ? 0xfff1a8 : 0x6f8c93;
      const bullet = new Graphics()
        .circle(projectile.x, projectile.y, 320)
        .fill(color)
        .stroke({ width: 100, color: 0x071012 });
      projectiles.addChild(bullet);
    });
  }, [ready, snapshot, genomes, selectedId]);

  return <div ref={hostRef} className="battlefield" />;
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

function drawCreature(g: Graphics, entity: EntitySnapshot, render: CreatureRenderView, selectedId: number | null) {
  const ownerColor = entity.owner === 0 ? 0x39e6b0 : 0xff8a4c;
  const bodyFill = entity.owner === 0 ? 0x123b33 : 0x3b2416;
  const bodyRadius = render.bodyRadiusMm;
  const bodyRy = bodyRadius * 0.72;

  g.position.set(entity.x, entity.y);
  g.rotation = (entity.angle / 65_536) * Math.PI * 2;

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

  if (entity.state === 2) {
    g.circle(0, 0, bodyRadius * 1.42).stroke({
      width: bodyRadius * 0.06,
      color: 0xff5c6c,
      alpha: 0.9
    });
  }
  if (entity.id === selectedId) {
    g.circle(0, 0, bodyRadius * 1.62).stroke({
      width: bodyRadius * 0.07,
      color: 0xffffff,
      alpha: 0.95
    });
  }
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

function drawFallbackCreature(
  layer: Container,
  entity: EntitySnapshot,
  genome: GenomeSummary | undefined,
  selectedId: number | null
) {
  const radius = Math.max(1_200, genome?.radiusMm ?? 500);
  const baseColor = entity.owner === 0 ? 0x39e6b0 : 0xff8a4c;
  const stateColor = entity.state === 2 ? 0xff5c6c : entity.state === 1 ? 0x75e6d0 : baseColor;
  const creature = new Graphics()
    .circle(entity.x, entity.y, radius)
    .fill(stateColor)
    .stroke({ width: entity.owner === 0 ? 250 : 350, color: 0x071012 });
  if (entity.id === selectedId) {
    creature.circle(entity.x, entity.y, radius + 650).stroke({ width: 350, color: 0xffffff });
  }
  layer.addChild(creature);
}

function drawGrid(layer: Container, map: MapDefinition) {
  const grid = new Graphics();
  for (let x = 0; x <= map.widthMm; x += 8_000) {
    grid.moveTo(x, 0).lineTo(x, map.heightMm);
  }
  for (let y = 0; y <= map.heightMm; y += 8_000) {
    grid.moveTo(0, y).lineTo(map.widthMm, y);
  }
  grid.stroke({ width: 250, color: 0x12242a, alpha: 0.9 });
  layer.addChild(grid);

  map.obstacles.forEach((obstacle) => {
    const rock = new Graphics()
      .rect(obstacle.x, obstacle.y, obstacle.width, obstacle.height)
      .fill(0x1b2a30)
      .stroke({ width: 500, color: 0x2f4a50 });
    layer.addChild(rock);
  });

  const border = new Graphics()
    .rect(0, 0, map.widthMm, map.heightMm)
    .stroke({ width: 700, color: 0x2d5962 });
  layer.addChild(border);
}

function clearLayer(layer: Container) {
  const children = layer.removeChildren();
  children.forEach((child) => {
    child.destroy({ children: true });
  });
}
