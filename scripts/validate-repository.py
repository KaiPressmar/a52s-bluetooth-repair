#!/usr/bin/env python3
"""Check compatibility evidence and local links in the implementation documents."""
import json
import re
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
STATUSES = {"UNTESTED", "API_COMPATIBLE", "REPORTED_WORKING", "FAULT_RECOVERY_OBSERVED", "REPEATEDLY_VALIDATED", "KNOWN_LIMITATION"}

def validate(data):
    assert data.get("schema_version") == 1, "unsupported schema"
    assert isinstance(data.get("records"), list), "missing records"
    for row in data["records"]:
        for field in ("model", "firmware", "android", "accessory", "app_version", "enrollment", "evidence"):
            assert isinstance(row.get(field), str) and row[field], "missing " + field
        assert row.get("status") in STATUSES, "invalid status"
        assert (ROOT / row["evidence"]).is_file(), "missing evidence"
        for field in ("observation_days", "eligible_calls", "faults", "automatic_recoveries", "manual_corrections"):
            value = row.get(field)
            assert value is None or (type(value) is int and value >= 0), "invalid " + field
        if row["status"] in {"FAULT_RECOVERY_OBSERVED", "REPEATEDLY_VALIDATED"}:
            assert row["faults"] and row["eligible_calls"], "recovery requires actual faulty calls"
            assert row["automatic_recoveries"] is not None, "missing recovery count"
            assert row["manual_corrections"] is not None, "missing manual count"
            assert row["automatic_recoveries"] <= row["faults"] <= row["eligible_calls"], "invalid counts"
            assert row["automatic_recoveries"] + row["manual_corrections"] <= row["faults"], "manual corrections are not automatic recoveries"
        assert not re.search(r"(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}", json.dumps(row)), "private address in evidence"

def main():
    validate(json.loads((ROOT / "docs/compatibility/results.json").read_text()))
    for name in ("RESEARCH_2026-10-15.md", "REFACTORING_PLAN.md", "IMPLEMENTATION_PLAN.md", "COMMUNITY_PLAN.md", "COMPATIBILITY.md"):
        path = ROOT / "docs" / name
        content = path.read_text()
        assert content.count("```") % 2 == 0, "unbalanced code fences"
        for target in re.findall(r"\[[^\]]*\]\(([^)]+)\)", content):
            if "://" not in target and not target.startswith("#"):
                assert (path.parent / target.split("#")[0]).exists(), "missing local link: " + target
    print("Repository documentation and compatibility evidence are valid")

if __name__ == "__main__":
    main()
