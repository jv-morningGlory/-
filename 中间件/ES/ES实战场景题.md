# Elasticsearch 实战场景题

> 来源：牛客网大厂面经 + 阿里云/腾讯云生产实践

---

## 一、集群架构设计（共 4 题）

### 场景 1：日增 1TB 日志，保留 7 天，如何设计 ES 集群？

**第一步：容量计算**

```
实际磁盘 = 日增量 × 保留天数 × 副本数 ÷ 0.6  （预留 40% 给 Merge + 水位线）
        = 1TB × 7 × 2 ÷ 0.6
        ≈ 23.3TB
```

**第二步：分片规划**

```
单分片目标 40GB
主分片数 = 1000GB × 7 / 40GB ≈ 175 个主分片
每个节点约 20 个分片
Data 节点数 = 175 / 20 ≈ 9 台 → 取 10 台
```

**第三步：集群方案**

```
3 台 Master 节点（4C8G，跨可用区）       ← 集群管理
10 台 Data 节点（16C64G + 4TB NVMe SSD） ← 存储 + 查询
2 台 Coordinating 节点（8C16G）           ← 聚合计算隔离
```

**第四步：索引策略**

```
- 按天切索引：logs-2026.06.13
- ILM 策略：Hot(SSD,7天) → Warm(HDD,30天) → Delete
- refresh_interval=30s（日志不需实时）
- 按天 Rollover
```

---

### 场景 2：ES 集群从 3 节点扩展到 10 节点，怎么平滑迁移？

**方案**：

```
1. 新节点加入集群 → 自动被发现
2. 设置分片分配策略
   PUT /_cluster/settings
   { "transient": { "cluster.routing.allocation.total_shards_per_node": 20 } }
3. 触发 Rebalance
4. 监控迁移进度
   GET /_cat/recovery?v&active_only=true
5. 迁移完成后逐台下掉旧节点
   PUT /_cluster/settings
   { "transient": { "cluster.routing.allocation.exclude._ip": "旧节点IP" } }
```

---

### 场景 3：ES 集群 Master 节点挂了怎么办？

**自动恢复流程**：

```
1. 其他候选 Master 节点检测到 Master 心跳超时
2. 发起选举（ping 所有候选节点）
3. 获得 N/2+1 票的节点成为新 Master
4. 新 Master 读取集群元数据（或从其他节点同步）
5. 重新分配未分配的分片
6. 集群恢复 Green 状态
```

**关键**：必须配置 `minimum_master_nodes = N/2+1`（7.x 前），防止脑裂。

---

### 场景 4：数据倾斜导致某个节点快满了，怎么处理？

**排查**：

```bash
# 查看各节点分片分布
GET /_cat/allocation?v

# 查看各分片大小
GET /_cat/shards?v&h=index,shard,prirep,store,node
```

**解决方案**：

```
1. 手动移动分片
   POST /_cluster/reroute
   {
     "commands": [
       { "move": { "index": "hot-index", "shard": 0,
         "from_node": "node1", "to_node": "node3" } }
     ]
   }

2. 拆分大分片（7.x+）
   POST /hot-index/_split/split-index  # 增加主分片数

3. 调整 routing 策略
4. 长期：冷热分离架构
```

---

## 二、写入场景（共 3 题）

### 场景 5：海量数据（10 亿条）如何快速导入 ES？

**策略**：

```
1. 初始导入：关闭副本 + 调大 refresh_interval
   PUT /my_index/_settings
   { "refresh_interval": "-1", "number_of_replicas": 0 }

2. Bulk 批量写入（每批 5000-10000 条）
   POST /_bulk
   {"index": {"_index": "my_index", "_id": "1"}}
   {"field1": "value1", "field2": "value2"}
   ...

3. 多线程并行写入（每个线程写不同分片的路由 Key）

4. 写入完成后恢复设置
   PUT /my_index/_settings
   { "refresh_interval": "1s", "number_of_replicas": 1 }

5. 等待分片恢复 Green
   GET /_cat/health?v

6. Force Merge（可选，减少 Segment 数量）
   POST /my_index/_forcemerge?max_num_segments=1
```

