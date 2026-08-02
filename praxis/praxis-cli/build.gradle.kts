plugins {
    java
    application
}

dependencies {
    implementation(project(":praxis-core"))
    implementation(project(":praxis-checks"))
    implementation(libs.picocli)
    annotationProcessor(libs.picocli)
    // YAML ruleset parsing lives in the CLI, keeping praxis-core dependency-light.
    implementation(libs.snakeyaml)
}

application {
    mainClass.set("dev.praxis.cli.PraxisCli")
    applicationName = "praxis"
}

// Run from the project root so documented relative paths (fixtures/...) resolve as written.
// The CLI exits 1 on a violation by design; don't let Gradle's `run` wrapper report that as
// "BUILD FAILED". The packaged CLI (installDist/distZip) still returns the real exit codes.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    isIgnoreExitValue = true
}
