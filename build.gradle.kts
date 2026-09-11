plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "org.icarus"
version = "1.6.4-BETA"
description = "支持物品组件的自定义配方插件"

val paperApiVersion = "1.21.11-R0.1-SNAPSHOT"
val lombokVersion = "1.18.42"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// Windows 下 JVM 默认使用 GBK，会把中文日志写成乱码，这里统一为 UTF-8
tasks.withType<JavaExec>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

tasks.runServer {
    minecraftVersion("1.21.11")
    jvmArgs("-Dcom.mojang.eula.agree=true")
}

