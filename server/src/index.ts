const ONLINE_TIMEOUT_MS = 45_000;
const MIN_GROUP_CODE_LENGTH = 6;
const MAX_GROUP_CODE_LENGTH = 128;
const MAX_BODY_BYTES = 4096;
const DEVICE_ID_REGEX = /^[A-Za-z0-9._:-]+$/;

interface DeviceRow {
  device_id: string;
  name: string;
  wifi_ssid: string | null;
  latency_ms: number | null;
  battery_pct: number | null;
  is_charging: number;
  explicitly_offline: number;
  last_seen_ms: number;
}

class HttpError extends Error {
  constructor(
    public readonly status: number,
    message: string
  ) {
    super(message);
  }
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store"
    }
  });
}

async function hashGroupCode(code: string): Promise<string> {
  const bytes = new TextEncoder().encode(code);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

async function requireRoomHash(request: Request): Promise<string> {
  const raw = request.headers.get("X-Group-Code");
  const code = raw?.trim() ?? "";
  if (code.length < MIN_GROUP_CODE_LENGTH || code.length > MAX_GROUP_CODE_LENGTH) {
    throw new HttpError(
      401,
      `Missing or invalid X-Group-Code header (must be ${MIN_GROUP_CODE_LENGTH}-${MAX_GROUP_CODE_LENGTH} characters)`
    );
  }
  return hashGroupCode(code);
}

async function readBoundedJson(request: Request): Promise<Record<string, unknown>> {
  const contentLengthHeader = request.headers.get("Content-Length");
  if (contentLengthHeader !== null) {
    const contentLength = Number(contentLengthHeader);
    if (Number.isFinite(contentLength) && contentLength > MAX_BODY_BYTES) {
      throw new HttpError(413, `Request body too large (max ${MAX_BODY_BYTES} bytes)`);
    }
  }

  const reader = request.body?.getReader();
  if (!reader) {
    throw new HttpError(400, "Request body is required");
  }

  const chunks: Uint8Array[] = [];
  let totalBytes = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    totalBytes += value.byteLength;
    if (totalBytes > MAX_BODY_BYTES) {
      await reader.cancel();
      throw new HttpError(413, `Request body too large (max ${MAX_BODY_BYTES} bytes)`);
    }
    chunks.push(value);
  }

  const combined = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    combined.set(chunk, offset);
    offset += chunk.byteLength;
  }

  const text = new TextDecoder().decode(combined);
  if (!text.trim()) {
    throw new HttpError(400, "Request body must be valid JSON");
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new HttpError(400, "Request body must be valid JSON");
  }

  if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new HttpError(400, "Request body must be a JSON object");
  }

  return parsed as Record<string, unknown>;
}

function validateDeviceId(value: unknown): string {
  if (typeof value !== "string") {
    throw new HttpError(400, "deviceId is required and must be a string");
  }
  const trimmed = value.trim();
  if (trimmed.length < 1 || trimmed.length > 128 || !DEVICE_ID_REGEX.test(trimmed)) {
    throw new HttpError(
      400,
      "deviceId must be 1-128 characters matching [A-Za-z0-9._:-]"
    );
  }
  return trimmed;
}

function validateName(value: unknown): string {
  if (typeof value !== "string") {
    throw new HttpError(400, "name is required and must be a string");
  }
  const trimmed = value.trim();
  if (trimmed.length < 1 || trimmed.length > 64) {
    throw new HttpError(400, "name must be 1-64 characters");
  }
  return trimmed;
}

function normalizeOptionalSsid(value: unknown): string | null {
  if (value === undefined || value === null) return null;
  if (typeof value !== "string") {
    throw new HttpError(400, "wifiSsid must be a string or null");
  }
  const trimmed = value.trim();
  if (!trimmed || trimmed === "<unknown ssid>") {
    return null;
  }
  return trimmed.slice(0, 64);
}

function validateOptionalInt(
  value: unknown,
  field: string,
  min: number,
  max: number
): number | null {
  if (value === undefined || value === null) return null;
  if (typeof value !== "number" || !Number.isInteger(value) || value < min || value > max) {
    throw new HttpError(400, `${field} must be an integer between ${min} and ${max}`);
  }
  return value;
}

function validateOptionalBool(value: unknown, field: string): boolean {
  if (value === undefined || value === null) return false;
  if (typeof value !== "boolean") {
    throw new HttpError(400, `${field} must be a boolean`);
  }
  return value;
}

async function handleHealth(env: Env): Promise<Response> {
  await env.DB.prepare("SELECT 1 AS ok").first();
  return jsonResponse({
    ok: true,
    service: "netcheck-server",
    serverTimeMs: Date.now(),
    onlineTimeoutMs: ONLINE_TIMEOUT_MS
  });
}

