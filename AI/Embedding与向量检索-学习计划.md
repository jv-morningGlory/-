# 2.2 Embedding 与向量检索 — 学习计划

> 目标：跑通"文本 → 向量 → 相似度计算 → 向量检索"全流程，理解每一步在做什么。

---

## Day 1：理解 Embedding —— 文本怎么变成数字

### 任务 1：直观感受 Embedding（30 分钟）

**目标**：亲眼看到一段文本变成一个数字数组，建立直觉。

**操作**：用 Python 调一次 Embedding API（Python 比 Java 简单，先跑通概念，Day 3 再用 Java）。

```bash
# 安装依赖
pip install zhipuai
```

```python
from zhipuai import ZhipuAI

client = ZhipuAI(api_key="你的Key粘贴在这里")

# 文本 → 向量
response = client.embeddings.create(
    model="embedding-3",
    input="今天天气真好，适合出去散步"
)
vector = response.data[0].embedding

print(f"向量维度: {len(vector)}")
print(f"前 10 个值: {vector[:10]}")
print(f"数据类型: {type(vector[0])}")
```

**预期输出**：
```
向量维度: 2048
前 10 个值: [0.0023, -0.0087, 0.0145, -0.0032, 0.0098, ...]
数据类型: <class 'float'>
```

**检查点**：你看到了一个 2048 个浮点数的数组。这就是 Embedding——把一句话"压缩"成 2048 个数字。

> Key 在智谱开放平台（open.bigmodel.cn）→ API Keys 页面获取。

---

### 任务 2：验证"语义相近 → 向量相近"（30 分钟）

**目标**：通过实验证明 Embedding 确实编码了语义。

```python
from zhipuai import ZhipuAI
import math

client = ZhipuAI(api_key="你的Key")

def get_embedding(text):
    resp = client.embeddings.create(
        model="embedding-3",
        input=text
    )
    return resp.data[0].embedding

# 余弦相似度函数（先当黑盒用，Day 2 详细讲原理）
def cosine_similarity(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    norm_a = math.sqrt(sum(x ** 2 for x in a))
    norm_b = math.sqrt(sum(x ** 2 for x in b))
    return dot / (norm_a * norm_b)

# 准备测试文本
texts = [
    "苹果是一家科技公司",
    "华为是一家科技公司",
    "香蕉是一种水果",
    "今天天气真好",
]

# 获取所有文本的向量
vectors = [get_embedding(t) for t in texts]

# 计算两两相似度
for i in range(len(texts)):
    for j in range(i + 1, len(texts)):
        sim = cosine_similarity(vectors[i], vectors[j])
        print(f"[{texts[i]}] vs [{texts[j]}]")
        print(f"  相似度: {sim:.4f}\n")
```

**预期结果**：
```
[苹果是一家科技公司] vs [华为是一家科技公司]
  相似度: 0.92   ← 都是科技公司，语义相近

[苹果是一家科技公司] vs [香蕉是一种水果]
  相似度: 0.45   ← 一个是公司，一个是水果，差距大

[苹果是一家科技公司] vs [今天天气真好]
  相似度: 0.15   ← 完全不相关
```

**检查点**：你亲手验证了——语义相近的句子，余弦相似度高；不相关的，相似度低。

**现在回答这个问题**（写下来，后面对照）：

> Embedding 是什么？
>
> 答：把文本编码成一个**固定长度的浮点数数组**。语义相近的文本 → 数组在空间中离得近 → 可以用数学公式（余弦相似度）计算"近不近"。

---

### 任务 3：理解 Embedding 的本质（1 小时）

**阅读材料**（按顺序看）：

1. **先看这个图解**（10 分钟）：https://jalammar.github.io/illustrated-word2vec/
   - 看懂"词可以变成向量"这个概念
   - 重点看"king - man + woman ≈ queen"那个经典例子

2. **再看这个理解演进**（15 分钟）：Embedding 技术的三代演进

   | 代际 | 代表模型 | 特点 | 缺点 |
   |------|---------|------|------|
   | **第一代：静态词向量** | Word2Vec、GloVe | 每个词一个固定向量，训练一次到处用 | 一个词只有一个向量（"苹果"不分水果和公司） |
   | **第二代：上下文向量** | BERT | 同一个词在不同语境下向量不同 | 需要微调，计算成本高 |
   | **第三代：大模型 Embedding** | 智谱 GLM、BGE、M3E | 直接调 API 拿到高质量向量，支持多语言 | 依赖外部服务或大模型推理 |