**性能对比**：

| 优化 | 写入 TPS |
|------|----------|
| 默认配置 | ~5,000/s |
| 关闭副本 + refresh_interval=-1 | ~50,000/s |
| 多线程 Bulk | ~200,000/s |

---

### 场景 6：写入时 ES 跟不上速度，消息积压在 Kafka 怎么办？

**排查**：

```bash
# 1. 查看 ES 写入拒绝
GET /_cat/thread_pool/write?v

# 2. 查看 Translog 是否阻塞
GET /_nodes/stats/indices/translog

# 3. 磁盘 IO 是否瓶颈
iostat -x 1
```

**解决方案**：

| 措施 | 配置 |
|------|------|
| 增加 Data 节点 | 横向扩容 |
| 增加分片数 | 新索引增加 `number_of_shards` |
| 调大 Bulk 队列 | `thread_pool.write.queue_size=10000` |
| 异步 Translog | `translog.durability=async` |
| 降低刷新频率 | `refresh_interval=30s` |
| 关闭副本 | 先关闭，写入完再开启 |

---

### 场景 7：索引 Mapping 设计失误，怎么在线修改？

**Mapping 不可修改的字段属性**：
- 字段类型（text → keyword）❌
- 分词器 ❌
- `index: true/false` ❌

**可修改的**：
- 增加新字段 ✅
- `ignore_above` 参数 ✅
- 动态模板 ✅

**不得已需要改类型**：**Reindex**

```bash
# 1. 创建新索引，带正确的 Mapping
PUT /my_index_v2
{
  "mappings": {
    "properties": {
      "price": { "type": "integer" },
      "create_time": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss" }
    }
  }
}

# 2. 数据迁移
POST /_reindex
{
  "source": { "index": "my_index_v1" },
  "dest": { "index": "my_index_v2" }
}

# 3. 切换别名
POST /_aliases
{
  "actions": [
    { "remove": { "index": "my_index_v1", "alias": "my_index" } },
    { "add":    { "index": "my_index_v2", "alias": "my_index" } }
  ]
}

# 4. 删除旧索引
DELETE /my_index_v1
```

---

## 三、查询场景（共 5 题）

### 场景 8：电商商品搜索，用户输入"华为手机 5000元以内"，怎么做？

**DSL 设计**：

```json
GET /products/_search
{
  "query": {
    "bool": {
      "must": [
        { "match": { "title": "华为手机" } }
      ],
      "filter": [
        { "range": { "price": { "lte": 5000 } } },
        { "term": { "in_stock": true } },
        { "term": { "status": "on_sale" } }
      ]
    }
  },
  "sort": [
    { "_score": "desc" },
    { "sales_count": "desc" },
    { "price": "asc" }
  ],
  "highlight": {
    "fields": { "title": {} }
  }
}
```

---

### 场景 9：搜索结果要支持多条件筛选（品牌、价格区间、是否在售），怎么做？

**使用 Bool Query + Filter**：

```json
GET /products/_search
{
  "query": {
    "bool": {
      "must": { "match": { "title": "手机" } },
      "filter": [
        { "terms": { "brand": ["华为", "小米"] } },
        { "range": { "price": { "gte": 1000, "lte": 8000 } } },
        { "term": { "in_stock": true } },
        { "range": { "rating": { "gte": 4.0 } } }
      ],
      "must_not": [
        { "term": { "status": "discontinued" } }
      ]
    }
  }
}
```

**原理**：Filter 条件会产生 **BitSet** 缓存，后续相同 Filter 的查询直接复用，无需重新计算。

---

### 场景 10：千万级商品，分页 500 页深度翻页，用户投诉慢，怎么解决？

