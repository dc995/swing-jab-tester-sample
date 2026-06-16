# Setup — end to end

Two levels: **UI-only** (no database, enough for most tests) and **full SQL backend** (for the
persistence test and `--backend` mode).

## A. UI-only (no database)

```powershell
# from the repo root
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ..\swing-jab-tester        # the tester package (provides swing_tester + the CLI)

# enable the Java Access Bridge once per machine, then it's permanent
jabswitch -enable

cd swing-client
.\run.ps1                                   # builds + launches the app (pick a trader)
python test_trade_flow.py                   # JAB test: login -> read -> BUY -> SELL
```

Prereqs: Windows, JDK 17+ (21 recommended) with `JAVA_HOME` set, Python 3.10+. See the tester's
`docs/INSTALL.md` for details and troubleshooting.

## B. Full SQL backend (for `test_trade_flow_sql.py` and `--backend` mode)

The `backend/` folder is a Spring Boot app over **SQL Server** (Windows Integrated Security). It
exposes the REST API the Swing client can target.

### 1. Prereqs

- **SQL Server** 2017+ running (Developer/Express is fine), TCP/IP enabled.
- **Maven** 3.8+ on `PATH` (or use the bundled wrapper).
- `sqlcmd` available (SQL Server command-line tools) — used by the persistence test.

### 2. Seed the database (once)

```powershell
cd backend
.\bootstrap.ps1            # checks prereqs, creates the `finanapp` DB, loads both trader profiles
# re-seed at any time:  .\bootstrap.ps1 -ResetData
```

This creates `dbo.holdings` and `dbo.orders` and seeds `JSMITH` + `MWILSON`. Schema and seed SQL are
in `backend/src/main/resources/schema.sql` and `backend/sql/`.

### 3. Start the REST API

```powershell
cd backend
.\start.ps1               # mvn spring-boot:run -> http://localhost:9296  (Swagger at /swagger-ui.html)
```

> If `mvn` isn't on `PATH` in a fresh shell, use the full path, e.g.
> `& 'C:\ProgramData\chocolatey\lib\maven\<ver>\bin\mvn.cmd' -f backend\pom.xml spring-boot:run`
> with `JAVA_HOME` set.

### 4. Run the client against SQL / run the persistence test

```powershell
cd swing-client
# the app, persisting to SQL:
java -cp out SwingTraderApp JSMITH --backend http://localhost:9296
# the end-to-end test (drives the app in --backend mode, checks dbo.holdings with sqlcmd):
python test_trade_flow_sql.py
```

### REST contract (the "web services")

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/portfolio/{profile}/holdings` | holdings + live P&L (reads SQL) |
| GET | `/api/portfolio/{profile}/orders` | order history |
| GET | `/api/market/prices` | simulated prices `{symbol: price}` |
| POST | `/api/trade/{profile}/buy?symbol=&quantity=&price=` | buy → SQL |
| POST | `/api/trade/{profile}/sell?symbol=&quantity=&price=` | sell → SQL (400 on insufficient) |

Stop the server with `Ctrl+C`. Re-seed with `backend\bootstrap.ps1 -ResetData` if a test leaves the
data changed.