async function handleHeartbeat(request: Request, env: Env): Promise<Response> {
  const roomHash = await requireRoomHash(request);
  const body = await readBoundedJson(request);

  const deviceId = validateDeviceId(body.deviceId);
  const name = validateName(body.name);
  const wifiSsid = normalizeOptionalSsid(body.wifiSsid);
  const latencyMs = validateOptionalInt(body.latencyMs, "latencyMs", 0, 60_000);
  const batteryPct = validateOptionalInt(body.batteryPct, "batteryPct", 0, 100);
  const isCharging = validateOptionalBool(body.isCharging, "isCharging") ? 1 : 0;

  const now = Date.now();

  await env.DB.prepare(
    `INSERT INTO devices (
       room_hash, device_id, name, wifi_ssid, latency_ms, battery_pct,
       is_charging, explicitly_offline, last_seen_ms, created_at_ms
     ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
     ON CONFLICT (room_hash, device_id) DO UPDATE SET
       name = excluded.name,
       wifi_ssid = excluded.wifi_ssid,
       latency_ms = excluded.latency_ms,
       battery_pct = excluded.battery_pct,
       is_charging = excluded.is_charging,
       explicitly_offline = 0,
       last_seen_ms = excluded.last_seen_ms`
  )
    .bind(roomHash, deviceId, name, wifiSsid, latencyMs, batteryPct, isCharging, now, now)
    .run();

  return jsonResponse({
    ok: true,
    deviceId,
    serverTimeMs: now
  });
}

async function handleOffline(request: Request, env: Env): Promise<Response> {
  const roomHash = await requireRoomHash(request);
  const body = await readBoundedJson(request);
  const deviceId = validateDeviceId(body.deviceId);

  await env.DB.prepare(
    `UPDATE devices
     SET explicitly_offline = 1
     WHERE room_hash = ? AND device_id = ?`
  )
    .bind(roomHash, deviceId)
    .run();

  return jsonResponse({
    ok: true,
    deviceId,
    serverTimeMs: Date.now()
  });
}

async function handleListDevices(request: Request, env: Env): Promise<Response> {
  const roomHash = await requireRoomHash(request);
  const now = Date.now();

  const { results } = await env.DB.prepare(
    `SELECT device_id, name, wifi_ssid, latency_ms, battery_pct,
            is_charging, explicitly_offline, last_seen_ms
     FROM devices
     WHERE room_hash = ?
     ORDER BY last_seen_ms DESC`
  )
    .bind(roomHash)
    .all<DeviceRow>();

  const devices = results
    .map((row) => {
      const isOnline =
        row.explicitly_offline === 0 && now - row.last_seen_ms <= ONLINE_TIMEOUT_MS;
      return {
        deviceId: row.device_id,
        name: row.name,
        isOnline,
        lastSeenMs: row.last_seen_ms,
        wifiSsid: row.wifi_ssid,
        latencyMs: row.latency_ms,
        batteryPct: row.battery_pct,
        isCharging: row.is_charging === 1
      };
    })
    .sort((a, b) => {
      if (a.isOnline !== b.isOnline) {
        return a.isOnline ? -1 : 1;
      }
      return b.lastSeenMs - a.lastSeenMs;
    });

  return jsonResponse({
    serverTimeMs: now,
    onlineTimeoutMs: ONLINE_TIMEOUT_MS,
    devices
  });
}

async function handleDeleteDevice(
  request: Request,
  env: Env,
  rawDeviceId: string
): Promise<Response> {
  const roomHash = await requireRoomHash(request);

  let decoded: string;
  try {
    decoded = decodeURIComponent(rawDeviceId);
  } catch {
    throw new HttpError(400, "Invalid encoded deviceId");
  }
  const deviceId = validateDeviceId(decoded);

  const result = await env.DB.prepare(
    `DELETE FROM devices WHERE room_hash = ? AND device_id = ?`
  )
    .bind(roomHash, deviceId)
    .run();

  return jsonResponse({
    ok: true,
    deviceId,
    deleted: (result.meta.changes ?? 0) > 0
  });
}

export default {
  async fetch(request: Request, env: Env, _ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    const { pathname } = url;
    const method = request.method.toUpperCase();

    try {
      if (method === "GET" && pathname === "/api/health") {
        return await handleHealth(env);
      }
      if (method === "POST" && pathname === "/api/heartbeat") {
        return await handleHeartbeat(request, env);
      }
      if (method === "POST" && pathname === "/api/offline") {
        return await handleOffline(request, env);
      }
      if (method === "GET" && pathname === "/api/devices") {
        return await handleListDevices(request, env);
      }
      if (method === "DELETE" && pathname.startsWith("/api/devices/")) {
        const rawDeviceId = pathname.slice("/api/devices/".length);
        if (!rawDeviceId || rawDeviceId.includes("/")) {
          return jsonResponse({ error: "Not found" }, 404);
        }
        return await handleDeleteDevice(request, env, rawDeviceId);
      }

      return jsonResponse({ error: "Not found" }, 404);
    } catch (error) {
      if (error instanceof HttpError) {
        return jsonResponse({ error: error.message }, error.status);
      }
      const message = error instanceof Error ? error.message : String(error);
      console.error(
        JSON.stringify({
          message: "unhandled request error",
          method,
          path: pathname,
          error: message
        })
      );
      return jsonResponse({ error: "Internal server error" }, 500);
    }
  }
} satisfies ExportedHandler<Env>;
