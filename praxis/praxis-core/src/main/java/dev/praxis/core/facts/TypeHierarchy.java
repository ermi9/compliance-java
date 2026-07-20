package dev.praxis.core.facts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A Layer-1 view over the project's type graph, resolved among <em>project-declared</em> types only
 * (library supertypes like {@code JPanel} are outside this graph and simply not matched). It answers
 * the structural questions the inheritance/subtyping/polymorphism checks need — ancestors, overrides,
 * subtypes — without executing anything.
 *
 * <p>Resolution is by simple type name (the form captured in {@code extends}/{@code implements}). In
 * the rare case of two project types sharing a simple name, the first is used; this only ever affects
 * <em>positive</em> evidence, and override matching additionally requires a matching erased method
 * signature, so it does not manufacture verdicts.
 */
public final class TypeHierarchy {

    private final List<TypeFact> types;
    private final Map<String, List<TypeFact>> bySimpleName = new HashMap<>();
    private final Map<String, Set<String>> ancestorSimpleNames = new HashMap<>();

    public TypeHierarchy(List<TypeFact> types) {
        this.types = List.copyOf(types);
        for (TypeFact t : this.types) {
            bySimpleName.computeIfAbsent(t.simpleName(), k -> new ArrayList<>()).add(t);
        }
        for (TypeFact t : this.types) {
            ancestorSimpleNames.put(t.qualifiedName(), computeAncestors(t));
        }
    }

    private Set<String> computeAncestors(TypeFact start) {
        Set<String> result = new LinkedHashSet<>();
        Set<String> visiting = new HashSet<>();
        collect(start, result, visiting);
        result.remove(start.simpleName());
        return result;
    }

    private void collect(TypeFact type, Set<String> result, Set<String> visiting) {
        if (!visiting.add(type.qualifiedName())) {
            return; // cycle guard
        }
        List<String> supers = new ArrayList<>();
        supers.addAll(type.extendedTypeNames());
        supers.addAll(type.implementedTypeNames());
        for (String superName : supers) {
            result.add(superName);
            TypeFact resolved = firstBySimpleName(superName);
            if (resolved != null) {
                collect(resolved, result, visiting);
            }
        }
        visiting.remove(type.qualifiedName());
    }

    private TypeFact firstBySimpleName(String simpleName) {
        List<TypeFact> matches = bySimpleName.get(simpleName);
        return (matches == null || matches.isEmpty()) ? null : matches.get(0);
    }

    /** True when {@code sub} is a proper subtype of {@code superSimpleName} in the project graph. */
    public boolean isProperSubtype(TypeFact sub, String superSimpleName) {
        Set<String> ancestors = ancestorSimpleNames.getOrDefault(sub.qualifiedName(), Set.of());
        return ancestors.contains(superSimpleName);
    }

    /** True when some project type named {@code subSimpleName} is a proper subtype of {@code superSimpleName}. */
    public boolean isProperSubtype(String subSimpleName, String superSimpleName) {
        if (subSimpleName.equals(superSimpleName)) {
            return false;
        }
        List<TypeFact> subs = bySimpleName.getOrDefault(subSimpleName, List.of());
        return subs.stream().anyMatch(s -> isProperSubtype(s, superSimpleName));
    }

    /**
     * If {@code method} (as declared on {@code type}) overrides a method inherited from a project
     * supertype, returns that supertype. Matching is on erased signature; static and private methods
     * are excluded (they are not overridable).
     */
    public Optional<TypeFact> overriddenSupertype(TypeFact type, MethodFact method) {
        if (method.isStatic() || method.isPrivate()) {
            return Optional.empty();
        }
        String signature = method.erasedSignature();
        for (String ancestorName : ancestorSimpleNames.getOrDefault(type.qualifiedName(), Set.of())) {
            TypeFact ancestor = firstBySimpleName(ancestorName);
            if (ancestor == null) {
                continue;
            }
            boolean declared = ancestor.methods().stream()
                    .filter(m -> !m.isStatic() && !m.isPrivate())
                    .anyMatch(m -> m.erasedSignature().equals(signature));
            if (declared) {
                return Optional.of(ancestor);
            }
        }
        return Optional.empty();
    }

    /** Project types that are proper subtypes of {@code type}, sorted by qualified name. */
    public List<TypeFact> subtypesOf(TypeFact type) {
        return types.stream()
                .filter(t -> isProperSubtype(t, type.simpleName()))
                .sorted(Comparator.comparing(TypeFact::qualifiedName))
                .toList();
    }

    /** Simple names of every project ancestor of {@code type} (transitive). */
    public Set<String> ancestorNamesOf(TypeFact type) {
        return ancestorSimpleNames.getOrDefault(type.qualifiedName(), Set.of());
    }

    /** All project types, sorted by qualified name. */
    public List<TypeFact> types() {
        return types;
    }
}
