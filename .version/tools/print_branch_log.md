# print_branch_log.md

Change log for `tools/print_branch.py`. Maintained per
`/workspace/.skills/python-coding-standards/SKILL.md` (R2).

## 2026-06-06 16:38 — initial creation

- file: tools/print_branch.py
- version: (none) → v0.1.0 (Y: initial intra-module feature add)
- change: new utility that runs `git rev-parse --abbrev-ref HEAD`
  via `subprocess.run` and prints the current branch name. Uses a
  `main()` entry point plus the `if __name__ == "__main__":` guard,
  per R3 (PEP 8 + Google Python Style Guide). No "变更过程" comment
  added in the `.py` header (R2.1 anti-pattern avoided). Log file
  path mirrors the source under `.version/tools/` per R2.3.
