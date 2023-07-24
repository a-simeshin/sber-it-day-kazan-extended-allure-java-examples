package ru.sber.it.day;

import io.qameta.allure.Step;
import io.qameta.allure.springweb.AllureRestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Import(RestApplication.RestApplicationController.class)
@SpringBootTest(
        classes = {
                RestApplication.class,
                PingSmokeWithWebInterceptorTest.RestOperationsTestConfiguration.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class PingSmokeWithWebInterceptorTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Step("[GET] /ping")
    private String getPingStep() {
        return restTemplate.getForObject("/ping1", String.class);
    }

    @Test
    @DisplayName("PING-PONG Сервис поднялся и работает")
    void smokeTest() {
        var response = getPingStep();
        assertThat("API /ping вернул корректные данные", response, is("pong"));
    }

    @Configuration
    static class RestOperationsTestConfiguration {

        @Bean
        RestTemplateCustomizer customClientHttpRequestInterceptor() {
            return restTemplate -> {
                var first = new HttpComponentsClientHttpRequestFactory();
                var buffered = new BufferingClientHttpRequestFactory(first);
                restTemplate.setRequestFactory(buffered);
                restTemplate.getInterceptors().add(new AllureRestTemplate());
            };
        }
    }

}
