package ru.sber.it.day;

import io.qameta.allure.attachment.AttachmentData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
