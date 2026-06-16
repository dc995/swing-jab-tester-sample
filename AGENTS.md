# AGENTS.md — FinanApp sample

This repo is the sample app for the **`swing-jab-tester`** toolkit, exercised by a **GitHub
Copilot–hosted agent** (GitHub Copilot runs the agent; `swing_tester` is its JAB hands). When working
here, also follow the tester's `AGENTS.md` (the JAB rules apply). This file adds the sample-specific
facts.

## Layout

- `swing-client/SwingTraderApp.java` — the whole desktop app, single file, compiles with `javac`
  (no Maven). `run.ps1` builds + launches it. Compiled output goes to `swing-client/out/`.
- `swing-client/test_*.py` — JAB-driven tests; they import `swing_tester` (install the tester:
  `pip install -e ..\swing-jab-tester`).
- `backend/` — the SQL-backed mock (Spring Boot REST + SQL Server schema + Thymeleaf frontend),
  copied from a working app. Only needed for the `*_sql` test and `--backend` mode.

## Running

```powershell
cd swing-client
.\run.ps1 -Trader JSMITH                       # launch straight to the desktop (skip splash)
python test_trade_flow.py                       # in-memory JAB test
python test_trade_flow_sql.py                   # needs backend on :9296 (see docs/SETUP.md)
```

## App conventions you must preserve

- **Glob-safe window titles** (no `[ ] * ?`): `FinanApp Stock Trader - Sign In`,
  `FinanApp Portfolio Dashboard - <name>`, `FinanApp Trade Desk - <name>`, `FinanApp Advanced Order`.
- **Accessible names on every control** — `a11y(component, "Name", "desc")` sets `setName` +
  `setAccessibleName`. Tests locate controls by these names; if you add a control, name it.
- **One trade core**: `boolean executeOrder(Side, symbol, qty)` is used by the main ticket **and**
  the Advanced Order dialog. Route new buy/sell paths through it so in-memory and `--backend` (SQL)
  modes both work.
- **Hybrid backend**: `--backend http://localhost:9296` switches reads/trades to the REST API → SQL;
  without it the app uses in-memory mock data. Keep new features working in both modes.
- **Dialogs**: the modeless Advanced Order is driven by JAB `click`; the modal one needs the
  `click_mouse` fallback (see the two `test_advanced_order*` tests).
- **Oracle for tests**: the Trade Desk shows `Last: $x   Own: N`; tests parse `Own:` (`tf._own(desk)`)
  to verify buys/sells.

## Adding a feature

Follow **[docs/SCENARIO-add-test-validate-e2e.md](docs/SCENARIO-add-test-validate-e2e.md)** — it walks
the full loop (add → JAB test → validate → SQL e2e) with copy-ready snippets. Always: give the new
control an accessible name, route trades through `executeOrder`, add a test that asserts on an in-app
oracle, and run it standalone before declaring done.
