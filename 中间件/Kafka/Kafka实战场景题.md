# Kafka 实战场景题

> 来源：牛客网大厂面经 + 阿里云/腾讯云生产实践

---

## 一、消息积压（共 5 题）

### 场景 1：线上告警，Consumer Lag 持续增长，怎么排查和解决？

**排查链路：**

```
1. 确认积压量级
   → kafka-consumer-groups.sh --describe 查看 LAG

2. 定位瓶颈层
   → Producer 写入正常？Broker 磁盘 IO 正常？Consumer 消费慢？

3. Consumer 端排查
   → 单条消息处理耗时多少？
   → 是否频繁 Rebalance？
   → 下游依赖（DB/Redis）是否慢？
   → 消费线程是否卡死？
```

**解决方案（逐级递进）：**

| 优先级 | 方案 | 操作 |
|--------|------|------|
| **P0** | 增加消费者实例 | 消费者数 = Partition 数，先扩容到上限 |
| **P0** | 优化消费逻辑 | 减少单条处理时间：批量入库、异步化、减少外部调用 |
| **P1** | 增加 Partition | 注意：增加 Partition 后 Key Hash 会变，有状态 consumer 要谨慎 |
| **P1** | 调整消费参数 | `max.poll.records` 调到 500-1000，`fetch.min.bytes` 调到 1MB |
| **P2** | 紧急临时方案 | 写分发程序，将积压消息写到一个新 Topic（更多 Partition），用多台机器并行消费 |

**避免措施：**

```java
// 消费代码模板
while (running) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
    // 多线程并行处理
    List<Future<?>> futures = new ArrayList<>();
    for (ConsumerRecord<String, String> record : records) {
        futures.add(executor.submit(() -> processRecord(record)));
    }
    // 等待本批次全部处理完
    for (Future<?> f : futures) {
        f.get();  // 有超时兜底
    }
    consumer.commitSync(); // 处理完再提交
}
```

---

### 场景 2：消费逻辑需要调用外部接口（耗时 200ms），积压严重怎么办？

**分析**：单条处理 200ms，单线程每秒只能处理 5 条，积压是必然的。

**方案**：

| 方案 | 说明 | 注意 |
|------|------|------|
| **多线程消费** | 拉取一批消息后分发到线程池并行处理 | 同一个 Partition 的消息不要打乱顺序（如需保序） |
| **批量调用下游** | 如果能支持批量接口，攒一批一起调 | 减少网络往返 |
| **异步化** | 消息落库后异步处理，先返回 ACK | 需要兜底任务扫描未处理消息 |
| **增加 Partition** | 配合增加消费者，并行处理 | 注意分区后 Key 路由变化 |

---

### 场景 3：频繁 Rebalance 导致消费积压，怎么排查？

**Rebalance 触发条件**：

```
1. 消费者心跳超时          → session.timeout.ms（默认 45s）
2. 两次 poll 间隔超时       → max.poll.interval.ms（默认 5min）
3. 消费者主动退出/加入      → 新消费者加入，触发全组 Rebalance
```

**排查步骤**：

```bash
# 1. 看消费者日志，搜索 "rebalance"
# 2. 检查 Full GC → jstat -gcutil <pid> 1000
# 3. 检查是否 poll 间隔超时（处理逻辑太慢）
```

**解决方案**：

```properties
# session.timeout.ms 不建议调太小，网络抖动可能导致频繁 Rebalance
session.timeout.ms=45000

# 如果单次 poll 处理确实需要较长时间
max.poll.interval.ms=600000   # 调大到 10 分钟

# 减少每次拉取量，加快单批次处理速度
max.poll.records=200

# 新版消费者：使用增量协作式 Rebalance
partition.assignment.strategy=org.apache.kafka.clients.consumer.CooperativeStickyAssignor
```

---

### 场景 4：Producer 端消息发不出去怎么排查？

**排查清单**：

```
1. 网络是否通？→ telnet <broker_ip> 9092
2. Broker 是否存活？→ 看 Broker 进程和日志
3. 磁盘是否满了？→ df -h
4. 分区 Leader 是否正常？→ kafka-topics.sh --describe
5. ACK 配置是否合理？→ acks=all 且 ISR 不足？
```

**常见错误及解决**：

| 错误 | 原因 | 解决 |
|------|------|------|
| `NOT_ENOUGH_REPLICAS` | ISR 数量 < min.insync.replicas | 检查 Follower 同步状态，恢复滞后副本 |
| `NETWORK_EXCEPTION` | 网络问题 | 检查 Broker 地址、防火墙、安全组 |
| `RECORD_TOO_LARGE` | 消息超过 `max.request.size` | 调大参数或压缩消息体 |
| `TOPIC_AUTHORIZATION_FAILED` | ACL 权限问题 | 检查 SASL 认证配置 |

