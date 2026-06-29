#!/usr/bin/env python3
"""
Stage 0 — generate a raw sprite candidate from an image model via OpenRouter.

Builds the prompt as a fixed STYLE + swappable SUBJECT + fixed BACKGROUND/NEGATIVE
(see WARRANTLY/STAGE0_GUIDE.md), calls the OpenRouter chat/completions image API,
and saves the returned PNG(s). The downstream sprite_pipeline (Stages 1-5) then
keys out the flat magenta background and downscales to 32x32.

Usage:
    python3 gemini_stage0.py <subject-key|"free text"> [--n 1] [--model M] [--out DIR]

Reads OPENROUTER_API_KEY (and optional OPENROUTER_IMAGE_MODEL) from the repo-root
.env or the environment. The key is never printed.
"""
import argparse
import base64
import json
import os
import sys
import time
import urllib.request
import urllib.error

API_URL = "https://openrouter.ai/api/v1/chat/completions"
DEFAULT_MODEL = "google/gemini-3.1-flash-image"

# Fixed STYLE — vanilla-Minecraft item look (flat, limited palette, NO black keyline).
# This is the MC-fit style from STAGE0_GUIDE.md. The older HD Terraria/SNES style is
# kept below as STYLE_HD for reference; STYLE is what build_prompt uses.
STYLE = """Vanilla-Minecraft-style item texture. True pixel art: hard-edged square pixels, flat
solid color blocks, NO anti-aliasing, NO blur, NO gradient, NO dithering. Very limited
palette (about 2-4 shades per material). Define edges and form with the material's own
DARKER shade — do NOT trace the object with a black outline (vanilla Minecraft items
have no keyline); black may still be used for genuinely black parts like berries.
Subtle top-left light. Chunky low-res look as if drawn on a ~16x16 to 32x32 grid and
shown enlarged. Bold readable silhouette, minimal internal detail. Grounded, slightly
grim witchcraft-herbalism mood — NOT cute, NOT chibi, NOT a cartoon mascot, no goofy face."""

STYLE_HD = """Dark-fantasy 16-bit RPG inventory item icon, in the spirit of Terraria and classic
SNES item sprites. Hard-edged square pixels, ~32x32 grid, 3-4 values per material, one
thin 1px dark-desaturated outline. (Reads more detailed/HD — clashes with vanilla MC.)"""

# Magenta is the default key; use GREEN for subjects that themselves contain bright
# pink/magenta paint (e.g. an arcane magenta glow), so the bg doesn't collide with art.
BACKGROUND_MAGENTA = """Solid flat magenta background, exact color #FF00FF, perfectly uniform, no texture,
no shadow, no checkerboard, no transparency. Exactly ONE object, centered, fully
inside the frame with even empty margin on all sides, not touching any edge.
Square 1:1 composition."""

BACKGROUND_GREEN = """Solid flat green background, exact color #00FF00, perfectly uniform, no texture,
no shadow, no checkerboard, no transparency. Exactly ONE object, centered, fully
inside the frame with even empty margin on all sides, not touching any edge.
Square 1:1 composition."""

NEGATIVE = """No scenery, no ground, no second object, no text, no letters, no numbers, no
watermark, no signature, no logo, no sparkles, no decorative canvas border, no black
outline, no keyline, no anti-aliasing, no soft/painterly shading, no Stardew style,
no smooth gradients, no realistic shading, no 3D render, no busy fine detail, not cute,
not cartoonish, no smiling face."""

