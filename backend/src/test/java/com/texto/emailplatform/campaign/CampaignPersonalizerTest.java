package com.texto.emailplatform.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CampaignPersonalizerTest {
    private final CampaignPersonalizer personalizer = new CampaignPersonalizer();

    @Test
    void rendersContactAndCustomFieldsAndEscapesHtmlValues() {
        String html = personalizer.render(
                "<p>Hello {{first_name}} {{custom.Region}}</p>",
                Map.of("first_name", "<Ada>", "custom", Map.of("Region", "A&B")),
                true
        );

        assertThat(html).isEqualTo("<p>Hello &lt;Ada&gt; A&amp;B</p>");
    }

    @Test
    void missingOptionalFieldsRenderAsEmptyStrings() {
        assertThat(personalizer.render("Hello {{first_name}}", Map.of(), false)).isEqualTo("Hello ");
    }
}