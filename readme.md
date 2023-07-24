# sber-it-day-kazan-extended-allure-java-examples

## Кратко и по поинтам

### Что тестируем

Тестируемый микросервис
```java
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

```

### Недостаточность данных про HTTP запрос и ответ

Напишем тест с поднятием контекста приложения и контроллера

```java
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
```

Пока что он работает корректно, генерируется корректный и красивый Allure отчет.
Попробуем сломать тест, сделав запрос на неактуальный эндпоинт: `/ping1`

```java
@Import(RestApplication.RestApplicationController.class)
@SpringBootTest(classes = RestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PingSmokeTest {

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

}
```

В таком случае при возникновении проблем в Allure будет информация о падении теста, но ее крайне 
недостаточно для комфортной дальнейшей работы с проблемой, нужны:
- адрес
- эндпоинт
- метод
- хедеры
- любые чувствительные данные, если есть (cookies, bearer token, oauth2, basic auth, etc.)
- тело запроса, если есть

Можно исправить ситуацию с помощью `allure-spring-web`
```xml
<dependency>
    <groupId>io.qameta.allure</groupId>
    <artifactId>allure-spring-web</artifactId>
    <version>${allure.version}</version>
</dependency>
```

Добавили, а что теперь с ним делать? Добавить конфигурацию, это же все-таки Spring-boot. 
Тогда нас тест будет выглядеть как:

```java
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
```

Что поменялось? => В методе `getPingStep` добавять 2 аттачмента, которые полностью описывают HTTP запрос и ответ.

### Недостаточность данных про Assert