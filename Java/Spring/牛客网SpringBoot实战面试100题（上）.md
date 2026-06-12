# 牛客网 Spring Boot 实战面试 100 题（上）

> 来源：牛客网面经 + 大厂高频真题 + 场景设计题
> 说明：本题库偏向**实战与场景**，每题均为真实面试出现过的题目
> 本篇涵盖：第 1-29 题（基础原理、自动配置、IoC 容器）

---

## 一、Spring Boot 基础与核心原理（13 题）

### 1. Spring Boot 相比传统 Spring 框架做了什么改进？为什么现在大家都在用？

> **第一，内嵌容器，开箱即用。** 传统 Spring 需要单独部署 Tomcat，打成 war 包丢到容器里跑。Spring Boot 直接把 Tomcat/Jetty/Undertow 内嵌进了 jar 包，一个 `java -jar` 就能启动，开发、测试、部署的体验都极大简化了。
>
> **第二，Starter 机制，一键集成。** 以前整合 MyBatis、Redis、MQ 这些框架，要一个一个找 Maven 依赖、解决版本冲突，非常痛苦。Spring Boot 提供了各种 Starter，比如你引入 `spring-boot-starter-data-redis`，它自动帮你拉好所有兼容的依赖，版本也不用你操心。
>
> **第三，约定优于配置。** 这是 Spring Boot 最核心的设计理念。它给几乎所有常见场景都预设了合理的默认值——端口默认 8080、数据源默认 HikariCP、静态资源默认放 `static` 目录。你只有不按约定来的时候才需要手动配置，90% 的情况零配置就能跑起来，极大减少了配置文件的体积和维护成本。

### 2. `@SpringBootApplication` 注解内部由哪几个注解构成？各自负责什么？

> `@SpringBootApplication` 是一个组合注解，由三个核心注解构成：
>
> **① `@SpringBootConfiguration`** — 本质就是 `@Configuration`，标记当前类是 Spring 的配置类，会被注册到 IoC 容器中。
>
> **② `@EnableAutoConfiguration`** — Spring Boot 的灵魂。它会借助 `AutoConfigurationImportSelector`，从 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件中读取所有候选的自动配置类，然后根据条件注解（`@ConditionalOnClass`、`@ConditionalOnMissingBean` 等）按需加载。这就是为什么你引入了 `spring-boot-starter-web` 之后不用做任何配置就能直接写 Controller 的原因。
>
> **③ `@ComponentScan`** — 开启组件扫描，默认扫描当前启动类所在包及其子包下的 `@Component`、`@Service`、`@Repository`、`@Controller` 等注解的类，将它们注册为 Bean。**注意：**如果你把启动类放在根包下，而业务代码放到了别的包里没被扫描到，Bean 就不会被注册，这是个很常见的坑。
>
> > **追问预警：** "那我想扫描别的包怎么办？" → 用 `@SpringBootApplication(scanBasePackages = "com.xxx")` 或在启动类上额外加 `@ComponentScan("com.xxx")` 指定扫描路径。

### 3. Spring Boot 的约定大于配置体现在哪些地方？举几个实际例子。

> 约定优于配置不是"不用配置"，而是**你不说话的时候，我按规矩来；你有意见的时候，我按你说的改。** 体现在这几个层面：
>
> **① Maven 依赖管理。** `spring-boot-starter-parent` 帮你锁定了海量第三方依赖的版本号，你只需声明用哪个 Starter，不用再操心各个 jar 包的版本兼容问题——Spring Boot 替你做好了版本仲裁。
>
> **② Starter 自动配置。** 引入 `spring-boot-starter-data-redis`，Spring Boot 约定你用 `localhost:6379`，没有密码；如果你 Redis 在内网另一台机器上，就得在配置文件里写 `spring.redis.host` 和 `spring.redis.port` 来覆盖约定。引入 `spring-boot-starter-data-jpa`，约定连接池用 HikariCP、自动建表策略默认 `none`——都能改，但不改就能跑。
>
> **③ 项目结构。** 约定启动类放在根包下，`@ComponentScan` 默认扫描这个包及其子包；静态资源放 `classpath:/static` 或 `/resources`；模板文件放 `classpath:/templates`。按这个目录结构来，零配置就能访问 `localhost:8080/index.html`，不按约定就要手动配 `spring.web.resources.static-locations`。
>
> **④ 配置文件。** 约定配置文件叫 `application.properties` 或 `application.yml`，放 `src/main/resources` 下，Spring Boot 自动加载。多环境也遵循约定——`application-dev.yml`、`application-prod.yml`，只需在 `application.yml` 里设 `spring.profiles.active: dev` 就自动切好了。
>
> **⑤ 内嵌容器。** 约定用 Tomcat，端口 8080。想换 Undertow 就 exclude Tomcat 再引入 Undertow；想换端口就 `server.port=9090`——不配，就按约定来。

### 4. Spring Boot 的启动流程是怎样的？`SpringApplication.run()` 内部做了哪些关键步骤？

