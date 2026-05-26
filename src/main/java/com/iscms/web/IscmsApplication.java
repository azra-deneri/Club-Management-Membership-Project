package com.iscms.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Spring Boot entry point for the ISC-MS web layer.
//
// The Swing desktop application (com.iscms.Main) remains the standalone
// thick-client entry point and is unaffected by this class. Both can
// coexist in the same module — they are simply two different mains.
//
// Run from terminal:    mvn spring-boot:run
// Run from IntelliJ:    right-click IscmsApplication → Run
// Browser:              http://localhost:8080
@SpringBootApplication
public class IscmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(IscmsApplication.class, args);
    }
}