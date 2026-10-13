# Knowledge base

This folder holds the source-of-truth documents the assistant answers from.

**The PDFs are deliberately not published in this Git repository.** Everything else
in the project is public, but the corpus is not, so a clone does not include it.

## What belongs here

These five files, using exactly these names:

1. `HeiFinance_Annual_Report_2025.pdf`
2. `HeiFinance Bank Product Fact Sheet.pdf`
3. `HeiFinance_Employee_Handbook.pdf`
4. `HeiFinance_Organization_Chart_Executive_Summary.pdf`
5. `HeiFinance_Full_Directory_Complete.pdf`

Filenames matter: they are recorded as each passage's citation source and shown in
the evidence panel, so renaming a file changes what users see.

## How the folder is used

| Consumer | Behaviour |
| --- | --- |
| Local development | `KNOWLEDGE_BASE_PATH` defaults to `../knowledge-base` |
| Docker image | Baked in at build time (`COPY knowledge-base/`) — a pulled image is self-contained |
| Docker Compose | Uses the bundled copy; the optional bind mount is commented out, since an empty host folder would shadow it |
| Ingestion | Runs on startup; a document whose SHA-256 checksum is unchanged is skipped |

Because the corpus is baked into the published image, `docker pull` alone is enough
to run the assistant. **Building the image yourself does require the PDFs here**, and
so does running the backend directly without Docker.

## Verifying

```bash
curl -s http://localhost:8080/api/system/health | python3 -m json.tool
```

Expect `"indexedDocuments": 5`. To rebuild the index after changing a file:

```bash
curl -X POST "http://localhost:8080/api/knowledge/reindex?force=true"
```

## Keeping the PDFs out of Git

`.gitignore` excludes everything in this folder except this file and `.gitkeep`, so
the folder survives a clone as a mount point while the PDFs stay local.

Two things to remember:

- **An ignore rule does not untrack a file that is already committed.** If a PDF
  ever becomes tracked, remove it with `git rm --cached <file>`.
- **Removing a file in a new commit leaves it in history.** To purge it properly the
  commit must be rewritten, or the repository recreated.

## Note on the published image

The Docker image on Docker Hub **does** contain the corpus, so anyone who can pull
it can extract the PDFs. That repository is currently public — make it private in
Docker Hub settings if the documents must not circulate.
