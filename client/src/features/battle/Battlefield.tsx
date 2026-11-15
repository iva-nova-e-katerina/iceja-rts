import { useEffect, useRef, useState } from "react";
import { Application, Container, Graphics } from "pixi.js";
import type { GenomeSummary, MapDefinition, Snapshot } from "../../types";

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
            .filter(({ distance }) => distance < 4000)
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
      const radius = Math.max(1_200, genome?.radiusMm ?? 500);
      const baseColor = entity.owner === 0 ? 0x39e6b0 : 0xff8a4c;
      const stateColor =
        entity.state === 2 ? 0xff5c6c : entity.state === 1 ? 0x75e6d0 : baseColor;
      const creature = new Graphics()
        .circle(entity.x, entity.y, radius)
        .fill(stateColor)
        .stroke({ width: entity.owner === 0 ? 250 : 350, color: 0x071012 });
      if (entity.id === selectedId) {
        creature.circle(entity.x, entity.y, radius + 650).stroke({ width: 350, color: 0xffffff });
      }
      if (entity.state === 2) {
        creature.circle(entity.x, entity.y, radius + 350).stroke({ width: 250, color: 0xff5c6c });
      }
      entities.addChild(creature);

      const hpPercent = entity.maxHp === 0 ? 0 : entity.hp / entity.maxHp;
      const hpBar = new Graphics()
        .rect(entity.x - radius, entity.y - radius - 900, radius * 2, 500)
        .fill(0x071012)
        .rect(entity.x - radius + 100, entity.y - radius - 800, (radius * 2 - 200) * hpPercent, 300)
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
