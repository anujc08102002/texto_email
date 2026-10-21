package com.texto.emailplatform.segment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texto.emailplatform.common.exception.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SegmentConditionQueryBuilderTest {
    private final SegmentConditionQueryBuilder builder =
            new SegmentConditionQueryBuilder(new ObjectMapper());

    @Test
    void buildsNestedGroupsAndBindsTextAndCustomPropertyValues() {
        var predicate = builder.build("""
                {"operator":"AND","conditions":[
                  {"field":"company","operator":"contains","value":"Example"},
                  {"operator":"OR","conditions":[
                    {"field":"custom.Region","operator":"equals","value":"India"},
                    {"field":"email","operator":"is_not_empty"}
                  ]}
                ]}
                """);

        assertThat(predicate.sql()).contains("AND", "OR", "POSITION", "jsonb_extract_path_text");
        assertThat(predicate.sql()).doesNotContain("Example", "India");
        assertThat(predicate.parameters().values()).contains("example", "Region", "india");
    }

    @Test
    void bindsAndSafelyCastsNumberAndDateConditions() {
        var predicate = builder.build("""
                {"operator":"AND","conditions":[
                  {"field":"custom.InvoiceAmount","type":"NUMBER","operator":"greater_than","value":"100000"},
                  {"field":"custom.InvoiceDate","type":"DATE","operator":"between","value":["2026-01-01","2026-12-31"]}
                ]}
                """);

        assertThat(predicate.sql()).contains("NUMERIC", "pg_input_is_valid", "BETWEEN");
        assertThat(predicate.parameters().values())
                .contains("InvoiceAmount", new BigDecimal("100000"), "InvoiceDate",
                        LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
    }

    @Test
    void rejectsUnknownFieldsAndUnsafeOperators() {
        assertThatThrownBy(() -> builder.build(
                "{\"operator\":\"AND\",\"conditions\":[{\"field\":\"email) OR true --\",\"operator\":\"equals\",\"value\":\"x\"}]}"
        )).isInstanceOf(ApiException.class);
    }

        @Test
        void invalidTypedValuesDoNotSatisfyNotEquals() {
                var predicate = builder.build("""
                                {"operator":"AND","conditions":[
                                  {"field":"custom.InvoiceAmount","type":"NUMBER","operator":"not_equals","value":"100"}
                                ]}
                                """);

                assertThat(predicate.sql()).contains("IS NOT NULL", "IS DISTINCT FROM");
        }
}