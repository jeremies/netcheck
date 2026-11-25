import type { Locale } from "./types";

export interface Translations {
  appName: string;
  titleOnlineDevices: string;
  refreshDevices: string;
  settingsLabel: string;
  tabMonitor: string;
  tabDevices: string;
  errorLoadDevices: string;
  errorRemoveDevice: string;
  monitorSubtitle: string;
  btnMuteAlarm: string;
  btnStartMonitoring: string;
  btnStopMonitoring: string;
  audioLockedBanner: string;
  cloudSyncNotConfigured: string;
  cloudBroadcastDisabled: string;
  cloudSyncError: (err: string) => string;
  cloudBroadcastingSynced: (name: string, time: string) => string;
  cloudWaitingHeartbeat: string;
  cloudWillBroadcast: (name: string) => string;
  statusStoppedTitle: string;
  statusStoppedDesc: string;
  statusCheckingTitle: string;
  statusCheckingDesc: string;
  statusOnlineTitle: string;
  statusOnlineDesc: string;
  statusAlarmTitle: string;
  statusAlarmDescDisconnected: string;
  statusAlarmDescNoInternet: string;
  statusMutedTitle: string;
  statusMutedDesc: string;
  metricNetwork: string;
  metricNetworkActive: string;
  metricNetworkDisconnected: string;
  metricLatency: string;
  metricLatencyValue: (ms: number) => string;
  metricFailures: string;
  metricLastProbe: (time: string) => string;
  devicesEmptyTitle: string;
  devicesEmptyDesc: string;
  dialogRemoveDeviceTitle: string;
  dialogRemoveDeviceMessage: (name: string) => string;
  actionRemove: string;
  actionCancel: string;
  devicesUnconfiguredTitle: string;
  devicesUnconfiguredDesc: (minLen: number) => string;
  btnOpenSettings: string;
  devicesSummaryCounts: (online: number, offline: number) => string;
  devicesSummaryUpdated: (time: string) => string;
  devicesSummaryAutoRefresh: string;
  deviceStatusOnline: string;
  deviceStatusOffline: string;
  deviceBadgeThisDevice: string;
  removeDeviceLabel: string;
  deviceLastSeen: (time: string) => string;
  relativeJustNow: string;
  relativeSecondsAgo: (s: number) => string;
  relativeMinutesAgo: (m: number) => string;
  relativeHoursAgo: (h: number) => string;
  relativeDaysAgo: (d: number) => string;
  settingsTitle: string;
  backLabel: string;
  settingsBroadcastTitle: string;
  settingsBroadcastDesc: string;
  btnTestConnection: string;
  settingsTestConnected: (ms: number) => string;
  settingsTestFailed: string;
  settingsGroupCodeLabel: string;
  settingsGroupCodePlaceholder: (minLen: number) => string;
  hideCode: string;
  showCode: string;
  settingsGroupCodeError: (minLen: number) => string;
  settingsGroupCodeHelper: string;
  btnGenerateCode: string;
  btnCopied: string;
  btnCopy: string;
  btnCopyLink: string;
  btnLinkCopied: string;
  settingsDeviceNameLabel: string;
  settingsDeviceId: (id: string) => string;
  btnSaveSettings: string;
  notifAlarmTitle: string;
  notifAlarmDisconnected: string;
  notifAlarmNoInternet: string;
  errorServerUrlNotConfigured: string;
  errorUnexpectedHealthResponse: string;
  errorBrowserOffline: string;
  errorProbeTimeout: string;
  errorUnknown: string;
  languageLabel: string;
}

