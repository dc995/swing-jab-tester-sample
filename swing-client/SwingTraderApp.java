import java.awt.*;
import java.awt.event.*;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;
import javax.swing.table.*;

/**
 * FinanApp Stock Trader -- Swing edition.
 *
 * A self-contained, single-file Swing port of the "FinanApp Stock Trader" web app
 * (Spring Boot + SQL Server, package com.finanapp). No Maven/Spring/SQL required:
 * all data is in-memory and mirrors the original seed data.
 *
 * Two things make this a useful sample / test target:
 *   1. MULTI-MONITOR: after picking a trader, it opens a Portfolio Dashboard on
 *      monitor 1 and a Trade Desk on monitor 2 (auto-detected via the AWT
 *      GraphicsEnvironment). With a single monitor it tiles the two windows
 *      side by side. The "Window" menu can move/tile windows on demand.
 *   2. ACCESSIBILITY: every interactive control sets an accessible name +
 *      description (and Component.setName), so Java Access Bridge clients
 *      (NVDA/JAWS, scout --jab-tree, AssertJ-Swing, etc.) can locate them.
 *
 * Mock users (from the original app's seed data):
 *   JSMITH  - James Smith  - Aggressive Growth  (NVDA, AAPL, MSFT, TSLA, AMZN, META)
 *   MWILSON - Maria Wilson - Conservative Value (JNJ, JPM, PG, KO, BRK.B, GOOGL, V, XOM)
 *
 * Build & run:
 *   javac -encoding UTF-8 -d out SwingTraderApp.java
 *   java  -cp out SwingTraderApp
 * (or just run .\run.ps1)
 */
public class SwingTraderApp {

    // ---------------------------------------------------------------- Theme --
    static final Color BG     = new Color(0x0B0E11);
    static final Color PANEL  = new Color(0x161B22);
    static final Color PANEL2 = new Color(0x11151C);
    static final Color HEADER = new Color(0x1C2128);
    static final Color BORDER = new Color(0x30363D);
    static final Color TEXT   = new Color(0xE6EDF3);
    static final Color MUTED  = new Color(0x8B949E);
    static final Color GREEN  = new Color(0x16C784);
    static final Color RED    = new Color(0xEA3943);
    static final Color ACCENT = new Color(0x2F81F7);
    static final Color SELBG  = new Color(0x1F2A37);

    static final Font UIF   = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font UIB   = new Font("Segoe UI", Font.BOLD, 13);
    static final Font MONO  = new Font("Consolas", Font.PLAIN, 13);
    static final Font TITLE = new Font("Segoe UI", Font.BOLD, 22);
    static final Font BIG   = new Font("Consolas", Font.BOLD, 22);

    static final DecimalFormat MONEY   = new DecimalFormat("$#,##0.00");
    static final DecimalFormat MONEY_S = new DecimalFormat("+$#,##0.00;-$#,##0.00");
    static final DecimalFormat PCT_S   = new DecimalFormat("+0.00%;-0.00%");
    static final DecimalFormat PCT     = new DecimalFormat("0.00%");
    static final DecimalFormat QTY     = new DecimalFormat("#,##0");
    static final DateTimeFormatter TS  = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ------------------------------------------ Market data (simulated live) --
    // Base prices mirror com.finanapp.service.MarketDataService.
    static final Map<String, Double> BASE = new LinkedHashMap<>();
    static {
        BASE.put("AAPL", 227.48); BASE.put("MSFT", 415.20); BASE.put("NVDA", 138.07);
        BASE.put("TSLA", 248.71); BASE.put("AMZN", 201.33); BASE.put("META", 585.25);
        BASE.put("GOOGL", 174.65); BASE.put("JNJ", 156.80); BASE.put("JPM", 242.50);
        BASE.put("PG", 168.30);   BASE.put("KO", 62.45);    BASE.put("BRK.B", 462.10);
        BASE.put("V", 312.70);    BASE.put("XOM", 108.20);  BASE.put("AMD", 165.50);
        BASE.put("NFLX", 912.30); BASE.put("DIS", 112.40);  BASE.put("BA", 178.90);
        BASE.put("INTC", 24.15);  BASE.put("WMT", 92.80);
    }
    final Map<String, Double> prices = new HashMap<>();
    final Random rnd = new Random();

    void tickPrices() { // jitter every symbol by +/-3%, like the web app's 5s refresh
        if (backend != null) {
            try { backendPrices(); } catch (Exception e) { /* keep last good prices */ }
            return;
        }
        for (Map.Entry<String, Double> e : BASE.entrySet()) {
            double pct = -0.03 + rnd.nextDouble() * 0.06;
            prices.put(e.getKey(), round2(e.getValue() * (1 + pct)));
        }
    }
    double price(String sym) { return prices.getOrDefault(sym, BASE.getOrDefault(sym, 100.0)); }
    static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    // ------------------------------------------------------------ Domain model --
    enum Side { BUY, SELL }

    static final class Holding {
        final String symbol; int qty; double avg;
        Holding(String s, int q, double a) { symbol = s; qty = q; avg = a; }
    }
    static final class Order {
        final String time, symbol; final Side side; final int qty; final double price;
        Order(String t, String sym, Side s, int q, double p) {
            time = t; symbol = sym; side = s; qty = q; price = p;
        }
    }
    static final class Profile {
        final String code, name, style;
        final List<Holding> holdings = new ArrayList<>();
        final List<Order> orders = new ArrayList<>();
        Profile(String c, String n, String st) { code = c; name = n; style = st; }
        Holding find(String sym) {
            for (Holding h : holdings) if (h.symbol.equals(sym)) return h;
            return null;
        }
    }

    final Map<String, Profile> profiles = new LinkedHashMap<>();
    Profile current;
    Rest backend;  // null = in-memory mock; non-null = persist via the FinanApp REST API/SQL

