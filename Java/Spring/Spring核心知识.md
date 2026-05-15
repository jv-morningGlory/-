# Spring 核心知识

## 一、Spring 与 Spring Boot 概述

| 概念 | 说明 |
|------|------|
| **Spring** | 开源框架，提供 IOC/AOP 等基础能力 |
| **框架** | 可复用的设计和架构，提供基础结构、标准、约定来帮助快速构建应用 |
| **Spring Boot** | 对 Spring 的扩展增强，"约定优于配置"，通过自动配置、内嵌服务器、Starter 简化开发 |
| **约定优于配置** | 系统设定默认值，开发者仅在偏离约定时才需显式配置 |

> **Web 服务器** vs **HTTP 客户端**：服务器 = 餐厅（接收请求提供服务，如 Tomcat），客户端 = 外卖员（发送请求调用其他服务，如 Feign）。

---

## 二、Spring AOP

### 2.1 核心概念

| 概念 | 说明 |
|------|------|
| **Advice（通知）** | 切面在特定连接点执行的动作 |
| **Join Point（连接点）** | 程序中所有可能被拦截的点（方法调用、异常抛出等） |
| **Pointcut（切点）** | 通过表达式筛选出的实际要拦截的连接点 |
| **Aspect（切面）** | 包含 Pointcut 和 Advice 的模块化单元 |

### 2.2 五种通知类型



```mermaid
flowchart TB
    subgraph L1["第①层：前置处理"]
        direction LR
        A1["① @Around（前）"] --> A2["② @Before"]
    end

    L1 --> L2

    subgraph L2["第②层：目标方法"]
        direction LR
        B1{"③ 目标方法执行"}
    end

    L2 -- "成功" --> L3a
    L2 -- "异常" --> L3b

    subgraph L3a["第③层：正常返回"]
        direction LR
        C1["④ @AfterReturning"]
    end

    subgraph L3b["第③层：异常返回"]
        direction LR
        C2["④ @AfterThrowing"]
    end

    L3a --> L4
    L3b --> L4

    subgraph L4["第④层：最终处理"]
        direction LR
        D1["⑤ @After（始终执行）"] --> D2["⑥ @Around（后）"]
    end

    L4 --> L5

    subgraph L5["第⑤层：返回"]
        direction LR
        E1["返回结果"]
    end

    style B1 fill:#ff9800,color:#fff
    style D1 fill:#e3f2fd
    style E1 fill:#4caf50,color:#fff
```

> 关键点：`@After` 在 `@AfterReturning` / `@AfterThrowing` 之后执行，类似于 finally 语义。`@Around` 可以修改返回值甚至不调用目标方法，其他通知不能。

### 2.3 完整示例

```java
@Aspect
@Component
public class LogAspect {

    Logger logger = LoggerFactory.getLogger(LogAspect.class);

    @Pointcut("execution(public * com.example.aspect.service.impl..*.*(..))")
    public void servicePointCut() {
    }

    @Before("servicePointCut()")
    public void logBeforeService(JoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        logger.info("请求方法名称类: {}", signature.getDeclaringTypeName());
        logger.info("请求方法名: {}", signature.getName());
        logger.info("请求参数：{}", joinPoint.getArgs());
    }

    @After(value = "servicePointCut()")
    public void afterService(JoinPoint joinPoint) {
    }

    @AfterReturning(returning = "object", pointcut = "servicePointCut()")
    public Object afterReturnService(Object object) {
        logger.info("返回参数：{}", object);
        return object;
    }
}
```

---

## 三、依赖注入 (DI)

DI 的核心思想：内部使用不关注外部实现，外部实现不影响内部使用，分工解耦。

### 3.1 注入方式对比

| 方式 | 示例 | 特点 |
|------|------|------|
| **字段注入** | `@Autowired private MyRepository repo;` | 使用最多，反射实现 |
| **Setter 注入** | `@Autowired public void setRepo(MyRepository r)` | 可选依赖，可动态更换 |
| **构造方法注入** | `public MyService(MyRepository r)` | 推荐方式，保证不可变，便于测试 |



