import { useEffect, useState } from "react";
import {
  AlertCircle,
  Battery,
  BatteryCharging,
  CloudOff,
  Gauge,
  Laptop,
  RefreshCw,
  Trash2,
  Wifi
} from "lucide-react";
import {
  DEVICES_REFRESH_INTERVAL_MS,
  MIN_GROUP_CODE_LENGTH,
  type CloudSettings,
  type DevicesUiState,
  type RemoteDevice
} from "../types";
import { formatRelativeTime, type Translations } from "../i18n";
import { isSettingsConfigured } from "../services/settingsStorage";

interface DevicesViewProps {
  settings: CloudSettings;
  state: DevicesUiState;
  t: Translations;
  onRefresh: () => void;
  onDeleteDevice: (deviceId: string) => void;
  onOpenSettings: () => void;
}

export function DevicesView({
  settings,
  state,
  t,
  onRefresh,
  onDeleteDevice,
  onOpenSettings
}: DevicesViewProps) {
  const configured = isSettingsConfigured(settings);
  const [deviceToDelete, setDeviceToDelete] = useState<RemoteDevice | null>(null);
  const [, setTick] = useState(0);

  // Auto-refresh every 10s when configured
  useEffect(() => {
    if (!configured) return;
    onRefresh();
    const id = window.setInterval(onRefresh, DEVICES_REFRESH_INTERVAL_MS);
    return () => clearInterval(id);
  }, [configured, settings.serverUrl, settings.groupCode, onRefresh]);

  // Tick every 5s so relative timestamps stay fresh
  useEffect(() => {
    const id = window.setInterval(() => setTick((n) => n + 1), 5_000);
    return () => clearInterval(id);
  }, []);

  if (!configured) {
    return (
      <section className="panel devices-panel centered-empty" aria-label={t.tabDevices}>
        <div className="empty-icon-box">
          <CloudOff size={40} />
        </div>
        <h2 className="empty-title">{t.devicesUnconfiguredTitle}</h2>
        <p className="empty-desc">{t.devicesUnconfiguredDesc(MIN_GROUP_CODE_LENGTH)}</p>
        <button type="button" className="btn btn-primary" onClick={onOpenSettings}>
          {t.btnOpenSettings}
        </button>
      </section>
    );
  }

  const onlineCount = state.devices.filter((d) => d.isOnline).length;
  const offlineCount = state.devices.length - onlineCount;
  const nowMs = Date.now();

  return (
    <section className="panel devices-panel" aria-label={t.tabDevices}>
      <div className="devices-summary-bar">
        <div className="summary-counts">
          <span className="count-pill online">
            <span className="dot" />
            {t.devicesSummaryCounts(onlineCount, offlineCount)}
          </span>
        </div>
        <div className="summary-meta">
          <span>
            {state.lastUpdatedMs > 0
              ? t.devicesSummaryUpdated(formatRelativeTime(state.lastUpdatedMs, t, nowMs))
              : t.devicesSummaryAutoRefresh}
          </span>
          <button
            type="button"
            className="icon-btn-small"
            onClick={onRefresh}
            title={t.refreshDevices}
            aria-label={t.refreshDevices}
            disabled={state.isLoading}
          >
            <RefreshCw size={15} className={state.isLoading ? "spin" : ""} />
          </button>
        </div>
      </div>

      {state.errorMessage && (
        <div className="error-banner" role="alert">
          <AlertCircle size={18} />
          <span>{state.errorMessage}</span>
        </div>
      )}

      {state.devices.length === 0 ? (
        <div className="centered-empty compact">
          <div className="empty-icon-box">
            <Laptop size={36} />
          </div>
          <h3 className="empty-title">{t.devicesEmptyTitle}</h3>
          <p className="empty-desc">{t.devicesEmptyDesc}</p>
        </div>
      ) : (
        <ul className="devices-list">
          {state.devices.map((device) => {
            const isSelf = device.deviceId === settings.deviceId;
            return (
              <li
                key={device.deviceId}
                className={`device-card ${device.isOnline ? "is-online" : "is-offline"}`}
              >
                <div className="device-card-main">
                  <div className="device-header-row">
                    <span
                      className={`status-dot ${device.isOnline ? "online" : "offline"}`}
                      aria-hidden="true"
                    />
                    <span className="device-name">{device.name}</span>
                    {isSelf && (
                      <span className="badge-this-device">{t.deviceBadgeThisDevice}</span>
                    )}
                    <span
                      className={`device-status-pill ${device.isOnline ? "online" : "offline"}`}
                    >
                      {device.isOnline ? t.deviceStatusOnline : t.deviceStatusOffline}
                    </span>
                  </div>

                  <div className="device-meta-row">
                    {device.wifiSsid && (
                      <span className="meta-chip">
                        <Wifi size={13} />
                        {device.wifiSsid}
                      </span>
                    )}
                    {device.latencyMs !== null && (
                      <span className="meta-chip">
                        <Gauge size={13} />
                        {t.metricLatencyValue(device.latencyMs)}
                      </span>
                    )}
                    {device.batteryPct !== null && (
                      <span className="meta-chip">
                        {device.isCharging ? (
                          <BatteryCharging size={13} />
                        ) : (
                          <Battery size={13} />
                        )}
                        {device.batteryPct}%
                      </span>
                    )}
                    <span className="meta-last-seen">
                      {t.deviceLastSeen(formatRelativeTime(device.lastSeenMs, t, nowMs))}
                    </span>
                  </div>
                </div>

                <button
                  type="button"
                  className="icon-btn-danger"
                  onClick={() => setDeviceToDelete(device)}
                  title={t.removeDeviceLabel}
                  aria-label={t.removeDeviceLabel}
                >
                  <Trash2 size={17} />
                </button>
              </li>
            );
          })}
        </ul>
      )}

      {deviceToDelete && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="dialog-card">
            <h3>{t.dialogRemoveDeviceTitle}</h3>
            <p>{t.dialogRemoveDeviceMessage(deviceToDelete.name)}</p>
            <div className="dialog-actions">
              <button
                type="button"
                className="btn btn-text"
                onClick={() => setDeviceToDelete(null)}
              >
                {t.actionCancel}
              </button>
              <button
                type="button"
                className="btn btn-alarm"
                onClick={() => {
                  onDeleteDevice(deviceToDelete.deviceId);
                  setDeviceToDelete(null);
                }}
              >
                {t.actionRemove}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
