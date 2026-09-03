plugins {
    id("ac.android.application")
}

android {
    namespace = "com.cn.ac.sample"

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(":ac-core"))
    implementation(project(":ac-android"))
    implementation(project(":ac-serialization"))
    implementation(project(":ac-kotlin"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

