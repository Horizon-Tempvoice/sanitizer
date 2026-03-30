plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.dokka") version "2.2.0"
    `maven-publish`
    `java-library`
}

group = "me.diamondforge"
version = System.getenv("RELEASE_VERSION") ?: "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
}

val dokkaJavadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.dokkaHtml)
    from(tasks.dokkaHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("mavenKotlin") {
            from(components["java"])
            artifact(dokkaJavadocJar)

            groupId = project.group.toString()
            artifactId = "string-sanitizer"
            version = project.version.toString()
        }
    }

    repositories {
        val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")
        val repoUrl = if (isSnapshot)
            System.getenv("REPO_SNAPSHOT_URL")
        else
            System.getenv("REPO_RELEASE_URL")

        if (!repoUrl.isNullOrBlank()) {
            maven {
                name = "Reposilite"
                url = uri(repoUrl)
                credentials {
                    username = System.getenv("REPO_USERNAME")
                    password = System.getenv("REPO_PASSWORD")
                }
            }
        }
    }
}
