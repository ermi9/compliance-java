package dev.praxis.checks;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import dev.praxis.checks.MutabilityHeuristics.Mutability;
import dev.praxis.core.check.Check;
import dev.praxis.core.check.CheckIds;
import dev.praxis.core.check.CheckOptions;
import dev.praxis.core.check.CheckReport;
import dev.praxis.core.facts.FactModel;
import dev.praxis.core.facts.FieldFact;
import dev.praxis.core.facts.MethodFact;
import dev.praxis.core.facts.TypeFact;
import dev.praxis.core.model.Finding;
import dev.praxis.core.model.TriState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Atomic check: a getter must not return a mutable internal reference directly.
 *
 * <p><b>Conservative by design (invariant 1).</b> A subject getter is a {@code public}, non-static,
 * zero-arg method that returns a reference type via a single {@code return} statement. Only when the
 * returned expression <em>provably aliases</em> an internal, well-known-mutable field is
 * {@code VIOLATION} emitted. When the returned value is a provable defensive copy or an immutable
 * type, the subject is {@code SATISFIED}. In every ambiguous case — the field's type mutability is
 * unknown, or the expression is not analyzable — the subject is {@code UNDETERMINED}, never a verdict.
 */
public final class LeakyGetterCheck implements Check {

    // Method names that provably return a fresh/immutable view rather than the internal reference.
    private static final Set<String> SAFE_COPY_METHODS = Set.of(
            "copyOf", "copyOfRange", "clone",
            "unmodifiableList", "unmodifiableSet", "unmodifiableMap", "unmodifiableCollection",
            "unmodifiableSortedSet", "unmodifiableSortedMap", "unmodifiableNavigableSet",
            "unmodifiableNavigableMap");

    @Override
    public String id() {
        return CheckIds.NO_LEAKED_MUTABLE_INTERNAL;
    }

    @Override
    public String description() {
        return "A getter must return a defensive copy, not a mutable internal reference.";
    }

    @Override
    public CheckReport evaluate(FactModel facts, CheckOptions options) {
        List<TriState> subjects = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();

        // Index project types so a getter returning a domain object can be classified from the
        // domain type's OWN definition rather than punting to UNDETERMINED.
        Map<String, TypeFact> projectTypes = new HashMap<>();
        for (TypeFact type : facts.types()) {
            projectTypes.putIfAbsent(type.simpleName(), type);
        }

        for (TypeFact type : facts.types()) {
            Map<String, FieldFact> instanceFields = new HashMap<>();
            for (FieldFact f : type.fields()) {
                if (f.isInstanceField()) {
                    instanceFields.put(f.name(), f);
                }
            }
            for (MethodFact method : type.methods()) {
                if (!isGetterCandidate(method)) {
                    continue; // not a shape this check makes claims about
                }
                Optional<Expression> returned = singleReturnExpression(method.node());
                if (returned.isEmpty()) {
                    continue; // not a single-return getter — no claim
                }
                Subject subject = classifyReturn(returned.get(), instanceFields, projectTypes);
                subjects.add(subject.state());
                if (subject.state() != TriState.SATISFIED) {
                    findings.add(new Finding(
                            id(),
                            subject.state(),
                            method.source().file(),
                            method.source().line(),
                            method.source().column(),
                            method.source().snippet(),
                            subject.explanation(method)));
                }
            }
        }
        return CheckReport.of(id(), subjects, findings);
    }

    private static boolean isGetterCandidate(MethodFact m) {
        return m.isPublic()
                && !m.isStatic()
                && m.parameterCount() == 0
                && !m.returnIsVoid()
                && !m.returnIsPrimitive(); // primitives can't alias internal mutable state
    }

    private static Optional<Expression> singleReturnExpression(MethodDeclaration node) {
        Optional<BlockStmt> body = node.getBody();
        if (body.isEmpty()) {
            return Optional.empty(); // abstract/interface method — no body to analyze
        }
        List<Statement> statements = body.get().getStatements();
        if (statements.size() != 1 || !statements.get(0).isReturnStmt()) {
            return Optional.empty();
        }
        return ((ReturnStmt) statements.get(0)).getExpression();
    }

