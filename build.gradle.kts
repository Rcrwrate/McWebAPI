
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.AZUL
        nativeImageCapable = false
    }
}
publishing {
    repositories {
        maven {
            val cnbArtifactsGradlePassword = System.getenv("maven_TOKEN")
            url = uri("https://maven.cnb.cool/shirokasoke/love/-/packages/")
            credentials {
                username = "cnb"
                password = cnbArtifactsGradlePassword.toString()
            }
        }
    }
}