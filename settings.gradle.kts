pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "aho-corasick-android"

include(":ac-core")
include(":ac-testkit")
include(":unicode-data-generator")
include(":ac-serialization")
include(":ac-kotlin")
include(":ac-android")
include(":benchmark-jvm")
include(":sample")
