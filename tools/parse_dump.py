import xml.etree.ElementTree as ET
import sys

sys.stdout.reconfigure(encoding='utf-8')
fname = sys.argv[1] if len(sys.argv) > 1 else 'tools/dump.xml'
tree = ET.parse(fname)
for node in tree.iter():
    text = node.attrib.get('text', '')
    res_id = node.attrib.get('resource-id', '')
    bounds = node.attrib.get('bounds', '')
    clickable = node.attrib.get('clickable', '')
    if text or 'btn' in res_id.lower() or clickable == 'true':
        print(f"text: '{text}' | id: '{res_id}' | clickable: {clickable} | bounds: {bounds}")
