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
