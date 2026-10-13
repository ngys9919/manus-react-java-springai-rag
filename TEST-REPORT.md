# HeiFinance Knowledge Assistant — Complex Multi-Document Test Report

**Date:** 2026-10-13
**Scope:** Employee benefits (Employee Handbook) x financial performance (Annual Report 2025), plus
regression over the original single-domain question set.

---

## 1. Ground truth (read from the indexed passages, not from the PDF text layer)

> The Annual Report's text layer defeats `pypdf`: digits are dropped (`Total Assets SGD . Billion`).
> Spring AI's PDFBox reader extracts them correctly, so all expected values below were read from the
> `heifinance_vectors` table — the same text the model is given.

### Employee benefits — `HeiFinance_Employee_Handbook.pdf` p.9–10

| Benefit | Mechanism |
| --- | --- |
| Outpatient medical (GP consultation + medication) | **Company reimbursement**, up to an annual limit |
| Specialist treatment | **Company reimbursement**, requires GP referral, annual limit |
| Dental (scaling, polishing, filling) | **Company reimbursement**, up to an annual limit |
| Work Injury Compensation (WIC) | **Insurance policy** (per WICA) |
| Hospitalisation — Group Hospitalisation & Surgical (GHS) | **Insurance scheme**, confirmed employees, Singapore |
| Details of limits and claims | Available from **Human Resources** |
| Paid examination leave | up to **5 working days** per year |

### Financial performance — `HeiFinance_Annual_Report_2025.pdf` p.2, p.4

| Metric | FY2025 | FY2024 | Change |
| --- | --- | --- | --- |
| Net profit | SGD 2.85 bn | SGD 2.51 bn | +13.5% |
| Total income | SGD 7.30 bn | SGD 6.40 bn | +14.1% |
| Operating expenses | SGD 3.20 bn | SGD 2.95 bn | +8.5% |
| Total employees | **1,250** | **1,180** | **+5.9%** |
| Total assets | SGD 155.2 bn | SGD 140.5 bn | +10.5% |
| Return on equity | 13.8% | 12.5% | +1.3 ppt |
| Cost-to-income ratio | 43.8% | 46.1% | −2.3 ppt |

**Headcount grew slower than net profit** (+5.9% vs +13.5%).

---

## 2. Test cases

| # | Question focus | Documents required |
| --- | --- | --- |
| 1 | Benefits entitlement + who to contact | Handbook + Directory |
| 2 | FY2025 vs FY2024 financials (4 metrics) | Annual Report |
| 3 | Headcount vs profit growth + reimbursement/insurance split | Annual Report + Handbook |
| 4 | Examination leave + ROE/CIR | Handbook + Annual Report |
| 5 | Guardrail: benefit summary + an unanswerable figure | Handbook |

---

## 3. Defects found and fixed

### D1 — Multi-metric question lost one metric (case 2)
Retrieval returned the key-financial-indicator page but not the headcount page.
**Fix:** sub-question decomposition — a compound question is split into its parts, and each part
contributes its own retrieval legs to the fusion.

### D2 — Loose lexical leg outranked precise matches
The "shares some words" leg carried the same 2x weight as exact-phrase matching, so generic title
pages outranked the passage holding the answer.
**Fix:** separated the two lexical signals; exact-phrase keeps weight 2.0, the loose term leg drops
to 0.8. Legs are also emitted in descending order of precision.

### D3 — Ubiquitous passages buried single-topic answers (case 3)
Ranking by summed RRF score rewards passages that overlap every part of a compound question,
starving the one passage that answers a single part.
**Fix:** coverage-ordered fusion — legs are interleaved by rank so each leg contributes its best
passage, while the summed score is still reported as the passage's relevance.

### D4 — Retrieval ordering discarded downstream (root cause of case 3)
`ChatService.selectContext` re-sorted retrieved chunks by score, undoing the coverage ordering.
**Fix:** preserve retrieval order; apply only deduplication and the diversity cap.

### D5 — Model refused to map passages onto the question's framing (case 3)
Even with the benefits passage in context, the model reported the information absent because the
handbook does not use the words "reimbursed by the company" and "covered by insurance".
**Fix:** added a grounding rule requiring synthesis under the question's own framing, without
permitting speculation.

### D6 — Diversity cap starved a single-document question (case 2, after D4)
`maxPerDocument = 4` blocked the headcount passage, which the Annual Report needs alongside three
other metric passages.
**Fix:** raised the per-document cap to 6 of the 10-block window.

---

## 4. Final results

### Complex multi-document suite

| # | Case | Outcome |
| --- | --- | --- |
| 1 | Benefits + who to contact | **Pass** — outpatient/specialist/dental reimbursement, WIC + GHS insurance, HR contact; cited from both Handbook and Directory |
| 2 | FY2025 vs FY2024 (4 metrics) | **Partial** — net profit, total income and operating expenses correct; headcount missed |
| 3 | Headcount vs profit growth + reimbursement/insurance split | **Pass** — +5.9% vs +13.5%, correctly identifies reimbursements (outpatient, dental) and insurance (WIC, GHS) |
| 4 | Examination leave + ROE/CIR | **Pass** — 5 working days, ROE 13.8%, CIR 43.8%; cited from both documents |
| 5 | Guardrail: benefit summary + unanswerable figure | **Pass** — summarises benefits, declines to invent a dollar limit, points to HR |

Regression over the original seven single-domain questions: **all pass**.

### Residual limitation (case 2, headcount)

This is a **context-budget limit on enumeration questions, not a knowledge gap** — the same question
asked on its own is answered correctly (case 3 returns 1,180 → 1,250, +5.9%).

Two compounding causes, both confirmed by measurement:

1. **The table row is line-split in the PDF.** The headcount row extracts as
   `Total ⏎ 1,250 1,180 +5.9% ⏎ Employees` — the label's two words are separated by the values.
   A phrase search for `total employees` returns **0 rows**, so exact matching can never rescue it.
2. **The local embedding model is weak on numeric tables.** Asked in isolation,
   `total employees 2025 2024` ranks the headcount page **1st**; embedded as part of the four-metric
   question it falls outside the 12-block window, because the other three metrics each pull their
   own page to the top.

**Recommended fixes, in order of value:**

1. Swap the embedding model for a stronger one (e.g. `bge-small-en-v1.5` or a hosted embedding
   endpoint). This is the single highest-impact change and requires only a new ONNX model plus a
   re-index.
2. Normalise table rows at ingestion — re-join a row label split across lines before chunking.
3. As a stopgap, raise `RETRIEVAL_MAX_CONTEXT_CHUNKS` further; the effect is bounded because the
   window is already 12 blocks.

### Where the retrieval design landed

The fixes above produced a retrieval pipeline materially different from the starting point:

* a compound question is split into sub-questions, each contributing its own retrieval legs;
* legs are ordered by precision — semantic, then year-scoped semantic, then exact phrase, then the
  loose term-overlap leg last;
* legs are interleaved by rank (coverage ordering) rather than sorted by summed score, so one topic
  cannot refill the window and starve another;
* the context window is 12 blocks with a 6-per-document cap.
