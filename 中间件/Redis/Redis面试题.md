# Redis 常见面试题

---

## 1. 基础概念

### Q1：Redis 是什么？有什么特点？

Redis（Remote Dictionary Server）是一个基于**内存**的 **key-value** 存储系统，主要特点：

- **高性能**：内存操作，单线程模型（6.0+ 引入多线程 IO），每秒 10w+ QPS
- **数据结构丰富**：String、Hash、List、Set、ZSet、Bitmap、HyperLogLog、Geo、Stream
- **持久化**：支持 RDB 快照和 AOF 日志
- **高可用**：主从复制、哨兵模式、Cluster 集群
- **功能丰富**：发布订阅、Lua 脚本、事务、Pipeline

### Q2：Redis 为什么这么快？

| 原因 | 说明 |
|------|------|
| **纯内存操作** | 数据存在内存中，无磁盘 IO 瓶颈 |
| **单线程模型** | 没有多线程的上下文切换和锁竞争 |
| **IO 多路复用** | 一个线程通过 epoll/kqueue 管理多个连接 |
| **高效数据结构** | 底层数据结构经过专门优化（SDS、跳表、压缩列表等） |

### Q3：Redis 是单线程还是多线程？

- **4.0 之前**：完全单线程
- **4.0**：引入后台线程（异步删除 unlink、flushdb async）
- **6.0**：引入多线程 IO，但**命令执行仍然是单线程**
- **7.0**：继续增强多线程 IO

> Redis 的核心瓶颈不是 CPU，而是网络 IO 和内存带宽。

---

## 2. 数据类型

### Q4：Redis 有哪些数据类型？各自适用什么场景？
| 类型 | 特点 | 场景 | 关键命令 |
|------|------|------|---------|
| **String** | 最基础，二进制安全 | 缓存、计数器、分布式锁、限流 | `SET`/`GET`/`INCR`/`SETNX` |
| **Hash** | key-field-value，适合存对象 | 用户信息、购物车、文章计数 | `HSET`/`HGET`/`HINCRBY` |
| **List** | 有序双向链表 | 消息队列、最新列表、栈 | `LPUSH`/`RPUSH`/`BLPOP`/`BRPOP` |
| **Set** | 无序去重，支持交并差集 | 标签、共同好友、抽奖、点赞去重 | `SADD`/`SINTER`/`SISMEMBER`/`SPOP` |
| **ZSet** | 有序去重，按 score 排序 | 排行榜、延迟队列、分页 | `ZADD`/`ZRANGE`/`ZREVRANGE` |
| **Bitmap** | 位图，极省空间（1 亿 bit ≈ 12MB） | 签到、在线状态 | `SETBIT`/`GETBIT`/`BITCOUNT` |
| **HyperLogLog** | 基数统计，12KB 固定内存，误差 0.81% | UV 统计、去重计数 | `PFADD`/`PFCOUNT` |
| **Geo** | 地理位置（底层 ZSet） | 附近的人、附近门店 | `GEOADD`/`GEORADIUS` |

> Hash 相比 String 存 JSON 的好处：可以单独修改某个字段，不需要整取整写。

---
## 3. 持久化

### Q5：RDB 和 AOF 有什么区别？为什么这样设计？

Redis 是内存数据库，需要持久化到磁盘防宕机丢失。两种思路：**快照** 和 **日志**。

> RDB = 内存的"照片"，隔一段时间拍一张，恢复快但可能丢最后一帧
> AOF = 操作的"录像"，每条写命令都录下来，数据安全但文件会越来越大

| 维度 | RDB | AOF |
|------|-----|-----|
| **原理** | fork 子进程 dump 内存快照为二进制文件 | 追加每条写命令到文本日志 |
| **恢复速度** | 快（二进制直接加载） | 慢（逐条回放命令） |
| **数据安全** | 两次快照之间数据可能丢失 | `everysec` 最多丢 1 秒 |
| **文件大小** | 小（压缩二进制） | 大（可 AOF rewrite 压缩） |
| **性能影响** | fork 瞬间阻塞 + COW 内存翻倍风险 | 取决于 fsync 策略，everysec 几乎无感 |