3. **回答这些问题**（检验理解）：

   - Q1：为什么不能直接用 ASCII/UTF-8 编码做向量？
     > ASCII 只编码了"字符的书写形式"，没有编码"含义"。"苹果"和"华为"的编码毫无关系，但语义上它们都是科技公司。

   - Q2：2048 维的向量，每一维代表什么？
     > 单独一维没有明确含义，是模型训练出来的隐式特征。2048 维组合在一起，才完整表达了语义。

   - Q3：为什么不用更高维度？比如 10000 维？
     > 维度越高表达力越强，但：计算成本上升、存储空间变大、向量索引变慢。2048 维是效果和成本的平衡点。

**检查点**：你能用自己的话说清楚"文本为什么能变成向量，以及为什么语义相近的文本向量也相近"。

---

## Day 2：理解余弦相似度与向量检索原理

### 任务 4：手写余弦相似度，理解原理（45 分钟）

**目标**：理解"两个向量有多相似"背后的数学，但只要高中数学水平。

**第 1 步：画图理解**（15 分钟）

在纸上画一个二维坐标系，标出 3 个点：

```
        A(4, 3)  ·
                  ·  B(3, 4)
       ·  C(-4, -3)
```

- A 和 B 方向接近（角度小）→ 语义相近
- A 和 C 方向相反（角度接近 180°）→ 语义相反

**余弦相似度 = 两个向量夹角的余弦值**：

```
cos(0°) = 1    → 方向完全相同 → 最相似
cos(90°) = 0   → 垂直 → 完全无关
cos(180°) = -1 → 方向相反 → 最不相似
```

> 所以余弦相似度范围是 **[-1, 1]**，越接近 1 越相似。实际应用中大多在 **[0, 1]** 范围。

**第 2 步：手算一遍**（15 分钟）

```python
# 用二维向量手算，方便理解
A = [3, 4]
B = [3, 4]    # 和 A 完全一样

# 点积：对应位置相乘再相加
dot_product = 3*3 + 4*4  # = 25

# 向量的模（长度）：各分量平方和的平方根
norm_A = (3**2 + 4**2) ** 0.5  # = 5
norm_B = (3**2 + 4**2) ** 0.5  # = 5

# 余弦相似度 = 点积 / (模A × 模B)
cos_sim = 25 / (5 * 5)  # = 1.0（完全相同，符合预期）
```

**第 3 步：用真实 Embedding 向量算一次**（15 分钟）

```python
from zhipuai import ZhipuAI
import numpy as np  # pip install numpy

client = ZhipuAI(api_key="你的Key")

def get_embedding(text):
    resp = client.embeddings.create(model="embedding-3", input=text)
    return np.array(resp.data[0].embedding)

v1 = get_embedding("我喜欢吃苹果")
v2 = get_embedding("我爱吃水果")

# numpy 一行搞定余弦相似度
sim = np.dot(v1, v2) / (np.linalg.norm(v1) * np.linalg.norm(v2))
print(f"余弦相似度: {sim:.4f}")
```

**检查点**：你能解释余弦相似度是什么——**两个向量夹角的余弦值，只看方向不看长度，值域 [-1, 1]，越接近 1 越相似。**

---

### 任务 5：理解向量检索原理（1 小时）

**目标**：知道"从百万向量中找最相似的 Top K"是怎么做到的。

**第 1 步：暴力搜索（Baseline）**

```
查询向量 Q，库中有 N 条向量
→ 计算 Q 和每一条的余弦相似度 → 排序 → 取 Top K
→ 时间复杂度 O(N)，N=100 万时要算 100 万次
```

问题：100 万条数据还能扛，1 亿条就崩了。

**第 2 步：ANN（近似最近邻）—— 用精度换速度**

不需要找到"绝对最相似"的，"差不多最相似"就行。

主流算法：

