# Spring Boot 应用类型分类详解

> 对应面试题：Spring Boot 启动流程第一步 — "推断应用类型"

---

## 一、三种应用类型总览

Spring Boot 启动时第一件事，就是检查 classpath 里有没有某些类，猜你要跑个什么应用，然后创建对应的容器。

| 类型 | 判定条件 | 创建的容器 | 你用过吗 |
|------|----------|-----------|----------|
| **SERVLET Web 应用** | classpath 有 `DispatcherServlet`（引入了 `spring-boot-starter-web`） | `AnnotationConfigServletWebServerApplicationContext` | ✅ 天天在用 |
| **REACTIVE 响应式应用** | classpath 有 `DispatcherHandler` 但无 `DispatcherServlet`（引入了 `spring-boot-starter-webflux`） | `AnnotationConfigReactiveWebServerApplicationContext` | 网关、推送、高并发 |
| **NONE 普通应用** | 上面两个都没有 | `AnnotationConfigApplicationContext` | 批处理、定时任务、命令行工具 |

---

## 二、三种类型详细对比

### 2.1 SERVLET Web 应用

**本质：** 一个请求进来，Tomcat 分配一个线程去处理，线程从头到尾跟着这个请求直到返回结果。

```
请求 → Tomcat 线程池取一个线程 → Controller → Service → 查数据库（线程阻塞等）→ 返回
```

**线程在"等数据库"的时候是阻塞的，啥也不干但占着坑。**

- 默认线程池大小：Tomcat 默认 200 线程
- 适用场景：传统增删改查、表单提交、后台管理系统
- 你的所有项目几乎都是这个类型

---

### 2.2 REACTIVE 响应式应用

**本质：** 用少量线程 + 事件循环处理海量连接。线程发起 I/O 操作后不等待，立刻去处理别的请求，数据好了再回来。

```
请求 → 线程A 发起数据库查询（线程A 立即去处理别的请求）
      → 数据库返回了 → 通知事件循环 → 随便哪个空闲线程接着处理
```

**核心思想：线程从不"傻等"，一直在干活。**

- 底层容器：Netty（不是 Tomcat）
- 默认线程数：通常等于 CPU 核心数，就能扛几万并发连接
- 适用场景：网关、推送、长连接、流式数据

---

### 2.3 NONE 普通应用

**本质：** 根本就不是 Web 服务。Spring Boot 只是把它当做一个"带依赖注入的命令行程序"来跑，跑完 `main` 方法就退出了。

- 不起端口，不监听请求
- 用完即走，像普通 Java 程序一样
- 适用场景：数据迁移脚本、批处理任务、测试工具、定时任务

---

## 三、三个关键追问

### 追问 1：响应式的核心是不是就是处理高并发？核心思想是什么？

**不是"处理高并发"，是"处理高 I/O 并发"。** 这两个不一样。

| | 高 CPU 并发 | 高 I/O 并发 |
|------|------|------|
| 瓶颈在哪 | CPU 算不过来 | 大部分时间在等网络/磁盘/数据库 |
| CPU 状态 | 跑满 | 很闲 |
| 举例 | 加密解密、视频转码、复杂计算 | 网关转发、推送、聚合查询 |
| 传统方案 | 加机器、多线程并行算 | 加更多线程（但线程多了切换开销大、内存吃不消） |
| 响应式方案 | **没用**，响应式不加快计算 | **有用**，少量线程就能管理海量等待中的连接 |

**核心思想就四个字：非阻塞 I/O。**

> 传统线程做 I/O 是同步阻塞的：调用 `socket.read()`，线程就卡在那等数据回来。响应式线程在做 I/O 时是异步非阻塞的：告诉操作系统"去读数据，好了通知我"，然后立刻去处理别的请求。一个线程能在几千个连接之间来回切换，谁的数据到了就处理谁。

打个比方：

| | 传统阻塞 | 响应式非阻塞 |
|------|------|------|
| 比喻 | 一个客服接电话，对方说"等一下我查资料"，客服举着电话干等 5 分钟 | 一个客服同时处理 100 个会话，客户打字的时候客服就去回复别人，谁发消息了切过去回谁 |
| 线程 | 一人占用一个客服 | 少数几个客服处理所有人 |

**所以响应式的优势有前提：你的系统瓶颈必须卡在 I/O 而不是 CPU。** 如果你瓶颈在数据库查询本身很慢（SQL 没索引），上响应式没用——SQL 跑 2 秒就是 2 秒，谁也绕不过。

---

### 追问 2：传统 Web 应用可以不可以生成一个响应式的接口？为什么？