**设计思路**：RDB 解决"快速恢复"，AOF 解决"数据安全"。两者可以同时开启，Redis 启动时优先加载 AOF（数据更新）。4.0 引入**混合持久化**，rewrite 时把内存以 RDB 格式写入文件头 + 增量 AOF 追加，兼顾快速恢复和数据安全。

**生产建议**：

```bash
# RDB
save 900 1
save 300 10
save 60 10000

# AOF
appendonly yes
appendfsync everysec

# Redis 4.0+ 推荐混合持久化
aof-use-rdb-preamble yes
```

> 没有银弹，只有取舍。对数据完整性要求高就用 AOF + 混合持久化，纯缓存场景可以完全关闭持久化。



---

## 4. 缓存问题

### Q7：缓存穿透 / 击穿 / 雪崩 是什么？怎么解决？
| 问题 | 一句话 | 核心方案 |
|------|--------|---------|
| **穿透** | 查不存在的数据 | 布隆过滤器 / 空值缓存 |
| **击穿** | 一个热点 key 过期 | 互斥锁 / 逻辑过期 |
| **雪崩** | 大量 key 同时过期 / Redis 宕机 | TTL 加随机值 / 多级缓存 / 高可用 |

---

## 5. 分布式锁

### Q8：Redis 如何实现分布式锁？

**基础实现**：

```java
// 加锁：SET lock_key unique_value NX EX 30
Boolean locked = redisTemplate.opsForValue()
    .setIfAbsent(lockKey, lockValue, 30, TimeUnit.SECONDS);

// 解锁：Lua 脚本保证原子性
String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "return redis.call('del', KEYS[1]) else return 0 end";
redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
    Collections.singletonList(lockKey), lockValue);
```

**关键点**：
- `SET NX EX` 一条命令保证加锁+过期原子性
- value 用 UUID，解锁时校验，防止误删
- 解锁用 Lua 脚本，保证 check + delete 原子性

**完整封装**：

```java
package org.jeecg.modules.mldong.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisLock {

    private static final long DEFAULT_TIME_OUT = 100L;
    public static final int DEFAULT_EXPIRE = 60;
    private static final long MAX_TIME_OUT = 120L;

    private final RedisTemplate<String, String> redisTemplate;
    private final String lockKey;
    private final String lockValue;      // 实例化时固定的随机值
    private final int expireTime;
    private final long timeOut;
    private volatile boolean locked;

    // 构造函数：生成随机 lockValue
    public RedisLock(RedisTemplate<String, String> redisTemplate, String lockKey, int expireTime, long timeOut) {
        if (timeOut > MAX_TIME_OUT) {
            throw new IllegalArgumentException("超时时间不能超过120秒");
        }
        this.redisTemplate = redisTemplate;
        this.lockKey = lockKey + "_lock";
        this.expireTime = expireTime;
        this.timeOut = timeOut;
        this.lockValue = UUID.randomUUID().toString();  // 实例化时生成一次
        this.locked = false;
    }

    public RedisLock(RedisTemplate<String, String> redisTemplate, String lockKey, int expireTime) {
        this(redisTemplate, lockKey, expireTime, DEFAULT_TIME_OUT);
    }

    public RedisLock(RedisTemplate<String, String> redisTemplate, String lockKey, long timeOut) {
        this(redisTemplate, lockKey, DEFAULT_EXPIRE, timeOut);
    }

    public RedisLock(RedisTemplate<String, String> redisTemplate, String lockKey) {
        this(redisTemplate, lockKey, DEFAULT_EXPIRE, DEFAULT_TIME_OUT);
    }

    // lock() 方法：先检查 locked 状态
    public boolean lock() {
        if (this.locked) {
            return true;   // 已经持有锁，直接返回成功
        }
        Boolean flag = redisTemplate.opsForValue()
                .setIfAbsent(this.lockKey, this.lockValue, this.expireTime, TimeUnit.SECONDS);
        if (flag == null) {
            throw new RuntimeException("Redis服务异常");
        }
        this.locked = flag;
        return this.locked;
    }

    // tryLock() 也增加 locked 检查，并复用 lockValue
    public boolean tryLock() {
        if (this.locked) {
            return true;
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(this.timeOut);
        while (System.nanoTime() < deadline) {
            Boolean flag = redisTemplate.opsForValue()
                    .setIfAbsent(this.lockKey, this.lockValue, this.expireTime, TimeUnit.SECONDS);
            if (flag == null) {
                throw new RuntimeException("Redis服务异常");
            }
            if (flag) {
                this.locked = true;
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("线程被中断", e);
                return false;
            }
        }
        return false;
    }

    public void unlock() {
        if (!this.locked) {
            return;
        }
        String currentValue = redisTemplate.opsForValue().get(this.lockKey);
        if (Objects.equals(currentValue, this.lockValue)) {
            redisTemplate.delete(this.lockKey);
        }
        this.locked = false;
    }
}
```

