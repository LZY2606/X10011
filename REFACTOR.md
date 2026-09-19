# 复制粘贴收敛说明

本次收敛了 commonmark 核心模块里四处成片的重复代码，并为三套 renderer 之间
"本来就不一样、但现有用例没覆盖"的行为补了守卫用例。所有公开接口
（`Renderer`、`NodeRenderer`、`AttributeProvider`、`UrlSanitizer`）、三个
Builder 的公开方法（含 `@Deprecated` 的 `stripNewlines`）、三个 writer 的
public/protected 成员签名均未改动；已有用例一条未改。

## 1. 三个 Core 节点渲染器的 `render(Node)` 与 `visitChildren(Node)`

- 收敛前：6 份。`CoreHtmlNodeRenderer`、`CoreTextContentNodeRenderer`、
  `CoreMarkdownNodeRenderer` 各自逐字实现了 `render(Node)`（`node.accept(this)`）
  和 `protected void visitChildren(Node)`（沿兄弟指针单趟遍历、先取 next 再渲染）。
- 收敛后：2 份。两个方法体现在只出现在新基类
  `org.commonmark.internal.renderer.CoreNodeRenderer`
  （`commonmark/src/main/java/org/commonmark/internal/renderer/CoreNodeRenderer.java`）
  的 `render(Node)` 和 `visitChildren(Node)` 里。三个 Core 渲染器改为继承它，
  各自只实现一个新钩子 `renderChild(Node)`（委托给各自的 `context.render(node)`），
  遍历仍然沿 live 兄弟指针单趟进行，不预收集 List。
- 复核：`grep -rn "node.accept(this)" commonmark/src/main/java/org/commonmark/renderer`
  应无结果；`grep -rln "protected void visitChildren" commonmark/src/main/java/org/commonmark/renderer`
  应无结果（唯一实现留在 `internal/renderer/CoreNodeRenderer.java`，
  `node/AbstractVisitor.java` 里那份是Visitor API 自带的，不在收敛范围）。

## 2. 三个 Renderer 的 `String render(Node)` 与 RendererContext 的工厂循环

- 收敛前：`String render(Node)`（new StringBuilder → render(node, sb) → toString）
  3 份，分别在 `HtmlRenderer`、`MarkdownRenderer`、`TextContentRenderer`；
  三个 `RendererContext` 构造函数里"遍历 nodeRendererFactories、
  `factory.create(this)`、塞进 `NodeRendererMap`"的循环也是 3 份。
- 收敛后：各 1 份。`String render(Node)` 的方法体收敛到
  `org.commonmark.internal.renderer.Renderers.renderToString(Renderer, Node)`
  （`commonmark/src/main/java/org/commonmark/internal/renderer/Renderers.java`），
  三个 Renderer 各留一行委托（接口要求方法必须存在）；工厂循环收敛到
  `org.commonmark.internal.renderer.NodeRendererMap.of(Iterable, Function)`
  （`commonmark/src/main/java/org/commonmark/internal/renderer/NodeRendererMap.java`），
  三个 RendererContext 各留一行调用。`HtmlRenderer.render(null)` 仍然抛
  带 `node must not be null` 消息的 NPE（空检查在 `render(Node, Appendable)` 里）。
- 复核：`grep -rn "Renderers.renderToString" commonmark/src/main/java` 应为 3 处调用
  + 1 处定义；`grep -rn "NodeRendererMap.of" commonmark/src/main/java` 应为 3 处调用
  + 1 处定义；`grep -rn "nodeRendererMap.add" commonmark/src/main/java` 应只剩
  `NodeRendererMap` 内部那 1 处。

## 3. 三个 writer 的 append 管道（append + IOException 包装 + lastChar 更新）

- 收敛前：6 份。`HtmlWriter.append(String)`、`TextContentWriter.append(String)`、
  `TextContentWriter.append(char)`、`MarkdownWriter` 的三个私有 `write`
  （`write(String, CharMatcher)`、`write(String)`、`write(char)`）各自抄了一遍
  "写 Appendable、把 IOException 包成 RuntimeException、非空时更新 lastChar"。
- 收敛后：2 份。管道只出现在新类
  `org.commonmark.internal.renderer.AppendableWriter`
  （`commonmark/src/main/java/org/commonmark/internal/renderer/AppendableWriter.java`）
  的 `append(String)` 和 `append(char)` 里。三个 writer 各自持有一个
  `AppendableWriter`，原方法全部改为委托；`MarkdownWriter` 的转义慢路径逐字符
  走 `AppendableWriter`，最后按原语义把 lastChar 修正为源字符串末字符
  （`write(String)` 对空串抛 StringIndexOutOfBoundsException 的行为也原样保留）。
  空串写入不改 lastChar 的语义不变。
- 复核：`grep -rn "catch (IOException" commonmark/src/main/java/org/commonmark/renderer`
  应无结果；`grep -rn "catch (IOException" commonmark/src/main/java/org/commonmark/internal/renderer`
  应只有 `AppendableWriter.java` 里的 2 处。

## 4. block parser 的"缩进是否够四格"判定

