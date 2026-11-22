import { beforeEach, describe, expect, it } from "vitest";
import { env, exports } from "cloudflare:workers";
import { applyD1Migrations } from "cloudflare:test";

const GROUP_ALPHA = "ALPHA-1234";
const GROUP_BRAVO = "BRAVO-5678";

async function sha256Hex(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

function apiRequest(path: string, init: RequestInit = {}): Request {
  return new Request(`https://netcheck.example${path}`, init);
}

async function postHeartbeat(
  groupCode: string,
  payload: Record<string, unknown>
): Promise<Response> {
  return exports.default.fetch(
    apiRequest("/api/heartbeat", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Group-Code": groupCode
      },
      body: JSON.stringify(payload)
    })
  );
}

describe("netcheck-server Worker", () => {
  beforeEach(async () => {
    await applyD1Migrations(env.DB, env.TEST_MIGRATIONS);
    await env.DB.exec("DELETE FROM devices;");
  });

  it("returns healthy status on GET /api/health", async () => {
    const res = await exports.default.fetch(apiRequest("/api/health"));
    expect(res.status).toBe(200);
    const data = await res.json<{
      ok: boolean;
      service: string;
      serverTimeMs: number;
      onlineTimeoutMs: number;
    }>();
    expect(data.ok).toBe(true);
    expect(data.service).toBe("netcheck-server");
    expect(data.onlineTimeoutMs).toBe(45_000);
    expect(typeof data.serverTimeMs).toBe("number");
  });

  it("rejects missing or too-short X-Group-Code with 401", async () => {
    const missingRes = await exports.default.fetch(apiRequest("/api/devices"));
    expect(missingRes.status).toBe(401);

    const shortRes = await exports.default.fetch(
      apiRequest("/api/devices", {
        headers: { "X-Group-Code": "12345" }
      })
    );
    expect(shortRes.status).toBe(401);
  });

  it("isolates devices strictly by group code", async () => {
    const hbA = await postHeartbeat(GROUP_ALPHA, {
      deviceId: "dev-a",
      name: "Pixel 8"
    });
    expect(hbA.status).toBe(200);

    const hbB = await postHeartbeat(GROUP_BRAVO, {
      deviceId: "dev-b",
      name: "Galaxy S24"
    });
    expect(hbB.status).toBe(200);

    const listARes = await exports.default.fetch(
      apiRequest("/api/devices", { headers: { "X-Group-Code": GROUP_ALPHA } })
    );
    expect(listARes.status).toBe(200);
    const listA = await listARes.json<{ devices: Array<{ deviceId: string; name: string }> }>();
    expect(listA.devices).toHaveLength(1);
    expect(listA.devices[0].deviceId).toBe("dev-a");

    const listBRes = await exports.default.fetch(
      apiRequest("/api/devices", { headers: { "X-Group-Code": GROUP_BRAVO } })
    );
    const listB = await listBRes.json<{ devices: Array<{ deviceId: string; name: string }> }>();
    expect(listB.devices).toHaveLength(1);
    expect(listB.devices[0].deviceId).toBe("dev-b");
  });

  it("upserts telemetry on heartbeat without duplicating rows", async () => {
    const first = await postHeartbeat(GROUP_ALPHA, {
      deviceId: "dev-1",
      name: "Pixel 7",
      wifiSsid: "<unknown ssid>",
      latencyMs: 42,
      batteryPct: 78,
      isCharging: false
    });
    expect(first.status).toBe(200);

    const second = await postHeartbeat(GROUP_ALPHA, {
      deviceId: "dev-1",
      name: "Pixel 7 Pro",
      wifiSsid: "HomeFiber",
      latencyMs: 19,
      batteryPct: 81,
      isCharging: true
    });
    expect(second.status).toBe(200);

    const listRes = await exports.default.fetch(
      apiRequest("/api/devices", { headers: { "X-Group-Code": GROUP_ALPHA } })
    );
    const data = await listRes.json<{
      devices: Array<{
        deviceId: string;
        name: string;
        isOnline: boolean;
        wifiSsid: string | null;
        latencyMs: number | null;
        batteryPct: number | null;
        isCharging: boolean;
      }>;
    }>();

    expect(data.devices).toHaveLength(1);
    expect(data.devices[0]).toMatchObject({
      deviceId: "dev-1",
      name: "Pixel 7 Pro",
      isOnline: true,
      wifiSsid: "HomeFiber",
      latencyMs: 19,
      batteryPct: 81,
      isCharging: true
    });
  });

  it("marks timed-out devices offline and sorts online devices first", async () => {
    await postHeartbeat(GROUP_ALPHA, {
      deviceId: "dev-online",
      name: "Online Phone"
    });

    const roomHash = await sha256Hex(GROUP_ALPHA);
    const staleTime = Date.now() - 50_000;
    await env.DB.prepare(
      `INSERT INTO devices (
         room_hash, device_id, name, wifi_ssid, latency_ms, battery_pct,
         is_charging, explicitly_offline, last_seen_ms, created_at_ms
       ) VALUES (?, ?, ?, NULL, 30, 50, 0, 0, ?, ?)`
    )
      .bind(roomHash, "dev-stale", "Stale Phone", staleTime, staleTime)
      .run();

    const listRes = await exports.default.fetch(
      apiRequest("/api/devices", { headers: { "X-Group-Code": GROUP_ALPHA } })
    );
    const data = await listRes.json<{
      devices: Array<{ deviceId: string; isOnline: boolean }>;
    }>();

    expect(data.devices).toHaveLength(2);
    expect(data.devices[0]).toMatchObject({ deviceId: "dev-online", isOnline: true });
    expect(data.devices[1]).toMatchObject({ deviceId: "dev-stale", isOnline: false });
  });

  it("handles explicit offline and subsequent heartbeat recovery", async () => {
    await postHeartbeat(GROUP_ALPHA, {
      deviceId: "dev-toggle",
      name: "Spare Monitor"
    });

    const offlineRes = await exports.default.fetch(
      apiRequest("/api/offline", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Group-Code": GROUP_ALPHA
        },
        body: JSON.stringify({ deviceId: "dev-toggle" })
      })
    );
    expect(offlineRes.status).toBe(200);

    let listRes = await exports.default.fetch(
      apiRequest("/api/devices", { headers: { "X-Group-Code": GROUP_ALPHA } })
    );
    let data = await listRes.json<{ devices: Array<{ deviceId: string; isOnline: boolean }> }>();
    expect(data.devices[0].isOnline).toBe(false);

    await postHeartbeat(GROUP_ALPHA, {
      deviceId: "dev-toggle",
      name: "Spare Monitor"
    });

    listRes = await exports.default.fetch(
      apiRequest("/api/devices", { headers: { "X-Group-Code": GROUP_ALPHA } })
    );
    data = await listRes.json<{ devices: Array<{ deviceId: string; isOnline: boolean }> }>();
    expect(data.devices[0].isOnline).toBe(true);
  });

  it("deletes a device only within its own group", async () => {
    await postHeartbeat(GROUP_ALPHA, {
      deviceId: "dev-target",
      name: "Phone Alpha"
    });

    const wrongRoomDel = await exports.default.fetch(
      apiRequest("/api/devices/dev-target", {
        method: "DELETE",
        headers: { "X-Group-Code": GROUP_BRAVO }
      })
    );
    expect(wrongRoomDel.status).toBe(200);
    expect(await wrongRoomDel.json<{ deleted: boolean }>()).toEqual({
      ok: true,
      deviceId: "dev-target",
      deleted: false
    });

    const rightRoomDel = await exports.default.fetch(
      apiRequest("/api/devices/dev-target", {
        method: "DELETE",
        headers: { "X-Group-Code": GROUP_ALPHA }
      })
    );
    expect(rightRoomDel.status).toBe(200);
    expect(await rightRoomDel.json<{ deleted: boolean }>()).toEqual({
      ok: true,
      deviceId: "dev-target",
      deleted: true
    });

    const listRes = await exports.default.fetch(
      apiRequest("/api/devices", { headers: { "X-Group-Code": GROUP_ALPHA } })
    );
    const data = await listRes.json<{ devices: unknown[] }>();
    expect(data.devices).toHaveLength(0);
  });

  it("validates payloads and returns 400/404 for malformed requests", async () => {
    const badJsonRes = await exports.default.fetch(
      apiRequest("/api/heartbeat", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Group-Code": GROUP_ALPHA
        },
        body: "{not-json"
      })
    );
    expect(badJsonRes.status).toBe(400);

    const badFieldsRes = await postHeartbeat(GROUP_ALPHA, {
      deviceId: "dev-bad",
      name: "Phone",
      batteryPct: 150
    });
    expect(badFieldsRes.status).toBe(400);

    const notFoundRes = await exports.default.fetch(apiRequest("/api/unknown"));
    expect(notFoundRes.status).toBe(404);
  });
});
