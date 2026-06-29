#!/usr/bin/env python3
"""Refine belladonna growth-stage sprites: improved per-stage prompts tuned for
16px readability (bold, few large shapes, bottom-rooted, frame-filling), N candidates
per stage, each keyed + de-outlined to 16px and 32px. Picks happen by eye afterwards.

    python bella_refine.py [N]      # default N=3 candidates per stage
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
N = int(sys.argv[1]) if len(sys.argv) > 1 else 3
OUT = os.path.join(_HERE, "_compare", "bella_v2")
os.makedirs(OUT, exist_ok=True)

# Tuned for tiny crop textures: bold/large/few shapes, rooted at bottom, fills the frame.
STAGES = {
    0: "A belladonna seedling rooted at the bottom center, filling the lower third of the frame: "
       "a short sturdy stem with TWO bold rounded seed-leaves spreading out. Simple and clearly "
       "readable. No flowers, no berries.",
    1: "A young belladonna sprout rooted at the bottom center, filling the lower half of the frame: "
       "one upright green stem with a few BOLD large oval leaves. No flowers or berries yet.",
    2: "A leafy belladonna plant rooted at the bottom center, filling most of the frame height: a "
       "strong upright stem with several large oval green leaves, bushy and full. No flowers or berries yet.",
    3: "A maturing belladonna plant rooted at the bottom center, filling the frame: a tall leafy stem "
       "with large oval leaves and a few clearly visible dark-purple bell flowers. No berries yet.",
    4: "A fully mature belladonna plant rooted at the bottom center, filling the frame: a tall leafy "
       "stem with several large dark-purple bell flowers and a few big glossy black berries among bold "
       "green oval leaves. Lush and full.",
}

report = []
for stage, subject in STAGES.items():
    prompt = g0.build_prompt(subject, bg="magenta")
    for cand in range(N):
        t0 = time.time()
        imgs, err, model = g0.generate_images(prompt)
        dt = round(time.time() - t0, 1)
        rec = {"stage": stage, "cand": cand, "gen_s": dt, "error": err}
        if not err and imgs:
            raw = os.path.join(OUT, f"belladonna_stage_{stage}_{cand}__raw.png")
            with open(raw, "wb") as f:
                f.write(imgs[0])
            for box in (16, 32):
                sp.process(raw, os.path.join(OUT, f"belladonna_stage_{stage}_{cand}__{box}.png"),
                           box=box, anchor="bottom", deoutline=True)
        report.append(rec)
        print(json.dumps(rec), flush=True)
        time.sleep(0.3)
print("=== DONE ===", flush=True)
