pluginManagement {
    repositories {
        maven(url = "https://mirrors.cloud.tencent.com/repository/maven/google/")
        maven(url = "https://mirrors.cloud.tencent.com/repository/maven/central/")
        maven(url = "https://mirrors.cloud.tencent.com/repository/maven/public/")
        maven(url = "https://mirrors.cloud.tencent.com/repository/maven/gradle-plugin/")
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/public")
        maven(url = "https://maven.aliyun.com/repository/central")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://mirrors.cloud.tencent.com/repository/maven/google/")
        maven(url = "https://mirrors.cloud.tencent.com/repository/maven/central/")
        maven(url = "https://mirrors.cloud.tencent.com/repository/maven/public/")
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/public")
        maven(url = "https://maven.aliyun.com/repository/central")
        maven(url = "https://jitpack.io")
    }
}
rootProject.name = "MyMind"
include(":app")