const en: Translations = {
  appName: "NetCheck",
  titleOnlineDevices: "Online Devices",
  refreshDevices: "Refresh devices",
  settingsLabel: "Settings",
  tabMonitor: "Monitor",
  tabDevices: "Devices",
  errorLoadDevices: "Failed to load devices",
  errorRemoveDevice: "Failed to remove device",
  monitorSubtitle: "Desktop Internet Loss Alarm",
  btnMuteAlarm: "MUTE ALARM",
  btnStartMonitoring: "START MONITORING",
  btnStopMonitoring: "STOP MONITORING",
  audioLockedBanner: "Browser audio locked · Click anywhere to enable alarm sound",
  cloudSyncNotConfigured: "Cloud sync not configured · Click to set up",
  cloudBroadcastDisabled: "Cloud broadcast disabled",
  cloudSyncError: (err) => `Sync error: ${err}`,
  cloudBroadcastingSynced: (name, time) => `Broadcasting as ${name} · synced ${time}`,
  cloudWaitingHeartbeat: "Waiting for online heartbeat…",
  cloudWillBroadcast: (name) => `Will broadcast as ${name} when monitoring starts`,
  statusStoppedTitle: "Service Stopped",
  statusStoppedDesc: "Click Start Monitoring to begin checking internet connectivity.",
  statusCheckingTitle: "Verifying Internet…",
  statusCheckingDesc: "Testing connection to Google connectivity check.",
  statusOnlineTitle: "Internet Online",
  statusOnlineDesc: "Active internet connection verified.",
  statusAlarmTitle: "ALARM: NO INTERNET!",
  statusAlarmDescDisconnected: "Browser reports network is disconnected!",
  statusAlarmDescNoInternet: "Network interface up, but cannot reach internet!",
  statusMutedTitle: "Alarm Muted",
  statusMutedDesc: "Alarm silenced for this outage. Will re-arm once connection recovers.",
  metricNetwork: "Network",
  metricNetworkActive: "Active",
  metricNetworkDisconnected: "Disconnected",
  metricLatency: "Latency",
  metricLatencyValue: (ms) => `${ms}ms`,
  metricFailures: "Failures",
  metricLastProbe: (time) => `Last probe: ${time}`,
  devicesEmptyTitle: "No devices in this group yet",
  devicesEmptyDesc:
    "Start monitoring on this browser or another device using the same Group Code to see live device status here.",
  dialogRemoveDeviceTitle: "Remove device?",
  dialogRemoveDeviceMessage: (name) =>
    `Remove "${name}" from this group list? If it is still monitoring, it will reappear on its next heartbeat.`,
  actionRemove: "Remove",
  actionCancel: "Cancel",
  devicesUnconfiguredTitle: "Set Up Cloud Sync",
  devicesUnconfiguredDesc: (minLen) =>
    `Enter a shared Group Code (at least ${minLen} characters) in Settings to broadcast and view online devices.`,
  btnOpenSettings: "Open Settings",
  devicesSummaryCounts: (online, offline) => `${online} online · ${offline} offline`,
  devicesSummaryUpdated: (time) => `Updated ${time} · auto-refreshes every 10s`,
  devicesSummaryAutoRefresh: "Auto-refreshes every 10s",
  deviceStatusOnline: "Online",
  deviceStatusOffline: "Offline",
  deviceBadgeThisDevice: "This device",
  removeDeviceLabel: "Remove device",
  deviceLastSeen: (time) => `Last seen: ${time}`,
  relativeJustNow: "Just now",
  relativeSecondsAgo: (s) => `${s}s ago`,
  relativeMinutesAgo: (m) => `${m}m ago`,
  relativeHoursAgo: (h) => `${h}h ago`,
  relativeDaysAgo: (d) => `${d}d ago`,
  settingsTitle: "Cloud Sync Settings",
  backLabel: "Back",
  settingsBroadcastTitle: "Broadcast Online Status",
  settingsBroadcastDesc: "Sends heartbeats every 15s while monitoring is active.",
  btnTestConnection: "Test Connection",
  settingsTestConnected: (ms) => `Connected (${ms}ms)`,
  settingsTestFailed: "Connection failed",
  settingsGroupCodeLabel: "Shared Group Code",
  settingsGroupCodePlaceholder: (minLen) => `At least ${minLen} characters`,
  hideCode: "Hide code",
  showCode: "Show code",
  settingsGroupCodeError: (minLen) => `Group code must be at least ${minLen} characters`,
  settingsGroupCodeHelper: "Use the same secret code on every device you want to see together.",
  btnGenerateCode: "Generate Code",
  btnCopied: "Copied",
  btnCopy: "Copy",
  btnCopyLink: "Copy Share Link",
  btnLinkCopied: "Link Copied",
  settingsDeviceNameLabel: "Device Name",
  settingsDeviceId: (id) => `Device ID: ${id}`,
  btnSaveSettings: "Save Settings",
  notifAlarmTitle: "⚠️ NO INTERNET!",
  notifAlarmDisconnected: "Network is disconnected!",
  notifAlarmNoInternet: "Cannot reach internet!",
  errorServerUrlNotConfigured: "Sync service is not configured",
  errorUnexpectedHealthResponse: "Unexpected server health response",
  errorBrowserOffline: "Network is disconnected or offline",
  errorProbeTimeout: "Connection error / timeout",
  errorUnknown: "Unknown error",
  languageLabel: "Language"
};

