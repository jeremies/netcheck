import { useState } from "react";
import {
  AlertCircle,
  ArrowLeft,
  Check,
  CheckCircle2,
  Copy,
  Eye,
  EyeOff,
  KeyRound,
  Link2
} from "lucide-react";
import {
  MAX_GROUP_CODE_LENGTH,
  MIN_GROUP_CODE_LENGTH,
  type CloudSettings,
  type Locale
} from "../types";
import type { Translations } from "../i18n";
import { checkHealth } from "../services/cloudflareClient";
import { buildShareLink, generateGroupCode } from "../services/settingsStorage";

interface SettingsModalProps {
  initialSettings: CloudSettings;
  locale: Locale;
  t: Translations;
  onChangeLocale: (locale: Locale) => void;
  onSave: (updated: CloudSettings) => void;
  onClose: () => void;
}

export function SettingsModal({
  initialSettings,
  locale,
  t,
  onChangeLocale,
  onSave,
  onClose
}: SettingsModalProps) {
  const [groupCode, setGroupCode] = useState(initialSettings.groupCode);
  const [deviceName, setDeviceName] = useState(initialSettings.deviceName);
  const [broadcastEnabled, setBroadcastEnabled] = useState(initialSettings.broadcastEnabled);
  const [showGroupCode, setShowGroupCode] = useState(false);

  const [isTesting, setIsTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ ok: boolean; message: string } | null>(null);
  const [copied, setCopied] = useState(false);
  const [copiedLink, setCopiedLink] = useState(false);

  const trimmedCode = groupCode.trim();
  const isGroupCodeValid =
    trimmedCode.length === 0 || trimmedCode.length >= MIN_GROUP_CODE_LENGTH;
  const canShareCode = trimmedCode.length >= MIN_GROUP_CODE_LENGTH;

  const handleTestConnection = async () => {
    setIsTesting(true);
    setTestResult(null);
    try {
      const ms = await checkHealth(t);
      setTestResult({ ok: true, message: t.settingsTestConnected(ms) });
    } catch (err) {
      setTestResult({
        ok: false,
        message: err instanceof Error ? err.message : t.settingsTestFailed
      });
    } finally {
      setIsTesting(false);
    }
  };

  const handleCopyCode = async () => {
    if (!trimmedCode) return;
    try {
      await navigator.clipboard.writeText(trimmedCode);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      // Clipboard blocked
    }
  };

  const handleCopyShareLink = async () => {
    if (!canShareCode) return;
    const shareUrl = buildShareLink(trimmedCode);
    if (!shareUrl) return;
    try {
      await navigator.clipboard.writeText(shareUrl);
      setCopiedLink(true);
      window.setTimeout(() => setCopiedLink(false), 1800);
    } catch {
      // Clipboard blocked
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!isGroupCodeValid) return;
    onSave({
      ...initialSettings,
      groupCode,
      deviceName,
      broadcastEnabled
    });
  };

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-label={t.settingsTitle}>
      <div className="settings-sheet">
        <header className="settings-header">
          <button
            type="button"
            className="icon-btn"
            onClick={onClose}
            aria-label={t.backLabel}
            title={t.backLabel}
          >
            <ArrowLeft size={20} />
          </button>
          <h2>{t.settingsTitle}</h2>
          <div className="lang-toggle" role="group" aria-label={t.languageLabel}>
            <button
              type="button"
              className={`lang-btn ${locale === "en" ? "active" : ""}`}
              onClick={() => onChangeLocale("en")}
            >
              EN
            </button>
            <button
              type="button"
              className={`lang-btn ${locale === "es" ? "active" : ""}`}
              onClick={() => onChangeLocale("es")}
            >
              ES
            </button>
          </div>
        </header>

        <form className="settings-body" onSubmit={handleSubmit}>
          <div className="settings-sync-group">
            <div className="settings-toggle-card">
              <div>
                <div className="toggle-title">{t.settingsBroadcastTitle}</div>
                <div className="toggle-desc">{t.settingsBroadcastDesc}</div>
              </div>
              <label className="switch">
                <input
                  type="checkbox"
                  checked={broadcastEnabled}
                  onChange={(e) => setBroadcastEnabled(e.target.checked)}
                />
                <span className="slider" />
              </label>
            </div>

            <div className="inline-actions">
              <button
                type="button"
                className="btn btn-outlined btn-sm"
                onClick={handleTestConnection}
                disabled={isTesting}
              >
                {isTesting ? "…" : t.btnTestConnection}
              </button>
              {testResult && (
                <span className={`test-feedback ${testResult.ok ? "ok" : "fail"}`}>
                  {testResult.ok ? <CheckCircle2 size={15} /> : <AlertCircle size={15} />}
                  {testResult.message}
                </span>
              )}
            </div>
          </div>

          <div className="field-group">
            <label htmlFor="group-code">{t.settingsGroupCodeLabel}</label>
            <div className="input-with-icon">
              <input
                id="group-code"
                type={showGroupCode ? "text" : "password"}
                value={groupCode}
                onChange={(e) => setGroupCode(e.target.value)}
                maxLength={MAX_GROUP_CODE_LENGTH}
                placeholder={t.settingsGroupCodePlaceholder(MIN_GROUP_CODE_LENGTH)}
                className={!isGroupCodeValid ? "invalid" : ""}
              />
              <button
                type="button"
                className="input-icon-btn"
                onClick={() => setShowGroupCode((v) => !v)}
                aria-label={showGroupCode ? t.hideCode : t.showCode}
                title={showGroupCode ? t.hideCode : t.showCode}
              >
                {showGroupCode ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            {!isGroupCodeValid ? (
              <span className="field-error">
                {t.settingsGroupCodeError(MIN_GROUP_CODE_LENGTH)}
              </span>
            ) : (
              <span className="field-helper">{t.settingsGroupCodeHelper}</span>
            )}
            <div className="inline-actions">
              <button
                type="button"
                className="btn btn-outlined btn-sm"
                onClick={() => {
                  setGroupCode(generateGroupCode());
                  setShowGroupCode(true);
                }}
              >
                <KeyRound size={15} />
                <span>{t.btnGenerateCode}</span>
              </button>
              <button
                type="button"
                className="btn btn-outlined btn-sm"
                onClick={handleCopyCode}
                disabled={!trimmedCode}
              >
                {copied ? <Check size={15} /> : <Copy size={15} />}
                <span>{copied ? t.btnCopied : t.btnCopy}</span>
              </button>
              <button
                type="button"
                className="btn btn-outlined btn-sm"
                onClick={handleCopyShareLink}
                disabled={!canShareCode}
              >
                {copiedLink ? <Check size={15} /> : <Link2 size={15} />}
                <span>{copiedLink ? t.btnLinkCopied : t.btnCopyLink}</span>
              </button>
            </div>
          </div>

          <div className="field-group">
            <label htmlFor="device-name">{t.settingsDeviceNameLabel}</label>
            <input
              id="device-name"
              type="text"
              value={deviceName}
              onChange={(e) => setDeviceName(e.target.value)}
              maxLength={64}
            />
            <span className="field-helper monospace">
              {t.settingsDeviceId(initialSettings.deviceId)}
            </span>
          </div>

          <div className="settings-footer">
            <button
              type="submit"
              className="btn btn-primary btn-large"
              disabled={!isGroupCodeValid}
            >
              {t.btnSaveSettings}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
