# Scenario: add a feature → test it → validate it → end‑to‑end

A complete worked example of the loop you'll repeat on any Swing app with `swing-jab-tester`. We add
a **"Close Position"** button (market‑sell the entire holding of the selected symbol), write a
JAB‑driven test, validate it in‑memory, then prove it end‑to‑end against **SQL Server**.

Everything below is copy‑ready and accurate to `swing-client/SwingTraderApp.java`.

---

## 1. Add the feature

The Trade Desk's order ticket is built in `buildTicket()`. It already has a `Place Order` button and
a shared trade core `boolean executeOrder(Side side, String sym, int qty)`. Add a **Close Position**
button that sells the entire owned quantity of the selected symbol — routed through `executeOrder`
so it works in **both** in‑memory and `--backend` (SQL) modes.

In `buildTicket()`, just after the `Place Order` button is created, add:

```java
JButton close = button("Close Position", RED, Color.WHITE);
close.addActionListener(e -> {
    String sym = (String) symbolBox.getSelectedItem();
    Holding h = current.find(sym);
    int own = (h == null) ? 0 : h.qty;
    if (own <= 0) { error("No position to close in " + sym + "."); return; }
    executeOrder(Side.SELL, sym, own);          // shared core -> in-memory OR REST/SQL
});
a11y(close, "Close Position", "Sell the entire position in the selected symbol at market");
```

…and add it to the ticket row alongside the others:

```java
p.add(place); p.add(adv); p.add(advM); p.add(close);   // <-- add close
```

Two things make it testable: it has an **accessible name** (`"Close Position"`) and it goes through
`executeOrder`, which updates the `Last: … Own: N` oracle the tests read.

Rebuild:

```powershell
cd swing-client
.\run.ps1 -BuildOnly
```

---

## 2. Write the test (JAB‑driven)

Create `swing-client/test_close_position.py`. It reuses the helpers in `test_trade_flow.py`
(`_compile`, `_wait_window`, `_own`, the window/title constants), logs in, confirms a non‑zero
position, clicks **Close Position**, and asserts the position is fully closed (`Own: 0`).

```python
"""Feature test: 'Close Position' sells the entire holding of the selected symbol."""
import subprocess, sys, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))          # for the sibling test_trade_flow import
import test_trade_flow as tf           # noqa: E402


def run() -> bool:
    tf._compile()
    tf._kill_finanapp()
    proc = subprocess.Popen([tf._tool("java"), "-cp", str(tf.OUT), "SwingTraderApp"])
    try:
        login = tf._wait_window(tf.LOGIN_WIN)
        login.click(tf.PROFILE_BTN)                  # sign in as James Smith (splash, not modal)
        time.sleep(tf.PACE)
        desk = tf._wait_window(tf.DESK_WIN)

        own0 = tf._own(desk)                         # NVDA is the default ticket symbol
        print(f"[read ] owned before = {own0}")
        assert own0 > 0, "expected an open position to close"

        print("[act  ] click 'Close Position'")
        desk.click("Close Position")                 # plain button -> JAB action click is fine
        time.sleep(tf.PACE)

        own1 = tf._own(desk)
        print(f"[check] owned after = {own1}")
        assert own1 == 0, f"position not fully closed (Own={own1})"
        print("[ok   ] Close Position verified")
        return True
    finally:
        proc.terminate()
        tf._kill_finanapp()


def test_close_position():
    assert run()


if __name__ == "__main__":
    sys.exit(0 if run() else 1)
```

Notes:
- It's a **plain button**, so `desk.click(...)` (JAB action) is correct — no modal, no mouse fallback.
- It asserts on the in‑app **oracle** (`Own:`), not on timing.
- It runs standalone *and* under pytest.

---

## 3. Validate (in‑memory)

```powershell
cd swing-client
python test_close_position.py
```

Expected:

```
[read ] owned before = 500
[act  ] click 'Close Position'
[check] owned after = 0
[ok   ] Close Position verified
```

If `Close Position` isn't found, you forgot the `a11y(close, "Close Position", …)` name or didn't add
the button to the row. If `_own` can't parse, confirm the `Last: … Own: N` label still renders.

Also re‑run the existing suite to confirm no regression:

```powershell
python test_trade_flow.py
python test_advanced_order.py
python test_advanced_order_modal.py
```

---

## 4. End‑to‑end (through the SQL backend)

Now prove the feature persists to the database, not just the in‑memory model. Bring up the backend
(see [SETUP.md](SETUP.md) §B), then drive the app in `--backend` mode and check `dbo.holdings`
directly.

Create `swing-client/test_close_position_sql.py`:

```python
"""E2E: 'Close Position' removes the holding in SQL Server (app -> REST -> SQL)."""
import re, subprocess, sys, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import test_trade_flow as tf           # noqa: E402

BACKEND = "http://localhost:9296"
PROFILE = "JSMITH"


def _sql_qty(symbol: str) -> int:
    out = subprocess.run(
        ["sqlcmd", "-S", "localhost", "-E", "-h", "-1", "-W", "-Q",
         "SET NOCOUNT ON; SELECT ISNULL((SELECT quantity FROM finanapp.dbo.holdings "
         f"WHERE profile='{PROFILE}' AND symbol='NVDA'), 0)"],
        capture_output=True, text=True)
    return int(re.search(r"-?\d+", out.stdout).group())


def run() -> bool:
    base = _sql_qty("NVDA")
    print(f"[setup] SQL NVDA baseline = {base}")
    assert base > 0, "seed the DB first (backend\\bootstrap.ps1)"

    tf._compile(); tf._kill_finanapp()
    proc = subprocess.Popen([tf._tool("java"), "-cp", str(tf.OUT),
                             "SwingTraderApp", "--backend", BACKEND])
    try:
        login = tf._wait_window(tf.LOGIN_WIN); login.click(tf.PROFILE_BTN); time.sleep(tf.PACE)
        desk = tf._wait_window(tf.DESK_WIN)
        desk.click("Close Position")
        # poll SQL until the position is gone (qty 0)
        for _ in range(15):
            if _sql_qty("NVDA") == 0:
                break
            time.sleep(0.4)
        after = _sql_qty("NVDA")
        print(f"[check] SQL NVDA after close = {after}")
        assert after == 0, f"SQL still shows {after} NVDA"
        print("[ok   ] Close Position persisted to SQL")
        return True
    finally:
        proc.terminate(); tf._kill_finanapp()


def test_close_position_sql():
    assert run()


if __name__ == "__main__":
    sys.exit(0 if run() else 1)
```

Run it:

```powershell
# backend must be up on :9296 (docs/SETUP.md §B)
cd swing-client
python test_close_position_sql.py
```

Expected:

```
[setup] SQL NVDA baseline = 500
[check] SQL NVDA after close = 0
[ok   ] Close Position persisted to SQL
```

> This permanently changes the seeded data (it closes the NVDA position). Restore it with
> `backend\bootstrap.ps1 -ResetData` when you're done.

---

## The loop, recapped

1. **Add** the control in `SwingTraderApp.java` — give it an **accessible name**, route trades through
   `executeOrder` so it works in both modes.
2. **Test** it with a JAB‑driven Python test that drives by name and asserts on an in‑app oracle.
3. **Validate** in‑memory (fast, no DB) and re‑run the suite for regressions.
4. **End‑to‑end** through the REST/SQL backend, asserting the real database with `sqlcmd`.

That's the whole workflow — repeat it for any feature, on this app or your own.
