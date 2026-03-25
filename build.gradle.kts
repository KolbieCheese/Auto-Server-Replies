plugins {
    java
}

group = "com.beautyinblocks"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.jar {
    archiveBaseName.set("snarkyserver")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
