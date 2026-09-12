plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "org.icarus"
version = "1.7.0"

val paperApiVersion = "1.21.11-R0.1-SNAPSHOT"
val lombokVersion = "1.18.42"
val jacksonVersion = "2.17.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    // 配方 YAML 的解析依赖，需随插件一起打包
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

// 未打包依赖的「瘦 jar」改名为 -plain，正式产物由 shadowJar 产出
tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveClassifier.set("")
    // 把依赖重定位到插件命名空间下，避免与服务端或其它插件自带的同名库冲突
    relocate("com.fasterxml.jackson", "org.icarus.tingere.libs.jackson")
    relocate("org.yaml.snakeyaml", "org.icarus.tingere.libs.snakeyaml")
}

tasks.runServer {
    minecraftVersion("1.21.11")
    jvmArgs("-Dcom.mojang.eula.agree=true")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

