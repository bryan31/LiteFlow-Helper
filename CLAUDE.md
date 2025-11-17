# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## 项目概述
LiteFlow-Helper 是一款 IntelliJ IDEA 插件，专为 LiteFlow 框架设计，用于增强其开发体验。该插件提供了规则扫描、代码补全、引用导航和 LiteFlow 组件专用图标等强大功能。

## 已实现核心功能
- **规则扫描与工具窗口**：自动扫描所有定义的组件和规则，提供独立的规则组件窗口，支持双击跳转和搜索
- **EL 代码支持**：LiteFlow 规则 EL（表达式语言）的语法高亮、代码补全、括号匹配提示
- **引用导航**：规则 EL 中的组件可点击跳转到定义处（支持代码、脚本、子流程）
- **专属图标**：为 LiteFlow 组件和规则提供定制化图标显示
- **行标记导航**：Java 代码中调用规则的位置左侧会显示图标，可快速跳转到规则定义
- **脚本高亮**：规则内各类脚本的智能语法高亮和补全

## 构建系统
项目使用 Gradle 结合 IntelliJ Platform Gradle Plugin 进行构建管理。

### 常用命令
- 构建插件：`./gradlew build`
- 运行测试：`./gradlew test`
- 在沙盒 IDE 中运行插件：`./gradlew runIde`
- 构建可分发 ZIP 包：`./gradlew buildPlugin`
- 检查依赖：`./gradlew dependencies`

### 插件配置文件
- 主插件定义：`src/main/resources/META-INF/plugin.xml`
- 构建配置：`build.gradle.kts`
- 项目属性：`gradle.properties`

## 代码架构
代码库按功能领域进行组织：

### 核心包结构
- `com.yomahub.liteflowhelper.completion`: EL 代码补全功能
- `com.yomahub.liteflowhelper.editor`: 编辑器增强（括号补全、配对提示等）
- `com.yomahub.liteflowhelper.highlight`: 语法高亮功能
- `com.yomahub.liteflowhelper.icon`: 自定义图标提供者
- `com.yomahub.liteflowhelper.injection`: 规则内脚本的语言注入
- `com.yomahub.liteflowhelper.marker`: 代码行标记导航
- `com.yomahub.liteflowhelper.reference`: 引用解析与导航
- `com.yomahub.liteflowhelper.service`: 核心服务（缓存、文件处理等）
- `com.yomahub.liteflowhelper.toolwindow`: LiteFlow 链窗口 UI

### 关键服务类
- `LiteFlowCacheService`: 缓存 LiteFlow 链和节点信息
- `FileService`: 处理文件操作和规则扫描
- `LiteFlowChainScanner`: 扫描 XML 文件中的 LiteFlow 链定义
- `LiteFlowNodeScanner`: 扫描 Java 文件中的 LiteFlow 节点定义

## IDE 集成点
插件通过以下 IntelliJ IDEA 扩展点实现集成：
- 代码补全贡献者
- 引用贡献者
- 注解器
- 行标记提供者
- 工具窗口
- 编辑器监听器
- 语言注入器

## 依赖项
- IntelliJ IDEA Platform SDK
- QLExpress（用于 EL 表达式解析）
- 可选 IDEA 插件依赖：Groovy、Python、Kotlin
