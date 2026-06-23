# JVM 调优实战题

## 实战一：CPU 飙升 + 频繁 Full GC

**场景**

某电商订单服务，大促期间接口响应从 50ms 飙到 3s+，运维告警 CPU 使用率 100%。重启后 10 分钟内再次恶化。JVM 参数：`-Xms2g -Xmx2g -XX:+UseParallelGC`。

**排查链路**

```
第一步：定位高 CPU 进程
$ top -c                    # 找到 CPU 最高的 Java 进程 PID

第二步：找到高 CPU 线程
$ top -Hp <pid>             # 查看进程内线程 CPU 排名，记下最高线程 tid

第三步：线程 ID 转 16 进制
$ printf "%x\n" <tid>       # tid 转 16 进制，如 12345 → 0x3039

第四步：看线程在干什么
$ jstack <pid> | grep -A 30 '0x3039'   # 发现大量线程在执行 GC 相关操作
                                         # 或发现某个业务线程在死循环

第五步：确认 GC 状态
$ jstat -gc <pid> 1000 10   # 每秒打印 GC 情况
  S0C    S1C    S0U    S1U      EC       EU        OC         OU       MC     MU
  0.0    0.0    0.0    0.0   163840.0  163840.0  1638400.0  1638400.0  48640.0 45200.0
  ↑ 新生代打满、老年代也满了 → 频繁 Full GC

第六步：dump 堆内存分析
$ jmap -dump:live,format=b,file=heap.hprof <pid>
$ jhat heap.hprof          # 或用 MAT / JProfiler 分析

第七步：定位大对象
# MAT Histogram 排序 → 发现 byte[] 占 1.5GB
# 追溯引用链 → 原来是大促实时计算缓存，HashMap 缓存了几百万条明细未过期
```

| 阶段 | 工具 | 关键发现 |
|------|------|---------|
| CPU 定位 | `top` / `top -Hp` | 哪个进程/线程吃 CPU |
| 线程状态 | `jstack` | 线程在 GC 还是业务循环 |
| GC 频率 | `jstat -gc` | 老年代持续满 → Full GC 触发 |
| 对象分布 | `jmap` + MAT | `byte[]` / HashMap 占满老年代 |

**根因**：实时计算缓存用 `HashMap` 无过期策略，大促流量下条目数暴增 → 老年代打满 → Full GC 频繁 → STW 线程跑满 CPU。

**解决**

```java
// 改前：无界缓存
static Map<Long, Detail> cache = new HashMap<>();

// 改后：Guava Cache，自动过期 + 最大条数限制
static Cache<Long, Detail> cache = CacheBuilder.newBuilder()
    .maximumSize(10000)
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build();
```

同时扩大堆：`-Xms4g -Xmx4g -XX:+UseG1GC`（G1 在大堆下停顿更可控）。

---

## 实战二：老年代缓慢增长 → OOM（ThreadLocal 内存泄漏）

**场景**

某 SaaS 后台管理系统，部署到 Tomcat 后运行 3~5 天就自动挂掉。日志报 `java.lang.OutOfMemoryError: Java heap space`。运维重启后又撑几天，反复循环。堆设了 `-Xmx4g`，日常访问量不大。

**排查链路**

```
第一步：看进程是否还活着
$ jps -l
  12345 org.apache.catalina.startup.Bootstrap

第二步：观察 GC 趋势（关键一步！）
$ jstat -gc <pid> 60000   # 每分钟打印一次，观察 10 分钟
  时间   S0C    S1C    ...    OC         OU       YGC   YGCT    FGC   FGCT
  T+0    ...             614400.0   428000.0   1234   45.2     3    1.8
  T+60   ...             614400.0   432000.0   1235   45.3     3    1.8
  T+120  ...             614400.0   438000.0   1236   45.4     3    1.8
  T+180  ...             614400.0   444000.0   1237   45.4     3    1.8
  ↑ 老年代在持续增长！Minor GC 无法回收 → 对象偷偷晋升到老年代且不被回收

第三步：OOM 时自动 dump（需提前加好参数）
# -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/oom.hprof

第四步：MAT 分析 dump
# 1. Leak Suspects → ThreadLocal → 持有大量 HashMap.Entry
# 2. dominator tree → 某个 ThreadLocal 的 value 累积了 1.5GB
# 3. 路径追溯到 Thread → 发现是 Tomcat worker 线程池的线程
```

| 排查步骤 | 工具 | 观察点 |
|---------|------|--------|
| 趋势确认 | `jstat -gc` 持续观察 | OU 是否持续增长（不回落） |
| 自动 dump | `-XX:+HeapDumpOnOutOfMemoryError` | OOM 瞬间的堆快照 |
| 泄漏分析 | MAT Leak Suspects | 哪类对象占最大 → 追溯 GC Root 路径 |
| 定位来源 | 引用链 + 线程名 | 哪个线程/线程池持有泄漏对象 |

