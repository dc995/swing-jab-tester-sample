package com.finanapp.controller;

import com.finanapp.repository.HoldingRepository;
import com.finanapp.repository.OrderRepository;
import com.finanapp.service.MarketDataService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class PortfolioController {

    static final String DEFAULT_PROFILE = "JSMITH";
    static final List<String[]> PROFILES = List.of(
            new String[]{"JSMITH", "James Smith", "Aggressive Growth"},
            new String[]{"MWILSON", "Maria Wilson", "Conservative Value"}
    );

    private final HoldingRepository holdingRepository;
    private final OrderRepository orderRepository;
    private final MarketDataService marketDataService;

    public PortfolioController(HoldingRepository holdingRepository, OrderRepository orderRepository, MarketDataService marketDataService) {
        this.holdingRepository = holdingRepository;
        this.orderRepository = orderRepository;
        this.marketDataService = marketDataService;
    }

    @GetMapping("/portfolio")
    public String showPortfolio(Model model, HttpSession session) {
        String profile = getProfile(session);
        if (session.getAttribute("profile") == null) {
            return "redirect:/login";
        }
        model.addAttribute("holdings", holdingRepository.findByProfile(profile));
        model.addAttribute("orders", orderRepository.findByProfileOrderByCreatedAtDesc(profile));
        model.addAttribute("marketPrices", marketDataService.getAllPrices());
        addProfileAttributes(model, profile);
        return "portfolio";
    }

    @GetMapping("/profile/{id}")
    public String switchProfile(@PathVariable String id, HttpSession session, RedirectAttributes ra) {
        String profileId = id.toUpperCase();
        session.setAttribute("profile", profileId);
        String name = PROFILES.stream()
                .filter(p -> p[0].equals(profileId))
                .map(p -> p[1])
                .findFirst().orElse(profileId);
        ra.addFlashAttribute("message", "Welcome, " + name);
        return "redirect:/portfolio";
    }

    @GetMapping("/")
    public String home(HttpSession session) {
        // If no profile selected yet, show login/profile selection
        if (session.getAttribute("profile") == null) {
            return "redirect:/login";
        }
        return "redirect:/portfolio";
    }

    @GetMapping("/login")
    public String showLogin(Model model, HttpSession session) {
        session.removeAttribute("profile");
        model.addAttribute("profiles", PROFILES);
        return "login";
    }

    static String getProfile(HttpSession session) {
        Object p = session.getAttribute("profile");
        return p != null ? p.toString() : DEFAULT_PROFILE;
    }

    static void addProfileAttributes(Model model, String profileId) {
        String[] info = PROFILES.stream()
                .filter(p -> p[0].equals(profileId))
                .findFirst()
                .orElse(new String[]{profileId, profileId, ""});
        String name = info[1];
        String[] parts = name.split(" ");
        String initials = parts.length >= 2
                ? ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0))
                : name.substring(0, Math.min(2, name.length()));
        model.addAttribute("activeProfile", profileId);
        model.addAttribute("activeProfileName", name);
        model.addAttribute("activeProfileStyle", info[2]);
        model.addAttribute("activeProfileInitials", initials);
    }
}