**不能混用，技术原理上不兼容。**

Spring MVC 和 Spring WebFlux 底层跑在两个完全不同的引擎上：

| | Spring MVC | Spring WebFlux |
|------|------|------|
| 底层容器 | Tomcat（Servlet 容器） | Netty（响应式引擎） |
| 线程模型 | 每请求一线程，阻塞 | 事件循环，非阻塞 |
| 核心接口 | `javax.servlet.Servlet` | `org.springframework.web.reactive.DispatcherHandler` |

**一个 Spring Boot 应用只能选一边：**

- 引入 `spring-boot-starter-web` → Tomcat 启动 → 跑在 Servlet 容器上 → 响应式跑不了
- 引入 `spring-boot-starter-webflux` → Netty 启动 → 跑在响应式引擎上 → Servlet 接口跑不了

**那为什么你会看到有人在 MVC 项目里用 `WebClient`？**

`WebClient` 是响应式风格的 HTTP 客户端，它可以在传统 MVC 项目里用——因为它只负责"发请求"，不负责"接收请求"。接收请求那层才是 Servlet 容器，发请求这层就是个工具类。所以你可以在 MVC 项目里用 `WebClient` 替代 `RestTemplate` 来做非阻塞的 HTTP 调用，**但这不叫"生成了响应式接口"**。

**如果两个依赖都引入了会怎样？**

Spring Boot 默认以 MVC 优先，应用跑在 Servlet 模式，WebFlux 的自动配置不会生效。你的代码里写了 `Mono`、`Flux` 也能跑，但底层还是阻塞的 Servlet 模型，只是把结果包装成了响应式类型而已——**形似神不似，没享受到响应式的真正好处。**

---

### 追问 3：普通应用和用 Web 项目来批处理，有什么区别？

假设你的业务需求：每天凌晨 2 点扫描过期订单，对超过 24 小时未支付的订单做关闭处理。

**方案 A：用 NONE 普通应用（推荐）**

```java
@SpringBootApplication
public class OrderCloseJob {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(OrderCloseJob.class, args);
        OrderCloseService service = ctx.getBean(OrderCloseService.class);
        service.closeExpiredOrders();
        ctx.close();  // 干完活就退出
    }
}
```

| 维度 | 表现 |
|------|------|
| 启动速度 | 快，不加载 Web 容器 |
| 内存占用 | 低，没有 Tomcat 那一套 |
| 端口 | **不启端口**，不会暴露任何端口 |
| 部署 | jar 包丢上去，crontab 定时调用 |
| 安全 | 无 Web 攻击面 |

**方案 B：用 Web 项目跑批处理**

```java
@RestController
public class OrderCloseController {
    @PostMapping("/job/closeOrders")
    public String closeOrders() {
        orderCloseService.closeExpiredOrders();
        return "ok";
    }
}
```

然后配个 cron 定时 curl 这个接口。

| 维度 | 表现 |
|------|------|
| 启动速度 | 慢，要起整个 Web 容器 |
| 内存占用 | 高，Tomcat 线程池、连接器都要占资源 |
| 端口 | **暴露端口** |
| 安全风险 | 任何人都能 curl 你的接口关单，必须额外做鉴权 |
| 运维 | 应用 7×24 运行，只是为了每天凌晨 2 点干 3 分钟的活 |

**一句话总结区别：**

> **NONE 普通应用是"工具"**，用完就走，不占资源、不暴露端口。**Web 批处理是让一个超市 24 小时开门，就为了凌晨 2 点卖一瓶水。** 浪费资源不说，还多了安全风险。

**什么时候用 NONE、什么时候用 Web？**

| 场景 | 用哪个 |
|------|--------|
| 离线数据迁移、一次性的修复脚本 | NONE |
| 每天凌晨定时跑的任务（crontab 触发） | NONE |
| 需要接收外部系统回调/Webhook 触发的任务 | Web |
| 需要随时手动调接口触发的任务 | Web |
| 一个应用里既有 Web 接口又有定时任务 | Web（用 `@Scheduled` 或 XXL-Job，别拆成两个应用） |

---

## 四、三种应用类型实战业务场景归类

### 4.1 SERVLET Web 应用 — 业务场景

占了实际项目的 90%+，几乎一切对用户提供 HTTP 接口的系统都是这个类型：

