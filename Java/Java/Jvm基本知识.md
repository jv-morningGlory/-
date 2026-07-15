# JVM 基本知识

## JDK / JRE / JVM 的关系

**包含关系**：JDK ⊃ JRE ⊃ JVM

```text
JDK（Java Development Kit，开发工具包）
├── JRE（Java Runtime Environment，运行环境）
│   ├── JVM（Java Virtual Machine）— 执行 .class 字节码的核心
│   └── 核心类库（rt.jar / java.base 等标准 API）
└── 开发工具（javac、javadoc、jps、jstack 等）
```

| 概念 | 全称 | 作用 | 谁需要 |
|------|------|------|--------|
| **JDK** | Java Development Kit | 开发 + 运行 Java 程序 | 开发者 |
| **JRE** | Java Runtime Environment | 运行 Java 程序 | 仅运行程序的用户 |
| **JVM** | Java Virtual Machine | 执行字节码，跨平台的核心 | — |

**关键认知**：

- JVM 只认 `.class` 字节码，不关心源码；**跨平台靠的是 JVM**——同一份字节码能在不同系统的 JVM 上跑（"一次编写，到处运行"）
- **JDK 是 JRE 的超集**：装了 JDK 即含 JRE，能开发也能运行
- JDK 11+ 起不再单独发布 JRE（模块化后用 `jlink` 按需生成运行镜像）

> 一句话：**JDK = JRE + 开发工具；JRE = JVM + 核心类库；JVM 是执行字节码的核心。**

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

**典型场景：JDBC SPI**

| 类 | 由谁加载 | 位置 |
|------|------|------|
| `java.sql.DriverManager` | **Bootstrap**（最顶层） | `JAVA_HOME/lib` 核心类 |
| `com.mysql.cj.jdbc.Driver` | **AppClassLoader** | classpath 下第三方 jar |

**为什么必然加载失败（推理链）**

```
DriverManager 中引用了 Driver
      ↓
按默认规则：Driver 的加载起点 = DriverManager 的加载器 = Bootstrap
      ↓
Bootstrap 是顶层，没有父加载器可委派，只能自己找
      ↓
Bootstrap 的搜索范围只有 JDK 核心库 → 找不到 mysql.jar 里的 Driver
      ↓
ClassNotFoundException → driver 加载失败 ✗
```

> 按默认规则 driver 真的加载不了——**这个必然失败，就是必须破坏双亲委派的根本原因**。

**补充**：DriverManager 不是 `new Driver()`，而是走 SPI（ServiceLoader）+ TCCL 自动发现驱动，这才是它能绕开上面这条失败链的原因。

**解决方案：线程上下文类加载器（TCCL）**

`Thread.currentThread().getContextClassLoader()` 返回一个运行时注入的类加载器，`DriverManager` 通过它拿到 AppClassLoader 去"借力"加载第三方 Driver。

> 双亲委派走不通的路，靠 TCCL 这个"绕道"走通。

**TCCL 从哪来：Launcher 初始化**

- JVM 启动时 `sun.misc.Launcher` 创建 AppClassLoader，并把它设到主线程上
- 主线程里 `getContextClassLoader()` 默认返回 AppClassLoader

**子线程为何也能拿到**：`Thread` 构造函数会复制父线程的 TCCL

```java
// Thread.init()
this.contextClassLoader = parent.contextClassLoader;  // 继承父线程
```

> 不管在哪个线程，TCCL 始终指向能加载应用类的加载器。

**Spring Boot 中 DriverManager 的加载流程**

Spring 不改变 DriverManager 的加载机制，差异只在**触发时机**和 **TCCL 的值**（Spring Boot 的 TCCL 是 `LaunchedURLClassLoader`，能看到 `BOOT-INF/lib` 下的驱动）。

```text
Spring Boot main 启动
        │
        ▼
jar launcher 创建 LaunchedURLClassLoader
        │  并把它设为主线程 TCCL ← 关键
        ▼
加载 @SpringBootApplication，初始化 IoC 容器
        │
        ▼
初始化 DataSource Bean（HikariCP，默认）
        │
        ▼  首次需要物理连接（建初始连接 or 首次借出）
HikariCP.newConnection()
        │
        ▼
DriverManager.getConnection(url, user, pwd)  ← 首次主动引用！
        │
        ▼  ① 双亲委派加载 DriverManager 类（Bootstrap，核心库）
        │  ② static{} → loadInitialDrivers() → SPI 扫描
        │  ③ ServiceLoader 用 TCCL（= LaunchedURLClassLoader）
        │     读 BOOT-INF/lib 的 META-INF/services/java.sql.Driver
        ▼  ④ TCCL 加载 mysql Driver 并实例化 → 注册到 registeredDrivers
getConnection() 返回连接
        │
        ▼
HikariCP 包装成池化连接，归还连接池
```

---

## 三、垃圾回收

### 3.1 判断垃圾

