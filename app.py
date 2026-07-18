import os
import re
import sys
import json
import time
import zipfile
import tempfile
import shutil
from flask import Flask, request, jsonify, render_template_string
import javalang
import javalang.ast
from zss import Node, simple_distance

sys.setrecursionlimit(50000)

# Load .env file if present so ANTHROPIC_API_KEY persists across restarts
_env_path = os.path.join(os.path.dirname(__file__), ".env")
if os.path.exists(_env_path):
    with open(_env_path) as _f:
        for _line in _f:
            _line = _line.strip()
            if _line and not _line.startswith("#") and "=" in _line:
                _k, _v = _line.split("=", 1)
                os.environ.setdefault(_k.strip(), _v.strip())

ZSS_NODE_LIMIT = 4000  # skip ZSS distance if either tree exceeds this

app = Flask(__name__)

# ── In-memory state ──────────────────────────────────────────────────────────

reference = {
    "zss_tree": None,
    "node_count": 0,
    "metrics": {},
    "guidelines": "",
    "profile": {},
    "uploaded_at": None,
}

results = []

# ── Java parsing ──────────────────────────────────────────────────────────────

SKIP_FRAGMENTS = {"test", "Test", ".git", "target", "build", "__MACOSX"}


def parse_zip_to_ast(zip_file) -> dict:
    tmpdir = tempfile.mkdtemp()
    try:
        try:
            with zipfile.ZipFile(zip_file, "r") as zf:
                zf.extractall(tmpdir)
        except zipfile.BadZipFile:
            return {"error": "Invalid or corrupted ZIP file"}

        java_files = []
        for root, _dirs, files in os.walk(tmpdir):
            for fname in files:
                if not fname.endswith(".java"):
                    continue
                full = os.path.join(root, fname)
                rel = os.path.relpath(full, tmpdir)
                if any(frag in rel for frag in SKIP_FRAGMENTS):
                    continue
                java_files.append(full)

        if not java_files:
            return {"error": "No Java files found in submission"}

        parsed_files = []
        parse_errors = []
        all_sources = []

        for fp in java_files:
            rel = os.path.relpath(fp, tmpdir)
            try:
                with open(fp, "r", encoding="utf-8", errors="replace") as fh:
                    source = fh.read()
                tree = javalang.parse.parse(source)
                parsed_files.append({"filename": rel, "source": source, "tree": tree})
                all_sources.append(source)
            except Exception as e:
                parse_errors.append(f"{rel}: {e}")

        if not parsed_files:
            return {
                "error": "No files could be parsed",
                "parse_errors": parse_errors,
            }

        return {
            "files": parsed_files,
            "parse_errors": parse_errors,
            "all_source": "\n\n".join(all_sources),
        }
    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)


def extract_metrics(parsed: dict) -> dict:
    classes = []
    interfaces = []
    methods = []
    fields = []

    for file_info in parsed.get("files", []):
        fname = file_info["filename"]
        tree = file_info["tree"]

        for _, node in tree.filter(javalang.tree.ClassDeclaration):
            extends = None
            if node.extends:
                extends = node.extends.name if hasattr(node.extends, "name") else str(node.extends)
            implements = []
            if node.implements:
                for iface in node.implements:
                    implements.append(iface.name if hasattr(iface, "name") else str(iface))
            is_abstract = "abstract" in (node.modifiers or [])
            type_params = list(node.type_parameters or [])
            classes.append(
                {
                    "name": node.name,
                    "extends": extends,
                    "implements": implements,
                    "is_abstract": is_abstract,
                    "type_params": type_params,
                    "file": fname,
                }
            )

            for member in (node.body or []):
                if isinstance(member, javalang.tree.MethodDeclaration):
                    has_override = any(
                        (a.name if hasattr(a, "name") else str(a)) == "Override"
                        for a in (member.annotations or [])
                    )
                    m_abstract = "abstract" in (member.modifiers or [])
                    m_type_params = list(member.type_parameters or [])
                    methods.append(
                        {
                            "name": member.name,
                            "class_name": node.name,
                            "has_override": has_override,
                            "is_abstract": m_abstract,
                            "type_params": m_type_params,
                            "modifiers": list(member.modifiers or []),
                            "file": fname,
                        }
                    )
                if isinstance(member, javalang.tree.FieldDeclaration):
                    ftype = member.type.name if hasattr(member.type, "name") else str(member.type)
                    for decl in (member.declarators or []):
                        fields.append(
                            {
                                "name": decl.name,
                                "class_name": node.name,
                                "modifiers": list(member.modifiers or []),
                                "type": ftype,
                                "file": fname,
                            }
                        )

        for _, node in tree.filter(javalang.tree.InterfaceDeclaration):
            interfaces.append({"name": node.name, "file": fname})

            for member in (node.body or []):
                if isinstance(member, javalang.tree.MethodDeclaration):
                    methods.append(
                        {
                            "name": member.name,
                            "class_name": node.name,
                            "has_override": False,
                            "is_abstract": True,
                            "type_params": list(member.type_parameters or []),
                            "modifiers": list(member.modifiers or []),
                            "file": fname,
                        }
                    )

    # Build extends map for depth calculation
    extends_map = {c["name"]: c["extends"] for c in classes}

    def inheritance_depth(name, visited=None):
        if visited is None:
            visited = set()
        if name in visited or name not in extends_map:
            return 0
        visited.add(name)
        parent = extends_map[name]
        if parent is None or parent not in extends_map:
            return 1 if parent else 0
        return 1 + inheritance_depth(parent, visited)

    max_depth = max((inheritance_depth(c["name"]) for c in classes), default=0)

    all_source = parsed.get("all_source", "")

    has_exception_handling = bool(re.search(r'\btry\s*\{', all_source))
    has_lambda = bool(re.search(r'\s*->\s*', all_source))
    has_streams = bool(re.search(r'\.(stream|filter|collect|map|reduce)\(', all_source))
    has_records = bool(re.search(r'\brecord\s+\w+\s*\(', all_source))

    banned_syntax = []
    if has_lambda:
        banned_syntax.append({"type": "lambda", "description": "Lambda expressions (->)"})
    if has_streams:
        banned_syntax.append({"type": "streams", "description": "Stream API usage"})
    if has_records:
        banned_syntax.append({"type": "records", "description": "Java records"})

    return {
        "classes": classes,
        "interfaces": interfaces,
        "methods": methods,
        "fields": fields,
        "class_count": len(classes),
        "interface_count": len(interfaces),
        "method_count": len(methods),
        "field_count": len(fields),
        "inheritance_count": sum(1 for c in classes if c["extends"]),
        "implementation_count": sum(1 for c in classes if c["implements"]),
        "override_count": sum(1 for m in methods if m["has_override"]),
        "private_field_count": sum(1 for f in fields if "private" in f["modifiers"]),
        "generic_class_count": sum(1 for c in classes if c.get("type_params")),
        "generic_method_count": sum(1 for m in methods if m.get("type_params")),
        "abstract_class_count": sum(1 for c in classes if c["is_abstract"]),
        "max_inheritance_depth": max_depth,
        "has_exception_handling": has_exception_handling,
        "has_lambda": has_lambda,
        "has_streams": has_streams,
        "has_records": has_records,
        "banned_syntax": banned_syntax,
    }


