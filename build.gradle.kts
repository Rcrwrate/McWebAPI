
import java.io.File

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.AZUL
        nativeImageCapable = false
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

val buildInfoDir = layout.buildDirectory.dir("generated/resources/buildInfo")

sourceSets {
    main {
        resources {
            srcDir(buildInfoDir)
        }
    }

    patchedMc {
        resources {
            // srcDir("tools/Applied-Energistics-2-Unofficial-rv3-beta-1000-GTNH/src/main/java")
            // srcDir("tools/GT5-Unofficial-5.09.54.20/src/main/java")
            // srcDir("tools/NotEnoughItems/src/main/java")
            // srcDir("tools/Hodgepodge/src/main/java")
        }
    }
}

tasks.register("generateBuildInfo") {
    val outputDir = buildInfoDir
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile
        val assetsDir = File(dir, "assets")
        assetsDir.mkdirs()
        val buildTime = System.currentTimeMillis() / 1000
        File(assetsDir, "build.json").writeText("{\"buildTime\":$buildTime}")
    }
}

tasks.processResources {
    dependsOn("generateBuildInfo")
}

tasks.named("sourcesJar") {
    dependsOn("generateBuildInfo")
}

publishing {
    repositories {
        maven {
            val cnbArtifactsGradlePassword = System.getenv("maven_TOKEN") ?: "UN_SET"
            url = uri("https://maven.cnb.cool/shirokasoke/love/-/packages/")
            credentials {
                username = "cnb"
                password = cnbArtifactsGradlePassword.toString()
            }
        }
    }
}