#!/usr/bin/env python3
"""Mass-generate growth-stage crop sprites for every Hexerei crop (flux.2-klein-4b
via OpenRouter), keyed + de-outlined to 16px & 32px, bottom-anchored.

Per-stage prompts are built from a per-crop identity (leaf look + the distinctive
mature feature) on the same readability template that worked for belladonna: bold,
few large shapes, rooted at the bottom, fills the frame; the feature appears only in
the final 1-2 stages. One candidate per stage (first pass); resumable (skips existing).

    python gen_all_crops.py [only_crop]
"""
import json
import os
import sys
import time

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)
import gemini_stage0 as g0          # noqa: E402
import sprite_pipeline as sp        # noqa: E402

g0.load_env(os.path.join(_HERE, "..", "..", ".env"))
OUTROOT = os.path.join(_HERE, "_compare", "crops")

# crop -> (n_stages, leaf description, mature-feature phrase)
CROPS = {
    "artichoke": (5, "green-and-purple lobed leaves",
                  "a round spiky green-and-purple artichoke bud on top"),
    "celandine": (5, "lobed green leaves",
                  "small bright-yellow four-petal celandine flowers"),
    "crowseye":  (5, "dark green leaves",
                  "a cluster of glossy black-purple poison berries with a faint violet sheen"),
    "garlic":    (6, "tall slender upright green garlic shoots",
                  "tall green garlic stalks with a papery off-white bulb at the base"),
    "hellebore": (5, "dark green palmate leaves",
                  "pale green-and-white cup-shaped hellebore flowers"),
    "hops":      (5, "broad green vine leaves",
                  "pale green papery hop cones hanging among the leaves"),
    "mandrake":  (5, "a rosette of broad green leaves",
                  "a low rosette of broad green leaves with a few small round yellow mandrake fruits"),
    "mindrake":  (5, "eerie pale-green leaves",
                  "a pale knotted mindrake bulb among eerie pale-green leaves with a faint witch-cyan glow"),
    "mistletoe": (5, "paired oval green leaves",
                  "paired oval green leaves with small white mistletoe berries"),
    "sandwort":  (5, "narrow wiry grey-green leaves",
                  "tiny white five-petal sandwort flowers among sparse wiry leaves"),
    "wolfsbane": (8, "dark jagged green leaves",
                  "tall deep-blue hooded wolfsbane flowers above dark jagged leaves"),
    "wormwood":  (5, "feathery silvery-green divided leaves",
                  "feathery silvery-green foliage with tiny pale-yellow flower buds"),
}


def build_subject(name, leaf, feature, st, n):
    disp = name.replace("_", " ")
    f = st / max(1, n - 1)
    if st == 0:
        return (f"A {disp} seedling rooted at the bottom center, filling the lower third of the "
                f"frame: a short sturdy stem with TWO bold rounded seed-leaves spreading out. "
                f"Simple and clearly readable. No flowers, no fruit.")
    if f < 0.55:
        return (f"A young {disp} plant rooted at the bottom center, filling the lower half of the "
                f"frame: one upright stem with a few BOLD large {leaf}. No flowers or fruit yet.")
    if f < 1.0:
        return (f"A maturing {disp} plant rooted at the bottom center, filling most of the frame: "
                f"a leafy stem of {leaf} with {feature} just starting to form.")
    return (f"A fully mature {disp} plant rooted at the bottom center, filling the frame: {feature}, "
            f"among bold {leaf}. Lush and full.")


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    crops = {only: CROPS[only]} if only else CROPS
    total_done = 0
    for name, (n, leaf, feature) in crops.items():
        out = os.path.join(OUTROOT, name)
        os.makedirs(out, exist_ok=True)
        for st in range(n):
            final16 = os.path.join(out, f"{name}_stage_{st}__16.png")
            if os.path.exists(final16):
                print(json.dumps({"crop": name, "stage": st, "skipped": True}), flush=True)
                continue
            subject = build_subject(name, leaf, feature, st, n)
            t0 = time.time()
            imgs, err, model = g0.generate_images(g0.build_prompt(subject, bg="magenta"))
            rec = {"crop": name, "stage": st, "gen_s": round(time.time() - t0, 1), "error": err}
            if not err and imgs:
                raw = os.path.join(out, f"{name}_stage_{st}__raw.png")
                with open(raw, "wb") as fh:
                    fh.write(imgs[0])
                for box in (16, 32):
                    sp.process(raw, os.path.join(out, f"{name}_stage_{st}__{box}.png"),
                               box=box, anchor="bottom", deoutline=True)
                total_done += 1
            print(json.dumps(rec), flush=True)
            time.sleep(0.3)
    print(f"=== DONE ({total_done} generated) ===", flush=True)


if __name__ == "__main__":
    main()
