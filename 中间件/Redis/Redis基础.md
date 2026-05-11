# Redis 基础与实战

---
## 1. Key 命名规范

### 1.1 命名格式

```
项目名:业务模块名:唯一标识:value类型
```

| 示例 | 说明 |
|------|------|
| `crm:index:10020:string` | CRM 项目，指标模块，ID=10020，值为 String |
| `crm:index_report:2001:map` | CRM 项目，指标报表模块，ID=2001，值为 Map |
| `crm:index_disposal.create:2001:string` | 用 `.` 连接同一业务逻辑的子模块 |

> **规则**：同一业务逻辑含义段的单词之间使用英文半角点号 `.` 分割，不同层级用 `:` 分割。

### 1.2 命名原则

- **可读性**：看到 key 就知道属于哪个业务、存的是什么
- **命名空间隔离**：通过项目名前缀防止 key 冲突
- **类型后缀**：`string` / `list` / `set` / `zset` / `hash` / `map`，方便运维和排查

---

## 2. Spring Data Redis 集成

### 2.1 依赖配置

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
    <exclusions>
        <!-- 排除默认 logging，使用项目统一的日志框架 -->
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-logging</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
    <version>2.9.0</version>
</dependency>
```

### 2.2 Lettuce 连接池配置

**池技术（Pooling）** 是一种**空间换时间**的优化手段：预先创建一批对象放入"池"中，使用时从池中借用，用完归还，避免反复创建/销毁对象的开销。

```
不使用池：每次请求 → new 对象 → 用完 → 销毁（GC）
使用池：  首次初始化 → 创建 N 个对象放入池 →
          请求 → borrow（借用）→ 用完 → return（归还）
```

**为什么需要连接池？**

| 对比 | 无连接池 | 有连接池 |
|------|---------|---------|
| TCP 连接 | 每次新建，用完关闭 | 复用已有连接 |
| 三次握手 | 每次都发生 | 仅首次 |
| TIME_WAIT | 频繁出现，消耗端口 | 可忽略 |
| 响应延迟 | 高（建连 ~10ms） | 低（直接获取） |

**池化技术的通用配置维度：**

| 配置维度 | 连接池参数 | 线程池参数 | 含义 |
|---------|-----------|-----------|------|
| **核心容量** | `min-idle` | `core-pool-size` | 池中最少保留的资源数，避免空池首次请求的创建开销 |
| **最大容量** | `max-active` / `max-idle` | `max-pool-size` | 池中允许的最大资源数，防止资源耗尽 |
| **获取超时** | `max-wait` | `keep-alive-time` + 拒绝策略 | 资源耗尽时，新请求等待多久 / 如何处理 |
| **空闲回收** | `time-between-eviction-runs` | `keep-alive-time` | 超过核心数的空闲资源多久后回收 |
| **健康检查** | `test-on-borrow` / `test-while-idle` | — | 借出前/定期验证资源是否可用 |

**线程池工作流程：**

```
提交任务 → 核心线程数未满？ → 创建新线程执行
            ↓ 已满
         工作队列未满？ → 入队等待
            ↓ 已满
         最大线程数未满？ → 创建新线程执行
            ↓ 已满
         执行拒绝策略