def build_zss_tree(parsed: dict) -> Node:
    root = Node("Project")

    def javalang_to_zss(node):
        if not isinstance(node, javalang.ast.Node):
            return None
        zss_node = Node(type(node).__name__)
        for child in node.children:
            if isinstance(child, javalang.ast.Node):
                kid = javalang_to_zss(child)
                if kid:
                    zss_node.addkid(kid)
            elif isinstance(child, list):
                for item in child:
                    if isinstance(item, javalang.ast.Node):
                        kid = javalang_to_zss(item)
                        if kid:
                            zss_node.addkid(kid)
        return zss_node

    for file_info in parsed.get("files", []):
        file_node = Node("File")
        kid = javalang_to_zss(file_info["tree"])
        if kid:
            file_node.addkid(kid)
        root.addkid(file_node)

    return root


def count_nodes(zss_node: Node) -> int:
    if zss_node is None:
        return 0
    total = 0
    stack = [zss_node]
    while stack:
        n = stack.pop()
        total += 1
        stack.extend(Node.get_children(n))
    return total


# ── Layer 1 ───────────────────────────────────────────────────────────────────

def layer1_static(metrics: dict, profile: dict) -> dict:
    passed = []
    failed = []
    warnings = []

    # banned syntax — only fail on items the professor explicitly banned
    banned_list = [b.lower() for b in profile.get("banned", [])]
    actually_banned = []
    for item in metrics["banned_syntax"]:
        t = item["type"]
        if t in banned_list or any(t in b for b in banned_list):
            actually_banned.append(item["description"])
    if actually_banned:
        failed.append(f"check_banned_syntax: Found banned syntax — {', '.join(actually_banned)}")
    else:
        note = f" ({', '.join(b['description'] for b in metrics['banned_syntax'])} detected but not banned by guidelines)" if metrics["banned_syntax"] else ""
        passed.append(f"check_banned_syntax: No professor-banned syntax found{note}")

    # java only (always pass at this point)
    passed.append("check_java_only: Only Java files processed")

    # minimum classes
    min_classes = profile.get("minimum_classes", 1)
    if metrics["class_count"] < min_classes:
        failed.append(
            f"check_classes: Only {metrics['class_count']} class(es) found, need {min_classes}"
        )
    else:
        passed.append(f"check_classes: {metrics['class_count']} class(es) found (min {min_classes})")

    # inheritance
    if profile.get("inheritance_required") and metrics["inheritance_count"] == 0:
        failed.append("check_inheritance: Inheritance required but none found")
    else:
        passed.append(f"check_inheritance: {metrics['inheritance_count']} inheritance relationship(s)")

    # interfaces
    min_ifaces = profile.get("minimum_interfaces", 1)
    if profile.get("interfaces_required") and metrics["interface_count"] < min_ifaces:
        failed.append(
            f"check_interfaces: {metrics['interface_count']} interface(s) found, need {min_ifaces}"
        )
    else:
        passed.append(f"check_interfaces: {metrics['interface_count']} interface(s) found")

    # overriding
    if profile.get("override_required") and metrics["override_count"] == 0:
        failed.append("check_overriding: Method overriding required but @Override not found")
    else:
        passed.append(f"check_overriding: {metrics['override_count']} override(s) found")

    # encapsulation
    if profile.get("private_fields_required") and metrics["private_field_count"] == 0:
        failed.append("check_encapsulation: Private fields required but none found")
    else:
        passed.append(f"check_encapsulation: {metrics['private_field_count']} private field(s)")

    # generics
    if profile.get("generics_required") and (
        metrics["generic_class_count"] == 0 and metrics["generic_method_count"] == 0
    ):
        failed.append("check_generics: Generics required but none found")
    else:
        passed.append(
            f"check_generics: {metrics['generic_class_count']} generic class(es), "
            f"{metrics['generic_method_count']} generic method(s)"
        )

    # exception handling
    if profile.get("exception_handling_required") and not metrics["has_exception_handling"]:
        failed.append("check_exception_handling: Exception handling required but no try block found")
    else:
        passed.append(
            f"check_exception_handling: Exception handling "
            + ("present" if metrics["has_exception_handling"] else "not present")
        )

    # complexity warning
    if metrics["class_count"] < 5:
        warnings.append(
            "check_complexity: Low class count may indicate insufficient complexity for this assignment"
        )

    summary_keys = [
        "class_count", "interface_count", "method_count", "field_count",
        "inheritance_count", "override_count", "private_field_count",
        "generic_class_count", "abstract_class_count",
    ]

    return {
        "verdict": "FAIL" if failed else "PASS",
        "passed_checks": passed,
        "failed_checks": failed,
        "warnings": warnings,
        "banned_syntax_found": metrics["banned_syntax"],
        "metrics_summary": {k: metrics[k] for k in summary_keys},
    }


