#!/usr/bin/env python3
"""Converts the author's JSON question bank into Reps' built-in bank assets.

Usage:
  python tools/convert_builtin_bank.py \
    --json "C:/Users/shang/Desktop/temp/reps_builtin_questions.json" \
    --images "C:/Users/shang/Desktop/temp/question_images" \
    --out app/src/main/assets/builtin_bank

The author's correction log (2026-08-30, verified against the original PDF)
is applied as a patch table below because the JSON on disk predates it:
  - cg_m_9  answer A  -> ABC  (录入串行)
  - cg_m_11 answer C  -> AC   (录入串行)
  - cg_m_15 answer C  -> CD   (录入串行)
  - qg_m_11 type multiple -> single (PDF 排版错误, 只有 B 正确)
  - tz_m_10 type multiple -> single (PDF 排版错误, 只有 D 正确)
Expected final stats: single 191 / multi 73 / judge 126 = 390.
"""
import argparse
import csv
import json
import re
import shutil
import sys
from collections import Counter
from pathlib import Path

PATCH = {
    "cg_m_9": {"answer": "ABC"},
    "cg_m_11": {"answer": "AC"},
    "cg_m_15": {"answer": "CD"},
    "qg_m_11": {"type": "single"},
    "tz_m_10": {"type": "single"},
}

TYPE_MAP = {"single": "single", "judgment": "judge", "multiple": "multi"}
COLUMNS = [
    "id", "content", "type",
    "option_a", "option_b", "option_c", "option_d", "option_e", "option_f",
    "correct_answer", "explanation", "category", "chapter", "image",
]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", required=True)
    ap.add_argument("--images", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    with open(args.json, encoding="utf-8") as f:
        data = json.load(f)
    questions = {q["id"]: q for q in data["questions"]}

    applied = []
    for qid, patch in PATCH.items():
        q = questions[qid]
        before = (q["type"], q["answer"])
        for key, value in patch.items():
            q[key] = value
        applied.append(f"  {qid}: {before} -> ({q['type']}, {q['answer']})")
    print("已应用作者修正补丁:")
    print("\n".join(applied))

    rows = []
    image_refs = {}
    for index, q in enumerate(data["questions"], start=1):
        q = questions[q["id"]]  # patched view, preserves JSON order
        qtype = TYPE_MAP[q["type"]]
        options = q.get("options") or {}

        answer = q["answer"].strip().upper()
        if qtype == "judge":
            text = options.get(answer, "")
            mapping = {"正确": "对", "错误": "错", "对": "对", "错": "错"}
            if text not in mapping:
                sys.exit(f"判断题 {q['id']} 答案无法映射: {answer} -> {text}")
            answer = mapping[text]
        elif qtype == "multi":
            letters = sorted(set(re.findall(r"[A-F]", answer)))
            if len(letters) < 2:
                sys.exit(f"多选题 {q['id']} 答案少于 2 个选项: {answer}")
            answer = "".join(letters)
        else:
            if answer not in options:
                sys.exit(f"单选题 {q['id']} 答案 {answer} 不在选项中")

        image = (q.get("image") or "").strip()
        if image:
            image_refs[image] = q["id"]

        rows.append({
            "id": index,
            "content": q["content"].strip(),
            "type": qtype,
            **{f"option_{c.lower()}": options.get(c, "") for c in "ABCDEF"},
            "correct_answer": answer,
            "explanation": q.get("explanation") or "",
            "category": "",
            "chapter": q.get("chapter") or "",
            "image": image,
        })

    # Assertions against the author's verified final stats.
    counts = Counter(r["type"] for r in rows)
    assert len(rows) == 390, f"题数异常: {len(rows)}"
    assert counts == {"single": 191, "multi": 73, "judge": 126}, f"题型统计异常: {counts}"
    for r in rows:
        if r["type"] == "multi":
            assert len(r["correct_answer"]) >= 2, f"多选异常: id={r['id']} {r['correct_answer']}"
    print(f"断言通过: 390 题 = 单选 {counts['single']} / 多选 {counts['multi']} / 判断 {counts['judge']}")

    out = Path(args.out)
    (out / "images").mkdir(parents=True, exist_ok=True)
    with open(out / "questions.csv", "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS, quoting=csv.QUOTE_MINIMAL)
        writer.writeheader()
        writer.writerows(rows)

    image_dir = Path(args.images)
    files = {p.stem.lower(): p for p in image_dir.iterdir() if p.suffix.lower() in (".jpeg", ".jpg", ".png")}
    missing = [name for name in image_refs if name.lower() not in files]
    if missing:
        sys.exit(f"引用的图片缺失: {missing}")
    copied = 0
    for stem, path in files.items():
        shutil.copyfile(path, out / "images" / path.name)
        copied += 1
    unused = sorted(set(files) - {k.lower() for k in image_refs})
    print(f"题图: 引用 {len(image_refs)} 张, 复制 {copied} 张" + (f", 未被引用: {unused}" if unused else ""))

    total_size = sum(p.stat().st_size for p in out.rglob("*") if p.is_file())
    print(f"输出: {out} (共 {total_size / 1024:.0f} KB)")


if __name__ == "__main__":
    main()