```

**线程池任务队列：**

| 队列类型 | 特点 | 适用场景 |
|---------|------|---------|
| `SynchronousQueue` | 不存储任务，提交一个必须有一个线程来接 | 任务少、要求即时响应（CachedThreadPool 默认） |
| `LinkedBlockingQueue` | 无界链表队列（可设容量），FIFO | 任务平稳、能容忍排队（FixedThreadPool 默认） |
| `ArrayBlockingQueue` | 有界数组队列，必须指定容量 | 需要严格控制内存、防止 OOM |
| `PriorityBlockingQueue` | 无界优先级队列，按任务优先级排序 | 任务有优先级区分 |

> **为什么要有队列？** 突发流量时作为缓冲区，避免无限制创建线程导致系统崩溃。核心线程忙 → 任务堆在队列 → 队列满了才扩到最大线程数。

**线程池拒绝策略：**

| 策略 | 行为 | 适用场景 |
|------|------|---------|
| `AbortPolicy`（默认） | 抛出 `RejectedExecutionException` | 必须感知任务丢失的场景 |
| `CallerRunsPolicy` | 由提交任务的线程自己执行 | 能接受调用者被阻塞，提供天然的限流缓冲 |
| `DiscardPolicy` | 直接丢弃，不抛异常 | 允许丢失非关键任务（日志、埋点） |
| `DiscardOldestPolicy` | 丢弃队列中最旧的任务，重试提交新任务 | 新任务优先于旧任务 |

> **推荐**：关键业务用 `CallerRunsPolicy` 配合监控告警；非关键任务用 `DiscardPolicy`。

**线程池大小估算：**

| 任务类型 | 公式 | 说明 |
|---------|------|------|
| 计算密集型 | `N + 1` | N=CPU 核数，+1 应对线程偶尔阻塞 |
| IO 密集型 | `2N` 或 `N * (1 + WT/ST)` | WT=等待时间，ST=计算时间，需压测确认 |
| 混合型 | 拆分为两个线程池分别配置 | 避免 IO 任务占满所有线程 |

> **最终要压测**：公式只是起点，实际最优值必须通过压测确定。

**连接池 vs 线程池对比：**

| 维度 | 连接池 | 线程池 |
|------|--------|--------|
| **池中是什么** | TCP 连接（网络资源） | 线程（CPU 资源） |
| **核心开销** | 三次握手 + 认证 | 线程创建 + 上下文切换 |
| **队列** | 通常直接阻塞等待（`max-wait`） | 有独立工作队列，队列满了才拒绝 |
| **资源上限因素** | 数据库/Redis 最大连接数限制 | CPU 核数 + 内存 |
| **框架示例** | HikariCP、Druid、Commons Pool2 | ThreadPoolExecutor、ForkJoinPool |

**Lettuce 连接池配置示例：**

```properties
# Redis 基础配置
spring.redis.database=0
spring.redis.host=10.220.0.2
spring.redis.port=6379
spring.redis.password=Cxsk@2022

# Lettuce 连接池配置
spring.redis.lettuce.pool.min-idle=0
spring.redis.lettuce.pool.max-active=8
spring.redis.lettuce.pool.max-idle=8
spring.redis.lettuce.pool.max-wait=-1

# 连接超时配置
spring.redis.connect-timeout=30000
```

| 参数 | 含义 | 建议值 |
|------|------|--------|
| `min-idle` | 最小空闲连接数 | 预热场景设 2~4，多数场景用默认 0 |
| `max-active` | 最大活跃连接数 | 根据并发量预估，一般 8~50 |
| `max-idle` | 最大空闲连接数 | ≤ `max-active`，避免浪费 Redis 连接 |
| `max-wait` | 获取连接最大等待时间(ms) | -1 表示无限等待，生产建议设 3000~5000 |
| `time-between-eviction-runs` | 空闲连接检测间隔 | 配合 `max-idle` 使用，回收多余连接 |

> **注意**：Lettuce 本身基于 Netty，单连接就支持并发（共享连接），连接池主要解决的是连接数上限控制和资源隔离问题。

### 2.3 RedisTemplate 配置

```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key 统一用 String 序列化，保证可读性
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value 用 JSON 序列化，支持复杂对象
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }
}
```

> **为什么 Key 用 String 序列化？** 保证 key 在 Redis 中可读、可搜索。如果用 JDK 序列化，key 会变成一堆不可读的二进制。
> **为什么 Value 用 JSON 序列化？** 可读性好，跨语言兼容。GenericJackson2JsonRedisSerializer 会在 JSON 中记录 `@class` 信息，反序列化时可以还原类型。

---

## 3. 数据类型实战

### 3.1 Set — 业务互斥锁

**场景**：CRM 处置任务，可被多个人同时修改和提交。用 Set 集合维护正在进行的业务 ID，防止并发冲突。

```java
// 核心方法：加锁 → 执行业务 → 解锁
private <T> T lockMutexBizIds(List<Long> warningBasicIds,
                               Supplier<T> supplier, String lockKey) {
    if (CollUtil.isEmpty(warningBasicIds)) {
        return supplier.get();
    }
    lockBizIds(warningBasicIds, lockKey);
    try {
        return supplier.get();
    } finally {
        unlockWarningBasicIds(warningBasicIds, lockKey);
    }
}

