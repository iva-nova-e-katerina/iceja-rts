import type { MapDefinition, Snapshot } from "../../types";

interface MinimapProps {
  map: MapDefinition;
  snapshot: Snapshot | null;
}

export default function Minimap({ map, snapshot }: MinimapProps) {
  const entities = snapshot?.entities ?? [];
  const corpses = snapshot?.corpses ?? [];
  const hives = snapshot?.hives ?? [];
  const objectives = snapshot?.objectives ?? [];

  return (
    <div className="battle-minimap" title="Мини-карта">
      <svg viewBox={`0 0 ${map.widthMm} ${map.heightMm}`} role="img" aria-label="Мини-карта боя">
        <rect
          x="0"
          y="0"
          width={map.widthMm}
          height={map.heightMm}
          fill={cssColor(map.theme.background)}
        />
        {map.hazards.map((hazard) => (
          <circle
            key={`hazard-${hazard.id}`}
            cx={hazard.x}
            cy={hazard.y}
            r={hazard.radiusMm}
            fill="#6f9c3f"
            opacity="0.28"
          />
        ))}
        {map.obstacles.map((obstacle, index) => (
          <rect
            key={index}
            x={obstacle.x}
            y={obstacle.y}
            width={obstacle.width}
            height={obstacle.height}
            fill={cssColor(map.theme.obstacle)}
          />
        ))}
        {hives.map((hive) => (
          <circle
            key={`hive-${hive.id}`}
            cx={hive.x}
            cy={hive.y}
            r={4_500}
            fill={hive.owner === 0 ? "#1f6f5c" : "#7a3d1e"}
            stroke={hive.owner === 0 ? "#39e6b0" : "#ff9d5c"}
            strokeWidth="600"
          />
        ))}
        {objectives.map((objective) => {
          const color = objective.owner === 0
            ? "#39e6b0"
            : objective.owner === 1
              ? "#ff9d5c"
              : "#7c9399";
          const ratio = objective.captureTicks === 0
            ? 0
            : Math.min(1, objective.progress / objective.captureTicks);
          return (
            <g key={`objective-${objective.id}`}>
              <circle
                cx={objective.x}
                cy={objective.y}
                r={objective.radiusMm}
                fill={color}
                opacity={objective.owner < 0 ? 0.12 : 0.22}
                stroke={objective.contested ? "#ff5c6c" : color}
                strokeWidth="500"
              />
              <circle
                cx={objective.x}
                cy={objective.y}
                r={objective.radiusMm * (0.25 + ratio * 0.65)}
                fill={color}
                opacity="0.42"
              />
            </g>
          );
        })}
        {corpses.map((corpse) => (
          <circle key={`corpse-${corpse.id}`} cx={corpse.x} cy={corpse.y} r={1_000} fill="#6f5a45" opacity="0.6" />
        ))}
        {entities.map((entity) => (
          <circle
            key={`entity-${entity.id}`}
            cx={entity.x}
            cy={entity.y}
            r={entity.state === 2 ? 1_800 : 1_300}
            fill={entity.owner === 0 ? "#39e6b0" : "#ff8a4c"}
          />
        ))}
      </svg>
    </div>
  );
}

function cssColor(value: number): string {
  return `#${value.toString(16).padStart(6, "0")}`;
}
