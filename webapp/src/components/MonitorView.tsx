import {
  AlertTriangle,
  Cloud,
  CloudOff,
  Play,
  Square,
  VolumeX,
  Wifi,
  WifiOff
} from "lucide-react";
import type { CloudSettings, MonitoringStatus, ProbeInfo } from "../types";
import { formatRelativeTime, formatTimeOfDay, type Translations } from "../i18n";
import { isSettingsConfigured } from "../services/settingsStorage";

interface MonitorViewProps {
  probeState: ProbeInfo;
  settings: CloudSettings;
  t: Translations;
  onStartMonitoring: () => void;
  onStopMonitoring: () => void;
  onMuteAlarm: () => void;
  onOpenSettings: () => void;
}

function getStatusVisuals(status: MonitoringStatus) {
  switch (status) {
    case "CONNECTED_ONLINE":
      return {
        colorClass: "status-online",
        pulse: true
      };
    case "ALARMING":
      return {
        colorClass: "status-alarm",
        pulse: true
      };
    case "MUTED":
      return {
        colorClass: "status-muted",
        pulse: false
      };
    case "CHECKING":
      return {
        colorClass: "status-checking",
        pulse: true
      };
    case "STOPPED":
    default:
      return {
        colorClass: "status-idle",
        pulse: false
      };
  }
}

export function MonitorView({
  probeState,
  settings,
  t,
  onStartMonitoring,
  onStopMonitoring,
  onMuteAlarm,
  onOpenSettings
}: MonitorViewProps) {
  const isMonitoring = probeState.status !== "STOPPED";
  const visuals = getStatusVisuals(probeState.status);
  const configured = isSettingsConfigured(settings);

  let statusTitle = t.statusStoppedTitle;
  let statusDesc = t.statusStoppedDesc;
  if (probeState.status === "CHECKING") {
    statusTitle = t.statusCheckingTitle;
    statusDesc = t.statusCheckingDesc;
  } else if (probeState.status === "CONNECTED_ONLINE") {
    statusTitle = t.statusOnlineTitle;
    statusDesc = t.statusOnlineDesc;
  } else if (probeState.status === "ALARMING") {
    statusTitle = t.statusAlarmTitle;
    statusDesc = probeState.isNetworkOnline
      ? t.statusAlarmDescNoInternet
      : t.statusAlarmDescDisconnected;
  } else if (probeState.status === "MUTED") {
    statusTitle = t.statusMutedTitle;
    statusDesc = t.statusMutedDesc;
  }

  let cloudText = t.cloudSyncNotConfigured;
  let cloudVariant = "chip-idle";
  let CloudIcon = CloudOff;

  if (!configured) {
    cloudText = t.cloudSyncNotConfigured;
    cloudVariant = "chip-idle";
    CloudIcon = CloudOff;
  } else if (!settings.broadcastEnabled) {
    cloudText = t.cloudBroadcastDisabled;
    cloudVariant = "chip-idle";
    CloudIcon = CloudOff;
  } else if (probeState.lastSyncError) {
    cloudText = t.cloudSyncError(probeState.lastSyncError);
    cloudVariant = "chip-alarm";
    CloudIcon = CloudOff;
  } else if (probeState.lastSyncTimestamp > 0 && isMonitoring) {
    cloudText = t.cloudBroadcastingSynced(
      settings.deviceName,
      formatRelativeTime(probeState.lastSyncTimestamp, t)
    );
    cloudVariant = "chip-online";
    CloudIcon = Cloud;
  } else if (isMonitoring) {
    cloudText = t.cloudWaitingHeartbeat;
    cloudVariant = "chip-checking";
    CloudIcon = Cloud;
  } else {
    cloudText = t.cloudWillBroadcast(settings.deviceName);
    cloudVariant = "chip-idle";
    CloudIcon = Cloud;
  }

  return (
    <section className="panel monitor-panel" aria-label={t.tabMonitor}>
      <p className="monitor-subtitle">{t.monitorSubtitle}</p>

      <div className="monitor-center">
        <div className={`beacon-wrapper ${visuals.colorClass}`}>
          {visuals.pulse && <span className="beacon-ring" aria-hidden="true" />}
          <div className="beacon-core">
            {probeState.status === "ALARMING" ? (
              <AlertTriangle size={48} strokeWidth={2.2} />
            ) : probeState.status === "MUTED" ? (
              <VolumeX size={48} strokeWidth={2.2} />
            ) : probeState.status === "STOPPED" ? (
              <WifiOff size={44} strokeWidth={2} />
            ) : (
              <Wifi size={48} strokeWidth={2.2} />
            )}
          </div>
        </div>

        <div className={`status-card ${visuals.colorClass}`}>
          <h2 className="status-title">{statusTitle}</h2>
          <p className="status-desc">{statusDesc}</p>

          <div className="metrics-grid">
            <div className="metric-item">
              <span className="metric-label">{t.metricNetwork}</span>
              <span className="metric-value">
                {probeState.isNetworkOnline
                  ? t.metricNetworkActive
                  : t.metricNetworkDisconnected}
              </span>
            </div>
            <div className="metric-item">
              <span className="metric-label">{t.metricLatency}</span>
              <span className="metric-value">
                {probeState.latencyMs !== null
                  ? t.metricLatencyValue(probeState.latencyMs)
                  : "—"}
              </span>
            </div>
            <div className="metric-item">
              <span className="metric-label">{t.metricFailures}</span>
              <span className="metric-value">{probeState.consecutiveFailures}</span>
            </div>
          </div>

          {probeState.lastCheckedTimestamp > 0 && (
            <div className="last-probe-text">
              {t.metricLastProbe(formatTimeOfDay(probeState.lastCheckedTimestamp))}
            </div>
          )}
        </div>

        <button
          type="button"
          className={`cloud-chip ${cloudVariant}`}
          onClick={onOpenSettings}
        >
          <CloudIcon size={16} />
          <span>{cloudText}</span>
        </button>
      </div>

      <div className="monitor-actions">
        {probeState.status === "ALARMING" && (
          <button
            type="button"
            className="btn btn-alarm btn-large"
            onClick={onMuteAlarm}
          >
            <VolumeX size={20} />
            <span>{t.btnMuteAlarm}</span>
          </button>
        )}

        <button
          type="button"
          className={`btn btn-large ${isMonitoring ? "btn-outlined" : "btn-primary"}`}
          onClick={isMonitoring ? onStopMonitoring : onStartMonitoring}
        >
          {isMonitoring ? <Square size={18} /> : <Play size={18} />}
          <span>{isMonitoring ? t.btnStopMonitoring : t.btnStartMonitoring}</span>
        </button>
      </div>
    </section>
  );
}
