pluginManagement {
    repositories {
        // Official repositories first, Chinese mirrors as fallback.
        //
        // The order matters and was learned the hard way: the mirrors used to be
        // listed first for mainland-network speed, but maven.aliyun.com returns
        // 502 Bad Gateway from overseas runners (GitHub Actions), and Gradle
        // treats a 5xx as a hard failure instead of trying the next repository.
        // That made every CI build fail on dependency resolution while working
        // perfectly on the author's machine.
        //
        // With official sources first, overseas builds get the fast path and
        // mainland builds still fall back to the mirrors after the official
        // endpoints time out.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()

        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "KlinRead"
include(":app")