    void seed() {
        Profile js = new Profile("JSMITH", "James Smith", "Aggressive Growth");
        js.holdings.addAll(List.of(
            new Holding("NVDA", 500, 138.42), new Holding("AAPL", 800, 192.75),
            new Holding("MSFT", 350, 415.20), new Holding("TSLA", 400, 248.60),
            new Holding("AMZN", 250, 201.33), new Holding("META", 300, 585.25)));
        o(js, "09-15 09:31", "NVDA", Side.BUY, 300, 125.50);
        o(js, "09-22 10:05", "AAPL", Side.BUY, 500, 185.00);
        o(js, "10-01 09:45", "MSFT", Side.BUY, 200, 398.50);
        o(js, "10-10 11:12", "TSLA", Side.BUY, 600, 222.00);
        o(js, "10-18 14:22", "AMZN", Side.BUY, 250, 201.33);
        o(js, "11-05 09:30", "NVDA", Side.BUY, 200, 157.80);
        o(js, "11-12 10:44", "AAPL", Side.BUY, 300, 205.60);
        o(js, "12-02 09:33", "MSFT", Side.BUY, 150, 437.80);
        o(js, "12-15 15:01", "TSLA", Side.SELL, 200, 289.40);
        o(js, "01-08 09:30", "META", Side.BUY, 300, 585.25);
        o(js, "02-10 13:45", "NVDA", Side.SELL, 100, 162.00);
        o(js, "03-01 10:15", "AAPL", Side.SELL, 100, 227.00);
        profiles.put(js.code, js);

        Profile mw = new Profile("MWILSON", "Maria Wilson", "Conservative Value");
        mw.holdings.addAll(List.of(
            new Holding("JNJ", 1200, 156.80), new Holding("JPM", 600, 242.50),
            new Holding("PG", 800, 168.30),   new Holding("KO", 1500, 62.45),
            new Holding("BRK.B", 200, 462.10), new Holding("GOOGL", 150, 174.65),
            new Holding("V", 400, 312.70),    new Holding("XOM", 700, 108.20)));
        o(mw, "08-10 09:30", "JNJ", Side.BUY, 500, 148.20);
        o(mw, "08-20 10:12", "JPM", Side.BUY, 300, 228.00);
        o(mw, "09-05 09:45", "PG", Side.BUY, 400, 162.50);
        o(mw, "09-12 11:00", "KO", Side.BUY, 1000, 60.20);
        o(mw, "09-25 09:31", "BRK.B", Side.BUY, 100, 445.00);
        o(mw, "10-15 10:22", "JNJ", Side.BUY, 700, 162.60);
        o(mw, "10-28 14:05", "JPM", Side.BUY, 300, 257.00);
        o(mw, "11-10 09:30", "GOOGL", Side.BUY, 150, 174.65);
        o(mw, "11-22 10:45", "PG", Side.BUY, 400, 174.10);
        o(mw, "12-05 09:30", "KO", Side.BUY, 500, 66.95);
        o(mw, "01-15 09:32", "V", Side.BUY, 400, 312.70);
        o(mw, "01-28 10:15", "BRK.B", Side.BUY, 100, 479.20);
        o(mw, "02-12 09:30", "XOM", Side.BUY, 700, 108.20);
        profiles.put(mw.code, mw);
    }
    void o(Profile p, String t, String s, Side sd, int q, double pr) {
        p.orders.add(new Order(t, s, sd, q, pr));
    }

    // ----------------------------------------------------------------- Widgets --
    JFrame splash, dash, trade;
    Timer timer;

    HoldingsModel holdModel;
    MarketModel marketModel;
    OrderModel orderModel;
    JLabel dashValueLbl, dashPnlLbl, dashScreenLbl, tradeScreenLbl, statusLbl, lastLbl, estLbl;
    JComboBox<String> symbolBox;
    JSpinner qtySpin;
    JToggleButton buyBtn, sellBtn;

    // ====================================================================== Splash
    void showSplash() {
        if (splash != null) splash.dispose();
        splash = new JFrame("FinanApp Stock Trader - Sign In");
        splash.setName("SignInWindow");
        splash.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel root = panel(new BorderLayout(0, 18));
        root.setBorder(pad(28, 36, 24, 36));

        JPanel head = panel(new GridLayout(0, 1, 0, 4));
        head.add(label("FinanApp Stock Trader", TEXT, TITLE));
        head.add(label("Select a trader profile to open the trading desktop", MUTED, UIF));
        root.add(head, BorderLayout.NORTH);

        JPanel cards = panel(new GridLayout(1, 0, 16, 0));
        for (Profile p : profiles.values()) cards.add(profileCard(p));
        root.add(cards, BorderLayout.CENTER);

        int screens = screens().length;
        String msg = screens >= 2
            ? screens + " monitors detected -- Dashboard opens on monitor 1, Trade Desk on monitor 2"
            : "1 monitor detected -- the two windows will tile side by side";
        JLabel mon = label(msg, screens >= 2 ? GREEN : MUTED, UIF);
        a11y(mon, "Monitor status", msg);
        root.add(mon, BorderLayout.SOUTH);

        splash.setContentPane(root);
        splash.pack();
        splash.setMinimumSize(new Dimension(640, 340));
        centerOnPrimary(splash);
        splash.setVisible(true);
    }

    JComponent profileCard(Profile p) {
        JPanel c = card(new BorderLayout(0, 12));
        c.setBorder(linePad(BORDER, 18));

        JPanel info = card(new GridLayout(0, 1, 0, 4));
        info.add(label(p.name, TEXT, UIB.deriveFont(17f)));
        info.add(label(p.style, ACCENT, UIF));
        info.add(label(p.holdings.size() + " holdings", MUTED, UIF));
        c.add(info, BorderLayout.CENTER);

        JButton go = button("Trade as " + p.name.split(" ")[0], ACCENT, Color.WHITE);
        go.addActionListener(e -> launchDesktop(p));
        a11y(go, "Sign in as " + p.name, "Open the trading desktop for profile " + p.code);
        c.add(go, BorderLayout.SOUTH);

        a11y(c, p.name + " profile card", p.style + " portfolio with " + p.holdings.size() + " holdings");
        return c;
    }

    // ============================================================= Launch desktop
    void launchDesktop(Profile p) {
        current = p;
        if (backend != null) {
            try {
                backendLoad(p);
            } catch (Exception e) {
                System.err.println("[backend] load failed, using in-memory data: " + e.getMessage());
            }
        }
        if (splash != null) splash.dispose();

        Rectangle[] layout = computeLayout();
        dash = buildDashboard();
        trade = buildTrade();
        dash.setBounds(layout[0]);
        trade.setBounds(layout[1]);

        if (!current.holdings.isEmpty()) symbolBox.setSelectedItem(current.holdings.get(0).symbol);

        dash.setVisible(true);
        trade.setVisible(true);
        refreshAll();

        if (timer != null) timer.stop();
        timer = new Timer(3000, e -> { tickPrices(); refreshAll(); }); // live ticks
        timer.start();
    }