private void lockBizIds(List<Long> basicIds, String lockKey) {
    // 1. 先获取分布式锁，防止并发操作 Set 本身
    RedisLock redisLock = new RedisLock(redisTemplate, CRM_PRE_DISPOSAL_MUTEX);
    boolean locked = redisLock.tryLock();
    if (!locked) {
        throw new ServiceException("系统繁忙，请稍后重试！");
    }
    try {
        // 2. 检查 Set 中是否已存在正在处理的业务 ID
        Set<String> members = redisTemplate.opsForSet().members(lockKey);
        if (members != null) {
            for (Long basicId : basicIds) {
                if (members.contains(basicId.toString())) {
                    throw new ServiceException(
                        "预警指标被同时处理，同时处理id :" + basicId + " 请刷新页面稍后重试！");
                }
            }
        }
        // 3. 将业务 ID 加入 Set，标记为"处理中"
        redisTemplate.opsForSet().add(lockKey,
            basicIds.stream().map(Object::toString).toArray(String[]::new));
    } finally {
        if (locked) {
            redisLock.unlock();
        }
    }
}

private void unlockWarningBasicIds(List<Long> basicIds, String lockKey) {
    redisTemplate.opsForSet()
        .remove(lockKey, basicIds.stream().map(Object::toString).toArray(String[]::new));
}
```

> **为什么用 Set 而不是单一分布式锁？** 因为需要同时维护多个业务 ID 的互斥状态，Set 可以方便地 add/remove/contains 检查。

---

### 3.2 String — 限流降级兜底

**场景**：CRM 调用第三方系统接口，前端需要实时数据。为了保证第三方系统稳定，CRM 做了熔断和降级。

```properties
# Resilience4j 限流配置
resilience4j.ratelimiter.instances.getTotalCountRateLimiter.limit-refresh-period=1m
resilience4j.ratelimiter.instances.getTotalCountRateLimiter.limit-for-period=200
resilience4j.ratelimiter.instances.getTotalCountRateLimiter.timeout-duration=1s
```

| 配置项 | 含义 |
|--------|------|
| `limit-refresh-period=1m` | 每分钟刷新限流计数器 |
| `limit-for-period=200` | 每分钟最多 200 个请求 |
| `timeout-duration=1s` | 获取许可的最大等待时间 1 秒 |

```java
// 正常调用，有限流保护
@RateLimiter(name = "getTotalCountRateLimiter", fallbackMethod = "getTotalCountFallback")
public TotalCountResp getTotalCount() {
    // 调用第三方接口...
}

