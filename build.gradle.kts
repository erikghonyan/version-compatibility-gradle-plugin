import com.diffplug.gradle.spotless.SpotlessExtension
import info.solidsoft.gradle.pitest.PitestPluginExtension
import info.solidsoft.gradle.pitest.PitestTask

plugins {
    id("com.diffplug.spotless") version "6.9.1" apply false
    id("info.solidsoft.pitest") version "1.7.4" apply false
}

apply(plugin = "com.diffplug.spotless")
configure<SpotlessExtension> {

    kotlin {
        target("**/*.kt")
        ktlint("0.46.1")
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint("0.46.1")
    }
}

allprojects {
    afterEvaluate {

        version = "0.2.0-SNAPSHOT"
        group = "io.github.davidburstrom.gradle.version-compatibility"

        apply(plugin = "com.diffplug.spotless")

        configure<SpotlessExtension> {
            if (plugins.hasPlugin(JavaPlugin::class.java)) {
                java {
                    googleJavaFormat("1.7")
                    licenseHeaderFile(rootProject.file("license-header.txt"))
                }
            }
        }

        project.tasks.withType<SourceTask>().configureEach {
            if (this.name != "spotlessJavaApply") {
                dependsOn("spotlessJavaApply")
            }
        }

        if (plugins.hasPlugin(JavaPlugin::class.java)) {
            configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }

            apply(plugin = "pmd")
            configure<PmdExtension> {
                toolVersion = "6.48.0"
                isConsoleOutput = true
                /* Disable default rules and provide specific ones. */
                ruleSets = listOf()
                ruleSetFiles(rootProject.files("config/pmd/rulesets.xml"))
            }

            apply(plugin = "info.solidsoft.pitest")
            configure<PitestPluginExtension> {
                pitestVersion.set("1.9.4")
                junit5PluginVersion.set("1.0.0")
                timestampedReports.set(false)
                targetClasses.set(
                    setOf(
                        "example.*",
                        "io.github.davidburstrom.gradle.versioncompatibility.*"
                    )
                )
                threads.set(2)
                failWhenNoMutations.set(true)
                mutators.set(listOf("DEFAULTS"))
                useClasspathFile.set(true)
                mutationThreshold.set(100)

                tasks.named("build").configure {
                    dependsOn("pitest")
                }
            }
            tasks.named<PitestTask>("pitest").configure {
                inputs.property("src", file("src/test"))
                onlyIf {
                    (inputs.properties["src"] as File).exists()
                }

                /*
                 * Carry over all system properties defined for test tasks into the Pitest tasks, except for the "junit"
                 * ones, as they can interfere with test stability.
                 */
                systemProperties(tasks.getByName<Test>("test").systemProperties.filterKeys { !it.contains("junit") })

                /*
                 * Include a "pitest" system property to be able to run tests differently if necessary. Use sparingly!
                 */
                systemProperty("pitest", "true")

                // Stabilizes test executions, especially in Docker
                environment.remove("HOME")

                outputs.cacheIf { true }
            }
        }
    }
}

val verifyVersion = tasks.register("verifyVersion") {
    doLast {
        if (!(version as String).endsWith("-SNAPSHOT")) {
            project.file("README.md").readLines()
                .filter { "version-compatibility" in it && "\"${project.version}\"" !in it }
                .forEach {
                    throw GradleException("Outdated version in README.md, could not find ${project.version} in $it")
                }
        }
    }
}

tasks.named("build").configure {
    dependsOn(verifyVersion)
}