    // ================================================================== Dashboard
    JFrame buildDashboard() {
        JFrame f = new JFrame("FinanApp Portfolio Dashboard - " + current.name);
        f.setName("DashboardWindow");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setJMenuBar(buildMenuBar(true));

        JPanel root = panel(new BorderLayout(0, 0));

        // Top bar: profile identity + actions
        JPanel bar = card(new BorderLayout());
        bar.setBorder(pad(14, 18, 14, 18));
        JPanel id = card(new GridLayout(0, 1, 0, 2));
        id.add(label(current.name, TEXT, UIB.deriveFont(18f)));
        id.add(label(current.style + " - Portfolio", ACCENT, UIF));
        bar.add(id, BorderLayout.WEST);

        JPanel actions = card(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton switchBtn = button("Switch Profile", PANEL2, TEXT);
        switchBtn.addActionListener(e -> switchProfile());
        a11y(switchBtn, "Switch Profile", "Sign out and return to the profile picker");
        JButton moveBtn = button("Move to Next Monitor", PANEL2, TEXT);
        moveBtn.addActionListener(e -> moveToNext(dash));
        a11y(moveBtn, "Move Dashboard to Next Monitor", "Relocate this window to the next display");
        actions.add(switchBtn);
        actions.add(moveBtn);
        bar.add(actions, BorderLayout.EAST);
        root.add(bar, BorderLayout.NORTH);

        // Metric strip
        dashValueLbl = new JLabel("$0.00");
        dashPnlLbl = new JLabel("+$0.00 (0.00%)");
        dashScreenLbl = new JLabel("Monitor -");
        JPanel strip = panel(new GridLayout(1, 3, 12, 0));
        strip.setBorder(pad(0, 18, 8, 18));
        strip.add(metric("TOTAL VALUE", dashValueLbl));
        strip.add(metric("TOTAL P&L", dashPnlLbl));
        strip.add(metric("DISPLAY", dashScreenLbl));

        // Holdings table
        holdModel = new HoldingsModel();
        JTable t = new JTable(holdModel);
        styleTable(t);
        a11y(t, "Holdings table", "Per-symbol quantity, average price, last price, market value, P&L and weight");
        setRenderer(t, 0, new TextRenderer(SwingConstants.LEFT));
        setRenderer(t, 1, new NumRenderer(QTY, false));
        setRenderer(t, 2, new NumRenderer(MONEY, false));
        setRenderer(t, 3, new NumRenderer(MONEY, false));
        setRenderer(t, 4, new NumRenderer(MONEY, false));
        setRenderer(t, 5, new NumRenderer(MONEY_S, true));
        setRenderer(t, 6, new NumRenderer(PCT_S, true));
        setRenderer(t, 7, new NumRenderer(PCT, false));

        JPanel center = panel(new BorderLayout(0, 8));
        center.setBorder(pad(0, 18, 16, 18));
        center.add(strip, BorderLayout.NORTH);
        center.add(section("Holdings", scroll(t)), BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        f.setContentPane(root);
        return f;
    }

    JComponent metric(String title, JLabel value) {
        JPanel p = card(new BorderLayout(0, 6));
        p.setBorder(linePad(BORDER, 14));
        p.add(label(title, MUTED, UIF.deriveFont(11f)), BorderLayout.NORTH);
        value.setFont(BIG);
        value.setForeground(TEXT);
        p.add(value, BorderLayout.CENTER);
        a11y(p, title + " metric", null);
        return p;
    }

    // ================================================================= Trade Desk
    JFrame buildTrade() {
        JFrame f = new JFrame("FinanApp Trade Desk - " + current.name);
        f.setName("TradeDeskWindow");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setJMenuBar(buildMenuBar(false));

        JPanel root = panel(new BorderLayout(0, 0));
        root.add(section("Trade Ticket", buildTicket()), BorderLayout.NORTH);

        marketModel = new MarketModel();
        JTable mkt = new JTable(marketModel);
        styleTable(mkt);
        a11y(mkt, "Market watch table", "Live last price and percent change for the watchlist");
        setRenderer(mkt, 0, new TextRenderer(SwingConstants.LEFT));
        setRenderer(mkt, 1, new NumRenderer(MONEY, false));
        setRenderer(mkt, 2, new NumRenderer(PCT_S, true));

        orderModel = new OrderModel();
        JTable ord = new JTable(orderModel);
        styleTable(ord);
        a11y(ord, "Order blotter table", "Executed orders, most recent first");
        setRenderer(ord, 0, new TextRenderer(SwingConstants.LEFT));
        setRenderer(ord, 1, new TextRenderer(SwingConstants.LEFT));
        setRenderer(ord, 2, new SideRenderer());
        setRenderer(ord, 3, new NumRenderer(QTY, false));
        setRenderer(ord, 4, new NumRenderer(MONEY, false));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            section("Market Watch", scroll(mkt)),
            section("Order Blotter", scroll(ord)));
        split.setResizeWeight(0.55);
        split.setBorder(null);
        split.setBackground(BG);
        root.add(split, BorderLayout.CENTER);

        statusLbl = label("Ready.", MUTED, UIF);
        tradeScreenLbl = label("Monitor -", MUTED, UIF);
        JPanel foot = card(new BorderLayout());
        foot.setBorder(pad(8, 16, 8, 16));
        foot.add(statusLbl, BorderLayout.WEST);
        foot.add(tradeScreenLbl, BorderLayout.EAST);
        root.add(foot, BorderLayout.SOUTH);

        f.setContentPane(root);
        return f;
    }

    JComponent buildTicket() {
        JPanel p = card(new FlowLayout(FlowLayout.LEFT, 12, 14));
        p.setBorder(pad(4, 14, 4, 14));

        symbolBox = new JComboBox<>(BASE.keySet().toArray(new String[0]));
        symbolBox.setBackground(PANEL2);
        symbolBox.setForeground(TEXT);
        symbolBox.setFont(MONO);
        symbolBox.addActionListener(e -> updateTicket());
        a11y(symbolBox, "Symbol", "Stock symbol to trade");

        buyBtn = new JToggleButton("BUY");
        sellBtn = new JToggleButton("SELL");
        ButtonGroup g = new ButtonGroup();
        g.add(buyBtn); g.add(sellBtn);
        buyBtn.setSelected(true);
        for (JToggleButton b : new JToggleButton[]{buyBtn, sellBtn}) {
            b.setFont(UIB);
            b.setFocusPainted(false);
            b.setOpaque(true);
            b.setBorderPainted(false);
            b.setBorder(pad(8, 18, 8, 18));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> recolorSides());
        }
        a11y(buyBtn, "Buy side", "Set order side to BUY");
        a11y(sellBtn, "Sell side", "Set order side to SELL");
        recolorSides();

        qtySpin = new JSpinner(new SpinnerNumberModel(100, 1, 1_000_000, 50));
        JComponent ed = qtySpin.getEditor();
        if (ed instanceof JSpinner.DefaultEditor) {
            JFormattedTextField tf = ((JSpinner.DefaultEditor) ed).getTextField();
            tf.setBackground(PANEL2); tf.setForeground(TEXT); tf.setCaretColor(TEXT);
            tf.setColumns(6); tf.setFont(MONO);
        }
        qtySpin.addChangeListener(e -> updateTicket());
        a11y(qtySpin, "Quantity", "Number of shares to trade");

        lastLbl = label("Last: --", TEXT, MONO);
        estLbl = label("Est. total: --", TEXT, MONO);

        JButton place = button("Place Order", ACCENT, Color.WHITE);
        place.addActionListener(e -> placeOrder());
        a11y(place, "Place Order", "Execute the order at the live market price");

        JButton adv = button("Advanced Order...", PANEL2, TEXT);
        adv.addActionListener(e -> showAdvancedOrder(false));
        a11y(adv, "Advanced Order", "Open the advanced order ticket (modeless) with conditional fields");

        JButton advM = button("Advanced (modal)...", PANEL2, TEXT);
        advM.addActionListener(e -> showAdvancedOrder(true));
        a11y(advM, "Advanced Order Modal", "Open the advanced order ticket as a modal dialog");

        p.add(label("Symbol", MUTED, UIF)); p.add(symbolBox);
        p.add(buyBtn); p.add(sellBtn);
        p.add(label("Qty", MUTED, UIF)); p.add(qtySpin);
        p.add(lastLbl); p.add(estLbl);
        p.add(place); p.add(adv); p.add(advM);
        a11y(p, "Trade ticket", "Order entry form");
        return p;
    }

