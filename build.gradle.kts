import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    mavenCentral()
    maven("https://papermc.io/repo/repository/maven-public/")
//    maven("https://maven.enginehub.org/repo/")
}

@Suppress("GradlePackageUpdate")
dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("reflect"))
    compileOnly("io.papermc.paper:paper-api:${project.properties["mcVersion"]}-R0.1-SNAPSHOT")
    compileOnly("io.github.monun:tap-api:${project.properties["tapVersion"]}")
    compileOnly("io.github.monun:kommand-api:${project.properties["kommandVersion"]}")
    implementation("com.github.stefvanschie.inventoryframework:IF:0.10.6")
}

val shade = configurations.create("shade")
shade.extendsFrom(configurations.implementation.get())

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
    }
    processResources {
        val file = File("version")
        val build = file.readText().toInt() + 1

        file.writeText(build.toString())

        val mmap = project.properties.toMutableMap()

        mmap["version"] = "${mmap["version"]}r$build"

        filesMatching("**/*.yml") {
            expand(mmap)
        }
        filteringCharset = "UTF-8"
    }
    register<ShadowJar>("outputJar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        archiveBaseName.set(project.name)
        archiveClassifier.set("")
        archiveVersion.set("")

        from(
            shade.map {
                if (it.isDirectory)
                    it
                else
                    zipTree(it)
            }
        )

        from(sourceSets["main"].output)

        doLast {
            copy {
                from(archiveFile)
                into("./out")
            }
        }
    }
    register<ShadowJar>("paperJar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        archiveBaseName.set(project.name)
        archiveClassifier.set("")
        archiveVersion.set("")

        from(
            shade.map {
                if (it.isDirectory)
                    it
                else
                    zipTree(it)
            }
        )

        from(sourceSets["main"].output)

        doLast {
            copy {
                from(archiveFile)
                val plugins = File(rootDir, ".server/plugins/")
                into(if (File(plugins, archiveFileName.get()).exists()) File(plugins, "update") else plugins)
            }
        }
    }
}