> 一句话概括：**判断应用类型 → 加载扩展 → 准备环境 → 创建容器 → `refresh()`（自动配置生效 + Bean 实例化）→ 回调 Runner。**
>
> 具体拆成七个步骤：
>
> **阶段一：启动准备**
>
> **① 推断应用类型。** 通过检查 classpath——有 `DispatcherServlet` 就是 Servlet Web 应用，有 `DispatcherHandler` 但没有 `DispatcherServlet` 就是响应式（Reactive）应用，啥都没有就是普通应用。这决定了后续创建什么类型的 `ApplicationContext`。
>
> **② 加载初始化器和监听器。** 从 `META-INF/spring.factories`（3.x 改为 `AutoConfiguration.imports`）中加载 `ApplicationContextInitializer` 和 `ApplicationListener`，让你在容器刷新前后能做自定义扩展。
>
> **阶段二：环境准备**
>
> **③ 准备 Environment。** 创建 `Environment` 对象，加载所有配置源并按优先级合并。优先级从高到低：
>
> | 优先级 | 来源 | 示例 |
> |--------|------|------|
> | 1（最高） | 命令行参数 | `--server.port=9999` |
> | 2 | JVM 系统属性（`-D`） | `-Dserver.port=8081` |
> | 3 | OS 环境变量 | `SERVER_PORT=8081` |
> | 4 | `application-{profile}.yml` | `application-prod.yml` |
> | 5（最低） | `application.yml` | 默认配置文件 |
>
> 规律：**越靠近运行时的优先级越高**（命令行 `--` > JVM `-D` > 环境变量 > profile 配置 > 默认配置）。同一 key 被多处配置时，高优先级覆盖低优先级。
>
> **④ 打印 Banner + 创建容器。** 就是启动时那个 Spring 大字，同时创建一个空的 `ApplicationContext`。
>
> **阶段三：容器刷新（最核心）**
>
> **⑤ `refresh()` 刷新容器。** 对应 `AbstractApplicationContext.refresh()`，内部十几步，核心的几个：
> - `obtainFreshBeanFactory()`：解析配置，注册 `BeanDefinition`
> - `invokeBeanFactoryPostProcessors()`：**执行 `BeanFactoryPostProcessor`，自动配置在这步生效**
> - `registerBeanPostProcessors()`：注册 `BeanPostProcessor`（AOP 代理就靠它）
> - `finishBeanFactoryInitialization()`：**实例化所有非懒加载的单例 Bean**
>
> **⑥ 自动配置生效。** 在第⑤步中，`ConfigurationClassPostProcessor` 解析 `@Configuration` 类，触发 `AutoConfigurationImportSelector` 加载所有候选自动配置类，再由条件注解（`@ConditionalOnClass` 等）决定哪些真正生效。
>
> **阶段四：收尾**
>
> **⑦ 回调 Runner。** 容器启动完成后，执行 `CommandLineRunner` 和 `ApplicationRunner`。常用于启动后初始化数据、预热缓存、检查依赖服务等。



### 5. Spring Boot 为什么能通过 main 方法直接启动，不需要外部 Tomcat？

> 传统 Spring 需要打成 war 包部署到外部 Tomcat，Tomcat 负责启动 Servlet 容器、加载应用。Spring Boot 不需要这些，因为它**把容器内嵌进了 jar 包**。
>
> **核心原理：**
>
> **① 内嵌 Servlet 容器。** 引入 `spring-boot-starter-web` 后，依赖里自带了 Tomcat 的 jar 包（`spring-boot-starter-tomcat`）。Spring Boot 启动时不是等外部容器来加载你，而是自己 new 一个 Tomcat 实例出来，把 DispatcherServlet 注册上去，监听端口——整个过程都在 JVM 内部完成。
>
> **② `main()` 方法就是入口。** `SpringApplication.run()` 做了两件事：创建 Spring 容器（`ApplicationContext`）+ 启动内嵌 Tomcat。Tomcat 作为 Spring Bean 的一部分被管理和启动，不需要外部进程。
>
> **③ 打包方式。** 用 `spring-boot-maven-plugin` 打成可执行 jar，里面通过 `MANIFEST.MF` 指定 `Main-Class` 为 `JarLauncher`，它负责加载嵌套 jar 里的依赖，然后调用你的 `main()` 方法。所以一个 `java -jar app.jar` 就能跑起来。
>
> | | 传统 Spring | Spring Boot |
> |---|---|---|
> | **打包方式** | war 包 | 可执行 jar 包 |
> | **容器** | 外部 Tomcat（独立进程） | 内嵌 Tomcat（同一 JVM） |
> | **启动方式** | 部署到 Tomcat，Tomcat 启动应用 | `java -jar` 直接启动 |
> | **换容器** | 换 Tomcat 版本或换 Jetty | exclude Tomcat，引入 Undertow/Jetty 依赖即可 |
>
> ```xml
> <!-- 想换 Undertow，排除默认 Tomcat -->
> <dependency>
>     <groupId>org.springframework.boot</groupId>
>     <artifactId>spring-boot-starter-web</artifactId>
>     <exclusions>
>         <exclusion>
>             <groupId>org.springframework.boot</groupId>
>             <artifactId>spring-boot-starter-tomcat</artifactId>
>         </exclusion>
>     </exclusions>
> </dependency>
> <dependency>
>     <groupId>org.springframework.boot</groupId>
>     <artifactId>spring-boot-starter-undertow</artifactId>
> </dependency>
> ```

### 6. `CommandLineRunner` 和 `ApplicationRunner` 的区别是什么？你在项目中用过吗？

> **相同点：** 执行时机一样，都在容器启动完成后（所有 Bean 创建好、`refresh()` 结束）执行。常用于预热缓存、初始化数据、启动后检查依赖服务等。
>
> **区别：参数类型不同。** 假设命令行为 `java -jar app.jar --name=张三 hello 123`：
>
> | | CommandLineRunner | ApplicationRunner |
> |---|---|---|
> | **收到的参数** | `String... args`（原始字符串数组） | `ApplicationArguments`（已解析好的对象） |
> | **实际拿到什么** | `["--name=张三", "hello", "123"]` | 选项参数和非选项参数已分好 |
> | **取 `--name` 的值** | 自己截字符串：`args[0].split("=")[1]` | 直接调：`args.getOptionValues("name")` → `["张三"]` |
> | **取非 `--` 参数** | 自己判断哪些没 `--` 前缀 | 直接调：`args.getNonOptionArgs()` → `["hello", "123"]` |
>
> 简单说：`CommandLineRunner` 给你一坨原始字符串自己解析；`ApplicationRunner` 帮你把 `--key=value` 和普通参数分好了，拿来就能用。
>
> **怎么选：** 需要用命令行参数选 `ApplicationRunner`，不需要参数选 `CommandLineRunner`。
>
> ```java
> // CommandLineRunner —— 不关心参数，启动后跑一段逻辑
> @Component
> public class CacheWarmRunner implements CommandLineRunner {
>     @Override
>     public void run(String... args) throws Exception {
>         cacheManager.preload(); // 预热缓存
>     }
> }
>
> // ApplicationRunner —— 需要用命令行参数
> @Component
> public class StartupCheckRunner implements ApplicationRunner {
>     @Override
>     public void run(ApplicationArguments args) throws Exception {
>         if (args.containsOption("env")) {
>             String env = args.getOptionValues("env").get(0);
>             if ("prod".equals(env)) healthChecker.checkAll();
>         }
>     }
> }
>
> // 多个 Runner 用 @Order 控制顺序，数字越小越先执行
> @Component @Order(1)
> public class RunnerA implements CommandLineRunner { ... }
>
> @Component @Order(2)
> public class RunnerB implements ApplicationRunner { ... }
> ```

