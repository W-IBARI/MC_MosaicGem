plugins {
    java
}

group = "com.mosaicgem"
version = "1.0.0-SNAPSHOT"
description = "MosaicGem - Folia 26.2 server plugin"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("dev.folia:folia-api:26.2.build.3-beta")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("projectVersion" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("MosaicGem")
}