**问题根因**：`from=5000, size=10` → Coordinator 从每个分片取 5010 条，排序后取 `[5000, 5010]`。分片越多，内存花费越大。

**方案一：search_after（推荐）**

```json
// 第一页
GET /products/_search
{
  "size": 10,
  "query": { "match": { "title": "手机" } },
  "sort": [{ "price": "asc" }, { "_id": "asc" }]
}

// 第二页（用上一页最后一条的 sort 值）
GET /products/_search
{
  "size": 10,
  "query": { "match": { "title": "手机" } },
  "sort": [{ "price": "asc" }, { "_id": "asc" }],
  "search_after": [4999, "product_12345"]
}
```

**方案二：限制翻页深度**

```json
PUT /products/_settings
{ "index.max_result_window": 10000 }
```

> **面试要说**：search_after 利用排序值定位下一页，避免了 from 带来的跨分片开销，适合深度翻页和无限滚动。

---

### 场景 11：聚合计算太慢，cardinality 上亿去重要几十秒，怎么优化？

**原因**：ES 的 `terms` 聚合精确去重需要在每个分片计算后合并，大数据量下极慢。

**优化方案**：

```json
// 1. 使用 cardinality（近似去重，基于 HyperLogLog++）
GET /logs/_search
{
  "size": 0,
  "aggs": {
    "unique_users": {
      "cardinality": {
        "field": "user_id",
        "precision_threshold": 40000  // 40000 以内精确，超出近似
      }
    }
  }
}
```

```
2. 在索引阶段预聚合（Rollup）
3. 用时间分桶减少单次计算量
4. 高基数字段避免精确去重
```

---

### 场景 12：用户搜索 "苹果"，需要返回水果和手机两种结果，如何提升搜索体验？

**问题**：query "苹果" 同时匹配水果和手机，默认 BM25 打分可能导致一种类型排名过低。

**方案一：Function Score 加权**

```json
GET /products/_search
{
  "query": {
    "function_score": {
      "query": { "match": { "title": "苹果" } },
      "functions": [
        { "filter": { "term": { "category": "手机" } }, "weight": 2 },
        { "filter": { "term": { "category": "水果" } }, "weight": 0.5 }
      ],
      "score_mode": "multiply"
    }
  }
}
```

**方案二：Multi-Field 搜索**

```json
{
  "query": {
    "multi_match": {
      "query": "苹果",
      "fields": ["title^3", "category^1", "brand^2"]
    }
  }
}
```

**方案三：同义词处理**

```json
PUT /products/_settings
{
  "analysis": {
    "filter": {
      "my_synonym": {
        "type": "synonym",
        "synonyms": ["苹果手机, iphone, 苹果"]
      }
    }
  }
}
```

---

## 四、数据场景（共 4 题）

### 场景 13：如何设计一个日志分析平台（ELK）？

**架构**：

```
┌──────────┐   ┌──────┐   ┌──────────┐   ┌──────┐   ┌───────┐
│Filebeat   │→  │Kafka  │→  │Logstash   │→  │  ES  │→  │Kibana │
│(采集)     │   │(缓冲) │   │(解析/Grok)│   │(存储)│   │(可视化)│
└──────────┘   └──────┘   └──────────┘   └──────┘   └───────┘
```

**各组件职责**：

| 组件 | 职责 | 关键配置 |
|------|------|----------|
| **Filebeat** | 采集日志文件，轻量级 Agent | 不解析，直接转发 |
| **Kafka** | 缓冲削峰，避免 ES 写入压力过大 | 保留 3 天 |
| **Logstash** | 解析日志（Grok），格式化，转义字段 | pipeline 并发 |
| **ES** | 存储 + 搜索 | 按天建索引，ILM 管理 |
| **Kibana** | 可视化 Dashboard + 查询 | 线图、饼图、聚合 |

**索引设计**：