| 算法 | 原理 | 谁在用 | 特点 |
|------|------|--------|------|
| **HNSW** | 多层跳表，先粗后细定位 | Elasticsearch 8.x、Milvus | 查询快，内存占用大 |
| **IVF** | 先聚类分桶，只搜索相关桶 | Faiss、Milvus | 适合超大数据量 |
| **PQ 量化** | 把向量压缩成短编码 | Faiss | 牺牲精度换内存，通常和 IVF 组合 |

**第 3 步：图解 HNSW（最常用，重点理解）**

```
想象你在商场找一家店：

Layer 3（最稀疏）：只有大门、大区域标记 → 快速定位大致区域
Layer 2（中等）：每层楼的分区标记 → 缩小范围
Layer 1（最密集）：每家店铺 → 精确定位

搜索过程：从 Layer 3 开始 → 找到最近的节点 → 跳到 Layer 2 → 再找最近 → 跳到 Layer 1 → 找到最终结果
→ 不需要遍历所有店铺，跳几次就到了
```

> 不需要实现 HNSW，只要知道：**向量数据库内部用了 ANN 算法，在精度损失很小的情况下，把搜索从 O(N) 降到 O(log N)。**

**检查点**：你能回答——"向量检索为什么不用暴力搜索？ANN 是什么？HNSW 的大致思路是什么？"

---

### 任务 6：用向量数据库跑一次完整检索（45 分钟）

**目标**：体验"存入向量 → 语义搜索"的完整流程。

```bash
pip install chromadb
```

```python
from zhipuai import ZhipuAI
import chromadb
import numpy as np

client = ZhipuAI(api_key="你的Key")

def get_embedding(text):
    resp = client.embeddings.create(model="embedding-3", input=text)
    return resp.data[0].embedding

# 1. 创建集合（使用余弦距离）
chroma = chromadb.Client()
collection = chroma.get_or_create_collection(
    "knowledge_base",
    metadata={"hnsw:space": "cosine"}
)

# 2. 存入文档（手动计算向量存入）
docs = [
    "Java 是一种面向对象的编程语言",
    "Python 是一种解释型编程语言",
    "MySQL 是关系型数据库，使用 SQL 查询",
    "Redis 是基于内存的键值存储，常用于缓存",
    "Spring Boot 是 Java 的微服务框架",
    "Kafka 是分布式消息队列，用于异步通信",
]
ids = [f"doc_{i}" for i in range(len(docs))]
embeddings = [get_embedding(doc) for doc in docs]

collection.add(documents=docs, ids=ids, embeddings=embeddings)

# 3. 语义搜索（用 GLM 计算查询向量）
query_embedding = get_embedding("缓存技术有哪些")
results = collection.query(
    query_embeddings=[query_embedding],
    n_results=3
)

print("查询: 缓存技术有哪些")
print("结果:")
for doc, dist in zip(results["documents"][0], results["distances"][0]):
    print(f"  - {doc} (距离: {dist:.4f})")

# 4. 再试几个查询
for query in ["Java 相关技术", "数据存储方案", "消息中间件"]:
    q_emb = get_embedding(query)
    results = collection.query(query_embeddings=[q_emb], n_results=2)
    print(f"\n查询: {query}")
    for doc in results["documents"][0]:
        print(f"  - {doc}")
```

**预期结果**：
```
查询: 缓存技术有哪些
结果:
  - Redis 是基于内存的键值存储，常用于缓存 (距离: 0.15)
  - MySQL 是关系型数据库，使用 SQL 查询 (距离: 0.45)
  - Kafka 是分布式消息队列，用于异步通信 (距离: 0.62)

查询: Java 相关技术
结果:
  - Java 是一种面向对象的编程语言
  - Spring Boot 是 Java 的微服务框架
```

**检查点**：你跑通了完整的向量检索流程。注意"缓存技术有哪些"没有出现"Redis"这个关键词，但 Redis 排第一——这就是语义检索 vs 关键词检索的区别。

---

## Day 3：用 Java 实战（最终目标）

### 任务 7：Java 调用 Embedding API（1 小时）

**目标**：用你熟悉的 Java 跑通 Embedding。

**方式选择**（二选一）：

| 方式 | 适合场景 | 复杂度 |
|------|---------|--------|
| **Spring AI + ZhipuAI** | 已有 Spring Boot 项目 | 中 |
| **OkHttp 直接调 API** | 只想快速验证 | 低 |

**选方式 2 先跑通**（最快）：

