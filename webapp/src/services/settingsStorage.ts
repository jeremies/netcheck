import { MIN_GROUP_CODE_LENGTH, type CloudSettings, type Locale } from "../types";
import { detectDefaultLocale } from "../i18n";

const STORAGE_KEY = "netcheck_cloud_settings_v1";
const LOCALE_KEY = "netcheck_locale_v1";
export const DEFAULT_SERVER_URL = "https://netcheck-server.jmonserrat-54c.workers.dev";

function generateUuid(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return "web-" + Math.random().toString(36).slice(2, 10) + "-" + Date.now().toString(36);
}

function defaultDeviceName(): string {
  if (typeof navigator === "undefined") return "Desktop Browser";
  const ua = navigator.userAgent;
  let browser = "Browser";
  if (ua.includes("Edg/")) browser = "Edge";
  else if (ua.includes("Chrome/")) browser = "Chrome";
  else if (ua.includes("Firefox/")) browser = "Firefox";
  else if (ua.includes("Safari/") && !ua.includes("Chrome/")) browser = "Safari";

  let os = "Desktop";
  if (ua.includes("Windows")) os = "Windows";
  else if (ua.includes("Mac OS X")) os = "macOS";
  else if (ua.includes("Linux")) os = "Linux";

  return `${browser} (${os})`;
}

export function normalizeServerUrl(url: string): string {
  return url.trim().replace(/\/+$/, "");
}

export function isSettingsConfigured(settings: CloudSettings): boolean {
  const url = normalizeServerUrl(settings.serverUrl);
  const code = settings.groupCode.trim();
  return (
    (url.startsWith("https://") || url.startsWith("http://")) &&
    code.length >= MIN_GROUP_CODE_LENGTH
  );
}

export function loadSettings(): CloudSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<CloudSettings>;
      const settings: CloudSettings = {
        serverUrl:
          typeof parsed.serverUrl === "string" && parsed.serverUrl.trim()
            ? parsed.serverUrl
            : DEFAULT_SERVER_URL,
        groupCode: typeof parsed.groupCode === "string" ? parsed.groupCode : "",
        deviceName:
          typeof parsed.deviceName === "string" && parsed.deviceName.trim()
            ? parsed.deviceName
            : defaultDeviceName(),
        deviceId:
          typeof parsed.deviceId === "string" && parsed.deviceId.trim()
            ? parsed.deviceId
            : generateUuid(),
        broadcastEnabled:
          typeof parsed.broadcastEnabled === "boolean" ? parsed.broadcastEnabled : true
      };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
      return settings;
    }
  } catch {
    // Fall through to defaults
  }

  const initial: CloudSettings = {
    serverUrl: DEFAULT_SERVER_URL,
    groupCode: "",
    deviceName: defaultDeviceName(),
    deviceId: generateUuid(),
    broadcastEnabled: true
  };
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(initial));
  } catch {
    // Ignore storage quota errors
  }
  return initial;
}

export function saveSettings(settings: CloudSettings): CloudSettings {
  const normalized: CloudSettings = {
    ...settings,
    serverUrl: normalizeServerUrl(settings.serverUrl) || DEFAULT_SERVER_URL,
    groupCode: settings.groupCode.trim(),
    deviceName: settings.deviceName.trim() || defaultDeviceName()
  };
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(normalized));
  } catch {
    // Ignore storage errors
  }
  return normalized;
}

export function loadLocale(): Locale {
  try {
    const stored = localStorage.getItem(LOCALE_KEY);
    if (stored === "en" || stored === "es") return stored;
  } catch {
    // Ignore
  }
  return detectDefaultLocale();
}

export function saveLocale(locale: Locale): void {
  try {
    localStorage.setItem(LOCALE_KEY, locale);
  } catch {
    // Ignore
  }
}

export function generateGroupCode(length = 12): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => alphabet[b % alphabet.length]).join("");
}
