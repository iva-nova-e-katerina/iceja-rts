import { useRef, useState, type PointerEvent as ReactPointerEvent, type WheelEvent as ReactWheelEvent } from "react";
import type { ChassisDefinition, Genome, PartCategory, PartDefinition, SocketType } from "../../types";

interface CreatureSchematicProps {
  genome: Genome;
  chassis: ChassisDefinition | undefined;
  partsById: Map<string, PartDefinition>;
  highlightSockets: SocketType[];
  onRemove: (index: number) => void;
}

const socketPositions: Record<string, { x: number; y: number }> = {
  CORE: { x: 0, y: 0 },
  FRONT: { x: 0, y: -88 },
  BACK: { x: 0, y: 88 },
  LEFT: { x: -88, y: 0 },
  RIGHT: { x: 88, y: 0 },
  TOP: { x: -52, y: -52 },
  UNDER: { x: 52, y: 52 }
};

const categoryColor: Record<PartCategory, string> = {
  CORE: "#e6d36f",
  LOCOMOTION: "#75a7ff",
  SENSOR: "#75e6d0",
  MELEE: "#ff7d6e",
  RANGED: "#ff9d5c",
  DEFENSE: "#b088ff",
  UTILITY: "#78d47d"
};

export default function CreatureSchematic({
  genome,
  chassis,
  partsById,
  highlightSockets,
  onRemove
}: CreatureSchematicProps) {
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [dragging, setDragging] = useState(false);
  const dragRef = useRef<{ x: number; y: number; panX: number; panY: number } | null>(null);
  const placementsBySocket = new Map<string, Array<{ index: number; part: PartDefinition; mirrored: boolean }>>();
  const internalPlacements: Array<{ index: number; part: PartDefinition }> = [];
  genome.parts.forEach((placement, index) => {
    const part = partsById.get(placement.partId);
    if (part === undefined) {
      return;
    }
    if (part.visual.visibility === "INTERNAL") {
      internalPlacements.push({ index, part });
      return;
    }
    const list = placementsBySocket.get(placement.socketId) ?? [];
    list.push({ index, part, mirrored: placement.mirrored });
    placementsBySocket.set(placement.socketId, list);
  });

  function applyZoom(nextZoom: number) {
    const clamped = Math.max(1, Math.min(3.5, nextZoom));
    const viewSize = 440 / clamped;
    const maxPan = Math.max(0, (440 - viewSize) / 2);
    setZoom(clamped);
    setPan((current) => ({
      x: Math.max(-maxPan, Math.min(maxPan, current.x)),
      y: Math.max(-maxPan, Math.min(maxPan, current.y))
    }));
  }

  function onWheel(event: ReactWheelEvent<SVGSVGElement>) {
    event.preventDefault();
    applyZoom(zoom * (event.deltaY < 0 ? 1.15 : 0.87));
  }

  function onPointerDown(event: ReactPointerEvent<SVGSVGElement>) {
    if ((event.target as Element).closest(".schematic-part") !== null) {
      return;
    }
    event.currentTarget.setPointerCapture(event.pointerId);
    dragRef.current = {
      x: event.clientX,
      y: event.clientY,
      panX: pan.x,
      panY: pan.y
    };
    setDragging(true);
  }

  function onPointerMove(event: ReactPointerEvent<SVGSVGElement>) {
    const drag = dragRef.current;
    if (drag === null) {
      return;
    }
    const rect = event.currentTarget.getBoundingClientRect();
    if (rect.width <= 0) {
      return;
    }
    const unitsPerPixel = 440 / zoom / rect.width;
    const maxPan = Math.max(0, (440 - 440 / zoom) / 2);
    setPan({
      x: Math.max(-maxPan, Math.min(maxPan, drag.panX - (event.clientX - drag.x) * unitsPerPixel)),
      y: Math.max(-maxPan, Math.min(maxPan, drag.panY - (event.clientY - drag.y) * unitsPerPixel))
    });
  }

  function onPointerUp() {
    dragRef.current = null;
    setDragging(false);
  }

  const viewSize = 440 / zoom;
  const viewBox = `${-viewSize / 2 + pan.x} ${-viewSize / 2 + pan.y} ${viewSize} ${viewSize}`;

  return (
    <div className="schematic-zoom-wrap">
      <div className="schematic-zoom-toolbar">
        <button onClick={() => applyZoom(zoom * 1.2)} title="Увеличить изображение">+</button>
        <button onClick={() => applyZoom(zoom * 0.8)} title="Уменьшить изображение">−</button>
        <button
          className="zoom-reset"
          onClick={() => {
            setZoom(1);
            setPan({ x: 0, y: 0 });
          }}
          title="Сбросить масштаб"
        >
          {Math.round(zoom * 100)}%
        </button>
      </div>
    <svg
      className={dragging ? "creature-schematic dragging" : "creature-schematic"}
      viewBox={viewBox}
      role="img"
      aria-label={genome.name}
      onWheel={onWheel}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
    >
      <defs>
        <pattern id="lab-grid" width="20" height="20" patternUnits="userSpaceOnUse">
          <path d="M 20 0 L 0 0 0 20" fill="none" stroke="#16262b" strokeWidth="1" />
        </pattern>
        <radialGradient id="body-glow" cx="50%" cy="45%" r="55%">
          <stop offset="0%" stopColor="#173238" />
          <stop offset="100%" stopColor="#0a1114" />
        </radialGradient>
      </defs>
      <rect x="-220" y="-220" width="440" height="440" fill="url(#lab-grid)" />
      <circle cx="0" cy="0" r="132" fill="none" stroke="#1c343a" strokeWidth="1" strokeDasharray="4 8" />
      <circle cx="0" cy="0" r="96" fill="url(#body-glow)" stroke="#2d5962" strokeWidth="2" />
      <path d="M -26 -10 L 0 -34 L 26 -10 L 0 14 Z" fill="#1a3b42" stroke="#39e6b0" strokeWidth="1.5" />

      <circle cx="0" cy="0" r="42" fill="none" stroke="#2b4a52" strokeWidth="1" strokeDasharray="3 5" />
      <text x="0" y="58" textAnchor="middle" fontSize="7" fill="#527078" fontFamily="monospace">
        внутренние
      </text>

      {internalPlacements.map((placement, order) => {
        const angle = (order / Math.max(1, internalPlacements.length)) * Math.PI * 2;
        const x = Math.cos(angle) * 28;
        const y = Math.sin(angle) * 28;
        return (
          <g
            key={`internal-${placement.index}-${placement.part.id}`}
            className="schematic-part internal"
            onClick={() => onRemove(placement.index)}
          >
            <title>{`${placement.part.name} — внутренний орган, в бою не виден`}</title>
            <circle cx={x} cy={y} r="6" fill={categoryColor[placement.part.category]} opacity="0.55" stroke="#071012" strokeWidth="1" />
          </g>
        );
      })}

      {chassis?.sockets.map((socket) => {
        const position = socketPositions[socket.type];
        if (position === undefined) {
          return null;
        }
        const highlighted = highlightSockets.includes(socket.type);
        return (
          <g key={socket.type} className={highlighted ? "socket-highlight" : undefined}>
            {highlighted && (
              <circle
                cx={position.x}
                cy={position.y}
                r="18"
                fill="none"
                stroke="#39e6b0"
                strokeWidth="1.5"
                strokeDasharray="4 3"
                className="socket-pulse"
              />
            )}
            <circle cx={position.x} cy={position.y} r="13" fill="#0b1417" stroke="#2d5962" strokeWidth="1" />
            <text
              x={position.x}
              y={position.y + 3}
              textAnchor="middle"
              fontSize="7"
              fill="#6798a1"
              fontFamily="monospace"
            >
              {socket.type.slice(0, 3)}
            </text>
            <text
              x={position.x + 17}
              y={position.y - 14}
              fontSize="7"
              fill="#3f6972"
              fontFamily="monospace"
            >
              {socket.count}
            </text>
          </g>
        );
      })}

      {Array.from(placementsBySocket.entries()).map(([socketType, placements]) =>
        placements.map((placement, order) => {
          const base = socketPositions[socketType];
          if (base === undefined) {
            return null;
          }
          const offset = placement.part.weapon?.ranged === true ? 18 : 0;
          const x = base.x + (order - (placements.length - 1) / 2) * 24 + offset;
          const y = base.y + (placement.mirrored ? 0 : order % 2 === 0 ? 0 : 10);
          return (
            <g
              key={`${placement.index}-${placement.part.id}`}
              className="schematic-part"
              onClick={() => onRemove(placement.index)}
            >
              <title>{`${placement.part.name} — внешний орган, виден в бою`}</title>
              <PartShape part={placement.part} x={x} y={y} />
              {placement.mirrored && <PartShape part={placement.part} x={-x} y={y} muted />}
            </g>
          );
        })
      )}
    </svg>
    </div>
  );
}

