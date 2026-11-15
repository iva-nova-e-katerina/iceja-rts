export type SocketType = "CORE" | "FRONT" | "BACK" | "LEFT" | "RIGHT" | "TOP" | "UNDER";

export type PartVisibility = "INTERNAL" | "EXTERNAL";

export type PartShape =
  | "LEG"
  | "WING"
  | "CLAW"
  | "FANG"
  | "SPIKE"
  | "TAIL"
  | "HAMMER"
  | "EYE"
  | "CHEMO"
  | "WHISKER"
  | "ORB"
  | "PLATE"
  | "NOZZLE"
  | "MOUTH"
  | "GLAND"
  | "LURE"
  | "VENT"
  | "FIN"
  | "SLIME"
  | "SKIN"
  | "HARPOON"
  | "MORTAR"
  | "BURROW_CLAW";

export type PartCategory =
  | "CORE"
  | "LOCOMOTION"
  | "SENSOR"
  | "MELEE"
  | "RANGED"
  | "DEFENSE"
  | "UTILITY";

export type DamageType = "KINETIC" | "PIERCING" | "THERMAL" | "ACID" | "ELECTRIC" | "TOXIC";

export type PartFlag =
  | "BRAIN"
  | "HEART"
  | "METABOLIC"
  | "NECTAR_CROP"
  | "SYMBIOTE"
  | "SCAVENGER"
  | "BURROW"
  | "FLIGHT"
  | "CAMOUFLAGE"
  | "STICKY"
  | "PHEROMONE"
  | "ADRENAL"
  | "NEUROLINK"
  | "LURE"
  | "OVIPOSITOR";

export type UpgradeStat = "HP" | "DAMAGE" | "ARMOR" | "SPEED" | "ENERGY" | "ACCURACY" | "CONTROL";

export interface ArmorProfile {
  kinetic: number;
  piercing: number;
  thermal: number;
  acid: number;
  electric: number;
  toxic: number;
}

export interface WeaponProfile {
  damage: number;
  cooldownTicks: number;
  rangeMm: number;
  damageType: DamageType;
  accuracy: number;
  projectileSpeedMmPerTick: number;
  splashRadiusMm: number;
  dotDamagePerSecond: number;
  dotDurationTicks: number;
  penetration: number;
  complexity: number;
  ranged: boolean;
}

export interface PartDefinition {
  id: string;
  name: string;
  category: PartCategory;
  socket: SocketType;
  cost: number;
  massKg: number;
  hpBonus: number;
  energyBonus: number;
  regenPerSecond: number;
  regenPercentPerSecond: number;
  controlBonus: number;
  controlCost: number;
  traction: number;
  visionRangeMm: number;
  visionAngleDeg: number;
  weapon: WeaponProfile | null;
  armor: ArmorProfile;
  evasionBonus: number;
  accuracyBonus: number;
  speedMultiplierPct: number;
  turnMultiplierPct: number;
  energyDrainMilliPerSecond: number;
  flags: PartFlag[];
  visual: {
    visibility: PartVisibility;
    shape: PartShape;
    scale: number;
  };
  description: string;
}

export interface SocketCapacity {
  type: SocketType;
  count: number;
}

export interface ChassisDefinition {
  id: string;
  name: string;
  sizeClass: string;
  massKg: number;
  hp: number;
  cost: number;
  sockets: SocketCapacity[];
  comfortableMassKg: number;
  hardMassKg: number;
  baseRadiusMm: number;
  canFly: boolean;
  canBurrow: boolean;
  isStatic: boolean;
}

