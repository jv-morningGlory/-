# JVM 基本知识

## 一、JVM 内存结构

```
JVM 内存结构
├── 线程共享区
│   ├── 堆 (Heap)
│   └── 方法区 (Method Area)
│
└── 线程私有区
    ├── 虚拟机栈 (JVM Stack)
    ├── 本地方法栈 (Native Stack)
    └── 程序计数器 (PC Register)
```

### 堆 (Heap)

虚拟机管理内存最大的一块，存储所有的**对象实例和数组**（通过 `new` 创建的对象）。

| 区域 | 说明 |
|------|------|
| **新生代 (Young Gen)** | Eden + Survivor×2，频发 Minor GC，使用复制算法 |
| **老年代 (Old Gen)** | 对象年龄达到阈值后晋升，通过 Major GC 清理 |

> 新生代复制算法：Eden + From 存活对象复制到 To，对象年龄+1，From 与 To 互换。

### 方法区 (Method Area)

- 存储已被虚拟机加载的**类信息**（类名、方法、字段）、运行时常量池
- JDK 7 之前：**永久代 (PermGen)**，在堆中
- JDK 8+：**元空间 (Metaspace)**，使用本地内存

> 未加载的类只是存在于类路径中，直到 JVM 执行类加载时才加载到内存。

### 程序计数器

每个线程独有，存储当前线程正在执行的 JVM 字节码指令地址。线程切换后可恢复到正确执行位置。

### 虚拟机栈

每个线程独有，存储方法调用的**栈帧**（局部变量表、操作数栈、方法出口等）。

### 本地方法栈

为 Native 方法（C/C++ 代码）提供栈空间。

---

## 二、类加载机制

### 2.1 加载过程

| 阶段 | 子阶段 | 说明 |
|------|--------|------|
| **加载** | — | 查找并加载类的二进制数据（class 文件） |
| **链接** | 验证 | 确保 class 文件正确性 |
| | 准备 | 为静态变量分配内存并初始化为默认值 |
| | 解析 | 符号引用 → 直接引用（常量池中的类、接口、字段、方法） |
| **初始化** | — | 为静态变量赋程序指定的值，执行 `<clinit>()` 方法 |

- **符号引用**：描述类/接口/方法的字面量
- **直接引用**：类/接口/方法在内存中的实际位置

`<clinit>()` 包含所有类变量的赋值动作和静态语句块，收集顺序由源码中出现的顺序决定。

```java
public class Singleton {
    private static int y;
    private static Singleton instance = new Singleton(); // ②
    private static int x = 0;

    private Singleton() {
        x++;
        y++;
    }

    public static void main(String[] args) {
        Singleton singleton = Singleton.getInstance();
        System.out.println(singleton.x); // 0
        System.out.println(singleton.y); // 1
    }
}
```

> 执行结果 x=0, y=1，因为 `<clinit>()` 中按顺序：y 默认 0 → instance 构造（x=1, y=1）→ x 被显式赋值为 0。

### 2.2 三大类加载器

| 加载器 | 加载范围 |
|--------|---------|
| **Bootstrap ClassLoader**（根加载器） | `JAVA_HOME/lib/*.jar`（如 rt.jar） |
| **Ext ClassLoader**（扩展加载器） | `JAVA_HOME/jre/lib/ext/` 下的类库 |
| **App ClassLoader**（系统加载器） | classpath 下的类库（含第三方 jar） |

### 2.3 双亲委派机制

**什么是双亲委派**：类加载器收到 loadClass 请求后，不直接加载，而是先委托父加载器尝试加载，直到最顶层，再依次向下加载。

```
代码逻辑：parent != null → parent.loadClass()
         parent == null → Bootstrap ClassLoader 加载
```

**设计目的**：

| 目的 | 说明 |
|------|------|
| 防止核心 API 被篡改 | `java.lang.String` 由 Bootstrap 优先加载，防止恶意冒充 |
| 保证类只加载一次 | 从上往下委派，同一类不会被重复加载 |

### 2.4 破坏双亲委派

**SPI 场景**：`DriverManager` 由 Java 提供（Bootstrap 加载），但第三方 `Driver` 实现需要 AppClassLoader 加载。

解决方案：`DriverManager` 静态模块中通过 `Thread.currentThread().getContextClassLoader()` 获取 AppClassLoader。

