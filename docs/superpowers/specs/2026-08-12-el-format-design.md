# EL 表达式格式化 — 设计文档

日期：2026-08-12
状态：已批准（brainstorming 阶段完成）

## 背景与目标

LiteFlow-Helper 已为规则 EL 提供高亮、补全、校验、导航，但缺少格式化能力。用户手写复杂 EL（多层嵌套、SWITCH/IF 续写、子变量定义）时排版混乱，可读性差。

目标：在编辑器中提供一键格式化当前 chain 内 EL 表达式的能力，输出风格统一、智能混合（短则单行、长则按层级展开）的结果。

## 需求决策（来自 brainstorming）

| 维度 | 决策 |
|---|---|
| 格式化样式 | 多行美化 + 智能混合（合并为一种行为：平铺宽度 ≤ 行宽预算保持单行，否则按嵌套层级展开多行） |
| 触发方式 | Alt+Enter 意图（IntentionAction）+ 编辑器右键菜单 Action；不接管 Ctrl+Alt+L；不默认绑定快捷键 |
| 作用范围 | 仅光标所在的当前 chain（具体为其 EL 承载标签：chain 直接值 / route / body 之一） |
| 容错策略 | EL 存在语法错误（括号不匹配）时拒绝格式化，并提示用户 |
| 核心实现 | 方案 A：基于现有 `LiteFlowElLexer` 的两趟 token 重写（不建 AST、不依赖 QLExpress） |

## 架构总览

```
┌─ utils 包（纯 Java，不依赖 IDE，可单测）───────────┐
│ LiteFlowElFormatter                                │
│   format(String elText, ElFormatOptions)           │
│     → FormatResult（成功: 格式化文本 / 失败: 原因）│
│   内部：Lexer → 括号配对 + 平铺宽度预计算          │
│        → fits-or-break 流式输出                    │
└──────────────────▲─────────────────────────────────┘
                   │ 调用
┌─ format 包（新增，IntelliJ 集成层）───────────────┐
│ ElFormatSupport            定位光标处 EL 承载标签、│
│                            计算替换范围、XML 转义、│
│                            执行写命令              │
│ LiteFlowFormatElIntention  Alt+Enter 入口          │
│ LiteFlowFormatElAction     编辑器右键菜单入口       │
└────────────────────────────────────────────────────┘
```

新包 `com.yomahub.liteflowhelper.format`，与项目按功能分包的惯例一致。格式化核心放 `utils` 包，保持与 `LiteFlowElLexer`/`LiteFlowElAnalyzer` 相同的"纯 Java 可单测"模式。

## 核心组件设计

### LiteFlowElFormatter（utils 包，纯 Java）

入口：

```java
public final class LiteFlowElFormatter {
    public static @NotNull FormatResult format(@NotNull String elText, @NotNull ElFormatOptions options);
}
```

- `ElFormatOptions`：`maxLineWidth`（默认 80）、`indentUnit`（默认 4 空格）、`baseIndent`（EL 续行的绝对缩进列，由 IDE 层根据标签位置计算传入）
- `FormatResult`：`success(String formatted)` / `failure(String reason)` 两种形态（简单的 final 类 + 静态工厂即可）

算法（两趟）：

1. `LiteFlowElLexer.tokenize(elText)` 分词（复用现有分词器，注释/字符串/数字/标识符/标点五类 token）
2. **括号配对**：栈扫描，为每个 `(` 记录匹配的 `)` 下标；扫描结束栈非空或出现多余 `)` → 返回 `failure("EL 表达式括号不匹配")`
3. **平铺宽度预计算**：自右向左（或递归）计算每个 `(...)` 分组"全部平铺成一行"的宽度：组内所有 token 文本按标准空格规则拼接后的总长度，嵌套分组按其自身平铺宽度计入
4. **流式输出**：维护当前行已用列数（含 baseIndent）；遇到分组起始时查其平铺宽度：
   - 当前行剩余宽度（maxLineWidth − 已用列）≥ 平铺宽度 → 整组平铺输出
   - 否则 → 展开：`(` 后换行，参数逐个独占一行（缩进 = 分组起始层级 + indentUnit），顶层逗号后换行，闭合 `)` 换行回到分组起始缩进；每个参数行内部递归应用同一 fits-or-break 决策
   - fits 判定时，若关键字分组后随点号续写链（`SWITCH(x).TO(...)`、`IF(...).ELIF(...).ELSE(...)` 等），整条续写链的平铺宽度一并计入宿主分组的宽度预算：整条链能平铺才全部同行，否则宿主分组展开、续写另起一行

formatter 不增删任何 token（包括结尾 `;` 的有无保持原样），仅重排 token 之间的空白。

### 格式化规则细则

空格规范（平铺输出时）：

- `,` 后一个空格，`(` 前无空格（关键字/标识符紧贴括号），`)` 前无空格
- `.` 前后均无空格（`a.tag("x")`、`SWITCH(x).TO(...)` 紧贴）
- `=` 前后各一个空格（子变量定义 `sub = THEN(a)`）
- `;` 前无空格
- 两个相邻词 token（IDENT/NUMBER/STRING）之间保底一个空格（防御性，正常 EL 不会触发）

换行规范：

