"""qa-agent TUI 测试工具 — 命令行直连 /v1/ask/stream.

启动: python -m qa-agent.tools.tui_repl (在 qa-agent/ 目录)
       或:  python qa-agent/tools/tui_repl.py (在仓库根)

特性:
- 0 依赖 (Python stdlib: cmd + urllib + json)
- 支持流式 SSE (逐字渲染答案)
- 多轮会话 (--session-id 或自动生成)
- 快捷命令: /help /exit /clear /session /raw /quiet /config /tools

环境变量:
- QA_AGENT_URL  默认 http://127.0.0.1:8001
"""
import argparse
import cmd
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from typing import Any

# ANSI 颜色
class C:
    RESET = "\033[0m"
    BOLD = "\033[1m"
    DIM = "\033[2m"
    RED = "\033[31m"
    GREEN = "\033[32m"
    YELLOW = "\033[33m"
    BLUE = "\033[34m"
    MAGENTA = "\033[35m"
    CYAN = "\033[36m"

    @classmethod
    def disable(cls):
        for attr in dir(cls):
            if attr.isupper() and not attr.startswith("_"):
                setattr(cls, attr, "")


def colorize(text: str, color: str) -> str:
    return f"{color}{text}{C.RESET}"


def strip_ansi(text: str) -> str:
    import re

    return re.sub(r"\033\[[0-9;]*m", "", text)


def print_answer_box(write, answer: str, *, no_color: bool = False) -> None:
    """醒目打印最终答案（与 ReAct 步骤分区）."""
    text = (answer or "").strip()
    if not text:
        write(colorize("（无最终答案）\n", C.YELLOW))
        return
    bar = "═" * 56
    if no_color:
        write(f"\n{bar}\n  最终答案\n{bar}\n")
        write(text + f"\n{bar}\n")
        return
    write(colorize(f"\n{bar}\n", C.GREEN))
    write(colorize("  最终答案\n", C.BOLD + C.GREEN))
    write(colorize(f"{bar}\n", C.GREEN))
    for line in text.splitlines() or [text]:
        write(colorize(f"  {line}\n", C.BOLD + C.GREEN))
    write(colorize(f"{bar}\n", C.GREEN))


class QaAgentClient:
    """轻量 HTTP 客户端 — 调 qa-agent /v1/ask/stream + /v1/turn/{sid}/stream."""

    def __init__(self, base_url: str, timeout: float = 60.0):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def health(self) -> dict:
        url = f"{self.base_url}/health"
        with urllib.request.urlopen(url, timeout=5) as r:
            return json.loads(r.read())

    def ask_stream(
        self, question: str, session_id: str | None = None
    ) -> "Iterator[dict]":
        """流式调 /v1/ask/stream (或 /v1/turn/{sid}/stream).

        返回 iterator, 每项 1 个 SSE 事件 (dict: event, data).
        """
        if session_id:
            url = f"{self.base_url}/v1/turn/{session_id}/stream"
        else:
            url = f"{self.base_url}/v1/ask/stream"
        body = json.dumps({"question": question}).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=body,
            headers={"Content-Type": "application/json", "Accept": "text/event-stream"},
            method="POST",
        )
        try:
            resp = urllib.request.urlopen(req, timeout=self.timeout)
        except urllib.error.HTTPError as e:
            raise RuntimeError(f"HTTP {e.code}: {e.read().decode('utf-8', errors='replace')}")
        except urllib.error.URLError as e:
            raise RuntimeError(f"网络错误: {e.reason}")

        # 解析 SSE 流
        # 按 \n\n 切事件
        def gen():
            buffer = ""
            for raw_chunk in resp:
                try:
                    text = raw_chunk.decode("utf-8", errors="replace")
                except Exception:
                    continue
                buffer += text
                while "\n\n" in buffer:
                    ev, buffer = buffer.split("\n\n", 1)
                    if not ev.strip():
                        continue
                    event_name = None
                    data_line = None
                    for line in ev.split("\n"):
                        if line.startswith("event: "):
                            event_name = line[7:].strip()
                        elif line.startswith("data: "):
                            data_line = line[6:].strip()
                    if event_name and data_line:
                        try:
                            data = json.loads(data_line)
                        except json.JSONDecodeError:
                            continue
                        yield {"event": event_name, "data": data}

        return gen()


