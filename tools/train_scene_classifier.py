#!/usr/bin/env python3
"""
Lynx Vision AI — Multi-Scene Vision Classifier & Training Suite (V4 2D Multi-Topology Edition)
Author: Lynx Core Team / NOIR
Location: tools/train_scene_classifier.py

Capabilities:
1. --pull: Pulls new captured scenes from Android device (/sdcard/Lynx/scenes/) via adb.
2. --extra-dir <dir>: Optional additional directory of captured scene PNGs to evaluate alongside archive/scenes/.
3. --evaluate: Runs full multi-scene V4 vision inference (Header 2D Discs + Dimmed Silhouettes + Autoplay Strokes + Special CG Cutscene + HP Bar Shield).
4. --report: Generates comprehensive JSON & Markdown reports in archive/scenes/.
"""

import os
import json
import math
import argparse
import subprocess
import numpy as np
from PIL import Image

SCENES_DIR = os.path.abspath("archive/scenes")
REPORT_JSON = os.path.join(SCENES_DIR, "classification_report.json")
REPORT_MD = os.path.join(SCENES_DIR, "TRAINING_REPORT.md")

# Calibrated V4 RGB bounds matching GenshinDialogueVision.kt & GenshinBottomSceneVision.kt
DIALOGUE_MIN_R, DIALOGUE_MAX_R = 196, 252
DIALOGUE_MIN_G, DIALOGUE_MAX_G = 190, 248
DIALOGUE_MIN_B, DIALOGUE_MAX_B = 175, 238

GOLD_MIN_R, GOLD_MAX_R = 170, 255
GOLD_MIN_G, GOLD_MAX_G = 130, 215
GOLD_MAX_B = 35


def is_dialogue_color(r, g, b):
    return (
        DIALOGUE_MIN_R <= r <= DIALOGUE_MAX_R
        and DIALOGUE_MIN_G <= g <= DIALOGUE_MAX_G
        and DIALOGUE_MIN_B <= b <= DIALOGUE_MAX_B
        and r >= g >= b
        and 6 <= (r - b) <= 40
    )


def is_dialogue_glyph_dark(r, g, b):
    mx = max(r, g, b)
    mn = min(r, g, b)
    return mx <= 130 and (mx - mn) <= 55


def is_autoplay_label_bright(r, g, b):
    mx = max(r, g, b)
    mn = min(r, g, b)
    return r >= 195 and g >= 190 and b >= 180 and (mx - mn) <= 45


def is_prompt_gold_color(r, g, b):
    return GOLD_MIN_R <= r <= GOLD_MAX_R and GOLD_MIN_G <= g <= GOLD_MAX_G and b <= GOLD_MAX_B and r >= g


def is_prompt_text_gold_strict(r, g, b):
    return 175 <= r <= 255 and 130 <= g <= 220 and 0 <= b <= 45 and r >= g and (r - b) >= 140


def is_prompt_diamond_gold(r, g, b):
    return 175 <= r <= 255 and 130 <= g <= 235 and 0 <= b <= 105 and r >= g and (r - b) >= 120


def is_hp_green(r, g, b):
    return 145 <= g <= 245 and 85 <= r <= 215 and b <= 95 and (g - r) >= 15 and (g - b) >= 70


def luma(px):
    return 0.299 * float(px[0]) + 0.587 * float(px[1]) + 0.114 * float(px[2])


def pull_scenes_from_device(device_serial=None):
    if device_serial is None:
        res = subprocess.run(["adb", "devices"], capture_output=True, text=True)
        for line in res.stdout.splitlines()[1:]:
            if "\tdevice" in line:
                device_serial = line.split("\t")[0].strip()
                break
    if not device_serial:
        print("No active ADB device found.")
        return
    print(f"Pulling /sdcard/Lynx/scenes/ from {device_serial} to {SCENES_DIR} ...")
    os.makedirs(SCENES_DIR, exist_ok=True)
    res = subprocess.run(["adb", "-s", device_serial, "pull", "/sdcard/Lynx/scenes/.", SCENES_DIR], capture_output=True, text=True)
    print(res.stdout or res.stderr)


