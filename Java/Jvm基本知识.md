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

**矛盾**：`DriverManager` 要加载第三方 `Driver`，但双亲委派**只能向上委托、不能向下找人**。Bootstrap 已是最顶层，没有父加载器可委托，看不到 classpath 下的类 → 核心类需要反过来使用下层加载器，单向链走不通。

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

> G1 有 STW，但 STW 的是"部分 Region"而非"整个堆"，且时间可预测。ZGC / Shenandoah 才把停顿压到 <10ms。

**G1 vs Parallel GC（为什么 JDK 9+ 默认改用 G1）**：

| 维度 | Parallel GC | G1 |
|------|-------------|-----|
| 设计目标 | **吞吐量优先** | **停顿时间可控** |
| 堆结构 | 物理分代（连续） | Region 化（逻辑分代） |
| 回收单位 | 整个新生代/老年代 | 部分 Region |
| STW 特点 | 整片 STW，**随堆增大变长** | 部分 Region STW，可设目标时间 |
| 大堆表现 | >6GB 停顿不可接受 | 大堆下仍可控 |

**G1 的代价**：额外内存（每 Region 维护记忆集，约占堆 5%~20%）、吞吐略低于 Parallel。

**选择标准**：

| 场景 | 选择 |
|------|------|
| 批处理 / 离线计算，追求吞吐量，堆 < 4GB | Parallel GC |
| Web 服务 / 交互应用，对延迟敏感，堆 > 4GB | G1 |
| 要求停顿 <10ms（交易系统、超大堆） | ZGC / Shenandoah |

> JDK 9+ 默认 G1，因为现代应用大多是延迟敏感的 Web 服务，而非吞吐型批处理。

---

## 四、四种引用类型

> **本质**：引用类型 = 保住对象不被回收的能力等级。强引用死保，软引用看内存脸色，弱引用随时丢，虚引用形同虚设（只为收尸通知）。

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
