# RAG + GLM 实战 Demo（Java）

> 从零开始，跑通一个能问答的 RAG 程序。LLM 用智谱 GLM，Embedding 也用智谱，向量库先用内存版。

---

## 一、引入依赖

**Maven（pom.xml）：**

```xml
<!-- LangChain4j 核心 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.0.0-beta1</version>
</dependency>

<!-- 智谱 GLM 集成（Chat + Embedding） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-zhipu-ai</artifactId>
    <version>1.0.0-beta1</version>
</dependency>
```

> 就两个包。`langchain4j` 包含文档切分、向量存储等核心能力，`langchain4j-zhipu-ai` 对接智谱 API。

---

## 二、获取 GLM 的 Embedding（文本转向量）

**不需要你做什么，调 API 就行。** GLM Embedding 模型会返回一个 `float[]`，这就是向量。

```java
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.zhipu.ZhipuAiEmbeddingModel;

// 1. 创建 Embedding 模型（连智谱 API）
EmbeddingModel embeddingModel = ZhipuAiEmbeddingModel.builder()
    .apiKey("你的智谱API-KEY")
    .model("embedding-3")     // 智谱 Embedding 模型，维度 1024
    .build();

// 2. 把文本转成向量
String text = "入职满10年的员工享有15天年假";
Embedding embedding = embeddingModel.embed(text).content();
float[] vector = embedding.vector();   // 这就是向量：[0.023, -0.451, ...] 共1024个浮点数

System.out.println("向量维度: " + vector.length);  // 输出: 1024
```

> **关键点：** 你只管传文本，GLM 返回一堆浮点数。两个语义相近的文本，返回的浮点数组在数学上"方向接近"。

---

## 三、存储向量的表怎么设计

### 方案一：内存存储（先跑通，0 行 SQL）

LangChain4j 内置 `InMemoryEmbeddingStore`，就是一个 `Map`，不用建表。**适合先跑通再升级。**

```java
EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
// 就这样，可以直接用了
```

### 方案二：PgVector（生产方案）

> MySQL 不擅长向量检索（没有向量索引，千万级就慢）。生产环境建议 PostgreSQL + pgvector 插件。

```sql
-- 1. 启用 pgvector 插件
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 建表
CREATE TABLE document_chunks (
    id          BIGSERIAL PRIMARY KEY,
    content     TEXT,                        -- 原始文本
    embedding   vector(1024),                -- 向量（维度必须和 Embedding 模型一致）
    metadata    JSONB DEFAULT '{}',          -- 元数据（文件名、页码等）
    created_at  TIMESTAMP DEFAULT NOW()
);

-- 3. 建向量索引（HNSW，检索用）
CREATE INDEX ON document_chunks USING hnsw (embedding vector_cosine_ops);
```

**表结构说明：**

| 字段 | 作用 | 为什么需要 |
|------|------|-----------|
| `content` | 存 Chunk 原始文本 | 最终发给 LLM 的是原文，向量只用来搜 |
| `embedding` | 存 1024 维浮点数 | 余弦相似度检索就靠它 |
| `metadata` | 存来源信息 | 回答时可以引用，方便溯源 |

对应 Java 代码：

```java
EmbeddingStore<TextSegment> store = PgVectorEmbeddingStore.builder()
    .host("localhost")
    .port(5432)
    .database("rag_demo")
    .user("postgres")
    .password("你的密码")
    .table("document_chunks")
    .dimension(1024)     // 和 GLM embedding-3 的 1024 对齐
    .build();
```

---

## 四、完整 Demo：从文档入库到回答问题

