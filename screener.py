#!/usr/bin/env python3
"""
Compliance Screener--under dev(proof of concept phase)
Scans Java source files for compliance and banned AI-favored syntax.
Usage: python3 screener.py <path-to-java-file-or-folder>
"""

import re
import os
import sys
from dataclasses import dataclass, field
from typing import List, Tuple

# ANSI COLORS 
GREEN  = "\033[92m"
RED    = "\033[91m"
YELLOW = "\033[93m"
RESET  = "\033[0m"
BOLD   = "\033[1m"

def tag_pass(s): return f"{GREEN}[PASS]{RESET}"
def tag_fail(s): return f"{RED}[FAIL]{RESET}"
def tag_warn(s): return f"{YELLOW}[WARN]{RESET}"

# VIOLATION 
@dataclass
class Violation:
    kind: str          # PASS / FAIL / WARN
    category: str
    message: str
    line: int = -1

    def __str__(self):
        tag = {"PASS": tag_pass, "FAIL": tag_fail, "WARN": tag_warn}[self.kind](self.kind)
        line_info = f" (Line {self.line})" if self.line > 0 else ""
        return f"  {tag} {self.category:<36} {self.message}{line_info}"

#  HELPERS 
def read_lines(path: str) -> List[str]:
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.readlines()

def find_pattern(lines, pattern, flags=0) -> List[Tuple[int, str]]:
    """Return list of (line_number, matched_line) for all matches."""
    results = []
    rx = re.compile(pattern, flags)
    for i, line in enumerate(lines, start=1):
        if rx.search(line):
            results.append((i, line.rstrip()))
    return results

def strip_comments(lines: List[str]) -> List[str]:
    """Remove single-line comments for cleaner analysis."""
    result = []
    in_block = False
    for line in lines:
        if in_block:
            if "*/" in line:
                in_block = False
                line = line[line.index("*/") + 2:]
            else:
                result.append("\n")
                continue
        if "/*" in line:
            before = line[:line.index("/*")]
            after_close = line[line.index("/*"):]
            if "*/" in after_close:
                line = before + after_close[after_close.index("*/") + 2:]
            else:
                in_block = True
                line = before
        line = re.sub(r"//.*", "", line)
        result.append(line)
    return result

#  CHECKER 
def check_file(path: str) -> List[Violation]:
    violations = []
    try:
        raw_lines = read_lines(path)
    except Exception as e:
        return [Violation("FAIL", "Parse Error", str(e))]

    lines = strip_comments(raw_lines)

    #  BANNED SYNTAX 

    # Records
    for ln, text in find_pattern(lines, r"\brecord\s+\w+\s*\("):
        violations.append(Violation("FAIL", "Banned: Record",
            f"Record declaration not allowed", ln))

    # Lambdas
    for ln, text in find_pattern(lines, r"->"):
        # Avoid false positives on -> in comments or generics
        if not re.search(r"<[^>]*->[^>]*>", text):
            violations.append(Violation("FAIL", "Banned: Lambda",
                "Lambda expression '->' not allowed", ln))

    # Stream API
    stream_methods = r"\.(stream|parallelStream|filter|flatMap|collect|reduce)\s*\("
    for ln, text in find_pattern(lines, stream_methods):
        method = re.search(stream_methods, text).group(1)
        violations.append(Violation("FAIL", "Banned: Stream API",
            f"Stream method '.{method}()' not allowed", ln))

    #  ENCAPSULATION 
    public_fields = find_pattern(lines,
        r"^\s*(public)\s+(static\s+)?(final\s+)?(int|double|float|long|String|boolean|char|byte|short|\w+)\s+\w+\s*[;=]")
    # Filter out static final constants (those are OK) and method signatures
    real_public_fields = []
    for ln, text in public_fields:
        if "(" not in text and "static final" not in text and "void" not in text:
            real_public_fields.append((ln, text))

    if real_public_fields:
        for ln, text in real_public_fields:
            field_name = re.search(r"\b(\w+)\s*[;=]", text)
            name = field_name.group(1) if field_name else "unknown"
            violations.append(Violation("FAIL", "Encapsulation",
                f"Public field '{name}' violates encapsulation", ln))
    else:
        private_fields = find_pattern(lines,
            r"^\s*private\s+(static\s+)?(final\s+)?(\w+)\s+\w+\s*[;=]")
        getter_setter = find_pattern(lines,
            r"public\s+\w+\s+(get|set|is)\w+\s*\(")
        if private_fields and getter_setter:
            violations.append(Violation("PASS", "Encapsulation",
                "Private fields with getters/setters detected"))
        elif private_fields:
            violations.append(Violation("WARN", "Encapsulation",
                "Private fields found but no getters/setters detected"))
        else:
            violations.append(Violation("WARN", "Encapsulation",
                "No fields detected — encapsulation not demonstrated"))

    #  INHERITANCE 
    extends_hits = find_pattern(lines, r"\bclass\s+\w+.*\bextends\s+(\w+)")
    if extends_hits:
        for ln, text in extends_hits:
            m = re.search(r"\bextends\s+(\w+)", text)
            parent = m.group(1) if m else "unknown"
            cls = re.search(r"\bclass\s+(\w+)", text)
            cls_name = cls.group(1) if cls else "unknown"
            violations.append(Violation("PASS", "Inheritance",
                f"'{cls_name}' extends '{parent}'", ln))
    else:
        violations.append(Violation("WARN", "Inheritance",
            "No 'extends' detected — inheritance not demonstrated"))

    #  SUBTYPING 
    implements_hits = find_pattern(lines, r"\bclass\s+\w+.*\bimplements\s+([\w,\s]+)")
    if implements_hits:
        for ln, text in implements_hits:
            m = re.search(r"\bimplements\s+([\w,\s]+)", text)
            ifaces = m.group(1).strip() if m else "unknown"
            cls = re.search(r"\bclass\s+(\w+)", text)
            cls_name = cls.group(1) if cls else "unknown"
            violations.append(Violation("PASS", "Subtyping",
                f"'{cls_name}' implements {ifaces}", ln))
    else:
        violations.append(Violation("WARN", "Subtyping",
            "No 'implements' detected — subtyping not demonstrated"))

    #  OVERLOADING 
    method_names = re.findall(r"^\s*(public|protected|private)\s+\S+\s+(\w+)\s*\(", 
                               "".join(lines), re.MULTILINE)
    name_counts = {}
    for _, name in method_names:
        name_counts[name] = name_counts.get(name, 0) + 1
    
    overloaded = {n: c for n, c in name_counts.items() if c > 1}
    if overloaded:
        for name, count in overloaded.items():
            violations.append(Violation("PASS", "Overloading",
                f"Method '{name}' overloaded {count} times"))
    
    #  PARAMETRIC POLYMORPHISM 
    generic_class = find_pattern(lines, r"\bclass\s+\w+\s*<\w")
    generic_method = find_pattern(lines, r"^\s*(public|protected|private)\s+<\w")
    
    if generic_class:
        for ln, text in generic_class:
            m = re.search(r"\bclass\s+(\w+\s*<[^>]+>)", text)
            name = m.group(1) if m else "generic class"
            violations.append(Violation("PASS", "Parametric Polymorphism",
                f"Generic class '{name.strip()}' detected", ln))
    elif generic_method:
        violations.append(Violation("PASS", "Parametric Polymorphism",
            "Generic method detected"))
    else:
        # Check if they use generic types in fields/params (softer check)
        uses_generics = find_pattern(lines, r"(List|Map|Set|Queue|Optional)<\w")
        if uses_generics:
            violations.append(Violation("WARN", "Parametric Polymorphism",
                "Generic collections used but no custom generic class/method defined"))
        else:
            violations.append(Violation("WARN", "Parametric Polymorphism",
                "No generic types detected"))

    #  OVERRIDING / RUNTIME POLYMORPHISM 
    override_hits = find_pattern(lines, r"@Override")
    if override_hits:
        # Find the method name after @Override
        for ln, _ in override_hits:
            if ln < len(lines):
                next_lines = "".join(lines[ln:ln+3])
                m = re.search(r"(\w+)\s*\(", next_lines)
                method_name = m.group(1) if m else "unknown"
                violations.append(Violation("PASS", "Overriding",
                    f"@Override on '{method_name}()' detected", ln))
    else:
        violations.append(Violation("WARN", "Overriding",
            "No @Override annotations — runtime polymorphism not demonstrated"))

    return violations

