package com.heifinance.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KnowledgeIngestionServiceTest {

    @Test
    @DisplayName("document ids are stable, lower-case slugs of the file name")
    void derivesStableDocumentIds() {
        assertThat(KnowledgeIngestionService.documentId("HeiFinance_Employee_Handbook.pdf"))
                .isEqualTo("heifinance_employee_handbook");
        assertThat(KnowledgeIngestionService.documentId("HeiFinance Bank Product Fact Sheet.pdf"))
                .isEqualTo("heifinance_bank_product_fact_sheet");
        assertThat(KnowledgeIngestionService.documentId("HeiFinance_Annual_Report_2025.PDF"))
                .isEqualTo("heifinance_annual_report_2025");
    }

    @Test
    @DisplayName("titles are the file name without extension or underscores")
    void derivesReadableTitles() {
        assertThat(KnowledgeIngestionService.titleFrom("HeiFinance_Organization_Chart_Executive_Summary.pdf"))
                .isEqualTo("HeiFinance Organization Chart Executive Summary");
        assertThat(KnowledgeIngestionService.titleFrom("HeiFinanceBankProductFactSheet.pdf"))
                .isEqualTo("HeiFinanceBankProductFactSheet");
    }

    @Test
    @DisplayName("conversation titles are derived from the first question")
    void derivesConversationTitles() {
        assertThat(ConversationService.deriveTitle("  how much annual leave?  "))
                .isEqualTo("How much annual leave?");
        assertThat(ConversationService.deriveTitle("   "))
                .isEqualTo("New conversation");

        String longQuestion = "What is the exact notice period that applies to an employee who resigns "
                + "during the probation period and how is it calculated?";
        String title = ConversationService.deriveTitle(longQuestion);
        assertThat(title).endsWith("…");
        assertThat(title.length()).isLessThanOrEqualTo(76);
        assertThat(title).startsWith("What is the exact notice period");
    }
}