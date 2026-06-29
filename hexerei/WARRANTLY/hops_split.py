#!/usr/bin/env python3
"""Generate a TALL hop bine and slice it into a seamless 2-block crop: a 16x32 plant
cut into upper (top block) + lower (bottom block) 16x16 textures. Stacking the two
crop blocks recombines the original plant, so nothing floats.

    python hops_split.py [N]   # N candidates (default 3)
"""
import os, sys, json, time
import numpy as np
from PIL import Image

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)
import gemini_stage0 as g0          # noqa: E402
import sprite_pipeline as sp        # noqa: E402

g0.load_env(os.path.join(_HERE, "..", "..", ".env"))
OUT = os.path.join(_HERE, "_compare", "hops_split")
os.makedirs(OUT, exist_ok=True)
N = int(sys.argv[1]) if len(sys.argv) > 1 else 3

SUBJECT = ("A single TALL hop bine climbing plant, tall vertical portrait filling the ENTIRE "
           "frame from the very bottom to the very top: one upright climbing vine with broad "
           "green lobed hop leaves along its whole length and several clusters of pale-green "
           "papery cone-shaped hop flowers near the TOP. Rooted at the bottom, one continuous "
           "tall plant, bold readable silhouette.")


def slice_tall(raw_path, stem):
    """Key + de-outline the raw, fit the plant into a 16x32 canvas (bottom-anchored),
    then slice into upper (rows 0-15) and lower (rows 16-31) 16x16 textures."""
    rgb = np.array(Image.open(raw_path).convert("RGB"))
    alpha, mode, bg = sp.background_to_alpha(rgb, "auto", 34, True)
    alpha = sp.strip_dark_outline(alpha, rgb)
    clean = sp.despill(rgb, alpha, bg)
    x0, y0, x1, y1 = sp.content_bbox(alpha, True)
    crop = np.dstack([clean[y0:y1, x0:x1], alpha[y0:y1, x0:x1]]).astype(np.float32)
    al = crop[:, :, 3:4] / 255.0
    crop[:, :, :3] *= al
    pm = Image.fromarray(crop.astype(np.uint8))
    w, h = pm.size
    BW, BH = 16, 32                                  # 2-block canvas
    s = min((BW - 0) / w, BH / h)                    # fit whole plant into 16x32
    nw, nh = max(1, round(w * s)), max(1, round(h * s))
    pm = pm.resize((nw, nh), Image.LANCZOS)
    r = np.array(pm).astype(np.float32)
    a2 = r[:, :, 3:4] / 255.0
    with np.errstate(divide="ignore", invalid="ignore"):
        r[:, :, :3] = np.where(a2 > 0, r[:, :, :3] / a2, 0)
    r[:, :, 3] = np.where(r[:, :, 3] >= 128, 255, 0)
    content = Image.fromarray(np.clip(r, 0, 255).astype(np.uint8))
    canvas = Image.new("RGBA", (BW, BH), (0, 0, 0, 0))
    px = (BW - nw) // 2
    py = BH - nh                                     # bottom-anchored in the 2-block canvas
    canvas.paste(content, (px, py), content)
    # decontaminate residual key fringe
    canvas = sp.decontaminate_edges(canvas, bg)
    upper = canvas.crop((0, 0, 16, 16))              # top block
    lower = canvas.crop((0, 16, 16, 32))             # bottom block
    canvas.save(os.path.join(OUT, f"{stem}__full32.png"))
    upper.save(os.path.join(OUT, f"{stem}__top.png"))
    lower.save(os.path.join(OUT, f"{stem}__bottom.png"))
    return {"stem": stem, "fit": [nw, nh]}


for i in range(N):
    prompt = g0.build_prompt(SUBJECT, bg="magenta")
    t0 = time.time()
    imgs, err, model = g0.generate_images(prompt)
    rec = {"cand": i, "gen_s": round(time.time() - t0, 1), "error": err}
    if not err and imgs:
        raw = os.path.join(OUT, f"hops_tall_{i}__raw.png")
        open(raw, "wb").write(imgs[0])
        rec.update(slice_tall(raw, f"hops_tall_{i}"))
    print(json.dumps(rec), flush=True)
    time.sleep(0.3)
print("=== DONE ===", flush=True)
