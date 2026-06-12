# Kafka 高频面试题 30 道

> 来源：牛客网京东、网易、字节、腾讯、美团等大厂面经汇总

---

## 一、基础概念（第 1-10 题）

### 1. Kafka 是什么？核心组件有哪些？

Apache Kafka 是一个**分布式发布订阅消息系统**，也是一个**流处理平台**。它最初由 LinkedIn 开发，2011 年开源并捐赠给 Apache 基金会。

**核心组件：**

| 组件 | 作用 |
|------|------|
| **Producer** | 消息生产者，向 Topic 推送数据 |
| **Broker** | Kafka 服务节点，负责存储和转发消息 |
| **Topic** | 消息的逻辑分类，类似数据库的表 |
| **Partition** | Topic 的物理分片，每个分区内消息有序 |
| **Consumer** | 消费者，从 Topic 拉取数据 |
| **Consumer Group** | 消费者组，组内共享消费 Topic 的数据 |
| **Zookeeper** | 集群元数据管理、Controller 选举（新版逐步移除） |

---

### 2. Kafka 的架构是什么样的？

```
              ┌─────────┐     ┌─────────┐     ┌─────────┐
              │Producer 1│     │Producer 2│     │Producer 3│
              └────┬─────┘     └────┬─────┘     └────┬─────┘
                   │                │                │
                   ▼                ▼                ▼
              ┌─────────────────────────────────────────┐
              │            Kafka  Cluster              │
              │  ┌─────────┐  ┌─────────┐  ┌────────┐ │
              │  │Broker 1  │  │Broker 2  │  │Broker 3 │ │
              │  │P0(Lead)  │  │P1(Lead)  │  │P2(Lead) │ │
              │  │P1(Foll)  │  │P2(Foll)  │  │P0(Foll)  │ │
              │  └─────────┘  └─────────┘  └────────┘ │
              └─────────────────────────────────────────┘
                   │                │                │
                   ▼                ▼                ▼
              ┌─────────┐     ┌─────────┐     ┌─────────┐
              │Consumer 1│     │Consumer 2│     │Consumer 3│
              │  Group A │     │  Group A │     │  Group B │
              └─────────┘     └─────────┘     └─────────┘
```

**架构特点**：分布式、多副本、水平扩展、Leader-Follower 模型。

---

### 3. Kafka 相比其他消息队列有什么优势？

| 特性 | Kafka | RabbitMQ | RocketMQ |
|------|-------|----------|----------|
| **吞吐量** | 百万级/秒（极高） | 万级/秒 | 十万级/秒 |
| **消息持久化** | 日志文件持久化，数据可长期保留 | 内存+磁盘 | CommitLog 持久化 |
| **水平扩展** | 天然支持（分区机制） | 一般 | 较好 |
| **消息有序** | 分区内有序 | 全局有序（单线程） | 分区内有序 |
| **事务支持** | 0.11+ 支持 | 不支持 | 支持 |
| **流处理** | 原生 Kafka Streams | 无 | 无 |
| **适用场景** | 大数据、日志、流处理 | 业务消息队列 | 业务消息、分布式事务 |

---

### 4. Kafka 为什么这么快？（高吞吐原理）⭐ 必问

这是面试中**最常见的题目**，回答要覆盖以下六个机制：

**（1）顺序写磁盘**

Kafka 采用追加写（Append Only），消息不断追加到日志文件末尾，避免了磁头寻道的随机 IO 开销。顺序写的速度可以达到 **600MB/s**，而随机写仅约 100KB/s。

**（2）Page Cache（页缓存）**

Kafka 不自己管理缓存，而是利用 OS 的 Page Cache。写入时数据先进入 Page Cache，由 OS 决定何时刷盘。读取时如果数据在 Page Cache 中，直接从内存返回，零磁盘 IO。

**（3）零拷贝（Zero Copy）**

传统数据传输路径：
```
磁盘 → 内核缓冲区 → 用户缓冲区 → Socket缓冲区 → 网卡
```

Kafka 使用 `sendfile` 系统调用 + DMA 技术：
```
磁盘 → 内核缓冲区 → Socket缓冲区 → 网卡（DMA直接传输）
```
减少了 2 次内核态-用户态的上下文切换和 2 次数据拷贝。

**（4）分区并行**

每个 Topic 有多个 Partition，不同 Partition 可以分布在不同的 Broker 上，实现并行读写。

**（5）批量发送**

Producer 支持批量发送，Consumer 支持批量拉取，减少网络往返次数。