SUBJECTS = {
    "belladonna": "A single small belladonna sprig: one dark-purple bell flower, two glossy "
                  "black berries, a few green leaves on a short stem. Compact, icon-sized.",
    "mandrake": "A single dried mandrake root: a gnarled, twisted, forked root vaguely "
                "resembling a small body (leg-like and arm-like roots), earthy brown, small tuft "
                "of green leaves on top. Eerie, like a real witch's herb root — no face, not a character.",
    "wolfsbane": "A single wolfsbane sprig with tall deep-blue hooded flowers and dark jagged "
                 "leaves on a slender stem.",
    "fly_agaric": "A single red fly-agaric mushroom with a domed white-spotted cap and a pale stem.",
    "dried_herbs": "A single small bundle of dried herbs tied with twine, muted greens.",
    "mortar": "A single stone mortar and pestle with a few crushed herbs inside.",
    "black_candle": "A single short black ritual candle with a small warm flame and dripping wax.",
    "cauldron": "A single small cast-iron witch's cauldron on three stubby legs, faint green glow "
                "rising from inside.",
    "grimoire": "A single closed leather-bound spellbook with a metal clasp and a small glowing "
                "arcane sigil on the cover.",
    "athame": "Exactly ONE ritual dagger (athame): a single straight blade pointing up, dark wrapped "
              "handle, faint engraved steel blade. ONE dagger only — not crossed, not two, not a pair.",
    "potion_vial": "A single small corked glass vial filled with glowing violet liquid.",
    "rune_tablet": "A single ancient stone rune tablet with one glowing cyan Fehu rune carved in the "
                   "center, simple stone frame, faint cyan glow.",
    "amethyst": "A single jagged violet crystal shard with a faint inner glow.",

    # --- real Hexerei mod items (registry ids) ---
    "artichoke": "A single water-artichoke bud: a round spiky green-and-purple bud on a short stem "
                 "with a couple of leaves. Compact, icon-sized.",
    "celandine": "A single celandine sprig: small bright-yellow four-petal flowers and lobed green "
                 "leaves on a thin stem.",
    "crowseye_berry": "A single crowseye sprig: a small cluster of glossy black-purple poison berries "
                      "with a faint violet sheen and a few dark leaves.",
    "hops": "A single hop cone: a pale green papery cone-shaped flower hanging from a thin vine with one leaf.",
    "sandwort": "A single sandwort sprig: tiny white five-petal flowers and narrow grey-green leaves, "
                "sparse and wiry.",
    "wormwood": "A single wormwood sprig: feathery silvery-green divided leaves on a pale stem.",
    "icy_needle": "A single sharp icy needle: a thin translucent pale-cyan ice shard with a faint frosty glow.",
    "mistletoe_sprig": "A single mistletoe sprig: paired oval green leaves and a few white berries on a "
                       "short forked twig.",
    "glowing_spore": "A single glowing fungal spore cluster: a small clump of pale luminous witch-cyan "
                     "spores with a faint glow.",
    "puffball": "A single round white puffball mushroom: a smooth pale dome on a short earthy base.",
    "webcap": "A single webcap mushroom with a rusty orange-brown conical cap and a slender pale stem.",
    "zevanty": "A single eerie glowing mushroom with a tall pale stem and a domed cap emitting a soft "
               "witch-cyan glow.",
    "ward_charm": "A single protective ward charm: a small carved wooden amulet disc with one simple "
                  "engraved rune, on a short leather cord.",
    "bloodlust_charm": "A single bloodlust charm: a small amulet with a dark red blood-drop gem set in "
                       "iron, on a cord.",
    "hexbane_charm": "A single hexbane charm: a small amulet of bound black thorns around a dull grey "
                     "stone, on a cord.",
    "bone_charm": "A single bone charm: a small carved bone fetish bound with twine, pale ivory.",
    "wax_poppet": "A single wax poppet: a small crude humanoid figure of pale wax bound with thread and "
                  "one pin. Eerie, not cute, no face detail.",
    "obsidian_skull": "A single small carved obsidian skull of glossy black volcanic glass with a faint "
                      "violet sheen.",
    "ritual_chalk": "A single stick of white ritual chalk, worn at the tip, with a faint dusting of chalk.",
    "charm_pouch": "A single small drawstring leather pouch tied at the neck, faintly bulging with trinkets.",

    # --- block-face textures (placed) ---
    "ritual_sigil": "Top-down view of a witch's ritual sigil drawn on the ground: a circular arcane "
                    "glyph with concentric rings and small runic marks, faint cyan glow, on a dark "
                    "stone slab, filling the tile.",
    "blood_moss_block": "A seamless top-down texture of dark crimson blood moss covering the whole "
                        "frame edge to edge, damp clotted red-and-black moss, no background, no border.",

    # --- blood moss item + brews ---
    "blood_moss": "A single clump of blood moss: damp dark crimson-red moss with a clotted texture on "
                  "a small piece of grey stone.",
    "sleeping_draught": "A single small round corked glass potion bottle filled with deep indigo liquid "
                        "and a soft violet glow.",
    "brew_of_frailty": "A single small round corked glass potion bottle filled with sickly grey-green liquid.",

    # --- seeds (registry ids: seeds_<crop>, plus garlic bulb and mindrake bulb) ---
    "seeds_belladonna": "A small pile of belladonna seeds: a few tiny glossy black-purple seeds.",
    "seeds_mandrake": "A small pile of mandrake seeds: a few tiny wrinkled earthy-brown seeds.",
    "seeds_wolfsbane": "A small pile of wolfsbane seeds: a few tiny dark blue-grey seeds.",
    "seeds_artichoke": "A small pile of artichoke seeds: a few slender striped greyish seeds.",
    "garlic": "A single garlic bulb: a small papery off-white bulb with a few cloves and a dry root tuft.",
    "seeds_hellebore": "A small pile of hellebore seeds: a few tiny pale frost-grey seeds.",
    "seeds_wormwood": "A small pile of wormwood seeds: a few tiny silvery-green seeds.",
    "mindrake_bulb": "A single mindrake bulb: a small pale knotted root-bulb with a faint eerie tint and a wisp of root.",
    "seeds_celandine": "A small pile of celandine seeds: a few tiny dark seeds with pale tips.",
    "seeds_crowseye": "A small pile of crowseye seeds: a few tiny glossy black seeds.",
    "seeds_hops": "A small pile of hop seeds: a few tiny pale green-brown seeds.",
    "seeds_sandwort": "A small pile of sandwort seeds: a few tiny sandy-grey seeds.",
    "seeds_mistletoe": "A small pile of mistletoe seeds: a few tiny pale translucent sticky seeds.",
}