> Launcher 初始化时将 AppClassLoader 保存到线程上下文，创建新线程时会复制父线程的上下文类加载器。

---

## 三、垃圾回收

### 3.1 判断垃圾

| 方法 | 原理 | 缺点 |
|------|------|------|
| **引用计数法** | 对象被引用时+1，失效时-1，为0可回收 | 无法解决循环依赖（已弃用） |
| **可达性分析** | 从 GC Roots 出发，不可达的对象可回收 | — |

### 3.2 垃圾回收算法

| 算法 | 过程 | 缺点 |
|------|------|------|
| **复制算法** | 内存分两块，一块用完后将存活对象复制到另一块 | 内存利用率低（50%） |
| **标记-清除** | 标记需回收对象，统一清除 | 效率不高；产生内存碎片 |
| **标记-整理** | 标记存活对象，移动到一端，清除边界外 | 移动成本（但存活率高时移动少） |

### 3.3 分代回收策略

| 区域 | 特点 | 适用算法 |
|------|------|----------|
| 新生代 | 存活率低 | 复制算法 |
| 老年代 | 存活率高 | 标记-清除 / 标记-整理 |

### 3.4 垃圾回收器

| 版本 | 默认收集器 |
|------|-----------|
| JDK 8 | Parallel GC（Parallel Scavenge + Parallel Old） |
| JDK 9+ | **G1** |

**G1 特点**：将堆划分为大小相等的 Region（1MB~32MB），不再强制连续。

| Region 类型 | 用途 |
|-------------|------|
| Eden Region | 新对象分配 |
| Survivor Region | 年轻代存活对象复制 |
| Old Region | 长期存活对象 |
| Humongous Region | 巨型对象（超过 Region 一半大小） |

核心优势：可局部收集，只回收部分 Region，不暂停整个堆。

### 3.5 GC 类型

| GC 类型 | 作用区域 | 触发条件 |
|---------|---------|---------|
| **Minor GC** | 新生代 | Eden 满 |
| **Major GC** | 老年代 | 老年代空间不足 |
| **Full GC** | 整个堆 | Minor GC 后老年代空间不足存放晋升对象 |

---

## 四、四种引用类型

| 引用类型 | 实现类 | 回收时机 | 影响生命周期 |
|---------|--------|---------|------------|
| **强引用** | 默认 | 从不回收（除非无强引用） | 是 |
| **软引用** | `SoftReference` | 内存不足时 | 是（直到 OOM） |
| **弱引用** | `WeakReference` | 下一次 GC 时 | 否 |
| **虚引用** | `PhantomReference` | 对象被回收后加入引用队列 | 否 |

**应用场景**：
- 软引用：缓存（内存不足自动释放）
- 弱引用：`WeakHashMap`、`ThreadLocal`
- 虚引用：管理直接内存（NIO Buffer 回收追踪）

---

## 五、JDK 诊断工具

### 5.1 jps — 查看 Java 进程

```bash
jps -lvm
```

| 选项 | 说明 |
|------|------|
| `-m` | 输出传递给 main 方法的参数 |
| `-l` | 输出主类的完整包名或 jar 路径 |
| `-v` | 输出传递给 JVM 的参数 |

### 5.2 jmap — 堆快照分析

```bash
# 查看堆配置与使用情况
jmap -heap <pid>

# 生成 Heap Dump
jmap -dump:format=b,file=heapdump.hprof <pid>
```

> 使用 MAT（Memory Analyzer）分析 dump 文件。

### 5.3 jstat — GC 监控

```bash
jstat -gcutil <pid>
```

输出示例与解读：

| 指标 | 含义 | 正常范围 |
|------|------|---------|
| S0/S1 | Survivor 区使用率 | 0%-50% |
| E | Eden 区使用率 | 70%-90% 需注意 |
| O | 老年代使用率 | <70% |
| M | 元空间使用率 | <80% |
| YGC | Young GC 次数 | — |
| FGC | Full GC 次数 | 0（频繁则异常） |
| GCT | GC 总耗时 | <5% 运行时间 |

> jstat 只能实时查看，如需保留日志需启动时添加 GC 日志参数。

### 5.4 jstack — 线程快照

```bash
# 基本用法
jstack <pid> > thread_dump.txt

# 包含锁信息（推荐）
jstack -l <pid> > thread_dump_with_locks.txt
```

---

## 六、JVM 参数