| 业务场景 | 具体例子 | 技术特点 |
|----------|---------|----------|
| **电商系统** | 京东、淘宝的订单服务、商品服务、用户服务、购物车服务 | 高并发读写，通常配合 Redis 缓存、MQ 异步削峰 |
| **后台管理系统** | 公司内部的人事系统、审批系统、CMS 内容管理 | 低并发，重 CRUD，通常用 Spring Boot + MyBatis-Plus + Vue/React |
| **开放平台 API** | 微信支付回调、支付宝接口、第三方物流查询接口 | 对内对外提供 RESTful API，重鉴权、签名验证、幂等 |
| **用户端 App 后端** | 小红书、知乎、抖音的服务端 | 高并发，通常会做微服务拆分，配合 Gateway 网关 |
| **SaaS 多租户系统** | 企业微信后台、飞书后台、钉钉后台 | 多租户数据隔离、权限体系复杂 |
| **数据中台服务** | 报表系统、数据查询服务 | 复杂 SQL、多数据源、读写分离 |
| **文件服务** | 图片/视频上传、OSS 代理 | 流式上传下载、分片上传、CDN 预热 |

> 一句话：**只要你写 `@RestController`，你就是 SERVLET Web 应用。**

---

### 4.2 REACTIVE 响应式应用 — 业务场景

只在特定场景下有优势，绝不是"新一代 MVC"：

| 业务场景 | 具体例子 | 为什么用响应式 |
|----------|---------|---------------|
| **API 网关** | Spring Cloud Gateway、Kong、Zuul 2.x | 网关要同时处理几万个连接的转发、鉴权、限流，大量时间在等上游返回。用 Netty 一个线程能管几万连接 |
| **IM 消息推送** | 客服系统、聊天室、通知推送服务 | 几十万人同时保持 WebSocket 长连接，大部分时间没消息，传统模型一个连接一个线程根本扛不住 |
| **实时行情** | 股票/期货行情推送、币圈交易所行情 | 客户端订阅某只股票，行情变了就推。连接数大 + 大部分时间空闲 |
| **Server-Sent Events 流式推送** | AI 对话打字效果、GPT 流式输出、日志实时查看 | 服务端持续输出，客户端持续接收，响应式用 `Flux` 天然支持 |
| **聚合查询 BFF** | 一个页面要调 5 个微服务拼数据 | 并发发出 5 个请求，谁先回来谁先处理，不阻塞线程。传统 MVC 也能用 `CompletableFuture`，但响应式写法更简洁 |
| **物联网数据接入** | 共享单车定位上报、工业传感器数据收集 | 几百万设备在线，每 10 秒上报一次数据。连接数极大，数据量小但频繁 |
| **CDN/代理服务** | 文件上传代理、图片实时处理 | 流式转发，边读边写，内存几乎不涨 |

> 一句话：**连接数极大 + 大部分时间在等 I/O，这两个条件同时满足才值得用响应式。**

---

### 4.3 NONE 普通应用 — 业务场景

最常见的是批处理和脚本，很多团队都在用但没意识到它是"第三种类型"：

| 业务场景 | 具体例子 | 为什么不用 Web |
|----------|---------|---------------|
| **离线批处理** | 每天凌晨关过期订单、每月生成财务报表、对账单核对 | 用完就走，不需要起端口。crontab 定时触发 |
| **数据迁移/修复脚本** | 数据库表结构变更后数据补全、修复脏数据、历史数据归档 | 一次性任务，跑完拉倒。用 Web 还得写接口、配鉴权，脱裤子放屁 |
| **ETL 数据抽取** | 从 MySQL 抽数据清洗后导入 ES/Hive/数仓 | 独立的数据处理任务，跟业务接口无关 |
| **消息队列消费者（独立部署）** | 物流状态变更消费者、订单超时消费者 | 只消费 MQ 消息，不需要对外提供 HTTP 接口。注意：如果消费者跟 Web 应用合并在一个项目里，那还是 Web 应用 |
| **文件处理任务** | 批量压缩图片、生成 PDF 报告、视频转码 | I/O 密集型，不需要端口 |
| **第三方数据同步** | 每天定时拉取外部 API 数据同步到本地 | 定时全量/增量同步 |
| **测试/压测工具** | 造测试数据、压测脚本、接口巡检 | 工具性质，用完即走 |

> 一句话：**"干完活就退"的任务用 NONE，"永远在线等请求"的任务用 Web。**

---

### 4.4 一张表快速判断

| 你的需求 | 选哪种 |
|----------|--------|
| 需要对外提供 HTTP 接口 / 前端调你 | **SERVLET Web** |
| 既有 HTTP 接口又有定时任务 | **SERVLET Web**（用 `@Scheduled` 或 XXL-Job，别拆） |
| 网关/AI 流式输出/WebSocket 长连接/海量设备接入 | **REACTIVE** |
| 纯批处理、crontab 定时跑、一次性的脚本 | **NONE** |
| 独立 MQ 消费者（不含 Web 接口） | **NONE** 或 Web（看是否方便运维统一管理） |