### 3.2 装配规则

| 注解 | 默认方式 | 支持位置 | 特殊说明 |
|------|---------|---------|---------|
| `@Autowired` | byType，多个则 byName | 字段、构造方法、setter、普通方法 | 配合 `@Qualifier` 可按 id 装配 |
| `@Resource` | byName → byType | 字段、setter | 通过 `name` 属性指定 bean 名称 |

---

## 四、Bean 生命周期

```
实例化 → 属性填充 → Aware 回调 → BeanPostProcessor 前置处理
→ 初始化方法 → BeanPostProcessor 后置处理 → 就绪 → 销毁
```

| 阶段 | 说明 |
|------|------|
| 1. 实例化 | 调用构造方法创建对象 |
| 2. 属性填充 | DI 注入依赖（setter、字段注入） |
| 3. Aware 回调 | `ApplicationContextAware` 等，感知容器环境 |
| 4. `BeanPostProcessor` 前置 | 初始化前加工（生成代理、修改属性） |
| 5. 初始化方法 | `@PostConstruct` → `InitializingBean` → `init-method` |
| 6. `BeanPostProcessor` 后置 | 初始化后加工（AOP 代理在此阶段创建） |
| 7. 就绪 | Bean 可被正常使用 |
| 8. 销毁 | `@PreDestroy` → `DisposableBean` → `destroy-method` |

> AOP 代理在 BeanPostProcessor 后置处理阶段创建。

**初始化方法示例**：

```java
@Service
public class AService {

    @PostConstruct
    public void init() {
        // 依赖注入完成后执行，适合做内部状态初始化
        System.out.println("AService 初始化完成");
    }

    @PreDestroy
    public void destroy() {
        // 容器销毁前执行，适合释放资源
        System.out.println("AService 即将销毁");
    }
}
```

> `@PostConstruct` 在依赖注入完成后立即执行，适合内部初始化。但对于依赖外部资源（Redis、MQ 等）的任务不推荐用 `@PostConstruct`，应该用 `CommandLineRunner`。



---

## 六、Spring Boot 自动配置

### 6.1 启动类注解

```
@SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
```

| 注解 | 来源 | 作用 |
|------|------|------|
| `@Configuration` | Spring | 声明配置类，配合 `@Bean` 注册 Bean |
| `@EnableAutoConfiguration` | Spring Boot | 基于 classpath 自动装配 Bean |
| `@ComponentScan` | Spring | 启用组件扫描，自动发现和注册 Bean |
| `@ConfigurationProperties` | Spring Boot | 批量绑定配置文件属性到 Java Bean |

> `@ConfigurationProperties` 只绑定属性，需配合 `@Component` 或 `@EnableConfigurationProperties` 才能注入容器。

### 6.2 Starter 工作原理

1. **依赖管理**：Starter 是 Maven/Gradle 模块，`pom.xml` 声明一组相关依赖，传递性下载
2. **自动配置**：扫描 classpath 下 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（2.7+）中的配置类
3. **条件注解**：控制配置生效时机

### 6.3 条件注解

| 注解 | 生效条件 |
|------|---------|
| `@ConditionalOnClass` | classpath 中存在指定类 |
| `@ConditionalOnMissingBean` | 容器中不存在指定 Bean |
| `@ConditionalOnProperty` | 配置文件存在指定属性 |
| `@ConditionalOnWebApplication` | 应用为 Web 应用 |

---

## 七、循环依赖与三级缓存

### 7.1 问题场景

```java
@Service
public class ServiceA {
    @Autowired
    ServiceB serviceB;
}

@Service
public class ServiceB {
    @Autowired
    ServiceA serviceA;
}
```

### 7.2 三级缓存