def load_env(path):
    if os.path.exists(path):
        for line in open(path):
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                os.environ.setdefault(k.strip(), v.strip())


def build_prompt(subject_text, bg="magenta"):
    background = BACKGROUND_GREEN if bg == "green" else BACKGROUND_MAGENTA
    return (f"STYLE (unified — identical for every asset of the mod):\n{STYLE}\n\n"
            f"SUBJECT:\n{subject_text}\n\n"
            f"BACKGROUND & FRAMING (fixed):\n{background}\n\n"
            f"NEGATIVE (fixed):\n{NEGATIVE}")


GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"


def request_image_openrouter(model, prompt, api_key):
    body = json.dumps({
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        # image-only: FLUX image models (e.g. black-forest-labs/flux.2-klein-4b) reject
        # ["image","text"] with 404 "no endpoints support … image, text". Asking just
        # ["image"] is the common denominator — Gemini/GPT image models honor it too.
        "modalities": ["image"],
    }).encode()
    req = urllib.request.Request(API_URL, data=body, headers={
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "HTTP-Referer": "https://github.com/vel5id/Minecraft-Re-Witch",
        "X-Title": "Hexerei sprite pipeline",
    })
    with urllib.request.urlopen(req, timeout=180) as resp:
        return json.loads(resp.read())


def extract_images_openrouter(data):
    """Raw PNG bytes from an OpenRouter chat/completions image response."""
    out = []
    for choice in data.get("choices", []):
        msg = choice.get("message", {})
        for img in msg.get("images", []) or []:
            url = (img.get("image_url") or {}).get("url", "")
            if url.startswith("data:") and "base64," in url:
                out.append(base64.b64decode(url.split("base64,", 1)[1]))
    return out