**（6）数据压缩**

支持 GZip、Snappy、LZ4、Zstd 等压缩算法，减少网络传输和存储开销。

---

### 5. Kafka 是 Push 还是 Pull 模式？为什么？

Kafka 遵循：**Producer Push → Broker，Consumer Pull ← Broker**。

**消费者选择 Pull 模式的原因：**

| 对比维度 | Pull（拉） | Push（推） |
|----------|-----------|-----------|
| 消费速率 | 消费者自己控制 | Broker 控制 |
| 积压处理 | 消费者处理慢时暂不拉取 | 可能压垮消费者 |
| 实现复杂度 | 消费者需轮询 | Broker 需维护推送状态 |

Pull 模式的核心优势是**消费者可以按自身处理能力消费**，不会因为 Broker 推送过快导致消费者 OOM。

---

### 6. Partition 是什么？有什么作用？

Partition 是 Topic 的**物理分片单位**，以日志文件（Segment）的形式存储在磁盘上。

**作用：**

- **并行处理**：多个 Partition 可被多个 Consumer 并行消费
- **水平扩展**：通过增加 Partition 数提升吞吐量
- **顺序保证**：同一 Partition 内消息严格有序
- **负载均衡**：不同 Partition 可分布在不同 Broker 上

**存储结构：**

```
Topic（逻辑） → Partition（物理）
                      ├── 000000.log       # 日志文件
                      ├── 000000.index     # 稀疏索引
                      └── 000000.timeindex # 时间索引
```

---

### 7. Consumer Group 是什么？消费机制是怎样的？

Consumer Group 是 Kafka 实现**消息广播和竞争消费**的核心机制：

- **不同 Consumer Group**：一个 Topic 的消息会广播给所有 Group（发布订阅模式）
- **同一 Consumer Group**：一个 Partition 只能被组内的一个 Consumer 消费（队列模式）
- **消费者数 > Partition 数**：多余的消费者会闲置，不会参与消费

```
示例：Topic 有 3 个 Partition (P0, P1, P2)

Group A 有 2 个消费者：
  Consumer A1 → P0, P1
  Consumer A2 → P2

Group B 有 1 个消费者：
  Consumer B1 → P0, P1, P2
```

---

### 8. 生产者消费者模式 vs 发布订阅模式在 Kafka 中如何体现？

Kafka **同时支持**两种模式：

| 模式 | 实现方式 | 特点 |
|------|---------|------|
| **队列模式**（点对点） | 所有消费者放在**同一个 Consumer Group** | Partition 被组内消费者竞争消费 |
| **发布订阅**（广播） | 每个消费者放在**不同的 Consumer Group** | 消息被每个 Group 独立消费一次 |

只需调整 Consumer Group 的配置就可以在两种模式间切换，不需要改动 Producer。

---

### 9. Kafka 的消息格式是什么样的？

Kafka 的消息由一个**变长 Header + Body**组成（V2 版本）：

```
┌────────────────────────────────────────────┐
│  Offset（8 bytes）                          │ ← 消息偏移量
│  Length（4 bytes）                           │ ← 消息长度
│  CRC（4 bytes）                              │ ← 校验码
│  Magic（1 byte）                             │ ← 版本号
│  Attributes（1 byte）                        │ ← 压缩类型、时间戳类型
│  Timestamp（8 bytes）                        │ ← 时间戳
│  Key Length（4 bytes）                       │
│  Key（变长）                                 │
│  Value Length（4 bytes）                     │
│  Value（变长）                               │ ← 实际消息体
│  Headers（变长）                              │ ← 自定义 Header
└────────────────────────────────────────────┘
```

V2 版本相比 V1 的主要改进：支持**消息批次（Record Batch）**，多条消息共享同一个 Header，进一步压缩和提升吞吐量。

---

### 10. Zookeeper 在 Kafka 中的作用？可以不用吗？

**早期版本（< 3.0）依赖 ZK 的职责：**

| 职责 | 说明 |
|------|------|
| **Broker 注册** | 在 ZK 创建临时节点注册 Broker，Broker 宕机节点自动删除 |
| **Controller 选举** | 第一个 Broker 在 ZK 创建 `/controller` 临时节点成为 Controller |
| **元数据存储** | 存储 Topic、Partition、ISR 等信息 |
| **消费者位移**（旧版） | 0.9 前消费者的 Offset 存在 ZK，之后移到内部 Topic `__consumer_offsets` |

