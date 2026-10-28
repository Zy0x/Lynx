import sqlite3

conn = sqlite3.connect(r"e:\Data\GitHub\Lynx\Emergency_Recovery\persistent.sqlite")
cursor = conn.cursor()

cursor.execute("SELECT id, alias, domain, namespace FROM keyentry WHERE alias LIKE '%synthetic%' LIMIT 10;")
keys = cursor.fetchall()
print("Synthetic keys:", keys)

for k in keys:
    kid = k[0]
    cursor.execute("SELECT id, subcomponent_type, length(blob), blob FROM blobentry WHERE keyentryid = ?;", (kid,))
    blobs = cursor.fetchall()
    print(f"Blobs for {k[1]} (id {kid}):")
    for b in blobs:
        print(f"  blob id: {b[0]}, subcomponent_type: {b[1]}, length: {b[2]}")