### 7. Spring Boot 有几种注入 Bean 的方式？各有什么优缺点？

> 三种方式：**构造器注入、Setter 注入、字段注入（`@Autowired` 打在字段上）**。
>
> ```java
> // ① 构造器注入（推荐）——单构造器时 @Autowired 可省略
> @Component
> public class OrderService {
>     private final UserService userService;
>     private final PayService payService;
>
>     public OrderService(UserService userService, PayService payService) {
>         this.userService = userService;
>         this.payService = payService;
>     }
> }
>
> // ② Setter 注入
> @Component
> public class OrderService {
>     private UserService userService;
>
>     @Autowired
>     public void setUserService(UserService userService) {
>         this.userService = userService;
>     }
> }
>
> // ③ 字段注入（不推荐）
> @Component
> public class OrderService {
>     @Autowired
>     private UserService userService;
> }
> ```
>
> | | 构造器注入 | Setter 注入 | 字段注入 |
> |---|---|---|---|
> | **能否 `final`** | ✅ 可以，保证不可变 | ❌ 不行 | ❌ 不行 |
> | **空指针安全** | ✅ 创建完就有值 | ⚠️ 可能没调 setter | ⚠️ 可能没注入 |
> | **循环依赖** | ❌ 直接报错（好事，早暴露问题） | ✅ 能解决 | ✅ 能解决 |
> | **可测试性** | ✅ new 时直接传 mock | ✅ 调 setter 传 mock | ❌ 要用反射才能测 |
> | **Spring 推荐** | ✅ 官方推荐 | 可选依赖时用 | 不推荐 |
>
> **结论：构造器注入是首选**——不可变（`final`）、不空、好单元测试、循环依赖早发现。字段注入最简单但问题最多：不能 `final`、不好测试、隐藏依赖关系。
### 8. Spring Boot 中 `@ConfigurationProperties` 和 `@Value` 的区别是什么？什么时候用哪个？

> ```java
> // @Value —— 一个一个取
> @Component
> public class RedisConfig {
>     @Value("${spring.redis.host}")
>     private String host;
>
>     @Value("${spring.redis.port}")
>     private int port;
>
>     @Value("${spring.redis.password:}")  // 默认空字符串
>     private String password;
> }
>
> // @ConfigurationProperties —— 按 prefix 批量绑定
> @Component
> @ConfigurationProperties(prefix = "spring.redis")
> public class RedisProperties {
>     private String host;      // 自动绑定 spring.redis.host
>     private int port;         // 自动绑定 spring.redis.port
>     private String password;  // 自动绑定 spring.redis.password
>     // getter/setter
> }
> ```
>
> | | `@Value` | `@ConfigurationProperties` |
> |---|---|---|
> | **绑定方式** | 一个字段一个注解 | 按 prefix 批量绑定 |
> | **松散绑定** | ❌ 必须精确匹配 key | ✅ `redis-host` 能绑定到 `redisHost` |
> | **SpEL 表达式** | ✅ 支持 `#{@beanName.method()}` | ❌ 不支持 |
> | **配置校验** | ❌ 不支持 | ✅ 配合 `@Validated` + JSR 303 校验 |
> | **动态刷新** | ❌ 启动后改了不生效 | ✅ 配合 `@RefreshScope` 可动态刷新 |
> | **复杂对象** | ❌ List、Map 写法很丑 | ✅ 直接绑定嵌套对象 |
> | **适合场景** | 取 1-2 个零散值 | 一组相关配置统一管理 |
>
> **怎么选：零散取值用 `@Value`，批量绑定用 `@ConfigurationProperties`。** Spring Boot 官方 Starter 用的全是 `@ConfigurationProperties`（如 `RedisProperties`、`DataSourceProperties`），因为支持松散绑定和校验，更规范。
### 9. Spring Boot 支持哪些配置文件格式？`.properties` 和 `.yml` 加载优先级是怎样的？

> Spring Boot 支持 `.properties` 和 `.yml`（`.yaml` 和 `.yml` 等同）两种格式。
>
> **优先级：`.properties` > `.yml`。** 两个文件同时存在且 key 相同时，`.properties` 的值生效。
>
> | | `.properties` | `.yml` |
> |---|---|---|
> | **格式** | `key=value`，扁平 | 缩进层级，树状结构 |
> | **可读性** | 配置多了很乱 | 层级清晰，适合复杂配置 |
> | **优先级** | **高** | 低 |
> | **支持 List/Map** | 要用下标 `list[0]=a` | 天然支持 |
>
> ```properties
> # .properties 扁平写法
> spring.profiles.active=dev
> spring.datasource.url=jdbc:mysql://localhost:3306/db
> spring.datasource.username=root
> spring.datasource.password=123456
> ```
>
> ```yaml
> # .yml 层级写法，可读性更好
> spring:
>   profiles:
>     active: dev
>   datasource:
>     url: jdbc:mysql://localhost:3306/db
>     username: root
>     password: 123456
> ```
>
> **实际项目中基本全用 `.yml`**，可读性好。优先级这个知道就行，不要两个文件混用，选一个坚持用。
### 10. Spring Boot 多环境配置怎么实现？你在项目中怎么切换 dev / test / prod 环境？