# ── Layer 2 ───────────────────────────────────────────────────────────────────

def layer2_distance(
    student_zss: Node,
    ref_zss: Node,
    student_node_count: int,
    ref_node_count: int,
) -> dict:
    if student_node_count > ZSS_NODE_LIMIT or ref_node_count > ZSS_NODE_LIMIT:
        return {
            "verdict": "PASS",
            "confidence": "LOW",
            "raw_distance": -1,
            "normalized_distance": -1,
            "similarity_percent": -1,
            "ref_node_count": ref_node_count,
            "student_node_count": student_node_count,
            "interpretation": f"Project too large for tree edit distance ({max(student_node_count, ref_node_count)} nodes); escalating to semantic evaluation.",
        }
    try:
        raw = simple_distance(ref_zss, student_zss)
    except Exception as e:
        return {
            "verdict": "PASS",
            "confidence": "LOW",
            "raw_distance": -1,
            "normalized_distance": -1,
            "similarity_percent": -1,
            "ref_node_count": ref_node_count,
            "student_node_count": student_node_count,
            "interpretation": f"Tree distance computation failed ({e}); escalating to semantic evaluation.",
        }
    denom = max(ref_node_count, student_node_count)
    normalized = raw / denom if denom else 0.0
    similarity_pct = max(0.0, (1 - normalized) * 100)

    if normalized <= 0.40:
        verdict, confidence = "PASS", "HIGH"
    elif normalized <= 0.65:
        verdict, confidence = "PASS", "LOW"
    elif normalized <= 0.80:
        verdict, confidence = "FAIL", "LOW"
    else:
        verdict, confidence = "FAIL", "HIGH"

    if similarity_pct >= 75:
        interpretation = "Student structure closely mirrors the reference solution."
    elif similarity_pct >= 50:
        interpretation = "Student structure partially resembles the reference; some divergence."
    elif similarity_pct >= 25:
        interpretation = "Significant structural differences from the reference solution."
    else:
        interpretation = "Student structure is very far from the reference solution."

    return {
        "verdict": verdict,
        "confidence": confidence,
        "raw_distance": float(raw),
        "normalized_distance": float(normalized),
        "similarity_percent": float(similarity_pct),
        "ref_node_count": ref_node_count,
        "student_node_count": student_node_count,
        "interpretation": interpretation,
    }


# ── Layer 3 ───────────────────────────────────────────────────────────────────

def _llm_call(system_prompt: str, user_prompt: str) -> str:
    from google import genai
    client = genai.Client(api_key=os.environ.get("GEMINI_API_KEY", ""))
    response = client.models.generate_content(
        model="gemini-2.0-flash",
        contents=f"{system_prompt}\n\n{user_prompt}",
    )
    return response.text


def layer3_semantic(
    student_metrics: dict,
    ref_metrics: dict,
    guidelines: str,
    all_source: str,
) -> dict:
    truncated = False
    if len(all_source) > 6000:
        all_source = all_source[:6000] + "\n...truncated for length"
        truncated = True

    def summary(m):
        keys = [
            "class_count", "interface_count", "method_count", "field_count",
            "inheritance_count", "implementation_count", "override_count",
            "private_field_count", "generic_class_count", "generic_method_count",
            "abstract_class_count", "max_inheritance_depth",
            "has_exception_handling", "has_lambda", "has_streams",
        ]
        return {k: m.get(k) for k in keys}

    ref_s = summary(ref_metrics)
    stu_s = summary(student_metrics)

    diff = {}
    for key in ref_s:
        rv = ref_s[key]
        sv = stu_s[key]
        if isinstance(rv, bool):
            diff[key] = {"ref": rv, "student": sv, "match": rv == sv}
        elif isinstance(rv, (int, float)):
            diff[key] = {"ref": rv, "student": sv, "delta": (sv or 0) - (rv or 0)}

    system_prompt = (
        "You are a strict but fair Java OOP compliance evaluator for a university course "
        "at University of Messina. You evaluate whether student code meaningfully implements "
        "OOP concepts, not just syntactically. Respond ONLY with valid JSON. No markdown. No preamble."
    )

    user_prompt = f"""PROFESSOR GUIDELINES:
{guidelines}

REFERENCE PROJECT METRICS (what a good solution looks like):
{json.dumps(ref_s, indent=2)}

STUDENT PROJECT METRICS:
{json.dumps(stu_s, indent=2)}

STRUCTURAL DIFF:
{json.dumps(diff, indent=2)}

STUDENT SOURCE CODE (sample):
{all_source}

Evaluate and return exactly this JSON:
{{
  "semantic_verdict": "PASS" or "FAIL",
  "confidence": "HIGH", "MEDIUM", or "LOW",
  "oop_concepts": {{
    "encapsulation": {{"present": bool, "meaningful": bool, "comment": "one sentence"}},
    "inheritance": {{"present": bool, "meaningful": bool, "comment": "one sentence"}},
    "polymorphism": {{"overloading": bool, "overriding": bool, "parametric": bool, "coercion": bool, "comment": "one sentence"}},
    "abstraction": {{"present": bool, "meaningful": bool, "comment": "one sentence"}},
    "subtyping": {{"present": bool, "meaningful": bool, "comment": "one sentence"}},
    "exception_handling": {{"present": bool, "meaningful": bool, "comment": "one sentence"}},
    "extensibility": {{"present": bool, "comment": "one sentence"}}
  }},
  "vibe_coding_signals": ["list any signals suggesting LLM generation"],
  "complexity_assessment": "one sentence on overall richness",
  "professor_summary": "2-3 sentences for professor",
  "student_feedback": "2-3 sentences explaining issues directly to student, constructive tone"
}}"""

    for attempt in range(2):
        try:
            raw = _llm_call(system_prompt, user_prompt)
            return json.loads(raw)
        except json.JSONDecodeError:
            return {"error": "LLM response invalid", "raw": raw}
        except Exception:
            if attempt == 0:
                time.sleep(2)
            else:
                return {"error": "LLM unavailable"}

    return {"error": "LLM unavailable"}