---

### 场景 5：Kafka Broker 磁盘满了怎么办？

**紧急处理**：

```bash
# 1. 修改保留策略，紧急清理
kafka-configs.sh --alter --entity-type topics \
  --entity-name <topic> --add-config retention.bytes=1073741824

# 2. 或者缩短保留时间
kafka-configs.sh --alter --entity-type topics \
  --entity-name <topic> --add-config retention.ms=3600000

# 3. 如果还不行，手动删除旧 Segment（慎重）
```

**预防措施**：

```properties
# 设置 Topic 级别保留策略
log.retention.bytes=107374182400   # 100GB
log.retention.hours=168            # 7 天

# 监控磁盘使用率，> 80% 告警
# 扩容 Broker、增加磁盘、迁移 Partition
```

---

## 二、重复消费 & 幂等性（共 4 题）

### 场景 6：线上出现订单重复扣款，怀疑 Kafka 重复消费，怎么排查和解决？

**问题分析**：Kafka 只能保证"至少一次"（At Least Once），重复消费无法完全避免，必须在业务层做幂等。

**重复消费根因**：

```
消费者处理完消息 → 准备提交 offset → 消费者宕机
    ↓
消费者重启 → 从上次已提交 offset 开始消费 → 重复消费
```

**幂等方案（按强度递增）**：

**方案一：Redis SETNX（推荐大多数场景）**

```java
String msgId = record.value().getMsgId();
String redisKey = "msg:dedup:" + msgId;
// SETNX + 设置过期时间（原子操作）
boolean success = redisTemplate.opsForValue()
    .setIfAbsent(redisKey, "1", Duration.ofHours(24));
if (!success) {
    // 重复消息，跳过
    return;
}
processRecord(record);
```

**方案二：数据库唯一索引**

```sql
-- 消息处理记录表
CREATE TABLE event_record (
    msg_id VARCHAR(128) PRIMARY KEY,
    status VARCHAR(20),
    create_time DATETIME
);
-- 插入成功 = 首次处理，插入失败 = 重复消息
INSERT INTO event_record(msg_id, status, create_time)
VALUES ('msg_123', 'PROCESSING', NOW());
-- 处理成功后更新状态
UPDATE event_record SET status = 'DONE' WHERE msg_id = 'msg_123';
```

**方案三：业务状态机**

```java
// 订单状态：PENDING → PAID → SHIPPED → DONE
// 重复的 PAY 事件不会改变已支付状态
if (order.getStatus() == OrderStatus.PENDING) {
    order.setStatus(OrderStatus.PAID);
    orderRepository.save(order);
} else {
    // 已支付，跳过
}
```

**方案四：Kafka 事务 + 业务 DB 事务联动**

```java
// 利用数据库事务 + offset 记录，保证原子性
@Transactional
public void processAndCommit(ConsumerRecord record) {
    // 1. 处理业务
    doBusiness(record);
    // 2. 记录 offset 到同一数据库
    offsetRepository.save(new OffsetRecord(topic, partition, offset));
}
// 重启时从数据库恢复 offset
```

---

### 场景 7：如何设计一个通用的消息去重组件？

```java
public interface DedupService {
    /**
     * @return true=首次处理  false=重复消息
     */
    boolean tryProcess(String msgId, Duration ttl);
}

// 基于 Redis 的实现
public class RedisDedupService implements DedupService {
    private final RedisTemplate<String, String> redis;

    public boolean tryProcess(String msgId, Duration ttl) {
        String key = "dedup:" + msgId;
        return Boolean.TRUE.equals(
            redis.opsForValue().setIfAbsent(key, "1", ttl));
    }
}

// 使用 BloomFilter 减少 Redis 查询（体量超大时）
// 1. BloomFilter 判断"可能存在" → 查 Redis 精确判断
// 2. BloomFilter 判断"一定不存在" → 直接处理
```

---

### 场景 8：Producer 端网络超时重试导致写入重复怎么办？

**问题**：Producer 发送 → 网络超时 → 但消息实际已写入 Broker → 重试 → Broker 收到两条相同消息。

**解决**：开启生产者幂等

```properties
enable.idempotence=true
# 原理：Broker 通过 (ProducerID, Partition, SeqNum) 去重
# 相同 SeqNum 的消息会被丢弃
```

**注意**：

