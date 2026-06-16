"""Complex cascading-flow test (progressive disclosure + dialog).

Opens the Advanced Order dialog and verifies controls are revealed step by step
-- each only appears after the triggering interaction -- then confirms the order.
Exercises scout/JAB across a separate (modeless) dialog window and show/hide
visibility, representing a realistic multi-step user interaction.

    login -> Advanced Order -> pick 'Limit' (reveals Limit price)
          -> 'Show advanced options' (reveals panel)
          -> 'Review order' (reveals summary + Confirm)
          -> 'Confirm order' (executes, closes dialog)

Run:
    .venv\\Scripts\\python.exe samples\\swing-trader\\test_advanced_order.py
"""
from __future__ import annotations

import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))  # for the sibling test_trade_flow import

import test_trade_flow as tf  # noqa: E402  (reuse compile/launch/login helpers)

DIALOG = "FinanApp Advanced Order"


def run() -> bool:
    tf._compile()
    tf._kill_finanapp()
    proc = subprocess.Popen([tf._tool("java"), "-cp", str(tf.OUT), "SwingTraderApp"])
    try:
        login = tf._wait_window(tf.LOGIN_WIN)
        print(f"[login] clicking '{tf.PROFILE_BTN}'")
        login.click(tf.PROFILE_BTN)
        time.sleep(tf.PACE)
        desk = tf._wait_window(tf.DESK_WIN)
        own0 = tf._own(desk)
        print(f"[read ] NVDA owned = {own0}")

        print("[open ] click 'Advanced Order' -> dialog window appears")
        desk.click("Advanced Order")
        dlg = tf._wait_window(DIALOG)
        time.sleep(0.4)

        assert not dlg.has_visible("Limit price"), "Limit price should be hidden initially"
        print("[step1] OK: Limit price hidden until an order type needs it")

        print("[step2] select 'Order type Limit' -> reveals Limit price")
        dlg.click("Order type Limit")
        time.sleep(0.4)
        assert dlg.has_visible("Limit price"), "Limit price should appear after choosing Limit"
        assert not dlg.has_visible("All or none"), "advanced panel should still be hidden"

        print("[step3] check 'Show advanced options' -> reveals advanced panel")
        dlg.click("Show advanced options")
        time.sleep(0.4)
        assert dlg.has_visible("All or none"), "All-or-none should appear after enabling advanced options"

        print("[step4] click 'Review order' -> reveals summary + Confirm")
        assert not dlg.has_visible("Confirm order"), "Confirm should be hidden before review"
        dlg.click("Review order")
        time.sleep(0.4)
        assert dlg.has_visible("Order summary"), "summary should appear after Review"
        assert dlg.has_visible("Confirm order"), "Confirm should appear after Review"

        print("[step5] click 'Confirm order' -> executes BUY 100 NVDA and closes dialog")
        dlg.click("Confirm order")
        time.sleep(tf.PACE)
        own1 = tf._own(desk)
        print(f"[done ] NVDA owned = {own1}")
        assert own1 == own0 + 100, f"expected {own0 + 100}, got {own1}"
        print("[ok   ] cascading dialog flow verified end-to-end")
        return True
    finally:
        try:
            proc.terminate()
        except Exception:  # noqa: BLE001
            pass
        tf._kill_finanapp()


def test_advanced_order():
    assert run()


if __name__ == "__main__":
    sys.exit(0 if run() else 1)