| 方法 | 原理 | 缺点 |
|------|------|------|
| **引用计数法** | 对象被引用时+1，失效时-1，为0可回收 | 无法解决循环依赖（已弃用） |
| **可达性分析** | 从 GC Roots 出发，不可达的对象可回收 | — |

**GC Roots（根对象）**：可达性分析的起点，从这些对象沿引用链能到达的对象都存活，走不到的可回收。

| GC Root | 说明 | 实例 |
|---------|------|------|
| 虚拟机栈中引用的对象 | 方法里的局部变量、参数、临时变量 | 方法内 `User u = ...` 的 `u` |
| 方法区静态属性引用的对象 | 类的 `static` 字段 | `static Config config` |
| 方法区常量引用的对象 | `static final` 常量 | `static final String NAME` |
| 本地方法栈中引用的对象 | JNI（被 native 代码引用） | — |
| 同步锁持有的对象 | `synchronized` 锁住的对象 | `synchronized(obj)` 的 `obj` |
| JVM 内部引用 | 基本类型 Class、系统类加载器、常驻异常 | `Integer.class` |

> 记忆口诀：**栈、静态、常量、本地、锁**——前五个最常考。

**关键点**：
- 局部变量、`static` 字段是**起点**；实例字段是引用链上的**边**（不是 Root）
- 不连到任何 Root 的对象（含循环引用）一律可回收

### 3.2 垃圾回收算法

| 算法 | 过程 | 缺点 |
|------|------|------|
| **复制算法** | 内存分两块，一块用完后将存活对象复制到另一块 | 内存利用率低（50%） |
| **标记-清除** | 标记需回收对象，统一清除 | 效率不高；产生内存碎片 |
| **标记-整理** | 标记存活对象，移动到一端，清除边界外 | 移动成本（但存活率高时移动少） |

### 3.3 垃圾回收器

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

核心优势：可局部收集，只回收部分 Region，不回收整个堆。

**G1 的 STW 机制（常见误区）**：G1 **仍会 STW**，目标是"停顿可控"而非"零停顿"。

```bash
-XX:MaxGCPauseMillis=200   # 默认 200ms，每次回收尽量不超过该时间
```

| GC 类型 | 是否 STW | 回收范围 |
|---------|---------|---------|
| **Young GC** | 是 | 年轻代 Region |
| **Mixed GC** | 是 | 全部年轻代 + **部分**老年代（G1 招牌：渐进式清老年代） |
| **Full GC** | 是（很慢） | 整个堆，失败兜底，需极力避免 |

**实战：用 `jstat` 验证 G1 运行状态**

```bash
# -gcutil 各区使用率+GC统计；-t 打印时间戳；-h5 每5行表头；63994 PID；1000ms/次 共10次
jstat -gcutil -t -h5 63994 1000 10
```

```text
Timestamp         S0     S1     E      O      M     CCS    YGC     YGCT     FGC    FGCT     CGC    CGCT       GCT   
       318117.3   0.00  73.53  28.95  75.70  98.19  93.76     88     3.096     0     0.000    22     0.429     3.524
       318118.3   0.00  73.53  28.95  75.70  98.19  93.76     88     3.096     0     0.000    22     0.429     3.524
```

**列含义速查**（带 `%` 的为使用率，其余为次数 / 秒）：

| 列 | 含义 | 本例值 |
|----|------|--------|
| Timestamp | 应用启动后秒数（318117s ≈ 3.7 天） | 318117.3 |
| S0 / S1 | 两个 Survivor 区使用率 | 0.00 / 73.53 |
| E | Eden 使用率 | 28.95 |
| O | 老年代使用率 | 75.70 |
| M / CCS | Metaspace / 压缩类空间使用率 | 98.19 / 93.76 |
| YGC / YGCT | Young GC 次数 / 总耗时 | 88 / 3.096s |
| FGC / FGCT | Full GC 次数 / 总耗时 | 0 / 0.000s |
| CGC / CGCT | 并发 GC 次数 / 总耗时（仅 G1/CMS 有此列） | 22 / 0.429s |
| GCT | 全部 GC 总耗时 | 3.524s |

**怎么读 → 看到了什么 → 该做什么**：

- **确认收集器是 G1**：输出含 `CGC` 列（G1 并发标记周期），与上文 JDK 9+ 默认 G1 呼应。
- **两次采样数据完全一致**（仅 Timestamp 差 1s）→ 这一秒未发生任何 GC，系统平稳。
- **`FGC = 0`**：无 Full GC，健康底线达标。Full GC 是 G1 兜底，一旦 > 0 且持续增长须立即排查。
- **`O = 75.70%` + `CGC = 22`**：老年代偏高，G1 已触发 22 次并发标记在做 Mixed GC 渐进清老年代——属正常工作；但要盯住 O 别一路涨到 90%+ 且 CGC 跟不上，否则会退化成 Full GC。
- **`M = 98.19%` 偏高（重点关注）**：Metaspace 近满，可能持续扩容甚至诱发 Full GC。确认是否设置 `-XX:MaxMetaspaceSize`，排查大量动态类生成（反射、动态代理、Groovy 脚本等）。
- **GC 耗时占比**：`GCT / Timestamp = 3.524 / 318117 ≈ 0.001%`，吞吐量极高；Young GC 平均 ≈ 35ms/次（3.096 / 88），单次可控。