```properties
# 开启幂等后，以下参数自动设置：
acks=all
retries=Integer.MAX_VALUE
max.in.flight.requests.per.connection=5  # 可大于 1
```

---

## 三、消息有序性（共 3 题）

### 场景 9：订单系统需要严格保证同一订单的消息顺序，怎么设计？

**要求**：订单的"创建 → 支付 → 发货 → 完成"必须顺序处理。

**方案**：Key 路由 + 单线程串行消费

**Producer 端**：

```java
// 用订单 ID 作为 Key，同一个订单进入同一个 Partition
producer.send(new ProducerRecord<>(
    "order-topic",
    orderId,          // Key = 订单 ID
    orderEventJson    // Value
));
```

**Consumer 端**：

```java
// 确保按顺序处理（单线程消费同一 Partition）
while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    // 按 Partition 分组
    for (TopicPartition partition : records.partitions()) {
        List<ConsumerRecord<String, String>> partitionRecords = records.records(partition);
        // 单线程按 offset 顺序处理
        for (ConsumerRecord<String, String> record : partitionRecords) {
            processWithOrderCheck(record);  // 业务逻辑里检查状态机
        }
    }
    consumer.commitSync();
}
```

---

### 场景 10：多线程消费，如何既保序又提高性能？

**核心思路**：按 Key Hash 路由到固定的线程/队列。

```java
// 创建 N 个内存队列，每个队列对应一个处理线程
private final List<BlockingQueue<ConsumerRecord>> queues = new ArrayList<>();
private final ExecutorService executors;

// 初始化
for (int i = 0; i < THREAD_COUNT; i++) {
    queues.add(new LinkedBlockingQueue<>(1000));
    executors.submit(() -> {
        while (running) {
            ConsumerRecord record = queues.get(i).take();
            processWithRetry(record);
        }
    });
}

// 消费线程分发
for (ConsumerRecord<String, String> record : records) {
    int index = Math.abs(record.key().hashCode()) % THREAD_COUNT;
    queues.get(index).put(record);  // 相同 Key 到相同队列 → 保序
}

// CountDownLatch 等本批次全部处理完再提交 offset
latch.await(30, TimeUnit.SECONDS);
consumer.commitSync();
```

---

### 场景 11：上游发消息乱序了，下游怎么处理？

**方案一：消息中自带版本号/序列号**

```java
// 消息格式：{orderId: "123", seq: 3, status: "PAID"}
// 处理逻辑只接受按顺序到达的
private final Map<String, Integer> orderSeqMap = new HashMap<>();

public void process(OrderEvent event) {
    String orderId = event.getOrderId();
    int expectedSeq = orderSeqMap.getOrDefault(orderId, 1);
    if (event.getSeq() == expectedSeq) {
        // 正常处理
        doBusinessLogic(event);
        orderSeqMap.put(orderId, expectedSeq + 1);
    } else if (event.getSeq() > expectedSeq) {
        // 乱序了，存起来等前面的消息到达
        pendingBuffer.put(orderId, event);
        // 或者丢到重试队列
    } else {
        // 重复消息，忽略
    }
}
```

**方案二：消费者端排序缓冲**

```
1. 消费者读取所有分区
2. 按业务时间排序后写入内部有序队列
3. 按固定窗口（如 30s）等待乱序消息
4. 窗口关闭后按顺序处理
```

---

## 四、高可用 & 故障处理（共 4 题）

### 场景 12：某个 Broker 宕机了，会发生什么？

**自动流程**：

```
1. ZK 发现 Broker 临时节点消失（session 超时）
2. Controller 监听到变化
3. Controller 为所有受影响的 Partition 重新选举 Leader
   → 从 ISR 中选新 Leader
4. Controller 将新 Leader 信息下发给所有 Broker
5. Producer/Consumer 收到新的 metadata，重新连接新 Leader
```

**影响时间**：通常毫秒 ~ 几秒

**注意事项**：

```properties
# 如果 ISR 中没有存活 Follower → unclean.leader.election.enable=true
# 会从 OSR 选 Leader → 可能导致数据丢失 → 建议 false（宁可不可用，不丢数据）
unclean.leader.election.enable=false

# 副本因子 ≥ 3，确保至少还有一个 ISR 副本存活
replication.factor=3

# min ISR ≥ 2
min.insync.replicas=2
```

---

### 场景 13：Controller 宕机了怎么办？

```
1. Controller 在 ZK 的临时节点 /controller 被自动删除
2. 所有 Broker 监听到 /controller 节点被删除
3. 所有 Broker 去争抢创建 /controller 节点
4. 先创建成功的 Broker 成为新 Controller
5. 新 Controller 读取 ZK 中的集群元数据，重建内存状态
```

