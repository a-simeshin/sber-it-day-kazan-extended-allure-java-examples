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

### Недостаточность данных, описывающих HTTP запрос и ответ

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

Что поменялось? => В методе `getPingStep` появились 2 аттачмента, которые полностью описывают HTTP запрос и ответ.

### Недостаточность данных про Assert

Помимо HTTP запроса и ответа в тесте есть еще 1 логический шаг - проверка результата.

```java
    assertThat("API /ping вернул корректные данные", response, is("pong"));
```

Попробуем написать свое решение для создания аттачментов с полным набором данных в Allure. 
Для этого можно использовать `Aspect` - новых зависимостей добавлять не нужно, Allure уже работает на аспектах.

Первый шаг, нужно понять, какой метод нас интересует?
Статический `assertThat` - метод из класса ``org.hamcrest.MatcherAssert``, но там их 3, нас интересует тот, который 
принимает на вход 3 параметра, именно он осуществляет проверку:

```java
    public static <T> void assertThat(String reason, T actual, Matcher<? super T> matcher) {
        if (!matcher.matches(actual)) {
            Description description = new StringDescription();
            description.appendText(reason)
                       .appendText(System.lineSeparator())
                       .appendText("Expected: ")
                       .appendDescriptionOf(matcher)
                       .appendText(System.lineSeparator())
                       .appendText("     but: ");
            matcher.describeMismatch(actual, description);
            
            throw new AssertionError(description.toString());
        }
    }
```

Было бы хорошо положить в Allure отчет:
1. Почему мы это проверяем `String reason`
2. Что мы проверяем `T actual`
3. Как мы это првоеряем `Matcher<? super T> matcher`

Напишем код ассерта:

```java
package ru.sber.it.day;

import com.github.underscore.U;
import io.qameta.allure.Allure;
import io.qameta.allure.attachment.AttachmentData;
import io.qameta.allure.attachment.AttachmentProcessor;
import io.qameta.allure.attachment.DefaultAttachmentProcessor;
import io.qameta.allure.attachment.FreemarkerAttachmentRenderer;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StepResult;
import io.qameta.allure.util.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.UUID;

import static io.qameta.allure.util.ResultsUtils.getStatus;

@Slf4j
@Aspect
public class AllureHamcrestAspect {

    @Pointcut("execution(void org.hamcrest.MatcherAssert.*(..))")
    public void initAssertThat() {
    }

    @SuppressWarnings("rawtypes")
    @Before("initAssertThat()")
    public void setStart(final JoinPoint joinPoint) {
        // Это нужный нам метод?
        if (joinPoint.getArgs().length != 3) return;
        if (Allure.getLifecycle().getCurrentTestCase().isEmpty()) return;
        // Достаем максимум полезной ифнормации из JointPoint
        final String reason = ObjectUtils.toString(joinPoint.getArgs()[0]);
        final Object actual = joinPoint.getArgs()[1];
        final String actualString = ObjectUtils.toString(actual);
        final String formatted = formatActual(actualString);
        final Matcher matcher = (Matcher) joinPoint.getArgs()[2];
        final String matcherDescription = getMatcherDescription(matcher, formatted);
        final String stepName = getStepName(matcher, reason);
        // Программно создаем Allure Step
        var stepResult = new StepResult().setName(stepName).setDescription("Hamcrest assert");
        Allure.getLifecycle().startStep(UUID.randomUUID().toString(),stepResult);
        // Программно создаем Allure Attachment из Freemarker шаблона
        final AttachmentProcessor<AttachmentData> processor = new DefaultAttachmentProcessor();
        processor.addAttachment(
                AllureMatcherDto.builder() // Вот эту сущность надо написать
                        .reason(reason).actual(formatted).expecting(matcherDescription)
                        .build(),
                new FreemarkerAttachmentRenderer("hamcrest-matcher.ftl"));
    }

    @AfterThrowing(pointcut = "initAssertThat()", throwing = "e")
    public void stepFailed(final Throwable e) {
        if (Allure.getLifecycle().getCurrentTestCase().isEmpty()) return;
        Allure.getLifecycle().updateStep(s -> s.setStatus(getStatus(e).orElse(Status.FAILED)));
        Allure.getLifecycle().stopStep();
    }

    @AfterReturning(pointcut = "initAssertThat()")
    public void stepStop() {
        if (Allure.getLifecycle().getCurrentTestCase().isEmpty()) return;
        Allure.getLifecycle().updateStep(s -> s.setStatus(Status.PASSED));
        Allure.getLifecycle().stopStep();
    }

    @SuppressWarnings("rawtypes")
    private String getMatcherDescription(Matcher matcher, Object actual) {
        final Description matcherDescription = new StringDescription();
        matcherDescription.appendDescriptionOf(matcher);

        final String matcherString =
                matcherDescription.toString()
                        .trim()
                        .replace(" and ", "\n and ")
                        .replace(" or ", "\n or ");

        final Description description = new StringDescription();
        description.appendText("\n");
        description.appendText("    Expecting:");
        description.appendText("\n");
        description.appendText(matcherString);
        description.appendText("\n");
        description.appendText("\n");

        if (matcher.matches(actual)) {
            description.appendText("    Result:");
            description.appendText("\n");
            description.appendText("No any mismatches found");
        } else {
            description.appendText("    But:");
            description.appendText("\n");
            matcher.describeMismatch(actual, description);
        }

        return description.toString();
    }

    @SuppressWarnings("rawtypes")
    private String getStepName(final Matcher matcher, final String reason) {
        if (reason != null && !reason.isEmpty()) {
            return reason;
        }

        final StringDescription description = new StringDescription();
        description.appendText("Assert that: ").appendDescriptionOf(matcher);
        return description.toString();
    }

    private String formatActual(final String actual) {
        try {
            return U.formatJsonOrXml(actual);
        } catch (Exception ignored) {
            return actual;
        }
    }
}
```

