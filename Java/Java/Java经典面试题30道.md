# Java 经典面试题 30 道

> 来源:牛客网高频面经整理。覆盖 **Java 基础、集合、并发、JVM** 四大块。
> 答题原则:先给结论 → 再讲原理 → 最后给实战判断标准。

---

## 一、Java 基础

### 1. == 和 equals 的区别是什么?

- `==`:判断两个对象的**内存地址**是否相等。基本类型比较**值**,引用类型比较**地址**。
- `equals`:默认行为同 `==`(继承自 Object),但通常被**重写**为比较**内容**是否相等。

> **实战判断**:比较基本类型或判断是否同一对象用 `==`;比较两个对象"内容是否一致"必须用重写后的 `equals`。
> **踩坑**:`Integer` 在 `[-128, 127]` 有缓存,`==` 比较可能为 true,超出范围为 false —— 永远用 `equals` 比较 Integer。

### 2. String、StringBuilder、StringBuffer 的区别?

| 对比项 | String | StringBuilder | StringBuffer |
|--------|--------|---------------|--------------|
| 可变性 | 不可变(`final char[]`) | 可变 | 可变 |
| 线程安全 | 安全(不可变) | **不安全** | 安全(`synchronized`) |
| 性能 | 拼接慢(新建对象) | 最快 | 较快 |

> **选择标准**:少量拼接用 `String`;单线程大量拼接用 `StringBuilder`;多线程共享用 `StringBuffer`。
> 实际开发中 `+` 编译期会优化为 StringBuilder,但循环内 `+=` 每次都会新建对象,**禁止在循环里用 String 拼接**。

### 3. Java 的异常体系说一下

```
Throwable
├── Error          // 系统级错误,程序无法处理(OOM、StackOverflow)
└── Exception
    ├── RuntimeException(运行时异常,非受检)  // NPE、ClassCast、IndexOutOfBounds
    └── 其他 Exception(检查异常,受检)        // IOException、SQLException
```

> **判断标准**:编译器强制 try-catch 或 throws 的是**检查异常**;运行期才暴露的是**运行时异常**。
> **处理动作**:受检异常必须处理;运行时异常优先**修复代码逻辑**,而非捕获后吞掉。

### 4. 重载(Overload)与重写(Override)的区别?

| 对比项 | 重载 | 重写 |
|--------|------|------|
| 发生位置 | 同一个类 | 父子类之间 |
| 方法名 | 相同 | 相同 |
| 参数列表 | **必须不同** | **必须相同** |
| 返回值 | 无要求 | 相同或子类型(协变返回) |
| 访问权限 | 无限制 | 不能比父类更严格 |
| 多态 | 编译时(静态分派) | 运行时(动态绑定) |

### 5. 接口和抽象类的区别?

| 对比项 | 接口(interface) | 抽象类(abstract class) |
|--------|-----------------|------------------------|
| 关键字 | `implements` | `extends` |
| 多继承 | 一个类可实现**多个** | 只能继承**一个** |
| 方法 | 默认 public abstract(JDK8+ 支持 default/static 方法体) | 可有普通方法实现 |
| 字段 | 只能是 public static final 常量 | 可有任意成员变量 |
| 设计语义 | **行为契约**(能做什么) | **模板复用**(是什么) |

> **选择标准**:定义跨类型族的公共行为用接口(如 `Comparable`);一组相关类共享代码和字段用抽象类。

### 6. 深拷贝与浅拷贝的区别?

- **浅拷贝**:复制对象本身,但**引用类型字段仍指向同一对象**(改一个影响另一个)。
- **深拷贝**:引用类型字段也**新建独立对象**,彻底互不影响。

```java
// 浅拷贝:实现 Cloneable + super.clone()
// 深拷贝:对每个引用字段再次 clone,或序列化/反序列化
```

> **实战动作**:POJO 链表、嵌套对象拷贝必须用深拷贝;浅拷贝会引发"改副本影响原始数据"的隐蔽 bug。

### 7. final 关键字的作用?

| 修饰对象 | 作用 |
|----------|------|
| 类 | 不能被继承(如 `String`) |
| 方法 | 不能被重写 |
| 变量(基本类型) | 值不可变(常量) |
| 变量(引用类型) | 引用不可变,但**对象内容可变** |