**影响**：Controller 切换期间，无法处理新 Leader 选举（短暂不可用），但不影响已有 Producer/Consumer 的正常读写。

---

### 场景 14：Consumer Group 中某个消费者挂了，消息会丢吗？

**不会丢，但可能重复消费**：

```
1. Broker 发现消费者心跳超时 → 触发 Rebalance
2. 该消费者负责的 Partition 被重新分配给组内其他消费者
3. 新消费者从上次提交的 offset 开始消费
4. 如果挂掉的消费者处理了消息但还没提交 offset → 重复消费
```

**减少影响**：

```java
// 处理完一批就提交一次（但要权衡性能和可靠性）
consumer.commitSync();  // 每条都提交影响性能，建议按批次或按时间间隔提交
```

---

### 场景 15：RabbitMQ 集群宕机丢消息，想迁移到 Kafka，怎么评估？

**Kafka 相比 RabbitMQ 在可靠性上的提升：**

| 维度 | RabbitMQ | Kafka |
|------|----------|-------|
| 消息持久化 | 内存优先，可配持久化队列 | 磁盘优先，消息落地才 ACK |
| 消息回溯 | 不支持（消费即删除） | 支持，可按 offset 重新消费 |
| 副本机制 | 镜像队列（性能开销大） | ISR 机制（异步复制，性能好） |
| 事务 | 支持，但影响性能 | 从 0.11 开始事务支持较好 |

**迁移注意**：

```
- RabbitMQ 是 Push 模式，Kafka 是 Pull 模式 → 消费逻辑要改
- RabbitMQ 全局有序，Kafka 分区内有序 → 需评估业务是否需要全局有序
- RabbitMQ 原生延迟队列，Kafka 需自己实现
```

---

## 五、性能与优化（共 3 题）

### 场景 16：单 Partition 单 Consumer 如何提升消费性能？

**当无法增加 Partition 时的优化手段**：

```java
// 1. 调大拉取参数
props.put("max.poll.records", 1000);
props.put("fetch.min.bytes", 1048576);    // 1MB
props.put("fetch.max.wait.ms", 500);

// 2. 消费端多线程（注意保序要求）
while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

    // 并行处理
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (ConsumerRecord<String, String> record : records) {
        futures.add(CompletableFuture.runAsync(() -> processRecord(record), executor));
    }
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);

    consumer.commitSync();
}

// 3. 优化下游调用
// - 批量写入 DB（攒 100 条一条 INSERT）
// - Redis pipeline
// - 减少不必要的日志
```

---

### 场景 17：Kafka 消费者需要读写 MySQL，如何保证消费性能？

**反模式**：

```java
// ❌ 每条消息都查一次 DB
for (ConsumerRecord record : records) {
    Order order = orderMapper.selectById(record.value().getOrderId());  // 逐条查
    order.setStatus(record.value().getStatus());
    orderMapper.update(order);                                          // 逐条改
}
```

**优化方案**：

```java
// ✅ 批量查询 + 批量更新
List<Long> orderIds = records.stream()
    .map(r -> r.value().getOrderId())
    .collect(toList());

// 一次查出所有订单
List<Order> orders = orderMapper.selectBatchIds(orderIds);

// 批量更新
Map<Long, Order> orderMap = orders.stream().collect(toMap(Order::getId, o -> o));
List<Order> toUpdate = new ArrayList<>();
for (ConsumerRecord record : records) {
    Order order = orderMap.get(record.value().getOrderId());
    order.setStatus(record.value().getStatus());
    toUpdate.add(order);
}
orderMapper.batchUpdate(toUpdate);  // 批量更新
```

---

### 场景 18：同一个 Topic 被 5 个消费组消费，某个消费组处理失败怎么精准重试 + 保证幂等？

**设计方案**：

```
原 Topic: order-events
           ├── 消费组 A：创建订单       → 正常消费
           ├── 消费组 B：扣减库存       → 消费失败！
           ├── 消费组 C：发优惠券       → 正常消费
           ├── 消费组 D：发短信通知     → 正常消费
           └── 消费组 E：写日志         → 正常消费

处理：
1. 消费组 B 处理失败 → 将失败消息写入「重试 Topic」
2. 重试 Topic 只有消费组 B 的专属消费者
3. 重试消费者处理后如果还失败 → 达到最大重试次数 → 写入「死信 Topic」
4. 死信 Topic → 人工兜底
```