export interface Obstacle {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface SpawnPoint {
  x: number;
  y: number;
}

export interface MapTheme {
  background: number;
  grid: number;
  obstacle: number;
  obstacleStroke: number;
  border: number;
}

export interface MapDefinition {
  id: string;
  name: string;
  widthMm: number;
  heightMm: number;
  spawnA: SpawnPoint;
  spawnB: SpawnPoint;
  deployRadiusMm: number;
  obstacles: Obstacle[];
  theme: MapTheme;
}

export interface Ruleset {
  version: string;
  tickRate: number;
  maxCreaturesPerArmy: number;
  armyBudgetBm: number;
  creatureMinCostBm: number;
  creatureMaxCostBm: number;
  matchDurationTicks: number;
  overtimeTicks: number;
  hiveHp: number;
  hiveRadiusMm: number;
  hiveArmor: number;
}

export interface BehaviorPresetDefinition {
  id: string;
  name: string;
  cp: number;
  description: string;
  aggression: number;
  caution: number;
  cohesion: number;
  greed: number;
  loyalty: number;
  focus: number;
  adaptability: number;
}

export interface Offset {
  x: number;
  y: number;
}

export interface PartPlacement {
  partId: string;
  socketId: SocketType;
  offset: Offset;
  rotation: number;
  mirrored: boolean;
}

export interface UpgradePlacement {
  stat: UpgradeStat;
  level: number;
}

export interface BehaviorNode {
  id: string;
  type: string;
  children: string[];
  params: Record<string, number>;
  text: string;
}

export interface BehaviorGraph {
  schemaVersion: number;
  rootId: string;
  parameters: Record<string, number>;
  nodes: BehaviorNode[];
}

export interface BehaviorSpec {
  presetId: string;
  graph: BehaviorGraph | null;
}

export interface Genome {
  schemaVersion: number;
  chassisId: string;
  name: string;
  parts: PartPlacement[];
  upgrades: UpgradePlacement[];
  behavior: BehaviorSpec;
  paletteId: string;
  catalogVersion: string;
  rulesetVersion: string;
}

export type ValidationSeverity = "ERROR" | "WARNING" | "INFO";

export interface ValidationIssue {
  code: string;
  message: string;
  severity: ValidationSeverity;
  nodeId: string;
}

export interface WeaponStats {
  partId: string;
  damage: number;
  cooldownTicks: number;
  rangeMm: number;
  damageType: DamageType;
  accuracy: number;
  projectileSpeedMmPerTick: number;
  splashRadiusMm: number;
  dotDamagePerSecond: number;
  dotDurationTicks: number;
  penetration: number;
  ranged: boolean;
}

export interface SocketUsage {
  type: SocketType;
  used: number;
  capacity: number;
}

export interface DerivedStats {
  maxHp: number;
  massKg: number;
  costBm: number;
  maxEnergy: number;
  regenMilliPerSecond: number;
  hpRegenPercentPerSecond: number;
  upkeepMilliPerSecond: number;
  speedMmPerTick: number;
  accelMmPerSecond2: number;
  turnRateDegPerSecond: number;
  controlCapacity: number;
  controlLoad: number;
  decisionPenaltyTicks: number;
  armor: ArmorProfile;
  visionRangeMm: number;
  visionAngleDeg: number;
  accuracyBonus: number;
  evasion: number;
  radiusMm: number;
  behaviorCp: number;
  weapons: WeaponStats[];
  flags: PartFlag[];
  sockets: SocketUsage[];
}

export interface ValidationResult {
  valid: boolean;
  errors: ValidationIssue[];
  warnings: ValidationIssue[];
  info: ValidationIssue[];
  stats: DerivedStats;
}

export interface UpgradeRuleView {
  stat: UpgradeStat;
  basePrice: number;
  growthPercent: number;
  maxLevel: number;
}

export interface PresetGraphView {
  presetId: string;
  graph: BehaviorGraph;
}

export interface TemplateView {
  id: string;
  name: string;
  genome: Genome;
  validation: ValidationResult;
}

export interface Catalog {
  catalogVersion: string;
  ruleset: Ruleset;
  chassis: ChassisDefinition[];
  parts: PartDefinition[];
  behaviorPresets: BehaviorPresetDefinition[];
  maps: MapDefinition[];
  templates: TemplateView[];
  upgrades: UpgradeRuleView[];
  behaviorGraphs: PresetGraphView[];
}

export interface CreatureView {
  id: string;
  genome: Genome;
  stats: DerivedStats;
  validation: ValidationResult;
  version: number;
  createdAt: number;
}

export interface ArmyUnitInput {
  creatureId: string;
  x: number;
  y: number;
}

export interface ArmyUnitView {
  creatureId: string;
  name: string;
  costBm: number;
  x: number;
  y: number;
}

export interface ArmyView {
  id: string;
  name: string;
  units: ArmyUnitView[];
  totalCostBm: number;
  creatureCount: number;
}

export interface MatchPlayerView {
  playerId: number;
  nickname: string;
  hiveId: number;
}

export interface CreatureRenderPart {
  partId: string;
  socketId: SocketType;
  shape: PartShape;
  scale: number;
  mirrored: boolean;
}

export interface CreatureRenderView {
  chassisId: string;
  sizeClass: string;
  bodyRadiusMm: number;
  parts: CreatureRenderPart[];
}

export interface GenomeSummary {
  genomeId: number;
  name: string;
  chassisId: string;
  behaviorPresetId: string;
  costBm: number;
  radiusMm: number;
  weapons: string[];
  render: CreatureRenderView;
}

export interface EntitySnapshot {
  id: number;
  kind: string;
  owner: number;
  x: number;
  y: number;
  angle: number;
  hp: number;
  maxHp: number;
  energy: number;
  flags: number;
  state: number;
  targetId: number;
  genomeId: number;
  mode: string;
  reason: string;
  nodeId: string;
}

export interface ProjectileSnapshot {
  id: number;
  owner: number;
  x: number;
  y: number;
  angle: number;
  kind: number;
  ttl: number;
}

export interface HiveSnapshot {
  id: number;
  owner: number;
  x: number;
  y: number;
  hp: number;
  maxHp: number;
  flags: number;
}

export interface CorpseSnapshot {
  id: number;
  owner: number;
  x: number;
  y: number;
  genomeId: number;
  remainingTicks: number;
}

export interface Snapshot {
  tick: number;
  serverTimeMs: number;
  matchId: string;
  entities: EntitySnapshot[];
  projectiles: ProjectileSnapshot[];
  corpses: CorpseSnapshot[];
  hives: HiveSnapshot[];
  scores: number[];
  flags: number;
  checksum: number;
}

export interface SimEvent {
  id: number;
  tick: number;
  type: string;
  sourceId: number;
  targetId: number;
  x: number;
  y: number;
  value: number;
  text: string;
}

export interface PlayerMatchStats {
  playerId: number;
  nickname: string;
  damageDealt: number;
  kills: number;
  losses: number;
  survivingCreatures: number;
  hiveHp: number;
}

export interface MatchResult {
  winner: number;
  reason: string;
  durationTicks: number;
  stats: PlayerMatchStats[];
}

export interface MatchStartPayload {
  matchId: string;
  seed: number;
  mapId: string;
  rulesetVersion: string;
  players: MatchPlayerView[];
  genomes: GenomeSummary[];
  snapshot: Snapshot;
}

export interface MatchView {
  id: string;
  status: string;
  ownerId: string;
  opponent: string;
  createdAt: number;
  mapId: string;
  players: MatchPlayerView[];
  result: MatchResult | null;
  replayId: string;
}

export interface ReplayView {
  replayId: string;
  matchId: string;
  mapId: string;
  seed: number;
  rulesetVersion: string;
  genomes: GenomeSummary[];
  players: MatchPlayerView[];
  snapshots: Snapshot[];
  events: SimEvent[];
  result: MatchResult | null;
  durationTicks: number;
}

export interface TicketResponse {
  ticket: string;
  expiresIn: number;
}

export interface GuestSession {
  playerId: string;
  nickname: string;
}

export interface WsMessage {
  type: string;
  payload?: unknown;
}

export interface EventBatchPayload {
  matchId: string;
  tick: number;
  events: SimEvent[];
}

export interface MatchEndPayload {
  matchId: string;
  result: MatchResult;
  replayId: string;
}
