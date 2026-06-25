# Stage 0 — промт для Gemini (`gemini-3.1-flash-image`, Nano Banana 2)

Цель: заставить модель стабильно выдавать спрайт, идеально проходящий пайплайн
(`sprite_pipeline.py`) и дающий чистый 32×32. Главный принцип — **управлять фоном
и стилем на генерации**, а не чинить хаотичный вывод постом.

---

## 1. Модульный шаблон промта

Промт = фиксированный **STYLE** + сменный **SUBJECT** + фиксированные
**BACKGROUND/FRAMING** + **NEGATIVE**. Меняешь только SUBJECT.

Рекомендуется писать промт **на английском** — для арт-терминов (`pixel art`,
`anti-aliasing`, `palette`) модель слушается заметно стабильнее.

```
STYLE (fixed for every asset of the mod):
Vanilla-Minecraft-style item texture. True pixel art: hard-edged square pixels, flat
solid color blocks, NO anti-aliasing, NO blur, NO gradient, NO dithering. Very limited
palette (about 2-4 shades per material). Define edges and form with the material's own
DARKER shade — do NOT trace the object with a black outline (vanilla Minecraft items
have no keyline); black may still be used for genuinely black parts like berries.
Subtle top-left light. Chunky low-res look as if drawn on a ~32x32 grid and shown
enlarged. Bold readable silhouette, minimal internal detail. Grounded, slightly grim
witchcraft-herbalism mood — NOT cute, NOT chibi, NOT a cartoon mascot, no goofy face.

SUBJECT (swap per item — keep it SIMPLE, icon-sized):
<one short line, a single object, simplified for a tiny icon>

BACKGROUND & FRAMING (fixed):
Solid flat magenta background, exact color #FF00FF, perfectly uniform, no texture,
no shadow, no checkerboard, no transparency. Exactly ONE object, centered, fully
inside the frame with even empty margin on all sides, not touching any edge.
Square 1:1 composition.

NEGATIVE (fixed):
No scenery, no ground, no second object, no text, no letters, no numbers,
no watermark, no signature, no logo, no sparkles, no decorative canvas border,
no black outline, no keyline, no realistic shading, no photoreal detail, no 3D
render, no busy fine detail, not cute, not cartoonish, no smiling face.
```

> **Обводка.** Раньше в STYLE был «1px dark outline» — для ваниль-MC его убираем
> (там предметы без чёрного keyline; край = собственный тёмный оттенок). Чёрный для
> реально чёрных деталей (ягоды беладонны) остаётся — это не обводка.
> **Анти-мультяшность.** Милый/комичный вид у мандрагоры шёл от «humanoid with a
> small face» + чибитных пропорций. В 32px лицо почти всегда читается мультяшно —
> для серьёзного мода надёжнее вообще без лица (см. SUBJECT мандрагоры ниже).

> **Почему magenta.** `#FF00FF` не встречается в природных/каменных/растительных
> спрайтах → `mode="chroma"` в пайплайне кейит его без ореола, и тёмные тени НЕ
> выгрызаются (в отличие от тёмного цветного чекера). Исключение: если в самом
> предмете есть яркая малиновая/розовая краска — поменяй чрома-фон на чистый
> зелёный `#00FF00` (и `--mode chroma` отработает так же).

---

## 2. Готовые SUBJECT-строки под твои три ассета

Руна (упрощённая — без лишних заклёпок и царапин, чтобы читалась в 32px):
```
SUBJECT: A single ancient stone rune tablet with one glowing cyan Fehu rune (ᚠ)
carved in the center. Simple stone frame, faint cyan glow. One tablet only.
```

Беладонна (СИЛЬНО упрощена против твоего варианта — иконка, а не ботаническая
иллюстрация):
```
SUBJECT: A single small belladonna sprig: one dark-purple bell flower, two glossy
black berries, and a few green leaves on a short stem. Compact, icon-sized.
```

Мандрагора (без лица — чтобы не читалась как «юмор-мод»; лица в 32px почти всегда
выглядят мультяшно):
```
SUBJECT: A single dried mandrake root: a gnarled, twisted, forked root that vaguely
resembles a small body (two leg-like roots, two arm-like roots), earthy brown, with a
small tuft of green leaves sprouting from the top. Natural and eerie, like a real
witch's herb root — no face, not a character, not cartoonish.

# жутковатый вариант, если всё же хочется намёк на лицо (не милый):
SUBJECT (eerie alt): ...with a faint hollow anguished face barely suggested in the
bark, unsettling and grim — not smiling, not cute.
```

---

## 3. Настройки вызова (важно)

- Модель: `gemini-3.1-flash-image` (Nano Banana 2). Также есть
  `gemini-3-pro-image-preview` (дороже/чище) и старая `gemini-2.5-flash-image`.
- **aspect_ratio = "1:1"** — заставляет квадрат (плюс дублируется словами в промте).
- Видимый вотермарк-«искра» Gemini промтом НЕ убирается — это нормально, пайплайн
  его срезает (`drop_strays`).
- Генерируй **по 3-4 варианта** на один промт и выбирай лучший — модель
  стохастична, проще отобрать, чем доводить один прогон.

---

## 4. Методика итерации промта

1. Зафиксируй STYLE/BACKGROUND/NEGATIVE — трогай только SUBJECT.
2. Сгенерируй 3-4 варианта (`gemini_stage0.py --n 4`).
3. Каждый прогони через пайплайн и смотри метрики из JSON:
   - `recommended_mode` должен быть `chroma` (значит фон чистая magenta — хорошо);
   - `real_content_px` не должен упираться в края (есть отступ);
   - `opaque_pct` в разумном диапазоне (не 3% «дырки» и не 95% «каша»).
4. Если фон вышел не magenta → усиль строку BACKGROUND (повтори «exact #FF00FF,
   uniform, no checkerboard»). Если деталей слишком много → упрости SUBJECT
   («fewer flowers», «no scratches», «single bold shape»).
5. Нашёл рабочий SUBJECT-паттерн — переиспользуй для всех ассетов мода.

---

## 5. Связка со Stage 1-5

`gemini_stage0.py` делает весь путь за один вызов: строит промт → генерит →
сохраняет сырой PNG → прогоняет `sprite_pipeline.process` → отдаёт готовый 32×32 +
метрики. Это и есть кандидат на первый MCP-тул цепочки
(`generate_sprite(subject) -> path32`), встающий перед `process_sprite`.
