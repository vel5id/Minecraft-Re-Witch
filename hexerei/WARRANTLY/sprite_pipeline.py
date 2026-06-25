#!/usr/bin/env python3
"""
sprite_pipeline.py -- Stages 1-5: raw model output -> clean transparent box×box sprite.

Pipeline (see PIPELINE-downscale.md):
  [1] load RGB
  [2] background_to_alpha   -- key the baked-in background to real alpha (chroma|neutral|colored|auto)
  [3] content_bbox          -- real content bbox, drop far stray pixels (watermark sparkle)
  [4] premult_downscale_fit -- premultiplied downscale, fit into box, centered
  [5] save PNG (box×box RGBA)

Each stage is a reusable function so the MCP wrapper can map tools onto them.
Deps: numpy, Pillow, scipy (scipy optional -- pure-numpy fallbacks included).
"""
import argparse
import json
import sys
import numpy as np
from PIL import Image

try:
    from scipy import ndimage
    _HAS_SCIPY = True
except Exception:
    _HAS_SCIPY = False


# ----------------------------------------------------------------------------- helpers
def _label(mask):
    """Connected-component labels (4-connectivity). scipy if present, else BFS."""
    if _HAS_SCIPY:
        return ndimage.label(mask)
    H, W = mask.shape
    lbl = np.zeros((H, W), np.int32)
    cur = 0
    from collections import deque
    for sy in range(H):
        for sx in range(W):
            if mask[sy, sx] and lbl[sy, sx] == 0:
                cur += 1
                dq = deque([(sy, sx)])
                lbl[sy, sx] = cur
                while dq:
                    y, x = dq.popleft()
                    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        ny, nx = y + dy, x + dx
                        if 0 <= ny < H and 0 <= nx < W and mask[ny, nx] and lbl[ny, nx] == 0:
                            lbl[ny, nx] = cur
                            dq.append((ny, nx))
    return lbl, cur


def _binary_dilation(mask, iters):
    if _HAS_SCIPY:
        return ndimage.binary_dilation(mask, iterations=iters)
    m = mask.copy()
    for _ in range(iters):
        d = m.copy()
        d[1:, :] |= m[:-1, :]; d[:-1, :] |= m[1:, :]
        d[:, 1:] |= m[:, :-1]; d[:, :-1] |= m[:, 1:]
        m = d
    return m


def _fill_holes(mask):
    if _HAS_SCIPY:
        return ndimage.binary_fill_holes(mask)
    # flood non-mask from border; anything not reached is an enclosed hole
    H, W = mask.shape
    free = ~mask
    lbl, n = _label(free)
    border = set(lbl[0, :]) | set(lbl[-1, :]) | set(lbl[:, 0]) | set(lbl[:, -1])
    border.discard(0)
    outside = np.isin(lbl, list(border))
    return mask | (free & ~outside)


