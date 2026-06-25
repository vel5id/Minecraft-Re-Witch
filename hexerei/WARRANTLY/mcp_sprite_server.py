#!/usr/bin/env python3
"""
FastMCP server for the Hexerei sprite pipeline (Stage 0 generation + Stages 1-5
downscale). Wraps gemini_stage0.py and sprite_pipeline.py as MCP tools so the whole
path "prompt -> clean 32x32 sprite" is callable from an MCP client.

Tools
  inspect_image(input_path)                     -> size, real-alpha?, transparency, bg, recommended mode
  process_sprite(input_path, output_path, ...)  -> downscale an existing image to a box×box sprite
  generate_sprite(subject, out_dir, ...)        -> end-to-end: gemini -> raw PNG -> 32×32 sprite(s)

Run (stdio, the way Claude Desktop / .mcp.json launches it):
    python3 mcp_sprite_server.py

Tools return JSON STRINGS (not dicts) so nested objects are not truncated by clients.
The OpenRouter key is read from the gitignored repo-root .env; it is never returned.
"""
import json
import os
import sys

import numpy as np
from PIL import Image
from mcp.server.fastmcp import FastMCP

# Make sibling modules importable regardless of the launch cwd.
_HERE = os.path.dirname(os.path.abspath(__file__))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)

import sprite_pipeline as sp          # noqa: E402
import gemini_stage0 as g0            # noqa: E402

# Load the repo-root .env (OPENROUTER_API_KEY, model defaults) at startup.
g0.load_env(os.path.join(_HERE, "..", "..", ".env"))

mcp = FastMCP("hexerei-sprite")


@mcp.tool()
def inspect_image(input_path: str) -> str:
    """Inspect a raw image before downscaling.

    Returns JSON: pixel size, whether it already has real alpha, the % of
    transparent pixels, the detected background color, and the RECOMMENDED keying
    mode ('chroma' for a flat magenta/green bg, 'neutral' for a grey checker,
    'colored' for a dark colored checker). Call this first if unsure of the mode.
    """
    im = Image.open(input_path)
    has_alpha = im.mode in ("RGBA", "LA") or (im.mode == "P" and "transparency" in im.info)
    transparent_pct = 0.0
    if has_alpha:
        a = np.array(im.convert("RGBA"))[:, :, 3]
        transparent_pct = round(float((a == 0).mean()) * 100, 1)
    rgb = np.array(im.convert("RGB"))
    pal, mode, stats = sp.detect_checker_palette(rgb)
    return json.dumps({
        "input": input_path,
        "size": list(im.size),
        "has_real_alpha": bool(has_alpha),
        "transparent_pct": transparent_pct,
        "bg_color": [int(c) for c in pal[0]],
        "recommended_mode": mode,
        "border_stats": stats,
    })


@mcp.tool()
def process_sprite(input_path: str, output_path: str, box: int = 32, soft: bool = False,
                   mode: str = "auto", tol: float = 34.0, drop_strays: bool = True,
                   warm_protect: bool = True, anchor: str = "center") -> str:
    """Downscale a raw image to a clean transparent box×box sprite (Stages 1-5).

    Keys out the baked-in background to real alpha, crops to the real content
    (dropping far stray/watermark pixels), premultiplied-downscales into the box,
    and (for chroma backgrounds) decontaminates the silhouette edge so no magenta
    fringe survives. Returns JSON: output path, detected mode, real content size,
    fitted size, opaque %.

    box: target square size (32 recommended for item icons; 16 loses thin detail).
    soft: keep soft anti-aliased alpha instead of crisp item-style hard alpha.
    mode: 'auto' | 'chroma' | 'neutral' | 'colored'.
    """
    rep = sp.process(input_path, output_path, box=box, soft=soft, mode=mode,
                     tol=tol, drop_strays=drop_strays, warm_protect=warm_protect, anchor=anchor)
    return json.dumps(rep)


@mcp.tool()
def generate_sprite(subject: str, out_dir: str, box: int = 32, n: int = 1,
                    model: str = "", process: bool = True, bg: str = "magenta",
                    anchor: str = "center") -> str:
    """End-to-end: generate a raw sprite from the image model and downscale it.

    Builds the unified STYLE + SUBJECT + magenta-background prompt, requests `n`
    image(s) from the model (default: OPENROUTER_IMAGE_MODEL / gemini-3.1-flash-image),
    saves the raw PNG(s), and — when process=True — runs each through the downscale
    pipeline to a box×box sprite. Returns JSON: raw paths, sprite paths, per-sprite
    metrics, and any errors. `subject` may be a SUBJECTS key (e.g. 'mandrake',
    'belladonna') or a free-text subject line.
    """
    os.makedirs(out_dir, exist_ok=True)

    subject_text = g0.SUBJECTS.get(subject, subject)
    label = subject if subject in g0.SUBJECTS else "custom"
    prompt = g0.build_prompt(subject_text, bg=bg)

    report = {"subject": label, "backend": os.environ.get("IMAGE_BACKEND", "openrouter"),
              "raw": [], "sprites": [], "metrics": [], "errors": []}
    for i in range(n):
        imgs, err, used_model = g0.generate_images(prompt, model or None)
        report["model"] = used_model
        if err:
            report["errors"].append(err)
            continue
        if not imgs:
            report["errors"].append("no image returned (wrong modality or refusal)")
            continue
        for j, raw in enumerate(imgs):
            raw_path = os.path.join(out_dir, f"{label}_{i}_{j}.png")
            with open(raw_path, "wb") as f:
                f.write(raw)
            report["raw"].append(raw_path)
            if process:
                sprite_path = os.path.join(out_dir, f"{label}_{i}_{j}__{box}.png")
                report["metrics"].append(sp.process(raw_path, sprite_path, box=box, anchor=anchor))
                report["sprites"].append(sprite_path)
    return json.dumps(report)


if __name__ == "__main__":
    mcp.run()
