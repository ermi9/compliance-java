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
