"""End-to-end SQL persistence test.

Drives the Swing app in --backend mode (login -> buy -> sell through the UI) and
verifies each trade actually hits SQL Server, by querying dbo.holdings directly
with sqlcmd between steps. This proves the round trip:

    Swing UI  ->  FinanApp REST API (:9296)  ->  TradingService  ->  SQL Server

Prereqs: the FinanApp Spring Boot server running on :9296 with the seeded
`finanapp` DB (start it with `mvn spring-boot:run` in JavaWorkspace).

Run:
    .venv\\Scripts\\python.exe samples\\swing-trader\\test_trade_flow_sql.py
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))  # for the sibling test_trade_flow import

import test_trade_flow as tf  # noqa: E402  (reuses _compile/_wait_window/_own/etc.)

BACKEND = "http://localhost:9296"
PROFILE = "JSMITH"
QTY = 100


def _api(path: str):
    with urllib.request.urlopen(BACKEND + path, timeout=8) as r:
        return json.loads(r.read().decode())


def _sql_qty(symbol: str) -> int:
    out = subprocess.run(
        ["sqlcmd", "-S", "localhost", "-E", "-h", "-1", "-W", "-Q",
         "SET NOCOUNT ON; SELECT ISNULL((SELECT quantity FROM finanapp.dbo.holdings "
         f"WHERE profile='{PROFILE}' AND symbol='{symbol}'), 0)"],
        capture_output=True, text=True,
    )
    m = re.search(r"-?\d+", out.stdout)
    if not m:
        raise RuntimeError(f"could not read SQL qty for {symbol}: {out.stdout}{out.stderr}")
    return int(m.group())


def _wait_sql(symbol: str, expected: int, timeout: float = 6) -> int:
    deadline = time.time() + timeout
    last = _sql_qty(symbol)
    while last != expected and time.time() < deadline:
        time.sleep(0.4)
        last = _sql_qty(symbol)
    return last


def run() -> bool:
    # Backend must be reachable; derive the ticket's default symbol the same way
    # the app does (first position returned by /holdings).
    try:
        _api("/api/market/prices")
        holdings = _api(f"/api/portfolio/{PROFILE}/holdings")
    except Exception as exc:  # noqa: BLE001
        print(f"[skip] FinanApp backend not reachable at {BACKEND}: {exc}")
        print("       Start it: mvn -f <JavaWorkspace>\\pom.xml spring-boot:run")
        return False

    sym = holdings["positions"][0]["symbol"]
    base = _sql_qty(sym)
    print(f"[setup] backend up; default ticket symbol={sym}; SQL baseline qty={base}")

    tf._compile()
    tf._kill_finanapp()
    proc = subprocess.Popen(
        [tf._tool("java"), "-cp", str(tf.OUT), "SwingTraderApp", "--backend", BACKEND])
    try:
        login = tf._wait_window(tf.LOGIN_WIN)
        print(f"[login] clicking '{tf.PROFILE_BTN}'")
        login.click(tf.PROFILE_BTN)
        time.sleep(tf.PACE)

        desk = tf._wait_window(tf.DESK_WIN)
        own0 = tf._own(desk)
        print(f"[read ] app shows Own={own0}; SQL baseline={base}")
        assert own0 == base, f"app Own {own0} != SQL baseline {base}"

        print(f"[buy  ] BUY {QTY} {sym} in the app -> REST -> SQL")
        desk.click("Buy side")
        time.sleep(0.25)
        desk.click("Place Order")
        sql_buy = _wait_sql(sym, base + QTY)
        own1 = tf._own(desk)
        print(f"[buy  ] SQL qty={sql_buy}, app Own={own1}")
        assert sql_buy == base + QTY, f"SQL {sql_buy} != expected {base + QTY}"
        assert own1 == base + QTY, f"app Own {own1} != {base + QTY}"

        print(f"[sell ] SELL {QTY} {sym} in the app -> REST -> SQL")
        desk.click("Sell side")
        time.sleep(0.25)
        desk.click("Place Order")
        sql_sell = _wait_sql(sym, base)
        own2 = tf._own(desk)
        print(f"[sell ] SQL qty={sql_sell}, app Own={own2}")
        assert sql_sell == base, f"SQL {sql_sell} != restored {base}"
        assert own2 == base, f"app Own {own2} != {base}"

        print(f"[ok   ] trades persisted to SQL and restored: {base} -> {base + QTY} -> {base}")
        return True
    finally:
        try:
            proc.terminate()
        except Exception:  # noqa: BLE001
            pass
        tf._kill_finanapp()


def test_trade_flow_sql():
    assert run()


if __name__ == "__main__":
    sys.exit(0 if run() else 1)
