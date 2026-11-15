import type { SimEvent } from "../types";

type AudioContextConstructor = new () => AudioContext;
type NoiseFilter = "lowpass" | "highpass" | "bandpass";

let sharedContext: AudioContext | null = null;

function getSharedAudioContext(): AudioContext | null {
  if (sharedContext === null) {
    const globalWindow = window as Window & { webkitAudioContext?: AudioContextConstructor };
    const Constructor = window.AudioContext ?? globalWindow.webkitAudioContext;
    if (Constructor === undefined) {
      return null;
    }
    sharedContext = new Constructor();
  }
  if (sharedContext.state === "suspended") {
    void sharedContext.resume();
  }
  return sharedContext;
}

export function unlockBattleAudio() {
  getSharedAudioContext();
}

interface AmbientVoice {
  oscillators: OscillatorNode[];
  lfo: OscillatorNode;
  gain: GainNode;
}

export class BattleAudio {
  private context: AudioContext | null = null;
  private master: GainNode | null = null;
  private compressor: DynamicsCompressorNode | null = null;
  private ambient: AmbientVoice | null = null;
  private enabled = true;
  private lastPlayed = new Map<string, number>();

  setEnabled(enabled: boolean) {
    this.enabled = enabled;
    if (this.master !== null && this.context !== null) {
      this.master.gain.setTargetAtTime(enabled ? 0.8 : 0, this.context.currentTime, 0.02);
    }
    if (enabled) {
      this.resume();
    } else {
      this.stopAmbient();
    }
  }

  resume() {
    const context = this.ensureContext();
    if (context !== null && context.state === "suspended") {
      void context.resume();
    }
  }

  state(): string {
    return this.context?.state ?? "unavailable";
  }

  startAmbient() {
    if (!this.enabled) {
      return;
    }
    const context = this.ensureContext();
    const master = this.master;
    if (context === null || master === null || context.state !== "running" || this.ambient !== null) {
      return;
    }
    const now = context.currentTime;
    const gain = context.createGain();
    const filter = context.createBiquadFilter();
    const low = context.createOscillator();
    const high = context.createOscillator();
    const lfo = context.createOscillator();
    const lfoGain = context.createGain();
    filter.type = "lowpass";
    filter.frequency.value = 320;
    filter.Q.value = 0.6;
    low.type = "sine";
    low.frequency.value = 110;
    high.type = "triangle";
    high.frequency.value = 164.81;
    high.detune.value = -5;
    lfo.type = "sine";
    lfo.frequency.value = 0.07;
    lfoGain.gain.value = 0.008;
    low.connect(filter);
    high.connect(filter);
    filter.connect(gain);
    gain.connect(master);
    lfo.connect(lfoGain);
    lfoGain.connect(gain.gain);
    gain.gain.setValueAtTime(0.0001, now);
    gain.gain.exponentialRampToValueAtTime(0.018, now + 2.5);
    low.start(now);
    high.start(now);
    lfo.start(now);
    this.ambient = { oscillators: [low, high], lfo, gain };
  }

  stopAmbient() {
    const ambient = this.ambient;
    const context = this.context;
    this.ambient = null;
    if (ambient === null || context === null) {
      return;
    }
    const now = context.currentTime;
    ambient.gain.gain.cancelScheduledValues(now);
    ambient.gain.gain.setTargetAtTime(0.0001, now, 0.4);
    ambient.oscillators.forEach((oscillator) => oscillator.stop(now + 1.2));
    ambient.lfo.stop(now + 1.2);
  }

  playEvents(events: SimEvent[]) {
    if (!this.enabled || events.length === 0) {
      return;
    }
    const context = this.ensureContext();
    if (context === null) {
      return;
    }
    if (context.state === "suspended") {
      void context.resume().then(() => this.playEventsNow(events));
      return;
    }
    this.playEventsNow(events);
  }