// 限流降级：从 Redis 缓存中读取上一次的查询结果
public TotalCountResp getTotalCountFallback(String iamUserId, RequestNotPermitted ex)
        throws JsonProcessingException {
    log.info("IndexWarningService.getTotalCountFallback get redisCache.");
    String redisCache = redisTemplate.opsForValue()
        .get(QUERY_WARNING_REVISE_CENTER_PLATFORM_TODO + iamUserId);
    if (StrUtil.isBlank(redisCache)) {
        return new TotalCountResp();  // 缓存也没有，返回空对象兜底
    }
    return new ObjectMapper().readValue(redisCache, TotalCountResp.class);
}
```

> **降级策略**：正常请求 → 限流触发 → 走 fallback → 读 Redis 缓存 → 缓存为空返回兜底空对象。这样保证前端至少不会报错白屏。

---

### 3.3 Hash — 业务数据缓存

**场景**：部门信息查询，从数据库抽离存入 Redis 缓存，减少 DB 压力。

```java
public List<IndexDeptCodeItem> listIndexReportDeptCode(String iamUserId) {
    String deptCodeKey = CRM_INDEX_INDEX_DEPT_CODE_INDEX_REPORT_INFO;

    // 1. 先查 Redis 缓存
    String cacheJson = (String) redisTemplate.opsForHash()
        .get(deptCodeKey, iamUserId);
    if (StrUtil.isNotBlank(cacheJson)) {
        return OBJECT_MAPPER.readValue(cacheJson,
            new TypeReference<List<IndexDeptCodeItem>>() {});
    }

    // 2. 缓存不存在，查数据库
    List<IndexDeptCodeItem> result = this.baseMapper
        .listIndexDeptCode(iamUserId, CrmSysConstants.INDEX_AUTH_INDEX_MANAGEMENT);
    if (CollUtil.isEmpty(result)) {
        return Collections.emptyList();
    }

    // 3. 将结果存入 Redis，过期时间 1 天
    String resultJson = OBJECT_MAPPER.writeValueAsString(result);
    Boolean hasKey = redisTemplate.hasKey(deptCodeKey);
    redisTemplate.opsForHash().put(deptCodeKey, iamUserId, resultJson);
    if (Boolean.FALSE.equals(hasKey)) {
        redisTemplate.expire(deptCodeKey, 1, TimeUnit.DAYS);
    }
    return result;
}
```

> **为什么用 Hash 而不是 String？** Hash 可以将每个用户的部门信息存在一个独立的 field 中，方便按用户维度管理和过期。如果用 String，每个用户一个 key，管理起来很分散。
> **注意 expire 时机**：只在 key 首次创建时设置过期时间，避免每次都重置 TTL。

---

## 4. Redis 五种基本数据类型

| 类型 | 结构 | 常用命令 | 典型场景 |
|------|------|---------|---------|
| **String** | key-value | `SET`, `GET`, `INCR`, `SETEX` | 缓存、计数器、分布式锁 |
| **Hash** | key → field-value | `HSET`, `HGET`, `HGETALL`, `HDEL` | 对象缓存（用户信息、配置项） |
| **List** | 双向链表 | `LPUSH`, `RPOP`, `LRANGE` | 消息队列、最新列表 |
| **Set** | 无序集合 | `SADD`, `SMEMBERS`, `SINTER` | 标签、好友关系、去重 |
| **ZSet** | 有序集合 | `ZADD`, `ZRANGE`, `ZREVRANK` | 排行榜、延迟队列 |

---

## 5. 缓存策略

### 5.1 Cache-Aside（旁路缓存）

```
读：先查缓存 → 缓存有则返回 → 缓存无则查 DB → 写入缓存 → 返回
写：更新 DB → 删除缓存（不是更新缓存）
```

> **为什么是删除缓存而不是更新缓存？** 更新缓存可能写入脏数据；删除缓存让下次读操作重建，更简单可靠。

### 5.2 缓存淘汰策略

| 策略 | 含义 |
|------|------|
| `noeviction` | 不淘汰，内存满时写入报错 |
| `allkeys-lru` | 所有 key 中淘汰最近最少使用的 |
| `volatile-lru` | 设置了过期时间的 key 中淘汰 LRU |
| `allkeys-lfu` | 所有 key 中淘汰最不经常使用的 |
| `volatile-lfu` | 设置了过期时间的 key 中淘汰 LFU |
| `volatile-ttl` | 设置了过期时间的 key 中淘汰 TTL 最短的 |

> **推荐**：通用场景用 `allkeys-lru`，需要热度统计用 `allkeys-lfu`。

---

## 6. 持久化

### 6.1 RDB vs AOF

| 维度 | RDB | AOF |
|------|-----|-----|
| **原理** | 定期快照（fork 子进程写全量数据） | 记录每次写操作日志 |
| **文件大小** | 小（压缩的二进制） | 大（可配置重写压缩） |
| **恢复速度** | 快 | 慢（逐条回放命令） |
| **数据安全** | 可能丢失最后一次快照后的数据 | 可配置每秒 fsync，丢失更少 |
| **性能影响** | fork 时可能短暂阻塞 | 持续 IO 开销 |

> **生产建议**：RDB + AOF 同时开启，RDB 用于快速恢复和备份，AOF 保证数据安全性。

---

## 7. 高可用架构

| 方案 | 原理 | 特点 |
|------|------|------|
| **主从复制** | 一主多从，主写从读 | 读写分离，故障需手动切换 |
| **Sentinel（哨兵）** | 监控主节点，故障自动选举新主 | 自动故障转移，至少 3 个哨兵节点 |
| **Cluster（集群）** | 数据分片，每个节点存部分数据 | 水平扩展，支持海量数据 |

---

## 8. 缓存三大问题

### 8.1 缓存穿透

**现象**：查询一个**根本不存在**的数据，缓存中没有，DB 中也没有。每次请求都会绕过缓存直接打到 DB。

```
请求 → 缓存(miss) → DB(miss) → 返回空
  ↑                              |
  └──── 下次请求重复上述过程 ──────┘
