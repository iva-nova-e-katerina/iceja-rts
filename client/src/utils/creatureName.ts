const storageKey = "iceja.creatureNameSequenceV2";
let localSequence = 0;

export function nextCreatureName(base: string): string {
  let stored = 0;
  try {
    const raw = window.localStorage.getItem(storageKey);
    const parsed = raw === null ? 0 : Number(raw);
    if (Number.isFinite(parsed)) {
      stored = Math.max(0, Math.floor(parsed));
    }
  } catch {
    stored = localSequence;
  }
  const sequence = Math.max(stored, localSequence) + 1;
  localSequence = sequence;
  try {
    window.localStorage.setItem(storageKey, String(sequence));
  } catch {
    localSequence = sequence;
  }
  const suffix = String(sequence).padStart(3, "0");
  const label = base.trim().slice(0, Math.max(1, 23 - suffix.length)) || "Тварь";
  return `${label} ${suffix}`;
}
