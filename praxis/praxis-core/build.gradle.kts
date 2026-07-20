plugins {
    java
    `java-library`
}

dependencies {
    // The ONLY runtime dependency of praxis-core: the parser + symbol solver.
    // praxis-core is framework-free by contract (see verifyFrameworkFree below).
    api(libs.javaparser.symbol.solver)
}

// --- Invariant 3 enforcement: praxis-core must have zero framework dependencies. ---
// Fails the build if Spring / a web-service framework leaks onto the classpath.
val forbiddenGroups = listOf(
    "org.springframework",
    "org.springframework.boot",
    "io.micronaut",
    "jakarta.servlet",
    "javax.servlet",
    "org.apache.kafka",
    "io.quarkus",
)

val verifyFrameworkFree by tasks.registering {
    group = "verification"
    description = "Fails if praxis-core depends on any web/service framework (invariant 3)."
    doLast {
        val offenders = configurations.getByName("runtimeClasspath")
            .resolvedConfiguration.resolvedArtifacts
            .map { it.moduleVersion.id }
            .filter { id -> forbiddenGroups.any { id.group == it || id.group.startsWith("$it.") } }
            .map { "${it.group}:${it.name}:${it.version}" }
            .distinct()
            .sorted()
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "praxis-core must be framework-free (invariant 3) but found: $offenders"
            )
        }
        logger.lifecycle("verifyFrameworkFree: praxis-core runtime classpath is framework-free.")
    }
}

tasks.named("check") { dependsOn(verifyFrameworkFree) }
