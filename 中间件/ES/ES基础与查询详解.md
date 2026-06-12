# Elasticsearch 基础与查询详解（新手入门）

> 从零掌握 ES 核心概念，全面攻克查询 DSL。适合新手系统学习。

---

## 一、Elasticsearch 是什么？

Elasticsearch（简称 ES）是一个基于 **Apache Lucene** 构建的**分布式搜索与分析引擎**。它提供**近实时（NRT）**的全文搜索、结构化搜索、数据分析能力。

**核心特性**：

| 特性 | 说明 |
|------|------|
| **分布式** | 天然支持集群，数据分片存储，水平扩展 |
| **全文搜索** | 基于倒排索引，毫秒级返回结果 |
| **RESTful API** | 所有操作通过 HTTP JSON 接口完成 |
| **近实时** | 写入后 1 秒内可搜索（默认 refresh_interval=1s） |
| **多租户** | 多个 Index 独立管理 |

---

## 二、ES vs MySQL 概念对比

| MySQL | Elasticsearch | 说明 |
|-------|--------------|------|
| Database | Cluster | 集群 |
| Table | Index（索引） | 文档的集合 |
| Row | Document（文档） | JSON 格式的一条数据 |
| Column | Field（字段） | JSON 中的 key |
| Schema | Mapping（映射） | 字段类型定义 |
| SQL | DSL | JSON 风格的查询语言 |

---

## 三、核心概念

### 3.1 集群（Cluster）与节点（Node）

```
                    ┌─────────────────────────────────┐
                    │         ES Cluster              │
                    │                                 │
                    │  ┌──────────┐ ┌──────────┐     │
                    │  │ Master   │ │ Data     │     │
                    │  │ Node 1   │ │ Node 2   │     │
                    │  └──────────┘ └──────────┘     │
                    │  ┌──────────┐                   │
                    │  │ Data     │                   │
                    │  │ Node 3   │                   │
                    │  └──────────┘                   │
                    └─────────────────────────────────┘
```

| 概念 | 说明 |
|------|------|
| **Master Node** | 管理集群元数据（创建/删除索引、分片分配），一个集群只有一个活跃 Master |
| **Data Node** | 存储数据和执行查询 |
| **Coordinating Node** | 接收客户端请求，路由到对应 Data Node，聚合结果后返回 |

### 3.2 索引（Index）与分片（Shard）

索引是**文档的集合**，由多个**分片**组成。分片又分为主分片（Primary）和副本（Replica）：

```
Index "orders"
├── Primary Shard 0  ──→ Node 1
│   └── Replica Shard 0 ──→ Node 2
├── Primary Shard 1  ──→ Node 2
│   └── Replica Shard 1 ──→ Node 3
└── Primary Shard 2  ──→ Node 3
    └── Replica Shard 2 ──→ Node 1
```

- **主分片**：数据写入的第一入口，创建索引时指定，之后不可修改
- **副本**：主分片的拷贝，可动态调整，提供故障转移和查询负载均衡

### 3.3 文档（Document）

文档是 ES 的**最小数据单元**，以 JSON 格式存储。每个文档有唯一的 `_id`：

```json
{
  "id": 1,
  "title": "华为 Mate 60 Pro",
  "price": 6999,
  "brand": "华为",
  "tags": ["5G", "卫星通话", "麒麟芯片"],
  "create_time": "2024-01-01T00:00:00"
}
```

### 3.4 Mapping（映射）

Mapping 定义字段的类型和索引方式，类似 MySQL 的 Schema。

**类比理解**：Mapping 就像建表时的 `CREATE TABLE`，定义了每个列的数据类型。

```json
PUT /products
{
  "mappings": {
    "properties": {
      "title":    { "type": "text", "analyzer": "ik_max_word" },
      "price":    { "type": "integer" },
      "brand":    { "type": "keyword" },
      "tags":     { "type": "keyword" },
      "create_time": { "type": "date" }
    }
  }
}
```

**常见字段类型**：

| 类型 | 说明 | 示例 |
|------|------|------|
| `text` | 全文搜索，会分词 | 文章内容、商品名称 |
| `keyword` | 精确匹配，不分词 | 标签、状态、ID |
| `integer` / `long` | 整数 | 价格、数量 |
| `float` / `double` | 浮点数 | 评分 |
| `boolean` | 布尔值 | 是否上架 |
| `date` | 日期 | 创建时间 |
| `object` | 嵌套 JSON 对象 | 地址信息 |
| `nested` | 独立索引的数组对象 | 订单商品列表 |
| `geo_point` | 地理位置 | 经纬度 |
| `ip` | IP 地址 | 用户 IP |

### 3.5 动态映射与显式映射

ES 可以自动推断字段类型（动态映射），但**生产环境建议显式定义 Mapping**，避免类型推断错误。

**反例（靠动态映射）**：

```json
POST /products/_doc
{ "title": "手机", "price": 6999 }
// ES 自动推断 price 为 long，可能不符合预期
```

**正例（显式定义 Mapping）**：

```json
PUT /products
{
  "mappings": {
    "dynamic": "strict",  // 严格模式，不允许未定义字段
    "properties": { ... }
  }
}
```

`dynamic` 取值：

| 值 | 含义 |
|----|------|
| `true`（默认） | 自动添加新字段 |
| `false` | 忽略新字段（不索引但存储） |
| `strict` | 拒绝新字段，报错 |
| `runtime` | 作为运行时字段 |

---

## 四、倒排索引 —— ES 快的核心

### 4.1 什么是倒排索引？

**正排索引**：文档 ID → 内容

**倒排索引**：词条 → 文档 ID 列表

```
原始文档：
Doc1: "华为智能手机"
Doc2: "华为平板电脑"
Doc3: "小米手机"

倒排索引：
┌──────────┬────────────┐
│  词条    │  文档ID列表  │
├──────────┼────────────┤
│  华为    │  Doc1,Doc2 │
│  智能    │  Doc1      │
│  手机    │  Doc1,Doc3 │
│  平板    │  Doc2      │
│  电脑    │  Doc2      │
│  小米    │  Doc3      │
└──────────┴────────────┘
```