И транзитное DTO, которое будет передавать в шаблон ``hamcrest-matcher.ftl``

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllureMatcherDto implements AttachmentData {
    private String reason;
    private String actual;
    private String expecting;

    @Override
    public String getName() {
        return "assertThat";
    }
}
```

Осталось написать шаблон и дать понять JVM, что у нас есть аспект, который должен "ловить" 
нужный метод.

Складываем указание на новый аспект в `src/test/resources/META-INF/aop-ajc.xml`
```xml
<aspectj>
    <aspects>
        <aspect name="ru.sber.it.day.AllureHamcrestAspect"/>
    </aspects>
</aspectj>
```

Пишем шаблон ``hamcrest-matcher.ftl`` в `src/test/resources/tpl/hamcrest-matcher.ftl`
```html
<#ftl output_format="HTML">
<#-- @ftlvariable name="data" type="ru.sber.it.day.AllureMatcherDto" -->

<head>
    <meta charset="UTF-8">
    <!-- <script type="text/javascript">Любой JS код без сторонних библиотек</script> -->
</head>

<!-- Делаем настолько персонализированным, насколько это возможно -->
<style>
    .preformatted-text {
        background-color: #f7f7f7;
        border: 1px solid #ccc;
        border-radius: 0.25rem;
        font-family: Consolas, Menlo, Courier, monospace;
        font-size: 1rem;
        line-height: 1.5;
        padding: 1rem;
        margin-bottom: 2rem;
        overflow: auto;
    }
</style>

<h4>Reason</h4>
<div class="preformatted-text">
    <pre>${data.reason}</pre>
</div>

<h4>Description</h4>
<div class="preformatted-text">
    <pre>${data.expecting}</pre>
</div>

<h4>Actual</h4>
<div class="preformatted-text">
    <pre>${data.actual}</pre>
</div>
```

Пересобираем и запускаем!

Что же изменилось? 
1. Добавился новый шаг, характеризующий Assert
2. Появилась полная информация, почему что-то проверяем, что проверяем, и как это проверяем