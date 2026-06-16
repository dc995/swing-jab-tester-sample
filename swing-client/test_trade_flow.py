"""Functional UI test for the FinanApp Swing trader, driven through the Java
Access Bridge (no pixels). It exercises the full happy path:

    select a user login  ->  read the trade data  ->  buy  ->  sell

and verifies each step against the app's own "Own: <n>" oracle on the Trade Desk
plus the holdings/blotter cell text.

Run standalone (watch the app on screen while it drives itself):

    .venv\\Scripts\\python.exe samples\\swing-trader\\test_trade_flow.py

or under pytest from the repo root:

    .venv\\Scripts\\python.exe -m pytest samples/swing-trader/test_trade_flow.py -s

Requires: a JDK on PATH/JAVA_HOME and the optional pyjab package (already in the
repo's .venv). The test compiles the app, launches it at the sign-in splash,
then logs in and trades as James Smith.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import time
from pathlib import Path
from shutil import which

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
SRC = HERE / "SwingTraderApp.java"

# Requires the companion tester package:
#   pip install -e ..\..\swing-jab-tester     (or: pip install swing-jab-tester)
from swing_tester.jab import JabActor  # noqa: E402

LOGIN_WIN = "FinanApp Stock Trader - Sign In"
DASH_WIN = "FinanApp Portfolio Dashboard - James Smith"
DESK_WIN = "FinanApp Trade Desk - James Smith"
PROFILE_BTN = "Sign in as James Smith"

PACE = 0.7  # seconds between steps, so a human can follow along on screen
HELD = {"NVDA", "AAPL", "MSFT", "TSLA", "AMZN", "META"}  # James Smith's holdings


def _tool(name: str) -> str:
    exe = which(name)
    if exe:
        return exe
    jh = os.environ.get("JAVA_HOME")
    if jh and (Path(jh) / "bin" / f"{name}.exe").exists():
        return str(Path(jh) / "bin" / f"{name}.exe")
    raise RuntimeError(f"{name} not found on PATH or under JAVA_HOME")


def _kill_finanapp() -> None:
    subprocess.run(
        ["powershell", "-NoProfile", "-Command",
         "Get-Process java -ErrorAction SilentlyContinue | "
         "Where-Object { $_.MainWindowTitle -like 'FinanApp*' } | Stop-Process -Force"],
        capture_output=True,
    )


def _compile() -> None:
    OUT.mkdir(exist_ok=True)
    subprocess.run([_tool("javac"), "-encoding", "UTF-8", "-d", str(OUT), str(SRC)], check=True)


def _wait_window(title: str, timeout: float = 25) -> JabActor:
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        try:
            return JabActor(title, timeout=2)
        except Exception as exc:  # noqa: BLE001
            last = exc
            time.sleep(0.4)
    raise TimeoutError(f"window {title!r} did not appear ({last})")


def _own(desk: JabActor) -> int:
    """Parse 'Own: <n>' from the Trade Desk's live 'Last: ... Own: N' label."""
    lbl = desk.first_name("Last:")
    m = re.search(r"Own:\s*([\d,]+)", lbl)
    if not m:
        raise AssertionError(f"could not read shares owned from label {lbl!r}")
    return int(m.group(1).replace(",", ""))


def run_flow() -> bool:
    print("[setup] compiling and launching the app at the sign-in splash...")
    _compile()
    _kill_finanapp()
    proc = subprocess.Popen([_tool("java"), "-cp", str(OUT), "SwingTraderApp"])
    try:
        # 1) LOGIN -- select a user on the splash
        login = _wait_window(LOGIN_WIN)
        print(f"[login] clicking '{PROFILE_BTN}'")
        login.click(PROFILE_BTN)
        time.sleep(PACE)

        desk = _wait_window(DESK_WIN)
        dash = _wait_window(DASH_WIN)

        # 2) READ the trade data
        held = sorted({n for n in dash.names() if n in HELD})
        print(f"[read] dashboard holdings: {held}")
        assert "NVDA" in held, "expected NVDA to be visible in the holdings table"

        # NVDA is the ticket's default symbol for James Smith (his first holding),
        # so the ticket already targets it -- confirm via the "Own:" oracle.
        # (pyjab's combo select() can't map Swing's label-rendered items, so we
        # rely on the default selection rather than re-selecting here.)
        time.sleep(PACE)
        own0 = _own(desk)
        print(f"[read] selected symbol NVDA, shares owned = {own0}")
        assert own0 == 500, f"expected to start with 500 NVDA, saw {own0}"

        # 3) BUY 100 NVDA (default ticket quantity)
        print("[buy ] side=BUY qty=100 NVDA -> Place Order")
        desk.click("Buy side")
        time.sleep(0.25)
        desk.click("Place Order")
        time.sleep(PACE)
        own1 = _own(desk)
        print(f"[buy ] NVDA shares owned = {own1}")
        assert own1 == own0 + 100, f"after BUY 100 expected {own0 + 100}, saw {own1}"

        # 4) SELL 100 NVDA
        print("[sell] side=SELL qty=100 NVDA -> Place Order")
        desk.click("Sell side")
        time.sleep(0.25)
        desk.click("Place Order")
        time.sleep(PACE)
        own2 = _own(desk)
        print(f"[sell] NVDA shares owned = {own2}")
        assert own2 == own1 - 100, f"after SELL 100 expected {own1 - 100}, saw {own2}"

        # 5) the order blotter recorded both fills
        blotter = desk.names()
        assert "BUY" in blotter and "SELL" in blotter, "blotter is missing the new orders"
        print("[ok  ] login + read + BUY + SELL all verified through JAB")
        return True
    finally:
        try:
            proc.terminate()
        except Exception:  # noqa: BLE001
            pass
        _kill_finanapp()


def test_trade_flow():
    """pytest entry point."""
    assert run_flow()


if __name__ == "__main__":
    sys.exit(0 if run_flow() else 1)
