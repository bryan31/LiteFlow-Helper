# LiteFlow-Helper

LiteFlow 框架的 IntelliJ IDEA 辅助插件 · [liteflow.cc](https://liteflow.cc)

<!-- Plugin description -->

IDEA plugin for the [LiteFlow](https://liteflow.cc) framework.

LiteFlow 框架的辅助插件，帮助你更好地使用 LiteFlow：

- 规则组件窗口：自动扫描所有组件与流程链，双击跳转、按名称/ID 搜索
- EL 语法高亮：关键字、组件、子流程、子变量、注释分色，适配亮色/暗色主题
- 智能补全：`.` 后只出修饰符/续写，其余位置出关键字 + 节点 + 子流程 + 子变量
- 引用导航：EL 中的组件/子流程/子变量点击跳转到定义
- 语法与语义校验：括号匹配、`SWITCH` 缺 `.TO`、`FOR/WHILE/ITERATOR` 缺 `.DO`、`IF` 参数不足、节点类型不匹配
- 反向导航：组件类左侧图标跳到引用它的所有 chain
- 未使用组件检查：发现从未被引用的组件
- 决策表写法：支持 `<chain>` 直接值、`<body>`、`<route>+<body>` 三种写法
- 脚本高亮：groovy / javascript / graaljs / python / lua / kotlin / java / aviator
- 括号配对高亮与自动补全、`liteflow.rule-source` 跳转、EL Live Templates、专属图标

<!-- Plugin description end -->