  playMatchEnd(winner: number) {
    if (!this.enabled) {
      return;
    }
    const context = this.ensureContext();
    if (context === null) {
      return;
    }
    if (context.state === "suspended") {
      void context.resume().then(() => this.playMatchEndNow(winner));
      return;
    }
    this.playMatchEndNow(winner);
  }

  playTest() {
    if (!this.canStart()) {
      return;
    }
    this.playTestNow();
  }

  playDeathTest() {
    if (!this.canStart()) {
      return;
    }
    this.playDeathTestNow();
  }

  playReady() {
    if (!this.canStart()) {
      return;
    }
    this.tone("sine", 392, 523, 0.18, 0.08, 0, -0.15);
    this.tone("sine", 523, 659, 0.22, 0.07, 0.12, 0.15);
  }

  dispose() {
    this.stopAmbient();
    this.master?.disconnect();
    this.compressor?.disconnect();
    this.master = null;
    this.compressor = null;
    this.context = null;
  }

  private canStart(): boolean {
    if (!this.enabled) {
      return false;
    }
    const context = this.ensureContext();
    if (context === null) {
      return false;
    }
    if (context.state === "suspended") {
      void context.resume();
      return false;
    }
    return true;
  }

  private playEventsNow(events: SimEvent[]) {
    if (!this.enabled) {
      return;
    }
    const hit = events.find((event) => event.type === "HIT");
    const attack = events.find((event) => event.type === "ATTACK");
    const miss = events.find((event) => event.type === "MISS");
    const death = events.find((event) => event.type === "DEATH");
    const objective = events.find((event) => event.type === "OBJECTIVE");
    const heal = events.find((event) => event.type === "HEAL");
    const spawn = events.find((event) => event.type === "SPAWN");

    if (spawn !== undefined && this.canPlay("spawn", 220)) {
      this.playSpawn(panFor(spawn));
    }
    if (death !== undefined && this.canPlay("death", 120)) {
      if (death.text === "HIVE_DESTROYED") {
        this.playHiveDeath(panFor(death));
      } else {
        this.playDeath(panFor(death));
      }
    }
    if (objective !== undefined && this.canPlay("objective", 160)) {
      this.playHiveDamage(panFor(objective));
    }
    if (heal !== undefined && this.canPlay("heal", 200)) {
      this.playHeal(panFor(heal));
    }
    if (hit !== undefined && this.canPlay("hit", 70)) {
      this.playImpact(hit.text, panFor(hit));
    } else if (attack !== undefined && this.canPlay("attack", 80)) {
      if (attack.text.startsWith("RANGED")) {
        this.playRangedAttack(panFor(attack));
      } else {
        this.playMeleeAttack(panFor(attack));
      }
    }
    if (miss !== undefined && this.canPlay("miss", 120)) {
      this.playMiss(panFor(miss));
    }
  }

  private playMatchEndNow(winner: number) {
    if (!this.enabled) {
      return;
    }
    if (winner === -1) {
      this.tone("triangle", 311, 311, 0.5, 0.12, 0, -0.2);
      this.tone("triangle", 233, 233, 0.6, 0.1, 0.18, 0.2);
      this.tone("sine", 155, 130, 0.9, 0.08, 0, 0);
    } else if (winner === 0) {
      this.tone("triangle", 523, 523, 0.2, 0.12, 0, -0.25);
      this.tone("triangle", 659, 659, 0.22, 0.12, 0.12, 0);
      this.tone("triangle", 784, 784, 0.5, 0.14, 0.24, 0.25);
      this.tone("sine", 1046, 1046, 0.6, 0.08, 0.24, 0.1);
      this.tone("sine", 131, 131, 0.8, 0.1, 0, 0);
    } else {
      this.tone("sawtooth", 220, 220, 0.28, 0.1, 0, -0.2);
      this.tone("sawtooth", 185, 185, 0.32, 0.1, 0.18, 0);
      this.tone("sawtooth", 147, 147, 0.55, 0.12, 0.36, 0.2);
      this.tone("sine", 73, 55, 0.9, 0.12, 0, 0);
    }
  }