```json
PUT /_index_template/logs_template
{
  "index_patterns": ["logs-*"],
  "template": {
    "settings": { "number_of_shards": 5, "refresh_interval": "30s" },
    "mappings": {
      "properties": {
        "timestamp": { "type": "date" },
        "level":     { "type": "keyword" },
        "service":   { "type": "keyword" },
        "message":   { "type": "text" },
        "trace_id":  { "type": "keyword" },
        "host_ip":   { "type": "ip" }
      }
    }
  }
}
```

---

### 场景 14：MySQL 数据需要实时同步到 ES，怎么设计？

**Canal + Kafka 方案**：

```
MySQL → Canal(伪装Slave,解析Binlog) → Kafka → ES Consumer → ES
```

**实现要点**：

```java
// 1. Canal 配置监听 MySQL 变更
// 2. Kafka Consumer 写入 ES
public void consume(ConsumerRecord<String, String> record) {
    BinlogEvent event = JSON.parseObject(record.value(), BinlogEvent.class);
    String indexName = event.getDatabase() + "_" + event.getTable();
    String docId = event.getAfter().get("id").toString();

    switch (event.getType()) {
        case "INSERT":
        case "UPDATE":
            esClient.index(indexName, docId, event.getAfter());
            break;
        case "DELETE":
            esClient.delete(indexName, docId);
            break;
    }
}
```

**一致性保障**：

```
1. 同一行的变更发送到 Kafka 同一 Partition（保证顺序）
2. ES 用 _id 写入（幂等覆盖）
3. 定时全量对账任务（凌晨低峰期）
4. 消费 Offset 记录到 DB（失败可按 Offset 回放）
```

---

### 场景 15：如何用 ES 实现附近的人/地理位置搜索？

**Mapping**：

```json
PUT /users
{
  "mappings": {
    "properties": {
      "name": { "type": "text" },
      "location": { "type": "geo_point" }
    }
  }
}
```

**写入**：

```json
POST /users/_doc/1
{
  "name": "张三",
  "location": { "lat": 31.2304, "lon": 121.4737 }
}
```

**搜索附近**：

```json
GET /users/_search
{
  "query": {
    "bool": {
      "must": { "match_all": {} },
      "filter": {
        "geo_distance": {
          "distance": "5km",
          "location": { "lat": 31.2304, "lon": 121.4737 }
        }
      }
    }
  },
  "sort": [
    {
      "_geo_distance": {
        "location": { "lat": 31.2304, "lon": 121.4737 },
        "order": "asc",
        "unit": "m"
      }
    }
  ]
}
```

---

### 场景 16：如何用 ES 实现自动补全（Suggestion）？

**Mapping**：

```json
PUT /search_suggest
{
  "mappings": {
    "properties": {
      "keyword": {
        "type": "completion"  // Completion Suggester 专用类型
      }
    }
  }
}
```

**写入**：

```json
POST /search_suggest/_doc/1
{
  "keyword": {
    "input": ["huawei", "华为"],
    "weight": 100
  }
}
```

**查询**：

```json
GET /search_suggest/_search
{
  "suggest": {
    "keyword-suggest": {
      "prefix": "hua",
      "completion": { "field": "keyword", "size": 5 }
    }
  }
}
```

> Completion Suggester 基于 FST 前缀匹配，速度极快（远快于 match）。

---

## 五、故障排查（共 4 题）

### 场景 17：线上 ES 突然变红（Red），怎么应急？

**红色 = 至少一个主分片未分配**，意味着数据不完整。

**排查步骤**：

```bash
# 1. 确认集群状态
GET /_cluster/health
# { "status": "red", "unassigned_shards": 5 }

# 2. 查看未分配分片原因
GET /_cat/shards?v&h=index,shard,prirep,state,unassigned.reason

# 3. 常见原因及解决
```