**Kafka 3.0+ KRaft 模式**：

- 引入了**KRaft 共识协议**替代 ZK
- 元数据存于 KRaft 内部的元数据日志
- 架构更简洁，运维成本更低，支持更大规模集群
- 生产环境逐步从 ZK 模式向 KRaft 迁移

---

## 二、核心机制（第 11-20 题）

### 11. ISR 机制是什么？ISR、OSR、AR 的区别？⭐ 必问

在 Kafka 中，每个 Partition 有多个副本（Replica），其中一个为 **Leader**，其余为 **Follower**。

| 术语 | 全称 | 含义 |
|------|------|------|
| **AR** | Assigned Replicas | 该 Partition 的**所有副本**（Leader + Follower） |
| **ISR** | In-Sync Replicas | 与 Leader **保持同步**的副本集合 |
| **OSR** | Out-of-Sync Replicas | **滞后于 Leader** 的副本集合 |

**ISR 管理机制：**

- Follower 与 Leader 的延迟超过 `replica.lag.time.max.ms`（默认 30s）会被踢出 ISR 进入 OSR
- 当 Follower 追上 Leader 后，重新加入 ISR
- 只有 ISR 中的副本可以参与 Leader 选举

> 公式：**AR = ISR + OSR**

---

### 12. ACK 应答机制有哪几种？分别代表什么？⭐ 必问

Producer 通过 `acks` 参数控制消息的可靠性级别：

| ACK 值 | 行为 | 可靠性 | 延迟 | 适用场景 |
|--------|------|--------|------|----------|
| **0** | 不等待任何确认，直接发送 | 最低（可能丢失） | 最低 | 日志采集等允许丢的场景 |
| **1** | Leader 写入成功即确认（**默认**） | 中等 | 中等 | 一般业务场景 |
| **-1 / all** | Leader + 所有 ISR 副本写入成功才确认 | 最高 | 最高 | 金融、订单等核心场景 |

**ack=all 的注意事项：**

- 需配合 `min.insync.replicas`（最小同步副本数，建议 ≥ 2）
- 如果 ISR 数量小于 `min.insync.replicas`，Producer 会收到异常
- 需要 `unclean.leader.election.enable=false` 避免从 OSR 选 Leader

---

### 13. Kafka 如何保证数据不丢失？⭐ 必问

需要从**三个维度**回答：

**（1）Producer 端（生产不丢）：**

```
acks=all                              # 所有 ISR 确认
enable.idempotence=true               # 开启幂等性，避免网络重试导致重复
retries=Integer.MAX_VALUE             # 最大重试（新版默认）
max.in.flight.requests.per.connection=5  # 幂等开启时可 > 1
```

**（2）Broker 端（存储不丢）：**

```
replication.factor=3                  # 副本因子 ≥ 3
min.insync.replicas=2                 # 最小同步副本数 ≥ 2
unclean.leader.election.enable=false  # 禁止选举 OSR 中的副本为新 Leader
```

**（3）Consumer 端（消费不丢）：**

```
enable.auto.commit=false              # 关闭自动提交 offset
// 处理完消息后手动提交
consumer.commitSync();                # 同步提交，确保成功
```

---

### 14. Kafka 如何保证数据不重复（幂等性）？

**生产者幂等（Producer Idempotence）**：

开启 `enable.idempotence=true` 后，每个 Producer 被分配一个唯一的 **PID**，每条消息带上**序列号（SeqNum）**。Broker 通过 `(PID, Topic, Partition, SeqNum)` 做去重：

- 如果 SeqNum 比 Broker 记录的**大 1** → 正常写入
- 如果 SeqNum 比 Broker 记录的**小或相等** → 认为是重复，丢弃
- 如果 SeqNum 比 Broker 记录的**大超过 1** → 数据丢失，抛出异常

**消费者幂等**：

- 业务层面做幂等设计（如数据库唯一键、Redis 去重）
- 利用 `offset` 和状态存储判断是否已处理

---

### 15. Exactly Once 语义怎么实现？⭐

**Exactly Once = 幂等生产者 + 事务**。

```
enable.idempotence=true               # 开启幂等
transactional.id="my-txn-id"          # 设置事务 ID
isolation.level=read_committed        # 消费者只读已提交的消息
```

事务流程：

```java
// 1. 初始化事务
producer.initTransactions();

// 2. 开始事务
producer.beginTransaction();

// 3. 发送消息
producer.send(new ProducerRecord<>("topic1", "key", "value1"));
producer.send(new ProducerRecord<>("topic2", "key", "value2"));

// 4. 提交事务
producer.commitTransaction();
// 或回滚：producer.abortTransaction();
```

