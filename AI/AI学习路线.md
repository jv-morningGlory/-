# AI 学习路线 — Java 后端开发者转型指南

> 目标：不丢掉后端优势，叠加 AI 应用能力，成为「后端 + AI」复合型人才
> 开始日期：2026-06-01

---

## 阶段一：会用 AI 工具

**目标：把 AI 当生产力工具，日常提效**

- [x] 1.1 熟练使用 Claude Code 辅助编码（代码生成、调试、重构）
- [x] 1.2 熟练使用 Cursor 或同类 AI IDE
- [x] 1.3 掌握 Prompt 技巧：角色设定、Few-shot、思维链（CoT）
- [x] 1.4 了解各模型能力边界：Claude / GPT / Gemini 各擅长什么
- [x] 1.5 在日常工作中用 AI 完成 3 个实际任务（记录下来）

### 学习笔记

#### 1.1 Claude Code 使用心得

> 笔记已迁移至 → [Claude Code 实战常用指令](Claude/实战常用指令.md)

#### 1.2 Prompt 技巧总结

> **角色设定**决定"它用什么水平回答"，**Few-shot** 决定"按什么格式回答"，**CoT** 决定"想多深再回答"。三个可以叠加使用。

### 好用的 Prompt 模板

**功能开发模板：**

```
## 需求描述

[一句话说清楚要做什么]

## 功能要求

1. [具体要求 1]
2. [具体要求 2]
3. ...

## 代码风格

参照项目 CLAUDE.md 中的规范。

## 约束条件

- 技术栈：[填写，如 Spring Boot + iTextPDF]
- 请先给出实现方案，确认后再写代码
```

> 模板要点：**需求描述**让 AI 理解背景，**功能要求**编号列出防止遗漏，**代码风格**保持项目一致性，**约束条件**限定技术栈避免自由发挥，"先方案后代码"避免方向错了白写。



## 阶段二：懂 AI 原理

**目标：理解 AI 工作原理，能做技术选型判断**

### 2.1 LLM 基础

- [x] 理解 Token、Temperature、Top-P 等核心概念
- [x] 理解上下文窗口（Context Window）的意义和限制
- [ ] 完成 吴恩达《ChatGPT Prompt Engineering》免费课程

**笔记：**

