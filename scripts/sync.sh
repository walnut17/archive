#!/usr/bin/env bash
# sync.sh — 单向同步：minimax 永远从 main 拉新
# 用法: ./scripts/sync.sh "你的 commit 信息"
# 流程:
#   1. fetch 远端
#   2. 切 minimax
#   3. 把 origin/main 合并进 minimax（保持不落后）
#   4. add + commit（如果有改动）+ push minimax
# 注意：本脚本**不碰 main 分支**

set -euo pipefail

MSG="${1:-}"
REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [ "$CURRENT_BRANCH" != "minimax" ] && [ "$CURRENT_BRANCH" != "main" ]; then
  echo "✗ 当前在 '$CURRENT_BRANCH'，请先切到 minimax 再跑本脚本"
  exit 1
fi

echo "==> 1. fetch 远端"
git fetch origin

echo "==> 2. 切到 minimax"
git checkout minimax

echo "==> 3. 把 origin/main 合并进 minimax"
if ! git merge origin/main --ff-only; then
  echo "✗ minimax 跟 origin/main 有分叉且无法 fast-forward，请手动处理"
  exit 1
fi

echo "==> 4. add 所有改动"
git add -A
if git diff --cached --quiet; then
  echo "==> 没有可提交的改动，跳过 commit/push"
else
  if [ -z "$MSG" ]; then
    echo "✗ 检测到改动但没传 commit 信息。用法: $0 \"你的 commit 信息\""
    git restore --staged .
    exit 1
  fi
  echo "==> 5. commit: $MSG"
  git commit -m "$MSG"
  echo "==> 6. push origin/minimax"
  git push origin minimax
fi

echo ""
echo "==> ✅ 同步完成。本脚本未触碰 main 分支。"
echo ""
echo "==> 当前状态："
git status
git log --oneline -5
