plugins {
    java
}

group = "uk.co.notnull"
version = "1.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    maven {
        url = uri("https://repo.not-null.co.uk/releases/")
    }
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    mavenCentral()
    mavenLocal()
}

dependencies {
    compileOnly(libs.velocityApi)
    compileOnly(libs.luckpermsApi)
    compileOnly(libs.platformDetection)

    annotationProcessor(libs.velocityApi)
}

tasks {
    compileJava {
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
        options.encoding = "UTF-8"
    }

    processResources {
        expand("version" to project.version)
    }
}