> **踩坑**:`final List list = new ArrayList();` 仍可 `list.add()`,final 锁的是引用不是对象。

### 8. Java 面向对象的三大特性?

- **封装**:私有化属性,提供 getter/setter,隐藏内部实现。
- **继承**:子类复用父类的属性和方法,单继承(`extends`)。
- **多态**:同一方法调用,因对象不同而表现不同行为(重写 + 父类引用指向子类对象)。

> **多态的三个前提**:继承、重写、**父类引用指向子类对象**(`Animal a = new Dog();`)。

---

## 二、Java 集合

### 9. Java 集合框架的整体结构?

```
Collection
├── List(有序、可重复): ArrayList, LinkedList, Vector
├── Set(无序、不重复): HashSet, LinkedHashSet, TreeSet
└── Queue(队列): ArrayDeque, PriorityQueue, LinkedList
Map(键值对): HashMap, LinkedHashMap, TreeMap, ConcurrentHashMap, Hashtable
```

> **判断标准**:需要键值映射选 Map;元素唯一选 Set;有序可重复选 List;先进先出选 Queue。

### 10. ArrayList 和 LinkedList 的区别?

| 对比项 | ArrayList | LinkedList |
|--------|-----------|------------|
| 底层结构 | **Object 数组** | **双向链表** |
| 随机访问 | O(1),快 | O(n),慢 |
| 增删(中间) | 需挪动元素,慢 | 改指针,快 |
| 内存 | 连续,占用小 | 每节点存前后指针,占用大 |
| 线程安全 | 不安全 | 不安全 |

> **实战选择**:99% 场景用 ArrayList(查询多)。即使频繁增删在尾部,ArrayList 仍更快。LinkedList 真正优势仅在**频繁头部插入**。

### 11. ArrayList 的扩容机制?

1. 无参构造初始化为**空数组**(JDK7 是直接分配 10,有内存浪费)。
2. 第一次 `add` 时扩容到 **10**。
3. 后续每次扩容为原来的 **1.5 倍**(`oldCapacity + (oldCapacity >> 1)`)。

```java
// 预知数据量时,提前指定容量避免多次扩容
List<Integer> list = new ArrayList<>(1000);
```

### 12. HashMap 的底层实现原理?

- **JDK 1.7**:数组 + 链表,**头插法**(多线程扩容可能形成**环形链表**,死循环)。
- **JDK 1.8**:数组 + 链表 + **红黑树**,**尾插法**。
  - 链表长度 > 8 **且** 数组长度 ≥ 64 时,链表转红黑树(查找从 O(n) → O(log n))。
  - 红黑树节点 ≤ 6 时退化回链表。

> **核心参数**:初始容量 16、负载因子 0.75、扩容阈值 = 容量 × 负载因子。

### 13. HashMap 扩容机制 + 为什么容量必须是 2 的幂次?

**扩容时机**:元素总数 > 阈值(容量 × 0.75)时,**容量翻倍**(×2)。

**为什么是 2 的幂次**:计算下标用 `(n - 1) & hash`,当 n 是 2 的幂时,`(n-1) & hash` 等价于 `hash % n`,**位运算比取模快**;同时让元素分布更均匀。

```java
// hash 扰动函数:高 16 位异或低 16 位,让 hash 高低位都参与下标运算,减少冲突
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

### 14. HashMap 为什么是线程不安全的?

- **JDK 1.7**:多线程扩容 → 环形链表 / 数据丢失。
- **JDK 1.8**:多线程 put → **数据覆盖**(两个线程同时判断槽位为空,后写的覆盖先写的)。
- **迭代器 fail-fast**:迭代期间结构被修改抛 `ConcurrentModificationException`。

> **实战动作**:多线程场景**必须**用 `ConcurrentHashMap`,不要加锁包 HashMap。

### 15. ConcurrentHashMap 为什么是线程安全的?

- **JDK 1.7**:`Segment` 分段锁,每段独立加锁,并发度 = 段数(默认 16)。
- **JDK 1.8**:摒弃分段锁,**Node 数组 + CAS + synchronized**。
  - 桶为空:用 **CAS** 写入。
  - 桶非空:用 `synchronized` 锁住**链表/红黑树的头节点**(锁粒度从段细化到桶)。

> **改进关键**:锁粒度从 Segment(段)→ 桶节点,并发度大幅提升;不再有 Segment 层,内存更省。

---

## 三、Java 并发

### 16. Java 创建线程的方式有哪些?

1. 继承 `Thread` 类,重写 `run()`。
2. 实现 `Runnable` 接口(无返回值)。
3. 实现 `Callable` 接口 + `FutureTask`(**有返回值、可抛异常**)。
4. 使用**线程池** `ExecutorService`(生产环境推荐)。

> **实战动作**:`Executors.newXxx()` 有 OOM 风险(无界队列/无上限线程),生产环境**必须**用 `ThreadPoolExecutor` 手动构造。

### 17. 线程的生命周期有哪几种状态?

```
NEW(新建)
  │ start()
  ▼
