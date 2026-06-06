#!/usr/bin/env bash
# sync.sh — 把本地改动推上去的标准工作流
# 用法: ./scripts/sync.sh "你的 commit 信息"
# 流程:
#   1. fetch 远端
#   2. 切 minimax，把 origin/main 合并进来（保持 minimax 不落后）
#   3. add + commit + push 到 origin/minimax
#   4. 切 main，把 minimax 合进来（fast-forward 优先）
#   5. push main

set -euo pipefail

MSG="${1:-}"
REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [ "$CURRENT_BRANCH" != "minimax" ] && [ "$CURRENT_BRANCH" != "main" ]; then
  echo "✗ 当前在 '$CURRENT_BRANCH'，请先切到 minimax 或 main 再跑本脚本"
  exit 1
fi

echo "==> 1. fetch 远端"
git fetch origin

echo "==> 2. 切到 minimax，从 origin/main 同步最新"
if [ "$CURRENT_BRANCH" != "minimax" ]; then
  git checkout minimax
fi
# 同步 main 的最新提交到 minimax（无冲突时直接 fast-forward）
if ! git merge origin/main --ff-only; then
  echo "✗ minimax 跟 origin/main 有分叉，请手动处理冲突后再跑"
  exit 1
fi

echo "==> 3. add 所有改动"
git add -A
if git diff --cached --quiet; then
  echo "==> 没有可提交的改动，跳过 commit/push minimax"
else
  if [ -z "$MSG" ]; then
    echo "✗ 检测到改动但没传 commit 信息。用法: $0 \"你的 commit 信息\""
    git restore --staged .
    exit 1
  fi
  echo "==> 4. commit: $MSG"
  git commit -m "$MSG"
  echo "==> 5. push minimax"
  git push origin minimax
fi

echo "==> 6. 切 main，把 minimax 合进来"
git checkout main
if ! git merge minimax --ff-only; then
  echo "✗ main 跟 minimax 有分叉，请手动处理"
  exit 1
fi

echo "==> 7. push main"
git push origin main

echo ""
echo "==> ✅ 全部完成。当前 commit graph："
git log --oneline --all --graph -8