> 注意：`unlock()` 中 get + delete 不是原子的，生产环境建议用 Lua 脚本或直接用 Redisson。
> 
> **有了 Redisson 就不需要自己写 RedisLock**。Redisson 已经封装了可重入、自动续期、公平锁等能力，详见 [Q9](#q9redisson-的看门狗watch-dog机制是什么)。自己实现的版本理解原理即可，生产应该用 Redisson。

### Q9：Redisson 的看门狗（Watch Dog）机制是什么？

**Redisson** 是一个 Java 的 Redis 客户端，在 Jedis/Lettuce 之上封装了**分布式锁、集合、队列**等高级功能，最核心的就是分布式锁的**自动续期**能力。

分布式锁的痛点：锁的过期时间不好设。设短了业务没执行完锁就释放了，设长了万一客户端挂了锁一直不释放造成**死锁**。

Redisson 的解决方案——**看门狗**：

```
1. RLock lock = redisson.getLock("order:10086");
2. lock.lock();  // 默认过期 30s，同时启动看门狗
                              ┌──────────────┐
    看门狗（后台定时任务）：    │ 每 10s 检查，  │
                              │ 如果锁还被持有， │
    业务执行中……  →  续期！   │ 续期到 30s     │
    业务执行中……  →  续期！   │              │
    业务完成，lock.unlock()   │ 看门狗停止     │
                              └──────────────┘
3. lock.unlock();  // 主动释放，看门狗随之停止
```

**如何避免死锁**：
- 业务正常执行 → `unlock()` 释放锁，看门狗停止
- 客户端宕机 → 看门狗随 JVM 进程一起死 → 不会再续期 → 30s 后锁自动过期释放
- 所以无论正常还是异常，锁都不会永久占用

```java
RLock lock = redisson.getLock("order:10086");
try {
    lock.lock();           // 加锁，默认 30s 过期，看门狗启动
    // 执行业务逻辑，即使执行几分钟也安全
} finally {
    lock.unlock();         // 释放锁，看门狗停止
}
```

> 对比你自己的 RedisLock：`tryLock()` 设了 expireTime 后不会续期，如果 expireTime 设 60s 但业务跑了 70s，锁提前释放，其他线程就能拿到锁。Redisson 的看门狗解决了这个问题。


---

## 6. 集群与高可用

### Q11：Redis 集群模式有哪些？
**主从复制**：一主多从，主负责写、从负责读。问题是主挂了没法自动切换，需要人工介入。

**Sentinel（哨兵）**：在**主从架构**上加一层哨兵进程，哨兵会盯着 master，发现 master 挂了就投票选出新 master 并通知客户端。解决了**自动故障转移**的问题，但本质上还是一个 master 承担全部写请求，**无法水平扩展**。

```
┌─────────┐    ┌─────────┐    ┌─────────┐
│ Sentinel│    │ Sentinel│    │ Sentinel│    ← 哨兵集群（互相通信）
└────┬────┘    └────┬────┘    └────┬────┘
     │              │              │
     ▼              ▼              ▼
┌─────────┐    ┌─────────┐    ┌─────────┐
│  Master  │───→│ Slave 1  │    │ Slave 2 │    ← 主从 + 自动故障转移
└─────────┘    └─────────┘    └─────────┘
```

**Cluster（集群分片）**：引入 **hash slot（槽位）** 概念，将数据按 key 分散到多个 master 节点，每个 master 只存一部分数据。解决了 **水平扩展** 的问题——数据量大、QPS 高时加节点即可。

```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   Master1    │  │   Master2    │  │   Master3    │
│ slot 0-5460  │  │slot 5461-10922│ │slot 10923-16383│
│   + Slave    │  │   + Slave    │  │   + Slave    │
└──────────────┘  └──────────────┘  └──────────────┘
        ↑                ↑                ↑
        └────────────────┼────────────────┘
                         │
                    客户端（直连对应 master）
```

| 模式 | 核心能力 | 适用场景 |
|------|----------|----------|
| **主从复制** | 读写分离 | 读多写少，可接受手动切换 |
| **Sentinel** | 自动故障转移 | 高可用要求，数据量不大 |
| **Cluster** | 水平扩展 + 自动故障转移 | 数据量大（百 GB+）、QPS 高 |

**各模式最少节点数**：

| 模式 | 最少节点 | 说明 |
|------|----------|------|
| **主从复制** | 2（1 主 + 1 从） | 从节点非必须，但建议至少有 1 个从保证数据安全 |
| **Sentinel** | 3 哨兵 + 2 Redis（1 主 + 1 从）= **5 个进程** | 哨兵需要奇数个(>=3)投票选 leader；实际上哨兵可以和 Redis 部署在同一台机器上，生产环境哨兵和 Redis 各自独立 |
| **Cluster** | **6 个**（3 主 + 3 从） | 3 个 master 是集群形成的最小单位（gossip 协议需要多数派），每个 master 至少 1 个 slave 保证高可用 |

**为什么数据量不大时优先选 Sentinel 而不是 Cluster**：

Cluster 也能保证高可用，但引入了额外的限制：

| 维度 | Sentinel | Cluster |
|------|----------|---------|
| **客户端** | 普通 Jedis/Lettuce 即可 | 必须支持 Cluster 协议（JedisCluster / Lettuce Cluster） |
| **多 key 操作** | `MGET`、`SINTER` 随意用 | 只支持同 slot 内的多 key 操作，跨 slot 直接报错 |
| **事务/Lua** | 无限制 | 脚本中操作的 key 必须在同一个 slot（可用 `{hash_tag}` 强制） |
| **Pipeline** | 无限制 | 跨 slot 需客户端自行按节点拆分合并 |
| **运维复杂度** | 配置简单 | gossip 协议、slot 迁移、节点扩缩容 |

> 数据量小用 Sentinel，架构简单、限制少；数据量大到单机扛不住时才上 Cluster，多付出的复杂度才值得。

### Q12：Redis Cluster 的数据分片原理？

- key 通过 **CRC16** hash 到 0-16383 的 slot
- 每个 master 节点负责一部分 slot
- 客户端计算 `CRC16(key) % 16384`，直连对应 master

```
Master1: slot 0-5460
Master2: slot 5461-10922
Master3: slot 10923-16383
```



---

## 7. 过期策略与内存淘汰

### Q14：Redis 的过期删除策略是什么？

**惰性删除 + 定期删除**：

| 策略 | 做法 |
|------|------|
| **惰性删除** | 访问 key 时才检查是否过期 |
| **定期删除** | 每隔 100ms 随机抽取一批 key 检查 |

> 不用定时删除的原因：每个 key 设定时器 CPU 开销太大。

### Q15：内存淘汰策略有哪些？
| 策略 | 说明 |
|------|------|
| `noeviction` | 不淘汰，内存满时报错 |
| `allkeys-lru` | 所有 key 中淘汰最近最少使用的 |
| `volatile-lru` | 有过期时间的 key 中淘汰 LRU |
| `allkeys-lfu` | 所有 key 中淘汰最不经常使用的 |
| `volatile-lfu` | 有过期时间的 key 中淘汰 LFU |
| `volatile-ttl` | 有过期时间的 key 中淘汰 TTL 最短的 |

> LRU 看"最近"，LFU 看"频率"。通用场景推荐 `allkeys-lru`。


---

## 9. 其他高频问题

### Q17：大 key 和热 key 怎么处理？

**什么是大 key**：阿里云 Redis 开发规范建议 String 不超过 10KB，集合元素不超过 5000。

**大 key 带来的问题**：
- **阻塞**：操作大 key（DEL、HGETALL）耗时久，Redis 单线程处理期间其他请求排队
- **网络带宽打满**：大 value 反复传输，出口带宽成为瓶颈
- **主从同步延迟**：全量同步时大 key 复制慢，主从数据不一致窗口拉大
- **内存碎片**：大 key 频繁更新/删除容易产生内存碎片


**缓存数据库字典会不会出现大 key**：会。字典表通常只有几十条记录，直接缓存没问题。但有几种情况容易出大 key：
- **全量字典打成一个 JSON String**：一个系统几十个字典表，每个字典几十条数据，有人会做一个大接口把所有字典拼成一个 key 返回给前端——这个 key 轻松超过 100KB
- **字典表字段过多**：比如 `dict:area` 存了全国 3000+ 区县，每个带经纬度、邮编等 20 个字段，用 HASH 存就超了 5000 元素的建议值
- 正确做法：按字典类型**分开缓存**，一个字典一个 key，前端按需请求

**解决思路**：

| 策略 | 说明 |
|------|------|
| **拆分** | String 按业务字段拆多个 key；Hash/Set/ZSet 按 field 取 hash 分桶（如 `hash(field) % 100`）；List 按时间拆分（如 `queue:2026-05-01`） |
| **异步删除** | 用 `UNLINK` 代替 `DEL`，主线程立即返回，后台异步回收内存 |
| **分批删除** | 集合类型用 `HSCAN`/`SSCAN` + `HDEL`/`SREM` 分批删，每次删 100 个，间隔几十 ms |
| **压缩** | String 类型用 snappy/gzip 压缩后存储（可减少 60%~80%） |
| **过期** | 设置合理过期时间，及时释放内存 |

**热 key**：某个 key 被超高频率访问（万级 QPS 以上），单分片压力过大，可能导致该分片不可用。  
例：秒杀商品的库存缓存 `seckill:stock:10086`，活动开始时 10 万+ QPS 全部打到同一个 key 所在的分片，该分片带宽被打满，连带分片上其他 key 的请求也超时。

**热 key 解决思路**：

| 策略 | 说明 |
|------|------|
| **本地缓存** | 在应用层加一层 Caffeine/Guava Cache，热点数据直接从 JVM 内存读 |
| **读写分离** | 增加多个只读副本，热 key 请求分散到不同从节点 |
| **key 备份** | 热 key 复制多份到不同分片（如 `hotkey:0`、`hotkey:1`、`hotkey:2`），随机选一个读 |

### Q18：Redis 和数据库的双写一致性怎么保证？

**经典方案：先更新 DB，后删除缓存**

```
写：更新 DB → 删除缓存
读：查缓存 → 有返回 → 无则查 DB → 写入缓存
```

**延时双删**（要求更严格时）：
```
1. 删除缓存 → 2. 更新 DB → 3. 延迟 N ms → 4. 再次删除缓存
```

> 最终一致方案：**Canal + MQ** 监听 MySQL binlog，异步更新/删除缓存。

### Q19：缓存淘汰策略 LRU 和 LFU 的区别？

**LRU（Least Recently Used）**：最近最少使用，看"最近一次访问时间"。一个 key 被访问后就排到队头，淘汰队尾。

**LFU（Least Frequently Used）**：最不经常使用，看"访问频率"。即使最近访问过，如果历史访问频率低也会被淘汰。

> LRU 的问题：一个冷门 key 被偶尔访问一次就排到队头，真正热 key 反而被淘汰。LFU 解决这个问题。