RUNNABLE(就绪 + 运行)
  ├─ wait()        → WAITING
  ├─ wait(ms)/sleep(ms) → TIMED_WAITING
  ├─ 锁竞争失败     → BLOCKED
  └─ run() 结束    → TERMINATED
```

> **区分**:`BLOCKED` 是等**锁**;`WAITING` 是等**通知**(`wait`/`join`/`LockSupport.park`)。

### 18. sleep() 和 wait() 的区别?

| 对比项 | sleep | wait |
|--------|-------|------|
| 所属类 | `Thread` | `Object` |
| 是否释放锁 | **不释放** | **释放锁** |
| 使用位置 | 任意 | 必须在 `synchronized` 块内 |
| 唤醒方式 | 超时自动 | `notify`/`notifyAll` 或超时 |
| 进入状态 | TIMED_WAITING | WAITING(无参)/TIMED_WAITING |

> **为什么 wait 在 Object**:锁是基于对象的 monitor,任意对象都能当锁,所以 wait/notify 必须在 Object 上。

### 19. synchronized 和 ReentrantLock 的区别?

| 对比项 | synchronized | ReentrantLock |
|--------|--------------|---------------|
| 层级 | JVM 关键字 | API 层类(Lock 接口实现) |
| 锁释放 | 自动(出代码块) | **手动** `unlock()`,须放 finally |
| 可中断 | 不可 | `lockInterruptibly()` 可 |
| 公平锁 | 非公平 | 可配置公平/非公平 |
| 条件变量 | 1 个(wait/notify) | 多个 `Condition` |
| 尝试获取 | 不支持 | `tryLock()` 支持 |

> **选择标准**:简单同步用 synchronized(自动释放、JVM 优化后性能足够);需要可中断、超时、多条件等高级功能用 ReentrantLock。

### 20. synchronized 的锁升级过程?

JDK 1.6 后为降低锁开销引入锁升级(基于对象头 Mark Word):

```
无锁 → 偏向锁 → 轻量级锁 → 重量级锁(不可逆)
```

| 锁状态 | 适用场景 | 原理 |
|--------|----------|------|
| **偏向锁** | 单线程重复获取 | Mark Word 记录线程 ID,下次无需 CAS |
| **轻量级锁** | 多线程交替,无竞争 | CAS + **自旋**等待 |
| **重量级锁** | 竞争激烈 | 依赖 OS **Monitor**,线程阻塞(内核态切换) |

> **升级触发**:出现第二个线程竞争 → 撤销偏向锁升级轻量级;自旋超时或第三个线程竞争 → 膨胀为重量级。

### 21. volatile 的作用和原理?

- **保证可见性**:写后立即刷回主内存,读时强制从主内存读(基于**内存屏障**)。
- **禁止指令重排序**(如单例的双重检查锁)。
- **不保证原子性**(`i++` 仍不安全)。

> **对比 synchronized**:volatile 是轻量级同步,只保证可见性;`synchronized` 保证可见性 + 原子性 + 有序性。

```java
// 双重检查单例(DCL):volatile 防止 new 对象时的指令重排
private static volatile Singleton instance;
```

### 22. CAS 是什么?有什么问题?

**CAS(Compare And Swap)**:三个操作数 —— 内存值 V、预期值 A、新值 B。当 `V == A` 时才把 V 更新为 B,否则重试(**自旋**)。底层是 CPU 的 `cmpxchg` 指令,保证原子性。

**三大问题**:

| 问题 | 说明 | 解决方案 |
|------|------|----------|
| ABA | 值从 A→B→A,CAS 误以为没变过 | 加**版本号**(`AtomicStampedReference`) |
| 自旋开销大 | 长时间不成功则空耗 CPU | 限制自旋次数 |
| 只能保证一个变量 | 多变量原子操作无法保证 | `AtomicReference` 封装对象 |

### 23. AQS 是什么?原理是什么?

**AQS(AbstractQueuedSynchronizer)**:并发同步框架,核心是 **state 变量 + CLH FIFO 双向等待队列**。

- **state**:volatile int,表示同步状态(可重入锁的重入次数、Semaphore 的许可数)。
- **队列**:获取锁失败的线程封装为 Node 入队阻塞,前驱释放后唤醒后继。

| 共享模式 | 含义 | 代表实现 |
|----------|------|----------|
| **独占式** | 同一时刻一个线程持有 | ReentrantLock |
| **共享式** | 多个线程同时获取 | Semaphore、CountDownLatch、CyclicBarrier |

> **实战**:子类通过重写 `tryAcquire/tryRelease` 等模板方法来定义获取/释放逻辑,排队阻塞由 AQS 顶层完成。

### 24. 线程池的七大核心参数 + 执行流程?

```java
new ThreadPoolExecutor(
    int corePoolSize,        // 1.核心线程数
    int maximumPoolSize,     // 2.最大线程数
    long keepAliveTime,      // 3.空闲线程存活时间
    TimeUnit unit,           // 4.时间单位
    BlockingQueue<Runnable> workQueue,  // 5.任务队列
    ThreadFactory threadFactory,        // 6.线程工厂
    RejectedExecutionHandler handler    // 7.拒绝策略
);
```

**执行流程**:

```
提交任务
  ├─ 线程数 < corePoolSize? → 创建核心线程执行
  ├─ 否 → 任务入 workQueue 排队
  ├─ 队列满 + 线程数 < max? → 创建非核心线程执行
  └─ 队列满 + 线程达 max? → 触发拒绝策略