def _generate_profile(guidelines: str) -> dict:
    system_prompt = "You are a course requirements extractor. Respond ONLY with valid JSON. No markdown."
    user_prompt = f"""Extract a structural compliance profile from these course guidelines. Return ONLY valid JSON, no markdown:
{{
  "minimum_classes": int,
  "minimum_interfaces": int,
  "inheritance_required": bool,
  "interfaces_required": bool,
  "override_required": bool,
  "private_fields_required": bool,
  "generics_required": bool,
  "exception_handling_required": bool,
  "banned": ["lambdas", "streams", "records"],
  "notes": "one sentence summary of key requirements"
}}

Guidelines:
{guidelines}"""

    for attempt in range(2):
        try:
            raw = _llm_call(system_prompt, user_prompt)
            return json.loads(raw)
        except json.JSONDecodeError:
            return {"error": "Profile generation failed", "raw": raw}
        except Exception:
            if attempt == 0:
                time.sleep(2)
            else:
                return {"error": "LLM unavailable for profile generation"}

    return {"error": "LLM unavailable"}


# ── Final verdict ─────────────────────────────────────────────────────────────

def compute_final_verdict(l1: dict, l2, l3) -> dict:
    if l1["verdict"] == "FAIL":
        return {
            "final_verdict": "NON_COMPLIANT",
            "reason": "Failed static analysis",
            "layers_run": ["layer1"],
            "can_appeal": False,
        }

    if l2 and l2["verdict"] == "FAIL" and l2["confidence"] == "HIGH":
        return {
            "final_verdict": "NON_COMPLIANT",
            "reason": "Structural distance too high from reference solution",
            "layers_run": ["layer1", "layer2"],
            "can_appeal": False,
        }

    if l2 and l2["verdict"] == "FAIL" and l2["confidence"] == "LOW":
        if l3 is None or l3.get("error"):
            return {
                "final_verdict": "NEEDS_REVIEW",
                "reason": "Borderline structural distance, requires professor review",
                "layers_run": ["layer1", "layer2", "layer3"],
                "can_appeal": True,
            }

    if l3 is None:
        return {
            "final_verdict": "NEEDS_REVIEW",
            "reason": "LLM evaluation unavailable",
            "layers_run": ["layer1", "layer2"],
            "can_appeal": True,
        }

    if l3.get("error"):
        return {
            "final_verdict": "NEEDS_REVIEW",
            "reason": "Semantic evaluation failed",
            "layers_run": ["layer1", "layer2"],
            "can_appeal": True,
        }

    if l3.get("semantic_verdict") == "FAIL":
        return {
            "final_verdict": "NON_COMPLIANT",
            "reason": "OOP concepts not meaningfully implemented",
            "layers_run": ["layer1", "layer2", "layer3"],
            "can_appeal": True,
        }

    if l3.get("confidence") in ["LOW", "MEDIUM"]:
        return {
            "final_verdict": "NEEDS_REVIEW",
            "reason": "Low semantic confidence, professor should verify",
            "layers_run": ["layer1", "layer2", "layer3"],
            "can_appeal": True,
        }

    return {
        "final_verdict": "COMPLIANT",
        "reason": "Passed all three layers",
        "layers_run": ["layer1", "layer2", "layer3"],
        "can_appeal": False,
    }


# ── Submission processing helper ──────────────────────────────────────────────

def _process_submission(zip_file, student_name: str) -> dict:
    t0 = time.time()

    parsed = parse_zip_to_ast(zip_file)
    if "error" in parsed:
        return {"error": parsed["error"], "parse_errors": parsed.get("parse_errors", [])}

    metrics = extract_metrics(parsed)
    zss_tree = build_zss_tree(parsed)
    node_count = count_nodes(zss_tree)

    l1 = layer1_static(metrics, reference["profile"])
    l2 = None
    l3 = None

    if l1["verdict"] == "PASS":
        l2 = layer2_distance(
            zss_tree,
            reference["zss_tree"],
            node_count,
            reference["node_count"],
        )
        if l2["verdict"] == "PASS" or l2["confidence"] == "LOW":
            l3 = layer3_semantic(
                metrics,
                reference["metrics"],
                reference["guidelines"],
                parsed["all_source"],
            )

    final = compute_final_verdict(l1, l2, l3)

    elapsed_ms = int((time.time() - t0) * 1000)
    result_id = len(results) + 1

    record = {
        "id": result_id,
        "student": student_name,
        "submitted_at": time.time(),
        "files_parsed": len(parsed["files"]),
        "parse_errors": parsed["parse_errors"],
        "layer1": l1,
        "layer2": l2,
        "layer3": l3,
        "final": final,
        "processing_time_ms": elapsed_ms,
    }
    results.append(record)
    return record


