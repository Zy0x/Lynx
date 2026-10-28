import sqlite3

conn = sqlite3.connect(r"e:\Data\GitHub\Lynx\Emergency_Recovery\persistent.sqlite")
cursor = conn.cursor()
cursor.execute("PRAGMA table_info(keyparameter);")
print("keyparameter schema:", cursor.fetchall())
cursor.execute("SELECT * FROM keyparameter WHERE keyentryid = 3272235486342031835;")
for row in cursor.fetchall():
    print(row)
