"""Validate observed client draw callbacks, scenarios and the exact tested artifact."""

import argparse
import hashlib
import json
import re
from pathlib import Path


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--jar", type=Path)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding="utf-8"))
    require(report["passed"] is True, report.get("failure", "client smoke failed"))
    scenarios = report["scenarios"]
    expected = {
        "water", "molten.steel", "molten.bisphenol_a",
        "molten.polybenzimidazole", "1,3dimethylbenzene",
    }
    require(len(scenarios) == 40 and {x["fluid"] for x in scenarios} == expected,
            "incomplete scenario inventory")
    require(all(x["width"] > 0 and x["height"] > 0 for x in scenarios),
            "empty tooltip dimensions")
    for fluid in expected:
        for text_only in (False, True):
            subset = [x for x in scenarios
                      if x["fluid"] == fluid and x["textOnly"] is text_only]
            require([x["modifier"] for x in subset] == ["NONE", "SHIFT", "NONE", "SHIFT"],
                    "missing press/release sequence")
            require([x["graph"] for x in subset] == [False, True, False, True],
                    "incorrect hint/graph switching")
    adapter = report["adapter"]
    require("chromaticNative=true" in adapter and "error=none" in adapter,
            "native adapter failure")
    draws = re.search(r"\bdraws=(\d+)", adapter)
    require(draws and int(draws[1]) >= sum(x["graph"] for x in scenarios) > 0,
            "graphics were measured but never drawn")
    if args.jar:
        require(hashlib.sha256(args.jar.read_bytes()).hexdigest() == report["gameJarSha256"],
                "tested JAR differs from supplied JAR")
    print("Client smoke evidence verified: 40 scenarios, nonempty layouts, "
          "native draw callbacks and artifact hash")


if __name__ == "__main__":
    main()
