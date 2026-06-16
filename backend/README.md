# FinanApp Stock Trader

An institutional stock trading demo built with Java, Spring Boot, and SQL Server. Features a dark terminal-inspired UI with live P&L, portfolio charts, multi-profile support, and a full REST API documented with OpenAPI 3.0.

## Quick Start

### First-time setup (run once)
```powershell
.\bootstrap.ps1
```
This checks Java 21, Maven, SQL Server, the JDBC auth DLL, creates the
`finanapp` database, loads both trader profiles, and compiles the project.

### Start the application
```powershell
.\start.ps1
```
Opens <http://localhost:9296> in your default browser once the server is ready.

### Reset sample data at any time
```powershell
.\bootstrap.ps1 -ResetData
```

## Scripts

| Script | Purpose |
|--------|---------|
| `bootstrap.ps1` | First-time setup — installs prereqs, creates DB, seeds data, compiles |
| `bootstrap.ps1 -ResetData` | Wipe and reload all sample data (re-runs schema + seeds) |
| `bootstrap.ps1 -SkipBuild` | Skip Maven compile (prereqs + DB only) |
| `start.ps1` | Start the application; opens browser automatically |
| `start.ps1 -NoBrowser` | Start without auto-opening the browser |

## Prerequisites

| Requirement | Version | Notes |
|------------|---------|-------|
| Java JDK | 21+ | OpenJDK or Microsoft Build of OpenJDK |
| Maven | 3.8+ | `mvn` must be on PATH |
| SQL Server | 2017+ | Developer or Enterprise Edition |
| SQL Server TCP/IP | Enabled | Required for JDBC — enable in SQL Server Configuration Manager |
| JDBC Auth DLL | x64, matching driver | `mssql-jdbc_auth-*.x64.dll` for Windows Integrated Security |

### SQL Server Setup