def request_image_google(model, prompt, api_key):
    body = json.dumps({
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"responseModalities": ["IMAGE", "TEXT"]},
    }).encode()
    url = GEMINI_URL.format(model=model) + f"?key={api_key}"
    req = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=180) as resp:
        return json.loads(resp.read())


def extract_images_google(data):
    """Raw PNG bytes from a Google generateContent (inlineData) response."""
    out = []
    for cand in data.get("candidates", []):
        for part in cand.get("content", {}).get("parts", []) or []:
            inl = part.get("inlineData") or part.get("inline_data")
            if inl and inl.get("data"):
                out.append(base64.b64decode(inl["data"]))
    return out


def generate_images(prompt, model=None):
    """Backend-aware generation. Returns (list[png_bytes], error_or_None, model_used).

    Backend chosen by IMAGE_BACKEND env ('google' | 'openrouter', default openrouter).
    Google uses the free AI Studio key (GEMINI_API_KEY); OpenRouter uses OPENROUTER_API_KEY.
    """
    backend = os.environ.get("IMAGE_BACKEND", "openrouter").lower()
    if backend in ("flux", "local"):
        import flux_backend
        imgs, err = flux_backend.generate(prompt, n=1, negative=NEGATIVE)
        return imgs, err, os.environ.get("LOCAL_MODEL", "qwen-image")
    if backend == "google":
        key = os.environ.get("GEMINI_API_KEY")
        if not key:
            return [], "GEMINI_API_KEY not set", None
        model = model or os.environ.get("GEMINI_IMAGE_MODEL", "gemini-3.1-flash-image")
        req_fn, ext_fn = request_image_google, extract_images_google
    else:
        key = os.environ.get("OPENROUTER_API_KEY")
        if not key:
            return [], "OPENROUTER_API_KEY not set", None
        model = model or os.environ.get("OPENROUTER_IMAGE_MODEL", DEFAULT_MODEL)
        req_fn, ext_fn = request_image_openrouter, extract_images_openrouter
    try:
        data = req_fn(model, prompt, key)
    except urllib.error.HTTPError as e:
        return [], f"HTTP {e.code}: {e.read().decode()[:200]}", model
    except Exception as e:  # noqa: BLE001 - surface transport errors to caller
        return [], f"{type(e).__name__}: {e}", model
    return ext_fn(data), None, model


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("subject", help="a SUBJECTS key, or free-text subject line")
    ap.add_argument("--n", type=int, default=1, help="how many variants to request")
    ap.add_argument("--model", default="", help="override model (else backend default)")
    ap.add_argument("--bg", default="magenta", choices=["magenta", "green"])
    ap.add_argument("--out", default=".", help="output directory")
    args = ap.parse_args()

    load_env(os.path.join(os.path.dirname(__file__), "..", "..", ".env"))

    subject_text = SUBJECTS.get(args.subject, args.subject)
    label = args.subject if args.subject in SUBJECTS else "custom"
    prompt = build_prompt(subject_text, bg=args.bg)
    os.makedirs(args.out, exist_ok=True)

    saved, report = [], {"subject": label, "backend": os.environ.get("IMAGE_BACKEND", "openrouter"),
                         "saved": [], "errors": []}
    for i in range(args.n):
        imgs, err, model = generate_images(prompt, args.model or None)
        report["model"] = model
        if err:
            report["errors"].append(err)
            continue
        if not imgs:
            report["errors"].append("no image returned (wrong modality or refusal)")
            continue
        for j, raw in enumerate(imgs):
            p = os.path.join(args.out, f"{label}_{i}_{j}.png")
            with open(p, "wb") as f:
                f.write(raw)
            saved.append(p)
            report["saved"].append(p)
        time.sleep(0.5)

    print(json.dumps(report, indent=2))
    return 0 if saved else 1


if __name__ == "__main__":
    sys.exit(main())