```java
// 重试消费模板
int maxRetry = 3;
for (ConsumerRecord<String, String> record : records) {
    try {
        processRecord(record);
        consumer.commitSync();
    } catch (Exception e) {
        int retryCount = getRetryCount(record); // 从 Header 中读取重试次数
        if (retryCount >= maxRetry) {
            // 写入死信 Topic
            sendToDeadLetterQueue(record, e);
        } else {
            // 写入重试 Topic，带上重试次数
            sendToRetryTopic(record, retryCount + 1);
        }
        // 仍然提交 offset，不阻塞正常消费
        consumer.commitSync();
    }
}
```

---

## 六、系统设计（共 2 题）

### 场景 19：设计一个秒杀系统，如何使用 Kafka 削峰填谷？

**架构设计**：

```
用户请求 → Nginx 限流 → 秒杀服务（校验+抢购） → Kafka → 订单服务
              ↓                                        ↓
         令牌桶/漏桶                       异步削峰 + 消费端限流
```

**设计要点**：

**（1）前端限流 + 后端校验**

```java
// 秒杀服务只做快速校验，通过后发 Kafka，不直接操作 DB
@PostMapping("/seckill")
public Result seckill(@RequestParam Long userId, @RequestParam Long goodsId) {
    // 1. 校验库存（Redis 预减）
    Long stock = redis.decr("goods:stock:" + goodsId);
    if (stock < 0) {
        redis.incr("goods:stock:" + goodsId);  // 恢复
        return Result.fail("已售罄");
    }
    // 2. 校验是否已购买（Redis SETNX）
    boolean firstBuy = redis.setIfAbsent("seckill:user:" + userId + ":goods:" + goodsId, "1", Duration.ofMinutes(30));
    if (!firstBuy) {
        return Result.fail("已购买");
    }
    // 3. 异步发 Kafka
    kafkaTemplate.send("seckill-orders", userId + ":" + goodsId);
    return Result.success("排队中");
}
```

**（2）Kafka 削峰**

```
- Topic 设置 50+ Partition，支持高并发
- 订单服务消费者按自身处理能力消费（如 TPS 1000）
- 利用 Kafka 的持久化能力缓冲瞬时高峰流量
```

**（3）兜底**

```
- 消费失败 → 重试 Topic → 最大重试后 → 死信 + 人工退款
- Redis 库存恢复定时任务（订单失败回补库存）
```

---

### 场景 20：如何用 Kafka 实现一个跨系统的数据同步管道？

**场景**：A 系统修改了用户信息，B、C 系统需要同步。

**方案**：

```
A 系统（用户中心）
    ↓ 发送变更事件
Kafka Topic: user-change-events
    ├── B 系统（推荐系统）→ 消费 → 更新用户画像缓存
    └── C 系统（风控系统）→ 消费 → 更新风险评分
```

**消息设计**：

```json
{
  "eventId": "uuid",
  "eventType": "USER_UPDATED",
  "timestamp": 1718784000000,
  "data": {
    "userId": 123,
    "changedFields": ["nickname", "phone"],
    "nickname": "张三",
    "phone": "138****0000"
  }
}
```

**设计要点**：

```java
// 1. 发消息与业务操作保持一致性（发消息必须在事务提交后）
@Transactional
public void updateUser(UserUpdateRequest request) {
    // 1. 更新数据库
    userMapper.update(request);
    // 2. 事务提交后发送消息
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kafkaTemplate.send("user-change-events", buildEvent(request));
            }
        }
    );
}

// 2. 消费端幂等（用 eventId 去重）
// 3. 消息保留策略：7-30 天，支持回放
// 4. Schema Registry 管理消息格式兼容性
```

---

## 📊 场景题速查表

| 场景 | 关键词 | 核心方案 |
|------|--------|----------|
| 消息积压 | Lag 飙升 | 加分区+消费者 → 多线程 → 紧急分流 |
| 重复消费 | 扣款两次 | Redis SETNX / DB 唯一索引 / 状态机 |
| 顺序消费 | 状态机乱序 | Key Hash 路由 + 单线程串行 |
| Broker 宕机 | 节点挂了 | Controller 自动切换 Leader（秒级恢复） |
| Rebalance | 频繁重分配 | 调大超时 + 减少单次处理时间 |
| 磁盘满 | 写不进去 | 缩短 retention + 扩容 |
| 跨系统同步 | CDC / 数据管道 | 事务后发消息 + 消费幂等 + Schema 管理 |
| 秒杀削峰 | 突发流量 | Redis 预减 + Kafka 缓冲 + 限流消费 |

---

*来源：牛客网大厂面经（京东/网易/字节/腾讯/美团）+ 阿里云/腾讯云实践文档*