```

> **根本原因**：缓存和 DB 都没有数据，无法建立缓存，导致每次请求都穿透到 DB。常见于恶意攻击（用不存在的 ID 大量请求）或业务查询。

**方案一：空值缓存**

```java
public Item getItem(Long id) {
    String key = "item:" + id;
    String cache = redisTemplate.opsForValue().get(key);
    if (cache != null) {
        if ("NULL".equals(cache)) {  // 命中了之前缓存的空值
            return null;
        }
        return JSON.parseObject(cache, Item.class);
    }
    // 查 DB
    Item item = db.query(id);
    if (item == null) {
        // 将空值也缓存，设置短过期时间，防止恶意攻击
        redisTemplate.opsForValue().set(key, "NULL", 60, TimeUnit.SECONDS);
        return null;
    }
    redisTemplate.opsForValue().set(key, JSON.toJSONString(item), 30, TimeUnit.MINUTES);
    return item;
}
```

> **注意**：空值过期时间设短（1~5 分钟），否则正常数据写入后还被空值挡住。

**方案二：布隆过滤器（Bloom Filter）**

```
布隆过滤器原理：
一个 key 经过 k 个 hash 函数 → 在 bit 数组中置位 k 个位置为 1
查询时 key 经过同样的 k 个 hash → 检查 k 个位置是否都为 1
  都为 1 → "可能存在"（有误判率）→ 继续查缓存/DB
  有一个为 0 → "一定不存在" → 直接返回，不查 DB
```

```java
// Guava 布隆过滤器示例
BloomFilter<String> filter = BloomFilter.create(
    Funnels.stringFunnel(Charset.defaultCharset()),
    1_000_000,   // 预计元素数量
    0.01         // 误判率 1%
);

// 初始化：把所有 DB 中的 ID 加入布隆过滤器
List<Long> allIds = db.queryAllIds();
allIds.forEach(id -> filter.put(id.toString()));

