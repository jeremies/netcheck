import { useCallback, useEffect, useRef, useState } from "react";
import {
  FAILURE_THRESHOLD,
  HEARTBEAT_INTERVAL_MS,
  PROBE_INTERVAL_MS,
  type CloudSettings,
  type ProbeInfo
} from "../types";
import type { Translations } from "../i18n";
import { AlarmController } from "../services/alarmController";
import { sendHeartbeat, sendOffline } from "../services/cloudflareClient";
import { probeConnectivity, readBatteryInfo } from "../services/networkProber";
import { isSettingsConfigured } from "../services/settingsStorage";

export function useNetCheckMonitor(settings: CloudSettings, t: Translations) {
  const configured = isSettingsConfigured(settings);

  const [probeState, setProbeState] = useState<ProbeInfo>(() => ({
    status: "STOPPED",
    consecutiveFailures: 0,
    latencyMs: null,
    lastCheckedTimestamp: 0,
    isNetworkOnline: typeof navigator !== "undefined" ? navigator.onLine : true,
    errorMessage: null,
    lastSyncTimestamp: 0,
    lastSyncError: null,
    isBroadcastConfigured: configured,
    isBroadcastEnabled: settings.broadcastEnabled
  }));

  const alarmRef = useRef<AlarmController | null>(null);
  if (!alarmRef.current) {
    alarmRef.current = new AlarmController();
  }

  const isRunningRef = useRef(false);
  const isMutedRef = useRef(false);
  const consecutiveFailuresRef = useRef(0);
  const lastHeartbeatSentRef = useRef(0);
  const settingsRef = useRef(settings);
  const tRef = useRef(t);

  settingsRef.current = settings;
  tRef.current = t;

  // Keep broadcast flags synced when settings change
  useEffect(() => {
    setProbeState((prev) => ({
      ...prev,
      isBroadcastConfigured: isSettingsConfigured(settings),
      isBroadcastEnabled: settings.broadcastEnabled
    }));
  }, [settings]);

  const maybeSendHeartbeat = useCallback(async (latencyMs: number | null, force = false) => {
    const currentSettings = settingsRef.current;
    if (!currentSettings.broadcastEnabled || !isSettingsConfigured(currentSettings)) {
      return;
    }
    const now = Date.now();
    if (!force && now - lastHeartbeatSentRef.current < HEARTBEAT_INTERVAL_MS) {
      return;
    }
    lastHeartbeatSentRef.current = now;

    try {
      const battery = await readBatteryInfo();
      await sendHeartbeat(
        currentSettings,
        {
          deviceId: currentSettings.deviceId,
          name: currentSettings.deviceName,
          wifiSsid: null,
          latencyMs,
          batteryPct: battery.batteryPct,
          isCharging: battery.isCharging
        },
        tRef.current
      );
      if (!isRunningRef.current) return;
      setProbeState((prev) => ({
        ...prev,
        lastSyncTimestamp: Date.now(),
        lastSyncError: null
      }));
    } catch (err) {
      if (!isRunningRef.current) return;
      const msg = err instanceof Error ? err.message : tRef.current.errorUnknown;
      setProbeState((prev) => ({
        ...prev,
        lastSyncError: msg
      }));
    }
  }, []);

  const runProbe = useCallback(async () => {
    if (!isRunningRef.current) return;
    const tr = tRef.current;
    const result = await probeConnectivity(tr);
    if (!isRunningRef.current) return;

    const now = Date.now();

    if (result.isOnline) {
      const wasOffline = consecutiveFailuresRef.current > 0;
      consecutiveFailuresRef.current = 0;
      isMutedRef.current = false;
      alarmRef.current?.stopAlarm();

      setProbeState((prev) => ({
        ...prev,
        status: "CONNECTED_ONLINE",
        consecutiveFailures: 0,
        latencyMs: result.latencyMs,
        lastCheckedTimestamp: now,
        isNetworkOnline: true,
        errorMessage: null
      }));

      void maybeSendHeartbeat(result.latencyMs, wasOffline || lastHeartbeatSentRef.current === 0);
    } else {
      const failures = consecutiveFailuresRef.current + 1;
      consecutiveFailuresRef.current = failures;

      let nextStatus: ProbeInfo["status"];
      if (!result.isNetworkInterfaceUp || failures >= FAILURE_THRESHOLD) {
        if (isMutedRef.current) {
          nextStatus = "MUTED";
          alarmRef.current?.stopAlarm();
        } else {
          nextStatus = "ALARMING";
          const body = result.isNetworkInterfaceUp
            ? tr.notifAlarmNoInternet
            : tr.notifAlarmDisconnected;
          alarmRef.current?.startAlarm(tr.notifAlarmTitle, body);
        }
      } else {
        // First transient failure: keep checking
        nextStatus = "CHECKING";
      }

      setProbeState((prev) => ({
        ...prev,
        status: nextStatus,
        consecutiveFailures: failures,
        latencyMs: null,
        lastCheckedTimestamp: now,
        isNetworkOnline: result.isNetworkInterfaceUp,
        errorMessage: result.errorMessage
      }));
    }
  }, [maybeSendHeartbeat]);

  const startMonitoring = useCallback(() => {
    if (isRunningRef.current) return;
    isRunningRef.current = true;
    isMutedRef.current = false;
    consecutiveFailuresRef.current = 0;
    lastHeartbeatSentRef.current = 0;

    alarmRef.current?.unlockAudio();
    void alarmRef.current?.requestNotificationPermission();

    setProbeState((prev) => ({
      ...prev,
      status: "CHECKING",
      consecutiveFailures: 0,
      latencyMs: null,
      errorMessage: null,
      lastSyncError: null
    }));

    void runProbe();
  }, [runProbe]);

  const stopMonitoring = useCallback(() => {
    if (!isRunningRef.current) return;
    isRunningRef.current = false;
    isMutedRef.current = false;
    consecutiveFailuresRef.current = 0;
    alarmRef.current?.stopAlarm();

    const currentSettings = settingsRef.current;
    if (currentSettings.broadcastEnabled && isSettingsConfigured(currentSettings)) {
      void sendOffline(currentSettings, false);
    }

    setProbeState((prev) => ({
      ...prev,
      status: "STOPPED",
      consecutiveFailures: 0,
      latencyMs: null,
      errorMessage: null
    }));
  }, []);

  const muteAlarm = useCallback(() => {
    if (!isRunningRef.current) return;
    isMutedRef.current = true;
    alarmRef.current?.stopAlarm();
    setProbeState((prev) => ({
      ...prev,
      status: prev.status === "ALARMING" ? "MUTED" : prev.status
    }));
  }, []);

  // Periodic 5s probe loop while monitoring
  useEffect(() => {
    if (probeState.status === "STOPPED") return;

    const intervalId = window.setInterval(() => {
      void runProbe();
    }, PROBE_INTERVAL_MS);

    const handleOnline = () => {
      void runProbe();
    };
    const handleOffline = () => {
      void runProbe();
    };

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);

    return () => {
      clearInterval(intervalId);
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, [probeState.status === "STOPPED", runProbe]);

  // Best-effort offline beacon on tab close
  useEffect(() => {
    const handleUnload = () => {
      if (isRunningRef.current) {
        alarmRef.current?.stopAlarm();
        const currentSettings = settingsRef.current;
        if (currentSettings.broadcastEnabled && isSettingsConfigured(currentSettings)) {
          void sendOffline(currentSettings, true);
        }
      }
    };
    window.addEventListener("beforeunload", handleUnload);
    return () => {
      window.removeEventListener("beforeunload", handleUnload);
    };
  }, []);

  return {
    probeState,
    startMonitoring,
    stopMonitoring,
    muteAlarm
  };
}
