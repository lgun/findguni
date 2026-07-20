package com.findguni.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ErrorViewController implements ErrorController {
    @RequestMapping("/error")
    public String error(HttpServletRequest request, Model model) {
        Object rawStatus = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = rawStatus == null ? 500 : Integer.parseInt(rawStatus.toString());
        model.addAttribute("status", status);
        model.addAttribute("message", status == 404 ? "요청한 페이지를 찾을 수 없습니다." : "잠시 후 다시 시도해 주세요.");
        return status == 404 ? "error/404" : "error/500";
    }
}
