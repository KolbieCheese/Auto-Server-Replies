plugins {
    java
}

import org.gradle.api.GradleException
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.api.tasks.wrapper.Wrapper.DistributionType

group = "com.beautyinblocks"

val pluginBaseVersion = providers.gradleProperty("pluginBaseVersion").get()

fun incrementVersion(versionString: String, buildNumber: Int): String {
    val parts = versionString.split(".")
    if (parts.size != 3 || parts.any { it.toIntOrNull() == null }) {
        throw GradleException(
            "pluginBaseVersion must follow numeric major.minor.patch format, got '$versionString'"
        )
    }

    var major = parts[0].toInt()
    var minor = parts[1].toInt()
    var patch = parts[2].toInt() + buildNumber

    minor += patch / 10
    patch %= 10

    major += minor / 10
    minor %= 10

    return "$major.$minor.$patch"
}

val pluginVersion = providers.gradleProperty("pluginVersion")
    .orElse(providers.environmentVariable("PLUGIN_VERSION"))
    .orElse(
        providers.environmentVariable("GITHUB_RUN_NUMBER").map {
            incrementVersion(pluginBaseVersion, it.toInt())
        }
    )
    .orElse("$pluginBaseVersion-SNAPSHOT")
    .get()

version = pluginVersion

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.20.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.jar {
    archiveFileName.set("SnarkyServer-${project.version}.jar")
}

tasks.processResources {
    inputs.property("pluginVersion", project.version)

    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "8.14.3"
    distributionType = DistributionType.BIN
}
