"""Modal-dialog flow test, driven by scout's mouse-click fallback.

Same cascading flow as test_advanced_order.py, but the dialog is MODAL. A modal
dialog blocks pyjab's programmatic click on the JAB action thread (it would hang),
so this drives every click with JabActor.click_mouse -> pyjab simulate=True, i.e.
a real OS mouse click at the control's centre. Reading the tree (has_visible) is
unaffected by modality.

Run:
    .venv\\Scripts\\python.exe samples\\swing-trader\\test_advanced_order_modal.py
"""
from __future__ import annotations

import ctypes

# Make this process DPI-aware so synthesized mouse coordinates match the JAB
# on-screen bounds on scaled (e.g. 150%) displays. Must happen before any clicks.
try:
    ctypes.windll.shcore.SetProcessDpiAwareness(2)  # per-monitor-v2
except Exception:  # noqa: BLE001
    try:
        ctypes.windll.user32.SetProcessDPIAware()
    except Exception:  # noqa: BLE001
        pass

import subprocess  # noqa: E402
import sys  # noqa: E402
import time  # noqa: E402
from pathlib import Path  # noqa: E402

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))  # for the sibling test_trade_flow import

import test_trade_flow as tf  # noqa: E402

DIALOG = "FinanApp Advanced Order"


def run() -> bool:
    tf._compile()
    tf._kill_finanapp()
    proc = subprocess.Popen([tf._tool("java"), "-cp", str(tf.OUT), "SwingTraderApp"])
    try:
        login = tf._wait_window(tf.LOGIN_WIN)
        print(f"[login] clicking '{tf.PROFILE_BTN}'")
        login.click(tf.PROFILE_BTN)  # splash isn't modal -> JAB click is fine
        time.sleep(tf.PACE)
        desk = tf._wait_window(tf.DESK_WIN)
        own0 = tf._own(desk)
        print(f"[read ] NVDA owned = {own0}")

        # A modal dialog's open action blocks JAB's programmatic click, so use the
        # mouse-click fallback to open AND to drive every control inside it.
        print("[open ] mouse-click 'Advanced Order Modal' -> MODAL dialog")
        desk.click_mouse("Advanced Order Modal")
        dlg = tf._wait_window(DIALOG)
        time.sleep(0.5)
        assert not dlg.has_visible("Limit price"), "Limit price should be hidden initially"
        print("[step1] OK: Limit price hidden initially")

        print("[step2] mouse-click 'Order type Limit' -> reveals Limit price")
        dlg.click_mouse("Order type Limit")
        time.sleep(0.4)
        assert dlg.has_visible("Limit price"), "Limit price should appear after choosing Limit"

        print("[step3] mouse-click 'Show advanced options' -> reveals advanced panel")
        dlg.click_mouse("Show advanced options")
        time.sleep(0.4)
        assert dlg.has_visible("All or none"), "advanced panel should appear"

        print("[step4] mouse-click 'Review order' -> reveals summary + Confirm")
        assert not dlg.has_visible("Confirm order"), "Confirm should be hidden before review"
        dlg.click_mouse("Review order")
        time.sleep(0.4)
        assert dlg.has_visible("Order summary"), "summary should appear after Review"
        assert dlg.has_visible("Confirm order"), "Confirm should appear after Review"

        print("[step5] mouse-click 'Confirm order' -> executes BUY 100 NVDA, closes modal")
        dlg.click_mouse("Confirm order")
        time.sleep(tf.PACE)
        own1 = tf._own(desk)
        print(f"[done ] NVDA owned = {own1}")
        assert own1 == own0 + 100, f"expected {own0 + 100}, got {own1}"
        print("[ok   ] modal cascading dialog driven end-to-end via mouse fallback")
        return True
    finally:
        try:
            proc.terminate()
        except Exception:  # noqa: BLE001
            pass
        tf._kill_finanapp()


def test_advanced_order_modal():
    assert run()


if __name__ == "__main__":
    sys.exit(0 if run() else 1)