### 6.1 参数分类

| 类型 | 来源 | 示例 |
|------|------|------|
| **环境参数** | 操作系统 | `JAVA_HOME` |
| **JVM 参数** | `-X` / `-XX` | `-Xmx2g`, `-XX:+UseG1GC` |
| **系统参数** | `-D` 定义 | `-Dfile.encoding=UTF-8` |
| **Spring Boot 参数** | `--` 前缀 | `--server.port=9090` |

```bash
java -Xmx2g -XX:+UseG1GC \
     -Dfile.encoding=UTF-8 \
     -jar myapp.jar \
     --server.port=9090
```

### 6.2 常用调优参数

```bash
java -Xms4g -Xmx4g \
     -Xss1m \
     -XX:MetaspaceSize=256m \
     -XX:MaxMetaspaceSize=512m \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:InitiatingHeapOccupancyPercent=45 \
     -XX:+PrintGCDetails \
     -XX:+PrintGCDateStamps \
     -Xloggc:/path/to/gc.log \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/path/to/heapdump.hprof \
     -jar your-application.jar
```

| 参数分类 | 参数 | 说明 |
|---------|------|------|
| 堆内存 | `-Xms` | 初始堆大小（memory start） |
| | `-Xmx` | 最大堆大小（memory max） |
| 线程栈 | `-Xss` | 线程栈大小（stack size） |
| 元空间 | `-XX:MetaspaceSize` | 元空间初始大小 |
| | `-XX:MaxMetaspaceSize` | 元空间最大大小 |
| GC | `-XX:+UseG1GC` | 使用 G1 收集器 |
| | `-XX:MaxGCPauseMillis` | 目标最大停顿时间 |
| | `-XX:InitiatingHeapOccupancyPercent` | 触发 Mixed GC 的堆占用阈值 |
| 诊断 | `-XX:+HeapDumpOnOutOfMemoryError` | OOM 时自动生成堆转储 |
| | `-Xloggc:/path/to/gc.log` | GC 日志输出路径 |

---

## 七、常见面试题

### 7.1 内存泄漏 vs 内存溢出

| 概念 | 定义 |
|------|------|
| **内存泄漏** | 申请内存后，无法释放已不再使用的空间 |
| **内存溢出 (OOM)** | 申请内存时没有足够空间可供分配 |

**常见内存泄漏场景**：
1. 静态集合类长期持有对象引用（`static List` 不断添加不移除）
2. 未关闭的资源（Connection、InputStream 未调 `close()`）
3. 内部类持有外部类隐式引用（非静态内部类被长期引用导致外部类无法回收）

### 7.2 GC 问题排查流程

**现象**：线上 CPU 飙升、响应变慢，`top` 显示 Load Average 15（4核机器），Java 进程 CPU 382.6%。

**排查步骤**：

1. **`top -Hp <pid>`** — 定位高 CPU 线程
   ```
   GC task thread#0 (ParallelGC)  → 78.6% CPU
   GC task thread#1 (ParallelGC)  → 78.2% CPU
   ```
   → GC 线程大量消耗 CPU，业务线程几乎挂起

2. **`jstat -gcutil <pid> 1000`** — 实时观察 GC 状态
   ```
   O = 99.8%（老年代几乎满）
   FGC 每秒+1（Full GC 频繁）
   FGC 后 O 几乎不下降（仅释放 0.02%）
   E = 100%（Young GC 无法正常工作）
   ```
   → **频繁且无效的 Full GC**，内存泄漏典型特征

3. **`jmap -dump`** — 生成堆转储，用 MAT 分析定位泄漏点

> 总结口诀：top 看整体 → top -Hp 定位线程 → jstat 取 GC 证据 → jmap 拿 dump 分析。

---

## 八、JDK 开发工具速览

| 工具 | 作用 |
|------|------|
| `javac` | 编译 `.java` → `.class` |
| `java` | 运行 Java 程序 |
| `jdb` | 调试器 |
| `javadoc` | 生成 API 文档 |
| `jar` | 打包 class 文件和资源 |
| `jps` | 查看 Java 进程 PID |
| `jmap` | 堆内存分析 / dump |
| `jstat` | GC 行为监控 |
| `jstack` | 线程快照 / 死锁检测 |

**核心类库**：`java.lang`（基础类）、`java.util`（工具类）、`java.io`（I/O）等。