搜索"华为手机"时：分词为"华为"+"手机" → 查倒排索引 → 取交集 → Doc1 命中。

### 4.2 倒排索引的三大组件

| 组件 | 作用 | 数据结构 |
|------|------|----------|
| **Posting List** | 存储该 Term 对应的 DocID 列表 + 词频 + 位置 | FOR 压缩 + Roaring Bitmap |
| **Term Dictionary** | 所有不重复 Term 的排序字典，支持二分查找 | 跳表/SkipList |
| **Term Index** | Term Dictionary 的前缀索引，加速定位 | FST（有限状态转换器） |

---

## 五、分词器（Analyzer）

### 5.1 分词流程

```
输入文本 → Character Filter → Tokenizer → Token Filters → 词条列表
```

**三步处理详解**：

| 步骤 | 组件 | 作用 | 示例 |
|------|------|------|------|
| 1 | Character Filter | 预处理（去除 HTML、替换字符） | `<p>华为</p>` → `华为` |
| 2 | Tokenizer | 切分词条 | `华为智能手机` → `华为` `智能` `手机` |
| 3 | Token Filters | 后处理（小写、停用词、同义词） | `The` → `the`; 移除 `的`/`了` |

### 5.2 常用分词器对比

| 分词器 | 输入 | 输出 | 适用场景 |
|--------|------|------|----------|
| `standard` | "The quick brown fox" | `the`, `quick`, `brown`, `fox` | 英文 |
| `whitespace` | "hello world" | `hello`, `world` | 简单分词 |
| `keyword` | "华为手机" | `华为手机`（整体） | 精确匹配 |
| **`ik_smart`** | "华为智能手机" | `华为`, `智能`, `手机` | 中文粗粒度 |
| **`ik_max_word`** | "华为智能手机" | `华为`, `智能`, `手机`, `智能手机` | 中文细粒度 |
| `pinyin` | "手机" | `shouji`, `sj` | 拼音搜索 |

**新手记忆口诀**：索引写入时用 `ik_max_word`（尽量多分，提高召回），搜索时用 `ik_smart`（粒度较粗，提高精度）。

### 5.3 测试分词效果

```bash
# 测试分词器
POST /_analyze
{
  "analyzer": "ik_max_word",
  "text": "华为智能手机5G旗舰"
}

# 返回
{
  "tokens": [
    { "token": "华为", "position": 0 },
    { "token": "智能", "position": 1 },
    { "token": "手机", "position": 2 },
    { "token": "智能手机", "position": 1 },
    { "token": "5g", "position": 3 },
    { "token": "旗舰", "position": 4 }
  ]
}
```

---

## 六、查询 DSL —— 全文检索（Query）

> 以下示例基于 products 索引，包含商品数据。

### 6.1 查询分类总览

| 查询类型 | 说明 | 是否分词 | 计分 |
|----------|------|----------|------|
| **match** | 全文匹配，分词后搜索 | ✅ | ✅ |
| **match_phrase** | 短语匹配，词条顺序一致 | ✅ | ✅ |
| **match_all** | 查询所有文档 | — | ✅ |
| **multi_match** | 多字段匹配 | ✅ | ✅ |
| **term** | 精确匹配，不分词 | ❌ | ✅/❌ |
| **terms** | 多值精确匹配 | ❌ | ✅/❌ |
| **range** | 范围查询 | ❌ | ✅/❌ |
| **exists** | 字段是否存在 | — | ❌ |
| **prefix** | 前缀匹配 | ❌ | ✅ |
| **wildcard** | 通配符匹配 | ❌ | ✅ |
| **fuzzy** | 模糊/纠错查询 | ❌ | ✅ |
| **regexp** | 正则匹配 | ❌ | ✅ |
| **ids** | 按 _id 批量查询 | — | ❌ |
| **bool** | 组合查询 | — | ✅ |

> 注意：term 系列查询放在 Query 上下文中会计分，放在 Filter 上下文中不计分且缓存。

---

### 6.2 match 查询（最常用，入门必学）

**match** 是全文搜索的基石，会对搜索词执行分词，然后匹配包含任意词条的文档。

```json
GET /products/_search
{
  "query": {
    "match": {
      "title": "华为手机"
    }
  }
}
// 分词为"华为"和"手机"，匹配包含"华为"或"手机"的文档
// 同时包含两个词条的文档得分更高
```

**match 的 operator 参数**：

```json
// operator: and → 必须同时包含所有词条
{
  "query": {
    "match": {
      "title": {
        "query": "华为手机",
        "operator": "and"
      }
    }
  }
}
// 文档必须同时包含"华为"和"手机"才算命中

// operator: or（默认）→ 包含任意一个即可
```

**match 的 minimum_should_match 参数**：

```json
// 至少匹配 2 个词条
{
  "query": {
    "match": {
      "title": {
        "query": "华为 5G 旗舰 手机",
        "minimum_should_match": 2
      }
    }
  }
}
```

**新手理解**：`minimum_should_match` 就像一个"模糊门槛" —— 用户搜了 4 个词，你要求至少命中 2 个，既不太严也不太松。

---

### 6.3 match_phrase 短语匹配

要求词条**相邻且顺序一致**。

```json
GET /products/_search
{
  "query": {
    "match_phrase": {
      "title": "华为手机"
    }
  }
}
// Doc: "华为智能手机" → ✅ 命中（"华为"和"手机"相邻且顺序正确）
// Doc: "手机华为" → ❌ 不命中（顺序不对）
// Doc: "华为最新手机" → ❌ 不命中（不紧邻）
```

**slop 参数**：允许词条之间有间隔。

