export type Language = "ru" | "en";

import { en } from "./en";

const storageKey = "iceja.language";
let currentLanguage: Language = readLanguage();
const reverse = new Map<string, string>();
Object.entries(en).forEach(([russian, english]) => {
  if (!reverse.has(english)) {
    reverse.set(english, russian);
  }
});

const dynamicEnglish: Array<[RegExp, string]> = [
  [/^(\d+) сохранено$/, "$1 saved"],
  [/^(\d+) \/ (\d+) существ$/, "$1 / $2 creatures"],
  [/^Опыт (\d+)\/(\d+) · \+(\d+)% HP\/урон$/, "XP $1/$2 · +$3% HP/damage"],
  [/^Улей (\d+)\/(\d+)$/, "Hive $1/$2"],
  [/^(\d+) урона$/, "$1 damage"],
  [/^(\d+) убийств$/, "$1 kills"],
  [/^(\d+) выжило$/, "$1 survived"],
  [/^(\d+) HP улья$/, "$1 hive HP"],
  [/^Существ (\d+)$/, "Creatures $1"],
  [/^Снарядов (\d+)$/, "Projectiles $1"],
  [/^Контрольная сумма (\d+)$/, "Checksum $1"],
  [/^(\d+) существ · (\d+) BM$/, "$1 creatures · $2 BM"],
  [/^(\d+) урона · (\d+) точек$/, "$1 damage · $2 points"],
  [/^(\d+) существ$/, "$1 creatures"],
  [/^(\d+) органов$/, "$1 organs"],
  [/^(\d+) доступно$/, "$1 available"],
  [/^(\d+) точек$/, "$1 points"],
  [/^Атака по (.+)$/, "Attack on $1"],
  [/^(.+) получает (\d+) урона$/, "$1 takes $2 damage"],
  [/^Промах по (.+)$/, "Miss against $1"],
  [/^(.+) уничтожен$/, "$1 destroyed"],
  [/^(.+) восстановил (\d+)$/, "$1 restored $2"],
  [/^(.+) захватывает (.+)$/, "$1 captures $2"],
  [/^(.+) теряет (.+)$/, "$1 loses $2"],
  [/^(.+) появилось$/, "$1 appeared"],
  [/^(.+) победил$/, "$1 won"],
  [/^Труп съеден \(\+(\d+) энергии\)$/, "Corpse eaten (+$1 energy)"]
];

const dynamicRussian: Array<[RegExp, string]> = [
  [/^(\d+) saved$/, "$1 сохранено"],
  [/^(\d+) \/ (\d+) creatures$/, "$1 / $2 существ"],
  [/^XP (\d+)\/(\d+) · \+(\d+)% HP\/damage$/, "Опыт $1/$2 · +$3% HP/урон"],
  [/^Hive (\d+)\/(\d+)$/, "Улей $1/$2"],
  [/^(\d+) damage$/, "$1 урона"],
  [/^(\d+) kills$/, "$1 убийств"],
  [/^(\d+) survived$/, "$1 выжило"],
  [/^(\d+) hive HP$/, "$1 HP улья"],
  [/^Creatures (\d+)$/, "Существ $1"],
  [/^Projectiles (\d+)$/, "Снарядов $1"],
  [/^Checksum (\d+)$/, "Контрольная сумма $1"],
  [/^(\d+) creatures · (\d+) BM$/, "$1 существ · $2 BM"],
  [/^(\d+) damage · (\d+) points$/, "$1 урона · $2 точек"],
  [/^(\d+) creatures$/, "$1 существ"],
  [/^(\d+) organs$/, "$1 органов"],
  [/^(\d+) available$/, "$1 доступно"],
  [/^(\d+) points$/, "$1 точек"],
  [/^Attack on (.+)$/, "Атака по $1"],
  [/^(.+) takes (\d+) damage$/, "$1 получает $2 урона"],
  [/^Miss against (.+)$/, "Промах по $1"],
  [/^(.+) destroyed$/, "$1 уничтожен"],
  [/^(.+) restored (\d+)$/, "$1 восстановил $2"],
  [/^(.+) captures (.+)$/, "$1 захватывает $2"],
  [/^(.+) loses (.+)$/, "$1 теряет $2"],
  [/^(.+) appeared$/, "$1 появилось"],
  [/^(.+) won$/, "$1 победил"],
  [/^Corpse eaten \(\+(\d+) energy\)$/, "Труп съеден (+$1 энергии)"]
];