| 缓存级别 | 存放内容 | 说明 |
|---------|---------|------|
| **一级缓存** | 完整 Bean（可直接使用） | `singletonObjects` |
| **二级缓存** | 提前曝光的对象（属性未填充） | `earlySingletonObjects` |
| **三级缓存** | 对象工厂（可能产生 A 或 proxyA） | `singletonFactories` |

### 7.3 解决流程

核心：A 创建 → 提前曝光到三级缓存 → 填充属性时发现需要 B → B 创建时需要 A → 从三级缓存拿到 A 的早期引用 → B 完成 → A 完成。

```mermaid
flowchart TB
    subgraph L1["第①层：A 创建"]
        direction LR
        A1["1.实例化 A"] --> A2["2.A 入三级缓存 📦 L3:{A→λ}"] --> A3["3.填充 A 属性"]
    end

    L1 -- "发现需注入 B" --> L2

    subgraph L2["第②层：B 创建"]
        direction LR
        B1["4.查缓存找 B: L1❌ L2❌ L3❌"] --> B2["5.实例化 B"] --> B3["6.B 入三级缓存 📦 L3:{A→λ, B→λ}"] --> B4["7.填充 B 属性"]
    end

    L2 -- "发现需注入 A → 查缓存" --> L3

    subgraph L3["第③层：三级缓存命中"]
        direction LR
        C1["8.查缓存找 A: L1❌ L2❌ L3✅"] --> C2["9.getObject() 生成 A 早期引用"] --> C3["10.A 早期引用 → 二级缓存<br/>📦 L2:{A} L3:{B→λ}"]
    end

    L3 -- "B 拿到 A 的早期引用" --> L4

    subgraph L4["第④层：B 完成"]
        direction LR
        D1["11.B 属性填充完成"] --> D2["12.B 初始化"] --> D3["13.B → 一级缓存<br/>📦 L1:{B} L2:{A}"]
    end

    L4 -- "A 拿到完整 B" --> L5

    subgraph L5["第⑤层：A 完成"]
        direction LR
        E1["14.A 属性填充完成"] --> E2["15.A 初始化"] --> E3["16.A → 一级缓存<br/>📦 L1:{A,B} L2:空 L3:空 ✅"]
    end

    style C1 fill:#ff9800,color:#fff
    style E3 fill:#4caf50,color:#fff
    style A2 fill:#e3f2fd
    style B3 fill:#e3f2fd
    style C3 fill:#fff3e0
```

---

## 八、SpEL 表达式

### 8.1 `#{}` vs `${}`

| 表达式 | 类型 | 说明 |
|--------|------|------|
| `${}` | 属性占位符 | 读取配置文件属性值 |
| `#{}` | SpEL 表达式 | 支持运算、方法调用、引用 Bean |

### 8.2 使用场景

```java
// 注解中使用 SpEL
@Value("#{systemProperties['user.timezone']}")
private String timezone;

@Scheduled(fixedDelayString = "#{${task.interval} * 1000}")
public void dynamicTask() { }

// Kafka 动态获取配置
@KafkaListener(topics = "#{@crmKafkaTopicConfig.index}", ...)
public void batchIndexListener(...) { }
```

```yaml
# 配置文件
app.batch-size: #{10 * 5}
app.data-dir: #{environment['DATA_HOME'] ?: '/tmp/data'}
app.api.url: #{'http://' + '${app.host}' + ':' + '${app.port}' + '/api'}
```

```xml
<!-- XML 中使用 SpEL -->
<property name="maximumPoolSize"
    value="#{T(java.lang.Integer).parseInt(environment['db.pool.max'])}"/>
```

> 注意：AOP 的 `@Pointcut` 表达式（`execution`、`@annotation`）不是 SpEL，是 AspectJ 独有的表达式语法。

---

## 九、Log4j2 日志

### 9.1 核心概念