| 原因 | 解决 |
|------|------|
| 节点宕机 | 恢复节点，自动重新分配 |
| 磁盘满（`disk watermark`） | 清理磁盘，或临时调高 `cluster.routing.allocation.disk.watermark.high=95%` |
| 分片分配冲突 | 手动 reroute 或重启 Master |
| 副本不足 | 减少副本数 |

```bash
# 临时强制分配（数据可能丢失，慎用）
POST /_cluster/reroute?retry_failed=true
```

---

### 场景 18：ES 查询突然变慢，怎么排查？

```bash
# 1. 查看慢查询日志
GET /_cluster/settings
{
  "transient": {
    "index.search.slowlog.threshold.query.warn": "1s",
    "index.search.slowlog.threshold.query.info": "500ms"
  }
}

# 2. 开启 Profile 分析查询
GET /products/_search
{
  "profile": true,
  "query": { "match": { "title": "手机" } }
}

# 3. 查看节点 GC
GET /_nodes/stats/jvm

# 4. 查看热点线程
GET /_nodes/hot_threads

# 5. 查看 Segment 数量
GET /_cat/segments?v
```

**常见原因**：

| 现象 | 原因 | 解决 |
|------|------|------|
| GC 频繁 | 堆内存不够 | 扩容或调大堆 |
| Segment 过多 | 未合并 | Force Merge |
| 深度分页 | from 太大 | search_after |
| 通配符查询 | `*xx` 前缀匹配 | 禁用或限制 |

---

### 场景 19：ES 节点频繁 Full GC，怎么处理？

**排查**：

```bash
# 确认 GC 频率
GET /_nodes/stats/jvm
# 查看 heap 使用率
GET /_cat/nodes?v&h=name,heap.percent,ram.percent
```

**解决方案**：

```
1. 堆内存设 31GB（不超过 32GB）
2. 检查是否加载了 text 字段的 fielddata（极耗内存）
   → 用 keyword 聚合替代
3. 聚合 size 设太大
   → 限制聚合 size
4. 太多 Segment
   → Force Merge
5. 扩容 Data 节点，分散分片
```

---

### 场景 20：线上如何安全重启 ES 节点？

```bash
# 1. 停止分片分配到该节点
PUT /_cluster/settings
{
  "transient": { "cluster.routing.allocation.exclude._name": "node-1" }
}

# 2. 等待分片迁移完毕
GET /_cat/shards?v | grep node-1

# 3. 停进程
kill -15 <pid>

# 4. 重启后恢复
PUT /_cluster/settings
{
  "transient": { "cluster.routing.allocation.exclude._name": "" }
}

# 5. 等集群恢复 Green
GET /_cat/health?v
```

---

## 📊 场景速查表

| 场景 | 关键词 | 核心方案 |
|------|--------|----------|
| 日增 1TB 日志 | 集群规划 | 容量计算 + 分片规划 + 冷热分离 + ILM |
| 10 亿数据导入 | 快速写入 | 关副本 + 关 Refresh + Bulk + 多线程 |
| 深度翻页慢 | 分页 500 页 | search_after 替代 from/size |
| 聚合去重慢 | 亿级去重 | cardinality（HLL 近似） |
| 搜索"苹果" | 歧义召回 | Function Score + 同义词 |
| 日志平台 | ELK | Filebeat → Kafka → Logstash → ES → Kibana |
| MySQL 同步 ES | CDC | Canal + Kafka + 幂等写入 |
| 附近的人 | 地理位置 | geo_point + geo_distance |
| 自动补全 | Suggestion | Completion Suggester（FST） |
| 集群 Red | 应急 | 定位未分配分片 → 磁盘/节点排查 |
| 查询变慢 | 性能瓶颈 | Profile + 慢日志 + GC 排查 |
| 节点重启 | 安全运维 | 先停分片分配 → 迁移 → 停机 |

---

*来源：牛客网大厂面经 + 阿里云/腾讯云/百度生产实践*