> **传统方式（纯 Spring Boot）：** 用 `application-{profile}.yml` 按环境拆配置文件。
>
> ```
> application.yml           ← 公共配置
> application-dev.yml       ← 开发环境
> application-test.yml      ← 测试环境
> application-prod.yml      ← 生产环境
> ```
>
> ```yaml
> # application.yml 里指定激活哪个环境
> spring:
>   profiles:
>     active: dev
> ```
>
> 启动时也可以覆盖：`java -jar app.jar --spring.profiles.active=prod`
>
> ---
>
> **项目实际方式（Nacos）：** 配置放 Nacos 服务端，本地只配 Nacos 地址和 profile，不同环境用 namespace 隔离。
>
> ```yaml
> # 本地 bootstrap.yml
> spring:
>   application:
>     name: order-service
>   profiles:
>     active: dev   # 切环境改这一行
>   cloud:
>     nacos:
>       server-addr: 192.168.1.100:8848
>       config:
>         namespace: dev
>         file-extension: yml
> ```
>
> | | 纯 Spring Boot | Nacos |
> |---|---|---|
> | **配置存放** | 打在 jar 包里 | Nacos 服务端，不进 jar |
> | **改配置** | 改代码重新打包部署 | Nacos 控制台改，不用重启 |
> | **切环境** | 改 `spring.profiles.active` | 改 namespace 或 profile |
> | **动态刷新** | ❌ 不支持 | ✅ `@RefreshScope` 实时生效 |
> | **配置回滚** | 靠 Git 历史版本 | Nacos 自带历史版本管理 |
>
> **面试答法：** 先说传统方式（`application-{profile}.yml` + `spring.profiles.active`），再说项目实际用的 Nacos 方案——配置放 Nacos，用 namespace 隔离环境，支持动态刷新不用重启。
### 11. Spring Boot 2.x 和 3.x 有哪些重要变化？升级时需要注意什么？

> **最低要求提升：**
>
> | | Spring Boot 2.x | Spring Boot 3.x |
> |---|---|---|
> | **JDK** | 8+ | **17+** |
> | **Spring Framework** | 5.x | **6.x** |
> | **Java EE 包名** | `javax.*` | **`jakarta.*`** |
>
> **① javax → jakarta（升级最痛的点）。** Java EE 捐给 Eclipse 基金会后改名为 Jakarta EE，包名跟着改。涉及 Servlet、Validation、JPA、Mail 等所有 Java EE 相关包。
>
> ```java
> // 2.x
> import javax.servlet.http.HttpServletRequest;
> import javax.validation.Valid;
>
> // 3.x —— 全部改为 jakarta
> import jakarta.servlet.http.HttpServletRequest;
> import jakarta.validation.Valid;
> ```
>
> **② 自动配置机制变了。**
>
> ```properties
> # 2.x：META-INF/spring.factories（key-value 格式）
> org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
>   com.example.MyAutoConfiguration
>
> # 3.x：META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
> # 每行一个全类名，去掉了 key-value 格式
> com.example.MyAutoConfiguration
> ```
>
> **③ 其他重要变化：**
>
> | 变化点 | 2.x | 3.x |
> |---|---|---|
> | **GraalVM 原生镜像** | 不支持 | ✅ 官方支持，启动快 10 倍+ |
> | **Observability** | Spring Cloud Sleuth + Zipkin | Micrometer + Tracing，统一指标和链路追踪 |
> | **Spring Security** | `WebSecurityConfigurerAdapter` | 废弃，改用 `SecurityFilterChain` Bean |
> | **路径匹配** | AntPathMatcher | 默认改用 **PathPatternParser**，性能更好 |
> | **Redis 配置前缀** | `spring.redis.*` | 改为 `spring.data.redis.*` |
>
> **④ 升级注意事项：**
> 1. **先升 JDK 17**——硬门槛
> 2. **javax → jakarta 全局替换**——IDE 批量替换 `import javax.` → `import jakarta.`，逐个验证
> 3. **依赖版本对齐**——第三方库必须用支持 Spring Boot 3 的版本（MyBatis-Plus、Druid、Nacos 等）
> 4. **spring.factories 迁移**——自研 Starter 要改配置文件位置和格式
> 5. **Spring Security 配置改写**——`WebSecurityConfigurerAdapter` 废弃了

## 二、自动配置与 Starter 机制（8 题）

### 14. Spring Boot 的自动装配原理是什么？从 `@EnableAutoConfiguration` 到最终加载配置类的完整链路是怎样的？