**根因**：线程池中使用 `ThreadLocal` 但未调 `remove()`。Tomcat worker 线程被线程池复用，下次请求复用时 ThreadLocal 旧值未被清除。

```java
// 问题代码
public class UserContext {
    private static ThreadLocal<Map<String, Object>> holder = new ThreadLocal<>();
    
    public static void set(Map<String, Object> data) {
        holder.set(data);  // 请求结束没有 remove
    }
}
```

**解决**

```java
// Filter 中 finally 清理
@WebFilter("/*")
public class UserContextFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) {
        try {
            chain.doFilter(req, resp);
        } finally {
            UserContext.clear();  // ThreadLocal.remove()
        }
    }
}
```

---

## 实战三：元空间 OOM（Metaspace 打满）

**场景**

某规则引擎服务，支持运维动态上传 Groovy 脚本实现业务规则热更新。运行一段时间后日志出现 `java.lang.OutOfMemoryError: Metaspace`。堆才用了 60%，但 Metaspace 被打满。

**排查链路**

```
第一步：看 GC 日志确认问题区域
$ jstat -gc <pid> 5000
  S0C    S1C    ...    OC         OU       MC         MU      YGC   FGC
  0.0    ...      204800.0  120000.0   262144.0  261800.0  234   45
  ↑ OU 才 60%，但 MU 接近 MC → Metaspace 快满了

第二步：看类加载器数量
$ jmap -clstats <pid>
  Index  Super  ClassLoader      Class Count
  1      null   BootStrap        3271
  2      1      AppClassLoader   8932
  3      2      GroovyClassLoader$InnerLoader  521    ← 大量 Groovy 类加载器！
  4      2      GroovyClassLoader$InnerLoader  487
  ...    ...    ...              ...
  Total  428 个 ClassLoader       → 远超正常水平（通常 10~30 个）

第三步：看类的加载/卸载趋势
$ jstat -class <pid> 5000
  Loaded   Bytes      Unloaded  Bytes   Time
  32541    48200.0    0         0.0     34.2    ← Unloaded = 0，类只增不减！

第四步：堆 dump 看谁在引用 ClassLoader
$ jmap -dump:live,format=b,file=meta.hprof <pid>
# MAT → GC Roots → 发现 GroovyShell 实例持有 ClassLoader 引用，从未释放
```

| 排查步骤 | 工具 | 关键指标 |
|---------|------|---------|
| 区域定位 | `jstat -gc` | MC/MU 接近上限 |
| 类加载器 | `jmap -clstats` | ClassLoader 数量异常多 |
| 类趋势 | `jstat -class` | Loaded 持续涨，Unloaded 不涨 |
| 引用链 | `jmap` + MAT | 谁阻止了 ClassLoader 被 GC |

**根因**：每次执行脚本都 `new GroovyShell().parse(script)`，每次生成新 ClassLoader 加载新类。Groovy 生成的类无法被卸载 → Metaspace 持续增长。

```java
// 问题代码
@Component
public class RuleExecutor {
    public Object execute(String groovyScript) {
        GroovyShell shell = new GroovyShell();  // 每次 new
        Script script = shell.parse(groovyScript);
        return script.run();
    }
}
```

**为什么类无法卸载**：`GroovyShell` 内部有自己的 `GroovyClassLoader`，`parse()` 会把类加载到 Metaspace。如果有对象引用该类或其 ClassLoader，GC 就无法卸载这些类。

**解决**

```java
// 方案一：缓存 GroovyShell，复用 ClassLoader（同一脚本内容用同一实例）
private static Map<String, GroovyShell> cache = new ConcurrentHashMap<>();

public Object execute(String groovyScript) {
    GroovyShell shell = cache.computeIfAbsent(
        groovyScript, k -> new GroovyShell()
    );
    return shell.evaluate(groovyScript);
}

// 方案二：如果脚本频繁变化，主动清理 + 加大 Metaspace
// -XX:MaxMetaspaceSize=512m
```

---

## 排查工具速查表

| 症状 | 优先工具 | 看什么 |
|------|---------|--------|
| CPU 飙高 | `top -Hp` → `jstack` | 线程在 GC？还是业务死循环？ |
| 响应变慢 | `jstat -gc` | FGC 频率、堆占用趋势 |
| OOM heap space | `jmap dump` + MAT | Leak Suspects → GC Root 引用链 |
| OOM Metaspace | `jmap -clstats` | ClassLoader 数量、类加载趋势 |
| 线程死锁 | `jstack` | `Found 1 deadlock` 输出 |
| GC 频繁 | `jstat -gc` + GC 日志 | 哪些 Region/分代变化剧烈 |

> **核心思路**：先看趋势（`jstat`）→ 再抓快照（`jmap` / `jstack`）→ 最后分析（MAT / GC 日志）。不要上来就 dump——先观察趋势确认方向。