def evaluate_header_2d(arr):
    h, w, _ = arr.shape
    ui_h = min(float(h), float(w) * 9.0 / 16.0)
    scale = ui_h / 1080.0
    y_min = max(6, min(h - 4, int(ui_h * 22.0 / 1080.0)))
    y_max = max(y_min + 12, min(h - 1, int(ui_h * 94.0 / 1080.0)))
    x_min = max(8, min(w - 2, int(w * 15 / 1000)))
    x_max = max(x_min + 100, min(w, int(w * 400 / 1000)))

    probe_y = (y_min + y_max) // 2
    step_sx = max(1, w // 640)
    black_c = white_c = cream_c = tot_s = 0
    for sx in range(x_min, x_max, step_sx):
        r, g, b = int(arr[probe_y, sx, 0]), int(arr[probe_y, sx, 1]), int(arr[probe_y, sx, 2])
        tot_s += 1
        if r < 30 and g < 30 and b < 30:
            black_c += 1
        if r > 230 and g > 230 and b > 230:
            white_c += 1
        if is_dialogue_color(r, g, b):
            cream_c += 1
    black_ratio = black_c / tot_s if tot_s else 0.0
    white_ratio = white_c / tot_s if tot_s else 0.0

    diam = max(22.0, ui_h * 46.0 / 1080.0)
    rad = diam * 0.5
    step_x = max(1, int(diam / 16.0))
    step_y = max(1, int(diam / 14.0))
    cy_min = max(y_min + 2, min(y_max - 2, int(ui_h * 38.0 / 1080.0)))
    cy_max = max(cy_min, min(y_max - 2, int(ui_h * 74.0 / 1080.0)))
    cx_min = max(16, x_min + int(rad))
    cx_max = min(w - 16, x_max - int(rad))

    cap_x = [-0.35, -0.15, 0.0, 0.15, 0.35]
    cap_y = [-0.75, -0.56, 0.56, 0.75]
    rim_x = [-0.78, -0.58, 0.58, 0.78]
    rim_y = [-0.30, 0.0, 0.30]
    core_o = [-0.38, -0.19, 0.0, 0.19, 0.38]

    cands = []
    for cx in range(cx_min, cx_max + 1, step_x):
        best = None
        for cy in range(cy_min, cy_max + 1, step_y):
            cap_hits = cap_tot = 0
            for dx in cap_x:
                px = int(cx + dx * rad)
                for dy in cap_y:
                    py = int(cy + dy * rad)
                    if 0 <= px < w and y_min <= py <= y_max:
                        cap_tot += 1
                        r, g, b = int(arr[py, px, 0]), int(arr[py, px, 1]), int(arr[py, px, 2])
                        if is_dialogue_color(r, g, b):
                            cap_hits += 1
            if cap_tot == 0 or cap_hits / cap_tot < 0.65:
                continue
            ring_hits = ring_tot = 0
            for dx in rim_x:
                px = int(cx + dx * rad)
                for dy in rim_y:
                    py = int(cy + dy * rad)
                    if 0 <= px < w and y_min <= py <= y_max:
                        ring_tot += 1
                        r, g, b = int(arr[py, px, 0]), int(arr[py, px, 1]), int(arr[py, px, 2])
                        if is_dialogue_color(r, g, b):
                            ring_hits += 1
            if ring_tot == 0 or ring_hits / ring_tot < 0.55:
                continue
            corner_cream = 0
            for dx in (-1.05, 1.05):
                for dy in (-1.05, 1.05):
                    px, py = int(cx + dx * rad), int(cy + dy * rad)
                    if 0 <= px < w and y_min <= py <= y_max:
                        r, g, b = int(arr[py, px, 0]), int(arr[py, px, 1]), int(arr[py, px, 2])
                        if is_dialogue_color(r, g, b):
                            corner_cream += 1
            if corner_cream > 1:
                continue
            core_dark = core_cream = core_tot = 0
            for dy in core_o:
                py = int(cy + dy * rad)
                for dx in core_o:
                    px = int(cx + dx * rad)
                    if 0 <= px < w and y_min <= py <= y_max:
                        core_tot += 1
                        r, g, b = int(arr[py, px, 0]), int(arr[py, px, 1]), int(arr[py, px, 2])
                        if is_dialogue_glyph_dark(r, g, b):
                            core_dark += 1
                        elif is_dialogue_color(r, g, b):
                            core_cream += 1
            if core_tot > 0:
                df, cf = core_dark / core_tot, core_cream / core_tot
                if df >= 0.08 and cf >= 0.16 and (df + cf) >= 0.50:
                    sc = cap_hits + ring_hits + core_dark
                    if best is None or sc > best[0]:
                        best = (sc, cx, cy, cap_hits + ring_hits + core_cream)
        if best is not None:
            cands.append(best)

    discs = []
    nms_dist = diam * 0.85
    for c in cands:
        if discs and abs(c[1] - discs[-1][1]) < nms_dist:
            if c[0] > discs[-1][0]:
                discs[-1] = c
        else:
            discs.append(c)

    def has_clean_gap(c1, c2):
        gx1, gx2 = int(c1[1] + rad * 1.15), int(c2[1] - rad * 1.15)
        mid_y = (c1[2] + c2[2]) // 2
        yh = max(2, int(rad * 0.45))
        gc = gt = 0
        for gy in range(max(y_min, mid_y - yh), min(y_max, mid_y + yh) + 1, max(1, step_y)):
            for gx in range(gx1, gx2 + 1, max(1, step_x)):
                gt += 1
                if is_dialogue_color(int(arr[gy, gx, 0]), int(arr[gy, gx, 1]), int(arr[gy, gx, 2])):
                    gc += 1
        return gt > 0 and (gc / gt) < 0.25

    def has_auto_label(c_left, x_limit):
        x1 = max(0, int(c_left[1] + 0.70 * diam))
        x2 = min(w - 1, int(min(x_limit, c_left[1] + 4.8 * diam)))
        y1, y2 = max(y_min, int(c_left[2] - 0.34 * diam)), min(y_max, int(c_left[2] + 0.34 * diam))
        if x2 - x1 < int(diam * 0.8) or y2 <= y1:
            return False
        bright = tot = edges = 0
        for py in range(y1, y2 + 1):
            prev = False
            for px in range(x1, x2 + 1):
                hit = is_autoplay_label_bright(int(arr[py, px, 0]), int(arr[py, px, 1]), int(arr[py, px, 2]))
                tot += 1
                if hit:
                    bright += 1
                if px > x1 and hit != prev:
                    edges += 1
                prev = hit
        if tot == 0:
            return False
        return bright >= max(120, int(160 * scale)) and edges >= max(40, int(55 * scale)) and 0.04 <= (bright / tot) <= 0.45

    def has_dimmed_disc(cx_t, cy_t):
        if cx_t - int(rad) < 0 or cx_t + int(rad) >= w:
            return False
        ring_l, core_l, out_l = [], [], []
        for dyf in (-0.72, -0.45, 0.0, 0.45, 0.72):
            py = int(cy_t + dyf * rad)
            if not (y_min <= py <= y_max):
                continue
            for dxf in (-0.72, -0.45, 0.0, 0.45, 0.72):
                px = int(cx_t + dxf * rad)
                if not (0 <= px < w):
                    continue
                dist = math.hypot(dxf, dyf)
                lv = luma(arr[py, px])
                if 0.56 <= dist <= 0.88:
                    ring_l.append(lv)
                elif dist <= 0.36:
                    core_l.append(lv)
        for dyf in (-1.15, 0.0, 1.15):
            py = int(cy_t + dyf * rad)
            if not (y_min <= py <= y_max):
                continue
            for dxf in (-1.15, 1.15):
                px = int(cx_t + dxf * rad)
                if 0 <= px < w:
                    out_l.append(luma(arr[py, px]))
        if not ring_l or not core_l or not out_l:
            return False
        return (sum(ring_l) / len(ring_l) - sum(out_l) / len(out_l)) >= 14.0 and (sum(ring_l) / len(ring_l) - sum(core_l) / len(core_l)) >= 6.0

    adj_pairs = split_pairs = 0
    for i in range(len(discs)):
        for j in range(i + 1, len(discs)):
            c1, c2 = discs[i], discs[j]
            dx = float(c2[1] - c1[1])
            dy = abs(float(c2[2] - c1[2]))
            if dy > 0.30 * diam:
                continue
            if 1.55 * diam <= dx <= 2.55 * diam and has_clean_gap(c1, c2):
                adj_pairs += 1
            elif 2.55 * diam < dx <= 11.5 * diam and c1[0] >= 34 and c2[0] >= 34 and max(c1[0], c2[0]) >= 38 and has_clean_gap(c1, c2):
                if (len(discs) >= 3 or has_auto_label(c1, c2[1] - rad * 1.1) or
                    has_dimmed_disc(int(c2[1] - 1.96 * diam), c2[2]) or
                    has_dimmed_disc(int(c2[1] + 1.96 * diam), c2[2]) or
                    has_dimmed_disc((c1[1] + c2[1]) // 2, c2[2])):
                    split_pairs += 1

    single_ok = False
    if adj_pairs == 0 and split_pairs == 0 and len(discs) == 1:
        c1 = discs[0]
        if c1[1] < int(w * 0.14) and c1[0] >= 40 and has_auto_label(c1, c1[1] + 4.8 * diam):
            dimmed_xs = []
            dim_start = int(c1[1] + 2.5 * diam)
            dim_end = min(x_max - int(rad) - 1, int(c1[1] + 11.5 * diam))
            dim_sx = max(2, int(diam / 8.0))
            dim_sy = max(2, int(diam / 12.0))
            for dxc in range(dim_start, dim_end + 1, dim_sx):
                dy_lo = max(y_min, c1[2] - int(0.15 * diam))
                dy_hi = min(y_max, c1[2] + int(0.15 * diam))
                if any(has_dimmed_disc(dxc, dyc) for dyc in range(dy_lo, dy_hi + 1, dim_sy)):
                    if not dimmed_xs or abs(dxc - dimmed_xs[-1]) >= nms_dist:
                        dimmed_xs.append(dxc)
            if len(dimmed_xs) >= 2:
                single_ok = True

    peak_cream = max(cream_c, max((d[3] for d in discs), default=0))
    return {
        "disc_count": len(discs),
        "aligned_pairs": adj_pairs,
        "split_pairs": split_pairs,
        "single_anchored": single_ok,
        "peak_cream": peak_cream,
        "black_ratio": round(black_ratio, 3),
        "white_ratio": round(white_ratio, 3),
    }


def extract_features(img_path):
    arr = np.array(Image.open(img_path).convert("RGB"))
    h, w, _ = arr.shape
    ui_h = min(float(h), float(w) * 9.0 / 16.0)
    s = ui_h / 1080.0
    xc = w // 2

    hdr = evaluate_header_2d(arr) if w >= h else {
        "disc_count": 0, "aligned_pairs": 0, "split_pairs": 0, "single_anchored": False,
        "peak_cream": 0, "black_ratio": 0.0, "white_ratio": 0.0
    }

    # Open-world HP bar (42%..58% W, 90.5%..94% H)
    hp_y1, hp_y2 = int(h * 0.905), int(h * 0.940)
    hp_x1, hp_x2 = int(w * 0.42), int(w * 0.58)
    hp_px = max_hp_run = 0
    for py in range(hp_y1, hp_y2 + 1, max(1, int(s))):
        cur = 0
        for px in range(hp_x1, hp_x2 + 1):
            if is_hp_green(int(arr[py, px, 0]), int(arr[py, px, 1]), int(arr[py, px, 2])):
                hp_px += 1
                cur += 1
                if cur > max_hp_run:
                    max_hp_run = cur
            else:
                cur = 0
    has_hp = hp_px >= max(120, int(260 * s)) and max_hp_run >= max(24, int(40 * s))

    # Multi-row prompt text (85.2%..90.8% H)
    ty1, ty2 = int(h * 0.852), int(h * 0.908)
    half_pw = max(160, int(260 * s))
    tx1, tx2 = max(0, xc - half_pw), min(w - 1, xc + half_pw)
    txt_px = txt_rows = txt_edges = txt_tot = 0
    for py in range(ty1, ty2 + 1):
        rg = 0
        prev = False
        for px in range(tx1, tx2 + 1):
            hit = is_prompt_text_gold_strict(int(arr[py, px, 0]), int(arr[py, px, 1]), int(arr[py, px, 2]))
            txt_tot += 1
            if hit:
                txt_px += 1
                rg += 1
            if px > tx1 and hit != prev:
                txt_edges += 1
            prev = hit
        if rg >= max(4, int(5 * s)):
            txt_rows += 1
    has_prompt_txt = txt_px >= max(120, int(220 * s)) and txt_rows >= max(5, int(7 * s)) and txt_edges >= max(60, int(100 * s)) and (txt_px / max(1, txt_tot)) <= 0.42

    # Centered diamond (91.5%..96.8% H)
    dy1, dy2 = int(h * 0.915), int(h * 0.968)
    half_dw = max(16, int(28 * s))
    dx1, dx2 = max(0, xc - half_dw), min(w - 1, xc + half_dw)
    fx_l1, fx_l2 = max(0, xc - int(140 * s)), max(0, xc - int(36 * s))
    fx_r1, fx_r2 = min(w - 1, xc + int(36 * s)), min(w - 1, xc + int(140 * s))
    dia_px = dia_rows = fl_g = fl_t = 0
    for py in range(dy1, dy2 + 1):
        rd = 0
        for px in range(dx1, dx2 + 1):
            if is_prompt_diamond_gold(int(arr[py, px, 0]), int(arr[py, px, 1]), int(arr[py, px, 2])):
                dia_px += 1
                rd += 1
        if rd >= max(2, int(2 * s)):
            dia_rows += 1
            for px in list(range(fx_l1, fx_l2, 2)) + list(range(fx_r1, fx_r2, 2)):
                fl_t += 1
                if is_prompt_diamond_gold(int(arr[py, px, 0]), int(arr[py, px, 1]), int(arr[py, px, 2])):
                    fl_g += 1
    has_dia = dia_px >= max(14, int(20 * s)) and dia_rows >= max(3, int(4 * s)) and (fl_g / fl_t if fl_t else 0.0) < 0.15

    # Row 944 margin blackness
    y944 = int(h * 944 / 1080)
    mx1, mx2 = int(w * 0.10), int(w * 0.90)
    pw = max(300, int(h * 450 / 1080))
    cs, ce = (w // 2) - (pw // 2), (w // 2) + (pw // 2)
    mb_c = mb_t = 0
    for px in range(mx1, mx2):
        if not (cs <= px < ce):
            mb_t += 1
            r, g, b = int(arr[y944, px, 0]), int(arr[y944, px, 1]), int(arr[y944, px, 2])
            if r < 25 and g < 25 and b < 25:
                mb_c += 1
    margin_black = mb_c / mb_t if mb_t else 0.0

    screen_mean = arr.mean(axis=(0, 1)).round(1).tolist()
    return {
        "filename": os.path.basename(img_path),
        "resolution": {"width": w, "height": h},
        "header_2d": hdr,
        "bottom_scene": {
            "has_hp_bar": has_hp, "hp_pixels": hp_px, "max_hp_run": max_hp_run,
            "has_prompt_text": has_prompt_txt, "prompt_gold_pixels": txt_px, "prompt_rows": txt_rows,
            "has_diamond": has_dia, "diamond_pixels": dia_px,
            "margin_black_ratio": round(margin_black, 3)
        },
        "global": {"mean_rgb": screen_mean}
    }


def classify(feat):
    w, h = feat["resolution"]["width"], feat["resolution"]["height"]
    if w < h:
        return "OPEN_WORLD", 0.95, "suppress_0_tap (portrait)"
    hdr = feat["header_2d"]
    bot = feat["bottom_scene"]

    if hdr["aligned_pairs"] >= 1:
        return "STANDARD_DIALOGUE", 0.99, "advance_and_choices (adjacent_discs)"
    if (hdr["split_pairs"] >= 1 or hdr["single_anchored"]) and not bot["has_hp_bar"]:
        return "STANDARD_DIALOGUE", 0.98, "advance_and_choices (dimmed_siblings_shielded)"
    if hdr["white_ratio"] > 0.95:
        return "WHITE_LOADING", 0.99, "suppress_0_tap"
    if bot["has_hp_bar"]:
        return "OPEN_WORLD", 0.99, "suppress_0_tap (HP Bar Active)"
    if hdr["black_ratio"] >= 0.80 and bot["margin_black_ratio"] >= 0.80 and (bot["has_prompt_text"] or (bot["has_diamond"] and bot["prompt_gold_pixels"] >= 30)):
        return "BLACK_INTERSTITIAL", 0.99, "tap_prompt_target"
    if bot["has_prompt_text"] and bot["has_diamond"]:
        return "SPECIAL_NARRATION", 0.98, "tap_prompt_target (CG Cutscene)"
    if hdr["white_ratio"] > 0.85:
        return "WHITE_LOADING", 0.95, "suppress_0_tap"
    mean_rgb = feat["global"]["mean_rgb"]
    if 25 <= mean_rgb[0] <= 45 and 55 <= mean_rgb[1] <= 95 and 95 <= mean_rgb[2] <= 170:
        return "START_SCREEN", 0.95, "suppress_0_tap"
    return "OPEN_WORLD", 0.95, "suppress_0_tap (Siaga Hening)"


def run_suite(extra_dirs=None, generate_md=True):
    seen = {}
    for d in [SCENES_DIR] + (extra_dirs or []):
        if not os.path.isdir(d):
            continue
        for f in sorted(os.listdir(d)):
            if f.lower().endswith(".png"):
                seen[f] = os.path.join(d, f)
    png_files = [seen[k] for k in sorted(seen.keys())]
    if not png_files:
        print(f"No PNG files found in {SCENES_DIR}")
        return

    print(f"Running Lynx Vision AI V4 Classifier on {len(png_files)} scenes...\n")
    results = []
    summary = {}

    for p in png_files:
        feat = extract_features(p)
        stype, conf, action = classify(feat)
        feat["classification"] = {"scene_type": stype, "confidence": conf, "recommended_action": action}
        results.append(feat)
        summary[stype] = summary.get(stype, 0) + 1
        print(f"[{feat['filename']:44s}] -> {stype:20s} (conf: {conf:.2f}) | {action}")

    print("\n=== Scene Taxonomy Distribution ===")
    for k, v in summary.items():
        print(f"  {k:22s}: {v:2d} scenes ({v/len(png_files)*100:.1f}%)")

    with open(REPORT_JSON, "w", encoding="utf-8") as out:
        json.dump(results, out, indent=2)
    print(f"\nSaved JSON report: {REPORT_JSON}")

    if generate_md:
        md_lines = [
            "# Lynx Vision AI — Scene Training & Calibration Report (V4 Multi-Topology Edition)",
            f"> Evaluated **{len(png_files)} captured Genshin scenes** across standard dialogues, dimmed cutscene controls, special CG narrations, black interstitials, open world, menus, and start/loading screens.",
            "",
            "## 1. Classification Summary",
            "| Scene Type | Count | Percentage | Autonomous Action | Vision Signature / Anti-False-Positive Shield |",
            "| :--- | :--- | :--- | :--- | :--- |"
        ]
        action_map = {
            "STANDARD_DIALOGUE": ("⚡ Fast Cycling (SafeAdvance, Choices, Narration)", "2D Cream Discs (Adjacent + Split/Dimmed Siblings + Autoplay Strokes)"),
            "BLACK_INTERSTITIAL": ("⚡ Prompt Confirmation Tap (Coords Narration)", "Black Header >= 80% + Margins >= 80% + Gold Prompt/Diamond"),
            "SPECIAL_NARRATION": ("⚡ Prompt Confirmation Tap (Coords Narration)", "Multi-Row Amber-Gold Prompt Text + Isolated Centered Diamond + No HP Bar"),
            "WHITE_LOADING": ("🛡️ Siaga Hening (0-Tap Suppressed)", "White Ratio >= 85%"),
            "START_SCREEN": ("🛡️ Siaga Hening (0-Tap Suppressed)", "Celestia Sky Profile & Zero Dialogue Anchors"),
            "OPEN_WORLD": ("🛡️ Siaga Hening (0-Tap Player Control)", "Active Green HP Bar Shield / Zero Disc Pairs")
        }
        for k, v in summary.items():
            act, shield = action_map.get(k, ("0-Tap", "Default Shield"))
            md_lines.append(f"| **`{k}`** | {v} | {v/len(png_files)*100:.1f}% | {act} | {shield} |")

        md_lines.extend([
            "",
            "## 2. Per-Scene Telemetry & Features",
            "| Filename | Group | Predicted Scene | Discs (Adj/Split/Single) | Prompt Gold | Diamond | HP Bar |",
            "| :--- | :--- | :--- | :--- | :--- | :--- | :--- |"
        ])
        for r in results:
            h2 = r["header_2d"]
            bs = r["bottom_scene"]
            st = r["classification"]["scene_type"]
            grp = "DIALOG" if st in ("STANDARD_DIALOGUE", "BLACK_INTERSTITIAL", "SPECIAL_NARRATION") else "NON_DIALOG"
            disc_info = f"{h2['disc_count']} ({h2['aligned_pairs']}/{h2['split_pairs']}/{int(h2['single_anchored'])})"
            md_lines.append(
                f"| `{r['filename']}` | **{grp}** | **`{st}`** | {disc_info} | "
                f"{bs['prompt_gold_pixels']} ({bs['prompt_rows']}r) | {bs['diamond_pixels']} | {bs['hp_pixels']} |"
            )

        with open(REPORT_MD, "w", encoding="utf-8") as out_md:
            out_md.write("\n".join(md_lines) + "\n")
        print(f"Saved Markdown report: {REPORT_MD}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Lynx Vision AI V4 Training Suite")
    parser.add_argument("--pull", action="store_true", help="Pull captured scenes from connected device")
    parser.add_argument("--extra-dir", action="append", default=[], help="Additional folder of captured scene PNGs")
    parser.add_argument("--evaluate", action="store_true", help="Evaluate dataset and produce reports")
    args = parser.parse_args()

    if args.pull:
        pull_scenes_from_device()
    run_suite(extra_dirs=args.extra_dir)
