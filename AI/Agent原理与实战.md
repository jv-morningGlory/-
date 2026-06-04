# Agent 原理与实战

> 目标：理解 Function Calling 机制，理解 Agent 循环，用 Java + LangChain4j + 智谱 GLM 跑通两个 Demo
> 前置：已完成 2.2 Embedding、2.3 RAG

---

## 一、Function Calling — 让 LLM 从"说话"变成"动手"

### 核心概念

**LLM 本身不执行任何代码**，它只是输出"我想调哪个函数、参数是什么"，执行是你的代码做的事。

```
普通对话：  用户 → LLM → 回答文本
Function Calling：用户 → LLM → 返回 JSON（函数名 + 参数）→ 你的代码执行 → 结果回传 LLM → 最终回答
```

LLM 返回的 JSON 长这样（示意）：

```json
{
  "name": "get_weather",
  "arguments": {
    "city": "北京",
    "date": "明天"
  }
}
```

> **你的代码收到这个 JSON 后，去调真实的天气 API，拿到结果再喂回给 LLM。** LLM 看到结果后才生成最终回答。整个过程 LLM 只负责"决策"，不负责"执行"。

### 它解决了什么问题

没有 Function Calling 时，LLM 只能"说话"：

```
用户："帮我查一下北京明天的天气"
LLM：  "我无法实时查询天气，建议你打开天气APP查看"   ← 没用，不能干活
```

有了 Function Calling，LLM 能"动手"：

```
用户："帮我查一下北京明天的天气"
LLM：  → 返回 { "name": "get_weather", "arguments": { "city": "北京", "date": "明天" } }
你的代码 → 调天气 API → 拿到 "晴，28°C"
结果回传 LLM
LLM：  "北京明天晴天，气温28°C"   ← 有用，能干活了
```

### 你需要做三件事

| 步骤 | 你做什么 | LLM 做什么 |
|------|---------|-----------|
| ① 定义工具 | 用代码写一个函数，加上描述（函数名、参数、功能说明） | — |
| ② 注册工具 | 把函数描述告诉 LLM（"你有这些工具可以用"） | LLM 记住它有哪些工具 |
| ③ 自动循环 | 框架（LangChain4j）帮你处理整个调用循环 | LLM 判断要不要调、调哪个、参数是什么 |

> **你只管写业务函数（"查天气"），框架帮你处理 LLM 和函数之间的来回通信。**

---

## 二、Agent = Function Calling + 循环

### 单次 vs 循环

**Function Calling 是单次调用，Agent 是循环多次调用直到任务完成。**

```
Function Calling（单次）：
  用户问 → LLM 调一次工具 → 回答

Agent（循环）：
  用户问 → LLM 调工具A → 拿到结果 → 还不够 → LLM 调工具B → 拿到结果 → 可以回答了 → 回答
```

### Agent 循环的本质

```
while (任务没完成) {
    1. 规划 — LLM 分析当前情况，决定下一步调哪个工具
    2. 执行 — 代码执行 LLM 选中的工具，拿到结果
    3. 观察 — 把结果喂回 LLM，让它判断任务完成没有
}
```

### 推演一个具体场景

```
用户问："北京明天适合爬山吗？"

第1轮：
  规划 → LLM："我需要先查北京明天天气" → 选择调用 get_weather("北京", "明天")
  执行 → 代码调天气API → 拿到 "晴，28°C，湿度40%"
  观察 → 把天气结果喂回 LLM

第2轮：
  规划 → LLM："天气信息已拿到，我可以直接回答了" → 不调工具，生成回答
  执行 → 输出："北京明天晴天28度，湿度低，适合爬山"
  观察 → 任务完成，退出循环
```

一个更复杂的场景（3 个工具、多轮循环）：

```
用户问："帮我规划一个北京三日游"

第1轮：LLM → 调用 get_weather("北京") → 拿到天气信息
第2轮：LLM → 调用 search_attractions("北京") → 拿到景点列表
第3轮：LLM → 调用 search_hotels("北京") → 拿到酒店信息
第4轮：LLM → 综合所有信息，生成完整的行程规划 → 任务完成
```

> **关键认知：** 你不需要写循环代码。LangChain4j 的 `AiServices` 帮你封装了整个循环，你只管定义工具，框架自动决定调几次、调哪个。

---

## 三、LangChain4j 帮你封装了什么

### 手写 Function Calling 循环（理解原理）

```java
// 不用框架，你手写的话是这样：
while (true) {
    // 1. 调 LLM
    Response response = callLLM(userMessage + history);

    // 2. 判断 LLM 是否要调工具
    if (response.hasToolCall()) {
        // 3. 执行工具
        String toolResult = executeTool(response.getToolName(), response.getToolArgs());

        // 4. 把结果加到历史，继续循环
        history.add(toolResult);
        continue;   // 回到第1步，再问 LLM
    }

    // 5. LLM 没有调工具，说明可以直接回答了
    return response.getText();
}
```

### LangChain4j 帮你做了什么

```java
// 用框架，你只需要写这些：
// ① 定义工具
class MyTools {
    @Tool("描述...")
    String myFunction(@P("参数描述") String param) {
        return "结果";
    }
}

// ② 组装
MyAgent agent = AiServices.builder(MyAgent.class)
    .chatLanguageModel(chatModel)
    .tools(new MyTools())
    .build();

// ③ 使用
agent.chat("用户问题");   // 框架自动处理整个循环
```

| 你写的 | 框架做的 |
|-------|---------|
| `@Tool` 注解的工具函数 | 把函数描述转成 LLM 能理解的格式 |
| `AiServices.builder()` 组装 | 建立循环：调 LLM → 判断 → 执行工具 → 回传结果 → 再调 LLM |
| `agent.chat()` 调用 | 管理对话历史、工具调用的完整生命周期 |

---

## 四、常见问题速查

| 问题 | 可能原因 | 排查方向 |
|------|---------|---------|
| LLM 没有调用工具 | `@Tool` 描述不够清晰 | 把工具描述写得更详细，说清楚"什么场景下该用这个工具" |
| LLM 传错参数 | `@P` 参数描述不够 | 给参数加上示例值，如 `@P("日期，如：今天、明天")` |
| LLM 反复调用同一个工具 | Prompt 没有引导收敛 | 在系统提示里明确任务边界 |
| Agent 调了不该调的工具 | 工具描述有歧义 | 让不同工具的描述更区分度 |
| 工具执行报错 | 函数内部异常 | 加 try-catch，返回友好错误信息让 LLM 重试 |

---

## 学习达成标准

> 不看代码，能说清楚三件事就过关：
> 1. Function Calling 的完整流程（LLM 输出 JSON → 代码执行 → 结果回传）
> 2. Agent 和 Function Calling 的区别（单次 vs 循环）
> 3. LangChain4j 里你写什么、框架帮你做什么（`@Tool` + `AiServices`）
