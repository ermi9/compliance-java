plugins {
    java
}

// Common configuration applied to every Praxis module.
subprojects {
    apply(plugin = "java")

    group = "dev.praxis"
    version = "0.1.0-SNAPSHOT"

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    repositories {
        mavenCentral()
    }

    val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")

    dependencies {
        add("testImplementation", platform(libs.findLibrary("junit-bom").get()))
        add("testImplementation", libs.findLibrary("junit-jupiter").get())
        add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
        add("testImplementation", libs.findLibrary("assertj").get())
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Shared fixture corpus lives at the repo root; expose it to every test JVM.
        systemProperty("praxis.fixtures.dir", rootProject.file("fixtures").absolutePath)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:all")
    }
}