> 已掌握，详见 → [Claude Code 实战常用指令 — 核心概念](Claude/实战常用指令.md#核心概念)
> - **Token**：文本计量单位，1 中文字 ≈ 1~2 token
> - **Temperature**：控制输出随机性，0 = 确定性（代码生成），1 = 发散（创意写作）
> - **Context Window**：模型的短期记忆上限，约 200K tokens，满了就记不住前面的内容

### 2.2 Embedding 与向量检索

- [ ] 理解文本如何转化为向量（Embedding）
- [ ] 理解余弦相似度、向量检索原理
- [ ] 用 Java 调用一次 Embedding API，计算两段文本的相似度

**笔记：**

向量：是由方向和大小的量
我们可以将任何的东西转化成向量 
可以通过函数计算 不同向量之间的相似度
词嵌入：训练好的词向量模型

（在这里记录）

### 2.3 RAG（检索增强生成）

- [ ] 理解 RAG 完整链路：文档加载 → 切分 → Embedding → 存储 → 检索 → 生成
- [ ] 理解 Chunk 切分策略对效果的影响
- [ ] 跑通 LangChain4j 官方 RAG 示例

**笔记：**

（在这里记录）

### 2.4 Agent 原理

- [ ] 理解 Function Calling / Tool Use 机制
- [ ] 理解 Agent 的「规划 → 执行 → 观察」循环
- [ ] 阅读一个开源 Agent 项目源码（推荐 LangChain4j Examples）

**笔记：**

（在这里记录）

---

## 阶段三：做项目

### 项目 1：企业知识库问答系统（入门）

**技术栈：** Spring Boot + PgVector/Milvus + LLM API

- [ ] 3.1 搭建项目骨架，引入 LangChain4j 依赖
- [ ] 3.2 实现文档上传和切分功能
- [ ] 3.3 对接 Embedding 模型，生成向量并存入向量数据库
- [ ] 3.4 实现用户提问 → 向量检索 → 拼装 Prompt → 调用 LLM → 返回答案
- [ ] 3.5 添加对话历史管理，支持多轮对话
- [ ] 3.6 前端页面（简单即可，能问答就行）

**项目笔记：**

#### 核心设计

（架构图、技术选型理由）

#### 遇到的问题及解决

| 问题 | 原因 | 解决方案 |
|------|------|---------|
|      |      |         |

---

### 项目 2：AI Agent 工作流引擎（进阶）

**技术栈：** Spring Boot + LangChain4j + Function Calling

- [ ] 3.7 实现 Agent 基础框架：接收指令 → 解析意图 → 调用工具
- [ ] 3.8 实现 3 个工具：数据库查询、API 调用、文件操作
- [ ] 3.9 实现多轮对话和上下文管理
- [ ] 3.10 实现工作流编排：多步骤任务自动执行
- [ ] 3.11 添加执行日志和错误处理
- [ ] 3.12 加一个管理界面（查看 Agent 执行记录）

**项目笔记：**

（在这里记录）

---

### 项目 3：多 Agent 协作系统（加分项）

**技术栈：** Spring Boot + Kafka + LangChain4j

- [ ] 3.13 设计多 Agent 架构：规划 Agent、执行 Agent、审核 Agent
- [ ] 3.14 用 Kafka 实现 Agent 间异步通信
- [ ] 3.15 实现任务分发和结果聚合
- [ ] 3.16 实现一个完整场景（如：自动分析数据库数据并生成报告）

**项目笔记：**

（在这里记录）

---

## 阶段四：深入方向（选一个）

### 方向 A：AI 工程化（推荐）

> 和你后端运维经验最契合

- [ ] 了解模型部署方案（Ollama、vLLM、TGI）
- [ ] 学习 Prompt 版本管理和评估方法
- [ ] 设计 AI 应用的可观测性方案（日志、链路追踪、Token 用量监控）
- [ ] 了解 AI 应用的限流、降级、容错策略

**笔记：**

（在这里记录）

### 方向 B：AI Agent 平台

- [ ] 深入学习 Agent 框架设计模式
- [ ] 学习 MCP 协议（Model Context Protocol）
- [ ] 了解多 Agent 编排策略
- [ ] 尝试设计一个通用 Agent 平台

**笔记：**

（在这里记录）

### 方向 C：RAG 深入

> 你有 Elasticsearch 经验，这个方向很合适

- [ ] 学习混合检索：向量 + 关键词 + 知识图谱
- [ ] 学习 ReRank 重排序
- [ ] 学习 Query 改写和意图识别
- [ ] 优化 RAG 效果：检索准确率和召回率评估

**笔记：**

（在这里记录）

---

## 技术选型速查

| 需求 | 推荐方案 | 备选 |
|------|---------|------|
| Java AI 框架 | LangChain4j | Spring AI |
| 向量数据库 | PgVector（入门）、Milvus（生产） | Qdrant、Weaviate |
| LLM API | Claude API / GPT API | 通义千问、DeepSeek |
| Embedding 模型 | OpenAI Embedding / BGE | text2vec |
| 消息队列 | Kafka（你已经会了） | RabbitMQ |
| 前端 | 简单 API + Swagger 即可 | Thymeleaf |

---

## 学习资源

| 资源 | 说明 | 地址 |
|------|------|------|
| 吴恩达 Prompt Engineering | 免费短课，必看 | DeepLearning.AI |
| LangChain4j 官方文档 | Java 开发者的 AI 框架 | github.com/langchain4j |
| LangChain4j Examples | 官方示例项目，直接跑 | github.com/langchain4j/langchain4j-examples |
| MCP 协议文档 | Anthropic 出的 Agent 通信协议 | modelcontextprotocol.io |
| Claude Code | AI 辅助编程工具，日常用 | claude.ai/code |

---

## 阶段性复盘

> 每完成一个阶段，在这里写下总结

### 阶段一完成后

- 花了多长时间：
- 最大收获：
- 下一步调整：

### 阶段二完成后

- 花了多长时间：
- 最大收获：
- 下一步调整：

### 阶段三完成后

- 花了多长时间：
- 做了哪些项目：
- 项目亮点：

---

## 一句话提醒

> **不用学算法，不用学 Python，用 Java 直接上手做项目。**
> 你的后端经验（分布式、中间件、高可用）在 AI 工程化落地时是稀缺能力。
> 先花两周把 Claude Code 用熟，投入产出比最高。