```json
{
  "query": {
    "match_phrase": {
      "title": {
        "query": "华为手机",
        "slop": 1
      }
    }
  }
}
// "华为最新手机" → ✅ 命中（"华为"和"手机"之间隔了 1 个词）
// slop 越大，匹配越宽松
```

**新手记忆**：`slop` = "允许跳过的词数"，`slop=2` 就是中间最多隔 2 个词。

---

### 6.4 match_all 与 match_none

```json
// 查询所有文档
{ "query": { "match_all": {} } }

// 不返回任何文档（用于只取聚合结果等场景）
{ "query": { "match_none": {} } }
```

---

### 6.5 multi_match 多字段搜索

当用户输入一个关键词，需要在多个字段中搜索时使用。

```json
GET /products/_search
{
  "query": {
    "multi_match": {
      "query": "华为旗舰",
      "fields": ["title^3", "brand^2", "description"]
    }
  }
}
// title 权重 3 倍，brand 权重 2 倍，description 权重 1 倍
// 符合 title 匹配的文档排序更靠前
```

**multi_match 的 type 类型**：

| type | 说明 |
|------|------|
| `best_fields`（默认） | 取匹配最好的字段的分数 |
| `most_fields` | 所有匹配字段分数求和 |
| `cross_fields` | 跨字段匹配（如"人名 地名"） |
| `phrase` | 在每个字段上执行 match_phrase |
| `phrase_prefix` | 在每个字段上执行 match_phrase_prefix |

```json
// cross_fields：适合搜索"人+地点"
{
  "query": {
    "multi_match": {
      "query": "张三 北京",
      "type": "cross_fields",
      "fields": ["name", "city"],
      "operator": "and"
    }
  }
}
```

---

### 6.6 match_phrase_prefix 前缀短语匹配（搜索提示）

适合"边输入边搜索"的场景：

```json
GET /products/_search
{
  "query": {
    "match_phrase_prefix": {
      "title": {
        "query": "华为智",
        "max_expansions": 10
      }
    }
  }
}
// 匹配 title 中包含"华为智"开头短语的文档
// 如 "华为智能"、"华为智慧"
// max_expansions：限制前缀扩展的 Term 数量
```

---

### 6.7 match_bool_prefix

ES 7.x+ 推荐替代 `match_phrase_prefix` 的查询，将最后一个词条转为 `prefix` 查询：

```json
{
  "query": {
    "match_bool_prefix": {
      "title": {
        "query": "华为智",
        "operator": "and"
      }
    }
  }
}
// 分词为 ["华为", "智"]
// "华为" 做 match，"智" 做 prefix：匹配以"智"开头的词条
```

---

### 6.8 query_string 与 simple_query_string（类 Lucene 语法）

**query_string** 支持完整的 Lucene 查询语法，适合高级用户：

```json
GET /products/_search
{
  "query": {
    "query_string": {
      "query": "(华为 OR 小米) AND 手机 NOT 翻新",
      "default_field": "title"
    }
  }
}
// 支持 AND/OR/NOT/通配符/正则等
```

**simple_query_string** 更安全，不会因语法错误抛异常：

```json
{
  "query": {
    "simple_query_string": {
      "query": "华为 + 手机 - 翻新 | 5G*",
      "fields": ["title", "description"]
    }
  }
}
// + 必须匹配  | OR  - 排除  * 通配符
```

> ⚠️ **新手注意**：`query_string` 虽然强大，但容易写错导致查询失败。推荐优先使用 `match` + `bool` 组合。

---

### 6.9 全文查询小结

| 查询 | 场景 | 一句话 |
|------|------|--------|
| `match` | 搜商品名 | 最常用，分词后匹配 |
| `match_phrase` | 搜完整短语"华为手机" | 词条必须相邻且顺序一致 |
| `multi_match` | 同时在标题、描述、品牌中搜 | 多字段权重搜索 |
| `match_phrase_prefix` | 搜索框输入"华为"时提示 | 边输入边搜 |
| `query_string` | 高级用户写复杂表达式 | `(A OR B) AND C` |

---

## 七、精确查询（Term-Level Query）

精确查询**不进行分词**，直接拿输入值去和倒排索引中的 Term 比对。

### 7.1 term — 精确匹配一个值

```json
GET /products/_search
{
  "query": {
    "term": { "brand": "华为" }
  }
}
// brand 是 keyword 类型 → 不分词 → 完全匹配 "华为"
```

**⚠️ 最易错点：对 text 字段用 term**：

```json
// ❌ 错误：title 是 text 类型，被分词为["华为","手机"]
// term 不会分词，直接找 "华为手机" 这个词条，找不到！
{ "query": { "term": { "title": "华为手机" } } }

// ✅ 正确：用 match
{ "query": { "match": { "title": "华为手机" } } }
```

### 7.2 terms — 匹配多个值（IN 查询）

```json
{
  "query": {
    "terms": { "brand": ["华为", "小米", "OPPO"] }
  }
}
// 等价于 SQL: WHERE brand IN ('华为', '小米', 'OPPO')
```

**terms lookup**（从另一个索引获取匹配值）：

```json
// 场景：查询用户收藏品牌下的商品
{
  "query": {
    "terms": {
      "brand": {
        "index": "user_favorites",   // 另一个索引
        "id": "user_1001",           // 该索引中的文档 ID
        "path": "favorite_brands"    // 取这个字段的值
      }
    }
  }
}
```

### 7.3 range — 范围查询

```json
{
  "query": {
    "range": {
      "price": {
        "gte": 1000,    // >= 1000
        "lt": 5000      // < 5000
      }
    }
  }
}
```

| 参数 | 含义 | 记忆 |
|------|------|------|
| `gte` | Greater Than or Equal | ≥ |
| `gt` | Greater Than | > |
| `lte` | Less Than or Equal | ≤ |
| `lt` | Less Than | < |

**日期范围查询**：