// 查询时先过布隆过滤器
public Item getItem(Long id) {
    if (!filter.mightContain(id.toString())) {
        return null;  // 一定不存在，直接返回
    }
    // 可能存在，继续查缓存 → DB
    // ... 正常缓存流程
}
```

| 对比 | 空值缓存 | 布隆过滤器 |
|------|---------|-----------|
| **实现复杂度** | 低 | 中（需要初始化 + 维护 bit 数组） |
| **空间占用** | 每个不存在的 key 一条 Redis 记录 | 固定大小 bit 数组（1000W 数据 ≈ 12MB） |
| **缺点** | 恶意用不同 ID 攻击仍会占满 Redis | 有误判率，元素不能删除 |
| **适用场景** | 偶发的不存在 key 查询 | ID 范围固定、需要防御大量恶意请求 |

---

### 8.2 缓存击穿

**现象**：一个**热点 key** 在过期瞬间，大量并发请求同时打到 DB。

```
时间线：
T0: 热点 key 在缓存中，正常服务
T1: key 过期
T2: 瞬间涌入 1000 个请求 → 缓存全部 miss → 1000 个请求同时查 DB
T3: DB 压力飙升，可能挂掉
```

> **关键区别**：击穿是**一个热点 key** 过期，雪崩是**大量 key** 同时过期。

**方案一：互斥锁（分布式锁）**

只让一个请求去查 DB 并重建缓存，其他请求等待。

```java
public Item getItemWithLock(Long id) {
    String key = "item:" + id;
    String lockKey = "lock:item:" + id;

    // 1. 先查缓存
    String cache = redisTemplate.opsForValue().get(key);
    if (StrUtil.isNotBlank(cache)) {
        return JSON.parseObject(cache, Item.class);
    }

    // 2. 缓存未命中，尝试获取锁
    String lockValue = UUID.randomUUID().toString();
    boolean locked = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS);

    if (Boolean.TRUE.equals(locked)) {
        try {
            // 3. 双重检查：拿到锁后再查一次缓存（可能前一个线程刚写完）
            cache = redisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(cache)) {
                return JSON.parseObject(cache, Item.class);
            }
            // 4. 查 DB 并重建缓存
            Item item = db.query(id);
            if (item != null) {
                redisTemplate.opsForValue().set(key,
                    JSON.toJSONString(item), 30, TimeUnit.MINUTES);
            }
            return item;
        } finally {
            // 5. 释放锁（用 Lua 保证原子性）
            String lua = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            redisTemplate.execute(new DefaultRedisScript<>(lua, Long.class),
                Collections.singletonList(lockKey), lockValue);
        }
    } else {
        // 6. 没拿到锁，短暂休眠后重试
        try { Thread.sleep(50); } catch (InterruptedException e) {}
        return getItemWithLock(id);  // 递归重试
    }
}
```

> **为什么用 Lua 脚本释放锁？** 保证"判断锁值 + 删除"的原子性，防止误删别人的锁。

**方案二：逻辑过期 + 异步更新**

不设 TTL，value 中记录过期时间戳。读取时发现过期则返回旧值 + 异步更新。

```java
@Data
public class RedisData {
    private Object data;       // 实际数据
    private LocalDateTime expireTime;  // 逻辑过期时间
}

public Item getItemWithLogicExpire(Long id) {
    String key = "item:" + id;
    String lockKey = "lock:item:" + id;

    String cache = redisTemplate.opsForValue().get(key);
    if (StrUtil.isBlank(cache)) {
        // 缓存完全为空（首次加载），查 DB
        return loadFromDbAndCache(id);
    }

    RedisData redisData = JSON.parseObject(cache, RedisData.class);
    Item item = (Item) redisData.getData();

    // 检查是否逻辑过期
    if (LocalDateTime.now().isBefore(redisData.getExpireTime())) {
        return item;  // 未过期，直接返回
    }

    // 已过期：先返回旧数据，再异步重建
    String lockValue = UUID.randomUUID().toString();
    boolean locked = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS);
    if (Boolean.TRUE.equals(locked)) {
        // 开新线程异步更新
        threadPool.submit(() -> {
            try {
                loadFromDbAndCache(id);
            } finally {
                // 释放锁...
            }
        });
    }
    return item;  // 返回旧值，不阻塞
}
```

| 对比 | 互斥锁 | 逻辑过期 |
|------|--------|---------|
| **一致性** | 强一致（拿到锁后查 DB，返回最新数据） | 最终一致（返回旧值，异步更新） |
| **性能** | 未拿到锁的请求需要等待 | 所有请求直接返回，无等待 |
| **实现复杂度** | 中 | 高（需要维护过期时间和异步线程池） |
| **适用场景** | 一致性要求高的数据 | 高并发、能容忍短暂不一致的热点数据 |

---

### 8.3 缓存雪崩

**现象**：**大量 key 在同一时间段过期**，或者 **Redis 宕机**，导致所有请求直接打到 DB。

```
场景 A：大量 key 同时过期
  key1 ── TTL=30min ──→ 过期
  key2 ── TTL=30min ──→ 过期   } 同一时刻 → DB 瞬间承受所有请求
  key3 ── TTL=30min ──→ 过期