| 概念 | 作用 |
|------|------|
| **Logger** | 记录什么日志（定义日志类别和级别） |
| **Appender** | 日志输出到哪儿（控制台、文件、远程等） |

### 9.2 Appender 类型

| Appender | 用途 |
|----------|------|
| **Console** | 输出到控制台（SYSTEM_OUT / SYSTEM_ERR） |
| **RollingRandomAccessFile** | 滚动日志文件（按时间/大小归档） |
| **GELF** | 发送到 Graylog 等日志收集系统 |

**Console 配置示例**：
```xml
<Console name="STDOUT" target="SYSTEM_OUT">
    <PatternLayout pattern="${logfile.pattern}"/>
</Console>

<Console name="STDERR" target="SYSTEM_ERR">
    <PatternLayout pattern="${logfile.pattern}"/>
    <Filters>
        <ThresholdFilter level="ERROR" onMatch="ACCEPT" onMismatch="DENY"/>
    </Filters>
</Console>
```

```bash
# SYSTEM_OUT → app.log, SYSTEM_ERR → error.log
nohup java -jar app.jar 1>app.log 2>error.log &
```

**滚动日志配置**：
```xml
<RollingRandomAccessFile
    name="SERVICE_LOG_FILE"
    fileName="${logfile.path}/service.log"
    filePattern="${logfile.arch.path}/service-%d{yyyy-MM-dd}-%i.log.gz">
    <Policies>
        <TimeBasedTriggeringPolicy/>
    </Policies>
    <DefaultRolloverStrategy/>
</RollingRandomAccessFile>
```

归档目录结构：
```
/data/logs/myapp/
├── service.log                  # 当前活跃日志
└── 2024-01/
    ├── service-2024-01-20-1.log.gz
    ├── service-2024-01-21-1.log.gz
    └── service-2024-01-22-1.log.gz
```

### 9.3 GELF（Graylog Extended Log Format）

```xml
<Gelf name="GELF-SERVICE"
      facility="SOA-SERVICE"
      host="${gelf.host}" port="${gelf.port}"
      version="1.1"
      extractStackTrace="true" filterStackTrace="true"
      mdcProfiling="true" includeFullMdc="true"
      maximumMessageSize="8192"
      originHost="%host{fqdn}">
    <Field name="logTime" pattern="${timestamp.pattern}"/>
    <Field name="severity" pattern="%p"/>
    <Field name="className" pattern="%C"/>
    <DynamicMdcFields regex="mdc.*"/>
</Gelf>
```

### 9.4 Logger 配置

```xml
<!-- ROOT：所有 logger 的父类 -->
<Root level="info">
    <AppenderRef ref="STDOUT"/>
    <AppenderRef ref="SERVICE_LOG_FILE"/>
</Root>

<!-- 特定包路径的 logger -->
<Logger name="com.alibaba.nacos" level="WARN" additivity="false">
    <AppenderRef ref="STDERR"/>
    <AppenderRef ref="SERVICE_LOG_FILE"/>
</Logger>
```

> 为什么需要单独配置第三方 Logger？ROOT 通常定义为 INFO 级别，但第三方组件（如 MyBatis）默认可能输出 DEBUG 或 WARN 级别，不单独配置就无法被 ROOT 捕获。`additivity="false"` 防止日志重复输出到父 logger。

### 9.5 日志级别

```
DEBUG < INFO < WARN < ERROR < FATAL
```

配置 `level="info"` 表示 >= INFO 的日志都会输出。

---

## 十、Spring Boot 扩展点

Spring Boot 在框架的关键节点预留了扩展口，开发者无需修改源码即可插入自定义逻辑。

### 10.1 扩展点全景