# ── Endpoints ─────────────────────────────────────────────────────────────────

@app.route("/setup", methods=["POST"])
def setup():
    if "reference_zip" not in request.files:
        return jsonify({"error": "No reference_zip file provided"}), 400
    guidelines = request.form.get("guidelines", "")

    parsed = parse_zip_to_ast(request.files["reference_zip"])
    if "error" in parsed:
        return jsonify({"error": parsed["error"]}), 400

    metrics = extract_metrics(parsed)
    zss_tree = build_zss_tree(parsed)
    node_count = count_nodes(zss_tree)
    profile = _generate_profile(guidelines)

    reference["zss_tree"] = zss_tree
    reference["node_count"] = node_count
    reference["metrics"] = metrics
    reference["guidelines"] = guidelines
    reference["profile"] = profile
    reference["uploaded_at"] = time.time()

    safe_metrics = {
        k: v for k, v in metrics.items()
        if not isinstance(v, list) or k in ("banned_syntax",)
    }
    # remove large lists
    for k in ("classes", "interfaces", "methods", "fields"):
        safe_metrics.pop(k, None)

    return jsonify({
        "status": "reference stored",
        "files_parsed": len(parsed["files"]),
        "parse_errors": parsed["parse_errors"],
        "metrics": safe_metrics,
        "profile": profile,
        "message": "System ready to evaluate submissions",
    })


@app.route("/submit", methods=["POST"])
def submit():
    if not reference["zss_tree"]:
        return jsonify({"error": "No reference solution uploaded. Call /setup first"}), 400
    if "submission_zip" not in request.files:
        return jsonify({"error": "No submission_zip file provided"}), 400

    student_name = request.form.get("student_name", "Unknown")
    record = _process_submission(request.files["submission_zip"], student_name)

    if "error" in record:
        return jsonify(record), 400
    return jsonify(record)


@app.route("/submit/batch", methods=["POST"])
def submit_batch():
    if not reference["zss_tree"]:
        return jsonify({"error": "No reference solution uploaded. Call /setup first"}), 400

    files = request.files.getlist("submission_zips")
    if not files:
        return jsonify({"error": "No files provided"}), 400

    batch_results = []
    total_ms = 0

    for f in files:
        student_name = os.path.splitext(f.filename)[0] if f.filename else "Unknown"
        record = _process_submission(f, student_name)
        batch_results.append(record)
        if "processing_time_ms" in record:
            total_ms += record["processing_time_ms"]

    compliant = sum(1 for r in batch_results if r.get("final", {}).get("final_verdict") == "COMPLIANT")
    non_compliant = sum(1 for r in batch_results if r.get("final", {}).get("final_verdict") == "NON_COMPLIANT")
    needs_review = sum(1 for r in batch_results if r.get("final", {}).get("final_verdict") == "NEEDS_REVIEW")

    l1_failures = sum(1 for r in batch_results if r.get("layer1", {}).get("verdict") == "FAIL")
    l2_failures = sum(1 for r in batch_results if r.get("layer2") and r["layer2"].get("verdict") == "FAIL")
    l3_failures = sum(
        1 for r in batch_results
        if r.get("layer3") and not r["layer3"].get("error") and r["layer3"].get("semantic_verdict") == "FAIL"
    )

    sims = [r["layer2"]["similarity_percent"] for r in batch_results if r.get("layer2")]
    avg_sim = sum(sims) / len(sims) if sims else 0.0

    return jsonify({
        "results": batch_results,
        "summary": {
            "total": len(batch_results),
            "compliant": compliant,
            "non_compliant": non_compliant,
            "needs_review": needs_review,
            "layer1_failures": l1_failures,
            "layer2_failures": l2_failures,
            "layer3_failures": l3_failures,
            "average_similarity_percent": round(avg_sim, 1),
            "processing_time_total_ms": total_ms,
        },
    })


VERDICT_ORDER = {"NON_COMPLIANT": 0, "NEEDS_REVIEW": 1, "COMPLIANT": 2}


@app.route("/results", methods=["GET"])
def get_results():
    sorted_results = sorted(
        results,
        key=lambda r: VERDICT_ORDER.get(r.get("final", {}).get("final_verdict", ""), 99),
    )
    return jsonify(sorted_results)


@app.route("/reference", methods=["GET"])
def get_reference():
    if not reference["zss_tree"]:
        return jsonify({"status": "no reference set"})
    safe = {k: v for k, v in reference.items() if k != "zss_tree"}
    safe_metrics = {
        k: v for k, v in safe.get("metrics", {}).items()
        if k not in ("classes", "interfaces", "methods", "fields")
    }
    return jsonify({
        "status": "reference set",
        "node_count": safe["node_count"],
        "guidelines": safe["guidelines"],
        "profile": safe["profile"],
        "uploaded_at": safe["uploaded_at"],
        "metrics": safe_metrics,
    })


@app.route("/reset", methods=["DELETE"])
def reset():
    reference["zss_tree"] = None
    reference["node_count"] = 0
    reference["metrics"] = {}
    reference["guidelines"] = ""
    reference["profile"] = {}
    reference["uploaded_at"] = None
    results.clear()
    return jsonify({"status": "reset complete"})


# ── Frontend ──────────────────────────────────────────────────────────────────

