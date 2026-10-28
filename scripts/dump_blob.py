import sqlite3

conn = sqlite3.connect(r"e:\Data\GitHub\Lynx\Emergency_Recovery\persistent.sqlite")
cursor = conn.cursor()

cursor.execute("SELECT blob FROM blobentry WHERE keyentryid = 3272235486342031835;")
row = cursor.fetchone()
blob = row[0]
print("Blob length:", len(blob))
print("Blob hex header (first 32 bytes):", blob[:32].hex())
print("Blob hex tail (last 32 bytes):", blob[-32:].hex())
