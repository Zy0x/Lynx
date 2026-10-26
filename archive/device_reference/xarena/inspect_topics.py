with open('archive/device_reference/xarena/game_features.txt', 'r', encoding='utf-8') as f:
    lines = [line.strip() for line in f if line.strip()]

print(f"Total lines: {len(lines)}")

# Let's filter out non-English / non-Indonesian if any, or print interesting ones
interesting = []
for l in lines:
    # check if latin letters
    if all(ord(c) < 500 for c in l):
        interesting.append(l)

print(f"Latin feature strings: {len(interesting)}")

# Group by topic
topics = {
    'Shoulder Key / Tombol Bahu': ['shoulder', 'bahu', 'l1', 'r1', 'tombol bahu'],
    'Gamepad / Key Mapping': ['gamepad', 'mapping', 'pemetaan'],
    'Heavy Mode / XBoost AI': ['heavy', 'xboost', 'boost', 'performa', 'kecepatan'],
    'Magic Voice / Suara': ['magic', 'suara', 'voice'],
    'Touch & Control': ['touch', 'sentuh', 'kontrol', 'akurasi'],
    'Bypass / Power': ['bypass', 'daya', 'baterai', 'charge'],
    'Network / Xunyou': ['xunyou', 'jaringan', 'latensi', 'network', 'vpn']
}

for topic, kws in topics.items():
    print(f"\n=== {topic} ===")
    matches = [l for l in interesting if any(k in l.lower() for k in kws)]
    print(f"Count: {len(matches)}")
    for m in matches[:10]:
        print("  -", m)
