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

> 数据类型、持久化、高可用、缓存三大问题、bigkey/hotkey 等内容已整理到 [面试题.md](面试题.md)，本文档不再重复。
