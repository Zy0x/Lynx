import sys
import xml.etree.ElementTree as ET

xml_file = sys.argv[1] if len(sys.argv) > 1 else 'tab0_dump.xml'
tree = ET.parse(xml_file)
root = tree.getroot()

for node in root.iter():
    text = node.attrib.get('text', '')
    desc = node.attrib.get('content-desc', '')
    bounds = node.attrib.get('bounds', '')
    if text or desc:
        print(f"[{bounds}] text='{text}' desc='{desc}'")