    void recolorSides() {
        buyBtn.setBackground(buyBtn.isSelected() ? GREEN : PANEL2);
        buyBtn.setForeground(buyBtn.isSelected() ? Color.BLACK : MUTED);
        sellBtn.setBackground(sellBtn.isSelected() ? RED : PANEL2);
        sellBtn.setForeground(sellBtn.isSelected() ? Color.WHITE : MUTED);
    }

    void updateTicket() {
        if (symbolBox == null || current == null) return;
        String sym = (String) symbolBox.getSelectedItem();
        if (sym == null) return;
        double px = price(sym);
        int qty = (Integer) qtySpin.getValue();
        Holding h = current.find(sym);
        int own = h == null ? 0 : h.qty;
        lastLbl.setText("Last: " + MONEY.format(px) + "   Own: " + QTY.format(own));
        estLbl.setText("Est. total: " + MONEY.format(px * qty));
    }

    void placeOrder() {
        executeOrder(buyBtn.isSelected() ? Side.BUY : Side.SELL,
                (String) symbolBox.getSelectedItem(), (Integer) qtySpin.getValue());
    }

    /** Core buy/sell used by both the main ticket and the Advanced Order dialog.
     *  Returns true if the order filled (so a dialog knows to close). */
    boolean executeOrder(Side side, String sym, int qty) {
        double px = price(sym);
        if (qty <= 0) { error("Quantity must be positive."); return false; }

        if (backend != null) {  // persist through the REST API -> SQL, then re-read
            try {
                if (!backendBuySell(side, sym, qty, px)) return false;
                backendLoad(current);
                refreshAll();
                statusLbl.setText(side + " " + QTY.format(qty) + " " + sym + " @ "
                        + MONEY.format(px) + "  persisted to SQL.");
                statusLbl.setForeground(side == Side.BUY ? GREEN : RED);
                return true;
            } catch (Exception e) {
                error("Backend error: " + e.getMessage());
                return false;
            }
        }

        Holding h = current.find(sym);
        if (side == Side.SELL) {
            int own = h == null ? 0 : h.qty;
            if (own < qty) {
                error("Insufficient shares: tried to sell " + qty + " " + sym + " but only own " + own + ".");
                return false;
            }
            h.qty -= qty;
            if (h.qty == 0) current.holdings.remove(h);
        } else {
            if (h == null) { h = new Holding(sym, 0, 0); current.holdings.add(h); }
            double newQty = h.qty + qty;
            h.avg = (h.qty * h.avg + qty * px) / newQty;
            h.qty = (int) newQty;
        }
        current.orders.add(new Order(LocalDateTime.now().format(TS), sym, side, qty, px));
        refreshAll();
        statusLbl.setText(side + " " + QTY.format(qty) + " " + sym + " @ " + MONEY.format(px) + "  filled.");
        statusLbl.setForeground(side == Side.BUY ? GREEN : RED);
        return true;
    }

    // ---- Advanced Order: a cascading / progressive-disclosure flow -----------
    // A modeless dialog (modeless so JAB can drive it -- a MODAL dialog would
    // block pyjab.click on the action thread). Fields are revealed step by step:
    //   * choosing Limit / Stop-Limit reveals the price field(s)
    //   * "Show advanced options" reveals an extra panel
    //   * "Review order" reveals a summary + the otherwise-hidden "Confirm order"
    // This is a realistic multi-step interaction and a test target for scout's
    // new-window + show/hide handling.
    void showAdvancedOrder(boolean modal) {
        if (current == null) return;
        final String sym = (String) symbolBox.getSelectedItem();
        JDialog d = new JDialog(trade, "FinanApp Advanced Order", modal);
        d.setName("AdvancedOrderDialog");

        JPanel root = panel(new BorderLayout(0, 10));
        root.setBorder(pad(16, 18, 16, 18));
        JPanel form = card(new GridBagLayout());
        form.setBorder(linePad(BORDER, 14));
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2;
        g.anchor = GridBagConstraints.WEST; g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(5, 6, 5, 6);

        form.add(label("Advanced order ticket  -  " + sym, TEXT, UIB.deriveFont(15f)), g);

        JRadioButton sBuy = radio("Buy", true), sSell = radio("Sell", false);
        group(sBuy, sSell);
        a11y(sBuy, "Order side Buy", null); a11y(sSell, "Order side Sell", null);
        g.gridy++; form.add(row("Side", sBuy, sSell), g);

        JSpinner qty = new JSpinner(new SpinnerNumberModel(100, 1, 1_000_000, 50));
        styleSpinner(qty); a11y(qty, "Order quantity", null);
        g.gridy++; form.add(row("Quantity", qty), g);

        JRadioButton tMkt = radio("Market", true), tLmt = radio("Limit", false), tStp = radio("Stop-Limit", false);
        group(tMkt, tLmt, tStp);
        a11y(tMkt, "Order type Market", null); a11y(tLmt, "Order type Limit", null); a11y(tStp, "Order type Stop-Limit", null);
        g.gridy++; form.add(row("Order type", tMkt, tLmt, tStp), g);

        JTextField limitField = textField("0.00"); a11y(limitField, "Limit price", null);
        JComponent limitRow = row("Limit price", limitField); limitRow.setVisible(false);
        JTextField stopField = textField("0.00"); a11y(stopField, "Stop price", null);
        JComponent stopRow = row("Stop price", stopField); stopRow.setVisible(false);
        g.gridy++; form.add(limitRow, g);
        g.gridy++; form.add(stopRow, g);

        JCheckBox advChk = check("Show advanced options"); a11y(advChk, "Show advanced options", null);
        g.gridy++; form.add(advChk, g);
        JCheckBox aon = check("All-or-none"); a11y(aon, "All or none", null);
        JTextField iceberg = textField("0"); a11y(iceberg, "Iceberg quantity", null);
        JPanel advPanel = card(new GridLayout(0, 1, 0, 4));
        advPanel.setBorder(linePad(BORDER, 8));
        advPanel.add(aon);
        advPanel.add(row("Iceberg qty", iceberg));
        a11y(advPanel, "Advanced options", null);
        advPanel.setVisible(false);
        g.gridy++; form.add(advPanel, g);

        JTextArea summary = new JTextArea(5, 28);
        summary.setEditable(false); summary.setBackground(PANEL2); summary.setForeground(TEXT);
        summary.setFont(MONO); summary.setBorder(pad(6, 8, 6, 8));
        a11y(summary, "Order summary", null);
        JScrollPane summaryScroll = new JScrollPane(summary);
        summaryScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        summaryScroll.setVisible(false);
        g.gridy++; form.add(summaryScroll, g);

        root.add(form, BorderLayout.CENTER);

        JButton review = button("Review order", PANEL2, TEXT); a11y(review, "Review order", null);
        JButton confirm = button("Confirm order", ACCENT, Color.WHITE); a11y(confirm, "Confirm order", null);
        confirm.setVisible(false);
        JButton cancel = button("Cancel", PANEL2, MUTED); a11y(cancel, "Cancel order", null);
        JPanel buttons = panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancel); buttons.add(review); buttons.add(confirm);
        root.add(buttons, BorderLayout.SOUTH);

