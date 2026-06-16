package com.finanapp.controller;

import com.finanapp.model.TradeForm;
import com.finanapp.model.OrderType;
import com.finanapp.service.InsufficientSharesException;
import com.finanapp.service.TradingService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/trade")
public class OrderController {

    private final TradingService tradingService;

    public OrderController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    private void addCommonAttributes(Model model, HttpSession session) {
        model.addAttribute("orderTypes", OrderType.values());
        String profile = PortfolioController.getProfile(session);
        PortfolioController.addProfileAttributes(model, profile);
    }

    @GetMapping
    public String showTradeForm(Model model, HttpSession session) {
        if (session.getAttribute("profile") == null) {
            return "redirect:/login";
        }
        model.addAttribute("tradeForm", new TradeForm());
        addCommonAttributes(model, session);
        return "trade";
    }

    @PostMapping("/buy")
    public String executeBuy(@Valid TradeForm form, BindingResult result,
                             Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            addCommonAttributes(model, session);
            return "trade";
        }

        String profile = PortfolioController.getProfile(session);
        tradingService.executeBuy(profile, form.getSymbol(), form.getQuantity(), form.getPrice());
        redirectAttributes.addFlashAttribute("message",
                "Bought " + form.getQuantity() + " shares of " + form.getSymbol());
        return "redirect:/portfolio";
    }

    @PostMapping("/sell")
    public String executeSell(@Valid TradeForm form, BindingResult result,
                              Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            addCommonAttributes(model, session);
            return "trade";
        }

        String profile = PortfolioController.getProfile(session);
        try {
            tradingService.executeSell(profile, form.getSymbol(), form.getQuantity(), form.getPrice());
            redirectAttributes.addFlashAttribute("message",
                    "Sold " + form.getQuantity() + " shares of " + form.getSymbol());
        } catch (InsufficientSharesException e) {
            addCommonAttributes(model, session);
            model.addAttribute("error", e.getMessage());
            return "trade";
        }

        return "redirect:/portfolio";
    }
}