  private playTestNow() {
    this.tone("sine", 523, 523, 0.16, 0.2, 0, -0.2);
    this.tone("sine", 659, 659, 0.16, 0.18, 0.12, 0);
    this.tone("sine", 784, 784, 0.28, 0.2, 0.24, 0.2);
  }

  private playDeathTestNow() {
    this.playTestNow();
    window.setTimeout(() => this.playDeath(0), 420);
  }

  private playSpawn(pan: number) {
    this.tone("sine", 220, 440, 0.18, 0.08, 0, pan);
    this.noise(0.1, 0.04, "bandpass", 900, pan);
  }

  private playMeleeAttack(pan: number) {
    const variation = 0.9 + Math.random() * 0.2;
    this.tone("triangle", 460 * variation, 180 * variation, 0.09, 0.1, 0, pan);
    this.noise(0.08, 0.07, "bandpass", 1800, pan);
  }

  private playRangedAttack(pan: number) {
    const variation = 0.92 + Math.random() * 0.16;
    this.tone("sawtooth", 1050 * variation, 320 * variation, 0.1, 0.1, 0, pan);
    this.noise(0.07, 0.05, "highpass", 2400, pan);
  }

  private playMiss(pan: number) {
    this.noise(0.14, 0.06, "bandpass", 1400, pan);
    this.tone("sine", 620, 220, 0.12, 0.04, 0, pan);
  }

  private playImpact(type: string, pan: number) {
    switch (type) {
      case "PIERCING":
        this.tone("square", 1500, 520, 0.06, 0.12, 0, pan);
        this.noise(0.06, 0.09, "highpass", 2200, pan);
        break;
      case "ACID":
        this.noise(0.22, 0.12, "bandpass", 900, pan);
        this.tone("sine", 320, 120, 0.28, 0.1, 0, pan);
        break;
      case "TOXIC":
        this.tone("sine", 230, 85, 0.3, 0.12, 0, pan);
        this.noise(0.18, 0.09, "lowpass", 420, pan);
        break;
      case "ELECTRIC":
        this.tone("square", 1900, 280, 0.12, 0.14, 0, pan);
        this.noise(0.1, 0.08, "highpass", 2600, pan);
        break;
      case "THERMAL":
        this.noise(0.2, 0.11, "bandpass", 1200, pan);
        this.tone("sawtooth", 420, 150, 0.2, 0.08, 0, pan);
        break;
      case "STARVATION":
        this.tone("sine", 110, 55, 0.16, 0.08, 0, pan);
        break;
      default:
        this.tone("sine", 160, 65, 0.12, 0.18, 0, pan);
        this.noise(0.1, 0.11, "lowpass", 700, pan);
        break;
    }
  }

  private playDeath(pan: number) {
    const variation = 0.9 + Math.random() * 0.2;
    this.tone("sawtooth", 720 * variation, 150 * variation, 0.38, 0.24, 0, pan);
    this.tone("square", 400 * variation, 85 * variation, 0.42, 0.12, 0.03, pan);
    this.tone("sine", 130 * variation, 45 * variation, 0.55, 0.18, 0.03, pan);
    this.noise(0.35, 0.15, "lowpass", 1600, pan);
  }

  private playHiveDeath(pan: number) {
    this.tone("sine", 95, 30, 0.9, 0.3, 0, pan);
    this.tone("sawtooth", 170, 40, 0.75, 0.18, 0.05, pan);
    this.noise(0.8, 0.24, "lowpass", 750, pan);
  }

  private playHiveDamage(pan: number) {
    this.tone("sawtooth", 210, 65, 0.4, 0.2, 0, pan);
    this.tone("sine", 105, 40, 0.48, 0.24, 0.02, pan);
    this.noise(0.3, 0.15, "lowpass", 700, pan);
  }

