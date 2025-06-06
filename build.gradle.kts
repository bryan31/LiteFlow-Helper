plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25" // 建议考虑升级到最新的稳定版 Kotlin
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "com.yomahub"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        // 你正在基于 IntelliJ IDEA Community Edition 2024.2.5 的 SDK 进行开发
        create("IC", "2025.1.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add necessary plugin dependencies for compilation here, example:
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"    // 插件最低支持 2024.2 版本系列 (包括 2024.2.5)
            untilBuild = "251.*"  // 插件最高支持到 2025.1 版本系列的最后一个版本 (包括 2025.1.1)
        }

        changeNotes = """
      Initial version
    """.trimIndent()
        // 你可能还需要配置 productDescriptor, pluginId, name, vendor 等信息
        // name.set("Your Plugin Name")
        // vendor.set("Your Company or Name")
        // description.set("Your plugin description.")
        // pluginId.set("com.your.domain.yourPluginId") // 确保这个 ID 是唯一的
    }
    // buildSearchableOptions.set(true) // 如果你的插件有设置项，可以考虑开启
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }

    // patchPluginXml {
    //     // 这里可以直接修改 plugin.xml 的内容，但通常通过 pluginConfiguration 更方便
    // }
}