---

## 五、为什么 Web 应用启动了 main 不会自动关闭？

这是很多初学者的困惑：普通 Java 程序 `main()` 跑完就退出了，为什么 Spring Boot Web 应用启动后一直不退？

**答案就一句话：非守护线程还在跑，JVM 不会退出。**

### 5.1 先搞懂 JVM 的退出规则

JVM 有一条铁律：

> **JVM 退出 = 所有非守护线程（non-daemon thread）都执行完毕。**

- **守护线程（Daemon Thread）：** 后台服务线程，JVM 退出时直接杀掉，不管它有没有跑完。比如 GC 线程。
- **非守护线程（User Thread）：** 业务线程，只要还有一个在跑，JVM 就不退出。Tomcat 的工作线程就是非守护线程。

### 5.2 普通 Java 程序为什么自动退出

```java
public static void main(String[] args) {
    System.out.println("hello");  // 打印完，main 线程结束
    // JVM：没有非守护线程了 → 退出
}
```

`main` 线程执行完就没了，没有其他非守护线程，JVM 直接退出。

### 5.3 Spring Boot Web 应用为什么不退出

`SpringApplication.run()` 做完这几件"让 JVM 停不下来"的事：

```
SpringApplication.run()
    │
    ├── 创建内嵌 Tomcat
    │       │
    │       ├── 启动 Acceptor 线程（非守护）→ 死循环监听 8080 端口，等连接进来
    │       ├── 启动 NIO Poller 线程（非守护）→ 死循环轮询 I/O 事件
    │       └── 启动 Worker 线程池（非守护）→ 等待任务队列里的请求
    │
    └── main 线程执行完毕，结束了
            │
            └── JVM 检查：Tomcat 那几个非守护线程还在跑 → 不退出，继续等
```

**Tomcat 的 `Acceptor` 线程一直在死循环监听端口，只要这个线程还在，JVM 就永远不退。**

打个比方：

```
main 方法就像餐厅开业仪式的主持人。
主持人宣布"开业！"（main 线程结束）→ 主持人走了
但厨房的厨师、前厅的服务员还在干活（Tomcat 的非守护线程）
→ 餐厅继续营业，不会关门（JVM 不退出）
```

### 5.4 那怎么让 Web 应用优雅关闭？

**方式一：** `kill` 或 `Ctrl+C` → 向 JVM 发信号 → Spring Boot 的 `ShutdownHook` 捕获 → 关闭容器 → 释放资源 → JVM 退出。

**方式二：** Actuator 的 `/actuator/shutdown` 端点（需手动开启）。

**方式三：**

```java
ConfigurableApplicationContext ctx = SpringApplication.run(MyApp.class, args);
// ...
ctx.close();  // 关闭容器 → Tomcat 线程停止 → JVM 退出
```

### 5.5 NONE 普通应用为什么又自动退出了

没有 Web 容器 → 没有 Tomcat 的非守护线程 → `main()` 跑完就没有非守护线程了 → JVM 退出。

Spring Boot 会在推断应用类型为 NONE 时不创建 WebServer，所以 `refresh()` 完成后容器里只有你的业务 Bean，没有死循环监听的线程。你的 `main` 线程执行完业务逻辑，JVM 一看，没有非守护线程了，直接退出。

### 5.6 核心对比

| | NONE 普通应用 | Web 应用 |
|------|------|------|
| 有内嵌容器吗 | 没有 | 有（Tomcat/Netty） |
| 有死循环监听的线程吗 | 没有 | 有（Acceptor + Worker） |
| main 跑完后 JVM | **退出** | **继续跑** |
| 停止方式 | 代码跑完自动退出 | `kill` / `Ctrl+C` / Actuator shutdown |

---

## 六、总结：一张图记住

```
Spring Boot 启动
    │
    ├── classpath 有 DispatcherServlet？
    │       │
    │       ├── 是 → SERVLET Web 应用 → 起 Tomcat → 监听端口 → 等你访问
    │       │                                        （你 90% 的项目）
    │       │
    │       └── 否 → classpath 有 DispatcherHandler？
    │                   │
    │                   ├── 是 → REACTIVE 应用 → 起 Netty → 事件循环模式
    │                   │                        （网关、推送、长连接）
    │                   │
    │                   └── 否 → NONE 普通应用 → 起容器 → 干活 → 退出
    │                                                （批处理、脚本、定时任务）
```
