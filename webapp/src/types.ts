export type MonitoringStatus =
  | "STOPPED"
  | "CHECKING"
  | "CONNECTED_ONLINE"
  | "ALARMING"
  | "MUTED";

export type Locale = "en" | "es";

export interface ProbeInfo {
  status: MonitoringStatus;
  consecutiveFailures: number;
  latencyMs: number | null;
  lastCheckedTimestamp: number;
  isNetworkOnline: boolean;
  errorMessage: string | null;
  lastSyncTimestamp: number;
  lastSyncError: string | null;
  isBroadcastConfigured: boolean;
  isBroadcastEnabled: boolean;
}

export interface CloudSettings {
  serverUrl: string;
  groupCode: string;
  deviceName: string;
  deviceId: string;
  broadcastEnabled: boolean;
}

export interface RemoteDevice {
  deviceId: string;
  name: string;
  isOnline: boolean;
  lastSeenMs: number;
  wifiSsid: string | null;
  latencyMs: number | null;
  batteryPct: number | null;
  isCharging: boolean;
}

export interface DevicesResponse {
  serverTimeMs: number;
  onlineTimeoutMs: number;
  devices: RemoteDevice[];
}

export interface DevicesUiState {
  isLoading: boolean;
  devices: RemoteDevice[];
  serverTimeMs: number;
  lastUpdatedMs: number;
  errorMessage: string | null;
}

export const MIN_GROUP_CODE_LENGTH = 6;
export const MAX_GROUP_CODE_LENGTH = 128;
export const PROBE_INTERVAL_MS = 5_000;
export const HEARTBEAT_INTERVAL_MS = 15_000;
export const DEVICES_REFRESH_INTERVAL_MS = 10_000;
export const FAILURE_THRESHOLD = 2;