**Kafka 事务实现原理**：

- 事务信息通过内部 Topic `__transaction_state` 持久化
- Consumer 设置 `isolation.level=read_committed` 会跳过未提交的事务消息
- 跨分区的原子写入通过两阶段提交保证

---

### 16. Leader 挂掉后怎么选举新 Leader？

**选举流程**：

1. Controller 监听到 Leader 宕机（通过 ZK 或 KRaft）
2. Controller 从该 Partition 的 **AR（所有副本）** 中选择**第一个在 ISR 中的副本**
3. 更新所有 Broker 的元数据，通知新的 Leader 信息
4. Follower 开始从新 Leader 同步数据

**四种选举触发场景**：

| 场景 | 触发条件 |
|------|---------|
| OfflinePartition | 分区上线时选举 |
| ReassignPartition | 副本重新分配 |
| PreferredReplica | 手动触发优先副本选举 |
| ControlledShutdown | Broker 正常关闭时迁移 |

**关键参数**：

```
unclean.leader.election.enable=false  # 禁止从 OSR 选 Leader，避免数据丢失
```

---

### 17. Controller 选举机制

Controller 是 Kafka 集群的**管理者**，负责：

- Partition Leader 选举
- 监听 Broker 上下线
- 分区副本分配
- 元数据变更通知

**选举方式**：集群中**第一个启动的 Broker** 在 Zookeeper 中创建**临时节点** `/controller`，成为 Controller。如果 Controller 宕机，临时节点被删除，其他 Broker 监听到变化后**争抢创建**该节点，先创建成功的成为新 Controller。

> KRaft 模式下，Controller 通过 QUORUM 选举，不再依赖 ZK。

---

### 18. Rebalance 是什么？什么时候触发？

Rebalance 是 Kafka 重新分配 Consumer Group 内分区与消费者之间映射关系的过程。

**触发条件**：

1. **消费者加入**：新的 Consumer 加入 Consumer Group
2. **消费者离开**：Consumer 主动退出或心跳超时
3. **Topic 变化**：订阅的 Topic 增加了 Partition

**Rebalance 带来的问题**：

- 期间消费者暂停消费，STW（Stop The World）
- 频繁 Rebalance 会导致消息积压

**避免频繁 Rebalance 的建议**：

```
session.timeout.ms=30000               # 心跳超时（不要太小）
max.poll.interval.ms=300000            # 两次 poll 最大间隔
max.poll.records=500                   # 每次拉取的消息数（不要太大）
```

---

### 19. 消费者提交 Offset 的方式有哪些？

| 方式 | 说明 | 优点 | 缺点 |
|------|------|------|------|
| **自动提交** | `enable.auto.commit=true`，定时自动提交 | 简单 | 可能丢消息 |
| **手动同步提交** | `consumer.commitSync()` | 可靠，保证提交成功 | 阻塞，影响吞吐 |
| **手动异步提交** | `consumer.commitAsync()` | 不阻塞 | 提交失败无感知 |
| **混合提交** | 正常用异步，关闭前用同步 | 兼顾吞吐和可靠性 | 稍复杂 |

**最佳实践**：

```java
try {
    while (true) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
        // 处理消息
        processRecords(records);
        // 异步提交
        consumer.commitAsync();
    }
} catch (Exception e) {
    // 异常处理
} finally {
    // 关闭前同步提交
    consumer.commitSync();
}
```

---

### 20. Kafka 的分区策略有哪些？

Producer 发送消息时，按以下优先级确定消息发往哪个 Partition：

**优先级（从高到低）：**

1. **指定 Partition**：代码中显式指定 Partition 号
2. **有 Key**：`hash(key) % partitionCount` 取模
3. **无 Key**（新版）：**Sticky Partitioner**（粘性分区），将一批消息发往同一个 Partition，下一批可能换分区
4. **无 Key**（旧版）：轮询（Round Robin）

**自定义分区器**：实现 `org.apache.kafka.clients.producer.Partitioner` 接口。

---

## 三、进阶与场景题（第 21-30 题）

### 21. Kafka 如何保证消息全局有序？

Kafka **天然保证分区内有序**，但**不保证跨分区的全局有序**。

**实现全局有序的方案**：

