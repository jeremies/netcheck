import type { Translations } from "../i18n";

export interface ProbeResult {
  isOnline: boolean;
  isNetworkInterfaceUp: boolean;
  latencyMs: number | null;
  errorMessage: string | null;
}

const CAPTIVE_CHECK_URL = "https://connectivitycheck.gstatic.com/generate_204";
const PROBE_TIMEOUT_MS = 3_500;

export async function probeConnectivity(t: Translations): Promise<ProbeResult> {
  if (typeof navigator !== "undefined" && !navigator.onLine) {
    return {
      isOnline: false,
      isNetworkInterfaceUp: false,
      latencyMs: null,
      errorMessage: t.errorBrowserOffline
    };
  }

  const controller = new AbortController();
  const timeoutId = window.setTimeout(() => controller.abort(), PROBE_TIMEOUT_MS);
  const start = performance.now();

  try {
    // mode: 'no-cors' returns an opaque response when reachable, and throws TypeError on network outage.
    await fetch(`${CAPTIVE_CHECK_URL}?_=${Date.now()}`, {
      method: "GET",
      mode: "no-cors",
      cache: "no-store",
      signal: controller.signal
    });
    const latencyMs = Math.max(1, Math.round(performance.now() - start));
    return {
      isOnline: true,
      isNetworkInterfaceUp: true,
      latencyMs,
      errorMessage: null
    };
  } catch {
    return {
      isOnline: false,
      isNetworkInterfaceUp: typeof navigator !== "undefined" ? navigator.onLine : true,
      latencyMs: null,
      errorMessage: t.errorProbeTimeout
    };
  } finally {
    clearTimeout(timeoutId);
  }
}

export interface BatteryInfo {
  batteryPct: number | null;
  isCharging: boolean;
}

interface BatteryManagerLike {
  level: number;
  charging: boolean;
}

export async function readBatteryInfo(): Promise<BatteryInfo> {
  try {
    const nav = navigator as Navigator & { getBattery?: () => Promise<BatteryManagerLike> };
    if (typeof nav.getBattery === "function") {
      const battery = await nav.getBattery();
      return {
        batteryPct: Math.round(battery.level * 100),
        isCharging: Boolean(battery.charging)
      };
    }
  } catch {
    // Battery API unavailable
  }
  return { batteryPct: null, isCharging: false };
}