> 一句话：**注解触发 → 读配置文件 → 加载候选类 → 条件过滤 → 注册 Bean。**
>
> **① 入口：`@EnableAutoConfiguration`**
>
> ```java
> @SpringBootApplication
>     ├── @SpringBootConfiguration
>     ├── @EnableAutoConfiguration    // ← 自动装配入口
>     └── @ComponentScan
>
> // @EnableAutoConfiguration 内部
> @AutoConfigurationPackage
> @Import(AutoConfigurationImportSelector.class)  // ← 关键
> public @interface EnableAutoConfiguration { }
> ```
>
> **② 读配置文件：`AutoConfigurationImportSelector`**
>
> 从所有 jar 包中读取候选自动配置类（可能有 100+ 个）：
>
> ```properties
> # Spring Boot 2.x：META-INF/spring.factories（key-value 格式）
> org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
>   org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration,\
>   org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,\
>   ...
>
> # Spring Boot 3.x：META-INF/spring/...AutoConfiguration.imports（每行一个类名）
> org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
> org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
> ```
>
> **③ 条件过滤（核心）：** 不是读出来就全部加载，要过三层过滤：
>
> ```java
> // 过滤一：@ConditionalOnClass —— classpath 里有没有这个类
> @ConditionalOnClass(DataSource.class)   // 没引入数据库依赖 → 不加载
> public class DataSourceAutoConfiguration { }
>
> // 过滤二：@ConditionalOnMissingBean —— 容器里有没有这个 Bean
> @ConditionalOnMissingBean
> @Bean
> public DataSource dataSource() { }      // 用户自己定义了 → 不加载
>
> // 过滤三：@ConditionalOnProperty —— 配置开关
> @ConditionalOnProperty(prefix = "spring.redis", name = "enabled", havingValue = "true")
> public class RedisAutoConfiguration { }  // 没配或配了 false → 不加载
> ```
>
> **④ 注册 BeanDefinition：** 过滤后剩下的配置类注册到容器，在 `refresh()` 时实例化。
>
> **完整链路：**
>
> ```
> @SpringBootApplication
>   └→ @EnableAutoConfiguration
>        └→ @Import(AutoConfigurationImportSelector)
>             └→ selectImports()
>                  └→ 读 spring.factories / AutoConfiguration.imports（100+ 个候选类）
>                       └→ @ConditionalOnClass 过滤（classpath 有没有）
>                       └→ @ConditionalOnMissingBean 过滤（用户有没有自定义）
>                       └→ @ConditionalOnProperty 过滤（配置开关）
>                            └→ 剩下的注册为 BeanDefinition → refresh() 时实例化
> ```
>
> **面试答法（四步说清楚）：** `@EnableAutoConfiguration` 触发 → `AutoConfigurationImportSelector` 读配置文件拿到候选类 → 条件注解过滤 → 剩下的注册为 Bean。
### 15. `spring.factories` 文件在 Spring Boot 2.x 和 3.x 中有什么变化？

> | | 2.x | 3.x |
> |---|---|---|
> | **文件路径** | `META-INF/spring.factories` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
> | **格式** | key-value（`EnableAutoConfiguration=类1,类2`） | 每行一个全类名 |
> | **一个文件管所有** | ✅ 同一个文件存多种扩展点 | ❌ 每种扩展点独立文件 |
>
> 变化的原因：`spring.factories` 一个文件塞了太多东西（自动配置、Initializer、Listener 全混在一起），3.x 拆开更清晰，加载也更快。如果自研了 Starter，升级 3.x 时要迁移这个文件。
### 16. `@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty` 这些条件注解在自动装配中怎么协作的？

> 三个注解各管一层过滤：
>
> | 注解 | 判断什么 | 什么时候生效 |
> |---|---|---|
> | **`@ConditionalOnClass`** | classpath 里有没有这个类 | 引入了对应依赖才生效 |
> | **`@ConditionalOnMissingBean`** | 容器里有没有这个 Bean | 用户没自定义才生效，避免覆盖用户的 |
> | **`@ConditionalOnProperty`** | 配置文件里某个值是什么 | 配置开关控制是否生效 |
>
> ```java
> @AutoConfiguration
> @ConditionalOnClass(RedisOperations.class)   // ① 引了 redis 依赖才往下走
> @EnableConfigurationProperties(RedisProperties.class)
> public class RedisAutoConfiguration {
>
>     @Bean
>     @ConditionalOnMissingBean(name = "redisTemplate")  // ② 用户没自定义才创建
>     public RedisTemplate<String, Object> redisTemplate(
>             RedisConnectionFactory factory) {
>         RedisTemplate<String, Object> template = new RedisTemplate<>();
>         template.setConnectionFactory(factory);
>         return template;
>     }
> }
> ```
>
> **三层过滤流程：**
>
> ```
> 100+ 个候选自动配置类
>     │
>     ▼ @ConditionalOnClass（第一道门）
>     │  没引入 spring-data-redis → RedisAutoConfiguration 淘汰
>     │  没引入 mybatis → MybatisAutoConfiguration 淘汰
>     │
>     ▼ @ConditionalOnMissingBean（第二道门）
>     │  用户自己定义了 DataSource → 自动配置的 DataSource 不创建
>     │  用户自己定义了 RedisTemplate → 自动配置的 RedisTemplate 不创建
>     │
>     ▼ @ConditionalOnProperty（第三道门）
>        配置了 enabled=false → 不加载
>        最终真正生效的配置类
> ```
>
> **核心思想：** `@ConditionalOnClass` 管"你有没有这个能力"，`@ConditionalOnMissingBean` 管"用户有没有自己搞"，`@ConditionalOnProperty` 管"配置开关"。三层过滤保证只加载真正需要的。
### 17. 如何排除某个不想用的自动配置？比如不想用默认的 DataSource？

> 三种方式：
>
> ```java
> // ① @SpringBootApplication 注解上排除（最常用）
> @SpringBootApplication(exclude = {
>     DataSourceAutoConfiguration.class,
>     DataSourceTransactionManagerAutoConfiguration.class
> })
> public class MyApp { }
>
> // ② @EnableAutoConfiguration 上排除（拆开写时用）
> @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
> ```
>
> ```yaml
> # ③ 配置文件排除（不用改代码，不同环境排除不同的）
> spring:
>   autoconfigure:
>     exclude:
>       - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
>       - org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
> ```
>
> | 方式 | 优点 | 缺点 |
> |---|---|---|
> | `@SpringBootApplication(exclude)` | 最常用，一眼能看到 | 改了要重新编译 |
> | 配置文件 | 不用改代码，不同环境排除不同的 | 类名太长 |
> | `@EnableAutoConfiguration(exclude)` | 注解拆开写时用 | 拆开写的情况少 |
>
> **实际项目中最常用第一种**。
### 18. 如果你自己封装一个公司内部的 Starter，步骤是怎样的？需要注意什么？