```

**四种拒绝策略**:

| 策略 | 行为 |
|------|------|
| `AbortPolicy`(默认) | 丢弃任务 + 抛 `RejectedExecutionException` |
| `CallerRunsPolicy` | 由提交任务的线程自己执行(降级,不丢任务) |
| `DiscardPolicy` | 静默丢弃新任务 |
| `DiscardOldestPolicy` | 丢弃队列最老的任务,重试新任务 |

> **参数配置经验**:CPU 密集型 → 核心线程数 ≈ CPU 核数(`N+1`);IO 密集型 → 核心线程数 ≈ `2N` 或更大。

### 25. ThreadLocal 原理 + 内存泄漏问题?

**原理**:每个 `Thread` 内部持有一个 `ThreadLocalMap`,key 是 ThreadLocal 的**弱引用**,value 是业务值(强引用)。每个线程读写的是自己 map 里的副本,**线程隔离**。

```java
// 数据结构
Thread → ThreadLocalMap
            └─ Entry(WeakReference<ThreadLocal> key, Object value)
```

**内存泄漏原因**:key 是弱引用,GC 后 key 变 null,但 value 仍是强引用。线程池中**线程复用不销毁**,导致 key=null 的 Entry 永久残留,value 无法回收。

> **实战动作**:用完 ThreadLocal **必须**调用 `remove()` 清理,尤其在线程池场景。

> **应用场景**:数据库连接管理、用户登录上下文(Session)、链路追踪 traceId。

---

## 四、JVM 虚拟机

### 26. JVM 运行时数据区有哪些?

| 区域 | 线程共享 | 存储内容 | 会 OOM |
|------|----------|----------|--------|
| **堆(Heap)** | 共享 | 对象实例、数组 | ✅ |
| **方法区(JDK8 后元空间)** | 共享 | 类信息、常量、静态变量、JIT 代码 | ✅ |
| **虚拟机栈** | 私有 | 栈帧(局部变量表、操作数栈、动态链接) | ✅(StackOverflow / OOM) |
| **本地方法栈** | 私有 | native 方法 | ✅ |
| **程序计数器** | 私有 | 当前执行的字节码行号 | ❌(唯一不会 OOM) |

> **堆细分**:新生代(Eden : S0 : S1 = 8:1:1) + 老年代。

### 27. 如何判断对象可以回收?(GC Roots 可达性分析)

- **引用计数法**(已淘汰):循环引用无法回收。
- **可达性分析**(主流):从 **GC Roots** 向下搜索,不可达的对象视为可回收。

**可作为 GC Roots 的对象**:

1. 虚拟机栈中引用的对象(局部变量、方法参数)。
2. 方法区中**静态属性**引用的对象。
3. 方法区中**常量**引用的对象。
4. 本地方法栈中 **JNI** 引用的对象。
5. 被同步锁(`synchronized`)持有的对象。

> **对象自救**:不可达的对象若重写了 `finalize()` 且未被调用过,会被放进 F-Queue,执行 `finalize()` 时只要重新建立引用即可"复活"(**不推荐使用**,已废弃)。

### 28. 垃圾回收算法有哪些?

| 算法 | 过程 | 优点 | 缺点 |
|------|------|------|------|
| **标记-清除** | 标记存活,清除其余 | 简单 | **内存碎片** |
| **标记-复制** | 存活对象复制到另一半,清空原区 | 无碎片、快 | 浪费一半空间 |
| **标记-整理** | 存活对象向一端移动,清边界外 | 无碎片、不浪费空间 | 移动成本高 |

> **分代选择**:新生代朝生夕灭 → **复制算法**;老年代存活率高 → **标记-清除/整理**。

### 29. CMS 和 G1 垃圾收集器对比?

**CMS(老年代收集器)** 四阶段:

```
初始标记(STW) → 并发标记 → 重新标记(STW) → 并发清除
```

CMS 问题:① 对 CPU 敏感;② 无法处理**浮动垃圾**;③ **标记-清除**产生碎片。

**G1(整堆收集器)** 改进:

| 对比项 | CMS | G1 |
|--------|-----|-----|
| 内存划分 | 固定分代 | 多个大小相等的 **Region** |
| 算法 | 标记-清除 | **标记-整理** + 复制(无碎片) |
| 停顿控制 | 不可控 | **可预测停顿**(`-XX:MaxGCPauseMillis`) |
| 适用堆 | 中小堆 | 大堆(6GB+) |

> **默认收集器**:JDK 8 及之前 `Parallel Scavenge + Parallel Old`;**JDK 9 起默认 G1**。

### 30. 类加载机制 + 双亲委派模型?

**类加载 7 阶段**:`加载 → 验证 → 准备 → 解析 → 初始化 → 使用 → 卸载`(前 5 个合称类加载)。

| 阶段 | 动作 |
|------|------|
| 加载 | 读取 class 字节流,生成 Class 对象 |
| 验证 | 校验字节码合法性 |
| 准备 | 为**静态变量**分配内存并赋**零值**(非用户值) |
| 解析 | 符号引用 → 直接引用 |
| 初始化 | 执行 `<clinit>()`,真正赋值静态变量 |

**三种类加载器**:`BootstrapClassLoader`(启动)→ `ExtClassLoader`(扩展)→ `AppClassLoader`(应用)。

**双亲委派**:收到加载请求时,**先委托父加载器加载**,父加载器失败才自己加载。

> **好处**:① 安全(防止用户伪造 `java.lang.String`);② 避免重复加载(保证类的唯一性)。

> **如何打破**:自定义类加载器,重写 `loadClass()`(Tomcat、SPI、JDBC 热加载都打破了双亲委派)。

---

## 附:高频考点速记表

| 模块 | 必背 TOP 题 |
|------|-------------|
| 基础 | `==` vs equals、String 不可变、异常体系 |
| 集合 | HashMap 底层/扩容/线程不安全、ConcurrentHashMap |
| 并发 | synchronized 锁升级、AQS、线程池七参数、ThreadLocal 内存泄漏 |
| JVM | 运行时数据区、GC Roots、CMS vs G1、双亲委派 |
