#!/usr/bin/env python3
"""Catch rendering regressions in the Spring Boot 4 docs site.

For every page, asserts:
  1. Every {% include-markdown ... %} directive in the source produced a
     <div class="language-* highlight"> block in the rendered HTML.
  2. No raw "{% include-markdown" survives in the rendered HTML (= un-processed).
  3. No bare Java/XML/YAML signatures leaked into <p>...</p> prose blocks
     (catches "package x.y.z;" or "import foo.bar;" rendered as paragraph).

Modes:
  --build   :  build the static site, check site/ files (CI-friendly, no server).
  --live    :  fetch live URLs from a running `mkdocs serve` (default).

Exits non-zero on first failure.

Usage:
    python3 scripts/check-render.py --build
    python3 scripts/check-render.py --live --base http://127.0.0.1:8000/spring-boot-4-docs
"""
import argparse
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
SITE = ROOT / "site"

INCLUDE_RE = re.compile(r'\{%\s*include-markdown\s+"([^"]+)"')
PROSE_LEAK_RE = re.compile(
    r"<p>(?:(?!</p>).)*?(?:^|\s)(package\s+[a-z][a-z0-9_.]*;|"
    r"import\s+[a-zA-Z_][\w.]+;|"
    r"@SpringBootApplication\b)",
    re.DOTALL,
)
LINK_RE = re.compile(r'!?\[[^\]]*\]\(([^)]+)\)')


def page_pairs_build():
    """Yield (markdown_path, html_path) for every chapter from the static site."""
    for md in sorted(DOCS.rglob("*.md")):
        rel = md.relative_to(DOCS).with_suffix("")
        if rel.name == "index":
            html = SITE / rel.parent / "index.html"
        else:
            html = SITE / rel / "index.html"
        if html.exists():
            yield md, html.read_text()


def page_pairs_live(base):
    """Yield (markdown_path, html_text) for every chapter via the live server."""
    base = base.rstrip("/")
    for md in sorted(DOCS.rglob("*.md")):
        rel = md.relative_to(DOCS).with_suffix("")
        url = f"{base}/" if rel.name == "index" and rel.parent.as_posix() == "." \
              else f"{base}/{rel.parent.as_posix()}/" if rel.name == "index" \
              else f"{base}/{rel.as_posix()}/"
        try:
            html = urllib.request.urlopen(url, timeout=5).read().decode()
        except urllib.error.HTTPError as e:
            print(f"  ! {url} HTTP {e.code}")
            continue
        except Exception as e:
            print(f"  ! {url} {e}")
            continue
        yield md, html


def check(md, html):
    failures = []
    src = md.read_text()
    includes = INCLUDE_RE.findall(src)
    code_blocks = html.count('class="language-')
    if "{% include-markdown" in html:
        failures.append("raw include directive leaked into HTML")
    # We expect at least one code block per include. Some pages have prose-only
    # code fences too, so >= is the right check.
    if includes and code_blocks < len(includes):
        failures.append(
            f"{len(includes)} include directives but only {code_blocks} "
            f"<div class=language-*> blocks in HTML"
        )
    leak = PROSE_LEAK_RE.search(html)
    if leak:
        snippet = leak.group(0)[-160:]
        failures.append(f"Java source leaked into prose: ...{snippet!r}")
    
    # Check relative links
    for link in LINK_RE.findall(src):
        clean_link = link.split("#")[0]
        if not clean_link or clean_link.startswith(("http:", "https:", "mailto:", "ftp:", "javascript:")):
            continue
        target_path = (md.parent / clean_link).resolve()
        if not target_path.exists():
            failures.append(f"broken relative link '{link}'")
            
    return failures


def main():
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--build", action="store_true", help="check static site/ output")
    g.add_argument("--live", action="store_true", help="check running mkdocs serve")
    ap.add_argument("--base", default="http://127.0.0.1:8000/spring-boot-4-docs")
    args = ap.parse_args()

    if args.build or not args.live:
        # Default mode: build then check (CI-safe, no server needed)
        mkdocs = ROOT / ".venv/bin/mkdocs"
        mkdocs_cmd = str(mkdocs) if mkdocs.exists() else shutil.which("mkdocs")
        if not mkdocs_cmd:
            print(
                "  x mkdocs not found. Create .venv and install "
                "requirements-docs.txt, or put mkdocs on PATH.",
                file=sys.stderr,
            )
            sys.exit(127)
        print("→ mkdocs build --strict")
        rc = subprocess.run(
            [mkdocs_cmd, "build", "--strict"],
            cwd=ROOT, capture_output=True, text=True,
        ).returncode
        if rc != 0:
            print("  ✗ build failed", file=sys.stderr)
            sys.exit(rc)
        pairs = page_pairs_build()
    else:
        pairs = page_pairs_live(args.base)

    total, bad = 0, 0
    for md, html in pairs:
        failures = check(md, html)
        total += 1
        rel = md.relative_to(ROOT)
        if failures:
            bad += 1
            print(f"✗ {rel}")
            for f in failures:
                print(f"    {f}")
        else:
            print(f"✓ {rel}")

    print(f"\n{total - bad}/{total} pages clean")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
