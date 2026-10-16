import subprocess

cmd = 'sh /data/local/tmp/test_telem.sh'
res = subprocess.run(['adb', '-s', '192.168.1.13:5555', 'shell', 'su', '-c', cmd], capture_output=True, text=True)
parts = res.stdout.strip().split('|')
print("PARTS COUNT:", len(parts))
for i, p in enumerate(parts):
    print(f"[{i}]: {p}")

curMa = int(parts[5]) if len(parts) > 5 else 0
if abs(curMa) > 10000: curMa //= 1000
voltMv = int(parts[4]) if len(parts) > 4 else 0
if voltMv > 100000: voltMv //= 1000
watt = (curMa * voltMv) / 1000000.0 if curMa > 0 and voltMv > 0 else 0.0

rfcAuth = (parts[11].strip() == "1") if len(parts) > 11 else False
adpMv = int(parts[8]) if len(parts) > 8 else 0
if adpMv > 100000: adpMv //= 1000
chgTyp = parts[9] if len(parts) > 9 else ""
rawPtyp = parts[21] if len(parts) > 21 else ""
ptypUpper = rawPtyp.upper()
pdSig = parts[20] if len(parts) > 20 else ""
isPlugged = (parts[24].strip() == "1") if len(parts) > 24 else False

print("\n--- PROTOCOL EVALUATION ---")
print(f"isPlugged: {isPlugged}")
print(f"watt: {watt}W (curMa={curMa}, voltMv={voltMv})")
print(f"adpMv: {adpMv}")
print(f"rfcAuth: {rfcAuth}")
print(f"chgTyp: {chgTyp}")
print(f"rawPtyp: {rawPtyp}")
print(f"pdSig: {pdSig}")

# Evaluating Kotlin logic:
cond_pd = pdSig == "1" or pdSig == "2" or chgTyp == "7" or chgTyp == "4" or rawPtyp == "MTK_7" or rawPtyp == "MTK_4" or "PD" in ptypUpper or "PPS" in ptypUpper or (7500 <= adpMv <= 9800 and (not rfcAuth or watt < 28.0))
print(f"Matches cond_pd: {cond_pd}")

cond_transsion = rfcAuth or (chgTyp == "9" and (rfcAuth or watt >= 28.0 or adpMv > 9800)) or rawPtyp == "MTK_9" or "TRAN" in ptypUpper or "RFC" in ptypUpper
print(f"Matches cond_transsion: {cond_transsion}")
