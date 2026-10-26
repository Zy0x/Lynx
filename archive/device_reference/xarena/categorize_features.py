import re

with open('archive/device_reference/xarena/all_clean_strings.txt', 'r', encoding='utf-8') as f:
    strings = [l.strip() for l in f if l.strip()]

out_f = open('archive/device_reference/xarena/categorized_output.txt', 'w', encoding='utf-8')

def search_kw(kw_list, title):
    found = set()
    for s in strings:
        if any(k.lower() in s.lower() for k in kw_list):
            found.add(s)
    out_f.write(f"\n==================== {title} (Total: {len(found)}) ====================\n")
    for s in sorted(list(found)):
        out_f.write(f"- {s}\n")

search_kw(['xboost', 'x-boost', 'x boost'], "XBOOST / XBOOST AI")
search_kw(['combo', 'kombo', 'makro', 'macro'], "COMBO / MACRO")
search_kw(['shoulder', 'bahu', 'trigger', 'l1', 'r1'], "SHOULDER KEY / GAMING TRIGGERS")
search_kw(['touch', 'sentuh', 'sensitivitas', 'sensitivity', 'sampling'], "TOUCH & SENSITIVITY")
search_kw(['bypass', 'charge', 'pengisian daya', 'baterai'], "BYPASS CHARGING / POWER")
search_kw(['magic voice', 'suara ajaib', 'voice changer'], "MAGIC VOICE")
search_kw(['heavy mode', 'mode berat', 'beban berat'], "HEAVY MODE")
search_kw(['fps', 'frame rate', 'kecepatan bingkai', 'refresh rate'], "FPS & DISPLAY")
search_kw(['game space', 'xarena', 'ruang game', 'permainan'], "GAME SPACE / XARENA CORE")

out_f.close()
print("Done writing categorized_output.txt")