# ----------------------------------------------------------------------------- stage 2 support
def detect_checker_palette(rgb, band=10):
    """Dominant border colors + a guessed mode. The border of a single-object
    sprite is overwhelmingly background."""
    ring = np.concatenate([
        rgb[:band, :, :].reshape(-1, 3), rgb[-band:, :, :].reshape(-1, 3),
        rgb[:, :band, :].reshape(-1, 3), rgb[:, -band:, :].reshape(-1, 3)])
    q = (ring // 8 * 8).astype(np.int32)
    keys, counts = np.unique(q, axis=0, return_counts=True)
    order = np.argsort(counts)[::-1]
    pal = keys[order][:4].astype(np.float32)
    top = pal[0]
    r, g, b = top
    sat = float(max(r, g, b) - min(r, g, b))
    luma = float(0.299 * r + 0.587 * g + 0.114 * b)
    # magenta/green chroma -> 'chroma'; low-sat dark -> 'neutral'/'colored'
    if sat > 90 and (g < 80 or r < 80):
        mode = "chroma"
    elif sat < 35:
        mode = "neutral"
    else:
        mode = "colored"
    return pal, mode, {"sat": round(sat, 1), "luma": round(luma, 1)}


# ----------------------------------------------------------------------------- stage 2
def background_to_alpha(rgb, mode="auto", tol=34, warm_protect=True, fill_holes=True,
                        protect_interior=True):
    """RGB uint8 -> alpha uint8 [H,W]. With protect_interior, removes only border-connected
    background so interior shadows survive (sprites); with protect_interior=False, keys ALL
    bg-colored pixels — enclosed loops become transparent too (line glyphs / runes)."""
    H, W = rgb.shape[:2]
    f = rgb.astype(np.float32)
    pal, auto_mode, _ = detect_checker_palette(rgb)
    if mode == "auto":
        mode = auto_mode
    bg = pal[0]

    if mode == "chroma":
        # snap to the EXACT pure chroma key so distance + despill are accurate
        # (the border average drifts, e.g. magenta read as [248,40,248]).
        R, G, B = bg
        if R > 180 and B > 180 and G < 110:
            bg = np.array([255, 0, 255], np.float32)   # magenta key
        elif G > 180 and R < 110 and B < 110:
            bg = np.array([0, 255, 0], np.float32)      # green key
        dist = np.linalg.norm(f - bg, axis=2)
        # Wider, higher ramp: the anti-aliased magenta HALO ring the model bakes
        # around the silhouette sits at mid distance; raising lo/hi makes the hard
        # threshold CUT that ring instead of keeping it as a purple fringe.
        lo = max(float(tol) * 1.8, 55.0)
        hi = lo + 95.0
        alpha = np.clip((dist - lo) / max(1e-3, hi - lo), 0, 1)
    elif mode == "neutral":
        mx = f.max(2); mn = f.min(2)
        sat = mx - mn
        # background = low-saturation pixels (grey checker), in any luma band
        bgness = np.clip(1 - sat / max(1.0, float(tol)), 0, 1)
        alpha = 1 - bgness
    else:  # colored: distance to palette, warm pixels protected
        dist = np.min([np.linalg.norm(f - c, axis=2) for c in pal], axis=0)
        alpha = np.clip((dist - tol) / (tol * 2.0), 0, 1)
        if warm_protect:
            warm = (f[:, :, 0] - f[:, :, 2]) > 12  # R > B => warm (plant shadow), keep
            alpha = np.where(warm, np.maximum(alpha, 0.6), alpha)

    bgmask = alpha < 0.5
    if protect_interior:
        # keep as background ONLY low-alpha regions connected to the frame border
        lbl, n = _label(bgmask)
        border = set(lbl[0, :]) | set(lbl[-1, :]) | set(lbl[:, 0]) | set(lbl[:, -1])
        border.discard(0)
        true_bg = np.isin(lbl, list(border))
        alpha = np.where(bgmask & ~true_bg, 1.0, alpha)   # interior pockets -> opaque
        if fill_holes:
            solid = _fill_holes(alpha >= 0.5)
            alpha = np.where(solid, np.maximum(alpha, 0.5), alpha)
        alpha[true_bg] = 0.0
    else:
        # line-glyph mode: any bg-colored pixel is background, enclosed loops included
        alpha[bgmask] = 0.0
    return (np.clip(alpha, 0, 1) * 255).astype(np.uint8), mode, bg


def despill(rgb, alpha, bg):
    """Un-mix the bg tint out of partially transparent edge pixels (no fringe)."""
    f = rgb.astype(np.float32)
    a = (alpha.astype(np.float32) / 255.0)[:, :, None]
    out = np.where(a > 0, (f - (1 - a) * bg) / np.where(a > 0, a, 1), 0)
    return np.clip(out, 0, 255).astype(np.uint8)


def decontaminate_edges(rgba, key, strength=1.0):
    """Neutralize residual key-color cast on the SILHOUETTE EDGE only (where spill
    lives). Interior art is untouched, so a genuinely purple subject (belladonna,
    amethyst) keeps its color while a magenta fringe on a brown root is killed."""
    arr = np.array(rgba).astype(np.float32)
    a = arr[:, :, 3]
    op = a > 0
    edge = op & _binary_dilation(~op, 1)          # opaque pixels touching transparency
    R, G, B = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2]
    if key[0] > 180 and key[2] > 180 and key[1] < 110:        # magenta: kill R&B excess over G
        excess = np.clip(np.minimum(R, B) - G, 0, None) * strength
        arr[:, :, 0] = np.where(edge, R - excess, R)
        arr[:, :, 2] = np.where(edge, B - excess, B)
    elif key[1] > 180:                                        # green: kill G excess
        excess = np.clip(G - np.maximum(R, B), 0, None) * strength
        arr[:, :, 1] = np.where(edge, G - excess, G)
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))


