CREATE TABLE IF NOT EXISTS devices (
  room_hash TEXT NOT NULL,
  device_id TEXT NOT NULL,
  name TEXT NOT NULL,
  wifi_ssid TEXT,
  latency_ms INTEGER,
  battery_pct INTEGER,
  is_charging INTEGER NOT NULL DEFAULT 0,
  explicitly_offline INTEGER NOT NULL DEFAULT 0,
  last_seen_ms INTEGER NOT NULL,
  created_at_ms INTEGER NOT NULL,
  PRIMARY KEY (room_hash, device_id)
);

CREATE INDEX IF NOT EXISTS idx_devices_room_last_seen
  ON devices (room_hash, last_seen_ms DESC);