```mermaid
flowchart TB
    subgraph L1["第①层：请求拦截"]
        direction LR
        A1["拦截器 preHandle"] --> A2["Controller 方法"] --> A3["拦截器 postHandle"]
    end

    L1 -- "正常返回" --> L2
    L1 -- "抛出异常" --> L2E

    subgraph L2["第②层：响应包装"]
        direction LR
        B1["ResponseBodyAdvice<br/>beforeBodyWrite"] --> B2["拦截器 afterCompletion"]
    end

    subgraph L2E["第②层：异常兜底"]
        direction LR
        C1["@ExceptionHandler"] --> C2["拦截器 afterCompletion"]
    end

    subgraph L3["第③层：启动任务"]
        direction LR
        D1["@PostConstruct"] --> D2["CommandLineRunner"] --> D3["ApplicationReadyEvent"] --> D4["接受外部请求"]
    end

    subgraph L4["第④层：全局工具"]
        direction LR
        E1["ApplicationContextAware<br/>获取 Bean"]
    end

    style A2 fill:#ff9800,color:#fff
    style C1 fill:#f44336,color:#fff
    style D4 fill:#4caf50,color:#fff
```

### 10.2 拦截器（HandlerInterceptor）

```java
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // Controller 执行前，return false 中断请求
        String token = request.getHeader("Authorization");
        if (!isValid(token)) {
            response.setStatus(401);
            return false;
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler,
                           ModelAndView modelAndView) throws Exception {
        // Controller 执行后、视图渲染前
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) throws Exception {
        // 请求完全结束后（含视图渲染），ex 为 Controller 抛出的异常
    }
}
```

注册拦截器：

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")          // 拦截所有
                .excludePathPatterns("/login", "/public/**");  // 排除
    }
}
```

| 方法 | 时机 | 用途 |
|------|------|------|
| `preHandle` | Controller 执行前 | 权限校验、登录检查 |
| `postHandle` | Controller 执行后、视图渲染前 | 修改 ModelAndView |
| `afterCompletion` | 请求完全结束 | 清理资源、日志记录 |

### 10.3 全局异常处理（@RestControllerAdvice）

```java
@RestControllerAdvice
public class ControllerResponseAdvice implements ResponseBodyAdvice<Object> {

    // ---- ResponseBodyAdvice：统一包装响应体 ----

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;  // 返回 true 才进入 beforeBodyWrite
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof Response resp) {
            resp.setTraceId(TraceContext.getTraceId());
            resp.setCostTime(System.currentTimeMillis());
        }
        return body;
    }

    // ---- @ExceptionHandler：统一异常捕获 ----

    @ExceptionHandler({Exception.class})
    public Response handleException(HttpServletRequest request,
                                    HttpServletResponse response, Exception e) {
        log.error("全局异常捕获", e);
        return Response.fail(e.getMessage());
    }
}
```

| 机制 | 接口/注解 | 时机 | 用途 |
|------|----------|------|------|
| 响应体增强 | `ResponseBodyAdvice` | Controller 返回后 → 序列化 JSON 前 | 统一包装返回值（traceId、耗时） |
| 异常捕获 | `@ExceptionHandler` | Controller 抛出异常后 | 统一异常格式，避免堆栈泄露 |

> `supports()` 可按条件筛选只包装特定返回值；异常处理也可以按异常类型分流（`@ExceptionHandler(NullPointerException.class)`）。

### 10.4 启动任务（CommandLineRunner / ApplicationRunner）

| 方式 | 接口/注解 | 时机 | 推荐度 |
|------|----------|------|--------|
| `CommandLineRunner` | 接口 `CommandLineRunner` | ApplicationContext 刷新完成后 | 推荐 |
| `ApplicationRunner` | 接口 `ApplicationRunner` | 同上（参数封装为 `ApplicationArguments`） | 推荐 |
| `@EventListener` | `ApplicationReadyEvent.class` | 应用完全启动后 | 可选 |
| `@PostConstruct` | JSR-250 注解 | 依赖注入完成后 | 不推荐 |

**方式一：CommandLineRunner**

```java
@Component
public class CachePreheatRunner implements CommandLineRunner {
    @Autowired
    private ProductCacheService productCacheService;

