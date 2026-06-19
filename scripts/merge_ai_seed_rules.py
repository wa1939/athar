"""
Merge AI-generated categorization rules from
`AI template and output Categorization/athar_seed_expansion_*_rules_templates.json`
into `core/data/src/main/assets/seed_rules.json`.

Strategy:
- Curated rules (existing in seed_rules.json) keep priority 100
- AI rules with confidence >= 0.85   → priority 80
- AI rules with confidence >= 0.70   → priority 60
- AI rules below 0.70 are dropped (too noisy)
- Null/missing category → dropped
- Lowercase + trim merchant pattern; collapse near-duplicates (e.g. "Whop", "whop")
- Existing curated pattern wins over AI pattern (curated patterns are preserved verbatim)
"""
from __future__ import annotations
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
AI_DIR = ROOT / "AI template and output Categorization"
SEED = ROOT / "core" / "data" / "src" / "main" / "assets" / "seed_rules.json"
AI_FILES = [
    AI_DIR / "athar_seed_expansion_first_user_rules_templates.json",
    AI_DIR / "athar_seed_expansion_user2_rules_templates.json",
]


def confidence_to_priority(c: float) -> int | None:
    if c >= 0.85:
        return 80
    if c >= 0.70:
        return 60
    return None


def main():
    with SEED.open(encoding="utf-8") as f:
        curated = json.load(f)
    existing = curated["rules"]
    keep_keys = set()
    cleaned_curated = []
    for r in existing:
        key = r["pattern"].strip().lower()
        if key in keep_keys:
            continue
        keep_keys.add(key)
        cleaned = {
            "pattern": r["pattern"].strip(),
            "categoryId": r["categoryId"],
            "priority": r.get("priority", 100),
        }
        if "patternType" in r:
            cleaned["patternType"] = r["patternType"]
        cleaned_curated.append(cleaned)
    print(f"curated rules (deduped): {len(cleaned_curated)}")

    ai_added: list[dict] = []
    ai_dropped = 0
    seen_ai = set()
    for fp in AI_FILES:
        with fp.open(encoding="utf-8") as f:
            d = json.load(f)
        for r in d.get("categorization_rules", []):
            cat = r.get("category")
            pat = (r.get("pattern") or "").strip()
            conf = r.get("confidence", 0.0)
            if not pat or not cat:
                ai_dropped += 1
                continue
            key = pat.lower()
            if key in keep_keys or key in seen_ai:
                ai_dropped += 1
                continue
            prio = confidence_to_priority(conf)
            if prio is None:
                ai_dropped += 1
                continue
            seen_ai.add(key)
            ai_added.append({
                "pattern": pat,
                "categoryId": cat,
                "priority": prio,
            })
    print(f"ai added: {len(ai_added)}, dropped/skipped: {ai_dropped}")

    merged = cleaned_curated + sorted(
        ai_added, key=lambda x: (-x["priority"], x["pattern"])
    )
    print(f"merged total: {len(merged)}")

    cat_counts: dict[str, int] = {}
    for r in merged:
        cat_counts[r["categoryId"]] = cat_counts.get(r["categoryId"], 0) + 1
    print("\nrules per category:")
    for cat, c in sorted(cat_counts.items(), key=lambda x: -x[1]):
        print(f"  {cat:30s} {c:4d}")

    out = {"rules": merged}
    SEED.write_text(
        json.dumps(out, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"\nwrote {SEED}")


if __name__ == "__main__":
    main()