| 方案 | 说明 | 适用场景 |
|------|------|---------|
| **单分区** | 整个 Topic 只用 1 个 Partition | 数据量小、对吞吐要求不高 |
| **Key 设计** | 相同业务 Key（如订单 ID）Hash 到同一分区 | 单实体内有序，跨实体可不有序 |
| **消费者端排序** | Consumer 读取所有分区后按时间戳排序 | 允许一定延迟的场景 |

---

### 22. Kafka 如何实现延迟队列？

Kafka 本身没有内置延迟队列功能，可以通过以下方式实现：

**方案一：Topic 分层 + 定时任务**

```
订单创建 → Topic1（实时队列）
           ↓
        消费者写入 Redis（Key=订单ID, Score=执行时间）
           ↓
        定时任务扫描 Redis → Topic2（延迟后的队列）
           ↓
        消费者处理
```

**方案二：时间轮（Timing Wheel）**

参考 Kafka 内部的**时间轮**实现（`org.apache.kafka.server.util.timer`），也可以用它来构建延迟队列。

**方案三：RocketMQ 方案**

如果延迟消息是核心需求，可以考虑直接使用 RocketMQ，它原生支持 18 个延迟级别。

---

### 23. Kafka 为什么不支持读写分离？

| 原因 | 说明 |
|------|------|
| **数据一致性** | 主从之间通过异步复制，存在延迟窗口，从副本数据可能落后 |
| **延时问题** | Kafka 数据链路更长（网络→内存→磁盘→网络→内存→磁盘），再走 Follower 延时更高 |
| **Partition 已做负载均衡** | 不同 Partition 的 Leader 分布在不同的 Broker 上，天然实现了读写的分散 |
| **运维复杂度** | 读写分离增加系统复杂度，故障排查困难 |

---

### 24. Kafka 中事务是怎么实现的？

Kafka 从 **0.11 版本**开始支持事务，实现跨 Topic 和 Partition 的原子写入。

**核心组件**：

| 组件 | 作用 |
|------|------|
| **Transaction Coordinator** | 事务协调者，管理事务状态 |
| **Transaction Log** | 内部 Topic `__transaction_state`，持久化事务提交/中止状态 |
| **Transaction ID** | 全局唯一 ID，用于标识事务生产者，支持跨会话恢复 |
| **Producer ID + Epoch** | 生产者标识和纪元，用于幂等和一致性 |

**事务流程**：

1. Producer 向 Transaction Coordinator 注册事务
2. Producer 发送消息到各 Partition
3. Producer 提交事务 → Coordinator 写 PREPARE_COMMIT 到 Transaction Log
4. Coordinator 通知各 Partition 标记事务状态（COMMIT 或 ABORT）
5. Consumer 的 `isolation.level=read_committed` 决定了是否看到未提交的消息

---

### 25. Kafka 的日志清理策略有哪些？

| 策略 | 参数 | 说明 |
|------|------|------|
| **按时间删除** | `log.retention.hours=168`（7天） | 超过指定时间的日志被删除 |
| **按大小删除** | `log.retention.bytes` | 日志总大小超过阈值时删除旧数据 |
| **日志压缩** | `log.cleanup.policy=compact` | 保留每个 Key 的最新 Value，旧的被压缩掉 |

**日志压缩（Log Compaction）**的典型应用场景：

- 数据库 CDC（Change Data Capture）
- 键值存储快照
- 用户状态变更流（不关心历史，只关注最新状态）

---

### 26. 如何提升 Kafka 吞吐量？

**Producer 端优化**：

```properties
batch.size=16384            # 增大批量大小（默认 16KB）
linger.ms=10                # 等待更多消息组成一个 Batch
buffer.memory=33554432      # 发送缓冲区（默认 32MB）
compression.type=lz4        # 开启压缩（推荐 LZ4 或 Zstd）
max.in.flight.requests.per.connection=5  # 允许更多发送中的请求
```

**Broker 端优化**：

```properties
num.partitions=12           # 适当增加分区数
num.network.threads=8       # 网络线程数
num.io.threads=16           # IO 线程数
log.flush.interval.messages=10000  # 刷盘间隔（减少刷盘频率）
```

**Consumer 端优化**：

```properties
fetch.min.bytes=1024         # 至少拉取 1KB 再返回
fetch.max.wait.ms=500        # 最多等待 500ms
max.poll.records=500         # 每次拉取最大条数
```

---

### 27. 消息积压了怎么办？⭐

消息积压是生产中最常见的问题之一，排查顺序：

**第一步：定位瓶颈在哪一层**

