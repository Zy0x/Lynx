import struct

with open(r"e:\Data\GitHub\Lynx\Emergency_Recovery\auth_token.bin", "rb") as f:
    data = f.read()

version, challenge, user_id, authenticator_id, auth_type, timestamp = struct.unpack("<BQQQIQ", data[:37])
hmac = data[37:]

print(f"Version:          {version}")
print(f"Challenge:        0x{challenge:016x}")
print(f"User ID:          0x{user_id:016x}")
print(f"Authenticator ID: 0x{authenticator_id:016x}")
print(f"Auth Type:        0x{auth_type:08x}")
print(f"Timestamp:        {timestamp}")
print(f"HMAC (32 bytes):  {hmac.hex()}")
