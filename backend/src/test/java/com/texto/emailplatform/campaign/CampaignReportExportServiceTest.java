package com.texto.emailplatform.campaign;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CampaignReportExportServiceTest {
    @Test void preventsSpreadsheetFormulaExecutionAndQuotesCsvValues() {
        assertThat(CampaignReportExportService.csv("=HYPERLINK(\"evil\")")).isEqualTo("\"'=HYPERLINK(\"\"evil\"\")\"");
        assertThat(CampaignReportExportService.csv(" +1")).isEqualTo("\"' +1\"");
        assertThat(CampaignReportExportService.csv("a,b\nc")).isEqualTo("\"a,b\nc\"");
        assertThat(CampaignReportExportService.csv(null)).isEqualTo("\"\"");
    }
}
