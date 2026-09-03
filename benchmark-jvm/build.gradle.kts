plugins {
    id("ac.jvm.library")
}

dependencies {
    implementation(project(":ac-core"))
    implementation(libs.ahocorasick.old)
}

// Disable publishing for internal benchmark module
tasks.withType<PublishToMavenRepository>().configureEach {
    enabled = false
}
tasks.withType<PublishToMavenLocal>().configureEach {
    enabled = false
}
