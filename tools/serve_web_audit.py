#!/usr/bin/env python3
"""Local-only WebUI test server backed by captured read-only native responses."""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit, parse_qs
import json

ROOT = Path(__file__).resolve().parents[1]
CAPTURE = ROOT / "tools/video_memory_audit_20261015/release_validation"
ENGINE = "/data/adb/modules/Lynx/system/bin/lynxd "

class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT / "webroot"), **kwargs)

    def log_message(self, *args):
        pass

    def reply(self, payload):
        raw = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        parsed = urlsplit(self.path)
        if parsed.path == "/config.json":
            raw = (CAPTURE / "preinstall.txt").read_text(encoding="utf-8")
            return self.reply(json.loads(raw[raw.index("{\n"):raw.rindex("}")+1]))
        if parsed.path == "/api/exec":
            command = parse_qs(parsed.query).get("cmd", [""])[0]
            name = command[len(ENGINE):] if command.startswith(ENGINE) else ""
            if command.startswith("cat /dev/lynxd_telemetry.json "):
                name = "telemetry"
            if command.startswith("cat /dev/lynxd_status.json "):
                name = "status --json"
            allowed = {item["command"] for item in json.loads((CAPTURE / "native_queries.json").read_text())}
            if name in allowed:
                filename = name.replace(" ", "_").replace("--", "") + ".txt"
                raw = (CAPTURE / filename).read_text(encoding="utf-8")
                stdout, code = raw.rsplit("__EXIT=", 1)
                return self.reply({"errno": int(code.strip()), "stdout": stdout.strip(), "stderr": ""})
            return self.reply({"errno": 2, "stdout": "", "stderr": "audit_fixture_read_only"})
        super().do_GET()

if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 18766), Handler).serve_forever()