function PartShape({ part, x, y, muted = false }: { part: PartDefinition; x: number; y: number; muted?: boolean }) {
  const color = categoryColor[part.category];
  const fill = muted ? "#1b2f34" : color;
  const opacity = muted ? 0.55 : 1;
  if (part.category === "MELEE" || part.category === "RANGED") {
    return (
      <path
        d={`M ${x - 7} ${y + 6} L ${x + 7} ${y + 6} L ${x} ${y - 8} Z`}
        fill={fill}
        opacity={opacity}
        stroke="#071012"
        strokeWidth="1"
      />
    );
  }
  if (part.category === "LOCOMOTION") {
    return (
      <rect
        x={x - 8}
        y={y - 4}
        width="16"
        height="8"
        rx="3"
        fill={fill}
        opacity={opacity}
        stroke="#071012"
        strokeWidth="1"
      />
    );
  }
  if (part.category === "DEFENSE") {
    return (
      <path
        d={`M ${x} ${y - 9} L ${x + 8} ${y - 3} L ${x + 6} ${y + 7} L ${x} ${y + 10} L ${x - 6} ${y + 7} L ${x - 8} ${y - 3} Z`}
        fill={fill}
        opacity={opacity}
        stroke="#071012"
        strokeWidth="1"
      />
    );
  }
  if (part.category === "CORE") {
    return (
      <path
        d={`M ${x} ${y - 8} L ${x + 7} ${y - 4} L ${x + 7} ${y + 4} L ${x} ${y + 8} L ${x - 7} ${y + 4} L ${x - 7} ${y - 4} Z`}
        fill={fill}
        opacity={opacity}
        stroke="#071012"
        strokeWidth="1"
      />
    );
  }
  if (part.category === "UTILITY") {
    return (
      <path
        d={`M ${x} ${y - 8} L ${x + 8} ${y} L ${x} ${y + 8} L ${x - 8} ${y} Z`}
        fill={fill}
        opacity={opacity}
        stroke="#071012"
        strokeWidth="1"
      />
    );
  }
  return (
    <circle
      cx={x}
      cy={y}
      r="7"
      fill={fill}
      opacity={opacity}
      stroke="#071012"
      strokeWidth="1"
    />
  );
}