```json
{
  "query": {
    "range": {
      "create_time": {
        "gte": "2024-01-01",
        "lte": "2024-12-31",
        "format": "yyyy-MM-dd",
        "time_zone": "+08:00"
      }
    }
  }
}

// 也支持 now 表达式
{
  "query": {
    "range": {
      "create_time": {
        "gte": "now-7d/d",   // 7天前零点
        "lt": "now"          // 现在
      }
    }
  }
}
```

**now 表达式速查**：

| 表达式 | 含义 |
|--------|------|
| `now` | 当前时间 |
| `now-1d` | 1 天前 |
| `now-7d/d` | 7 天前零点 |
| `now-1M/M` | 1 月前月初 |
| `now/d` | 今天零点 |

### 7.4 exists — 判断字段是否存在

```json
// 查询有 description 字段的文档
{ "query": { "exists": { "field": "description" } } }

// 查询缺少 description 字段的文档
{
  "query": {
    "bool": {
      "must_not": { "exists": { "field": "description" } }
    }
  }
}
// 注意：字段值为 null 或 [] 也视为"不存在"
```

### 7.5 prefix — 前缀查询

```json
// 查询 brand 以 "华" 开头的
{ "query": { "prefix": { "brand": "华" } } }

// 或 text 字段
{ "query": { "prefix": { "title": "华为" } } }
```

> ⚠️ `prefix` 查询性能较差，大索引上慎用。需要前缀搜索优先考虑 `match_phrase_prefix` 或 Completion Suggester。

### 7.6 wildcard — 通配符查询

```json
// ? 匹配单个字符，* 匹配 0 个或多个字符
{ "query": { "wildcard": { "brand": "华?" } } }   // 匹配 "华为"、"华硕"
{ "query": { "wildcard": { "title": "*手机*" } } } // 包含"手机"

// 注意：避免 *xx 这种前导通配符，性能极差
// ❌ { "wildcard": { "title": "*手机" } }    前导通配符！
// ✅ { "wildcard": { "title": "华*" } }       后缀通配符
```

### 7.7 fuzzy — 模糊查询（纠错）

用于处理拼写错误，基于 **Levenshtein 编辑距离**：

```json
{
  "query": {
    "fuzzy": {
      "title": {
        "value": "huwawei",
        "fuzziness": "AUTO"
      }
    }
  }
}
// 能匹配到 "huawei"
```

| 参数 | 说明 |
|------|------|
| `fuzziness` | 允许的编辑距离（`AUTO` 自动根据词长度计算） |
| `prefix_length` | 前 N 个字符必须精确匹配（性能优化） |
| `max_expansions` | 最大扩展词数 |

> **新手理解**：用户搜 "iphone" 写成了 "iphnoe"（字母顺序反了），fuzzy 能帮你纠正回来。

### 7.8 regexp — 正则表达式查询

```json
{
  "query": {
    "regexp": {
      "brand": "华.*"
    }
  }
}
```

> ⚠️ 正则查询性能极差，生产环境尽量用 prefix/wildcard 替代。

### 7.9 ids — 按文档 ID 批量查询

```json
{
  "query": {
    "ids": { "values": ["1", "3", "5"] }
  }
}
// 等价于 GET /products/_doc/1 + GET /products/_doc/3 + GET /products/_doc/5
```

---

## 八、Bool 组合查询（重点掌握）

Bool 查询是 ES 中**最强大最常用**的查询，将多个查询条件组合在一起。

### 8.1 四种子句

```
Bool 查询
├── must        → 必须满足（AND），参与计分
├── filter      → 必须满足（AND），不参与计分，自动缓存
├── should      → 应该满足（OR），参与计分
└── must_not    → 必须不满足（NOT），不参与计分
```

### 8.2 基础示例

```json
GET /products/_search
{
  "query": {
    "bool": {
      "must": [
        { "match": { "title": "手机" } }
      ],
      "filter": [
        { "term": { "brand": "华为" } },
        { "range": { "price": { "gte": 3000, "lte": 8000 } } },
        { "term": { "in_stock": true } }
      ],
      "should": [
        { "match": { "title": "5G" } },
        { "match": { "title": "卫星通话" } }
      ],
      "must_not": [
        { "term": { "status": "discontinued" } }
      ],
      "minimum_should_match": 1
    }
  }
}
// 翻译成人话：
// 标题必须包含"手机"
// 且品牌必须是华为、价格在 3000-8000、且在售
// 如果标题还包含"5G"或"卫星通话"则加分
// 排除已停产的
// should 中至少满足 1 个（由 minimum_should_match 指定）
```

### 8.3 minimum_should_match 详解

```json
// should 中至少满足 2 个
{ "bool": { "should": [...], "minimum_should_match": 2 } }

// 按百分比：至少满足 75%
{ "bool": { "should": [...], "minimum_should_match": "75%" } }

// 组合：3 个以内满足 2 个，超过 3 个满足 80%
{ "bool": { "should": [...], "minimum_should_match": "2<80%" } }
```

> **关键规则**：如果 bool 中**没有 must/filter，只有 should**，则 `minimum_should_match` 默认为 1（至少匹配一个 should）。如果有 must/filter，则默认为 0。

### 8.4 Bool 多层嵌套

```json
{
  "query": {
    "bool": {
      "must": [
        { "match": { "title": "手机" } }
      ],
      "filter": [
        {
          "bool": {
            "should": [
              { "term": { "brand": "华为" } },
              { "term": { "brand": "小米" } }
            ],
            "minimum_should_match": 1
          }
        }
      ]
    }
  }
}
// 翻译：标题包含"手机"，且品牌是华为或小米
```

### 8.5 新手常见错误

```
❌ 把精确匹配放在 must 中
   { "must": [{ "term": { "brand": "华为" } }] }
   → term 在 must 中也会计分，但精确匹配不需要计分，浪费

✅ 精确匹配放 filter 中
   { "filter": [{ "term": { "brand": "华为" } }] }
   → 不计分 + 自动缓存，更快
```