        Runnable updateTypeRows = () -> {
            limitRow.setVisible(tLmt.isSelected() || tStp.isSelected());
            stopRow.setVisible(tStp.isSelected());
            confirm.setVisible(false); summaryScroll.setVisible(false);  // inputs changed -> re-hide confirm
            d.revalidate(); d.repaint(); d.pack();
        };
        tMkt.addActionListener(e -> updateTypeRows.run());
        tLmt.addActionListener(e -> updateTypeRows.run());
        tStp.addActionListener(e -> updateTypeRows.run());
        advChk.addActionListener(e -> { advPanel.setVisible(advChk.isSelected()); d.revalidate(); d.repaint(); d.pack(); });

        review.addActionListener(e -> {
            int q = (Integer) qty.getValue();
            Side side = sBuy.isSelected() ? Side.BUY : Side.SELL;
            String type = tStp.isSelected() ? "Stop-Limit" : (tLmt.isSelected() ? "Limit" : "Market");
            StringBuilder sb = new StringBuilder();
            sb.append("Symbol     : ").append(sym).append('\n');
            sb.append("Side       : ").append(side).append('\n');
            sb.append("Quantity   : ").append(QTY.format(q)).append('\n');
            sb.append("Order type : ").append(type).append('\n');
            if (tLmt.isSelected() || tStp.isSelected()) sb.append("Limit price: ").append(limitField.getText()).append('\n');
            if (tStp.isSelected()) sb.append("Stop price : ").append(stopField.getText()).append('\n');
            if (advChk.isSelected()) sb.append("All-or-none: ").append(aon.isSelected() ? "yes" : "no").append('\n');
            sb.append("Est. total : ").append(MONEY.format(price(sym) * q));
            summary.setText(sb.toString());
            summaryScroll.setVisible(true);
            confirm.setVisible(true);
            d.revalidate(); d.repaint(); d.pack();
        });
        confirm.addActionListener(e -> {
            int q = (Integer) qty.getValue();
            Side side = sBuy.isSelected() ? Side.BUY : Side.SELL;
            if (executeOrder(side, sym, q)) d.dispose();
        });
        cancel.addActionListener(e -> d.dispose());