class TuiRepl(cmd.Cmd):
    """qa-agent 交互式 REPL."""

    intro = colorize(
        "\n╔══════════════════════════════════════════════════════════════╗\n"
        "║  qa-agent TUI 测试工具 (v1.2)                              ║\n"
        "║  命令: /help  查看  |  /exit  退出                          ║\n"
        "╚══════════════════════════════════════════════════════════════╝\n",
        C.CYAN
    )
    prompt = colorize("\n❯ ", C.GREEN)

    def __init__(self, base_url: str, session_id: str | None, no_color: bool = False, raw: bool = False, quiet: bool = False):
        super().__init__()
        if no_color:
            C.disable()
            # 重新 colorize 已经引用的字段
            self.intro = "\n[qa-agent TUI 测试工具 v1.2]  /help 查看命令  /exit 退出\n"
            self.prompt = "\n> "
        self.client = QaAgentClient(base_url)
        self.session_id = session_id or str(uuid.uuid4())
        self.raw = raw
        self.quiet = quiet
        self.base_url = base_url
        self.last_answer = ""
        self.last_steps: list[dict] = []
        self.last_sources: list[dict] = []
        self.no_color = no_color

    # ============== 核心命令: 直接输入问题 ==============

    def default(self, line: str):
        """默认: 把输入当问题发到 qa-agent."""
        line = line.strip()
        if not line:
            return
        if line.startswith("/"):
            self.stdout.write(colorize(f"未知命令: {line}. 输入 /help\n", C.RED))
            return
        self._ask(line)

    # ============== REPL 命令 ==============

    def do_help(self, arg):
        """显示帮助: /help [command]"""
        if arg:
            try:
                func = getattr(self, f"do_{arg}")
                self.stdout.write(colorize(f"\n/{arg} ", C.BOLD))
                self.stdout.write(func.__doc__ or "(无说明)\n")
            except AttributeError:
                self.stdout.write(colorize(f"未知命令: /{arg}\n", C.RED))
            return
        help_text = """
可用命令:
  直接输入问题         → 调 qa-agent, 流式渲染
  /help [cmd]          → 显示帮助
  /exit  /quit         → 退出
  /clear               → 清屏
  /session             → 显示当前 session_id
  /new                 → 开启新 session
  /config              → 显示当前配置
  /set <k> <v>         → 临时改配置 (url/quiet/raw/timeout)
  /raw                 → 切换 raw 模式 (输出原始 SSE 事件 JSON)
  /quiet               → 切换 quiet 模式 (只显示答案, 不显示步骤)
  /health              → 调 /health 端点
  /tools               → 列出可用工具 (静态, 来自 prompts)
  /last                → 显示上一次完整响应 (步骤+来源+答案)
  /ask <q>             → 显式提问 (同直接输入, 但显式触发)
  /time <q>            → 测延迟 (非流式, 直接 /v1/ask)
  /bench [N=5]         → 跑 N 次 "你好" 测 P50/P90 延迟

快捷键:
  Ctrl+C / Ctrl+D      → 中断/退出
  ↑ / ↓                → 命令历史 (需 readline, 多数系统已自带)
"""
        self.stdout.write(colorize(help_text, C.CYAN))

    def do_exit(self, arg):
        """退出 TUI."""
        self.stdout.write(colorize("\n再见 👋\n", C.CYAN))
        return True

    def do_quit(self, arg):
        """退出 TUI (alias)."""
        return self.do_exit(arg)

    def do_EOF(self, arg):
        """Ctrl+D 退出."""
        return self.do_exit(arg)

    def do_clear(self, arg):
        """清屏."""
        os.system("cls" if os.name == "nt" else "clear")
        self.stdout.write(self.intro)

    def do_session(self, arg):
        """显示当前 session_id."""
        self.stdout.write(colorize(f"session_id: {self.session_id}\n", C.CYAN))

    def do_new(self, arg):
        """开启新 session (旧 session 状态保留, 不影响)."""
        old = self.session_id
        self.session_id = str(uuid.uuid4())
        self.stdout.write(colorize(
            f"新 session: {self.session_id}\n旧 session: {old}\n", C.YELLOW
        ))

    def do_config(self, arg):
        """显示当前配置."""
        cfg = {
            "base_url": self.base_url,
            "session_id": self.session_id,
            "raw_mode": self.raw,
            "quiet_mode": self.quiet,
            "timeout": self.client.timeout,
        }
        self.stdout.write(colorize(json.dumps(cfg, indent=2, ensure_ascii=False) + "\n", C.CYAN))

    def do_set(self, arg):
        """临时改配置: /set <key> <value>. 支持: url timeout raw quiet"""
        parts = arg.split(maxsplit=1)
        if len(parts) != 2:
            self.stdout.write(colorize("用法: /set <key> <value>\n", C.RED))
            return
        key, val = parts
        if key == "url":
            self.base_url = val.rstrip("/")
            self.client = QaAgentClient(self.base_url, self.client.timeout)
            self.stdout.write(colorize(f"base_url → {self.base_url}\n", C.GREEN))
        elif key == "timeout":
            try:
                self.client.timeout = float(val)
                self.stdout.write(colorize(f"timeout → {self.client.timeout}\n", C.GREEN))
            except ValueError:
                self.stdout.write(colorize("timeout 必须是数字\n", C.RED))
        elif key == "raw":
            self.raw = val.lower() in ("1", "true", "on", "yes")
            self.stdout.write(colorize(f"raw → {self.raw}\n", C.GREEN))
        elif key == "quiet":
            self.quiet = val.lower() in ("1", "true", "on", "yes")
            self.stdout.write(colorize(f"quiet → {self.quiet}\n", C.GREEN))
        else:
            self.stdout.write(colorize(f"未知配置: {key}\n", C.RED))

    def do_raw(self, arg):
        """切换 raw 模式."""
        self.raw = not self.raw
        self.stdout.write(colorize(f"raw 模式: {self.raw}\n", C.YELLOW))

    def do_quiet(self, arg):
        """切换 quiet 模式."""
        self.quiet = not self.quiet
        self.stdout.write(colorize(f"quiet 模式: {self.quiet}\n", C.YELLOW))

    def do_health(self, arg):
        """调 /health 端点."""
        try:
            h = self.client.health()
            self.stdout.write(colorize(json.dumps(h, indent=2, ensure_ascii=False) + "\n", C.GREEN))
        except Exception as e:
            self.stdout.write(colorize(f"health 失败: {e}\n", C.RED))

    def do_tools(self, arg):
        """列出可用工具 (来自 prompts)."""
        tools_text = """
8 个 Agent 工具:
  1. find_project                锁定项目
  2. get_project_business_data   项目汇总
  3. search_fulltext             材料全文检索
  4. query_mysql                 查 6 张白名单表
  5. archive_fs                  项目材料本地文件
  6. network_dict_lookup         业务术语
  7. llm_summarize               摘要抽取
  8. ask_clarification           追问用户
"""
        self.stdout.write(colorize(tools_text, C.CYAN))

    def do_last(self, arg):
        """显示上一次完整响应."""
        if not self.last_answer:
            self.stdout.write(colorize("还没有响应记录\n", C.YELLOW))
            return
        self.stdout.write(colorize("\n── 上一次响应 ──\n", C.BOLD))
        print_answer_box(self.stdout.write, self.last_answer, no_color=self.no_color)
        if self.last_steps:
            self.stdout.write(colorize(f"步骤 ({len(self.last_steps)}):\n", C.CYAN))
            for s in self.last_steps:
                self.stdout.write(f"  步 {s.get('iteration', '?')}: {s.get('thought', '')}\n")
                self.stdout.write(f"    工具: {s.get('tool', '?')}\n")
                obs = s.get("observation", "")
                if obs:
                    obs_short = obs[:120] + "..." if len(obs) > 120 else obs
                    self.stdout.write(f"    观察: {obs_short}\n")
        if self.last_sources:
            self.stdout.write(colorize(f"\n来源 ({len(self.last_sources)}):\n", C.MAGENTA))
            for s in self.last_sources[:10]:
                self.stdout.write(f"  - [{s.get('type', '?')}] {s.get('title', s.get('id', '?'))}\n")

    def do_ask(self, arg):
        """显式提问 (同直接输入): /ask <question>"""
        if not arg.strip():
            self.stdout.write(colorize("用法: /ask <question>\n", C.RED))
            return
        self._ask(arg.strip())

    def do_time(self, arg):
        """非流式测延迟: /time <question>"""
        if not arg.strip():
            self.stdout.write(colorize("用法: /time <question>\n", C.RED))
            return
        url = f"{self.base_url}/v1/ask" if not self.session_id else f"{self.base_url}/v1/turn/{self.session_id}"
        body = json.dumps({"question": arg.strip()}).encode("utf-8")
        req = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json"}, method="POST")
        start = time.time()
        try:
            with urllib.request.urlopen(req, timeout=self.client.timeout) as r:
                resp = json.loads(r.read())
            elapsed = (time.time() - start) * 1000
            self.stdout.write(colorize(f"\n耗时: {elapsed:.0f}ms\n", C.DIM))
            print_answer_box(self.stdout.write, resp.get("answer", ""), no_color=self.no_color)
            steps = resp.get("steps") or []
            if steps:
                self.stdout.write(colorize(f"\n步骤 ({len(steps)}):\n", C.CYAN))
                for s in steps:
                    self.stdout.write(
                        f"  步 {s.get('iteration', '?')}: {s.get('tool', '?')}\n"
                    )
            self.last_answer = resp.get("answer", "") or ""
            self.last_steps = [
                {
                    "iteration": s.get("iteration", 0),
                    "thought": s.get("thought", ""),
                    "tool": s.get("tool", ""),
                    "toolArgs": s.get("toolArgs", ""),
                    "observation": s.get("observation", ""),
                }
                for s in steps
            ]
            if resp.get("degraded"):
                self.stdout.write(colorize("(降级模式)\n", C.YELLOW))
        except Exception as e:
            self.stdout.write(colorize(f"失败: {e}\n", C.RED))

    def do_bench(self, arg):
        """跑 N 次"你好"测 P50/P90 延迟: /bench [N=5]"""
        n = 5
        if arg.strip().isdigit():
            n = int(arg.strip())
        times: list[float] = []
        self.stdout.write(colorize(f"\n跑 {n} 次 benchmark...\n", C.CYAN))
        for i in range(n):
            url = f"{self.base_url}/v1/ask"
            body = json.dumps({"question": "你好"}).encode("utf-8")
            req = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json"}, method="POST")
            start = time.time()
            try:
                with urllib.request.urlopen(req, timeout=self.client.timeout) as r:
                    r.read()
                times.append((time.time() - start) * 1000)
                self.stdout.write(f"  [{i+1}/{n}] {times[-1]:.0f}ms\n")
            except Exception as e:
                self.stdout.write(colorize(f"  [{i+1}/{n}] 失败: {e}\n", C.RED))
        if times:
            times.sort()
            p50 = times[len(times) // 2]
            p90 = times[int(len(times) * 0.9)]
            self.stdout.write(colorize(f"\nP50: {p50:.0f}ms  P90: {p90:.0f}ms  (n={len(times)})\n", C.GREEN))

    # ============== 核心: 调 qa-agent 流式 ==============

    def _ask(self, question: str):
        if self.raw:
            self._ask_raw(question)
        else:
            self._ask_pretty(question)

    def _ask_raw(self, question: str):
        """raw 模式: 输出所有 SSE 事件 JSON."""
        self.stdout.write(colorize(f"\n→ 问: {question}\n", C.BOLD))
        try:
            for ev in self.client.ask_stream(question, self.session_id):
                self.stdout.write(json.dumps(ev, ensure_ascii=False) + "\n")
        except Exception as e:
            self.stdout.write(colorize(f"错误: {e}\n", C.RED))

    def _ask_pretty(self, question: str):
        """漂亮模式: 流式逐字渲染答案, 然后显示步骤 + 来源."""
        start = time.time()
        self.stdout.write(colorize(f"\n→ ", C.BOLD) + colorize(question, C.CYAN) + "\n")
        self.stdout.write(colorize("─" * 60 + "\n", C.DIM))

        acc_answer = ""
        steps: list[dict] = []
        sources: list[dict] = []
        badge: str | None = None
        degraded = False
        tool_calls = 0
        err_msg: str | None = None

        try:
            for ev in self.client.ask_stream(question, self.session_id):
                et = ev["event"]
                data = ev["data"]
                if et == "token":
                    acc_answer += data.get("delta", "")
                elif et == "step":
                    step = {
                        "iteration": data.get("iteration", 0),
                        "thought": data.get("thought", ""),
                        "tool": data.get("tool", ""),
                        "toolArgs": data.get("toolArgs", ""),
                        "observation": data.get("observation", ""),
                    }
                    steps.append(step)
                    tool_calls += 1
                elif et == "source":
                    sources.append(data)
                elif et == "done":
                    acc_answer = data.get("answer", acc_answer)
                    badge = data.get("confidence_badge")
                    degraded = data.get("degraded", False)
                elif et == "error":
                    err_msg = data.get("message", "未知错误")
        except KeyboardInterrupt:
            self.stdout.write(colorize("\n[用户中断]\n", C.YELLOW))
        except Exception as e:
            err_msg = str(e)

        elapsed = (time.time() - start) * 1000
        self.stdout.write("\n" + colorize("─" * 60 + "\n", C.DIM))

        if err_msg:
            self.stdout.write(colorize(f"❌ 失败: {err_msg}\n", C.RED))
            return

        final_answer = acc_answer.strip()
        print_answer_box(self.stdout.write, final_answer, no_color=self.no_color)

        # 元信息（时间/步数/徽章，不与答案混在一行）
        meta = f"⏱ {elapsed:.0f}ms  🔧 {tool_calls} 步"
        if badge:
            meta += f"  🏷 {badge}"
        if degraded:
            meta += "  ⚠️ 降级"
        self.stdout.write(colorize(meta + "\n", C.DIM))

        if not self.quiet and steps:
            self.stdout.write(colorize("\n── ReAct 步骤 ──\n", C.CYAN))
            for s in steps:
                if s.get("tool") == "_resolve_reference":
                    self.stdout.write(colorize(f"  步 {s['iteration']} 🔗 锁定: ", C.MAGENTA) + colorize(s.get("observation", ""), C.CYAN) + "\n")
                    continue
                if s.get("tool") == "FINAL_ANSWER":
                    self.stdout.write(colorize(f"  步 {s['iteration']} ✅ FINAL_ANSWER\n", C.GREEN))
                    continue
                thought = s.get("thought", "")[:60]
                tool = s.get("tool", "?")
                self.stdout.write(f"  步 {s['iteration']} 💭 {thought}\n")
                self.stdout.write(f"      🔧 {tool}\n")
                obs = s.get("observation", "")
                if obs and len(obs) < 200:
                    self.stdout.write(colorize(f"      👁 {obs}\n", C.DIM))

        if not self.quiet and sources:
            self.stdout.write(colorize(f"\n── 来源 ({len(sources)}) ──\n", C.MAGENTA))
            for i, s in enumerate(sources, 1):
                t = s.get("type", "?")
                title = s.get("title", s.get("id", "?"))
                self.stdout.write(f"  [{i}] {colorize(t, C.MAGENTA)} · {title}\n")

        # 保存
        self.last_answer = final_answer
        self.last_steps = steps
        self.last_sources = sources


def main():
    parser = argparse.ArgumentParser(
        description="qa-agent TUI 测试工具 (直连流式 SSE 端点)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--url",
        default=os.environ.get("QA_AGENT_URL", "http://127.0.0.1:8001"),
        help="qa-agent base URL (默认: http://127.0.0.1:8001, env: QA_AGENT_URL)",
    )
    parser.add_argument(
        "--session-id",
        default=None,
        help="多轮 session ID (默认: 随机 UUID)",
    )
    parser.add_argument(
        "--no-color", action="store_true", help="禁用颜色"
    )
    parser.add_argument(
        "--raw", action="store_true", help="raw 模式 (输出原始 SSE 事件 JSON)"
    )
    parser.add_argument(
        "--quiet", action="store_true", help="quiet 模式 (只显示答案, 不显示步骤)"
    )
    parser.add_argument(
        "--timeout", type=float, default=60.0, help="请求超时 (秒, 默认 60)"
    )
    args = parser.parse_args()

    # 健康检查
    try:
        client = QaAgentClient(args.url, args.timeout)
        h = client.health()
        print(colorize(f"✓ qa-agent 健康: {h.get('status', '?')} ({h.get('service', '?')})\n", C.GREEN))
    except Exception as e:
        print(colorize(f"✗ qa-agent 不可达 ({args.url}): {e}\n", C.RED))
        print(colorize("提示: 确认 qa-agent 已启动 (uvicorn app.main:app)\n", C.YELLOW))
        sys.exit(1)

    repl = TuiRepl(
        base_url=args.url,
        session_id=args.session_id,
        no_color=args.no_color,
        raw=args.raw,
        quiet=args.quiet,
    )
    repl.client.timeout = args.timeout

    try:
        repl.cmdloop()
    except KeyboardInterrupt:
        print()


if __name__ == "__main__":
    main()
