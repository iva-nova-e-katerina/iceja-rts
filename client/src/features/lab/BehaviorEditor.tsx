import { useEffect, useMemo, useRef, useState } from "react";
import {
  Background,
  BackgroundVariant,
  Controls,
  Handle,
  MiniMap,
  Position,
  ReactFlow,
  addEdge,
  useEdgesState,
  useNodesState,
  type Connection,
  type Edge,
  type Node,
  type NodeProps,
  type NodeTypes
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import type {
  BehaviorGraph,
  BehaviorNode,
  BehaviorPresetDefinition,
  PresetGraphView,
  ValidationIssue
} from "../../types";

type BehaviorData = {
  behaviorNode: BehaviorNode;
  [key: string]: unknown;
};

type BehaviorFlowNode = Node<BehaviorData, "behavior">;

interface BehaviorEditorProps {
  open: boolean;
  presetId: string;
  graph: BehaviorGraph | null;
  presets: BehaviorPresetDefinition[];
  presetGraphs: PresetGraphView[];
  validationErrors: ValidationIssue[];
  onApply: (graph: BehaviorGraph) => void;
  onClose: () => void;
}

const compositeTypes = new Set([
  "Selector",
  "Sequence",
  "UtilitySelector",
  "RandomSelector",
  "Parallel",
  "Cooldown",
  "Repeat",
  "Inverter"
]);

const conditionTypes = [
  "HasTarget",
  "TargetInRange",
  "TargetVisible",
  "HpBelow",
  "HpAbove",
  "EnergyBelow",
  "EnergyAbove",
  "EnemyCount",
  "AllyCount",
  "IsUnderAttack",
  "HiveUnderAttack",
  "HiveHpBelow",
  "EnemyHiveInRange",
  "TimerElapsed",
  "RandomChance",
  "ResourceNearby",
  "CorpseNearby"
];

const actionTypes = [
  "MoveTo",
  "MoveToPoint",
  "AttackTarget",
  "AttackMove",
  "Flee",
  "Follow",
  "Regroup",
  "HoldPosition",
  "Patrol",
  "Guard",
  "Harvest",
  "EatCorpse",
  "Wait"
];

const paramDefinitions: Record<string, Array<{ key: string; label: string; defaultValue: number }>> = {
  TargetInRange: [{ key: "range", label: "Радиус, м", defaultValue: 12 }],
  HpBelow: [{ key: "pct", label: "HP ниже, %", defaultValue: 20 }],
  HpAbove: [{ key: "pct", label: "HP выше, %", defaultValue: 50 }],
  EnergyBelow: [{ key: "pct", label: "Энергия ниже, %", defaultValue: 15 }],
  EnergyAbove: [{ key: "pct", label: "Энергия выше, %", defaultValue: 50 }],
  EnemyCount: [
    { key: "min", label: "Минимум", defaultValue: 1 },
    { key: "max", label: "Максимум", defaultValue: 99 },
    { key: "radius", label: "Радиус, м", defaultValue: 30 }
  ],
  AllyCount: [
    { key: "min", label: "Минимум", defaultValue: 1 },
    { key: "max", label: "Максимум", defaultValue: 99 },
    { key: "radius", label: "Радиус, м", defaultValue: 20 }
  ],
  IsUnderAttack: [{ key: "seconds", label: "Секунды", defaultValue: 3 }],
  HiveHpBelow: [{ key: "pct", label: "HP улья ниже, %", defaultValue: 50 }],
  EnemyHiveInRange: [{ key: "range", label: "Радиус, м", defaultValue: 30 }],
  TimerElapsed: [{ key: "seconds", label: "Секунды", defaultValue: 60 }],
  RandomChance: [{ key: "pct", label: "Шанс, %", defaultValue: 50 }],
  ResourceNearby: [{ key: "radius", label: "Радиус, м", defaultValue: 20 }],
  CorpseNearby: [{ key: "radius", label: "Радиус, м", defaultValue: 20 }],
  MoveToPoint: [
    { key: "x", label: "X, мм", defaultValue: 64_000 },
    { key: "y", label: "Y, мм", defaultValue: 64_000 }
  ],
  Wait: [{ key: "ticks", label: "Тики", defaultValue: 30 }]
};

const targetTypes = new Set(["MoveTo", "AttackTarget", "AttackMove", "HasStatus"]);

const nodeTypes: NodeTypes = {
  behavior: BehaviorNodeView
};

export default function BehaviorEditor({
  open,
  presetId,
  graph,
  presets,
  presetGraphs,
  validationErrors,
  onApply,
  onClose
}: BehaviorEditorProps) {
  const presetGraph = presetGraphs.find((item) => item.presetId === presetId)?.graph ?? null;
  const initialGraph = graph ?? presetGraph;
  const [nodes, setNodes, onNodesChange] = useNodesState<BehaviorFlowNode>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const nextNodeId = useRef(1);

  useEffect(() => {
    if (!open) {
      return;
    }
    const loaded = initialGraph === null ? emptyGraph() : initialGraph;
    const flow = graphToFlow(loaded);
    setNodes(flow.nodes);
    setEdges(flow.edges);
    setSelectedId(flow.nodes[0]?.id ?? null);
    nextNodeId.current = flow.nodes.length + 1;
  }, [open, initialGraph, setEdges, setNodes]);

  const selectedNode = useMemo(
    () => nodes.find((node) => node.id === selectedId) ?? null,
    [nodes, selectedId]
  );

  if (!open) {
    return null;
  }

  function handleConnect(connection: Connection) {
    if (connection.source === null || connection.target === null || connection.source === connection.target) {
      return;
    }
    const source = nodes.find((node) => node.id === connection.source);
    if (source === undefined || !compositeTypes.has(source.data.behaviorNode.type)) {
      return;
    }
    if (edges.some((edge) => edge.source === connection.source && edge.target === connection.target)) {
      return;
    }
    setEdges((current) =>
      addEdge(
        {
          id: `e-${connection.source}-${connection.target}`,
          source: connection.source,
          target: connection.target,
          type: "smoothstep"
        },
        current
      )
    );
  }

  function addNode(type: string) {
    const id = `n-${nextNodeId.current}`;
    nextNodeId.current += 1;
    const behaviorNode = defaultNode(id, type);
    const selected = nodes.find((node) => node.id === selectedId);
    const position =
      selected !== undefined && compositeTypes.has(selected.data.behaviorNode.type)
        ? { x: selected.position.x + 260, y: selected.position.y + 90 }
        : { x: 260, y: nodes.length * 90 + 40 };
    const flowNode: BehaviorFlowNode = {
      id,
      type: "behavior",
      position,
      data: { behaviorNode }
    };
    setNodes((current) => [...current, flowNode]);
    setSelectedId(id);
    if (selected !== undefined && compositeTypes.has(selected.data.behaviorNode.type)) {
      setEdges((current) => [
        ...current,
        {
          id: `e-${selected.id}-${id}`,
          source: selected.id,
          target: id,
          type: "smoothstep"
        }
      ]);
    }
  }

  function updateSelectedBehavior(updater: (node: BehaviorNode) => BehaviorNode) {
    if (selectedId === null) {
      return;
    }
    setNodes((current) =>
      current.map((node) =>
        node.id === selectedId
          ? {
              ...node,
              data: {
                behaviorNode: updater(node.data.behaviorNode)
              }
            }
          : node
      )
    );
  }

  function loadPreset(preset: PresetGraphView) {
    const flow = graphToFlow(preset.graph);
    setNodes(flow.nodes);
    setEdges(flow.edges);
    setSelectedId(flow.nodes[0]?.id ?? null);
    nextNodeId.current = flow.nodes.length + 1;
  }

  function applyGraph() {
    onApply(flowToGraph(nodes, edges));
    onClose();
  }

  const behaviorErrors = validationErrors.filter((issue) =>
    ["NO_ROOT", "MULTIPLE_ROOTS", "ORPHAN_NODE", "CYCLE", "INVALID_PORT", "MISSING_PARAM", "OUT_OF_RANGE", "TOO_DEEP", "TOO_MANY_NODES", "TOO_COMPLEX", "UNKNOWN_NODE", "DUPLICATE_NODE", "BEHAVIOR_TOO_SIMPLE"].includes(
      issue.code
    )
  );

  return (
    <div className="behavior-modal" role="dialog" aria-modal="true">
      <div className="behavior-modal-card">
        <header className="behavior-modal-header">
          <div>
            <span className="eyebrow">Редактор поведения</span>
            <h2>{presets.find((preset) => preset.id === presetId)?.name ?? "Свой граф"}</h2>
          </div>
          <div className="behavior-modal-actions">
            <button className="secondary" onClick={onClose}>
              Отмена
            </button>
            <button className="primary" onClick={applyGraph}>
              Применить
            </button>
          </div>
        </header>

        <div className="behavior-editor-layout">
          <aside className="behavior-palette">
            <h3>Группы</h3>
            <div className="node-palette">
              {Array.from(compositeTypes).map((type) => (
                <button key={type} className="node-palette-button" onClick={() => addNode(type)}>
                  {nodeTypeLabel(type)}
                </button>
              ))}
            </div>
            <h3>Условия</h3>
            <div className="node-palette">
              {conditionTypes.map((type) => (
                <button key={type} className="node-palette-button" onClick={() => addNode(type)}>
                  {nodeTypeLabel(type)}
                </button>
              ))}
            </div>
            <h3>Действия</h3>
            <div className="node-palette">
              {actionTypes.map((type) => (
                <button key={type} className="node-palette-button" onClick={() => addNode(type)}>
                  {nodeTypeLabel(type)}
                </button>
              ))}
            </div>
          </aside>

          <section className="behavior-canvas">
            <ReactFlow
              nodes={nodes}
              edges={edges}
              nodeTypes={nodeTypes}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onConnect={handleConnect}
              onNodeClick={(_, node) => setSelectedId(node.id)}
              onNodesDelete={(deleted) => {
                const deletedIds = new Set(deleted.map((node) => node.id));
                setEdges((current) => current.filter((edge) => !deletedIds.has(edge.source) && !deletedIds.has(edge.target)));
                if (selectedId !== null && deletedIds.has(selectedId)) {
                  setSelectedId(null);
                }
              }}
              fitView
              colorMode="dark"
              proOptions={{ hideAttribution: true }}
            >
              <Background variant={BackgroundVariant.Dots} gap={18} size={1} />
              <Controls />
              <MiniMap />
            </ReactFlow>
          </section>

          <aside className="behavior-inspector">
            <div className="panel-header">
              <h3>Инспектор</h3>
              <span className="muted">{selectedNode?.data.behaviorNode.type ?? "не выбран"}</span>
            </div>
            {selectedNode === null && <p className="muted">Выбери узел, чтобы изменить его.</p>}
            {selectedNode !== null && (
              <div className="behavior-inspector-body">
                <label>
                  ID узла
                  <input value={selectedNode.id} readOnly />
                </label>
                <label>
                  Тип
                  <input value={nodeTypeLabel(selectedNode.data.behaviorNode.type)} readOnly />
                </label>
                {targetTypes.has(selectedNode.data.behaviorNode.type) && (
                  <label>
                    Цель
                    <select
                      value={selectedNode.data.behaviorNode.text || defaultTarget(selectedNode.data.behaviorNode.type)}
                      onChange={(event) =>
                        updateSelectedBehavior((node) => ({ ...node, text: event.target.value }))
                      }
                    >
                      <option value="enemy.nearest">Ближайший враг</option>
                      <option value="enemyHive">Улей врага</option>
                      <option value="ally.nearest">Ближайший союзник</option>
                      <option value="hive">Свой улей</option>
                      <option value="ACID">Кислота</option>
                      <option value="TOXIC">Яд</option>
                    </select>
                  </label>
                )}
                {(paramDefinitions[selectedNode.data.behaviorNode.type] ?? []).map((param) => (
                  <label key={param.key}>
                    {param.label}
                    <input
                      type="number"
                      value={selectedNode.data.behaviorNode.params[param.key] ?? param.defaultValue}
                      onChange={(event) =>
                        updateSelectedBehavior((node) => ({
                          ...node,
                          params: {
                            ...node.params,
                            [param.key]: Number(event.target.value)
                          }
                        }))
                      }
                    />
                  </label>
                ))}
                <button
                  className="secondary"
                  onClick={() => {
                    if (selectedId === null) {
                      return;
                    }
                    setNodes((current) => current.filter((node) => node.id !== selectedId));
                    setEdges((current) =>
                      current.filter((edge) => edge.source !== selectedId && edge.target !== selectedId)
                    );
                    setSelectedId(null);
                  }}
                >
                  Удалить узел
                </button>
              </div>
            )}

            <h3>Пресеты</h3>
            <div className="behavior-preset-list">
              {presetGraphs.map((preset) => (
                <button key={preset.presetId} className="template-button" onClick={() => loadPreset(preset)}>
                  {presets.find((item) => item.id === preset.presetId)?.name ?? preset.presetId}
                </button>
              ))}
            </div>

            <h3>Проверка</h3>
            {behaviorErrors.length === 0 && <p className="muted">Ошибок нет.</p>}
            {behaviorErrors.map((issue) => (
              <p key={`${issue.code}-${issue.nodeId}-${issue.message}`} className="notice">
                {issueText(issue.code)}
              </p>
            ))}
          </aside>
        </div>
      </div>
    </div>
  );
}

function BehaviorNodeView({ data, selected }: NodeProps<BehaviorFlowNode>) {
  const node = data.behaviorNode;
  const color = compositeTypes.has(node.type)
    ? "composite"
    : conditionTypes.includes(node.type)
      ? "condition"
      : "action";
  return (
    <div className={`behavior-node ${color} ${selected ? "selected" : ""}`}>
      <Handle type="target" position={Position.Top} />
      <span className="behavior-node-type">{nodeTypeLabel(node.type)}</span>
      {node.text !== "" && <span className="behavior-node-target">{node.text}</span>}
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

function nodeTypeLabel(type: string): string {
  return {
    Selector: "Выбор",
    Sequence: "Последовательность",
    UtilitySelector: "Выбор по приоритету",
    RandomSelector: "Случайный выбор",
    Parallel: "Параллельно",
    Cooldown: "Перезарядка",
    Repeat: "Повтор",
    Inverter: "Инверсия",
    HasTarget: "Есть цель",
    TargetInRange: "Цель в радиусе",
    TargetVisible: "Цель видна",
    HpBelow: "HP ниже",
    HpAbove: "HP выше",
    EnergyBelow: "Энергия ниже",
    EnergyAbove: "Энергия выше",
    EnemyCount: "Врагов рядом",
    AllyCount: "Союзников рядом",
    IsUnderAttack: "Под атакой",
    HiveUnderAttack: "Улей атакован",
    HiveHpBelow: "HP улья ниже",
    EnemyHiveInRange: "Улей врага в радиусе",
    TimerElapsed: "Время прошло",
    RandomChance: "Случайный шанс",
    ResourceNearby: "Ресурс рядом",
    CorpseNearby: "Труп рядом",
    MoveTo: "Идти к",
    MoveToPoint: "Идти в точку",
    AttackTarget: "Атаковать цель",
    AttackMove: "Идти и атаковать",
    Flee: "Отступить",
    Follow: "Следовать",
    Regroup: "Собраться",
    HoldPosition: "Держать позицию",
    Patrol: "Патруль",
    Guard: "Охранять",
    Harvest: "Собрать",
    EatCorpse: "Съесть труп",
    Wait: "Ждать"
  }[type] ?? type;
}

function issueText(code: string): string {
  return {
    NO_ROOT: "Нужен корневой узел",
    MULTIPLE_ROOTS: "В схеме несколько корневых узлов",
    ORPHAN_NODE: "Есть узел без связи",
    CYCLE: "В схеме есть цикл",
    INVALID_PORT: "Такое соединение не подходит",
    MISSING_PARAM: "Не хватает параметра",
    OUT_OF_RANGE: "Параметр вне диапазона",
    TOO_DEEP: "Схема слишком глубокая",
    TOO_MANY_NODES: "Слишком много узлов",
    TOO_COMPLEX: "Схема слишком сложная",
    UNKNOWN_NODE: "Неизвестный узел",
    DUPLICATE_NODE: "Повторяющийся узел",
    UNKNOWN_SUBTREE: "Неизвестная подсхема",
    BEHAVIOR_TOO_SIMPLE: "Схема очень простая — это нормально"
  }[code] ?? "Проверь схему";
}

function emptyGraph(): BehaviorGraph {
  return {
    schemaVersion: 1,
    rootId: "root",
    parameters: {},
    nodes: [
      {
        id: "root",
        type: "UtilitySelector",
        children: [],
        params: {},
        text: ""
      }
    ]
  };
}

function defaultNode(id: string, type: string): BehaviorNode {
  const params = Object.fromEntries(
    (paramDefinitions[type] ?? []).map((param) => [param.key, param.defaultValue])
  );
  return {
    id,
    type,
    children: [],
    params,
    text: targetTypes.has(type) ? defaultTarget(type) : ""
  };
}

function defaultTarget(type: string): string {
  if (type === "AttackTarget" || type === "MoveTo" || type === "AttackMove") {
    return "enemy.nearest";
  }
  if (type === "HasStatus") {
    return "ACID";
  }
  return "";
}

function graphToFlow(graph: BehaviorGraph): { nodes: BehaviorFlowNode[]; edges: Edge[] } {
  const nodesById = new Map(graph.nodes.map((node) => [node.id, node]));
  const levelCounts = new Map<number, number>();
  const positions = new Map<string, { x: number; y: number }>();
  const queue: Array<{ id: string; depth: number }> = [{ id: graph.rootId, depth: 0 }];
  const seen = new Set<string>();
  while (queue.length > 0) {
    const current = queue.shift();
    if (current === undefined || seen.has(current.id)) {
      continue;
    }
    seen.add(current.id);
    const index = levelCounts.get(current.depth) ?? 0;
    levelCounts.set(current.depth, index + 1);
    positions.set(current.id, {
      x: current.depth * 250,
      y: index * 96
    });
    const node = nodesById.get(current.id);
    node?.children.forEach((childId) => queue.push({ id: childId, depth: current.depth + 1 }));
  }
  graph.nodes
    .filter((node) => !seen.has(node.id))
    .forEach((node, index) => {
      positions.set(node.id, { x: 0, y: (levelCounts.get(0) ?? 0 + index) * 96 + 96 });
    });

  return {
    nodes: graph.nodes.map((node) => ({
      id: node.id,
      type: "behavior",
      position: positions.get(node.id) ?? { x: 0, y: 0 },
      data: { behaviorNode: node }
    })),
    edges: graph.nodes.flatMap((node) =>
      node.children.map((childId) => ({
        id: `e-${node.id}-${childId}`,
        source: node.id,
        target: childId,
        type: "smoothstep"
      }))
    )
  };
}

function flowToGraph(nodes: BehaviorFlowNode[], edges: Edge[]): BehaviorGraph {
  const childrenByParent = new Map<string, string[]>();
  edges.forEach((edge) => {
    const children = childrenByParent.get(edge.source) ?? [];
    if (!children.includes(edge.target)) {
      children.push(edge.target);
    }
    childrenByParent.set(edge.source, children);
  });
  const childIds = new Set(edges.map((edge) => edge.target));
  const root = nodes.find((node) => !childIds.has(node.id)) ?? nodes[0];
  return {
    schemaVersion: 1,
    rootId: root?.id ?? "root",
    parameters: {},
    nodes: nodes.map((node) => ({
      id: node.id,
      type: node.data.behaviorNode.type,
      children: compositeTypes.has(node.data.behaviorNode.type)
        ? childrenByParent.get(node.id) ?? []
        : [],
      params: node.data.behaviorNode.params,
      text: node.data.behaviorNode.text
    }))
  };
}
