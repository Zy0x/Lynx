import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

lines = open(r'C:\Users\aliri\.gemini\antigravity\brain\69edb5f3-9464-4782-a977-a06158d87812\.system_generated\logs\transcript.jsonl', encoding='utf-8').readlines()
for i in range(39230, 39250):
    l = json.loads(lines[i])
    if 'tool_calls' in l:
        for tc in l['tool_calls']:
            print(i, tc.get('name'), tc.get('CommandLine') or tc.get('Path') or tc.get('Summary'))
    if l.get('type') == 'PLANNER_RESPONSE' and l.get('content'):
        print(i, "PLANNER:", l['content'][:200])