HTML = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>OOP Compliance Screener</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{background:#0f1117;color:#e2e8f0;font-family:system-ui,sans-serif;min-height:100vh}
.header{background:#1a1d2e;border-bottom:1px solid #2d3155;padding:1.2rem 2rem;display:flex;align-items:center;gap:1rem}
.header h1{font-size:1.4rem;color:#fff}
.header span{color:#6c63ff;font-weight:700}
.badge-small{background:#6c63ff22;color:#6c63ff;border:1px solid #6c63ff44;border-radius:6px;padding:2px 8px;font-size:.75rem}
.container{max-width:1200px;margin:0 auto;padding:2rem}
.card{background:#1a1d2e;border:1px solid #2d3155;border-radius:12px;padding:1.5rem;margin-bottom:1.5rem}
.card h2{font-size:1.1rem;margin-bottom:1rem;color:#a5b4fc}
.section-title{color:#6c63ff;font-size:.8rem;text-transform:uppercase;letter-spacing:.1em;margin-bottom:.5rem}
.two-col{display:grid;grid-template-columns:1fr 1fr;gap:1.5rem}
label{display:block;color:#94a3b8;font-size:.85rem;margin-bottom:.4rem}
input[type=text],textarea,input[type=file]{width:100%;background:#0f1117;border:1px solid #2d3155;border-radius:8px;padding:.6rem .8rem;color:#e2e8f0;font-size:.9rem;outline:none}
input[type=text]:focus,textarea:focus{border-color:#6c63ff}
textarea{resize:vertical;font-family:monospace}
.btn{display:inline-flex;align-items:center;gap:.4rem;background:#6c63ff;color:#fff;border:none;border-radius:8px;padding:.6rem 1.2rem;font-size:.9rem;cursor:pointer;transition:background .15s}
.btn:hover{background:#7c73ff}
.btn:disabled{background:#3d3560;cursor:not-allowed}
.btn-outline{background:transparent;border:1px solid #6c63ff;color:#6c63ff}
.btn-outline:hover{background:#6c63ff22}
.tabs{display:flex;gap:.5rem;margin-bottom:1rem;border-bottom:1px solid #2d3155;padding-bottom:.5rem}
.tab{background:none;border:none;color:#94a3b8;padding:.5rem 1rem;cursor:pointer;border-radius:8px 8px 0 0;font-size:.9rem}
.tab.active{color:#a5b4fc;background:#6c63ff22;border-bottom:2px solid #6c63ff}
.tab-panel{display:none}.tab-panel.active{display:block}
.row{display:flex;gap:1rem;align-items:flex-end;flex-wrap:wrap;margin-bottom:1rem}
.progress-wrap{background:#0f1117;border-radius:8px;height:8px;overflow:hidden;margin:1rem 0}
.progress-bar{background:#6c63ff;height:100%;transition:width .3s}
.results-table{width:100%;border-collapse:collapse;font-size:.85rem}
.results-table th{color:#64748b;font-weight:500;text-align:left;padding:.6rem .8rem;border-bottom:1px solid #2d3155}
.results-table td{padding:.6rem .8rem;border-bottom:1px solid #1e2235;vertical-align:middle}
.results-table tr:hover td{background:#1e2235;cursor:pointer}
.badge{display:inline-block;border-radius:6px;padding:2px 10px;font-size:.78rem;font-weight:600}
.COMPLIANT{background:#22c55e22;color:#22c55e;border:1px solid #22c55e44}
.NON_COMPLIANT{background:#ef444422;color:#ef4444;border:1px solid #ef444444}
.NEEDS_REVIEW{background:#f59e0b22;color:#f59e0b;border:1px solid #f59e0b44}
.l-pass{color:#22c55e;font-weight:600}
.l-fail{color:#ef4444;font-weight:600}
.l-skip{color:#64748b}
.sim-bar-wrap{display:flex;align-items:center;gap:.5rem;min-width:120px}
.sim-bar{height:6px;border-radius:3px;flex:1}
.sim-high{background:#22c55e}.sim-mid{background:#f59e0b}.sim-low{background:#ef4444}
.sim-num{font-size:.8rem;min-width:36px;text-align:right}
/* Modal */
.modal-overlay{display:none;position:fixed;inset:0;background:#0008;z-index:100;align-items:center;justify-content:center}
.modal-overlay.open{display:flex}
.modal{background:#1a1d2e;border:1px solid #2d3155;border-radius:14px;width:min(860px,96vw);max-height:90vh;overflow-y:auto;padding:2rem;position:relative}
.modal-close{position:absolute;top:1rem;right:1rem;background:none;border:none;color:#64748b;font-size:1.4rem;cursor:pointer}
.modal-close:hover{color:#fff}
.modal h3{color:#a5b4fc;margin-bottom:1rem;font-size:1.1rem}
.layer-block{margin-bottom:1.5rem}
.layer-block h4{color:#6c63ff;font-size:.8rem;text-transform:uppercase;letter-spacing:.08em;margin-bottom:.7rem}
.check-list{list-style:none;font-size:.85rem}
.check-list li{padding:.25rem 0;display:flex;gap:.5rem;align-items:flex-start}
.check-pass::before{content:"✓";color:#22c55e;flex-shrink:0}
.check-fail::before{content:"✗";color:#ef4444;flex-shrink:0}
.check-warn::before{content:"⚠";color:#f59e0b;flex-shrink:0}
.oop-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:.8rem}
.oop-card{background:#0f1117;border:1px solid #2d3155;border-radius:8px;padding:.8rem}
.oop-card h5{color:#94a3b8;font-size:.8rem;margin-bottom:.4rem;text-transform:capitalize}
.oop-card .flags{display:flex;gap:.4rem;flex-wrap:wrap;margin-bottom:.3rem}
.flag{font-size:.7rem;padding:1px 6px;border-radius:4px}
.flag-yes{background:#22c55e22;color:#22c55e}
.flag-no{background:#ef444422;color:#ef4444}
.oop-card .comment{color:#64748b;font-size:.75rem;line-height:1.4}
.textbox{background:#0f1117;border:1px solid #2d3155;border-radius:8px;padding:.8rem;font-size:.85rem;color:#94a3b8;line-height:1.6}
.textbox h5{color:#a5b4fc;margin-bottom:.5rem;font-size:.85rem}
.info-row{display:flex;gap:2rem;flex-wrap:wrap;font-size:.85rem;margin-bottom:.8rem}
.info-row span{color:#64748b}
.info-row strong{color:#e2e8f0}
.big-bar-wrap{background:#0f1117;border-radius:8px;height:14px;overflow:hidden;margin:.5rem 0 .3rem}
.big-bar{height:100%;border-radius:8px;transition:width .5s}
.ref-summary{background:#0f1117;border:1px solid #2d3155;border-radius:8px;padding:1rem;margin-top:1rem;font-size:.85rem}
.ref-summary dl{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:.4rem}
.ref-summary dt{color:#64748b}
.ref-summary dd{color:#e2e8f0;font-weight:500}
.alert{border-radius:8px;padding:.8rem 1rem;font-size:.85rem;margin:.8rem 0}
.alert-err{background:#ef444411;border:1px solid #ef444433;color:#ef4444}
.alert-ok{background:#22c55e11;border:1px solid #22c55e33;color:#22c55e}
.hidden{display:none}
.vibe-list{font-size:.82rem;color:#f59e0b;list-style:disc;padding-left:1.2rem}
.vibe-list li{margin:.2rem 0}
</style>
</head>
<body>
<div class="header">
  <h1><span>OOP</span> Compliance Screener</h1>
  <span class="badge-small">POC · University of Messina</span>
</div>
<div class="container">

  <!-- SETUP -->
  <div class="card" id="setupSection">
    <div class="section-title">Step 1 — Initialize System</div>
    <h2>Professor Reference Upload</h2>
    <div class="two-col">
      <div>
        <label>Professor Reference Solution (.zip)</label>
        <input type="file" id="refZip" accept=".zip"/>
      </div>
      <div>
        <label>Course Guidelines</label>
        <textarea id="guidelines" rows="10" placeholder="Paste the full assignment guidelines here…"></textarea>
      </div>
    </div>
    <button class="btn" style="margin-top:1rem" id="initBtn" onclick="initSystem()">Initialize System</button>
    <div id="setupAlert"></div>
    <div id="refSummary" class="ref-summary hidden"></div>
  </div>

  <!-- SUBMIT -->
  <div class="card hidden" id="submitSection">
    <div class="section-title">Step 2 — Student Submissions</div>
    <h2>Submit for Evaluation</h2>
    <div class="tabs">
      <button class="tab active" onclick="switchTab('single',this)">Single Submission</button>
      <button class="tab" onclick="switchTab('batch',this)">Batch Submission</button>
    </div>

    <div class="tab-panel active" id="tab-single">
      <div class="row">
        <div style="flex:1"><label>Student Name</label><input type="text" id="studentName" placeholder="e.g. Mario Rossi"/></div>
        <div style="flex:1"><label>Submission ZIP</label><input type="file" id="subZip" accept=".zip"/></div>
        <button class="btn" onclick="submitSingle()">Submit</button>
      </div>
      <div id="singleAlert"></div>
      <div id="singleResult"></div>
    </div>

    <div class="tab-panel" id="tab-batch">
      <div class="row">
        <div style="flex:1"><label>Student ZIPs (multiple)</label><input type="file" id="batchZips" accept=".zip" multiple/></div>
        <button class="btn" onclick="submitBatch()">Submit All</button>
      </div>
      <div id="batchProgress" class="hidden">
        <div class="progress-wrap"><div class="progress-bar" id="batchBar" style="width:0%"></div></div>
        <div id="batchStatus" style="font-size:.85rem;color:#94a3b8;text-align:center"></div>
      </div>
      <div id="batchSummary"></div>
    </div>
  </div>

  <!-- RESULTS -->
  <div class="card hidden" id="resultsSection">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:1rem">
      <div><div class="section-title">Step 3 — Results</div><h2>Submission Results</h2></div>
      <div style="display:flex;gap:.5rem">
        <button class="btn btn-outline" onclick="loadResults()">Refresh</button>
        <button class="btn btn-outline" style="border-color:#ef4444;color:#ef4444" onclick="resetSystem()">Reset System</button>
      </div>
    </div>
    <table class="results-table">
      <thead><tr>
        <th>#</th><th>Student</th><th>Files</th>
        <th>L1</th><th>L2 Similarity</th><th>L3</th>
        <th>Verdict</th><th>Time</th>
      </tr></thead>
      <tbody id="resultsBody"></tbody>
    </table>
  </div>
</div>

<!-- MODAL -->
<div class="modal-overlay" id="modal" onclick="closeModal(event)">
  <div class="modal">
    <button class="modal-close" onclick="closeModalBtn()">✕</button>
    <div id="modalContent"></div>
  </div>
</div>

<script>
let currentDetail = null;

async function initSystem(){
  const zip = document.getElementById('refZip').files[0];
  const guide = document.getElementById('guidelines').value.trim();
  const alertEl = document.getElementById('setupAlert');
  alertEl.innerHTML = '';
  if(!zip){alertEl.innerHTML='<div class="alert alert-err">Please select a reference ZIP.</div>';return;}
  if(!guide){alertEl.innerHTML='<div class="alert alert-err">Please enter course guidelines.</div>';return;}
  const btn = document.getElementById('initBtn');
  btn.disabled=true; btn.textContent='Initializing…';
  const fd = new FormData();
  fd.append('reference_zip', zip);
  fd.append('guidelines', guide);
  try{
    const r = await fetch('/setup',{method:'POST',body:fd});
    const d = await r.json();
    if(!r.ok || d.error){
      alertEl.innerHTML=`<div class="alert alert-err">${d.error||'Setup failed'}</div>`;
      return;
    }
    alertEl.innerHTML='<div class="alert alert-ok">System initialized successfully.</div>';
    showRefSummary(d);
    document.getElementById('submitSection').classList.remove('hidden');
    document.getElementById('resultsSection').classList.remove('hidden');
    document.getElementById('setupSection').style.opacity='.6';
  }catch(e){
    alertEl.innerHTML=`<div class="alert alert-err">Network error: ${e.message}</div>`;
  }finally{
    btn.disabled=false; btn.textContent='Initialize System';
  }
}

function showRefSummary(d){
  const m = d.metrics; const p = d.profile;
  const el = document.getElementById('refSummary');
  el.innerHTML=`<strong style="color:#a5b4fc">Reference Metrics</strong>
  <dl style="margin-top:.5rem">
    <div><dt>Classes</dt><dd>${m.class_count}</dd></div>
    <div><dt>Interfaces</dt><dd>${m.interface_count}</dd></div>
    <div><dt>Methods</dt><dd>${m.method_count}</dd></div>
    <div><dt>Fields</dt><dd>${m.field_count}</dd></div>
    <div><dt>Inheritance</dt><dd>${m.inheritance_count}</dd></div>
    <div><dt>Overrides</dt><dd>${m.override_count}</dd></div>
    <div><dt>Private fields</dt><dd>${m.private_field_count}</dd></div>
    <div><dt>Abstract classes</dt><dd>${m.abstract_class_count}</dd></div>
  </dl>
  <div style="margin-top:.7rem;color:#64748b;font-size:.8rem">Profile: ${p.notes||'—'} | Files parsed: ${d.files_parsed}</div>`;
  el.classList.remove('hidden');
}

function switchTab(name, el){
  document.querySelectorAll('.tab').forEach(t=>t.classList.remove('active'));
  document.querySelectorAll('.tab-panel').forEach(p=>p.classList.remove('active'));
  el.classList.add('active');
  document.getElementById('tab-'+name).classList.add('active');
}

async function submitSingle(){
  const name = document.getElementById('studentName').value.trim()||'Unknown';
  const zip = document.getElementById('subZip').files[0];
  const alertEl = document.getElementById('singleAlert');
  alertEl.innerHTML='';
  if(!zip){alertEl.innerHTML='<div class="alert alert-err">Please select a ZIP file.</div>';return;}
  alertEl.innerHTML='<div class="alert" style="background:#6c63ff11;border:1px solid #6c63ff33;color:#a5b4fc">Evaluating…</div>';
  const fd = new FormData();
  fd.append('submission_zip', zip);
  fd.append('student_name', name);
  try{
    const r = await fetch('/submit',{method:'POST',body:fd});
    const d = await r.json();
    if(!r.ok || d.error){alertEl.innerHTML=`<div class="alert alert-err">${d.error}</div>`;return;}
    alertEl.innerHTML='';
    document.getElementById('singleResult').innerHTML = renderResultCard(d);
    loadResults();
  }catch(e){
    alertEl.innerHTML=`<div class="alert alert-err">Network error: ${e.message}</div>`;
  }
}

async function submitBatch(){
  const files = document.getElementById('batchZips').files;
  if(!files.length){return;}
  const prog = document.getElementById('batchProgress');
  const bar = document.getElementById('batchBar');
  const status = document.getElementById('batchStatus');
  prog.classList.remove('hidden');
  const allResults=[];
  for(let i=0;i<files.length;i++){
    status.textContent=`Processing ${i+1} of ${files.length}: ${files[i].name}`;
    bar.style.width=((i/files.length)*100)+'%';
    const fd=new FormData();
    fd.append('submission_zip', files[i]);
    fd.append('student_name', files[i].name.replace(/\.zip$/i,''));
    const r = await fetch('/submit',{method:'POST',body:fd});
    const d = await r.json();
    allResults.push(d);
  }
  bar.style.width='100%';
  status.textContent=`Done — ${files.length} submission(s) processed.`;
  renderBatchSummary(allResults);
  loadResults();
}

function renderBatchSummary(results){
  const c=results.filter(r=>r.final?.final_verdict==='COMPLIANT').length;
  const nc=results.filter(r=>r.final?.final_verdict==='NON_COMPLIANT').length;
  const nr=results.filter(r=>r.final?.final_verdict==='NEEDS_REVIEW').length;
  document.getElementById('batchSummary').innerHTML=`
  <div style="display:flex;gap:1rem;margin-top:1rem;flex-wrap:wrap">
    <div class="card" style="flex:1;min-width:130px;text-align:center;padding:1rem">
      <div style="font-size:1.8rem;font-weight:700;color:#22c55e">${c}</div>
      <div style="color:#64748b;font-size:.8rem">COMPLIANT</div>
    </div>
    <div class="card" style="flex:1;min-width:130px;text-align:center;padding:1rem">
      <div style="font-size:1.8rem;font-weight:700;color:#ef4444">${nc}</div>
      <div style="color:#64748b;font-size:.8rem">NON_COMPLIANT</div>
    </div>
    <div class="card" style="flex:1;min-width:130px;text-align:center;padding:1rem">
      <div style="font-size:1.8rem;font-weight:700;color:#f59e0b">${nr}</div>
      <div style="color:#64748b;font-size:.8rem">NEEDS_REVIEW</div>
    </div>
