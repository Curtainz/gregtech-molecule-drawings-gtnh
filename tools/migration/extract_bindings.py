"""Audit the small, fixed legacy Java tables; not a Java interpreter or runtime loader."""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "src/main/java/com/rubenverg/moldraw"

def quoted(text):
    return re.findall(r'"([^"\\]*)"', text)

def extract():
    paths = ["MoleculeManager.java", "AlloyManager.java", "util/FormulaParser.java", "molecule/Element.java"]
    source = {p: (BASE / p).read_text(encoding="utf-8") for p in paths}
    molecule, alloy, formula, element_source = (source[p] for p in paths)
    def table(text, name):
        match = re.search(r'\b' + name + r'\s*=\s*new String\[\]\s*\{(.*?)\}', text, re.S)
        if not match:
            raise ValueError("Legacy table moved: " + name)
        body = re.sub(r'"[^"\\]*"|[\s,]', '', match[1])
        if body:
            raise ValueError("Nonliteral Java table: " + name)
        return quoted(match[1])
    aliases = dict(re.findall(r'ALIASES\.put\("([^"\\]+)", "([^"\\]+)"\)', molecule))
    calls = re.findall(r'register(?:BaseElement|ElementAlias)\("([^"\\]+)", "([^"\\]+)"\)', formula)
    elements = dict(calls)
    if len(aliases) != molecule.count('ALIASES.put(') or len(elements) != len(calls):
        raise ValueError("Unexpected alias or element mapping shape")
    for symbol, material in re.findall(r'createStandard\(\s*"([^"\\]+)",\s*(?:0x[0-9a-fA-F]+|null),\s*getGT5Material\("([^"\\]+)"\)', element_source):
        elements.setdefault(symbol, material)
    result = {
        "sourceHashes": {p: hashlib.sha256((BASE / p).read_bytes()).hexdigest() for p in paths},
        "namespaces": table(molecule, "NAMESPACES"),
        "aliases": aliases,
        "prefixes": table(molecule, "PREFIXES_TO_STRIP"),
        "candidates": table(alloy, "defaultAlloys"),
        "containsAny": re.findall(r'name\.contains\("([^"\\]+)"\)', alloy),
        "elements": elements,
    }
    assert len(result["namespaces"]) == 7 and len(elements) > 80 and len(aliases) > 50
    return result

if __name__ == "__main__":
    import sys
    text = json.dumps(extract(), ensure_ascii=False, indent=2) + "\n"
    destination = Path(__file__).with_name("legacy-bindings.json")
    if "--check" in sys.argv:
        assert destination.read_text(encoding="utf-8") == text, "Legacy bindings snapshot drift"
        print("Legacy Java binding snapshot matches source tables")
    else:
        destination.write_text(text, encoding="utf-8")