const tokenEnglish: Array<[string, string]> = [
  ["дальний бой", "ranged"],
  ["ближний бой", "melee"],
  ["Обзор:", "Vision:"],
  ["Здоровье:", "Health:"],
  ["Энергия:", "Energy:"],
  ["Регенерация энергии:", "Energy regen:"],
  ["Дистанция:", "Range:"],
  ["Точность:", "Accuracy:"],
  ["Взрыв:", "Splash:"],
  ["Урон по времени:", "Damage over time:"],
  ["Пробитие брони:", "Armor penetration:"],
  ["Броня от ", "Armor vs "],
  ["Уклонение:", "Evasion:"],
  ["Тяга:", "Traction:"],
  ["Скорость:", "Speed:"],
  ["Поворот:", "Turn:"],
  ["Расход энергии:", "Energy drain:"],
  ["Свойства:", "Traits:"],
  ["Цена ", "Price "],
  ["Опыт ", "XP "],
  ["Урон ", "Damage "],
  ["Оружие:", "Weapon:"],
  ["урона", "damage"],
  ["кг", "kg"],
  ["м/с", "m/s"],
  ["Архитектор", "Architect"],
  ["Бот-улей", "Bot hive"],
  ["доступно", "available"],
  ["органов", "organs"],
  ["существ", "creatures"],
  ["существо", "creature"],
  ["точек", "points"],
  ["узел", "node"],
  ["сохранено", "saved"],
  ["Улей", "Hive"],
  ["улья", "hive"],
  ["м,", "m,"],
  ["урона", "damage"],
  ["кинетики", "kinetic"],
  ["пронзания", "piercing"],
  ["термики", "thermal"],
  ["кислоты", "acid"],
  ["электричества", "electric"],
  ["токсинов", "toxic"]
];

const tokenRussian: Array<[string, string]> = tokenEnglish.map(([ru, en]) => [en, ru]);

function readLanguage(): Language {
  try {
    return window.localStorage.getItem(storageKey) === "en" ? "en" : "ru";
  } catch {
    return "ru";
  }
}

export function getLanguage(): Language {
  return currentLanguage;
}

export function setLanguage(language: Language) {
  currentLanguage = language;
  try {
    window.localStorage.setItem(storageKey, language);
  } catch {
    currentLanguage = language;
  }
  document.documentElement.lang = language;
}

export function t(text: string): string {
  if (currentLanguage === "ru") {
    return text;
  }
  return en[text] ?? text;
}

function translateCore(text: string): string {
  const exact = currentLanguage === "en" ? en[text] : reverse.get(text);
  if (exact !== undefined) {
    return exact;
  }
  const dynamic = currentLanguage === "en" ? dynamicEnglish : dynamicRussian;
  for (const [pattern, replacement] of dynamic) {
    if (pattern.test(text)) {
      return text.replace(pattern, replacement);
    }
  }
  let translated = text;
  const tokens = currentLanguage === "en" ? tokenEnglish : tokenRussian;
  tokens.forEach(([from, to]) => {
    translated = translated.split(from).join(to);
  });
  return translated;
}

function translateTextNode(node: Text) {
  const value = node.nodeValue ?? "";
  if (value.trim() === "") {
    return;
  }
  const leading = value.match(/^\s*/)?.[0] ?? "";
  const trailing = value.match(/\s*$/)?.[0] ?? "";
  const core = value.slice(leading.length, value.length - trailing.length);
  const translated = translateCore(core);
  if (translated !== core) {
    node.nodeValue = `${leading}${translated}${trailing}`;
  }
}

const attributeNames = ["title", "placeholder", "aria-label", "alt"];

function translateElement(element: Element) {
  attributeNames.forEach((name) => {
    const value = element.getAttribute(name);
    if (value !== null && value.trim() !== "") {
      const translated = translateCore(value);
      if (translated !== value) {
        element.setAttribute(name, translated);
      }
    }
  });
}

function shouldSkip(element: Element): boolean {
  const tag = element.tagName;
  return tag === "SCRIPT" || tag === "STYLE" || tag === "TEXTAREA" || tag === "CODE";
}

function translateSubtree(root: Node) {
  if (root.nodeType === Node.TEXT_NODE) {
    translateTextNode(root as Text);
    return;
  }
  if (!(root instanceof Element)) {
    if (root instanceof DocumentFragment) {
      root.childNodes.forEach(translateSubtree);
    }
    return;
  }
  if (shouldSkip(root)) {
    return;
  }
  translateElement(root);
  root.childNodes.forEach(translateSubtree);
}

export function translateDocument() {
  if (document.body === null) {
    return;
  }
  translateSubtree(document.body);
}

export function installDocumentLocalization() {
  setLanguage(currentLanguage);
  translateDocument();
  const observer = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      if (mutation.type === "characterData" && mutation.target instanceof Text) {
        translateTextNode(mutation.target);
      } else if (mutation.type === "attributes" && mutation.target instanceof Element) {
        translateElement(mutation.target);
      } else {
        mutation.addedNodes.forEach(translateSubtree);
      }
    });
  });
  observer.observe(document.body, {
    childList: true,
    characterData: true,
    subtree: true,
    attributes: true,
    attributeFilter: attributeNames
  });
  return () => observer.disconnect();
}
