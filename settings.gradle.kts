pluginManagement {
    repositories {
        google()
        // Google-hosted mirror of Maven Central — several times faster from Thailand than repo.maven.apache.org.
        maven("https://maven-central-asia.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central-asia.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "kiosk-kotlin"
include(":app")
include(":baselineprofile")
