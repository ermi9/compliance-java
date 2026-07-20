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
