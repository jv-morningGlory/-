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

### 2.1 客户端选型：Jedis / Lettuce / Redisson

| 客户端 | 连接模型 | 线程安全 | 连接池 | 定位 |
|--------|---------|---------|--------|------|
| **Jedis** | 直连，一连接一线程 | 否（必须池化） | commons-pool2 | 老牌，API 简单 |
| **Lettuce** | 基于 Netty，单连接可共享 | 是 | 内置（需 commons-pool2） | Spring Boot 默认客户端 |
| **Redisson** | 基于 Netty | 是 | 内置 | 分布式工具箱（锁、限流、延迟队列） |

> **日常组合**：`RedisTemplate` / `StringRedisTemplate` 走 **Lettuce**（Spring 默认，零配置）；分布式锁、限流、延迟队列等走 **Redisson**。两者连同一个 Redis，各用各的连接池，互不影响。

---

### 2.2 池技术本质：省时间 + 保命

> **池 = 复用（省时间）+ 限流（保命）**

- **省时间**：每次新建连接要经历 `TCP 三次握手 + 协议握手 + AUTH 鉴权`（2~3 个 RTT），跨机房几十毫秒。池复用已有连接，直接 borrow 就能用。
- **保命**：`max-active` 限制最大连接数。没有上限，高并发下应用会无限制建连接，瞬间打垮 Redis（**Redis 单线程**，连接过多会拖慢所有命令）。

#### 池如何保证连接可用、并一直保持住？

**问题**：TCP 连接会"偷偷死掉"——防火墙/NAT 静默回收空闲连接、Redis 重启、网络中断，导致**半开连接**（socket 显示已连接，但实际链路已断，下次操作才报错）。

**池的应对**：

| 机制 | 作用 |
|------|------|
| `test-on-borrow` | 借出连接前先 `PING` 一下，坏了就丢掉重建 |
| `test-while-idle` | 空闲时定期检测池中连接是否健康 |
| `time-between-eviction-runs` | 后台检测线程的运行间隔 |
| **心跳** | Lettuce / Redisson 底层定时发命令，保持连接活跃 |

> 池中的连接"一直活着"，不是 TCP 自己活着的，而是**池在主动探测和续命**。

---

### 2.3 Spring Boot 连接池核心参数

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 8       # 最大活跃连接数
          max-idle: 8         # 最大空闲连接数
          min-idle: 0         # 最小空闲连接数（常驻）
          max-wait: -1ms      # 获取连接最大等待时间，-1 表示无限等待
```

| 参数 | 含义 | 建议值 |
|------|------|--------|
| `max-active` | 池最大连接数（**保命上限**） | 按并发预估，一般 8~50 |
| `max-idle` | 最大空闲连接数 | ≤ `max-active`，避免浪费 |
| `min-idle` | 最小空闲连接数（常驻，省首次建连） | 预热场景设 2~4，多数用 0 |
| `max-wait` | 连接耗尽时的等待时间 | 生产建议 3000~5000ms，别用 -1 |
| `time-between-eviction-runs` | 空闲检测间隔（配合 test-while-idle） | 30s~60s |

> **坑**：Lettuce 的 `pool` 配置要生效，**必须引入 `commons-pool2` 依赖**，否则配置不报错但池化不生效（退化为单连接）。

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
