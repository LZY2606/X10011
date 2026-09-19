# 渲染器与块解析器重复代码收敛说明

本次收敛只动内部实现，公开接口（`Renderer`、`NodeRenderer`、`AttributeProvider`、`UrlSanitizer`）、
三个 Builder 的公开方法（含已废弃的 `TextContentRenderer.Builder.stripNewlines`）、以及
`HtmlWriter` / `MarkdownWriter` / `TextContentWriter` 的 public/protected 成员签名均未改动。
新增的实现类都放在 `org.commonmark.internal` 下面：

- `org.commonmark.internal.renderer.AbstractVisitorNodeRenderer`
- `org.commonmark.internal.renderer.RendererUtil`
- `org.commonmark.internal.util.LastCharAppendable`

另在 `org.commonmark.internal.renderer.NodeRendererMap` 上加了一个静态工厂方法，
在 `org.commonmark.internal.util.Parsing` 上加了一个具名判定。

## 1. 三个 Core*NodeRenderer 的 render(Node) 与 visitChildren(Node)

- 收敛前：6 份逐字相同的副本。`CoreHtmlNodeRenderer`、`CoreTextContentNodeRenderer`、
  `CoreMarkdownNodeRenderer` 各有 1 份 `public void render(Node)`（方法体 `node.accept(this);`）
  和 1 份 `protected void visitChildren(Node)`（先取 `getNext()` 再 `context.render(node)` 的循环）。
- 收敛后：2 份，都在 `org.commonmark.internal.renderer.AbstractVisitorNodeRenderer`
  （`render(Node)` 1 份、`visitChildren(Node)` 1 份）。三个 Core 渲染器改为继承它，
  各自只实现一个 `protected void renderChild(Node)` 钩子（委托给 `context.render(node)`），
  保证子节点仍然经过 `NodeRendererMap` 分派，扩展注册的自定义渲染器照常生效。
  遍历仍是"先记下 next、再渲染当前节点"的单趟活链表遍历，没有改成先收集成 List。
- 复核：
  - `grep -rn "protected void visitChildren" commonmark/src/main/java` → 2 处
    （另一处是 `AbstractVisitor` 里本来就有的、`node.accept(this)` 版本，方法体不同）。
  - `grep -rn "public void render(Node node)" commonmark/src/main/java/org/commonmark/internal/renderer/AbstractVisitorNodeRenderer.java` → 1 处。

## 2. 三个 Renderer 的 String render(Node) 与 RendererContext 构造循环

- `String render(Node)`：
  - 收敛前：3 份（`HtmlRenderer`、`MarkdownRenderer`、`TextContentRenderer` 各自
    "new StringBuilder → render(node, sb) → toString" 三行）。
  - 收敛后：1 份，在 `org.commonmark.internal.renderer.RendererUtil#renderToString`。
    三个 `render(Node)` 方法体只剩一行委托（接口要求方法必须存在，无法完全删掉）。
    `HtmlRenderer` 对 null node 的 `NullPointerException("node must not be null")` 行为不变
    （由 `render(Node, Appendable)` 里的 `Objects.requireNonNull` 抛出）。
  - 复核：`grep -rn "renderToString" commonmark/src/main/java` → 定义 1 处 + 委托调用 3 处。
- `RendererContext` 构造函数里"遍历 nodeRendererFactories → factory.create(this) → 塞进
  NodeRendererMap"的循环：
  - 收敛前：3 份（三个 Renderer 的内部类 `RendererContext` 各一份）。
  - 收敛后：1 份，在 `org.commonmark.internal.renderer.NodeRendererMap#create(Iterable, Function)`。
    三个构造器各留一行调用 `NodeRendererMap.create(nodeRendererFactories, factory -> factory.create(this))`。
  - 复核：`grep -rn "NodeRendererMap.create" commonmark/src/main/java` → 定义 1 处 + 调用 3 处；
    `grep -rn "nodeRendererMap.add" commonmark/src/main/java` → 只剩 `NodeRendererMap` 内部。

## 3. 三个 writer 的"追加 + IOException 包装 + 更新 lastChar"管道

- 收敛前：6 份。`HtmlWriter.append(String)`、`TextContentWriter.append(String)`、
  `TextContentWriter.append(char)`、`MarkdownWriter` 的三个私有 `write`
  （`write(String, CharMatcher)`、`write(String)`、`write(char)`）各自内联了
  "try { buffer.append(...) } catch (IOException e) { throw new RuntimeException(e); } + 更新 lastChar"。
- 收敛后：2 份，都在 `org.commonmark.internal.util.LastCharAppendable`
  （`append(String)` 与 `append(char)` 各一份 try/catch）。三个 writer 改为各自持有一个
  `LastCharAppendable`，lastChar 由它统一维护；`MarkdownWriter` 的转义慢路径和单字符写入
  通过 `setLastChar` 记录"逻辑上"的最后一个字符（转义写出的字符与源字符不一一对应），
  语义与原来逐点一致：空字符串写入不改变 lastChar。
- 复核：`grep -rn "catch (IOException" commonmark/src/main/java/org/commonmark/renderer` → 0 处；
  `grep -c "catch (IOException" commonmark/src/main/java/org/commonmark/internal/util/LastCharAppendable.java` → 2。

## 4. 块解析器里"缩进够不够四格、要不要让位给 indented code block"的判定