- Producer 端：写入慢？→ 扩容 Producer 或增加分区
- Broker 端：磁盘 IO 满？→ 扩容 Broker、增加分区
- Consumer 端：消费慢？→ 见下

**第二步：Consumer 端优化**

```properties
# 增加消费者实例（消费者数 = 分区数）
# 提高单次拉取量
max.poll.records=1000       # 增大每次拉取条数
fetch.min.bytes=1048576     # 1MB

# 优化消费逻辑
# - 异步处理、批量入库
# - 减少外部调用（Redis、数据库）
# - 开启多线程并行处理
```

**第三步：紧急处理（临时）**

1. 新建一个临时 Topic，增加更多 Partition
2. 启动临时 Consumer 消费原 Topic → 写入临时 Topic
3. 用更多的 Consumer 从临时 Topic 消费 → 快速消化积压
4. 消费完成后切回流

---

### 28. 如何保证 Kafka 消息的可靠性投递？

这是面试中的**综合性题目**，需要从全链路回答：

```
┌──────────────┬──────────────────┬─────────────────────┐
│   Producer   │     Broker       │      Consumer       │
├──────────────┼──────────────────┼─────────────────────┤
│ ack=all      │ 副本数 ≥ 3        │ 手动提交 offset      │
│ 幂等开启      │ min.isr ≥ 2      │ 消费幂等处理         │
│ 无限重试      │ un.leader=false  │ 死信队列兜底         │
│ 事务保证      │ 持久化不丢        │ 异常重试机制         │
└──────────────┴──────────────────┴─────────────────────┘
```

**监控告警**：

- 消费延迟（Consumer Lag）监控
- 死信队列（Dead Letter Queue）兜底
- 端到端消息完整性校验（消息 ID + 对账）

---

### 29. Kafka 怎么压测？

使用 Kafka 官方自带的压测脚本：

**生产者压测**：

```bash
kafka-producer-perf-test.sh \
  --topic test-topic \
  --num-records 10000000 \
  --record-size 1024 \
  --throughput -1 \
  --producer-props bootstrap.servers=localhost:9092 acks=1
```

**消费者压测**：

```bash
kafka-consumer-perf-test.sh \
  --topic test-topic \
  --messages 10000000 \
  --bootstrap-server localhost:9092
```

**关注指标**：

| 指标 | 含义 |
|------|------|
| **records/sec** | 每秒发送/消费消息数 |
| **MB/sec** | 每秒吞吐量（MB） |
| **avg latency** | 平均延迟 |
| **99th latency** | P99 延迟 |

---

### 30. Kafka vs RocketMQ，如何选择？

| 对比维度 | Kafka | RocketMQ |
|----------|-------|----------|
| **设计定位** | 日志收集、流处理、大数据 | 业务消息中间件 |
| **消息可靠性** | 高（ack=all + 多副本） | 很高（同步刷盘 + 多副本） |
| **事务消息** | 0.11+ 支持 | 原生支持（分布式事务） |
| **延迟消息** | 需自行实现 | 原生支持 18 个延迟级别 |
| **顺序消息** | 分区内有序 | 支持全局有序 |
| **社区生态** | Apache + Confluent，非常活跃 | 阿里开源，国内活跃 |
| **运维成本** | 较高（需关注 ISR、Rebalance） | 中等 |

**选型建议**：

- **日志/埋点/流计算** → Kafka
- **业务解耦/分布式事务/电商订单** → RocketMQ
- **公司技术栈倾向** → 外企多用 Kafka，国内大厂两者都有

---

## 📊 高频统计

根据牛客网面经汇总，**最容易被问到的 TOP 5**：

| 排名 | 题目 | 频次 |
|------|------|------|
| **1** | Kafka 为什么快（高吞吐原因） | ⭐⭐⭐⭐⭐ |
| **2** | 如何保证数据不丢失 + 不重复 | ⭐⭐⭐⭐⭐ |
| **3** | ISR 机制 + ACK 机制 | ⭐⭐⭐⭐ |
| **4** | 消费者组 + Rebalance 原理 | ⭐⭐⭐⭐ |
| **5** | Exactly Once 语义实现 | ⭐⭐⭐ |

> **大数据开发岗**会结合 Flink/Spark 问消费位点管理和 Checkpoint 机制
> **Java 后端岗**更侧重消息可靠性、幂等性和事务消息

---

*来源：牛客网京东、网易、字节、腾讯、美团等大厂面经汇总*
