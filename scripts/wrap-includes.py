#!/usr/bin/env python3
"""Wrap bare {% include-markdown "..." %} directives in fenced code blocks.

The include-markdown plugin pastes file contents inline, so without a
fenced ``` block, the content renders as a paragraph instead of a code box.
"""
import re
import sys
from pathlib import Path

EXT_LANG = {
    ".java": "java",
    ".kt": "kotlin",
    ".xml": "xml",
    ".yml": "yaml",
    ".yaml": "yaml",
    ".json": "json",
    ".properties": "properties",
    ".sh": "bash",
    ".py": "python",
    ".gradle": "groovy",
    ".kts": "kotlin",
}

INCLUDE_RE = re.compile(r'^(\s*)\{%\s*include-markdown\s+"([^"]+)"[^%]*%\}\s*$')

def lang_for(path: str) -> str:
    for ext, lang in EXT_LANG.items():
        if path.endswith(ext):
            return lang
    return "text"

def wrap(md_path: Path) -> int:
    lines = md_path.read_text().splitlines(keepends=False)
    out, i, changed = [], 0, 0
    while i < len(lines):
        line = lines[i]
        m = INCLUDE_RE.match(line)
        if not m:
            out.append(line)
            i += 1
            continue
        prev_nonblank = next((l for l in reversed(out) if l.strip()), "")
        next_line = lines[i+1] if i+1 < len(lines) else ""
        # Already wrapped?
        if prev_nonblank.startswith("```") and next_line.strip().startswith("```"):
            out.append(line)
            i += 1
            continue
        indent = m.group(1)
        path = m.group(2)
        lang = lang_for(path)
        out.append(f"{indent}```{lang}")
        out.append(line)
        out.append(f"{indent}```")
        i += 1
        changed += 1
    if changed:
        md_path.write_text("\n".join(out) + "\n")
    return changed

if __name__ == "__main__":
    targets = [Path(p) for p in sys.argv[1:]] or list(Path("docs").rglob("*.md"))
    total = 0
    for p in targets:
        n = wrap(p)
        if n:
            print(f"  {p}: wrapped {n} include(s)")
        total += n
    print(f"done. {total} include(s) wrapped.")
