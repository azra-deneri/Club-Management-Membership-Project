package com.iscms.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Root controller — redirects the bare URL to the login screen.
// The /login mapping itself lives on LoginController, which owns the
// full auth flow (GET to render, POST to authenticate).
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }
}
