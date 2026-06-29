#!/usr/bin/env python3
"""Generate belladonna GROWTH-STAGE crop sprites via the OpenRouter backend.

Reads IMAGE_BACKEND/OPENROUTER_* from the repo-root .env (currently
black-forest-labs/flux.2-klein-4b). Each stage -> raw PNG + 16px (in-game match)
+ 32px sprite, magenta-keyed, bottom-anchored (a crop grows from the soil).

    python or_stage_gen.py [subject]      # default subject: belladonna
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

# argv: [model_id] [out_subdir]  — defaults keep the original flux-klein run.
MODEL = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("OPENROUTER_IMAGE_MODEL")
SUBDIR = sys.argv[2] if len(sys.argv) > 2 else "or_stages"
if MODEL:
    os.environ["OPENROUTER_IMAGE_MODEL"] = MODEL

OUT = os.path.join(_HERE, "_compare", SUBDIR)
os.makedirs(OUT, exist_ok=True)

# Per-stage SUBJECT lines for a deadly-nightshade (belladonna) crop, 0 -> 4.
STAGES = {
    0: "A just-sprouted belladonna seedling rising from the bottom center: two tiny pale-green "
       "seed leaves on a very short thin stem, barely above the ground. Tiny and sparse.",
    1: "A young belladonna sprout rising from the bottom center: a short green stem with a few "
       "small oval green leaves, no flowers yet. Small and low.",
    2: "A growing belladonna plant rising from the bottom center: a taller green leafy stem with "
       "several oval green leaves, bushier, still no flowers or berries.",
    3: "A maturing belladonna plant rising from the bottom center: a tall green leafy stem with "
       "oval leaves and a couple of small budding dark-purple bell flowers starting to form.",
    4: "A fully mature belladonna plant rising from the bottom center: a tall leafy green stem "
       "bearing dark-purple bell flowers and glossy black berries among oval green leaves. Lush.",
}

report = []
for stage, subject in STAGES.items():
    prompt = g0.build_prompt(subject, bg="magenta")
    t0 = time.time()
    imgs, err, model = g0.generate_images(prompt)
    dt = round(time.time() - t0, 1)
    rec = {"stage": stage, "model": model, "gen_seconds": dt, "error": err,
           "raw": None, "sprite16": None, "sprite32": None}
    if not err and imgs:
        raw = os.path.join(OUT, f"belladonna_stage_{stage}__raw.png")
        with open(raw, "wb") as f:
            f.write(imgs[0])
        rec["raw"] = raw
        for box in (16, 32):
            outp = os.path.join(OUT, f"belladonna_stage_{stage}__{box}.png")
            try:
                sp.process(raw, outp, box=box, anchor="bottom")
                rec[f"sprite{box}"] = outp
            except Exception as e:  # noqa: BLE001
                rec[f"sprite{box}_error"] = f"{type(e).__name__}: {e}"
    report.append(rec)
    print(json.dumps(rec), flush=True)
    time.sleep(0.5)

print("=== DONE ===", flush=True)
