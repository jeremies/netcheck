export class AlarmController {
  private audioCtx: AudioContext | null = null;
  private pulseTimer: number | null = null;
  private titleTimer: number | null = null;
  private isPlaying = false;
  private activeNotification: Notification | null = null;
  private readonly originalTitle = typeof document !== "undefined" ? document.title : "NetCheck";

  private getAudioContextConstructor(): typeof AudioContext | undefined {
    if (typeof window === "undefined") return undefined;
    return (
      window.AudioContext ||
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
    );
  }

  public canUseAudio(): boolean {
    return Boolean(this.getAudioContextConstructor());
  }

  public isAudioUnlocked(): boolean {
    if (!this.canUseAudio()) return true;
    return this.audioCtx !== null && this.audioCtx.state === "running";
  }

  public async unlockAudio(): Promise<boolean> {
    const AudioCtx = this.getAudioContextConstructor();
    if (!AudioCtx) return true;

    try {
      if (!this.audioCtx) {
        this.audioCtx = new AudioCtx();
      }
      if (this.audioCtx.state === "suspended") {
        await this.audioCtx.resume();
      }
      return this.audioCtx.state === "running";
    } catch {
      return false;
    }
  }

  public async requestNotificationPermission(): Promise<void> {
    if (typeof Notification === "undefined") return;
    if (Notification.permission === "default") {
      try {
        await Notification.requestPermission();
      } catch {
        // Ignore permission denial
      }
    }
  }

  public startAlarm(title: string, body: string): void {
    if (this.isPlaying) return;
    this.isPlaying = true;
    void this.unlockAudio();

    // Play immediate tone then repeat every 900ms
    this.playDualBeep();
    this.pulseTimer = window.setInterval(() => {
      if (this.isPlaying) {
        this.playDualBeep();
      }
    }, 900);

    // Flash browser tab title
    let toggle = false;
    document.title = `🚨 ${title}`;
    this.titleTimer = window.setInterval(() => {
      toggle = !toggle;
      document.title = toggle ? `⚠️ ${this.originalTitle}` : `🚨 ${title}`;
    }, 800);

    // Desktop notification if granted
    if (typeof Notification !== "undefined" && Notification.permission === "granted") {
      try {
        this.activeNotification?.close();
        this.activeNotification = new Notification(title, {
          body,
          tag: "netcheck-outage-alarm",
          requireInteraction: true
        });
      } catch {
        // Ignore notification errors
      }
    }
  }

  public stopAlarm(): void {
    this.isPlaying = false;

    if (this.pulseTimer !== null) {
      clearInterval(this.pulseTimer);
      this.pulseTimer = null;
    }
    if (this.titleTimer !== null) {
      clearInterval(this.titleTimer);
      this.titleTimer = null;
    }
    if (typeof document !== "undefined") {
      document.title = this.originalTitle;
    }
    if (this.activeNotification) {
      this.activeNotification.close();
      this.activeNotification = null;
    }
  }

  private playDualBeep(): void {
    if (!this.audioCtx || this.audioCtx.state !== "running") return;

    const now = this.audioCtx.currentTime;
    this.scheduleTone(880, now, 0.22);
    this.scheduleTone(660, now + 0.25, 0.22);
  }

  private scheduleTone(freq: number, startTime: number, duration: number): void {
    if (!this.audioCtx) return;
    const osc = this.audioCtx.createOscillator();
    const gain = this.audioCtx.createGain();

    osc.type = "square";
    osc.frequency.setValueAtTime(freq, startTime);

    gain.gain.setValueAtTime(0.001, startTime);
    gain.gain.exponentialRampToValueAtTime(0.18, startTime + 0.02);
    gain.gain.setValueAtTime(0.18, startTime + duration - 0.03);
    gain.gain.exponentialRampToValueAtTime(0.001, startTime + duration);

    osc.connect(gain);
    gain.connect(this.audioCtx.destination);

    osc.start(startTime);
    osc.stop(startTime + duration);
  }
}