    private Subject classifyReturn(Expression expr, Map<String, FieldFact> instanceFields, Map<String, TypeFact> projectTypes) {
        Optional<String> fieldName = returnedFieldName(expr);
        if (fieldName.isPresent()) {
            FieldFact field = instanceFields.get(fieldName.get());
            if (field == null) {
                // A bare name that is not one of this type's instance fields (inherited/static/etc.);
                // cannot prove it aliases internal mutable state.
                return Subject.undetermined(null, "returned symbol is not a resolvable instance field");
            }
            Mutability mutability = MutabilityHeuristics.classify(field.simpleTypeName(), field.isArray());
            if (mutability == Mutability.UNKNOWN) {
                // Fall back to the field type's OWN project definition, if we have it.
                mutability = classifyProjectType(field.simpleTypeName(), projectTypes, new HashSet<>());
            }
            return switch (mutability) {
                case MUTABLE -> Subject.violation(field);
                case IMMUTABLE -> Subject.satisfied();
                case UNKNOWN -> Subject.undetermined(field,
                        "mutability of field type '" + field.declaredTypeName() + "' is unknown");
            };
        }
        if (isProvablySafeCopy(expr)) {
            return Subject.satisfied();
        }
        // Some other expression (computed value, another object's member, ternary, ...).
        return Subject.undetermined(null, "returned expression could not be proven to alias or copy internal state");
    }

    /** The name of the instance field this expression returns directly, if it is {@code name} or {@code this.name}. */
    private static Optional<String> returnedFieldName(Expression expr) {
        if (expr.isNameExpr()) {
            return Optional.of(((NameExpr) expr).getNameAsString());
        }
        if (expr.isFieldAccessExpr()) {
            FieldAccessExpr fa = (FieldAccessExpr) expr;
            if (fa.getScope() instanceof ThisExpr) {
                return Optional.of(fa.getNameAsString());
            }
        }
        return Optional.empty();
    }

    /** Names of Java primitives, treated as immutable field types. */
    private static final Set<String> PRIMITIVES = Set.of(
            "int", "long", "short", "byte", "char", "boolean", "double", "float");

    /**
     * Classifies a project type's mutability from its own definition: any non-final instance field, or
     * a final field of a mutable type, makes it MUTABLE; all-final fields of provably-immutable types
     * make it IMMUTABLE; anything unresolved stays UNKNOWN. The {@code visiting} set guards recursion.
     */
    private static Mutability classifyProjectType(String simpleTypeName, Map<String, TypeFact> projectTypes, Set<String> visiting) {
        TypeFact type = projectTypes.get(simpleTypeName);
        if (type == null || !visiting.add(simpleTypeName)) {
            return Mutability.UNKNOWN; // not a project type, or a cycle — do not guess
        }
        boolean allImmutable = true;
        for (FieldFact f : type.fields()) {
            if (!f.isInstanceField()) {
                continue;
            }
            if (!f.isFinal()) {
                return Mutability.MUTABLE; // a reassignable field ⇒ the object is mutable
            }
            Mutability fieldMutability = classifyFieldType(f, projectTypes, visiting);
            if (fieldMutability == Mutability.MUTABLE) {
                return Mutability.MUTABLE; // final ref to a mutable component still leaks
            }
            if (fieldMutability == Mutability.UNKNOWN) {
                allImmutable = false;
            }
        }
        return allImmutable ? Mutability.IMMUTABLE : Mutability.UNKNOWN;
    }

    private static Mutability classifyFieldType(FieldFact f, Map<String, TypeFact> projectTypes, Set<String> visiting) {
        if (!f.isArray() && PRIMITIVES.contains(f.simpleTypeName())) {
            return Mutability.IMMUTABLE;
        }
        Mutability jdk = MutabilityHeuristics.classify(f.simpleTypeName(), f.isArray());
        if (jdk != Mutability.UNKNOWN) {
            return jdk;
        }
        return classifyProjectType(f.simpleTypeName(), projectTypes, visiting);
    }

    private static boolean isProvablySafeCopy(Expression expr) {
        if (expr.isObjectCreationExpr()) {
            return true; // `new ArrayList<>(field)` etc. — a fresh object
        }
        if (expr.isMethodCallExpr()) {
            MethodCallExpr call = (MethodCallExpr) expr;
            return SAFE_COPY_METHODS.contains(call.getNameAsString());
        }
        return false;
    }

    /** Internal carrier for a subject's verdict plus the field (if any) it concerns. */
    private record Subject(TriState state, FieldFact field, String reason) {
        static Subject violation(FieldFact field) {
            return new Subject(TriState.VIOLATION, field, null);
        }

        static Subject satisfied() {
            return new Subject(TriState.SATISFIED, null, null);
        }

        static Subject undetermined(FieldFact field, String reason) {
            return new Subject(TriState.UNDETERMINED, field, reason);
        }

        String explanation(MethodFact method) {
            if (state == TriState.VIOLATION) {
                return "Getter '" + method.name() + "()' returns internal mutable field '"
                        + field.name() + "' (type " + field.declaredTypeName()
                        + ") directly; callers can mutate internal state. Return a defensive copy instead.";
            }
            return "Could not determine whether getter '" + method.name() + "()' leaks internal state ("
                    + reason + "); left UNDETERMINED rather than risk a false positive.";
        }
    }
}