```java
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.model.zhipu.ZhipuAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.List;
import java.util.stream.Collectors;

public class RAGDemo {

    // ====== 配置（换成你自己的 Key）======
    private static final String API_KEY = System.getenv("ZHIPU_API_KEY");

    public static void main(String[] args) {
        // ──────────── 初始化 ────────────
        // Chat 模型（生成答案）
        ChatLanguageModel chatModel = ZhipuAiChatModel.builder()
            .apiKey(API_KEY)
            .model("glm-4-flash")
            .build();

        // Embedding 模型（文本转向量）
        EmbeddingModel embeddingModel = ZhipuAiEmbeddingModel.builder()
            .apiKey(API_KEY)
            .model("embedding-3")
            .build();

        // 向量存储（先用内存版跑通）
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        // ──────────── 第一步：文档入库 ────────────
        // 模拟你的公司文档
        String doc = """
            员工手册
            一、年假规定
            入职满1年的员工享有5天年假。
            入职满5年的员工享有10天年假。
            入职满10年的员工享有15天年假。
            年假需提前一周申请，经直属领导审批后方可休假。
            
            二、病假规定
            员工因病需要休息的，凭医院证明可请病假。
            每年累计病假不超过30天的不影响年薪。
            病假超过3天需提供三甲医院诊断证明。
            
            三、加班规定
            工作日加班按1.5倍计算加班工资。
            休息日加班按2倍计算，法定假日按3倍计算。
            每月加班时长原则上不超过36小时。
            """;

        // 切成 Chunk（每段 300 字，重叠 30 字）
        List<TextSegment> chunks = DocumentSplitters.recursive(300, 30)
            .split(Document.from(doc));

        System.out.println("文档被切成 " + chunks.size() + " 个 Chunk：");
        for (int i = 0; i < chunks.size(); i++) {
            System.out.printf("  Chunk-%d: %s...%n", i,
                chunks.get(i).text().substring(0, Math.min(40, chunks.get(i).text().length())));
        }

        // 每个 Chunk 转成向量，存进向量库
        for (TextSegment chunk : chunks) {
            Embedding embedding = embeddingModel.embed(chunk.text()).content();
            store.add(embedding, chunk);
        }
        System.out.println("\n√ " + chunks.size() + " 个 Chunk 已入库\n");

        // ──────────── 第二步：用户提问 ────────────
        String question = "我想请年假需要怎么做？";

        // 用户问题也转成向量
        Embedding questionEmb = embeddingModel.embed(question).content();

        // 从库里搜相似度最高的 Top-3 Chunk
        List<EmbeddingMatch<TextSegment>> matches =
            store.findRelevant(questionEmb, 3);

        System.out.println("检索到的 Top-3 Chunk：");
        for (EmbeddingMatch<TextSegment> match : matches) {
            System.out.printf("  相似度: %.3f | %s%n",
                match.score(), match.embedded().text());
        }

        // 拼装 Prompt
        String context = matches.stream()
            .map(m -> m.embedded().text())
            .collect(Collectors.joining("\n---\n"));

        String prompt = """
            你是一个公司制度问答助手。请严格基于以下文档内容回答问题。
            如果文档中没有相关内容，请回答"文档中暂无相关信息"。
            
            【参考文档】
            %s
            
            【用户问题】
            %s
            """.formatted(context, question);

        // ──────────── 第三步：LLM 生成答案 ────────────
        System.out.println("\n===== LLM 回答 =====");
        String answer = chatModel.generate(prompt);
        System.out.println(answer);
    }
}
```

**运行输出示例：**

```
文档被切成 4 个 Chunk：
  Chunk-0: 员工手册 一、年假规定 入职满1年的员工享有5天年假...
  Chunk-1: 入职满5年的员工享有10天年假。入职满10年的员工...
  Chunk-2: 年假需提前一周申请，经直属领导审批后方可休假...
  Chunk-3: 二、病假规定 员工因病需要休息的，凭医院证明...

√ 4 个 Chunk 已入库

检索到的 Top-3 Chunk：
  相似度: 0.891 | 年假需提前一周申请，经直属领导审批后方可休假...
  相似度: 0.823 | 员工手册 一、年假规定 入职满1年的员工享有5天年假...
  相似度: 0.712 | 入职满5年的员工享有10天年假。入职满10年的员工...

===== LLM 回答 =====
根据文档，请年假需要提前一周申请，经直属领导审批后方可休假。
```

---

## 五、整体流程总结

```
                        ┌─── 离线阶段 ───┐
                        │                │
公司文档 ─→ 切成 Chunk ─→ 每段转成向量 ─→ 存入向量库
                                        │
        ┌───────────────────────────────┘
        ▼
                        ┌─── 在线阶段 ───┐
                        │                │
用户提问 ─→ 转成向量 ─→ 搜 Top-K Chunk ─→ 拼 Prompt ─→ LLM 生成答案
```

**你应该重点关注三个地方：**

1. 切分后 chunks 的内容 —— 看切得是否合理
2. 检索返回的相似度分数 —— 0.8 以上靠谱，0.5 以下基本不相关
3. LLM 最终回答是否引用了原文 —— 防止幻觉

---

## 六、从 Demo 到生产的升级路径

| 环节 | Demo 方案 | 生产方案 |
|------|----------|---------|
| 向量存储 | InMemory（重启丢失） | PgVector / Milvus |
| 文档来源 | 代码里写死字符串 | 文件上传接口 |
| Embedding | 每次调 API | 批量处理，缓存已入库的 |
| 检索 | 暴力遍历 | 向量索引（HNSW） |
| 切分策略 | 固定长度 | 按文档类型选择策略 |
