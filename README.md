# OOP Compliance Screener — POC

Proof of concept for Professor Salvatore Distefano's OOP course at University of Messina.

A pre-screening tool for student Java submissions: it checks whether required OOP concepts
are present and flags AI-favored banned syntax before a professor grades by hand.

There are two entry points in this repo:

- **`app.py`** — the full three-layer Flask service (reference solution + LLM-backed evaluation).
- **`screener.py`** — a lightweight, dependency-free regex scanner for a single file or folder.

---

## `app.py` — three-layer Flask service

### Setup

```bash
pip install flask javalang anthropic zss
export ANTHROPIC_API_KEY=your_key_here
python3 app.py
```

Open http://localhost:5000

### How it works

Three-layer evaluation pipeline:

**Layer 1 — Static Analysis**  
Checks for banned syntax (lambdas, streams, records), minimum class count, required inheritance, interfaces, overrides, private fields, generics, and exception handling. Fast, rule-based, no API calls.

**Layer 2 — Tree Edit Distance**  
Converts both the reference and student Java ASTs to ZSS trees and computes the normalized edit distance. HIGH confidence FAIL (>60% distance) blocks immediately; LOW confidence FAIL escalates to Layer 3.

**Layer 3 — LLM Semantic Evaluation**  
Sends student metrics, reference metrics, structural diff, and a sample of source code to `claude-sonnet-4-6`. Returns per-concept OOP analysis, vibe-coding signals, and constructive feedback for the student.

### Verdict types

| Verdict | Meaning |
|---|---|
| `COMPLIANT` | Passed all three layers with HIGH confidence |
| `NON_COMPLIANT` | Failed Layer 1, or Layer 2 with HIGH confidence, or Layer 3 |
| `NEEDS_REVIEW` | Borderline result — professor should verify manually |

### API endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/setup` | Upload reference ZIP + guidelines |
| `POST` | `/submit` | Submit single student ZIP |
| `POST` | `/submit/batch` | Submit multiple ZIPs at once |
| `GET` | `/results` | All results sorted by verdict |
| `GET` | `/reference` | Current reference status |
| `DELETE` | `/reset` | Clear all state |

### Test with real data