#  REPORT PRINTER 
def print_report(file_results: List[Tuple[str, List[Violation]]]):
    border = BOLD + "=" * 65 + RESET
    thin   = "-" * 65

    print(f"\n{border}")
    print(f"{BOLD}   OOP COMPLIANCE SCREENER — University of Messina{RESET}")
    print(border)

    total_pass = total_fail = total_warn = 0

    for filename, violations in file_results:
        print(f"\n  {BOLD}File: {filename}{RESET}")
        print(f"  {thin}")

        passes = [v for v in violations if v.kind == "PASS"]
        fails  = [v for v in violations if v.kind == "FAIL"]
        warns  = [v for v in violations if v.kind == "WARN"]

        # Print FAILs first so professor sees problems immediately
        for v in fails + warns + passes:
            print(str(v))

        total_pass += len(passes)
        total_fail += len(fails)
        total_warn += len(warns)

        print(f"  {thin}")
        print(f"  Score: {GREEN}{len(passes)} passed{RESET}, "
              f"{RED}{len(fails)} failed{RESET}, "
              f"{YELLOW}{len(warns)} warnings{RESET}")

    print(f"\n{border}")
    print(f"{BOLD}  SUMMARY{RESET}")
    print(f"  {thin}")
    print(f"  Files scanned : {len(file_results)}")
    print(f"  Total {GREEN}PASS{RESET}    : {total_pass}")
    print(f"  Total {RED}FAIL{RESET}    : {total_fail}")
    print(f"  Total {YELLOW}WARN{RESET}    : {total_warn}")
    print()

    if total_fail == 0:
        print(f"  {GREEN}{BOLD}VERDICT: COMPLIANT — Ready for professor review{RESET}")
    else:
        print(f"  {RED}{BOLD}VERDICT: NON-COMPLIANT — Fix violations before resubmitting{RESET}")
    print(border + "\n")

#  MAIN 
def collect_java_files(path: str) -> List[str]:
    if os.path.isfile(path) and path.endswith(".java"):
        return [path]
    result = []
    for root, dirs, files in os.walk(path):
        for f in files:
            if f.endswith(".java"):
                result.append(os.path.join(root, f))
    return sorted(result)

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 screener.py <path-to-java-file-or-folder>")
        sys.exit(1)

    target = sys.argv[1]
    if not os.path.exists(target):
        print(f"[ERROR] Path not found: {target}")
        sys.exit(1)

    java_files = collect_java_files(target)
    if not java_files:
        print(f"[ERROR] No .java files found in: {target}")
        sys.exit(1)

    results = []
    for f in java_files:
        violations = check_file(f)
        results.append((os.path.basename(f), violations))

    print_report(results)

if __name__ == "__main__":
    main()
