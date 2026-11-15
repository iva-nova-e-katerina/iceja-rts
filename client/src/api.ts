import type {
  ArmyView,
  Catalog,
  CreatureView,
  Genome,
  GuestSession,
  MatchView,
  ReplayView,
  TicketResponse,
  ValidationResult,
  ArmyUnitInput
} from "./types";

const PLAYER_ID_KEY = "iceja.playerId";
const NICKNAME_KEY = "iceja.nickname";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: unknown;

  constructor(status: number, code: string, message: string, details: unknown = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export async function ensureSession(): Promise<GuestSession> {
  const storedPlayerId = localStorage.getItem(PLAYER_ID_KEY);
  const storedNickname = localStorage.getItem(NICKNAME_KEY);
  if (storedPlayerId !== null && storedNickname !== null) {
    return { playerId: storedPlayerId, nickname: storedNickname };
  }
  const response = await fetch("/api/v1/auth/guest", { method: "POST" });
  if (!response.ok) {
    throw new ApiError(response.status, "GUEST_FAILED", "Cannot create guest session");
  }
  const session = (await response.json()) as GuestSession;
  localStorage.setItem(PLAYER_ID_KEY, session.playerId);
  localStorage.setItem(NICKNAME_KEY, session.nickname);
  return session;
}

export function getPlayerId(): string {
  return localStorage.getItem(PLAYER_ID_KEY) ?? "local";
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("X-Player-Id", getPlayerId());
  if (init.body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  const response = await fetch(path, { ...init, headers });
  if (!response.ok) {
    let code = "REQUEST_FAILED";
    let message = `HTTP ${response.status}`;
    let details: unknown = null;
    try {
      const body = (await response.json()) as {
        code?: string;
        message?: string;
        details?: unknown;
      };
      code = body.code ?? code;
      message = body.message ?? message;
      details = body.details ?? null;
    } catch {
      details = await response.text().catch(() => null);
    }
    throw new ApiError(response.status, code, message, details);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export async function getCatalog(): Promise<Catalog> {
  return request<Catalog>("/api/v1/catalog");
}

export async function validateGenome(genome: Genome): Promise<ValidationResult> {
  return request<ValidationResult>("/api/v1/creatures/validate", {
    method: "POST",
    body: JSON.stringify({ genome })
  });
}

export async function createCreature(genome: Genome): Promise<CreatureView> {
  return request<CreatureView>("/api/v1/creatures", {
    method: "POST",
    body: JSON.stringify({ genome })
  });
}

export async function createCreatureFromTemplate(templateName: string, name: string): Promise<CreatureView> {
  return request<CreatureView>("/api/v1/creatures/from-template", {
    method: "POST",
    body: JSON.stringify({ templateName, name })
  });
}

export async function listCreatures(): Promise<CreatureView[]> {
  return request<CreatureView[]>("/api/v1/creatures");
}

export async function createArmy(name: string, units: ArmyUnitInput[]): Promise<ArmyView> {
  return request<ArmyView>("/api/v1/armies", {
    method: "POST",
    body: JSON.stringify({ name, units })
  });
}

export async function listArmies(): Promise<ArmyView[]> {
  return request<ArmyView[]>("/api/v1/armies");
}

export async function createMatch(armyId: string, mapId: string): Promise<MatchView> {
  return request<MatchView>("/api/v1/matches", {
    method: "POST",
    body: JSON.stringify({ armyId, mapId, opponent: "bot" })
  });
}

export async function listMatches(): Promise<MatchView[]> {
  return request<MatchView[]>("/api/v1/matches");
}

export async function getMatch(matchId: string): Promise<MatchView> {
  return request<MatchView>(`/api/v1/matches/${matchId}`);
}

export async function getReplay(replayId: string): Promise<ReplayView> {
  return request<ReplayView>(`/api/v1/replays/${replayId}`);
}

export async function getWsTicket(): Promise<TicketResponse> {
  return request<TicketResponse>("/api/v1/ws/ticket", { method: "POST" });
}

export function matchWebSocketUrl(matchId: string, ticket: string): string {
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  return `${protocol}://${window.location.host}/ws/matches/${matchId}?ticket=${encodeURIComponent(ticket)}`;
}
