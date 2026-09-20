# LiteFlow-Helper 更新日志

## [Unreleased]

## [1.2.1]

### 新增

- 支持识别 LiteFlow AI Agent 与 Jev 组件，包括继承 `HarnessAgentComponent`、`JevSwitchComponent` 的业务组件及其间接子类。
- 为 AI 组件增加专属 SVG 图标：沿用 LiteFlow 的立方体造型，加入镂空“AI”标识，适配深浅主题，并用于项目树、组件列表和代码补全。
- 为 XML EL 中的 AI 组件引用增加独立的紫色高亮，便于区分 AI 节点与普通业务节点，并适配深浅主题。

### 修复

- 修复通过 Spring `@Bean` 注册的组件无法被扫描，导致 XML 中合法节点被误报为不存在的问题。
- 支持 `@Bean` 的 `value`、`name`、默认方法名和常量形式的主 Bean 名称，补全与引用解析可识别这些节点，并支持跳转到对应工厂方法。