---

## 九、Filter 上下文详解

### 9.1 Query vs Filter 对比

| 维度 | Query 上下文 | Filter 上下文 |
|------|-------------|--------------|
| **计分** | ✅ 计算 `_score` | ❌ 不计分 |
| **缓存** | ❌ 不缓存 | ✅ BitSet 自动缓存 |
| **速度** | 较慢 | 较快 |
| **用途** | 全文搜索、相关性排序 | 条件过滤、精确匹配 |

**核心原则**：能放 Filter 的就放 Filter。

### 9.2 Filter 单独使用

```json
{
  "query": {
    "bool": {
      "filter": [
        { "term": { "status": "active" } },
        { "range": { "price": { "gte": 1000 } } }
      ]
    }
  }
}
// pure filter：不关心分数，快速过滤
// 每个文档的 _score 都是 0.0（相同分数）
```

### 9.3 constant_score 查询

将 Query 查询包装成 Filter 行为（统一分数）：

```json
{
  "query": {
    "constant_score": {
      "filter": { "term": { "status": "active" } },
      "boost": 1.0
    }
  }
}
```

---

## 十、复合查询（Compound Queries）

### 10.1 bool 查询

见第八章，最核心的复合查询。

### 10.2 boosting 查询

降低某些匹配结果的分数，但不完全排除：

```json
{
  "query": {
    "boosting": {
      "positive": { "match": { "title": "苹果" } },
      "negative": { "match": { "title": "苹果醋" } },
      "negative_boost": 0.5
    }
  }
}
// 匹配"苹果"的文档正常打分
// 同时匹配"苹果醋"的文档分数乘以 0.5（降权）
// 注意：不是排除！只是降权
```

**新手理解**：你想搜"苹果"手机，但不希望"苹果醋"这种无关结果排太前，就用 boosting 降权。

### 10.3 dis_max 查询

多字段匹配时，取**最大分数**而非求和：

```json
{
  "query": {
    "dis_max": {
      "queries": [
        { "match": { "title": "华为手机" } },
        { "match": { "description": "华为手机" } }
      ],
      "tie_breaker": 0.3
    }
  }
}
// 取两个 query 的最高分作为最终分数
// tie_breaker：其他字段的分数乘以 0.3 加到最高分上
```

**对比 multi_match**：`multi_match` 的 `best_fields` 类型内部就是 `dis_max` 实现。

### 10.4 function_score — 自定义评分（高级）

这是 ES 最强大也是最复杂的查询之一，允许你**自定义排序规则**。

```json
{
  "query": {
    "function_score": {
      "query": { "match": { "title": "手机" } },
      "functions": [
        {
          "filter": { "term": { "brand": "华为" } },
          "weight": 2
        },
        {
          "filter": { "range": { "sales_count": { "gte": 1000 } } },
          "weight": 3
        }
      ],
      "score_mode": "multiply",
      "boost_mode": "multiply"
    }
  }
}
// 基础分 = match("手机") 的 BM25 分数
// 华为品牌 → 分数 × 2
// 销量 ≥ 1000 → 分数 × 3
// 最终分数 = 基础分 × 2 × 3（如果两个条件都满足）
```

**score_mode**（functions 之间的计算方式）：

| 值 | 含义 |
|----|------|
| `multiply` | 相乘（默认） |
| `sum` | 相加 |
| `avg` | 取平均 |
| `max` / `min` | 取最大/最小 |
| `first` | 只用第一个匹配的 function |

**boost_mode**（functions 结果与基础分的计算方式）：

| 值 | 含义 |
|----|------|
| `multiply` | 基础分 × functions 分（默认） |
| `sum` | 基础分 + functions 分 |
| `avg` | 取平均 |
| `max` / `min` | 取最大/最小 |
| `replace` | 用 functions 分替换基础分 |

**实际场景**：

```json
// 场景：商品搜索，热销商品加权，新商品加权
{
  "query": {
    "function_score": {
      "query": { "match": { "title": "手机" } },
      "functions": [
        { "field_value_factor": { "field": "sales_count", "factor": 0.1, "modifier": "log1p" } },
        { "gauss": { "create_time": { "origin": "now", "scale": "30d", "decay": 0.5 } } }
      ],
      "score_mode": "sum",
      "boost_mode": "sum"
    }
  }
}
// 销量越高 → 加分（log1p 防止线性增长）
// 商品越新 → 加分（高斯衰减函数）
```

---

## 十一、排序（Sort）

### 11.1 基本排序

```json
GET /products/_search
{
  "query": { "match_all": {} },
  "sort": [
    { "price": { "order": "asc" } },
    { "sales_count": { "order": "desc" } },
    { "_score": { "order": "desc" } }
  ]
}
// 先按价格升序，价格相同按销量降序，再按相关性
```

### 11.2 缺失值排序

```json
{
  "sort": [
    {
      "sales_count": {
        "order": "desc",
        "missing": "_last"   // 缺失的排最后（或 _first）
      }
    }
  ]
}
```

### 11.3 地理位置排序

```json
{
  "sort": [
    {
      "_geo_distance": {
        "location": { "lat": 39.9, "lon": 116.4 },
        "order": "asc",
        "unit": "km",
        "mode": "min"
      }
    }
  ]
}
```

### 11.4 按数组字段排序

```json
{
  "sort": [
    { "tags": { "order": "asc", "mode": "min" } }
  ]
}
// mode: min / max / avg / sum / median
```

---

## 十二、分页（Pagination）

### 12.1 from/size（常规分页）

```json
GET /products/_search
{
  "from": 0,
  "size": 10,
  "query": { "match_all": {} }
}
// 第 1 页：from=0  size=10
// 第 2 页：from=10 size=10
// 第 N 页：from=(N-1)*10  size=10
```

**硬限制**：`from + size ≤ 10000`（`index.max_result_window`）

### 12.2 search_after（深度分页）

