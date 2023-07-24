package ru.sber.it.day;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class RestApplication {

    @SuppressWarnings("resource")
    public static void main(String[] args) {
        SpringApplication.run(RestApplication.class, args);
    }

    @RestController
    static class RestApplicationController {
        @GetMapping("/ping")
        String ping() {
            return "pong";
        }
    }
}
