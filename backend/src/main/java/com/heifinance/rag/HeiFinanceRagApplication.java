package com.heifinance.rag;

import com.heifinance.rag.config.RagProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * HeiFinance Knowledge Assistant — a retrieval-augmented generation service that answers internal
 * staff questions strictly from the HeiFinance source-of-truth PDF corpus.
 */
@SpringBootApplication
@EnableConfigurationProperties(RagProperties.class)
public class HeiFinanceRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(HeiFinanceRagApplication.class, args);
    }
}