  private playHeal(pan: number) {
    this.tone("sine", 660, 880, 0.18, 0.12, 0, pan);
    this.tone("sine", 990, 990, 0.16, 0.08, 0.1, pan);
    this.tone("sine", 1320, 1320, 0.1, 0.04, 0.18, pan);
  }

  private canPlay(key: string, intervalMs: number): boolean {
    const now = performance.now();
    const previous = this.lastPlayed.get(key) ?? 0;
    if (now - previous < intervalMs) {
      return false;
    }
    this.lastPlayed.set(key, now);
    return true;
  }

  private ensureContext(): AudioContext | null {
    if (this.context !== null) {
      return this.context;
    }
    this.context = getSharedAudioContext();
    if (this.context === null) {
      return null;
    }
    this.master = this.context.createGain();
    this.master.gain.value = this.enabled ? 0.8 : 0;
    this.compressor = this.context.createDynamicsCompressor();
    this.compressor.threshold.value = -20;
    this.compressor.knee.value = 20;
    this.compressor.ratio.value = 6;
    this.compressor.attack.value = 0.003;
    this.compressor.release.value = 0.25;
    this.master.connect(this.compressor);
    this.compressor.connect(this.context.destination);
    return this.context;
  }

  private tone(
    type: OscillatorType,
    startFrequency: number,
    endFrequency: number,
    duration: number,
    volume: number,
    delaySeconds = 0,
    pan = 0
  ) {
    const context = this.ensureContext();
    const master = this.master;
    if (context === null || master === null) {
      return;
    }
    const now = context.currentTime + delaySeconds;
    const oscillator = context.createOscillator();
    const gain = context.createGain();
    oscillator.type = type;
    oscillator.frequency.setValueAtTime(Math.max(20, startFrequency), now);
    oscillator.frequency.exponentialRampToValueAtTime(Math.max(20, endFrequency), now + duration);
    gain.gain.setValueAtTime(0.0001, now);
    gain.gain.exponentialRampToValueAtTime(Math.max(0.0002, volume), now + 0.012);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + duration);
    oscillator.connect(gain);
    const panner = createPanner(context, pan);
    if (panner === null) {
      gain.connect(master);
    } else {
      gain.connect(panner);
      panner.connect(master);
    }
    oscillator.start(now);
    oscillator.stop(now + duration + 0.04);
  }

  private noise(duration: number, volume: number, filterType: NoiseFilter, frequency: number, pan = 0) {
    const context = this.ensureContext();
    const master = this.master;
    if (context === null || master === null) {
      return;
    }
    const frameCount = Math.max(1, Math.floor(context.sampleRate * duration));
    const buffer = context.createBuffer(1, frameCount, context.sampleRate);
    const data = buffer.getChannelData(0);
    for (let index = 0; index < frameCount; index += 1) {
      data[index] = Math.random() * 2 - 1;
    }
    const source = context.createBufferSource();
    const filter = context.createBiquadFilter();
    const gain = context.createGain();
    source.buffer = buffer;
    filter.type = filterType;
    filter.frequency.value = frequency;
    filter.Q.value = filterType === "bandpass" ? 1.4 : 0.7;
    const now = context.currentTime;
    gain.gain.setValueAtTime(Math.max(0.0002, volume), now);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + duration);
    source.connect(filter);
    filter.connect(gain);
    const panner = createPanner(context, pan);
    if (panner === null) {
      gain.connect(master);
    } else {
      gain.connect(panner);
      panner.connect(master);
    }
    source.start(now);
  }
}

function createPanner(context: AudioContext, pan: number): StereoPannerNode | null {
  if (typeof context.createStereoPanner !== "function") {
    return null;
  }
  const panner = context.createStereoPanner();
  panner.pan.value = Math.max(-0.8, Math.min(0.8, pan));
  return panner;
}

function panFor(event: SimEvent): number {
  if (event.x <= 0) {
    return 0;
  }
  return Math.max(-0.65, Math.min(0.65, (event.x / 128_000) * 1.4 - 0.7));
}