    @Override
    public void run(String... args) {
        productCacheService.preheatHotProducts();
    }
}
```

**方式二：监听 ApplicationReadyEvent**

```java
@Component
public class ApplicationReadyListener {
    @Autowired
    private ProductCacheService productCacheService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        productCacheService.preheatHotProducts();
    }
}
```

**缓存预热示例**：

```java
@Service
public class ProductCacheService {
    private static final String HOT_PRODUCTS_KEY = "hot:products";

    public void preheatHotProducts() {
        List<Product> hotProducts = productMapper.selectHotProducts(100);
        redisTemplate.opsForValue().set(HOT_PRODUCTS_KEY, hotProducts, 1, TimeUnit.HOURS);
    }
}
```

> **为什么不推荐 `@PostConstruct`？** 此时 Redis 连接池等外部资源可能尚未就绪；如果 Bean 被 AOP 代理，`@PostConstruct` 可能被多次执行。`CommandLineRunner` 在 Spring 上下文完全就绪后才执行，更安全。

**启动时序**：
```
应用启动 → Bean 加载完成 → Spring 上下文就绪 → CommandLineRunner.run() → Web 服务器启动 → 接受外部请求
```

### 10.5 获取 Bean（ApplicationContextAware）

```java
@Component
public class SpringUtil implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        SpringUtil.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name) {
        return (T) applicationContext.getBean(name);
    }
}
```

> 适用场景：多线程/非 Spring 管理的类中需要获取 Bean；策略模式按名称动态获取实现类。注意 `static` 变量持有 `ApplicationContext` 是安全的——它本身是单例。

### 10.6 Bean 初始化与销毁（@PostConstruct / @PreDestroy）

```java
@Service
public class AService {

    @PostConstruct
    public void init() {
        // 依赖注入完成后执行，适合内部状态初始化
    }

    @PreDestroy
    public void destroy() {
        // 容器销毁前执行，适合释放资源
    }
}
```

> `@PostConstruct` 适合 Bean 内部初始化；依赖外部资源（Redis、MQ）的任务用 `CommandLineRunner`。

### 10.7 扩展点选择速查

| 需求 | 扩展点 |
|------|--------|
| 请求鉴权、日志记录 | 拦截器 `HandlerInterceptor` |
| 统一返回值包装 | `ResponseBodyAdvice` |
| 全局异常兜底 | `@ExceptionHandler` |
| 容器启动后初始化 | `CommandLineRunner` |
| 非 Spring 管理的类获取 Bean | `ApplicationContextAware` |
| Bean 内部状态初始化 | `@PostConstruct` |

## 十一、常用注解速查

| 分类 | 注解 | 作用 |
|------|------|------|
| **Bean 定义** | `@Component` | 通用组件 |
| | `@Service` | 业务层组件 |
| | `@Repository` | 数据访问层（含异常转换） |
| | `@Controller` | Web 控制器 |
| | `@RestController` | REST 控制器 = `@Controller` + `@ResponseBody` |
| **依赖注入** | `@Autowired` | 按类型自动装配 |
| | `@Resource` | 按名称装配（JSR-250） |
| | `@Qualifier` | 指定 Bean 名称 |
| | `@Primary` | 首选 Bean |
| | `@Lazy` | 延迟初始化 |
| **配置** | `@Configuration` | 配置类 |
| | `@Bean` | 方法返回值注册为 Bean |
| | `@ConfigurationProperties` | 配置文件属性绑定 |
| **条件** | `@ConditionalOnClass` | 类存在时生效 |
| | `@ConditionalOnMissingBean` | Bean 不存在时生效 |
| | `@ConditionalOnProperty` | 属性存在时生效 |
| **生命周期** | `@PostConstruct` | 初始化回调 |
| | `@PreDestroy` | 销毁回调 |

---

## 十二、面试：常用注解怎么答

面试官问"你用过哪些 Spring Boot 注解"，不要逐个背名字，按**使用场景**分类回答，体现你有体系。

### 15.1 启动类三合一

```java
@SpringBootApplication  // = @Configuration + @EnableAutoConfiguration + @ComponentScan
```

| 拆解 | 作用 |
|------|------|
| `@Configuration` | 标记配置类，`@Bean` 注册组件 |
| `@EnableAutoConfiguration` | 根据 classpath 自动装配（核心） |
| `@ComponentScan` | 扫描当前包及子包的 `@Component` 等 |

### 15.2 Bean 注册（四个 stereotype）

| 注解 | 场景 | 面试加分点 |
|------|------|-----------|
| `@Component` | 通用组件 | 基础注解 |
| `@Service` | 业务层 | 语义化，和 `@Component` 功能一样 |
| `@Repository` | DAO 层 | **额外功能**：自动转换数据库异常为 Spring 的 `DataAccessException` |
| `@Controller` / `@RestController` | Web 层 | `@RestController` = `@Controller` + `@ResponseBody` |

> `@Repository` 是唯一有附加能力的 stereotype：通过 `PersistenceExceptionTranslationPostProcessor` 将数据库异常统一转换。

### 15.3 依赖注入

```java
// 字段注入（最常用但不推荐）
@Autowired
private UserService userService;

