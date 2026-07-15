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