> **① 建两个模块（官方推荐结构）：**
>
> ```
> xxx-spring-boot-starter           ← 空壳模块，只做依赖聚合
>   └── pom.xml（引入 autoconfigure + 第三方依赖）
>
> xxx-spring-boot-autoconfigure     ← 核心逻辑
>   ├── XxxProperties.java          ← 配置属性类
>   ├── XxxAutoConfiguration.java   ← 自动配置类
>   ├── XxxService.java             ← 核心功能
>   └── META-INF/spring/...AutoConfiguration.imports
> ```
>
> **② 核心代码：**
>
> ```java
> // 配置属性类
> @ConfigurationProperties(prefix = "xxx.sms")
> public class SmsProperties {
>     private String url;
>     private String appKey;
>     private String appSecret;
>     // getter/setter
> }
>
> // 自动配置类
> @AutoConfiguration
> @ConditionalOnClass(SmsService.class)
> @EnableConfigurationProperties(SmsProperties.class)
> public class SmsAutoConfiguration {
>
>     @Bean
>     @ConditionalOnMissingBean
>     public SmsService smsService(SmsProperties props) {
>         return new SmsService(props.getUrl(), props.getAppKey(), props.getAppSecret());
>     }
> }
> ```
>
> ```properties
> # 3.x：META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
> com.company.sms.autoconfigure.SmsAutoConfiguration
> ```
>
> ```xml
> <!-- starter 模块只做依赖聚合 -->
> <dependencies>
>     <dependency>
>         <groupId>com.company</groupId>
>         <artifactId>xxx-spring-boot-autoconfigure</artifactId>
>     </dependency>
> </dependencies>
> ```
>
> **③ 使用方引入后配一下就能用：**
>
> ```xml
> <dependency>
>     <groupId>com.company</groupId>
>     <artifactId>xxx-spring-boot-starter</artifactId>
> </dependency>
> ```
>
> **需要注意：**
>
> | 注意点 | 说明 |
> |---|---|
> | 业务逻辑放 autoconfigure，不放 starter | starter 只做依赖聚合 |
> | 必须加 `@ConditionalOnMissingBean` | 否则会覆盖用户自定义的 Bean |
> | 配置类用 `@ConfigurationProperties` | 方便用户批量配置，不用 `@Value` |
> | 提供默认值 | 用户不配也能用 |
> | 不要用 `@ComponentScan` | 通过配置文件注册，避免和用户项目冲突 |
### 19. Starter 中的自动配置类和用户自己定义的 Bean，谁的优先级更高？如果用户想覆盖 Starter 的默认 Bean 怎么做？

> **用户的 Bean 优先级更高。** Spring Boot 设计原则：用户定义的优先，自动配置的后备。
>
> 原因是自动配置类的 `@Bean` 方法都加了 `@ConditionalOnMissingBean`，容器里已有同类型 Bean 就不创建。
>
> ```java
> @AutoConfiguration
> public class RedisAutoConfiguration {
>     @Bean
>     @ConditionalOnMissingBean(name = "redisTemplate")  // 容器里没有才创建
>     public RedisTemplate<String, Object> redisTemplate() {
>         return new RedisTemplate<>();
>     }
> }
> ```
>
> **用户覆盖 Starter 默认 Bean 的三种方式：**
>
> ```java
> // 方式一：自己定义同类型 Bean（最常用）
> @Configuration
> public class MyRedisConfig {
>     @Bean
>     public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
>         RedisTemplate<String, Object> template = new RedisTemplate<>();
>         template.setConnectionFactory(factory);
>         template.setKeySerializer(new StringRedisSerializer());
>         return template;
>     }
> }
> ```
>
> ```yaml
> # 方式二：通过配置文件覆盖属性
> spring:
>   data:
>     redis:
>       host: 192.168.1.100
> ```
>
> ```java
> // 方式三：排除整个自动配置类
> @SpringBootApplication(exclude = {RedisAutoConfiguration.class})
> ```
### 20. 你在项目中实际封装过哪些 Starter？解决了什么问题？

> 项目中用过集团内部封装的 **OpenAPI 调用 Starter**。
>
> **背景：** 集团内部服务之间通过第三方 OpenAPI 平台进行调用，每次调用都要处理签名、鉴权、加密、重试、日志记录等通用逻辑，每个服务都重复写一遍。
>
> **集团封装的 Starter 做了什么：**
> - 统一的签名算法、鉴权令牌获取、请求加密/解密
> - 统一的异常处理、重试机制、调用日志记录
> - 通过 `@ConfigurationProperties` 暴露配置（appId、appSecret、平台地址等）
> - 提供自动注册的 `OpenApiClient` Bean
>
> ```xml
> <!-- 引入 Starter -->
> <dependency>
>     <groupId>com.group</groupId>
>     <artifactId>openapi-spring-boot-starter</artifactId>
> </dependency>
> ```
>
> ```yaml
> # 配置应用信息
> openapi:
>   app-id: order-service
>   app-secret: xxx
>   server-url: https://openapi.group.com
> ```
>
> ```java
> // 直接注入使用，不用关心签名、鉴权等细节
> @Service
> public class OrderService {
>     @Autowired
>     private OpenApiClient openApiClient;
>
>     public UserInfo getUser(String userId) {
>         return openApiClient.invoke("user-service", "/api/user/" + userId, UserInfo.class);
>     }
> }
> ```
>
> **解决的问题：** 各业务服务不用再重复写调用 OpenAPI 平台的通用逻辑，引入 Starter + 配置即可使用，减少了大量重复代码和维护成本。

## 三、IoC 容器与 Bean 生命周期（8 题）

### 22. IoC（控制反转）你是怎么理解的？它解决了什么问题？