```json
// 第一页
GET /products/_search
{
  "size": 10,
  "query": { "match_all": {} },
  "sort": [{ "price": "asc" }, { "_id": "asc" }]
}
// 记录最后一条的 sort 值：[4999, "product_123"]

// 第二页
GET /products/_search
{
  "size": 10,
  "query": { "match_all": {} },
  "sort": [{ "price": "asc" }, { "_id": "asc" }],
  "search_after": [4999, "product_123"]
}
```

**注意事项**：

- `_id` 必须放在 sort 最后（保证唯一性，避免丢数据）
- 不能跳页，只能一页一页翻
- 适合无限滚动、批量导出

### 12.3 Scroll（已废弃，了解即可）

Scroll 生成数据快照，适合一次性导出大量数据。ES 7.x 后推荐用 **PIT（Point In Time）+ search_after** 替代。

### 12.4 PIT（Point In Time）— ES 7.10+

```json
// 1. 创建 PIT
POST /products/_pit?keep_alive=5m
// 返回 { "id": "xxx" }

// 2. 使用 PIT 搜索
GET /_search
{
  "size": 100,
  "query": { "match_all": {} },
  "pit": { "id": "xxx", "keep_alive": "5m" },
  "sort": [{ "_shard_doc": "asc" }]
}

// 3. 删除 PIT
DELETE /_pit
{ "id": "xxx" }
```

> PIT 隔离了索引变更，确保遍历期间数据视图一致。

---

## 十三、高亮（Highlight）

### 13.1 默认高亮

```json
GET /products/_search
{
  "query": { "match": { "title": "华为手机" } },
  "highlight": {
    "fields": {
      "title": {}
    }
  }
}
// 返回的 title 中包含 <em>华为</em> <em>手机</em>
```

### 13.2 自定义标签

```json
{
  "highlight": {
    "pre_tags": ["<span class='red'>"],
    "post_tags": ["</span>"],
    "fields": {
      "title": { "number_of_fragments": 0 }
    }
  }
}
// <span class='red'>华为</span> <span class='red'>手机</span>
```

### 13.3 高亮类型

| 类型 | 说明 |
|------|------|
| `unified`（默认） | 平衡性能和准确度 |
| `plain` | 精确但慢 |
| `fvh`（Fast Vector Highlighter） | 需要 `term_vector: with_positions_offsets` |

---

## 十四、聚合查询（Aggregation）—— 数据统计分析

聚合是 ES 的**数据分析引擎**，类似 SQL 的 GROUP BY + 聚合函数。

### 14.1 聚合查询结构

```json
GET /products/_search
{
  "size": 0,         // 不返回文档，只返回聚合结果
  "aggs": {
    "my_agg_name": {  // 自定义聚合名称
      "agg_type": { ... }  // 聚合类型 + 参数
    }
  }
}
```

### 14.2 Bucket 聚合（分桶）

**terms — 按字段值分组**：

```json
GET /products/_search
{
  "size": 0,
  "aggs": {
    "brands": {
      "terms": { "field": "brand", "size": 10, "order": { "_count": "desc" } }
    }
  }
}
// 返回：
// { "buckets": [
//   { "key": "华为", "doc_count": 150 },
//   { "key": "小米", "doc_count": 120 },
//   ...
// ]}
```

**range — 按范围分组**：

```json
{
  "aggs": {
    "price_ranges": {
      "range": {
        "field": "price",
        "ranges": [
          { "key": "千元以下", "to": 1000 },
          { "key": "1000-5000", "from": 1000, "to": 5000 },
          { "key": "5000以上", "from": 5000 }
        ]
      }
    }
  }
}
```

**date_histogram — 按时间间隔分组（最常用）**：

```json
{
  "aggs": {
    "sales_over_time": {
      "date_histogram": {
        "field": "create_time",
        "calendar_interval": "day",    // 按天
        "format": "yyyy-MM-dd",
        "min_doc_count": 1
      }
    }
  }
}
// interval 可选：year, quarter, month, week, day, hour, minute, second
// 或 fixed_interval: "30m", "1d"
```

**histogram — 按数值间隔分组**：

```json
{
  "aggs": {
    "price_histogram": {
      "histogram": {
        "field": "price",
        "interval": 500     // 每 500 元一个区间
      }
    }
  }
}
```

### 14.3 Metric 聚合（计算指标）

**基本指标**：

```json
{
  "aggs": {
    "avg_price": { "avg": { "field": "price" } },
    "max_price": { "max": { "field": "price" } },
    "min_price": { "min": { "field": "price" } },
    "sum_price": { "sum": { "field": "price" } },
    "count":     { "value_count": { "field": "price" } }
  }
}
```

**stats — 一次性获取所有统计值**：

```json
{
  "aggs": {
    "price_stats": {
      "stats": { "field": "price" }
    }
  }
}
// 返回：count, min, max, avg, sum
```

**cardinality — 近似去重计数**：

```json
{
  "aggs": {
    "unique_brands": {
      "cardinality": { "field": "brand", "precision_threshold": 40000 }
    }
  }
}
// 类似 SQL: SELECT COUNT(DISTINCT brand)
// precision_threshold 以内精确，超出为近似值
```

### 14.4 嵌套聚合（子聚合）

聚合可以层层嵌套，实现复杂数据分析：

```json
GET /products/_search
{
  "size": 0,
  "aggs": {
    "by_brand": {
      "terms": { "field": "brand" },
      "aggs": {                          // 每个品牌下的子聚合
        "avg_price": { "avg": { "field": "price" } },
        "price_range": {
          "range": {
            "field": "price",
            "ranges": [
              { "to": 1000 },
              { "from": 1000, "to": 5000 },
              { "from": 5000 }
            ]
          }
        }
      }
    }
  }
}
// 结果：每个品牌 → 平均价格 + 价格区间分布
```

### 14.5 Pipeline 聚合

