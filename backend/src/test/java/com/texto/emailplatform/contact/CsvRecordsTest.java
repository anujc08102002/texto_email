package com.texto.emailplatform.contact;

import com.texto.emailplatform.common.exception.ApiException;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CsvRecordsTest {
    @Test void readsBomCrLfQuotedCommasEscapedQuotesAndMultilineFields() throws Exception {
        var rows = CsvRecords.parse(new StringReader("\uFEFFemail,company,first_name\r\na@example.com,\"Example, Inc\",\"Ada \"\"A\"\"\"\r\nb@example.com,\"First\nSecond\",Bob\r\n"));
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsExactly("email", "company", "first_name");
        assertThat(rows.get(1)).containsExactly("a@example.com", "Example, Inc", "Ada \"A\"");
        assertThat(rows.get(2)).containsExactly("b@example.com", "First\nSecond", "Bob");
    }
    @Test void rejectsUnterminatedOrMisplacedQuotes() {
        for (String malformed : new String[]{"email\na\"b", "email\n\"unfinished", "email\n\"closed\"bad"}) {
            assertThatThrownBy(() -> CsvRecords.parse(new StringReader(malformed))).isInstanceOf(ApiException.class);
        }
    }
    @Test void countsRecordsInsteadOfPhysicalLinesAndEnforcesLimit() throws Exception {
        String permitted = "email\n" + "a@example.com\n".repeat(10_000);
        assertThat(CsvRecords.parse(new StringReader(permitted))).hasSize(10_001);
        assertThatThrownBy(() -> CsvRecords.parse(new StringReader(permitted + "overflow@example.com")))
                .isInstanceOf(ApiException.class).hasMessageContaining("10,000");
    }
    @Test void preservesTrailingEmptyColumnsAndIgnoresBlankRecords() throws Exception {
        assertThat(CsvRecords.parse(new StringReader("email,name\r\n\r\na@example.com,")))
                .containsExactly(java.util.List.of("email", "name"), java.util.List.of("a@example.com", ""));
    }
}
