plugins {
    java
}

group = "com.mosaicgem"
version = "1.0.0"
description = "MosaicGem - Folia 26.2 server plugin"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("dev.folia:folia-api:26.2.build.3-beta")
    testImplementation("dev.folia:folia-api:26.2.build.3-beta")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // 使用当前 JDK 编译，但目标字节码保持 Java 25 兼容
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

tasks.test {
    useJUnitPlatform()
}