场景 B：Redis 宕机
  所有请求 → 缓存全部 miss → DB 被打挂 → 级联故障
```

**方案一：过期时间加随机值**

```java
// 基础过期时间 30 分钟 + 随机 0~10 分钟
int ttl = 30 * 60 + new Random().nextInt(10 * 60);
redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS);
```

> **原理**：让 key 的过期时间分散开，避免集体过期。

**方案二：多级缓存**

```
请求 → 本地缓存(Caffeine) → Redis → DB
        ↓ 命中              ↓ 命中    ↓
       直接返回            直接返回   写入 Redis + 本地缓存
```

```java
// Caffeine 本地缓存 + Redis 二级缓存
@Service
public class ItemService {
    private Cache<Long, Item> localCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build();

    public Item getItem(Long id) {
        // L1：本地缓存
        Item item = localCache.getIfPresent(id);
        if (item != null) return item;

        // L2：Redis
        String cache = redisTemplate.opsForValue().get("item:" + id);
        if (StrUtil.isNotBlank(cache)) {
            item = JSON.parseObject(cache, Item.class);
            localCache.put(id, item);  // 回填本地缓存
            return item;
        }

        // L3：DB
        item = db.query(id);
        if (item != null) {
            redisTemplate.opsForValue().set("item:" + id,
                JSON.toJSONString(item), 30, TimeUnit.MINUTES);
            localCache.put(id, item);
        }
        return item;
    }
}
```

**方案三：限流降级**

即使缓存全挂，也保证 DB 不会被瞬间打死（参考 3.2 节的 Resilience4j 示例）。

**方案四：Redis 高可用**

| 措施 | 说明 |
|------|------|
| **主从 + 哨兵** | 宕机自动切换，读请求导向从节点 |
| **Cluster** | 数据分散在多个节点，单个节点挂掉影响范围有限 |
| **持久化** | RDB + AOF 同时开启，快速恢复数据 |

---

### 8.4 三兄弟对比总结

| 问题 | 触发条件 | 核心解决方案 | 一句话总结 |
|------|---------|-------------|-----------|
| **穿透** | 查不存在的数据 | 布隆过滤器 / 空值缓存 | "数据根本不存在，别查 DB" |
| **击穿** | 一个热点 key 过期 | 互斥锁 / 逻辑过期 | "让一个人查 DB，其他人等着" |
| **雪崩** | 大量 key 同时过期 / Redis 宕机 | TTL 加随机值 / 多级缓存 / 高可用 | "别让所有 key 同时过期，别让 Redis 单点" |

---
### 8.5 bigkey

| 问题 | 现象 | 解决方案 |
|------|------|---------|
| **bigkey** | 单个 key 的 value 过大，导致阻塞 | 拆分 key、用 Hash 分 field 存储 |

> **排查**：`redis-cli --bigkeys` 扫描大 key；生产慎用，建议用 `MEMORY USAGE key`。
> **危害**：读写 bigkey 会阻塞 Redis 单线程，分配/释放内存时可能卡顿。

### 8.6 hotkey

| 问题 | 现象 | 解决方案 |
|------|------|---------|
| **hotkey** | 某个 key 被大量请求集中访问 | 本地缓存、读写分离、key 复制到多个分片 |

> **排查**：`redis-cli --hotkeys`（需 `maxmemory-policy` 为 lfu）。
> **处理**：轻量级用 Caffeine 本地缓存抗一层；重量级在 key 后加随机后缀分散到多个 key。
