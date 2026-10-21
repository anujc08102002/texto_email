package com.texto.emailplatform.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.texto.emailplatform.common.exception.ApiException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void escapesContactValuesInHtmlWithoutChangingPlainText() {
        var rendered = renderer.render("Hi {{name}}", "<p>{{name}}</p>", "Hi {{name}}",
                Map.of("name", Map.of("type", "string")), Map.of("name", "<img src=x>&\"'"));
        assertThat(rendered.htmlContent()).isEqualTo("<p>&lt;img src=x&gt;&amp;&quot;&#39;</p>");
        assertThat(rendered.textContent()).isEqualTo("Hi <img src=x>&\"'");
    }

    @Test
    void replacesDeclaredVariables() {
        var rendered = renderer.render(
                "Hi {{first_name}}",
                "<p>Hello {{first_name}}</p>",
                "Hello {{first_name}}",
                Map.of("first_name", Map.of("type", "string", "required", true)),
                Map.of("first_name", "Ada")
        );

        assertThat(rendered.subject()).isEqualTo("Hi Ada");
        assertThat(rendered.htmlContent()).contains("Ada");
        assertThat(rendered.textContent()).isEqualTo("Hello Ada");
    }

    @Test
    void rejectsMissingRequiredVariable() {
        assertThatThrownBy(() -> renderer.render(
                "Hi {{first_name}}",
                "<p>{{first_name}}</p>",
                null,
                Map.of("first_name", Map.of("type", "string", "required", true)),
                Map.of()
        )).isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("MISSING_TEMPLATE_VARIABLE"));
    }

    @Test
    void rejectsUnknownProvidedVariable() {
        assertThatThrownBy(() -> renderer.render(
                "Hi",
                "<p>Hi</p>",
                null,
                Map.of("first_name", Map.of("type", "string", "required", false)),
                Map.of("evil", "x")
        )).isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_TEMPLATE_VARIABLE"));
    }

    @Test
    void rejectsUndeclaredPlaceholder() {
        assertThatThrownBy(() -> renderer.render(
                "Hi {{unknown}}",
                "<p></p>",
                null,
                Map.of(),
                Map.of()
        )).isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("UNKNOWN_TEMPLATE_PLACEHOLDER"));
    }
}