> **看 jstat 的固定动作**：① 有无 `CGC` 列定收集器 → ② `FGC` 是否为 0 → ③ 盯 `O` 趋势（单点没用，要连采看涨跌）→ ④ `M` 接近 100% 单独警惕 → ⑤ 算 GC 耗时占比判断吞吐是否健康。

---

## 四、四种引用类型

> **本质**：引用类型 = 保住对象不被回收的能力等级。强引用死保，软引用看内存脸色，弱引用随时丢，虚引用形同虚设（只为收尸通知）。

| 引用类型 | 实现类 | 回收时机 | 影响生命周期 |
|---------|--------|---------|------------|
| **强引用** | 默认 | 从不回收（除非无强引用） | 是 |
| **弱引用** | `WeakReference` | 下一次 GC 时 | 否 |
| **软引用** | `SoftReference` | 内存不足时 | 是（直到 OOM） |
| **虚引用** | `PhantomReference` | 对象被回收后加入引用队列 | 否 |

**应用场景**：
- 软引用：缓存（内存不足自动释放）
- 弱引用：`WeakHashMap`、`ThreadLocal`
- 虚引用：管理直接内存（NIO Buffer 回收追踪）

---

## 五、JVM 参数

### 5.1 参数分类与优先级

**4 类参数来源**：

| 类型 | 前缀/来源 | 说明 | 示例 |
|------|----------|------|------|
| **环境参数** | 操作系统环境变量 | OS 注入，`System.getenv()` 读取 | `JAVA_HOME`、`SERVER_PORT=9090` |
| **JVM 参数** | `-X` / `-XX` | 控制 JVM 运行（堆、栈、GC），**不属应用配置** | `-Xmx2g`、`-XX:+UseG1GC` |
| **系统参数** | `-D` | 系统属性（System Property），`System.getProperty()` 读取 | `-Dserver.port=9090` |
| **配置文件参数** | `application.yml` | Spring Boot 配置文件，项目内维护 | `server.port: 8080` |

```bash
java -Xmx2g -XX:+UseG1GC \
     -Dfile.encoding=UTF-8 \
     -jar myapp.jar \
     --server.port=9090
```

**配置优先级**（同一配置项多处设置时，最终生效哪个）：

> `-X` / `-XX` 控制 JVM 自身，不属应用配置范畴，**不参与覆盖比较**。

| 优先级 | 来源 | 示例 |
|--------|------|------|
| 1（最高） | 命令行参数 `--` | `--server.port=9090` |
| 2 | JVM 系统属性 `-D` | `-Dserver.port=9090` |
| 3 | 操作系统环境变量 | `SERVER_PORT=9090` |
| 4（最低） | 配置文件 `application.yml` | `server.port: 8080` |

> 口诀：命令行 > 系统属性 > 环境变量 > 配置文件。高优先级覆盖低优先级，低优先级仅作兜底。

### 5.2 常用调优参数

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



## 六、内存泄漏 vs 内存溢出

| 概念 | 定义 |
|------|------|
| **内存泄漏** | 申请内存后，无法释放已不再使用的空间 |
| **内存溢出 (OOM)** | 申请内存时没有足够空间可供分配 |

**常见内存泄漏场景**：
1. 静态集合类长期持有对象引用（`static List` 不断添加不移除）
2. 未关闭的资源（Connection、InputStream 未调 `close()`）
3. 内部类持有外部类隐式引用（非静态内部类被长期引用导致外部类无法回收）

> **为什么资源未关闭会泄漏**：`Connection`、`InputStream` 等 Java 对象只是底层 **native 资源的句柄**——文件流背后是 OS 的**文件描述符**，Connection 背后是 **TCP 连接 + 数据库会话**。这部分在堆外，**GC 管不到**，只有 `close()` 能把它们归还操作系统。
>
> - **后果**：文件流泄漏 → 文件描述符耗尽 → `Too many open files`；连接泄漏 → 连接池耗尽 → 新请求拿不到连接，应用假死
> - **不能依赖 GC 兜底**：`finalize()` 已废弃，`Cleaner` 触发时机不确定，堆没压力时 GC 可能很久不跑。优先用 **try-with-resources** 自动调 `close()`：
>
> ```java
> try (Connection conn = dataSource.getConnection();
>      PreparedStatement ps = conn.prepareStatement(sql)) {
>     // 业务逻辑
> }
> // 无论正常或异常，编译器自动在 finally 逆序调用三个资源的 close()
> ```

---

## 七、JDK 开发工具速览

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