对聚合结果再做聚合：

```json
{
  "aggs": {
    "sales_per_month": {
      "date_histogram": { "field": "create_time", "calendar_interval": "month" },
      "aggs": {
        "total_sales": { "sum": { "field": "price" } }
      }
    },
    "avg_monthly_sales": {
      "avg_bucket": {
        "buckets_path": "sales_per_month>total_sales"
      }
    }
  }
}
// 先按月统计销售额 → 再计算月均销售额
```

### 14.6 top_hits — 每组取 Top N

```json
{
  "aggs": {
    "by_brand": {
      "terms": { "field": "brand" },
      "aggs": {
        "top_products": {
          "top_hits": {
            "size": 3,
            "sort": [{ "sales_count": "desc" }],
            "_source": ["title", "price", "sales_count"]
          }
        }
      }
    }
  }
}
// 每个品牌下取销量最高的 3 个商品
```

---

## 十五、结果过滤与优化

### 15.1 _source 过滤（返回指定字段）

```json
GET /products/_search
{
  "_source": ["title", "price", "brand"],
  "query": { "match_all": {} }
}

// 或者用 includes/excludes
{
  "_source": {
    "includes": ["title", "price"],
    "excludes": ["description", "detail"]
  }
}
```

### 15.2 返回字段计数

```json
{
  "fields": ["title", "price"],
  "_source": false,
  "query": { "match_all": {} }
}
```

### 15.3 关闭 _source

```json
{
  "_source": false,
  "query": { "match_all": {} }
}
// 只返回 _id 和 _score，不返回原始 JSON
// 节省带宽，适合只需要 docId 的场景
```

---

## 十六、索引操作速查

### 16.1 索引管理

```json
// 创建索引（带 Settings + Mapping）
PUT /products
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "refresh_interval": "5s"
  },
  "mappings": {
    "properties": {
      "title": { "type": "text", "analyzer": "ik_max_word" },
      "price": { "type": "integer" },
      "brand": { "type": "keyword" },
      "tags": { "type": "keyword" },
      "create_time": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" }
    }
  }
}

// 查看索引
GET /products

// 查看索引 Mapping
GET /products/_mapping

// 查看索引 Settings
GET /products/_settings

// 删除索引
DELETE /products

// 查看所有索引
GET /_cat/indices?v

// 查看索引分片
GET /_cat/shards?v
```

### 16.2 文档 CRUD

```json
// 添加文档（指定 ID）
PUT /products/_doc/1
{ "title": "华为 Mate 60 Pro", "price": 6999, "brand": "华为" }

// 添加文档（自动生成 ID）
POST /products/_doc
{ "title": "小米 14 Ultra", "price": 5999, "brand": "小米" }

// 查询单个文档
GET /products/_doc/1

// 部分更新
POST /products/_update/1
{ "doc": { "price": 5999 } }

// 使用脚本更新（自增）
POST /products/_update/1
{ "script": { "source": "ctx._source.sales_count += 1" } }

// 删除文档
DELETE /products/_doc/1

// 按查询删除
POST /products/_delete_by_query
{ "query": { "term": { "status": "discontinued" } } }

// 按查询更新
POST /products/_update_by_query
{
  "script": { "source": "ctx._source.price += 100" },
  "query": { "term": { "brand": "华为" } }
}

// 批量操作
POST /_bulk
{"index": {"_index": "products", "_id": "1"}}
{"title": "产品A", "price": 100}
{"create": {"_index": "products", "_id": "2"}}
{"title": "产品B", "price": 200}
{"update": {"_index": "products", "_id": "1"}}
{"doc": {"price": 150}}
{"delete": {"_index": "products", "_id": "2"}}
```

### 16.3 文档数量统计

```json
// 统计文档总数
GET /products/_count
{ "query": { "term": { "brand": "华为" } } }

// 结果 { "count": 150 }
```

---

## 十七、搜索模板与参数

### 17.1 搜索结果字段说明

```json
{
  "took": 5,                  // 查询耗时（毫秒）
  "timed_out": false,         // 是否超时
  "_shards": { "total": 3, "successful": 3, "skipped": 0, "failed": 0 },
  "hits": {
    "total": { "value": 10000, "relation": "eq" },  // 总命中数
    "max_score": 12.5,        // 最高分
    "hits": [
      {
        "_index": "products",
        "_id": "1",
        "_score": 12.5,        // 相关性分数
        "_source": { ... }     // 原始 JSON
      }
    ]
  }
}
```

### 17.2 常用 URL 参数

```bash
# 不返回文档，只看总数
GET /products/_search?filter_path=hits.total
{ "query": { "match_all": {} } }

# 只返回聚合
GET /products/_search?filter_path=aggregations

# 超时控制
GET /products/_search?timeout=2s

# 限制并发分片数
GET /products/_search?max_concurrent_shard_requests=2

# 请求直接结束（不等待慢分片）
GET /products/_search?allow_partial_search_results=false

# 返回版本号
GET /products/_search?version=true
```

---

## 十八、数据写入与搜索流程

### 18.1 写入流程

```
文档写入
  ↓
路由到分片：shard = hash(_id) % num_primary_shards
  ↓
① 写入 Memory Buffer（内存）
② 同时写入 Translog（事务日志，防丢）
  ↓
③ Refresh（默认 1s）：Memory Buffer → Filesystem Cache → 可搜索
  ↓
④ Flush（每 30min 或 Translog 达 512MB）：Filesystem Cache → 磁盘 Segment
  ↓
⑤ Merge：后台合并小 Segment 为大 Segment（减少文件数，提高查询效率）
```

| 操作 | 触发频率 | 对用户的影响 |
|------|----------|-------------|
| **Refresh** | 1s | 数据变为可搜索 |
| **Flush** | 30min / 512MB translog | 数据持久化到磁盘 |
| **Merge** | 后台自动 | 释放磁盘空间 |

### 18.2 搜索流程（Query Then Fetch）