1. **Enable TCP/IP** in SQL Server Configuration Manager → Protocols → TCP/IP → Enable → Restart service
2. **Auth DLL**: `bootstrap.ps1` automatically detects `mssql-jdbc_auth-*.x64.dll` in the project's
   `lib/` folder and configures `pom.xml` accordingly.  If the DLL is absent from `lib/`, download the
   [Microsoft JDBC Driver 13.x](https://learn.microsoft.com/sql/connect/jdbc/download-microsoft-jdbc-driver-for-sql-server)
   and copy the x64 `.dll` into `lib/` before running the bootstrap.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   Browser (Thymeleaf + JS)               │
│  Login → Portfolio Dashboard → Trade Form                │
│  Chart.js charts │ fetch() AJAX polling every 5s         │
├─────────────────────────────────────────────────────────┤
│                   REST API (Spring Boot)                  │
│  /api/portfolio/{profile}/holdings   GET  (P&L, weights) │
│  /api/portfolio/{profile}/orders     GET  (order history) │
│  /api/trade/{profile}/buy            POST (execute buy)   │
│  /api/trade/{profile}/sell           POST (execute sell)  │
│  /api/market/prices                  GET  (live prices)   │
│  /swagger-ui.html                    Swagger UI           │
├─────────────────────────────────────────────────────────┤
│                   SQL Server (finanapp)                    │
│  dbo.orders │ dbo.holdings │ Windows Integrated Security  │
└─────────────────────────────────────────────────────────┘
```

## Features

- **Mock Login** — Select from two trader profiles (James Smith, Maria Wilson) on a splash page
- **Portfolio Dashboard** — Holdings table with live market price, P&L ($), P&L (%), weight; donut allocation chart; bar chart
- **Trade Execution** — Buy/sell with BUY/SELL toggle, live estimated total, form validation
- **Multi-Profile** — Each trader has isolated holdings and order history
- **REST API** — Full CRUD via OpenAPI 3.0 with Swagger UI
- **Live Data** — Simulated market prices refresh every 5 seconds via AJAX polling
- **Dark Theme** — Bloomberg-style dark UI with green/red P&L indicators

## Project Structure

```
JavaWorkspace/
├── pom.xml                          # Maven: Spring Boot 3.3, mssql-jdbc 13.2.1, Playwright, springdoc
├── src/main/java/com/finanapp/
│   ├── StockTraderApplication.java  # Spring Boot entry point
│   ├── config/
│   │   └── OpenApiConfig.java       # Swagger/OpenAPI configuration
│   ├── model/
│   │   ├── Order.java               # Order entity (profile, symbol, type, qty, price)
│   │   ├── Holding.java             # Holding entity (profile, symbol, qty, avgPrice)
│   │   ├── OrderType.java           # BUY/SELL enum
│   │   └── TradeForm.java           # Form validation DTO
│   ├── repository/
│   │   ├── OrderRepository.java     # Spring Data JDBC (profile-scoped queries)
│   │   └── HoldingRepository.java
│   ├── service/
│   │   ├── TradingService.java      # Buy/sell business logic with avg price calculation
│   │   ├── MarketDataService.java   # Simulated live market prices
│   │   └── InsufficientSharesException.java
│   └── controller/
│       ├── PortfolioController.java     # SSR: portfolio, login, profile switching
│       ├── OrderController.java         # SSR: trade form submission
│       ├── PortfolioApiController.java  # REST: /api/portfolio/** (enriched P&L)
│       ├── TradingApiController.java    # REST: /api/trade/** (buy/sell)
│       └── MarketApiController.java     # REST: /api/market/** (prices)
├── src/main/resources/
    ├── application.properties       # DB connection, port 9296
│   ├── schema.sql                   # DDL: orders + holdings tables with profile column
│   └── templates/
│       ├── login.html               # Profile selection splash page
│       ├── portfolio.html           # Dashboard with charts + live API polling
│       └── trade.html               # Order ticket with BUY/SELL toggle
├── src/test/java/com/finanapp/
│   ├── service/TradingServiceTest.java  # 6 JUnit 5 unit tests (Mockito)
│   └── e2e/TradeFlowE2ETest.java        # 2 Playwright E2E tests
├── sql/
│   ├── seed-jsmith.sql              # James Smith: 6 holdings, 12 orders (aggressive tech)
│   ├── seed-mwilson.sql             # Maria Wilson: 8 holdings, 13 orders (conservative value)
│   └── reset-all.sql                # Drop/recreate + seed all
├── specs/
│   ├── ARCHITECTURE.md              # Multi-tier diagram, tech stack
│   ├── API-SPEC.md                  # REST API endpoints, request/response examples
│   ├── BUY-ORDER.md                 # Buy transaction flow + state diagram
│   ├── SELL-ORDER.md                # Sell transaction flow + state diagram
│   ├── DATA-MODEL.md               # ER diagram, table schemas
│   ├── BDD-SCENARIOS.md            # 17 Gherkin scenarios with Playwright selectors
│   ├── TEST-STRATEGY.md            # Test pyramid, coverage targets
│   ├── UI-DESIGN.md                # Design system, wireframes
│   └── SETUP.md                    # Installation + initialization guide
└── lib/                             # Auth DLL copies (local fallback)
```

## Running Tests

```powershell
# Unit tests only (no DB/app required)
mvn test -Dtest="com.finanapp.service.TradingServiceTest"

# Playwright E2E tests (requires running app + DB with seed data)
mvn test -Dtest="com.finanapp.e2e.TradeFlowE2ETest"

# All tests
mvn test
```

**Test results**: 8/8 pass (6 unit + 2 E2E)

## API Documentation

With the app running, open **http://localhost:9296/swagger-ui.html** for interactive API docs.

Key endpoints:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/portfolio/{profile}/holdings` | GET | Holdings with live P&L, weights |
| `/api/portfolio/{profile}/orders` | GET | Order history |
| `/api/trade/{profile}/buy?symbol=X&quantity=N&price=P` | POST | Execute buy |
| `/api/trade/{profile}/sell?symbol=X&quantity=N&price=P` | POST | Execute sell |
| `/api/market/prices` | GET | All simulated market prices |
| `/api/market/prices/{symbol}` | GET | Single symbol price |

## Seed Data

| Profile | Name | Strategy | Holdings | Orders |
|---------|------|----------|----------|--------|
| JSMITH | James Smith | Aggressive Growth | NVDA, AAPL, MSFT, TSLA, AMZN, META | 12 |
| MWILSON | Maria Wilson | Conservative Value | JNJ, JPM, PG, KO, BRK.B, GOOGL, V, XOM | 13 |

Reset to clean state:
```powershell
sqlcmd -S localhost -E -i sql/reset-all.sql
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 9296 | HTTP port |
| `spring.datasource.url` | `jdbc:sqlserver://localhost:1433;databaseName=finanapp;integratedSecurity=true` | SQL Server connection |
| `spring.thymeleaf.cache` | false | Template hot-reload |

## Tech Stack

- **Java 21** + **Spring Boot 3.3.13**
- **Thymeleaf** (server-side rendering) + **Chart.js 4.x** (client-side charts)
- **Spring Data JDBC** (repositories)
- **mssql-jdbc 13.2.1** with Windows Integrated Security
- **springdoc-openapi 2.3.0** (Swagger UI)
- **JUnit 5** + **Mockito** (unit tests)
- **Playwright 1.49** (E2E browser tests)
