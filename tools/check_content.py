#!/usr/bin/env python3
"""Gate M2 content: every content/*.matou must parse identically with the
sibling SPI parsers (py reference + java port), and the proof shape holds
(owned: 1 block + 1 item + 1 mob + 1 weakspot + 1 feature;
additive: 1 late feature;
structure: 2 blocks + 1 leaf structure + 1 composite;
structure_cross: 1 block + 1 cross-file cycle half;
structure_badparts: 1 block + cycle + cross-file leaf + cycle half).
Structural compare, key order free. No Minecraft imports.
"""
import glob
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENT = os.path.join(ROOT, "content")
SPI = os.path.join(ROOT, "..", "spi")
PY_PARSER = os.path.join(SPI, "parser", "matou_parse.py")
JAVA_BUILD = os.path.join(ROOT, "java", "build")
JAVA_MAIN = "fr.iamacat.spi.MatouParse"

WANT_OWNED = [("Block", 1), ("Item", 1), ("Mob", 1), ("Weakspot", 1),
        ("Feature", 1)]
WANT_ADDITIVE = [("Feature", 1)]
WANT_STRUCTURE = [("Block", 2), ("Structure", 2)]
WANT_BADPARTS = [("Block", 1), ("Structure", 3)]
WANT_CROSS = [("Block", 1), ("Structure", 1)]
WANT_VEIN = [("Vein", 1)]


def run(cmd, path):
    r = subprocess.run(cmd + [path], capture_output=True, text=True)
    return r.returncode, r.stdout.strip()


def main():
    paths = sorted(glob.glob(os.path.join(CONTENT, "*.matou")))
    if not paths:
        print("FAIL content : no content/*.matou")
        return 1
    fails = 0
    for path in paths:
        name = os.path.basename(path)
        py_code, py_out = run([sys.executable, PY_PARSER], path)
        jv_code, jv_out = run(["java", "-cp", JAVA_BUILD, JAVA_MAIN], path)
        if py_code != 0 or jv_code != 0:
            print("FAIL content : %s unparsable (py=%d java=%d %s)" % (
                name, py_code, jv_code, (py_out or jv_out).split(":")[:2]))
            fails += 1
            continue
        try:
            py_tree, jv_tree = json.loads(py_out), json.loads(jv_out)
        except ValueError as e:
            print("FAIL content : %s bad json (%s)" % (name, e))
            fails += 1
            continue
        if py_tree != jv_tree:
            print("FAIL content : %s py/java trees differ" % name)
            fails += 1
            continue
        decls = [i["decl"] for i in py_tree["instances"]]
        want = {"additive.matou": WANT_ADDITIVE,
                "structure.matou": WANT_STRUCTURE,
                "structure_cross.matou": WANT_CROSS,
                "structure_badparts.matou": WANT_BADPARTS,
                "vein.matou": WANT_VEIN}.get(
                        name, WANT_OWNED)
        bad_shape = sorted(decls) != sorted(
            [d for d, n in want for _ in range(n)])
        if bad_shape:
            print("FAIL content : %s shape %s" % (name, decls))
            fails += 1
            continue
        print("ok content : %s (py+java, %s)" % (name, decls))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