- 收敛前：8 处内联判定，分布在 7 个类里（`BlockQuoteParser`、`FencedCodeBlockParser` 2 处、
  `HeadingParser`、`HtmlBlockParser`、`IndentedCodeBlockParser`、`ListBlockParser`、
  `ThematicBreakParser`），写法混用 `state.getIndent() >= Parsing.CODE_BLOCK_INDENT`、
  `< Parsing.CODE_BLOCK_INDENT` 和裸的 `>= 4` / `< 4`。
  （另外 `IndentedCodeBlockParser.tryContinue` 里还有 1 处同类判定、`DocumentParser` 的
  块起始循环里有 1 处，本次一并收敛，口径从宽。）
- 收敛后：1 个具名判定 `org.commonmark.internal.util.Parsing#isCodeBlockIndent(int)`
  （`indent >= CODE_BLOCK_INDENT`；小于号场景用 `!isCodeBlockIndent(...)` 表达），
  全部 10 个调用点都改用它，8 处原址不再出现裸数字字面量。
- 复核：
  - `grep -rn "isCodeBlockIndent" commonmark/src/main/java` → 定义 1 处 + 调用 10 处
    （7 个类 9 处 + `DocumentParser` 1 处）。
  - `grep -rn "getIndent() >= 4\|getIndent() < 4" commonmark/src/main/java` → 0 处。
  - `grep -rn "getIndent() >= Parsing\|getIndent() < Parsing" commonmark/src/main/java` → 0 处。

## 守卫用例（新增，钉死三套 renderer 的既有行为差异）

全部在 `commonmark-integration-test/src/test/java/org/commonmark/integration/RendererBehaviorGuardTest.java`，
共 19 个用例；测试总数（最后一个模块的汇总）从 1959 增加到 1978。

- `htmlRendererCallsBeforeAndAfterRootAroundRendering`：HtmlRenderer 在渲染整棵树之前调
  `beforeRoot`、之后调 `afterRoot`（事件序列正好是 beforeRoot → render → afterRoot）。
- `markdownRendererDoesNotCallBeforeOrAfterRoot`：MarkdownRenderer 不调 `beforeRoot`/`afterRoot`，只调 `render`。
- `textContentRendererDoesNotCallBeforeOrAfterRoot`：TextContentRenderer 同样不调 `beforeRoot`/`afterRoot`。
- `attributeProviderCalledForPreAndCodeOfFencedCodeBlock`：围栏代码块触发 2 次 AttributeProvider，
  tagName 依次是 `pre`、`code`。
- `attributeProviderCalledForPAndImgOfImage`：Image 触发 `p`、`img` 两次调用（含段落标签）。
- `attributeProviderNotCalledForOmittedSingleParagraphP`：`omitSingleParagraphP(true)` 时被省掉的
  `p` 标签不触发任何 AttributeProvider 调用（输出为 `foo`，无 `<p>`）。
- `attributeProviderCalledForWrappingPOfEscapedHtmlBlock`：`escapeHtml(true)` 时 HtmlBlock 被包进
  `p` 标签，AttributeProvider 被调 1 次，tagName 为 `p`。
- `attributeProviderNotCalledForRawHtmlBlock`：`escapeHtml(false)` 时 HtmlBlock 原样输出，
  AttributeProvider 一次也不调。
- `attributeProvidersCalledInRegistrationOrderPerTag`：注册多个 provider 时，每个标签都按注册顺序
  依次回调（first:pre → second:pre → first:code → second:code）。
- `unlinkingNodeDuringHtmlRenderDoesNotStopFollowingSiblings` /
  `unlinkingNodeDuringMarkdownRenderDoesNotStopFollowingSiblings` /
  `unlinkingNodeDuringTextContentRenderDoesNotStopFollowingSiblings`：渲染过程中对当前节点调
  `Node.unlink()`，后面的兄弟节点照常渲染，且被摘节点只被渲染 1 次（单趟遍历）。
- `unlinkDetachesNodeAndReconnectsNeighbors`：`unlink()` 后该节点的 parent/previous/next 三个方向
  都置空，原先相邻的两个节点双向接上。
- `renderToAppendableAppendsMultipleTimes`：三块内容的文档经 `render(Node, Appendable)` 渲染时，
  三套 renderer 对 Appendable 的 append 调用次数都大于 1（边渲染边写），且内容与 `render(Node)` 一致。
- `ioExceptionDuringRenderLeavesPartialOutputInAppendable`：Appendable 中途抛 IOException 时，
  三套 renderer 都抛出 cause 为该 IOException 的 RuntimeException，且已写出的部分完整留在
  Appendable 里（是完整输出的非空前缀）。
- `htmlWriterEmptyAppendDoesNotChangeLastChar`：HtmlWriter 写入空串后 `line()` 行为不变
  （lastChar 未被清掉，仍会补换行）。
- `textContentWriterEmptyWriteDoesNotChangeLastChar`：TextContentWriter 写入空串后
  `whitespace()` 行为不变（仍会补空格）。
- `markdownWriterEmptyRawDoesNotChangeLastChar`：MarkdownWriter 写入空串后 `getLastChar()` 不变。
- `escapingDiffersBetweenRenderers`：同一段含 `<`、`&`、`"`、`_` 和强调标记的文本
  （`x < y & "z" _em_ a_b`），HTML 输出实体转义 + `<em>`，Markdown 输出反斜杠转义，
  TextContent 原样输出文本，三边结果分别钉死。
