import { useCallback, useState } from "react";
import { Laptop, Radio, RefreshCw, Settings, Wifi } from "lucide-react";
import type { CloudSettings, DevicesUiState, Locale } from "./types";
import { translations } from "./i18n";
import {
  isSettingsConfigured,
  loadLocale,
  loadSettings,
  saveLocale,
  saveSettings
} from "./services/settingsStorage";
import { deleteDevice, fetchDevices } from "./services/cloudflareClient";
import { useNetCheckMonitor } from "./hooks/useNetCheckMonitor";
import { MonitorView } from "./components/MonitorView";
import { DevicesView } from "./components/DevicesView";
import { SettingsModal } from "./components/SettingsModal";

type MobileTab = "monitor" | "devices";

export function App() {
  const [locale, setLocale] = useState<Locale>(() => loadLocale());
  const [settings, setSettings] = useState<CloudSettings>(() => loadSettings());
  const [activeTab, setActiveTab] = useState<MobileTab>("monitor");
  const [showSettings, setShowSettings] = useState(false);

  const [devicesState, setDevicesState] = useState<DevicesUiState>({
    isLoading: false,
    devices: [],
    serverTimeMs: 0,
    lastUpdatedMs: 0,
    errorMessage: null
  });

  const t = translations[locale];
  const { probeState, startMonitoring, stopMonitoring, muteAlarm } = useNetCheckMonitor(
    settings,
    t
  );

  const handleChangeLocale = useCallback((next: Locale) => {
    setLocale(next);
    saveLocale(next);
  }, []);

  const refreshDevices = useCallback(async () => {
    if (!isSettingsConfigured(settings)) return;
    setDevicesState((prev) => ({ ...prev, isLoading: true }));
    try {
      const data = await fetchDevices(settings, t);
      setDevicesState({
        isLoading: false,
        devices: data.devices,
        serverTimeMs: data.serverTimeMs,
        lastUpdatedMs: Date.now(),
        errorMessage: null
      });
    } catch (err) {
      setDevicesState((prev) => ({
        ...prev,
        isLoading: false,
        errorMessage: err instanceof Error ? err.message : t.errorLoadDevices
      }));
    }
  }, [settings, t]);

  const handleDeleteDevice = useCallback(
    async (deviceId: string) => {
      try {
        await deleteDevice(settings, deviceId, t);
        setDevicesState((prev) => ({
          ...prev,
          devices: prev.devices.filter((d) => d.deviceId !== deviceId),
          errorMessage: null
        }));
      } catch (err) {
        setDevicesState((prev) => ({
          ...prev,
          errorMessage: err instanceof Error ? err.message : t.errorRemoveDevice
        }));
      }
    },
    [settings, t]
  );

  const handleSaveSettings = useCallback((updated: CloudSettings) => {
    const saved = saveSettings(updated);
    setSettings(saved);
    setShowSettings(false);
  }, []);

  const onlineCount = devicesState.devices.filter((d) => d.isOnline).length;
  const configured = isSettingsConfigured(settings);

  return (
    <div className="app-shell">
      <header className="top-bar">
        <div className="top-bar-inner">
          <div className="brand">
            <div className="brand-icon">
              <Wifi size={20} strokeWidth={2.5} />
            </div>
            <h1>{t.appName}</h1>
          </div>

          <div className="top-bar-actions">
            {configured && (
              <button
                type="button"
                className="icon-btn"
                onClick={refreshDevices}
                title={t.refreshDevices}
                aria-label={t.refreshDevices}
              >
                <RefreshCw size={19} className={devicesState.isLoading ? "spin" : ""} />
              </button>
            )}
            <button
              type="button"
              className="icon-btn"
              onClick={() => setShowSettings(true)}
              title={t.settingsLabel}
              aria-label={t.settingsLabel}
            >
              <Settings size={20} />
            </button>
          </div>
        </div>
      </header>

      <nav className="mobile-tabs" aria-label="Navigation">
        <button
          type="button"
          className={`tab-btn ${activeTab === "monitor" ? "active" : ""}`}
          onClick={() => setActiveTab("monitor")}
        >
          <Radio size={17} />
          <span>{t.tabMonitor}</span>
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === "devices" ? "active" : ""}`}
          onClick={() => setActiveTab("devices")}
        >
          <Laptop size={17} />
          <span>{t.tabDevices}</span>
          {configured && onlineCount > 0 && (
            <span className="tab-badge">{onlineCount}</span>
          )}
        </button>
      </nav>

      <main className="main-grid">
        <div className={`grid-col ${activeTab === "monitor" ? "mobile-visible" : "mobile-hidden"}`}>
          <MonitorView
            probeState={probeState}
            settings={settings}
            t={t}
            onStartMonitoring={startMonitoring}
            onStopMonitoring={stopMonitoring}
            onMuteAlarm={muteAlarm}
            onOpenSettings={() => setShowSettings(true)}
          />
        </div>

        <div className={`grid-col ${activeTab === "devices" ? "mobile-visible" : "mobile-hidden"}`}>
          <DevicesView
            settings={settings}
            state={devicesState}
            t={t}
            onRefresh={refreshDevices}
            onDeleteDevice={handleDeleteDevice}
            onOpenSettings={() => setShowSettings(true)}
          />
        </div>
      </main>

      {showSettings && (
        <SettingsModal
          initialSettings={settings}
          locale={locale}
          t={t}
          onChangeLocale={handleChangeLocale}
          onSave={handleSaveSettings}
          onClose={() => setShowSettings(false)}
        />
      )}
    </div>
  );
}
