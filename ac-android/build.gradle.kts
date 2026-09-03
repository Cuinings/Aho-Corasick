plugins {
    id("ac.android.library")
}

android {
    namespace = "com.cn.ac.android"
}

dependencies {
    api(project(":ac-core"))
    api(project(":ac-serialization"))
}
