plugins {
    `java-library`
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.cn.ac.tools.UnicodeDataGenerator")
}