```
客户端发送请求
  ↓
① 协调节点（Coordinator）接收请求
  ↓
② Query Phase（查询阶段）：
   → 并行发送到所有相关分片
   → 每个分片返回 (docId, _score)，不返回完整文档
   → 协调节点排序后确定全局 Top N
  ↓
③ Fetch Phase（获取阶段）：
   → 根据 docId 到对应分片拉取完整 _source
   → 返回给客户端
```

> **为什么分两阶段？** Query 阶段只传 docId（轻量），Fetch 阶段再拉完整数据（重量），减少网络开销。

---

## 十九、text vs keyword 终极对比

| 维度 | text | keyword |
|------|------|---------|
| **分词** | ✅ 分词 | ❌ 不分词，原样存储 |
| **用途** | 全文搜索 | 精确匹配、排序、聚合 |
| **如何查询** | `match` | `term` |
| **能否排序** | ❌ | ✅ |
| **能否聚合** | ❌（需 fielddata，极耗内存） | ✅ |
| **典型场景** | 文章内容、商品名 | 状态、标签、ID、品牌 |

**Multi-Field 最佳实践**：

```json
{
  "title": {
    "type": "text",
    "analyzer": "ik_max_word",
    "fields": {
      "keyword": { "type": "keyword", "ignore_above": 256 }
    }
  }
}
// 搜索用 title → match 查询
// 排序/聚合用 title.keyword → term 查询
```

---

## 二十、相关性评分（BM25）

### 20.1 BM25 三要素

| 要素 | 含义 | 趋势 |
|------|------|------|
| **TF（词频）** | 词在文档中出现的次数 | 出现越多分越高（但有上限，非线性） |
| **IDF（逆文档频率）** | 词在所有文档中的稀有程度 | 越稀有分越高 |
| **Field Length** | 字段长度 | 字段越短分越高（短文本匹配价值更大） |

### 20.2 查看评分细节

```json
GET /products/_search
{
  "explain": true,
  "query": { "match": { "title": "华为手机" } }
}
// explain: true 返回详细的评分计算过程
```

---

## 二十一、生产环境性能优化

| 优化方向 | 具体措施 |
|----------|----------|
| **写入优化** | Bulk 批量（1000~5000 条/批）、调大 `refresh_interval=30s`、异步 Translog |
| **查询优化** | Filter 代替 Query、禁用前导通配符 `*xx`、`_source` 过滤 |
| **分页优化** | 用 `search_after` 替代 `from/size` |
| **聚合优化** | 用 keyword 聚合、限制聚合 `size`、高基数用 `cardinality` |
| **字段优化** | 关闭不需要索引的字段 `"index": false`、`_source` 按需返回 |
| **分片规划** | 单分片 30-50GB、单节点分片数 ≤ 磁盘 GB × 20 |
| **JVM 优化** | 堆内存 ≤ 31GB（利用指针压缩）、剩余给 OS Page Cache |
| **Mapping 优化** | `dynamic: strict` 避免字段爆炸 |

**生产环境索引模板示例**：

```json
PUT /_index_template/product_template
{
  "index_patterns": ["products-*"],
  "template": {
    "settings": {
      "number_of_shards": 5,
      "number_of_replicas": 1,
      "refresh_interval": "10s",
      "translog": { "durability": "async", "sync_interval": "30s" }
    },
    "mappings": {
      "dynamic": "strict",
      "properties": {
        "title": { "type": "text", "analyzer": "ik_max_word" },
        "price": { "type": "integer" },
        "brand": { "type": "keyword" },
        "create_time": { "type": "date" }
      }
    }
  }
}
```

---

## 附录：查询速查表

| 场景 | 查询类型 | 示例 |
|------|----------|------|
| 搜"手机" | `match` | `{"match": {"title": "手机"}}` |
| 精确搜品牌 | `term` | `{"term": {"brand": "华为"}}` |
| 价格 1000-5000 | `range` | `{"range": {"price": {"gte":1000,"lte":5000}}}` |
| 品牌是华为或小米 | `terms` | `{"terms": {"brand": ["华为","小米"]}}` |
| 搜"华为手机"短语 | `match_phrase` | `{"match_phrase": {"title": "华为手机"}}` |
| 多字段搜索 | `multi_match` | `{"multi_match": {"query":"华为","fields":["title","desc"]}}` |
| 组合条件 | `bool` | must + filter + should + must_not |
| 模糊纠错 | `fuzzy` | `{"fuzzy": {"title": "huwawei"}}` |
| 前缀搜索 | `prefix` | `{"prefix": {"title": "华为"}}` |
| 判断字段是否存在 | `exists` | `{"exists": {"field": "desc"}}` |
| 按品牌分组统计 | `terms agg` | `{"terms": {"field": "brand"}}` |
| 计算平均价格 | `avg agg` | `{"avg": {"field": "price"}}` |
| 按时段统计 | `date_histogram` | `{"date_histogram": {"field":"time","calendar_interval":"day"}}` |
| 高亮匹配词 | `highlight` | `{"highlight": {"fields": {"title":{}}}}` |
| 分组取 Top N | `top_hits` | 嵌套在 terms 聚合中使用 |
| 搜附近 | `geo_distance` | `{"geo_distance": {"distance":"5km","location":...}}` |

---

## 学习路线建议

1. **第一遍**：理解核心概念（索引、文档、分片、倒排索引）
2. **第二遍**：动手写 CRUD 和简单 match 查询
3. **第三遍**：掌握 Bool 组合查询（工作中 90% 的查询都是 Bool）
4. **第四遍**：学习聚合（数据分析场景必备）
5. **第五遍**：深入 function_score、搜索流程、性能优化

> **推荐练习方式**：安装 Kibana Dev Tools，逐条复制本文档中的查询示例，亲手执行并观察结果。

---

*下一篇：[ES 面试题 30 道](./ES面试题30道.md)*