# ----------------------------------------------------------------------------- stage 3
def content_bbox(alpha, drop_strays=True, dilate_frac=0.04):
    """Real content bbox. With drop_strays, merge nearby parts by dilation and take
    the largest cluster, discarding far strays (watermark sparkle)."""
    op = alpha > 0
    if not op.any():
        return (0, 0, alpha.shape[1], alpha.shape[0])
    if drop_strays:
        iters = max(1, int(min(alpha.shape) * dilate_frac))
        lbl, n = _label(_binary_dilation(op, iters))
        if n > 1:
            sizes = np.array([(lbl == i).sum() for i in range(1, n + 1)])
            main = int(np.argmax(sizes)) + 1
            op = (lbl == main) & op
    ys, xs = np.where(op)
    return int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1


# ----------------------------------------------------------------------------- stage 4
def premult_downscale_fit(rgb, alpha, bbox, box=32, hard=True, hard_thr=128, margin=1, anchor="center"):
    """Premultiplied LANCZOS downscale of the cropped content, fit into a box×box
    transparent square. anchor='center' (item icon) or 'bottom' (a plant rooted on
    the ground, e.g. a cross-model mushroom/crop). hard => crisp binary alpha."""
    x0, y0, x1, y1 = bbox
    crop = np.dstack([rgb[y0:y1, x0:x1], alpha[y0:y1, x0:x1]]).astype(np.float32)
    al = crop[:, :, 3:4] / 255.0
    crop[:, :, :3] *= al                                   # premultiply
    pm = Image.fromarray(crop.astype(np.uint8))
    w, h = pm.size
    inner = max(1, box - 2 * margin)
    s = inner / max(w, h)
    nw, nh = max(1, round(w * s)), max(1, round(h * s))
    pm = pm.resize((nw, nh), Image.LANCZOS)
    r = np.array(pm).astype(np.float32)
    a2 = r[:, :, 3:4] / 255.0
    with np.errstate(divide="ignore", invalid="ignore"):
        r[:, :, :3] = np.where(a2 > 0, r[:, :, :3] / a2, 0)  # un-premultiply
    if hard:
        r[:, :, 3] = np.where(r[:, :, 3] >= hard_thr, 255, 0)
    content = Image.fromarray(np.clip(r, 0, 255).astype(np.uint8))
    canvas = Image.new("RGBA", (box, box), (0, 0, 0, 0))
    px = (box - nw) // 2
    py = (box - nh - margin) if anchor == "bottom" else (box - nh) // 2
    canvas.paste(content, (px, py), content)
    return canvas


# ----------------------------------------------------------------------------- end-to-end
def process(input_path, output_path, box=32, soft=False, mode="auto",
            tol=34, drop_strays=True, warm_protect=True, anchor="center", protect_interior=True):
    rgb = np.array(Image.open(input_path).convert("RGB"))
    alpha, used_mode, bg = background_to_alpha(rgb, mode, tol, warm_protect,
                                               protect_interior=protect_interior)
    clean_rgb = despill(rgb, alpha, bg)
    bbox = content_bbox(alpha, drop_strays)
    sprite = premult_downscale_fit(clean_rgb, alpha, bbox, box=box, hard=not soft, anchor=anchor)
    if used_mode == "chroma":
        sprite = decontaminate_edges(sprite, bg)   # kill any residual key fringe on the edge
    sprite.save(output_path)
    a = np.array(sprite)[:, :, 3]
    return {
        "input": input_path, "output": output_path, "box": box,
        "mode": used_mode, "bg": [int(c) for c in bg],
        "real_content_px": [bbox[2] - bbox[0], bbox[3] - bbox[1]],
        "fitted_px": [int((a.any(0)).sum()), int((a.any(1)).sum())],
        "opaque_pct": round(float((a > 0).mean()) * 100, 1),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input"); ap.add_argument("output")
    ap.add_argument("--box", type=int, default=32)
    ap.add_argument("--soft", action="store_true")
    ap.add_argument("--mode", default="auto", choices=["auto", "chroma", "neutral", "colored"])
    ap.add_argument("--tol", type=float, default=34)
    ap.add_argument("--keep-strays", action="store_true")
    ap.add_argument("--no-warm-protect", action="store_true")
    args = ap.parse_args()
    rep = process(args.input, args.output, box=args.box, soft=args.soft, mode=args.mode,
                  tol=args.tol, drop_strays=not args.keep_strays,
                  warm_protect=not args.no_warm_protect)
    print(json.dumps(rep, indent=2))


if __name__ == "__main__":
    main()
