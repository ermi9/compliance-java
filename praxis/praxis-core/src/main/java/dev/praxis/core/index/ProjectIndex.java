package dev.praxis.core.index;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import dev.praxis.core.facts.CallSiteFact;
import dev.praxis.core.facts.ConstructorFact;
import dev.praxis.core.facts.FactModel;
import dev.praxis.core.facts.FieldFact;
import dev.praxis.core.facts.MethodFact;
import dev.praxis.core.facts.SourceRef;
import dev.praxis.core.facts.TypeFact;
import dev.praxis.core.facts.TypedNewFact;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Layer 1. Parses every {@code .java} file under a root and extracts the neutral {@link FactModel}.
 *
 * <p>Contracts honoured here:
 * <ul>
 *   <li><b>Static-only (invariant 2):</b> only parses and symbol-solves; never compiles or runs
 *       submission code. The symbol solver resolves against the JDK via reflection over the
 *       <em>analyzer's</em> classpath and the submission sources — it never loads submission classes.</li>
 *   <li><b>Resilience (spec):</b> a file that fails to parse is recorded in
 *       {@link FactModel#unparsableFiles()} and skipped; the run continues so dependent checks can
 *       yield {@code UNDETERMINED} rather than aborting.</li>
 *   <li><b>Determinism (invariant 4):</b> types and call sites are emitted in a stable sorted order.</li>
 * </ul>
 */
public final class ProjectIndex {

    private final Path root;
    private final FactModel factModel;

    private ProjectIndex(Path root, FactModel factModel) {
        this.root = root;
        this.factModel = factModel;
    }

    public Path root() {
        return root;
    }

    public FactModel factModel() {
        return factModel;
    }

    /**
     * Builds an index over {@code root} (a directory tree or a single {@code .java} file).
     *
     * @throws IOException if the root cannot be read
     */
    public static ProjectIndex build(Path root) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        Path sourceRoot = Files.isDirectory(normalized) ? normalized : normalized.getParent();

        JavaParser parser = newParser(sourceRoot);

        List<TypeFact> types = new ArrayList<>();
        List<CallSiteFact> callSites = new ArrayList<>();
        List<TypedNewFact> typedNews = new ArrayList<>();
        List<String> unparsable = new ArrayList<>();
        int[] exceptionCounts = new int[3]; // [throwStmts, catchClauses, throwsDecls]

        for (Path file : javaFiles(normalized)) {
            String display = displayPath(normalized, sourceRoot, file);
            ParseResult<CompilationUnit> result;
            try {
                result = parser.parse(file);
            } catch (IOException e) {
                unparsable.add(display);
                continue;
            }
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                unparsable.add(display);
                continue;
            }
            CompilationUnit cu = result.getResult().get();
            List<String> lines = readLines(file);
            extractTypes(cu, display, lines, types);
            extractCallSites(cu, display, lines, callSites);
            extractTypedNews(cu, display, lines, typedNews);
            countExceptionUsage(cu, exceptionCounts);
        }

        types.sort(Comparator.comparing(TypeFact::qualifiedName));
        callSites.sort(Comparator.comparing((CallSiteFact c) -> c.source().file())
                .thenComparingInt(c -> c.source().line())
                .thenComparingInt(c -> c.source().column())
                .thenComparing(CallSiteFact::methodName));
        typedNews.sort(Comparator.comparing((TypedNewFact t) -> t.source().file())
                .thenComparingInt(t -> t.source().line())
                .thenComparingInt(t -> t.source().column())
                .thenComparing(TypedNewFact::createdTypeName));
        unparsable.sort(Comparator.naturalOrder());

        return new ProjectIndex(normalized, new FactModel(
                List.copyOf(types),
                List.copyOf(callSites),
                List.copyOf(typedNews),
                exceptionCounts[0],
                exceptionCounts[1],
                exceptionCounts[2],
                List.copyOf(unparsable)));
    }

    private static JavaParser newParser(Path sourceRoot) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        if (sourceRoot != null && Files.isDirectory(sourceRoot)) {
            typeSolver.add(new JavaParserTypeSolver(sourceRoot));
        }
        ParserConfiguration config = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver))
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        return new JavaParser(config);
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of(); // snippet falls back to empty; line/column are still recorded
        }
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        if (Files.isRegularFile(root)) {
            return root.toString().endsWith(".java") ? List.of(root) : List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    // Sort so file iteration order is deterministic regardless of filesystem.
                    .sorted()
                    .toList();
        }
    }

    private static String displayPath(Path root, Path sourceRoot, Path file) {
        Path base = Files.isDirectory(root) ? root : (sourceRoot != null ? sourceRoot : root);
        try {
            return base.relativize(file).toString();
        } catch (IllegalArgumentException e) {
            return file.getFileName().toString();
        }
    }

    private static void extractTypes(CompilationUnit cu, String file, List<String> lines, List<TypeFact> out) {
        for (TypeDeclaration<?> td : cu.findAll(TypeDeclaration.class)) {
            out.add(toTypeFact(td, file, lines));
        }
    }

    private static TypeFact toTypeFact(TypeDeclaration<?> td, String file, List<String> lines) {
        String qualified = td.getFullyQualifiedName().orElse(td.getNameAsString());
        TypeFact.Kind kind = kindOf(td);

        List<String> extended = new ArrayList<>();
        List<String> implemented = new ArrayList<>();
        List<String> typeParams = new ArrayList<>();
        boolean isAbstract = false;
        if (td instanceof ClassOrInterfaceDeclaration coid) {
            coid.getExtendedTypes().forEach(t -> extended.add(t.getNameAsString()));
            coid.getImplementedTypes().forEach(t -> implemented.add(t.getNameAsString()));
            coid.getTypeParameters().forEach(t -> typeParams.add(t.getNameAsString()));
            isAbstract = coid.isAbstract();
        } else if (td instanceof EnumDeclaration ed) {
            ed.getImplementedTypes().forEach(t -> implemented.add(t.getNameAsString()));
        } else if (td instanceof RecordDeclaration rd) {
            rd.getImplementedTypes().forEach(t -> implemented.add(t.getNameAsString()));
            rd.getTypeParameters().forEach(t -> typeParams.add(t.getNameAsString()));
        }

        List<FieldFact> fields = new ArrayList<>();
        for (FieldDeclaration fd : td.getFields()) {
            for (VariableDeclarator var : fd.getVariables()) {
                fields.add(toFieldFact(fd, var, qualified, file, lines));
            }
        }

        List<MethodFact> methods = new ArrayList<>();
        for (MethodDeclaration md : td.getMethods()) {
            methods.add(toMethodFact(md, qualified, file, lines));
        }

        List<ConstructorFact> constructors = new ArrayList<>();
        for (var ctor : td.getConstructors()) {
            List<String> params = new ArrayList<>();
            ctor.getParameters().forEach(p -> params.add(simpleTypeName(p.getType())));
            constructors.add(new ConstructorFact(
                    qualified, List.copyOf(params), ctor.isPrivate(), SourceRef.of(file, ctor.getName(), lines)));
        }

        boolean resolved = tryResolve(td);

        return new TypeFact(
                qualified,
                td.getNameAsString(),
                kind,
                isAbstract,
                List.copyOf(typeParams),
                List.copyOf(extended),
                List.copyOf(implemented),
                List.copyOf(fields),
                List.copyOf(methods),
                List.copyOf(constructors),
                resolved,
                SourceRef.of(file, td.getName(), lines));
    }

    private static TypeFact.Kind kindOf(TypeDeclaration<?> td) {
        if (td instanceof ClassOrInterfaceDeclaration coid) {
            return coid.isInterface() ? TypeFact.Kind.INTERFACE : TypeFact.Kind.CLASS;
        }
        if (td instanceof EnumDeclaration) return TypeFact.Kind.ENUM;
        if (td instanceof RecordDeclaration) return TypeFact.Kind.RECORD;
        if (td instanceof AnnotationDeclaration) return TypeFact.Kind.ANNOTATION;
        return TypeFact.Kind.CLASS;
    }

    private static FieldFact toFieldFact(FieldDeclaration fd, VariableDeclarator var, String owner, String file, List<String> lines) {
        boolean isPrivate = fd.isPrivate();
        boolean isProtected = fd.isProtected();
        boolean isPublic = fd.isPublic();
        boolean isPackagePrivate = !isPrivate && !isProtected && !isPublic;
        Type type = var.getType();
        return new FieldFact(
                var.getNameAsString(),
                owner,
                isPrivate,
                isProtected,
                isPublic,
                isPackagePrivate,
                fd.isStatic(),
                fd.isFinal(),
                type.isArrayType(),
                type.asString(),
                simpleTypeName(type),
                SourceRef.of(file, fd, lines));
    }

    private static MethodFact toMethodFact(MethodDeclaration md, String owner, String file, List<String> lines) {
        List<String> params = new ArrayList<>();
        md.getParameters().forEach(p -> params.add(simpleTypeName(p.getType())));
        List<String> typeParams = new ArrayList<>();
        md.getTypeParameters().forEach(t -> typeParams.add(t.getNameAsString()));
        Type ret = md.getType();
        return new MethodFact(
                md.getNameAsString(),
                owner,
                List.copyOf(params),
                List.copyOf(typeParams),
                ret.asString(),
                simpleTypeName(ret),
                ret.isVoidType(),
                ret.isPrimitiveType(),
                ret.isArrayType(),
                md.isStatic(),
                md.isPrivate(),
                md.isPublic(),
                md.isAbstract(),
                SourceRef.of(file, md.getName(), lines),
                md);
    }

    /**
     * Best-effort call-site collection. Populates the model so the future dispatch analysis has
     * data to work with; no dispatch reasoning happens here. Receiver type resolution is guarded —
     * any symbol-solver failure yields a {@code null} receiver type rather than aborting the run.
     */
    private static void extractCallSites(CompilationUnit cu, String file, List<String> lines, List<CallSiteFact> out) {
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            String receiverType = null;
            if (call.getScope().isPresent()) {
                try {
                    receiverType = call.getScope().get().calculateResolvedType().describe();
                } catch (RuntimeException e) {
                    receiverType = null; // unresolved receiver — recorded as unknown, never guessed
                }
            }
            out.add(new CallSiteFact(call.getNameAsString(), receiverType, enclosingTypeName(call), SourceRef.of(file, call, lines)));
        }
    }

    /**
     * Collects AST-decidable "new bound to a differently-typed target" edges (variable, field, and
     * return sites). The upcast check later confirms which of these are true supertype coercions via
     * the {@link dev.praxis.core.facts.TypeHierarchy}. No resolution is required here.
     */
    private static void extractTypedNews(CompilationUnit cu, String file, List<String> lines, List<TypedNewFact> out) {
        for (VariableDeclarator var : cu.findAll(VariableDeclarator.class)) {
            if (var.getInitializer().isEmpty() || !var.getInitializer().get().isObjectCreationExpr()) {
                continue;
            }
            String target = simpleTypeName(var.getType());
            String created = var.getInitializer().get().asObjectCreationExpr().getType().getNameAsString();
            if (isCoercionCandidate(target, created)) {
                TypedNewFact.Site site = var.findAncestor(FieldDeclaration.class).isPresent()
                        ? TypedNewFact.Site.FIELD : TypedNewFact.Site.VARIABLE;
                out.add(new TypedNewFact(target, created, site, SourceRef.of(file, var, lines)));
            }
        }
        for (ReturnStmt ret : cu.findAll(ReturnStmt.class)) {
            if (ret.getExpression().isEmpty() || !ret.getExpression().get().isObjectCreationExpr()) {
                continue;
            }
            var method = ret.findAncestor(MethodDeclaration.class);
            if (method.isEmpty()) {
                continue;
            }
            String target = simpleTypeName(method.get().getType());
            String created = ret.getExpression().get().asObjectCreationExpr().getType().getNameAsString();
            if (isCoercionCandidate(target, created)) {
                out.add(new TypedNewFact(target, created, TypedNewFact.Site.RETURN, SourceRef.of(file, ret, lines)));
            }
        }
    }

    private static boolean isCoercionCandidate(String target, String created) {
        return target != null && created != null
                && !target.equals(created)
                && !target.equals("var")
                && !target.isBlank();
    }

    private static void countExceptionUsage(CompilationUnit cu, int[] counts) {
        counts[0] += cu.findAll(ThrowStmt.class).size();
        counts[1] += cu.findAll(CatchClause.class).size();
        for (MethodDeclaration md : cu.findAll(MethodDeclaration.class)) {
            counts[2] += md.getThrownExceptions().size();
        }
        for (ConstructorDeclaration cd : cu.findAll(ConstructorDeclaration.class)) {
            counts[2] += cd.getThrownExceptions().size();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String enclosingTypeName(com.github.javaparser.ast.Node node) {
        java.util.Optional<TypeDeclaration> ancestor = node.findAncestor(TypeDeclaration.class);
        if (ancestor.isPresent() && ancestor.get() instanceof TypeDeclaration<?> td) {
            return td.getFullyQualifiedName().orElse(null);
        }
        return null;
    }

    private static boolean tryResolve(TypeDeclaration<?> td) {
        try {
            td.resolve();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Erased, unqualified type name: strips array brackets, generics, and package qualifiers. */
    private static String simpleTypeName(Type type) {
        Type base = type;
        while (base.isArrayType()) {
            base = base.asArrayType().getComponentType();
        }
        if (base.isClassOrInterfaceType()) {
            ClassOrInterfaceType coit = base.asClassOrInterfaceType();
            return coit.getNameAsString();
        }
        if (base.isPrimitiveType()) {
            return base.asPrimitiveType().asString();
        }
        if (base.isVoidType()) {
            return "void";
        }
        return base.asString();
    }

    /** Convenience for callers that want to treat IO failure as unchecked. */
    public static ProjectIndex buildUnchecked(Path root) {
        try {
            return build(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
