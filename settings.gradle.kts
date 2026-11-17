pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
    }
}

rootProject.name = "fastgpt-kotlin-demo"