        d.setContentPane(root);
        d.pack();
        d.setLocationRelativeTo(trade);
        d.setVisible(true);
    }

    void error(String m) {
        statusLbl.setText(m);
        statusLbl.setForeground(RED);
        JOptionPane.showMessageDialog(trade, m, "Order rejected", JOptionPane.WARNING_MESSAGE);
    }

    // ===================================================================== Menus
    JMenuBar buildMenuBar(boolean isDashboard) {
        JMenuBar mb = new JMenuBar();
        mb.setBackground(HEADER);
        mb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));

        JMenu profile = new JMenu("Profile");
        a11y(profile, "Profile menu", null);
        JMenuItem sw = item("Switch Profile...", e -> switchProfile());
        JMenuItem ex = item("Exit", e -> System.exit(0));
        profile.add(sw); profile.addSeparator(); profile.add(ex);

        JMenu window = new JMenu("Window");
        a11y(window, "Window menu", null);
        window.add(item("Move This Window to Next Monitor",
            e -> moveToNext(isDashboard ? dash : trade)));
        window.add(item("Tile Both Windows on This Monitor",
            e -> tileHere(isDashboard ? dash : trade)));

        JMenu help = new JMenu("Help");
        a11y(help, "Help menu", null);
        help.add(item("About FinanApp", e -> about()));

        mb.add(profile);
        mb.add(window);
        mb.add(help);
        return mb;
    }

    JMenuItem item(String text, ActionListener al) {
        JMenuItem mi = new JMenuItem(text);
        mi.setBackground(PANEL);
        mi.setForeground(TEXT);
        mi.addActionListener(al);
        a11y(mi, text, null);
        return mi;
    }

    void about() {
        JOptionPane.showMessageDialog(activeFrame(),
            "FinanApp Stock Trader (Swing edition)\n\n" +
            "Multi-monitor sample / Java Access Bridge test target.\n" +
            "Dashboard + Trade Desk open across two monitors.\n\n" +
            "Profiles: James Smith (Aggressive Growth), Maria Wilson (Conservative Value).",
            "About FinanApp", JOptionPane.INFORMATION_MESSAGE);
    }

    void switchProfile() {
        if (timer != null) timer.stop();
        if (dash != null) dash.dispose();
        if (trade != null) trade.dispose();
        showSplash();
    }

    // ============================================================== Refresh logic
    void refreshAll() {
        if (holdModel != null) holdModel.fireTableDataChanged();
        if (marketModel != null) marketModel.fireTableDataChanged();
        if (orderModel != null) orderModel.fireTableDataChanged();
        updateTotals();
        updateTicket();
        updateScreenLabels();
    }

    void updateTotals() {
        if (current == null || dashValueLbl == null) return;
        double tot = 0, cost = 0;
        for (Holding h : current.holdings) { tot += h.qty * price(h.symbol); cost += h.qty * h.avg; }
        double pnl = tot - cost;
        double pct = cost == 0 ? 0 : pnl / cost;
        dashValueLbl.setText(MONEY.format(tot));
        dashPnlLbl.setText(MONEY_S.format(pnl) + "  (" + PCT_S.format(pct) + ")");
        dashPnlLbl.setForeground(pnl >= 0 ? GREEN : RED);
    }

    void updateScreenLabels() {
        int n = screens().length;
        if (dashScreenLbl != null && dash != null)
            dashScreenLbl.setText("Monitor " + (screenIndexOf(dash) + 1) + " of " + n);
        if (tradeScreenLbl != null && trade != null)
            tradeScreenLbl.setText("Trade Desk on monitor " + (screenIndexOf(trade) + 1) + " of " + n);
    }

    // ====================================================== Multi-monitor helpers
    static GraphicsDevice[] screens() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
    }
    static Rectangle usable(GraphicsDevice d) {
        GraphicsConfiguration gc = d.getDefaultConfiguration();
        Rectangle b = gc.getBounds();
        Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc); // exclude taskbar
        return new Rectangle(b.x + in.left, b.y + in.top,
            b.width - in.left - in.right, b.height - in.top - in.bottom);
    }
    static Rectangle inset(Rectangle r, double f) {
        int dx = (int) (r.width * f), dy = (int) (r.height * f);
        return new Rectangle(r.x + dx, r.y + dy, r.width - 2 * dx, r.height - 2 * dy);
    }
    // Returns [dashboardBounds, tradeBounds]. Two monitors -> one window each;
    // single monitor -> tiled left/right halves.
    static Rectangle[] computeLayout() {
        GraphicsDevice[] s = screens();
        if (s.length >= 2)
            return new Rectangle[]{ inset(usable(s[0]), 0.04), inset(usable(s[1]), 0.04) };
        Rectangle u = usable(s[0]);
        Rectangle left = new Rectangle(u.x, u.y, u.width / 2, u.height);
        Rectangle right = new Rectangle(u.x + u.width / 2, u.y, u.width - u.width / 2, u.height);
        return new Rectangle[]{ inset(left, 0.02), inset(right, 0.02) };
    }
    static int screenIndexOf(Window w) {
        GraphicsDevice[] s = screens();
        Point c = new Point(w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2);
        for (int i = 0; i < s.length; i++)
            if (s[i].getDefaultConfiguration().getBounds().contains(c)) return i;
        return 0;
    }
    void moveToNext(JFrame f) {
        GraphicsDevice[] s = screens();
        if (s.length < 2) { if (statusLbl != null) statusLbl.setText("Only one monitor detected."); return; }
        int next = (screenIndexOf(f) + 1) % s.length;
        f.setBounds(inset(usable(s[next]), 0.04));
        updateScreenLabels();
    }
    void tileHere(JFrame ref) {
        int idx = screenIndexOf(ref);
        Rectangle u = usable(screens()[idx]);
        if (dash != null)
            dash.setBounds(inset(new Rectangle(u.x, u.y, u.width / 2, u.height), 0.02));
        if (trade != null)
            trade.setBounds(inset(new Rectangle(u.x + u.width / 2, u.y, u.width - u.width / 2, u.height), 0.02));
        updateScreenLabels();
    }
    static void centerOnPrimary(Window w) {
        Rectangle u = usable(screens()[0]);
        w.setLocation(u.x + (u.width - w.getWidth()) / 2, u.y + (u.height - w.getHeight()) / 2);
    }
    Window activeFrame() {
        if (trade != null && trade.isActive()) return trade;
        if (dash != null && dash.isActive()) return dash;
        return dash != null ? dash : trade;
    }

    // ================================================================ Table models
    class HoldingsModel extends AbstractTableModel {
        final String[] cols = {"Symbol", "Qty", "Avg Px", "Last", "Mkt Value", "P&L $", "P&L %", "Weight %"};
        public int getRowCount() { return current == null ? 0 : current.holdings.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }
        double totalValue() {
            double t = 0;
            if (current != null) for (Holding h : current.holdings) t += h.qty * price(h.symbol);
            return t;
        }
        public Object getValueAt(int r, int c) {
            Holding h = current.holdings.get(r);
            double last = price(h.symbol);
            double mkt = h.qty * last;
            double pnl = (last - h.avg) * h.qty;
            double pnlPct = h.avg == 0 ? 0 : (last / h.avg - 1);
            double tot = totalValue();
            switch (c) {
                case 0: return h.symbol;
                case 1: return h.qty;
                case 2: return h.avg;
                case 3: return last;
                case 4: return mkt;
                case 5: return pnl;
                case 6: return pnlPct;
                case 7: return tot == 0 ? 0.0 : mkt / tot;
                default: return null;
            }
        }
        public Class<?> getColumnClass(int c) {
            return c == 0 ? String.class : (c == 1 ? Integer.class : Double.class);
        }
        public boolean isCellEditable(int r, int c) { return false; }
    }

    class MarketModel extends AbstractTableModel {
        final List<String> syms = new ArrayList<>(BASE.keySet());
        final String[] cols = {"Symbol", "Last", "Chg %"};
        public int getRowCount() { return syms.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }
        public Object getValueAt(int r, int c) {
            String sym = syms.get(r);
            double last = price(sym);
            switch (c) {
                case 0: return sym;
                case 1: return last;
                case 2: return last / BASE.get(sym) - 1;
                default: return null;
            }
        }
        public Class<?> getColumnClass(int c) { return c == 0 ? String.class : Double.class; }
        public boolean isCellEditable(int r, int c) { return false; }
    }

    class OrderModel extends AbstractTableModel {
        final String[] cols = {"Time", "Symbol", "Side", "Qty", "Price"};
        public int getRowCount() { return current == null ? 0 : current.orders.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName(int c) { return cols[c]; }
        public Object getValueAt(int r, int c) {
            Order ord = current.orders.get(current.orders.size() - 1 - r); // newest first
            switch (c) {
                case 0: return ord.time;
                case 1: return ord.symbol;
                case 2: return ord.side.name();
                case 3: return ord.qty;
                case 4: return ord.price;
                default: return null;
            }
        }
        public Class<?> getColumnClass(int c) { return c == 3 ? Integer.class : (c == 4 ? Double.class : String.class); }
        public boolean isCellEditable(int r, int c) { return false; }
    }

    // =================================================================== Renderers
    class NumRenderer extends DefaultTableCellRenderer {
        final DecimalFormat fmt; final boolean color;
        NumRenderer(DecimalFormat f, boolean color) { this.fmt = f; this.color = color; setHorizontalAlignment(RIGHT); }
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            double d = v instanceof Number ? ((Number) v).doubleValue() : 0;
            setText(v == null ? "" : fmt.format(d));
            setBackground(sel ? SELBG : PANEL);
            setForeground(color ? (d >= 0 ? GREEN : RED) : TEXT);
            setFont(MONO);
            return this;
        }
    }
    class TextRenderer extends DefaultTableCellRenderer {
        TextRenderer(int align) { setHorizontalAlignment(align); }
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            setBackground(sel ? SELBG : PANEL);
            setForeground(TEXT);
            setFont(MONO);
            return this;
        }
    }
    class SideRenderer extends DefaultTableCellRenderer {
        SideRenderer() { setHorizontalAlignment(CENTER); }
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            String s = String.valueOf(v);
            setBackground(sel ? SELBG : PANEL);
            setForeground("BUY".equals(s) ? GREEN : RED);
            setFont(UIB);
            return this;
        }
    }

    // ==================================================================== UI utils
    static JPanel panel(LayoutManager lm) { JPanel p = new JPanel(lm); p.setBackground(BG); return p; }
    static JPanel card(LayoutManager lm) { JPanel p = new JPanel(lm); p.setBackground(PANEL); return p; }
    static Border pad(int t, int l, int b, int r) { return BorderFactory.createEmptyBorder(t, l, b, r); }
    static Border linePad(Color c, int p) {
        return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(c), pad(p, p, p, p));
    }
    static JLabel label(String t, Color fg, Font f) { JLabel l = new JLabel(t); l.setForeground(fg); l.setFont(f); return l; }
    static JButton button(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setOpaque(true); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setBackground(bg); b.setForeground(fg); b.setFont(UIB);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(pad(8, 16, 8, 16));
        return b;
    }
    static JRadioButton radio(String text, boolean selected) {
        JRadioButton r = new JRadioButton(text, selected);
        r.setOpaque(false); r.setForeground(TEXT); r.setFont(UIF); r.setFocusPainted(false);
        return r;
    }
    static JCheckBox check(String text) {
        JCheckBox c = new JCheckBox(text);
        c.setOpaque(false); c.setForeground(TEXT); c.setFont(UIF); c.setFocusPainted(false);
        return c;
    }
    static void group(AbstractButton... bs) {
        ButtonGroup grp = new ButtonGroup();
        for (AbstractButton b : bs) grp.add(b);
    }
    static JTextField textField(String text) {
        JTextField t = new JTextField(text, 8);
        t.setBackground(PANEL2); t.setForeground(TEXT); t.setCaretColor(TEXT); t.setFont(MONO);
        t.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), pad(3, 5, 3, 5)));
        return t;
    }
    static void styleSpinner(JSpinner sp) {
        if (sp.getEditor() instanceof JSpinner.DefaultEditor) {
            JFormattedTextField tf = ((JSpinner.DefaultEditor) sp.getEditor()).getTextField();
            tf.setBackground(PANEL2); tf.setForeground(TEXT); tf.setCaretColor(TEXT); tf.setColumns(6); tf.setFont(MONO);
        }
    }
    static JComponent row(String labelText, JComponent... comps) {
        JPanel p = card(new FlowLayout(FlowLayout.LEFT, 8, 2));
        p.add(label(labelText, MUTED, UIF));
        for (JComponent c : comps) p.add(c);
        return p;
    }
    static <T extends JComponent> T a11y(T c, String name, String desc) {
        c.setName(name);
        c.getAccessibleContext().setAccessibleName(name);
        if (desc != null) c.getAccessibleContext().setAccessibleDescription(desc);
        return c;
    }
    static JScrollPane scroll(JComponent view) {
        JScrollPane sp = new JScrollPane(view);
        sp.getViewport().setBackground(PANEL);
        sp.setBackground(PANEL);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        return sp;
    }
    static JComponent section(String title, JComponent body) {
        JPanel p = panel(new BorderLayout(0, 6));
        p.setBorder(pad(8, 12, 10, 12));
        p.add(label(title, MUTED, UIB), BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }
    static void styleTable(JTable t) {
        t.setBackground(PANEL);
        t.setForeground(TEXT);
        t.setGridColor(BORDER);
        t.setRowHeight(24);
        t.setShowGrid(true);
        t.setFont(MONO);
        t.setSelectionBackground(SELBG);
        t.setSelectionForeground(TEXT);
        t.setFillsViewportHeight(true);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        DefaultTableCellRenderer hr = new DefaultTableCellRenderer();
        hr.setBackground(HEADER);
        hr.setForeground(MUTED);
        hr.setFont(UIB);
        hr.setHorizontalAlignment(SwingConstants.LEFT);
        hr.setBorder(pad(6, 8, 6, 8));
        JTableHeader header = t.getTableHeader();
        header.setDefaultRenderer(hr);
        header.setBackground(HEADER);
        header.setReorderingAllowed(false);
    }
    static void setRenderer(JTable t, int col, TableCellRenderer r) {
        t.getColumnModel().getColumn(col).setCellRenderer(r);
    }

    // ============================================================ REST backend
    // With --backend <url> (and the FinanApp Spring Boot app running), the app
    // reads holdings/orders and executes trades through the existing REST API,
    // which persists to SQL Server -- instead of the in-memory mock. Falls back
    // to in-memory if the backend is absent/unreachable, so it still runs solo.

    void backendLoad(Profile p) {
        Map<String, Object> h = asObj(backend.get("/api/portfolio/" + p.code + "/holdings"));
        p.holdings.clear();
        for (Object o : asList(h.get("positions"))) {
            Map<String, Object> r = asObj(o);
            p.holdings.add(new Holding(asS(r.get("symbol")), asI(r.get("quantity")), asD(r.get("avgPrice"))));
        }
        Map<String, Object> od = asObj(backend.get("/api/portfolio/" + p.code + "/orders?size=100"));
        p.orders.clear();
        List<Object> ords = asList(od.get("orders"));
        for (int k = ords.size() - 1; k >= 0; k--) {  // server is newest-first; keep oldest-first
            Map<String, Object> r = asObj(ords.get(k));
            String t = asS(r.get("createdAt"));
            t = (t == null) ? "" : t.replace('T', ' ');
            if (t.length() >= 16) t = t.substring(5, 16);  // MM-dd HH:mm
            Side side = "SELL".equalsIgnoreCase(asS(r.get("orderType"))) ? Side.SELL : Side.BUY;
            p.orders.add(new Order(t, asS(r.get("symbol")), side, asI(r.get("quantity")), asD(r.get("price"))));
        }
    }

    void backendPrices() {
        Map<String, Object> m = asObj(backend.get("/api/market/prices"));
        for (Map.Entry<String, Object> e : m.entrySet()) prices.put(e.getKey(), asD(e.getValue()));
    }

    boolean backendBuySell(Side side, String sym, int qty, double px) {
        String path = "/api/trade/" + current.code + "/" + (side == Side.BUY ? "buy" : "sell")
                + "?symbol=" + sym + "&quantity=" + qty
                + "&price=" + String.format(java.util.Locale.US, "%.2f", px);
        Object resp = backend.post(path);
        Map<String, Object> r = (resp instanceof Map) ? asObj(resp) : null;
        if (r != null && "REJECTED".equals(asS(r.get("status")))) {
            error(asS(r.getOrDefault("error", "Order rejected by backend")));
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asObj(Object o) { return (Map<String, Object>) o; }
    @SuppressWarnings("unchecked")
    static List<Object> asList(Object o) { return (List<Object>) o; }
    static double asD(Object o) { return o instanceof Number ? ((Number) o).doubleValue() : Double.parseDouble(String.valueOf(o)); }
    static int asI(Object o) { return (int) Math.round(asD(o)); }
    static String asS(Object o) { return o == null ? null : String.valueOf(o); }

    /** Tiny HTTP client (JDK HttpClient) for the FinanApp REST API. */
    static final class Rest {
        final String base;
        final java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        Rest(String base) { this.base = base.replaceAll("/+$", ""); }
        Object get(String path) { return send("GET", path); }
        Object post(String path) { return send("POST", path); }
        boolean ping() { try { get("/api/market/prices"); return true; } catch (Exception e) { return false; } }
        private Object send(String method, String path) {
            try {
                java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create(base + path)).timeout(java.time.Duration.ofSeconds(8));
                if ("POST".equals(method)) b.POST(java.net.http.HttpRequest.BodyPublishers.noBody());
                else b.GET();
                java.net.http.HttpResponse<String> resp = http.send(
                        b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 != 2)
                    throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
                String t = resp.body();
                return (t == null || t.isBlank()) ? null : Json.parse(t);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Minimal recursive-descent JSON parser (object/array/string/number/bool/null). */
    static final class Json {
        private final String s; private int i;
        private Json(String s) { this.s = s; }
        static Object parse(String s) { return new Json(s).value(); }
        private Object value() {
            ws();
            char c = s.charAt(i);
            switch (c) {
                case '{': return obj();
                case '[': return arr();
                case '"': return str();
                case 't': i += 4; return Boolean.TRUE;
                case 'f': i += 5; return Boolean.FALSE;
                case 'n': i += 4; return null;
                default: return num();
            }
        }
        private Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                String k = str();
                ws(); i++;  // ':'
                m.put(k, value());
                ws();
                if (s.charAt(i++) == '}') break;  // else ','
            }
            return m;
        }
        private List<Object> arr() {
            List<Object> a = new ArrayList<>();
            i++; ws();
            if (s.charAt(i) == ']') { i++; return a; }
            while (true) {
                a.add(value());
                ws();
                if (s.charAt(i++) == ']') break;  // else ','
            }
            return a;
        }
        private String str() {
            StringBuilder b = new StringBuilder();
            i++;  // opening quote
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': b.append('\n'); break;
                        case 't': b.append('\t'); break;
                        case 'r': b.append('\r'); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                        default: b.append(e);
                    }
                } else {
                    b.append(c);
                }
            }
            return b.toString();
        }
        private Double num() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(start, i));
        }
        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    }

    // ======================================================================== main
    // First non-flag arg (or -Dprofile=) = JSMITH | MWILSON: skip the sign-in
    // splash and open that trader's two-monitor desktop directly.
    // --backend <url> (or -Dbackend=) routes reads/trades through the FinanApp
    // REST API/SQL; without it the app runs on in-memory mock data.
    public static void main(String[] args) {
        applyDarkUIDefaults();
        String pre = System.getProperty("profile", "");
        String backendUrl = System.getProperty("backend", "");
        for (int k = 0; k < args.length; k++) {
            String a = args[k];
            if ("--backend".equals(a) && k + 1 < args.length) backendUrl = args[++k];
            else if (a.startsWith("--backend=")) backendUrl = a.substring("--backend=".length());
            else if (!a.startsWith("--") && pre.isEmpty()) pre = a;
        }
        final String preF = pre, backendF = backendUrl;
        SwingUtilities.invokeLater(() -> {
            SwingTraderApp app = new SwingTraderApp();
            if (backendF != null && !backendF.isBlank()) {
                Rest r = new Rest(backendF);
                if (r.ping()) { app.backend = r; System.out.println("[backend] using " + r.base); }
                else System.err.println("[backend] " + backendF + " unreachable -> in-memory mode");
            }
            app.seed();
            app.tickPrices();
            Profile p = (preF == null || preF.isBlank()) ? null : app.profiles.get(preF.trim().toUpperCase());
            if (p != null) app.launchDesktop(p);
            else app.showSplash();
        });
    }

    // Default LAF is cross-platform Metal, which honours these color keys
    // (unlike the native Windows LAF), giving a consistent dark theme.
    static void applyDarkUIDefaults() {
        UIManager.put("control", PANEL);
        UIManager.put("Panel.background", BG);
        UIManager.put("MenuBar.background", HEADER);
        UIManager.put("MenuBar.foreground", TEXT);
        UIManager.put("Menu.background", PANEL);
        UIManager.put("Menu.foreground", TEXT);
        UIManager.put("Menu.selectionBackground", SELBG);
        UIManager.put("Menu.selectionForeground", TEXT);
        UIManager.put("MenuItem.background", PANEL);
        UIManager.put("MenuItem.foreground", TEXT);
        UIManager.put("MenuItem.selectionBackground", SELBG);
        UIManager.put("MenuItem.selectionForeground", TEXT);
        UIManager.put("PopupMenu.background", PANEL);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("Separator.foreground", BORDER);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("ComboBox.background", PANEL2);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", SELBG);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("TextField.background", PANEL2);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("ScrollPane.background", PANEL);
        UIManager.put("Viewport.background", PANEL);
        UIManager.put("TableHeader.background", HEADER);
        UIManager.put("TableHeader.foreground", MUTED);
        UIManager.put("ToolTip.background", PANEL);
        UIManager.put("ToolTip.foreground", TEXT);
    }
}