> **IoC（Inversion of Control）**——对象的创建和依赖管理不由你自己 new，而是交给 Spring 容器来管。"控制"指的是对象创建和依赖关系的管理权，以前自己 new（正转），现在交给容器（反转）。
>
> ```java
> // 没有 IoC：自己 new，对象之间硬绑死
> public class OrderService {
>     private UserService userService = new UserService();     // 换实现要改代码
>     private PayService payService = new AliPayService();     // 换微信支付要改代码
> }
>
> // 有 IoC：不 new，让 Spring 注入
> public class OrderService {
>     private final UserService userService;   // Spring 负责创建和注入
>     private final PayService payService;     // 换实现只改配置或加个 @Bean
>
>     public OrderService(UserService userService, PayService payService) {
>         this.userService = userService;
>         this.payService = payService;
>     }
> }
> ```
>
> | 没有 IoC | 有 IoC |
> |---|---|
> | 对象之间 new 来 new 去，耦合死了 | 只声明依赖，不管谁创建的 |
> | 换实现要改业务代码 | 加个 `@Bean` 或换个 `@Qualifier` 就行 |
> | 循环依赖自己解决不了 | Spring 三级缓存自动处理 |
> | 单例、多例自己管理 | `@Scope` 一行搞定 |
> | 事务、AOP 要自己写模板代码 | 注解一加，Spring 自动处理 |
>
> ```java
> // 实际例子：换支付方式只改 @Qualifier，业务代码不用动
> @Service
> public class OrderService {
>     private final PayService payService;
>
>     public OrderService(@Qualifier("wechatPay") PayService payService) {
>         this.payService = payService;
>     }
> }
> ```
>
> **面试答法：** IoC 就是把对象的创建和依赖管理从代码里剥离出来交给 Spring 容器。解决了对象之间的硬耦合问题——你只声明需要什么，Spring 负责创建和注入。换个实现改配置就行，不用改业务代码。

### 23. Spring 容器启动时，Bean 的完整生命周期是怎样的？每一步都在做什么？

> **核心四阶段：实例化 → 属性注入 → 初始化 → 销毁。**
>
> ```
> ① 实例化（new）
>    ↓
> ② 属性注入（@Autowired、setter）
>    ↓
> ③ BeanNameAware / BeanFactoryAware / ApplicationContextAware 回调
>    ↓
> ④ BeanPostProcessor.postProcessBeforeInitialization()  ← 前置处理
>    ↓
> ⑤ @PostConstruct 方法（InitializingBean.afterPropertiesSet）  ← 初始化
>    ↓
> ⑥ init-method（自定义初始化方法）
>    ↓
> ⑦ BeanPostProcessor.postProcessAfterInitialization()  ← 后置处理（AOP 代理在这里生成）
>    ↓
> ⑧ Bean 就绪，可以使用
>    ↓
> ⑨ @PreDestroy 方法（DisposableBean.destroy）  ← 销毁
>    ↓
> ⑩ destroy-method（自定义销毁方法）
> ```
>
> **各阶段在做什么：**
>
> | 阶段 | 做了什么 | 你能插手的地方 |
> |---|---|---|
> | **实例化** | 反射调用构造器，创建对象（还没设属性） | 构造器注入在这步完成 |
> | **属性注入** | 填充 `@Autowired`、`@Value`、setter 的依赖 | — |
> | **Aware 回调** | 让 Bean 拿到容器的一些信息（自己的名字、ApplicationContext 等） | 实现 `ApplicationContextAware` 接口 |
> | **前置处理** | `BeanPostProcessor` 的 `before` 方法 | — |
> | **初始化** | 调用 `@PostConstruct` → `afterPropertiesSet()` → `init-method` | `@PostConstruct` 做初始化逻辑 |
> | **后置处理** | `BeanPostProcessor` 的 `after` 方法 | **AOP 代理在这步生成** |
> | **销毁** | 调用 `@PreDestroy` → `destroy()` → `destroy-method` | `@PreDestroy` 释放资源 |
>
> ```java
> @Component
> public class OrderService implements ApplicationContextAware, InitializingBean, DisposableBean {
>
>     @Autowired
>     private UserService userService;  // ② 属性注入
>
>     private ApplicationContext ctx;
>
>     @Override
>     public void setApplicationContext(ApplicationContext ctx) {  // ③ Aware 回调
>         this.ctx = ctx;
>     }
>
>     @PostConstruct
>     public void init() {  // ⑤ 初始化
>         System.out.println("Bean 初始化完成");
>     }
>
>     @Override
>     public void afterPropertiesSet() {  // ⑤ InitializingBean 接口方法
>         System.out.println("属性都设好了");
>     }
>
>     @PreDestroy
>     public void cleanup() {  // ⑨ 销毁前释放资源
>         System.out.println("释放资源");
>     }
>
>     @Override
>     public void destroy() {  // ⑨ DisposableBean 接口方法
>         System.out.println("Bean 销毁");
>     }
> }
> ```
>
> **面试答法：** 四大阶段——实例化 → 属性注入 → 初始化 → 销毁。初始化阶段依次调用 `@PostConstruct` → `afterPropertiesSet()` → `init-method`，销毁阶段依次调用 `@PreDestroy` → `destroy()` → `destroy-method`。**AOP 代理在后置处理（`postProcessAfterInitialization`）那步生成。**
### 24. `BeanFactory` 和 `ApplicationContext` 的区别是什么？你平时用的是哪个？