- 收敛前：8 处内联判定，分布在 7 个类里：`BlockQuoteParser`（isMarker）、
  `FencedCodeBlockParser`（tryContinue、Factory.tryStart）、`HeadingParser`
  （Factory.tryStart）、`HtmlBlockParser`（Factory.tryStart，裸字面量 `< 4`）、
  `IndentedCodeBlockParser`（Factory.tryStart）、`ListBlockParser`
  （Factory.tryStart）、`ThematicBreakParser`（Factory.tryStart，裸字面量 `>= 4`）。
- 收敛后：1 个具名判定 `org.commonmark.internal.util.Parsing.isCodeBlockIndent(int)`
  （`commonmark/src/main/java/org/commonmark/internal/util/Parsing.java`），
  上述 8 处全部改用它（正向或取反），不再出现裸数字字面量；
  `IndentedCodeBlockParser.tryContinue` 里同含义的第 9 处判断也一并对齐到该判定，
  共 9 个调用点。`CODE_BLOCK_INDENT` 常量本身未动。
- 复核：`grep -rn "isCodeBlockIndent" commonmark/src/main/java/org/commonmark/internal`
  应为 9 处调用 + 1 处定义；`grep -rn "getIndent() < 4\|getIndent() >= 4" \
  commonmark/src/main/java/org/commonmark/internal` 应无结果。

## 守卫用例

新增 `commonmark-integration-test/src/test/java/org/commonmark/integration/RendererBehaviorGuardTest.java`
（18 个用例，全部钉的是改动前的现状行为；放在 integration-test 模块，
因此 `mvn -B test` 尾部模块用例数从 1959 变为 1977）：

- `htmlRendererCallsBeforeAndAfterRootAroundRendering`：HtmlRenderer 在渲染根节点
  前后各调一次 `beforeRoot`/`afterRoot`，顺序为 beforeRoot → render → afterRoot。
- `markdownRendererDoesNotCallBeforeOrAfterRoot`：MarkdownRenderer 完全不调
  `beforeRoot`/`afterRoot`。
- `textContentRendererDoesNotCallBeforeOrAfterRoot`：TextContentRenderer 完全不调
  `beforeRoot`/`afterRoot`。
- `attributeProviderCalledForPreAndCodeOfFencedCodeBlock`：围栏代码块触发 2 次
  AttributeProvider，tagName 依次为 `pre`、`code`。
- `attributeProviderCalledForParagraphAndImgOfImage`：Image 触发 `p`、`img` 两次调用。
- `attributeProviderNotCalledForOmittedSingleParagraphP`：`omitSingleParagraphP(true)`
  时被省掉的 `p` 不触发任何 AttributeProvider 调用。
- `attributeProviderCalledForPOfEscapedHtmlBlock`：`escapeHtml(true)` 时 HtmlBlock
  触发 1 次、tagName 为 `p`。
- `attributeProviderNotCalledForRawHtmlBlock`：`escapeHtml(false)` 时 HtmlBlock
  原样输出，触发 0 次。
- `attributeProvidersCalledInRegistrationOrder`：注册多个 provider 时按注册顺序
  逐标签依次调用（A:pre、B:pre、A:code、B:code）。
- `unlinkOfRenderedNodeDoesNotStopSiblingRendering`：节点在渲染自身时 `unlink()`，
  后续兄弟节点照常渲染，且被摘下的节点 parent/prev/next 均不再指回原树。
- `childrenAreTraversedLazilyNotCollectedBeforeRendering`：渲染前一个兄弟时把后一个
  兄弟 unlink，后一个不会被渲染——证明遍历沿 live 指针单趟进行，没有先收集成 List。
- `unlinkDetachesNodeAndRelinksFormerNeighbors`：`unlink()` 后该节点三个方向指针
  清空，原先相邻的两个节点双向接上，首尾指针正确。
- `renderToAppendableStreamsOutputInMultipleAppends`：三套 renderer 的
  `render(Node, Appendable)` 都是边渲染边写（append 次数 > 1），且流式输出与
  `render(Node)` 的字符串结果一致。
- `ioExceptionDuringRenderPropagatesAsRuntimeExceptionAndKeepsPartialOutput`：
  Appendable 中途抛 IOException 时以 RuntimeException（cause 为 IOException）抛出，
  已写出的部分保留在 Appendable 里，且是完整输出的前缀。
- `htmlWriterEmptyAppendDoesNotChangeLastChar`：HtmlWriter 写空串不改 lastChar，
  `line()` 在空串写入前后表现一致。
- `textContentWriterEmptyWriteDoesNotChangeLastChar`：TextContentWriter 写空串不改
  lastChar，`whitespace()` 在空串写入前后表现一致。
- `markdownWriterEmptyRawDoesNotChangeLastChar`：MarkdownWriter 写空串不改
  `getLastChar()`。
- `renderersEscapeSameTextDifferently`：同一段同时含 `<`、`&`、`"`、`_` 和强调标记
  的文本，三套 renderer 的转义输出分别钉死为现状值（HTML 实体转义、Markdown
  反斜杠转义、TextContent 原样）。
