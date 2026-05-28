package com.iscms.web.config;

import com.iscms.dao.MemberDAO;
import com.iscms.dao.MemberDAOImpl;
import com.iscms.dao.MembershipDAO;
import com.iscms.dao.MembershipDAOImpl;
import com.iscms.service.AuthService;
import com.iscms.service.EventService;
import com.iscms.service.MemberService;
import com.iscms.service.PTService;
import com.iscms.service.ReportService;
import com.iscms.service.ManagerService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Wires the existing Project 1 service + DAO layer into the Spring container.
// Services and DAOs use their no-arg constructors — no refactor needed in Project 1 code.
@Configuration
public class SpringConfig {

    @Bean
    public AuthService authService() {
        return new AuthService();
    }

    @Bean
    public MemberService memberService() {
        return new MemberService();
    }

    @Bean
    public ManagerService managerService() {
        return new ManagerService();
    }

    @Bean
    public EventService eventService() {
        return new EventService();
    }

    @Bean
    public PTService ptService() {
        return new PTService();
    }


    @Bean
    public ReportService reportService() {
        return new ReportService();
    }
}