> **`BeanFactory`** 是 Spring 最底层的容器接口，只提供最基本的 Bean 管理。**`ApplicationContext`** 是它的子接口，在基础上加了一堆高级功能。
>
> | 功能 | BeanFactory | ApplicationContext |
> |---|---|---|
> | **Bean 的创建和获取** | ✅ | ✅ |
> | **BeanPostProcessor 自动注册** | ❌ 要手动注册 | ✅ 自动识别并注册 |
> | **BeanFactoryPostProcessor 自动注册** | ❌ 手动 | ✅ 自动 |
> | **国际化（i18n）** | ❌ | ✅ |
> | **事件发布机制** | ❌ | ✅ `publishEvent()` |
> | **AOP 支持** | ❌ 要手动配置 | ✅ 自动识别 `@Aspect` |
> | **自动配置（Spring Boot）** | ❌ | ✅ |
> | **Bean 获取时机** | **懒加载**，`getBean()` 时才创建 | **预加载**，启动时创建所有单例 |
>
> **平时用的是 `ApplicationContext`。** Spring Boot 的 `SpringApplication.run()` 创建的就是 `ApplicationContext`，几乎不会直接用到 `BeanFactory`。
>
> **面试答法：** `ApplicationContext` 是 `BeanFactory` 的子接口，加了事件发布、AOP、国际化、自动配置等功能。`BeanFactory` 懒加载，`ApplicationContext` 预加载。平时用的全是 `ApplicationContext`，Spring Boot 启动创建的就是它。
### 25. `@Component` 和 `@Bean` 的区别是什么？什么场景用 `@Bean`？

| 维度 | `@Component` | `@Bean` |
|------|-------------|---------|
| **作用位置** | 类上 | 方法上（必须在 `@Configuration` 类内） |
| **注册方式** | 类路径扫描（Spring 自动发现） | 手动声明，显式调用工厂方法 |
| **归属权** | 标注的类必须**你自己能改源码** | 适用于**第三方类**（改不了源码） |
| **灵活性** | 单一用途，全量扫描注册 | 可加条件判断、循环创建多个实例 |

> 一句话：**能改源码用 `@Component`，改不了或需要灵活控制用 `@Bean`**。

### 26. Bean 的作用域有哪些？`singleton`、`prototype`、`request`、`session` 分别在什么场景使用？

| 作用域 | 生命周期 | 典型场景 |
|--------|---------|---------|
| **singleton** | 容器生命周期内只有一个实例（默认） | 无状态服务、工具类、配置类 |
| **prototype** | 每次 `getBean()` 都创建新实例 | 有状态对象、每次请求需要独立实例 |
| **request** | 每次 HTTP 请求创建一个新实例（仅 Web 环境） | 请求级别的数据封装（如 `RequestContext`） |
| **session** | 每个 HTTP Session 创建一个新实例（仅 Web 环境） | 用户会话级别的数据（如购物车） |
| **application** | 整个 ServletContext 生命周期一个实例 | 应用级别的全局配置 |
| **websocket** | 每个 WebSocket 会话一个实例 | WebSocket 通信上下文 |

**判断标准**：这个对象有没有**可变状态**。

- **无状态** → `singleton`：`Service`、`Dao`、工具类，线程安全，复用实例
- **有状态** → `prototype`：每次请求需要独立数据，用完即弃

### 各作用域实战示例

```java
// ==================== singleton（默认） ====================
@Service
public class UserService {
    // 无状态，线程安全，整个应用共享一个实例
    public User getUser(Long id) { ... }
}

// ==================== prototype ====================
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Component
public class ExcelExportTask {
    // 每次导出都是一个新任务对象，各自持有独立的文件路径和进度
    private String filePath;
    private int progress;

    public void export() { ... }
}

// ==================== request ====================
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContextHolder {
    // 每个 HTTP 请求一个实例，存当前请求的 traceId、用户信息等
    private String traceId;
    private Long userId;

    // 注意：需配合代理模式，否则 singleton 注入时会报错
}

// ==================== session ====================
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UserShoppingCart {
    // 每个用户 Session 一个购物车，登录期间跨请求共享
    private final List<CartItem> items = new ArrayList<>();

    public void addItem(CartItem item) { items.add(item); }
    public List<CartItem> getItems() { return items; }
}

// ==================== application ====================
@Component
@Scope(WebApplicationContext.SCOPE_APPLICATION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class AppConfigHolder {
    // 整个应用生命周期只有一个实例，类似 ServletContext 属性
    private Map<String, String> configMap;
}

// ==================== websocket ====================
@Component
@Scope(WebApplicationContext.SCOPE_WEBSOCKET, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class WebSocketSessionContext {
    // 每个 WebSocket 连接一个实例，存储该连接的会话信息
    private String sessionId;
    private String userName;
}
```

> **注意**：`request`、`session`、`application`、`websocket` 均需在 Web 环境下使用，且需要设置 `proxyMode = TARGET_CLASS`（或 `INTERFACES`）做**作用域代理**，否则注入到 `singleton` 时会因为生命周期不匹配报错。

### 面试加分点：prototype 注入到 singleton 的问题

`prototype` Bean 被注入到 `singleton` 时，**Spring 只创建一次 prototype 实例**（注入时创建），之后 singleton 永远用的是同一个 prototype 实例。原因：singleton 创建时依赖注入只发生一次。

**解决方案**：
1. 改用 `ObjectProvider<Bean>`（推荐），每次调用 `getObject()` 获取新实例
2. 不用注入，通过 `ApplicationContext.getBean()` 手动获取

```java
// 方案一：ObjectProvider（推荐）
@Service
public class OrderService {
    @Autowired
    private ObjectProvider<OrderContext> orderContextProvider;

    public void handle() {
        OrderContext ctx = orderContextProvider.getObject();  // 每次都是新的
    }
}

// 方案二：手动获取
@Service
public class OrderService {
    @Autowired
    private ApplicationContext ctx;

    public void handle() {
        OrderContext ctx2 = ctx.getBean(OrderContext.class);  // 每次都是新的
    }
}
```

### 27. 如果一个 `prototype` 作用域的 Bean 被注入到 `singleton` 的 Bean 中，会发生什么？怎么解决？

### 28. Spring 怎么解决循环依赖的？三级缓存各自存的是什么？为什么必须是三级，两级行不行？

参看  Spring核心知识.md

---

### 29. 构造器注入的循环依赖能解决吗？为什么？

**不能解决，直接抛异常。**
