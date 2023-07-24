package ru.sber.it.day;

import io.qameta.allure.Step;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Import(RestApplication.RestApplicationController.class)
@SpringBootTest(classes = RestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PingSmokeTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Step("[GET] /ping")
    private String getPingStep() {
        return restTemplate.getForObject("/ping", String.class);
    }

    @Test
    @DisplayName("PING-PONG Сервис поднялся и работает")
    void smokeTest() {
        var response = getPingStep();
        assertThat("API /ping вернул корректные данные", response, is("pong"));
    }

}