const es: Translations = {
  appName: "NetCheck",
  titleOnlineDevices: "Dispositivos en línea",
  refreshDevices: "Actualizar dispositivos",
  settingsLabel: "Ajustes",
  tabMonitor: "Monitor",
  tabDevices: "Dispositivos",
  errorLoadDevices: "Error al cargar los dispositivos",
  errorRemoveDevice: "Error al eliminar el dispositivo",
  monitorSubtitle: "Alarma de corte de internet (Escritorio)",
  btnMuteAlarm: "SILENCIAR ALARMA",
  btnStartMonitoring: "INICIAR MONITORIZACIÓN",
  btnStopMonitoring: "DETENER MONITORIZACIÓN",
  audioLockedBanner: "Audio bloqueado por el navegador · Pulsa en cualquier lugar para activar el sonido de alarma",
  cloudSyncNotConfigured: "Sincronización en la nube no configurada · Pulsa para configurar",
  cloudBroadcastDisabled: "Envío a la nube desactivado",
  cloudSyncError: (err) => `Error de sincronización: ${err}`,
  cloudBroadcastingSynced: (name, time) => `Enviando como ${name} · sincronizado ${time}`,
  cloudWaitingHeartbeat: "Esperando señal de estado…",
  cloudWillBroadcast: (name) => `Se enviará como ${name} al iniciar la monitorización`,
  statusStoppedTitle: "Servicio detenido",
  statusStoppedDesc: "Pulsa Iniciar monitorización para empezar a comprobar la conexión a internet.",
  statusCheckingTitle: "Comprobando internet…",
  statusCheckingDesc: "Probando la conexión mediante la comprobación de conectividad de Google.",
  statusOnlineTitle: "Internet en línea",
  statusOnlineDesc: "Conexión activa a internet verificada.",
  statusAlarmTitle: "ALARMA: ¡SIN INTERNET!",
  statusAlarmDescDisconnected: "¡El navegador indica que la red está desconectada!",
  statusAlarmDescNoInternet: "Red activa, ¡pero sin acceso a internet!",
  statusMutedTitle: "Alarma silenciada",
  statusMutedDesc: "Alarma silenciada para este corte. Se reactivará en cuanto vuelva la conexión.",
  metricNetwork: "Red",
  metricNetworkActive: "Activa",
  metricNetworkDisconnected: "Desconectada",
  metricLatency: "Latencia",
  metricLatencyValue: (ms) => `${ms} ms`,
  metricFailures: "Fallos",
  metricLastProbe: (time) => `Última comprobación: ${time}`,
  devicesEmptyTitle: "Aún no hay dispositivos en este grupo",
  devicesEmptyDesc:
    "Inicia la monitorización en este navegador u otro dispositivo con el mismo código de grupo para ver aquí el estado en tiempo real.",
  dialogRemoveDeviceTitle: "¿Eliminar dispositivo?",
  dialogRemoveDeviceMessage: (name) =>
    `¿Eliminar "${name}" de la lista de este grupo? Si sigue monitorizando, volverá a aparecer con su próxima señal de estado.`,
  actionRemove: "Eliminar",
  actionCancel: "Cancelar",
  devicesUnconfiguredTitle: "Configurar sincronización",
  devicesUnconfiguredDesc: (minLen) =>
    `Introduce un código de grupo compartido (mínimo ${minLen} caracteres) en Ajustes para enviar y ver los dispositivos en línea.`,
  btnOpenSettings: "Abrir ajustes",
  devicesSummaryCounts: (online, offline) => `${online} en línea · ${offline} sin conexión`,
  devicesSummaryUpdated: (time) => `Actualizado ${time} · actualización automática cada 10 s`,
  devicesSummaryAutoRefresh: "Actualización automática cada 10 s",
  deviceStatusOnline: "En línea",
  deviceStatusOffline: "Sin conexión",
  deviceBadgeThisDevice: "Este dispositivo",
  removeDeviceLabel: "Eliminar dispositivo",
  deviceLastSeen: (time) => `Visto por última vez: ${time}`,
  relativeJustNow: "Ahora mismo",
  relativeSecondsAgo: (s) => `Hace ${s} s`,
  relativeMinutesAgo: (m) => `Hace ${m} min`,
  relativeHoursAgo: (h) => `Hace ${h} h`,
  relativeDaysAgo: (d) => `Hace ${d} d`,
  settingsTitle: "Ajustes de sincronización",
  backLabel: "Volver",
  settingsBroadcastTitle: "Compartir estado en línea",
  settingsBroadcastDesc: "Envía señales de estado cada 15 s mientras la monitorización está activa.",
  btnTestConnection: "Probar conexión",
  settingsTestConnected: (ms) => `Conectado (${ms} ms)`,
  settingsTestFailed: "Error de conexión",
  settingsGroupCodeLabel: "Código de grupo compartido",
  settingsGroupCodePlaceholder: (minLen) => `Al menos ${minLen} caracteres`,
  hideCode: "Ocultar código",
  showCode: "Mostrar código",
  settingsGroupCodeError: (minLen) => `El código de grupo debe tener al menos ${minLen} caracteres`,
  settingsGroupCodeHelper: "Usa el mismo código secreto en cada dispositivo que quieras ver en el mismo grupo.",
  btnGenerateCode: "Generar código",
  btnCopied: "Copiado",
  btnCopy: "Copiar",
  btnCopyLink: "Copiar enlace",
  btnLinkCopied: "Enlace copiado",
  settingsDeviceNameLabel: "Nombre del dispositivo",
  settingsDeviceId: (id) => `ID del dispositivo: ${id}`,
  btnSaveSettings: "Guardar ajustes",
  notifAlarmTitle: "⚠️ ¡SIN INTERNET!",
  notifAlarmDisconnected: "¡La red está desconectada!",
  notifAlarmNoInternet: "¡Sin conexión a internet!",
  errorServerUrlNotConfigured: "El servicio de sincronización no está configurado",
  errorUnexpectedHealthResponse: "Respuesta inesperada del servidor",
  errorBrowserOffline: "La red está desconectada o sin conexión",
  errorProbeTimeout: "Error de conexión / tiempo agotado",
  errorUnknown: "Error desconocido",
  languageLabel: "Idioma"
};

export const translations: Record<Locale, Translations> = { en, es };

export function detectDefaultLocale(): Locale {
  if (typeof navigator !== "undefined" && navigator.language.toLowerCase().startsWith("es")) {
    return "es";
  }
  return "en";
}

export function formatRelativeTime(timestampMs: number, t: Translations, nowMs = Date.now()): string {
  const diffSec = Math.max(0, Math.floor((nowMs - timestampMs) / 1000));
  if (diffSec < 5) return t.relativeJustNow;
  if (diffSec < 60) return t.relativeSecondsAgo(diffSec);
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return t.relativeMinutesAgo(diffMin);
  const diffHours = Math.floor(diffMin / 60);
  if (diffHours < 24) return t.relativeHoursAgo(diffHours);
  const diffDays = Math.floor(diffHours / 24);
  return t.relativeDaysAgo(diffDays);
}

export function formatTimeOfDay(timestampMs: number): string {
  const d = new Date(timestampMs);
  return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}
