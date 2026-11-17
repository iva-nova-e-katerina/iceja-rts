import type { SimEvent } from "../types";

type AudioContextConstructor = new () => AudioContext;
type NoiseFilter = "lowpass" | "highpass" | "bandpass";

let sharedContext: AudioContext | null = null;
let battleMapWidth = 128_000;

export function setBattleMapWidth(width: number) {
  battleMapWidth = width > 0 ? width : 128_000;
}

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

export class BattleAudio {
  private context: AudioContext | null = null;
  private master: GainNode | null = null;
  private compressor: DynamicsCompressorNode | null = null;
  private reverb: ConvolverNode | null = null;
  private reverbReturn: GainNode | null = null;
  private enabled = true;
  private volume = 0.8;
  private lastPlayed = new Map<string, number>();

  setEnabled(enabled: boolean) {
    this.enabled = enabled;
    if (this.master !== null && this.context !== null) {
      this.master.gain.setTargetAtTime(
        enabled ? 0.8 * this.volume : 0,
        this.context.currentTime,
        0.02
      );
    }
    if (enabled) {
      this.resume();
    }
  }

  setVolume(volume: number) {
    this.volume = Math.max(0, Math.min(1, volume));
    if (this.master !== null && this.context !== null) {
      this.master.gain.setTargetAtTime(
        this.enabled ? 0.8 * this.volume : 0,
        this.context.currentTime,
        0.03
      );
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
    this.master?.disconnect();
    this.compressor?.disconnect();
    this.reverb?.disconnect();
    this.reverbReturn?.disconnect();
    this.master = null;
    this.compressor = null;
    this.reverb = null;
    this.reverbReturn = null;
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
    const hits = events
      .filter((event) => event.type === "HIT")
      .sort((first, second) => second.value - first.value);
    const attacks = events
      .filter((event) => event.type === "ATTACK")
      .sort((first, second) => second.value - first.value);
    const miss = events.find((event) => event.type === "MISS");
    const death = events.find((event) => event.type === "DEATH");
    const objective = events.find((event) => event.type === "OBJECTIVE");
    const heal = events.find((event) => event.type === "HEAL");
    const spawn = events.find((event) => event.type === "SPAWN");
    const resource = events.find((event) => event.type === "RESOURCE");
    const eat = events.find((event) => event.type === "EAT");
    const telepathy = events.find((event) => event.type === "TELEPATHY");
    const whip = events.find((event) => event.type === "WHIP");
    const hiveFound = events.find((event) => event.type === "HIVE_FOUND");
    const hit = hits[0];
    const attack = attacks[0];

    if (spawn !== undefined && this.canPlay("spawn", 220)) {
      this.playSpawn(panFor(spawn));
    }
    if (eat !== undefined && this.canPlay("eat-start", 350)) {
      this.playFeast(panFor(eat));
    }
    if (resource !== undefined && this.canPlay("resource", 180)) {
      this.playEat(panFor(resource));
    }
    if (telepathy !== undefined && this.canPlay("telepathy", 420)) {
      this.playTelepathy(
        panFor(telepathy),
        telepathy.text === "HELP" || telepathy.text === "HIVE_CALL"
      );
    }
    if (whip !== undefined && this.canPlay("whip", 80)) {
      this.playImpact("ELECTRIC", panFor(whip), 1.25);
      this.tone("sawtooth", 720, 120, 0.18, 0.08, 0, panFor(whip), 0.06);
    }
    if (hiveFound !== undefined && this.canPlay("hive-found", 420)) {
      this.playTelepathy(panFor(hiveFound), false);
      this.tone("triangle", 523, 784, 0.5, 0.05, 0.18, panFor(hiveFound), 0.2);
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
    if (attack !== undefined && this.canPlay("attack", 80)) {
      const ranged = attack.text.startsWith("RANGED");
      if (ranged || hit === undefined) {
        const weaponId = attack.text.split(":")[1] ?? "";
        if (ranged) {
          this.playRangedAttack(weaponId, panFor(attack));
        } else {
          this.playMeleeAttack(weaponId, panFor(attack));
        }
      }
    }
    if (hit !== undefined && this.canPlay("hit", 60)) {
      this.playImpact(hit.text, panFor(hit), 1);
      if (hits[1] !== undefined && this.canPlay("hit-secondary", 95)) {
        this.playImpact(hits[1].text, panFor(hits[1]), 0.45);
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
    window.setTimeout(() => this.playMeleeAttack("CLAWS", -0.2), 300);
    window.setTimeout(() => this.playRangedAttack("ACIDSPIT", 0.2), 520);
    window.setTimeout(() => this.playImpact("ACID", 0.1), 680);
    window.setTimeout(() => this.playDeath(0), 860);
  }

  private playSpawn(pan: number) {
    this.tone("sine", 180, 420, 0.22, 0.07, 0, pan, 0.08);
    this.tone("triangle", 340, 680, 0.16, 0.035, 0.03, pan, 0.06);
    this.noise(0.12, 0.035, "bandpass", 1100, pan, 0.06);
  }

  private playMeleeAttack(weaponId: string, pan: number) {
    const variation = 0.9 + Math.random() * 0.2;
    let start = 460;
    let end = 180;
    let volume = 0.1;
    let noiseFrequency = 1800;
    let noiseVolume = 0.07;
    let noiseType: NoiseFilter = "bandpass";
    switch (weaponId) {
      case "BONEHAMMER":
        start = 210;
        end = 65;
        volume = 0.16;
        noiseFrequency = 520;
        noiseVolume = 0.1;
        noiseType = "lowpass";
        break;
      case "CLAWS":
        start = 760;
        end = 240;
        noiseFrequency = 2600;
        noiseVolume = 0.075;
        break;
      case "ELECTRO":
        start = 1350;
        end = 260;
        volume = 0.12;
        noiseFrequency = 3400;
        noiseType = "highpass";
        break;
      case "SPINE":
        start = 980;
        end = 320;
        noiseFrequency = 3000;
        break;
      case "VENOMFANG":
        start = 380;
        end = 120;
        noiseFrequency = 900;
        noiseVolume = 0.09;
        break;
    }
    this.tone("triangle", start * variation, end * variation, 0.1, volume, 0, pan, 0.05);
    this.tone("sine", (start / 2) * variation, end * 0.7, 0.14, volume * 0.55, 0.012, pan, 0.04);
    this.noise(0.09, noiseVolume, noiseType, noiseFrequency, pan, 0.05);
  }

  private playRangedAttack(weaponId: string, pan: number) {
    const variation = 0.92 + Math.random() * 0.16;
    switch (weaponId) {
      case "ACIDSPIT":
        this.tone("sine", 520 * variation, 150, 0.22, 0.085, 0, pan, 0.12);
        this.noise(0.2, 0.08, "bandpass", 720, pan, 0.1);
        this.noise(0.08, 0.035, "highpass", 2500, pan, 0.03);
        break;
      case "SPOREMORTAR":
        this.tone("sine", 150, 48, 0.24, 0.15, 0, pan, 0.16);
        this.noise(0.22, 0.1, "lowpass", 620, pan, 0.12);
        break;
      case "HARPOON":
        this.tone("triangle", 1150 * variation, 240, 0.12, 0.09, 0, pan, 0.04);
        this.noise(0.08, 0.055, "highpass", 2100, pan, 0.03);
        break;
      case "ELECTROARC":
        this.tone("square", 2100 * variation, 260, 0.16, 0.12, 0, pan, 0.05);
        this.tone("sawtooth", 1200 * variation, 180, 0.2, 0.06, 0.03, pan, 0.04);
        this.noise(0.16, 0.08, "highpass", 3400, pan, 0.04);
        break;
      default:
        this.tone("sawtooth", 1050 * variation, 320 * variation, 0.11, 0.085, 0, pan, 0.05);
        this.noise(0.08, 0.04, "highpass", 2400, pan, 0.03);
        break;
    }
  }

  private playMiss(pan: number) {
    this.noise(0.16, 0.055, "bandpass", 1500, pan, 0.04);
    this.tone("sine", 640, 190, 0.14, 0.035, 0, pan, 0.03);
  }

  private playImpact(type: string, pan: number, gainScale = 1) {
    switch (type) {
      case "PIERCING":
        this.tone("square", 1650, 520, 0.06, 0.11 * gainScale, 0, pan, 0.03);
        this.noise(0.07, 0.085 * gainScale, "highpass", 2300, pan, 0.02);
        break;
      case "ACID":
        this.noise(0.24, 0.11 * gainScale, "bandpass", 780, pan, 0.12);
        this.tone("sine", 300, 95, 0.3, 0.09 * gainScale, 0, pan, 0.1);
        this.noise(0.1, 0.035 * gainScale, "highpass", 1800, pan, 0.05);
        break;
      case "TOXIC":
        this.tone("sine", 230, 74, 0.34, 0.11 * gainScale, 0, pan, 0.1);
        this.noise(0.19, 0.085 * gainScale, "lowpass", 390, pan, 0.08);
        break;
      case "ELECTRIC":
        this.tone("square", 2100, 240, 0.13, 0.12 * gainScale, 0, pan, 0.04);
        this.noise(0.11, 0.08 * gainScale, "highpass", 2900, pan, 0.03);
        this.tone("sine", 95, 48, 0.15, 0.04 * gainScale, 0, pan, 0.03);
        break;
      case "THERMAL":
        this.noise(0.22, 0.1 * gainScale, "bandpass", 1150, pan, 0.08);
        this.tone("sawtooth", 440, 140, 0.22, 0.07 * gainScale, 0, pan, 0.06);
        break;
      case "STARVATION":
        this.tone("sine", 110, 52, 0.18, 0.07 * gainScale, 0, pan, 0.02);
        break;
      default:
        this.tone("sine", 170, 58, 0.13, 0.16 * gainScale, 0, pan, 0.04);
        this.tone("triangle", 320, 90, 0.08, 0.06 * gainScale, 0, pan, 0.03);
        this.noise(0.11, 0.1 * gainScale, "lowpass", 720, pan, 0.03);
        break;
    }
  }

  private playDeath(pan: number) {
    const variation = 0.9 + Math.random() * 0.2;
    this.tone("sawtooth", 720 * variation, 150 * variation, 0.4, 0.23, 0, pan, 0.16);
    this.tone("square", 400 * variation, 85 * variation, 0.44, 0.11, 0.03, pan, 0.12);
    this.tone("sine", 130 * variation, 45 * variation, 0.6, 0.17, 0.03, pan, 0.12);
    this.noise(0.4, 0.14, "lowpass", 1600, pan, 0.14);
    this.noise(0.12, 0.04, "highpass", 2800, pan, 0.03);
  }

  private playHiveDeath(pan: number) {
    this.tone("sine", 95, 30, 0.95, 0.3, 0, pan, 0.3);
    this.tone("sawtooth", 170, 40, 0.8, 0.17, 0.05, pan, 0.22);
    this.noise(0.85, 0.23, "lowpass", 750, pan, 0.26);
    this.noise(0.3, 0.08, "bandpass", 1800, pan, 0.12);
  }

  private playHiveDamage(pan: number) {
    this.tone("sawtooth", 210, 65, 0.42, 0.19, 0, pan, 0.16);
    this.tone("sine", 105, 40, 0.5, 0.22, 0.02, pan, 0.18);
    this.noise(0.32, 0.14, "lowpass", 700, pan, 0.14);
    this.noise(0.1, 0.045, "highpass", 2200, pan, 0.04);
  }

  private playTelepathy(pan: number, help: boolean) {
    if (help) {
      this.tone("sine", 220, 440, 0.3, 0.13, 0, pan, 0.3);
      this.tone("triangle", 330, 660, 0.42, 0.1, 0.08, pan, 0.26);
      this.tone("sine", 880, 1320, 0.24, 0.065, 0.2, pan, 0.2);
      this.noise(0.24, 0.045, "bandpass", 2200, pan, 0.2);
      return;
    }
    this.tone("sine", 380, 560, 0.65, 0.08, 0, pan, 0.28);
    this.tone("sine", 570, 840, 0.6, 0.06, 0.1, pan, 0.24);
    this.tone("triangle", 760, 1140, 0.5, 0.035, 0.2, pan, 0.2);
    this.noise(0.3, 0.035, "bandpass", 2600, pan, 0.15);
  }

  private playFeast(pan: number) {
    for (let bite = 0; bite < 6; bite += 1) {
      const delay = bite * 0.2;
      const variation = 0.88 + Math.random() * 0.24;
      const bitePan = Math.max(-0.8, Math.min(0.8, pan + (Math.random() * 0.16 - 0.08)));
      this.noise(0.24, 0.15, "bandpass", 560 * variation, bitePan, 0.09, delay);
      this.noise(0.08, 0.085, "highpass", 2800 * variation, bitePan, 0.04, delay + 0.01);
      this.tone("sine", 185 * variation, 62, 0.28, 0.13, delay, bitePan, 0.06);
      this.tone("triangle", 310 * variation, 105, 0.16, 0.055, delay + 0.04, bitePan, 0.04);
    }
  }

  private playEat(pan: number) {
    this.noise(0.16, 0.11, "bandpass", 780, pan, 0.06);
    this.noise(0.1, 0.06, "lowpass", 320, pan, 0.04);
    this.tone("sine", 190, 75, 0.26, 0.09, 0, pan, 0.05);
    this.tone("sine", 140, 60, 0.22, 0.07, 0.1, pan, 0.04);
  }

  private playHeal(pan: number) {
    this.tone("sine", 660, 880, 0.2, 0.11, 0, pan, 0.12);
    this.tone("sine", 990, 990, 0.18, 0.07, 0.1, pan, 0.1);
    this.tone("sine", 1320, 1320, 0.12, 0.035, 0.2, pan, 0.08);
    this.noise(0.18, 0.018, "highpass", 3200, pan, 0.06);
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
    this.master.gain.value = this.enabled ? 0.8 * this.volume : 0;
    this.compressor = this.context.createDynamicsCompressor();
    this.compressor.threshold.value = -18;
    this.compressor.knee.value = 20;
    this.compressor.ratio.value = 7;
    this.compressor.attack.value = 0.003;
    this.compressor.release.value = 0.28;
    this.reverb = this.context.createConvolver();
    this.reverb.buffer = createImpulseBuffer(this.context, 1.8, 2.8);
    this.reverbReturn = this.context.createGain();
    this.reverbReturn.gain.value = 0.2;
    this.reverb.connect(this.reverbReturn);
    this.reverbReturn.connect(this.compressor);
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
    pan = 0,
    reverbSend = 0
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
    this.routeOutput(gain, pan, reverbSend);
    oscillator.start(now);
    oscillator.stop(now + duration + 0.04);
  }

  private noise(
    duration: number,
    volume: number,
    filterType: NoiseFilter,
    frequency: number,
    pan = 0,
    reverbSend = 0,
    delaySeconds = 0
  ) {
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
    const now = context.currentTime + delaySeconds;
    gain.gain.setValueAtTime(0.0001, now);
    gain.gain.exponentialRampToValueAtTime(Math.max(0.0002, volume), now + 0.006);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + duration);
    source.connect(filter);
    filter.connect(gain);
    this.routeOutput(gain, pan, reverbSend);
    source.start(now);
  }

  private routeOutput(source: AudioNode, pan: number, reverbSend: number) {
    const context = this.context;
    const master = this.master;
    if (context === null || master === null) {
      return;
    }
    const panner = createPanner(context, pan);
    if (panner === null) {
      source.connect(master);
    } else {
      source.connect(panner);
      panner.connect(master);
    }
    if (reverbSend > 0 && this.reverb !== null) {
      const send = context.createGain();
      send.gain.value = reverbSend;
      if (panner === null) {
        source.connect(send);
      } else {
        panner.connect(send);
      }
      send.connect(this.reverb);
    }
  }
}

function createImpulseBuffer(
  context: AudioContext,
  durationSeconds: number,
  decay: number
): AudioBuffer {
  const frameCount = Math.max(1, Math.floor(context.sampleRate * durationSeconds));
  const buffer = context.createBuffer(2, frameCount, context.sampleRate);
  for (let channel = 0; channel < buffer.numberOfChannels; channel += 1) {
    const data = buffer.getChannelData(channel);
    for (let index = 0; index < frameCount; index += 1) {
      const envelope = Math.pow(1 - index / frameCount, decay);
      data[index] = (Math.random() * 2 - 1) * envelope;
    }
  }
  return buffer;
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
  return Math.max(-0.65, Math.min(0.65, (event.x / battleMapWidth) * 1.4 - 0.7));
}
