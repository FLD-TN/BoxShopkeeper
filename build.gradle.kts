plugins {
    java
}

group = "com.dex"
version = "1.3.1"

repositories {
    mavenCentral()
    mavenLocal()                                                // boxcore-api (publishToMavenLocal from BoxCore)
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")                                 // VaultAPI
    maven("https://repo.rosewooddev.io/repository/public/")     // PlayerPoints
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
    compileOnly("org.black_ixx:playerpoints:3.3.5")
    compileOnly("com.dex:boxcore-api:1.1.0")
    compileOnly(files("libs/MMOItems-6.10.jar", "libs/MythicLib-1.7.jar"))   // /chetao update-mmoitems

    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName.set("BoxShopkeeper-${project.version}.jar")
}
