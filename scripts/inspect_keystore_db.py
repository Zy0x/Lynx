import sqlite3

conn = sqlite3.connect(r"e:\Data\GitHub\Lynx\Emergency_Recovery\persistent.sqlite")
cursor = conn.cursor()

cursor.execute("PRAGMA table_info(keyentry);")
print("keyentry columns:", cursor.fetchall())

cursor.execute("SELECT id, alias, domain, namespace FROM keyentry WHERE alias LIKE '%synthetic%' LIMIT 10;")
print("Synthetic keys in keyentry:", cursor.fetchall())

cursor.execute("PRAGMA table_info(blobentry);")
print("blobentry columns:", cursor.fetchall())

cursor.execute("SELECT id, keyentryid, subcomponent_type, length(blob) FROM blobentry LIMIT 10;")
print("blobentry samples:", cursor.fetchall())
