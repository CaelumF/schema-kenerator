import org.gradle.plugins.signing.Sign

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.dokka") version "1.9.20" apply false
    id("org.owasp.dependencycheck") version "8.2.1" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.0" apply false
    id("com.vanniktech.maven.publish") version "0.33.0" apply false
    id("com.github.ben-manes.versions") version "0.51.0" apply false
    id("ru.vyarus.mkdocs") version "4.0.1"
}


// Register the root task to publish all modules to the project repository
tasks.register("publishAllToProjectRepository") {
    group = "publishing"
    description = "Publishes all modules to the project's maven-repo directory"
    
    // This task will depend on the publishToProjectRepository tasks of all subprojects
    // The dependencies will be added by each subproject in its own configuration
}

// Configure all subprojects to publish to the local "maven-repo" directory
subprojects {
    // Skip the 'tests' and 'examples' modules as they don't need to be published
    if (name != "tests" && name != "examples") {
        // Apply the maven-publish plugin to all subprojects that need to be published
        plugins.withId("com.vanniktech.maven.publish") {
            // Add the maven-publish plugin explicitly to access its APIs
            apply(plugin = "maven-publish")
            
            // Check if signing is enabled from gradle.properties
            val signingEnabled = rootProject.findProperty("signingEnabled")?.toString()?.toBoolean() ?: false
            
            // Disable signing tasks if signingEnabled is false
            if (!signingEnabled) {
                tasks.withType<Sign>().configureEach {
                    enabled = false
                    logger.lifecycle("Signing disabled for task: ${this.path}")
                }
            }
            
            // Configure the publishing after the project has been evaluated
            afterEvaluate {
                // Get project properties
                val projectGroupId: String by project
                val projectVersion: String by project
                val projectArtifactIdBase: String by project
                
                // Set the artifactId based on the module name
                val artifactId = when (project.name) {
                    "schema-kenerator-core" -> "$projectArtifactIdBase-core"
                    "schema-kenerator-reflection" -> "$projectArtifactIdBase-reflection"
                    "schema-kenerator-serialization" -> "$projectArtifactIdBase-serialization"
                    "schema-kenerator-swagger" -> "$projectArtifactIdBase-swagger"
                    "schema-kenerator-jsonschema" -> "$projectArtifactIdBase-jsonschema"
                    "schema-kenerator-jackson" -> "$projectArtifactIdBase-jackson"
                    "schema-kenerator-jackson-jsonschema" -> "$projectArtifactIdBase-jackson-jsonschema"
                    "schema-kenerator-jackson-swagger" -> "$projectArtifactIdBase-jackson-swagger"
                    "schema-kenerator-validation-swagger" -> "$projectArtifactIdBase-validation-swagger"
                    else -> "$projectArtifactIdBase-${project.name}"
                }
                
                // Configure the publishing extension
                extensions.configure<org.gradle.api.publish.PublishingExtension> {
                    // Register a new publication for the local repository
                    publications {
                        create<org.gradle.api.publish.maven.MavenPublication>("projectRepo") {
                            groupId = projectGroupId
                            this.artifactId = artifactId
                            version = projectVersion
                            
                            // Add the main JAR artifact
                            from(components["java"])
                        }
                    }
                    
                    // Add the local repository
                    repositories {
                        maven {
                            name = "ProjectRepository"
                            url = uri("${rootProject.projectDir}/maven-repo")
                        }
                    }
                }
                
                // Create a simple task to publish to the project repository
                tasks.register("publishToProjectRepository") {
                    group = "publishing"
                    description = "Publishes Maven publication to the project's maven-repo directory"
                    
                    // This task depends on the jar task to ensure it's built
                    dependsOn("jar")
                    
                    doFirst {
                        // Disable signing for this publication
                        tasks.findByName("signProjectRepoPublication")?.enabled = false
                    }
                    
                    // Depend on the publish task
                    finalizedBy("publishProjectRepoPublicationToProjectRepositoryRepository")
                }
                
                // Add this module's publishToProjectRepository task as a dependency of the root publishAllToProjectRepository task
                rootProject.tasks.named("publishAllToProjectRepository").configure {
                    dependsOn(tasks.named("publishToProjectRepository"))
                }
            }
        }
    }
}

mkdocs {
    sourcesDir = "."
    buildDir = "./build/mkdocs"
    updateSiteUrl = true
    publish {
        branch = "gh-pages"
        version = "2.x"
        rootRedirect = true
        rootRedirectTo = "latest"
        setVersionAliases("latest")
        generateVersionsFile = true
    }
    python {
        minPythonVersion = "3.12"
    }
}
