// Root project build file
val groupProp = providers.gradleProperty("GROUP").getOrElse("com.cn.ac")
val versionProp = providers.gradleProperty("VERSION_NAME").getOrElse("1.0.0")

subprojects {
    group = groupProp
    version = versionProp
}
