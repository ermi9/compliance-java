# OOP Compliance Screener — POC


A pre-screening tool for student Java submissions: it checks whether the required OOP
concepts are present and flags AI-favored banned syntax before a professor grades by hand.

## Setup

```bash
pip install flask javalang anthropic zss
export ANTHROPIC_API_KEY=your_key_here
python3 app.py
```

Open http://localhost:5000

## How it works

Three-layer evaluation pipeline:

**Layer 1 — Static Analysis**  
Checks for banned syntax (lambdas, streams, records), minimum class count, required inheritance, interfaces, overrides, private fields, generics, and exception handling. Fast, rule-based, no API calls.

**Layer 2 — Tree Edit Distance**  
Converts both the reference and student Java ASTs to ZSS trees and computes the normalized edit distance. HIGH confidence FAIL (>60% distance) blocks immediately; LOW confidence FAIL escalates to Layer 3.

**Layer 3 — LLM Semantic Evaluation**  
Sends student metrics, reference metrics, structural diff, and a sample of source code to `claude-sonnet-4-6`. Returns per-concept OOP analysis, vibe-coding signals, and constructive feedback for the student.

## Verdict types

| Verdict | Meaning |
|---|---|
| `COMPLIANT` | Passed all three layers with HIGH confidence |
| `NON_COMPLIANT` | Failed Layer 1, or Layer 2 with HIGH confidence, or Layer 3 |
| `NEEDS_REVIEW` | Borderline result — professor should verify manually |

## API endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/setup` | Upload reference ZIP + guidelines |
| `POST` | `/submit` | Submit single student ZIP |
| `POST` | `/submit/batch` | Submit multiple ZIPs at once |
| `GET` | `/results` | All results sorted by verdict |
| `GET` | `/reference` | Current reference status |
| `DELETE` | `/reset` | Clear all state |

## Test with real data

1. Upload your own correct solution as the reference ZIP
2. Paste the assignment guidelines
3. Upload student ZIPs via batch submit
4. Compare verdicts against professor's actual grades

## Known limitations

- In-memory only — resets on restart
- Java only in this version
- Tree distance thresholds (0.25 / 0.45 / 0.60) need calibration against real submissions
- LLM evaluation costs API credits per submission
- Large projects may cause slow ZSS distance computation

## Status

Proof of concept.
