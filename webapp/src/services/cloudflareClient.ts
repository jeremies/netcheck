import type { CloudSettings, DevicesResponse } from "../types";
import { normalizeServerUrl } from "./settingsStorage";
import type { Translations } from "../i18n";

async function parseErrorMessage(res: Response, fallback: string): Promise<string> {
  try {
    const data = (await res.json()) as { error?: unknown };
    if (typeof data.error === "string" && data.error.trim()) {
      return data.error;
    }
  } catch {
    // Ignore JSON parse failure
  }
  return `${fallback} (HTTP ${res.status})`;
}

export async function checkHealth(serverUrl: string, t: Translations): Promise<number> {
  const baseUrl = normalizeServerUrl(serverUrl);
  if (!baseUrl) {
    throw new Error(t.errorEnterServerUrl);
  }
  const start = performance.now();
  const res = await fetch(`${baseUrl}/api/health`, {
    method: "GET",
    cache: "no-store"
  });
  const latencyMs = Math.round(performance.now() - start);
  if (!res.ok) {
    throw new Error(await parseErrorMessage(res, t.settingsTestFailed));
  }
  const data = (await res.json()) as { ok?: boolean };
  if (!data.ok) {
    throw new Error(t.errorUnexpectedHealthResponse);
  }
  return latencyMs;
}

export interface HeartbeatPayload {
  deviceId: string;
  name: string;
  wifiSsid: string | null;
  latencyMs: number | null;
  batteryPct: number | null;
  isCharging: boolean;
}

export async function sendHeartbeat(
  settings: CloudSettings,
  payload: HeartbeatPayload,
  t: Translations
): Promise<void> {
  const baseUrl = normalizeServerUrl(settings.serverUrl);
  if (!baseUrl) throw new Error(t.errorServerUrlNotConfigured);

  const res = await fetch(`${baseUrl}/api/heartbeat`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Group-Code": settings.groupCode.trim()
    },
    body: JSON.stringify(payload),
    cache: "no-store"
  });

  if (!res.ok) {
    throw new Error(await parseErrorMessage(res, "Heartbeat failed"));
  }
}

export async function sendOffline(
  settings: CloudSettings,
  keepalive = false
): Promise<void> {
  const baseUrl = normalizeServerUrl(settings.serverUrl);
  const groupCode = settings.groupCode.trim();
  if (!baseUrl || !groupCode) return;

  try {
    await fetch(`${baseUrl}/api/offline`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Group-Code": groupCode
      },
      body: JSON.stringify({ deviceId: settings.deviceId }),
      keepalive,
      cache: "no-store"
    });
  } catch {
    // Best-effort on stop/unload
  }
}

export async function fetchDevices(
  settings: CloudSettings,
  t: Translations
): Promise<DevicesResponse> {
  const baseUrl = normalizeServerUrl(settings.serverUrl);
  if (!baseUrl) throw new Error(t.errorServerUrlNotConfigured);

  const res = await fetch(`${baseUrl}/api/devices`, {
    method: "GET",
    headers: {
      "X-Group-Code": settings.groupCode.trim()
    },
    cache: "no-store"
  });

  if (!res.ok) {
    throw new Error(await parseErrorMessage(res, t.errorLoadDevices));
  }

  return (await res.json()) as DevicesResponse;
}

export async function deleteDevice(
  settings: CloudSettings,
  deviceId: string,
  t: Translations
): Promise<void> {
  const baseUrl = normalizeServerUrl(settings.serverUrl);
  if (!baseUrl) throw new Error(t.errorServerUrlNotConfigured);

  const res = await fetch(`${baseUrl}/api/devices/${encodeURIComponent(deviceId)}`, {
    method: "DELETE",
    headers: {
      "X-Group-Code": settings.groupCode.trim()
    },
    cache: "no-store"
  });

  if (!res.ok) {
    throw new Error(await parseErrorMessage(res, t.errorRemoveDevice));
  }
}