```java
// pom.xml
// 只需要 okHttp + jackson
```

```xml
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.0</version>
</dependency>
```

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.List;

public class EmbeddingDemo {

    private static final String API_URL = "https://open.bigmodel.cn/api/paas/v4/embeddings";
    private static final String API_KEY = "你的Key";
    private static final String MODEL = "embedding-3";

    public static void main(String[] args) throws IOException {
        String text1 = "Java 是一种面向对象的编程语言";
        String text2 = "Python 是一种解释型编程语言";
        String text3 = "今天中午吃火锅";

        // 1. 获取向量
        List<Double> v1 = getEmbedding(text1);
        List<Double> v2 = getEmbedding(text2);
        List<Double> v3 = getEmbedding(text3);

        System.out.println("向量维度: " + v1.size());

        // 2. 计算相似度
        double sim12 = cosineSimilarity(v1, v2);
        double sim13 = cosineSimilarity(v1, v3);

        System.out.printf("[%s] vs [%s] 相似度: %.4f%n", text1, text2, sim12);
        System.out.printf("[%s] vs [%s] 相似度: %.4f%n", text1, text3, sim13);
    }

    /** 调用智谱 Embedding API，返回向量 */
    static List<Double> getEmbedding(String text) throws IOException {
        OkHttpClient client = new OkHttpClient();

        // 构造请求体
        String body = String.format(
            "{\"model\":\"%s\",\"input\":\"%s\"}", MODEL, text
        );

        Request request = new Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer " + API_KEY)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(body, MediaType.parse("application/json")))
            .build();

        // 解析响应
        try (Response response = client.newCall(request).execute()) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.body().string());
            return mapper.convertValue(
                root.at("/data/0/embedding"),
                mapper.getTypeFactory().constructCollectionType(List.class, Double.class)
            );
        }
    }

    /** 余弦相似度 */
    static double cosineSimilarity(List<Double> a, List<Double> b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

**预期输出**：
```
向量维度: 2048
[Java 是一种面向对象的编程语言] vs [Python 是一种解释型编程语言] 相似度: 0.85
[Java 是一种面向对象的编程语言] vs [今天中午吃火锅] 相似度: 0.12
```

**检查点**：你用 Java 完成了"调用智谱 Embedding API → 拿到向量 → 计算余弦相似度"全流程。这就是 Day 1 用 Python 做的事，只不过换成了 Java。

---

### 任务 8：用 Spring AI 封装（1 小时，进阶）

**目标**：用 Spring 生态的标准方式做 Embedding，适合正式项目。

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-zhipuai-spring-boot-starter</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  ai:
    zhipu:
      api-key: ${ZHIPU_API_KEY}
      embedding:
        model: embedding-3
```

```java
@Service
public class EmbeddingService {

    @Autowired
    private EmbeddingModel embeddingModel;

    /** 获取单条文本的向量 */
    public float[] getEmbedding(String text) {
        EmbeddingResponse response = embeddingModel.call(
            new EmbeddingRequest(List.of(text), EmbeddingOptions.EMPTY)
        );
        return response.getResult().getOutput();
    }

    /** 计算两段文本的相似度 */
    public double similarity(String text1, String text2) {
        float[] v1 = getEmbedding(text1);
        float[] v2 = getEmbedding(text2);
        return cosineSimilarity(v1, v2);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

**检查点**：你有了可以在 Spring Boot 项目中直接用的 Embedding 服务类。

---

## 学习完成后，你应该能回答这些问题

| 问题 | 答案线索 |
|------|---------|
| Embedding 是什么？ | 文本 → 固定长度浮点数数组，编码了语义 |
| 余弦相似度怎么算？ | 点积 / (向量A的模 × 向量B的模)，值域 [-1, 1] |
| 为什么不用精确搜索？ | 百万级以上数据 O(N) 太慢，ANN 换一点精度提速到 O(log N) |
| 向量数据库和 MySQL 的区别？ | MySQL 精确匹配，向量数据库语义相似度搜索 |
| Java 项目怎么集成？ | OkHttp 直接调智谱 API 或 Spring AI + ZhipuAI 封装 |

> **整个学习计划预计 3 天完成。Day 1 建立直觉，Day 2 理解原理，Day 3 Java 落地。每个任务都有明确的检查点，跑完预期输出就算通过。**