// 构造器注入（推荐，Spring 4.3+ 可省略 @Autowired）
private final UserService userService;
public UserController(UserService userService) {
    this.userService = userService;
}

// 按名称注入
@Autowired @Qualifier("userServiceImpl")
private UserService userService;

// 延迟注入（解决循环依赖 / 加速启动）
@Lazy
private HeavyService heavyService;
```

### 15.4 参数获取

| 注解 | 场景 | 示例 |
|------|------|------|
| `@Value` | 注入配置值 / SpEL | `@Value("${app.name}")` |
| `@ConfigurationProperties` | 批量绑定配置 | `@ConfigurationProperties(prefix = "app")` |
| `@PathVariable` | REST 路径参数 | `/user/{id}` → `@PathVariable Long id` |
| `@RequestParam` | URL 查询参数 | `?name=xx` → `@RequestParam String name` |
| `@RequestBody` | JSON 请求体 | `@RequestBody UserDTO dto` |

### 15.5 条件装配与自动化

| 注解 | 场景 |
|------|------|
| `@ConditionalOnClass` | 有这个类才装配（如 Redis 依赖存在才配 RedisTemplate） |
| `@ConditionalOnMissingBean` | 用户没自定义 Bean 才用默认配置 |
| `@ConditionalOnProperty` | 配置文件有某属性才生效（开关控制） |
| `@Profile` | 指定环境生效（dev / test / prod） |

### 15.6 异步与定时任务

```java
// 启动类加 @EnableAsync + @EnableScheduling
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class App {}

// 异步
@Async
public CompletableFuture<String> doAsync() { ... }

// 定时
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨2点
public void scheduledTask() { ... }

@Scheduled(fixedDelay = 5000)  // 上次结束后5秒再执行
public void fixedDelayTask() { ... }
```

### 15.7 面试回答模板

> "我按使用场景分几类来说：
> 1. **启动类**：`@SpringBootApplication`，它是三个注解的合体
> 2. **Bean 注册**：`@Service`、`@Repository`（注意它的异常转换能力）、`@RestController`
> 3. **注入**：`@Autowired` 按类型、`@Qualifier` 按名称、`@Lazy` 延迟加载
> 4. **参数绑定**：`@Value` 单个、`@ConfigurationProperties` 批量
> 5. **条件装配**：`@ConditionalOnClass`、`@ConditionalOnMissingBean`，这是自动配置的核心
> 6. **异步定时**：`@Async` + `@Scheduled`
>
> 实际项目里最常用的是前四类，条件装配主要在写 Starter 或框架级配置时用。"
