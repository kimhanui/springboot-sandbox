package com.mani.springbootoauth2.controller;

import com.mani.springbootoauth2.config.auth.dto.SessionUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpSession;

@Slf4j
@RequiredArgsConstructor
@Controller
public class IndexController {

    private final HttpSession httpSession;

    @GetMapping("/")
    public String index(Model model) {
        log.info("IndexController");
        SessionUser user = (SessionUser) httpSession.getAttribute("user");

        if(user != null){
            log.info("user가 null이 아님:name="+user.getName());
            model.addAttribute("name", user.getName());//{{username}}에 넘겨주는건 이 값이 아닌듯?
        }
        return "index";
    }
}