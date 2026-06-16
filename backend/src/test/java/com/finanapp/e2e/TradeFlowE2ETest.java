package com.finanapp.e2e;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Playwright E2E tests for Buy and Sell order flows.
 * Requires the Spring Boot app running on localhost:9296 and SQL Server (finanapp) available.
 * Seed data must be loaded: sqlcmd -S localhost -E -d finanapp -i sql/seed-jsmith.sql
 *
 * Run with: mvn test -Dtest="com.finanapp.e2e.TradeFlowE2ETest"
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TradeFlowE2ETest {

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;

    private static final String BASE_URL = "http://localhost:9296";

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void createContext() {
        context = browser.newContext();
        page = context.newPage();
        // Login as JSMITH
        page.navigate(BASE_URL + "/profile/JSMITH");
        page.waitForURL("**/portfolio**");
    }

    @AfterEach
    void closeContext() {
        if (context != null) context.close();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    void testBuyOrderFlow() {
        // Navigate to trade page
        page.navigate(BASE_URL + "/trade");
        assertTrue(page.title().contains("FinanApp"), "Should be on FinanApp trade page");

        // Verify trade form elements exist
        assertTrue(page.locator("#symbol").isVisible(), "Symbol field should be visible");
        assertTrue(page.locator("#quantity").isVisible(), "Quantity field should be visible");
        assertTrue(page.locator("#price").isVisible(), "Price field should be visible");
        assertTrue(page.locator("#btnBuy").isVisible(), "Buy toggle should be visible");
        assertTrue(page.locator("#btnSell").isVisible(), "Sell toggle should be visible");
        assertTrue(page.locator("#submitBtn").isVisible(), "Submit button should be visible");

        // Fill in values and verify estimated total updates
        page.fill("#symbol", "AMD");
        page.fill("#quantity", "100");
        page.fill("#price", "165.50");
        page.waitForTimeout(500);
        String estTotal = page.locator("#estTotal").textContent();
        assertTrue(estTotal.contains("16,550"), "Estimated total should show ~$16,550");

        // Execute buy via REST API
        var buyResponse = page.request().post(BASE_URL + "/api/trade/JSMITH/buy?symbol=AMD&quantity=100&price=165.50");
        String buyBody = buyResponse.text();
        System.out.println("BUY STATUS: " + buyResponse.status() + " BODY: " + buyBody);
        assertTrue(buyResponse.ok(), "Buy API should return 200, got " + buyResponse.status() + ": " + buyBody);
        assertTrue(buyBody.contains("FILLED"), "API should return FILLED status");
        assertTrue(buyBody.contains("AMD"), "API should echo AMD symbol");

        // Navigate to portfolio and verify AMD appears
        page.navigate(BASE_URL + "/portfolio");
        page.waitForTimeout(1000);
        String portfolioContent = page.content();
        assertTrue(portfolioContent.contains("AMD"), "Portfolio should contain AMD after buy");

        Locator amdRow = page.locator("table tbody tr", new Page.LocatorOptions().setHasText("AMD"));
        assertTrue(amdRow.count() > 0, "AMD row should exist in holdings table");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void testSellOrderFlow() {
        // Navigate to trade page and verify sell toggle
        page.navigate(BASE_URL + "/trade");
        page.click("#btnSell");
        String btnText = page.locator("#submitBtn").textContent();
        assertTrue(btnText.contains("Sell"), "Button should say Place Sell Order");
        String sideLabel = page.locator("#sideLabel").textContent();
        assertTrue(sideLabel.contains("Sell"), "Side label should say Market Sell");

        // Execute sell via REST API (JSMITH has 800 AAPL from seed)
        var sellResponse = page.request().post(BASE_URL + "/api/trade/JSMITH/sell?symbol=AAPL&quantity=100&price=230.00");
        assertTrue(sellResponse.ok(), "Sell API should return 200");
        String sellBody = sellResponse.text();
        assertTrue(sellBody.contains("FILLED"), "API should return FILLED status");

        // Navigate to portfolio and verify
        page.navigate(BASE_URL + "/portfolio");
        page.waitForTimeout(1000);
        String content = page.content();

        // AAPL should still be there (partial sell)
        Locator aaplRow = page.locator("table tbody tr", new Page.LocatorOptions().setHasText("AAPL"));
        assertTrue(aaplRow.count() > 0, "AAPL row should still exist");

        // Order history should show SELL
        assertTrue(content.contains("SELL"), "Order history should show SELL entry");
    }
}