- 顶层 `;` 分隔的语句（子变量定义、主表达式）各自独占一行；最后一个 `;` 保留
- 展开分组内：每个顶层参数独占一行，每深一层缩进 +indentUnit，闭合 `)` 独占一行回到分组起始缩进
- 点号续写关键字（`.DO/.TO/.to/.DEFAULT/.ELSE/.ELIF/.BREAK/.PRE/.FINALLY`，即 `LiteFlowXmlUtil.EL_DOT_CONTINUATIONS` 集合）：与宿主分组一起做 fits 判定，整条链可平铺则同行；宿主展开时续写另起一行，缩进与宿主分组的闭合 `)` 相同
- 点号修饰符（`.tag/.data/.id/.any/.must/.bind/.ignoreError/.threadPool/.parallel/.retry/.maxWaitSeconds/.maxWaitMilliseconds/.percentage`，即 `EL_DOT_MODIFIERS` 集合）：永远紧贴宿主不拆行（其参数为短字符串/数字，按普通分组规则参与宽度计算）

保护措施：

- STRING、COMMENT token 内容逐字保留，不做任何内部改动
- 注释保持相对位置：与其后随 token 同处一行（中间一个空格）；若注释是分组/语句内最后一个 token，则与前一个 token 同行
- 只重写"EL 语句区"：value 文本中第一个非空白字符到最后一个非空白字符之间的部分；首尾空白（如 `<chain>` 后的换行缩进）原样保留

幂等性：`format(format(x)) == format(x)`，作为单元测试断言。

### 缩进基准（IDE 层计算，作为 baseIndent 传入）

- EL 首 token 与 `<chain ...>` 标签同行 → baseIndent = 标签所在行的前导空白宽度 + indentUnit
- EL 已另起一行（前导空白含换行）→ baseIndent = EL 首行的实际前导空白宽度

## IDE 集成层（format 包）

### ElFormatSupport（共用逻辑）

1. 由 Editor + PsiFile 定位光标处 `XmlTag`，要求：`LiteFlowXmlUtil.isLiteFlowXml(file)` 且 `isElCarrierTag(tag)`
2. 取 `tag.getValue().getText()` 与其在文档中的 TextRange
3. 剥离首尾空白、计算 baseIndent，调用 `LiteFlowElFormatter.format`
4. 成功：`WriteCommandAction` 内用格式化结果替换"EL 语句区"范围；写回前做 XML 转义——仅转义 STRING token 内的 `<`、`>`，`&` 仅在不成合法实体引用（`&name;`/`&#123;`）时转义为 `&amp;`，避免用户已写的实体引用被二次转义
5. 失败：`HintManager.showErrorHint(editor, reason + "，无法格式化")`

### LiteFlowFormatElIntention（Alt+Enter）

- `getText()` = "格式化 LiteFlow EL 表达式"
- `isAvailable`：轻量定位（光标在 EL 承载标签的 value 文本内、EL 非空白），不做完整格式化预检，保证 Alt+Enter 列表响应速度
- `invoke`：委托 ElFormatSupport；失败时 HintManager 提示

### LiteFlowFormatElAction（右键菜单）

- text = "格式化 LiteFlow EL 表达式"
- `update`：仅在 LiteFlow XML 且光标位于 EL 承载标签内时 enabled && visible
- `actionPerformed`：委托 ElFormatSupport
- 注册到 `EditorPopupMenu`，`anchor="after" relative-to-action="ReformatCode"`；不绑定默认快捷键

### plugin.xml 注册

```xml
<intentionAction>
    <className>com.yomahub.liteflowhelper.format.LiteFlowFormatElIntention</className>
</intentionAction>
<actions>
    <action id="LiteFlow.FormatEl"
            class="com.yomahub.liteflowhelper.format.LiteFlowFormatElAction"
            text="格式化 LiteFlow EL 表达式">
        <add-to-group group-id="EditorPopupMenu" anchor="after" relative-to-action="ReformatCode"/>
    </action>
</actions>
```

## 错误处理

| 场景 | 行为 |
|---|---|
| 括号不匹配 | 拒绝格式化，HintManager 提示"EL 表达式括号不匹配，无法格式化" |
| EL 为空白 | 两个入口均不显示/不可用 |
| 非 LiteFlow XML、光标不在 EL 承载标签内 | 入口不可见 |
| PSI/文件失效 | 防御性判空，静默返回，不抛异常 |

## 测试

`src/test/java/com/yomahub/liteflowhelper/utils/LiteFlowElFormatterTest.java`（纯 JUnit，参照 `LiteFlowElLexerTest`/`LiteFlowElAnalyzerTest` 模式）：

1. 短表达式保持单行 + 空格规范化（`THEN( a ,WHEN( b,c ) );` → `THEN(a, WHEN(b, c));`）
2. 超宽表达式按层级展开（长 THEN 链、嵌套 WHEN）
3. `SWITCH(x).TO(...).DEFAULT(...)`、`FOR/WHILE/ITERATOR...DO`、`IF...ELIF...ELSE` 续写的平铺与展开
4. 子变量定义多语句（`sub = THEN(a, b); THEN(sub, c);`）
5. 字符串内含括号/逗号/分号不受影响
6. 块注释保留且位置正确
7. 点号修饰符（`a.tag("x").data(y)`）不拆行
8. 括号不匹配 → failure
9. 幂等性：`format(format(x)) == format(x)`
10. baseIndent 计入行宽预算与续行缩进

IDE 集成层不写 UI 测试（与项目现状一致）。

## 明确的非目标（YAGNI）

- 不做格式化设置页（行宽/缩进暂为常量，预留 `ElFormatOptions` 参数）
- 不接管 Ctrl+Alt+L，不默认绑定快捷键
- 不做整个文件/所有 chain 的批量格式化（仅当前 chain）
- 不支持 yml/json 规则文件中的 EL（项目现有 EL 支持均为 XML 专属）
- 不提供"强制全部展开"的独立模式（智能混合已覆盖）
