# 牛客网 Spring Boot 实战面试 100 题

> 来源：牛客网面经 + 大厂高频真题 + 场景设计题
> 说明：本题库偏向**实战与场景**，每题均为真实面试出现过的题目

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

---


## 四、AOP 面向切面编程（5 题）

### 30. AOP 的实现原理是什么？JDK 动态代理和 CGLIB 代理有什么区别？Spring Boot 默认用哪个？

**实现原理：**

Spring AOP 基于**动态代理**。容器启动时，通过 `BeanPostProcessor`（`AbstractAutoProxyCreator`）对目标 Bean 创建代理对象。调用时先走代理的拦截逻辑（advice），再调用目标方法，实现横切逻辑（日志、事务、缓存等）与业务代码解耦。

> **核心流程：** 目标 Bean 初始化后 → `postProcessAfterInitialization()` → 判断是否有切面匹配 → 创建代理对象替换原 Bean 放入容器。

---

**JDK 动态代理 vs CGLIB 代理**

| 维度 | JDK 动态代理 | CGLIB 代理 |
|------|------------|-----------|
| 实现方式 | `java.lang.reflect.Proxy` 生成接口实现类 | 字节码生成（ASM），子类化目标类 |
| 目标要求 | 目标类必须有**接口** | 目标类无需接口（但必须是非 `final` 类） |
| 性能 | 创建慢、调用快（JDK 1.8+ 已优化） | 创建快（有缓存）、调用略慢 |
| 方法拦截 | 只能拦截**接口方法** | 可拦截类及父类的 public 方法 |

> **关键限制：** 目标类为 `final` 或方法为 `final` 时，CGLIB 无法代理。

**Spring Boot 默认用哪个？**

- Spring Boot（Spring 5+）**默认使用 CGLIB**（`spring.aop.proxy-target-class=true`）。
- 只有当显式设置 `proxy-target-class=false` 且目标类实现了接口时，才回退到 JDK 动态代理。
- 即：**不设参数 → 一律 CGLIB**。

```java
// Spring Boot 默认配置（application.properties 无需额外设置）
spring.aop.proxy-target-class=true
spring.aop.auto=true
```

```java
// 强制使用 JDK 动态代理
spring.aop.proxy-target-class=false
```

### 31. `@Before`、`@After`、`@AfterReturning`、`@AfterThrowing`、`@Around` 的执行顺序是怎样的？

**正常流程：**

```
@Around（前半段）
  → @Before
    → 目标方法执行
  → @AfterReturning
  → @After
@Around（后半段）
```

**异常流程：**

```
@Around（前半段）
  → @Before
    → 目标方法抛异常
  → @AfterThrowing
  → @After
@Around（后半段，catch 异常后不再继续）
```

> `@After` 类似 finally，无论正常还是异常都会执行。`@AfterReturning` 和 `@AfterThrowing` 二选一，不会同时触发。`@Around` 最外层，可以决定是否调用目标方法。
### 32. 你在项目中用 AOP 做过哪些事？具体怎么实现的？

> 回答框架：做了什么 → 为什么用 AOP → 怎么做的（4 步）

**我做过的两个场景：枚举翻译、用户数据脱敏。**

#### 场景一：枚举翻译

**业务背景**：接口返回的用户状态是 `0`/`1`/`2`，前端需要展示"正常"/"禁用"/"注销"。如果每个接口手动翻译，重复代码太多。

**为什么用 AOP**：所有查询接口返回后都要做一次翻译，属于**横切关注点**，适合用切面统一处理。

**实现步骤**：

1. **明确业务是否需要做切面** — 多个接口都有同样的翻译需求，且翻译逻辑无业务耦合
2. **明确切入时机** — 用 `@AfterReturning`，目标方法成功返回后对响应体做翻译
3. **写切点表达式** — 匹配所有返回带 `@EnumTranslate` 注解字段的 DTO 的 Controller 方法
4. **完成切面** — 反射扫描返回对象中带注解的字段，查枚举映射表替换值

```java
@Aspect
@Component
public class EnumTranslateAspect {

    @AfterReturning(pointcut = "execution(* com.example.controller..*.*(..))", returning = "result")
    public void translate(Result<?> result) {
        Object data = result.getData();
        if (data != null) {
            translateEnums(data);  // 反射扫描 @EnumTranslate 字段并翻译
        }
    }
}
```

#### 场景二：用户数据脱敏

**业务背景**：用户列表、详情等接口返回的手机号、身份证号需要部分隐藏（如 `138****1234`）。

**为什么用 AOP**：涉及用户敏感数据的接口很多，逐个手写脱敏逻辑容易遗漏，用切面统一拦截保证不漏。

**实现步骤**：

1. **明确业务是否需要做切面** — 涉及隐私合规，必须全量覆盖不能遗漏，适合切面兜底
2. **明确切入时机** — 同样用 `@AfterReturning`，在数据返回给前端之前脱敏
3. **写切点表达式** — 匹配返回用户信息的接口
4. **完成切面** — 反射扫描带 `@SensitiveField(type=PHONE)` 注解的字段，按类型脱敏

```java
@Aspect
@Component
public class DataMaskingAspect {

    @AfterReturning(pointcut = "execution(* com.example.controller..*.*(..))", returning = "result")
    public void mask(Result<?> result) {
        Object data = result.getData();
        if (data != null) {
            maskSensitiveFields(data);  // 反射扫描 @SensitiveField 字段并脱敏
        }
    }
}
```

#### 总结：做切面的 4 步方法论

| 步骤 | 要回答的问题 | 关键决策 |
|------|-------------|---------|
| ① 明确业务 | 这个需求是横切关注点吗？多个地方重复出现？ | 不是横切关注点就别硬用 AOP |
| ② 确定时机 | 方法前（`@Before`）？方法后（`@AfterReturning`）？环绕（`@Around`）？ | 枚举翻译/脱敏都是改返回值，用 `@AfterReturning` |
| ③ 写切点 | 切哪些包、哪些类、哪些方法？ | 范围太大会影响性能，太窄会遗漏 |
| ④ 完成切面 | 注解驱动 or 表达式匹配？反射还是序列化拦截？ | 注解驱动（`@EnumTranslate`）更灵活，表达式匹配更省事 |

> 面试加分点：主动说明**为什么不用其他方案**——枚举翻译也可以在 Service 层做，但每个方法都要调一次；脱敏也可以用 Jackson 序列化器，但切面方案更统一、不依赖具体框架。
### 33. AOP 的自调用问题是什么？为什么同一个类里调用 `@Transactional` 方法不走代理？怎么解决？

**原因**：代理对象和目标对象是两个不同的对象。代理确实重写了所有方法，但方法内部的 `this` 指向的是**目标对象**，不是代理对象，所以 `this.methodB()` 绕过了代理。

```java
// 代理对象（Spring 生成的子类）
class UserService$$Proxy {
    private UserService target;  // 持有真正的目标对象

    @Override
    public void methodA() {
        // 开启事务（切面逻辑）
        target.methodA();  // 调用目标对象的方法
        // 提交事务
    }

    @Override
    public void methodB() {
        // 开启事务（切面逻辑）
        target.methodB();
        // 提交事务
    }
}
```

```java
// 目标对象（你写的原始类）
@Service
public class UserService {

    @Transactional
    public void methodA() {
        // this 是 UserService 本身，不是 Proxy
        // 所以绕过了代理，事务不生效
        this.methodB();
    }

    @Transactional
    public void methodB() {
        // ...
    }
}
```

**调用链对比：**

```
外部调 methodA()：
  Controller → Proxy.methodA()（事务生效）
                → target.methodA()
                    → this.methodB()  ← this 是 target，不是 Proxy
                    → target.methodB()（没有经过 Proxy，事务不生效）

外部直接调 methodB()：
  Controller → Proxy.methodB()（事务生效）✅
```

**解决方式**（选一种）：

1. **注入自身**：`@Autowired` 把自己注入进来，通过代理调用
2. **`AopContext.currentProxy()`**：获取当前代理对象调用（需开启 `@EnableAspectJAutoProxy(exposeProxy = true)`）
3. **拆到另一个类**：把方法移到别的 Service，天然走代理（推荐）

> 一句话：代理包的是 target 的外壳，但 target 内部的 `this` 永远指向自己，不会指向代理。
### 34. Spring AOP 和 AspectJ 有什么区别？各自适用什么场景？
### 35. Spring 的事务传播机制有哪几种？`REQUIRED`、`REQUIRES_NEW`、`NESTED` 有什么区别？你在项目中怎么选的？

**7 种传播行为一览：**

| 传播行为 | 有事务时 | 没事务时 | 用途 |
|---------|---------|---------|------|
| **REQUIRED**（默认） | 加入当前事务 | 新建一个 | 大多数场景的默认选择 |
| **SUPPORTS** | 加入当前事务 | 非事务执行 | 查询方法 |
| **MANDATORY** | 加入当前事务 | 抛异常 | 强制要求在事务中调用 |
| **REQUIRES_NEW** | 挂起当前事务，新建一个 | 新建一个 | 独立事务，不受外层回滚影响 |
| **NOT_SUPPORTED** | 挂起当前事务，非事务执行 | 非事务执行 | 不需要事务的操作 |
| **NEVER** | 抛异常 | 非事务执行 | 强制要求不在事务中调用 |
| **NESTED** | 在当前事务中创建保存点（Savepoint） | 新建一个 | 嵌套事务，内层可独立回滚 |

**核心三者的区别（重点）：**

```
REQUIRED：methodA 和 methodB 共用同一个事务
  methodA（事务T1）
    → methodB（加入 T1）
  B 异常 → A 和 B 一起回滚

REQUIRES_NEW：methodB 挂起 A 的事务，自己开一个全新的
  methodA（事务T1，挂起）
    → methodB（事务T2，全新）
  B 异常 → 只回滚 T2，T1 不受影响（除非 A 也抛异常）

NESTED：methodB 在 A 的事务中设置一个保存点
  methodA（事务T1）
    → Savepoint
    → methodB（嵌套在 T1 内）
  B 异常 → 回滚到 Savepoint，A 继续执行
  A 异常 → A 和 B 一起回滚
```

| 对比 | REQUIRED | REQUIRES_NEW | NESTED |
|------|---------|-------------|--------|
| 事务数量 | 1 个 | 2 个（内层全新） | 1 个（Savepoint 嵌套） |
| 内层回滚影响外层 | ✅ 一起回滚 | ❌ 不影响 | ❌ 回滚到保存点，外层继续 |
| 外层回滚影响内层 | ✅ 一起回滚 | ❌ 不影响 | ✅ 一起回滚 |
| 性能 | 最好 | 较差（要挂起+新建连接） | 折中（Savepoint 很轻） |

**项目中怎么选：**

- **日志/审计记录** → 用 `REQUIRES_NEW`，主业务回滚了日志也不能丢
- **批量处理中某条失败不影响整体** → 用 `NESTED`，失败回滚到保存点，继续处理下一条
- **其余全部用 `REQUIRED`**（默认值），没必要别换

> 面试一句话：绝大多数场景用 REQUIRED 就够；需要独立提交/回滚用 REQUIRES_NEW；想省钱又想内层可独立回滚用 NESTED。
### 36. `@Transactional` 注解在什么情况下会失效？列举至少 5 种场景。

| # | 场景 | 原因 | 解决 |
|---|------|------|------|
| 1 | **同类自调用** | `this.methodB()` 绕过代理 | 拆类 / `AopContext.currentProxy()` |
| 2 | **方法非 public** | Spring AOP 只拦截 public 方法 | 改为 public |
| 3 | **方法被 final/static 修饰** | CGLIB 无法重写 final/static 方法 | 去掉 final/static |
| 4 | **异常被 try-catch 吞掉** | 事务感知不到异常，不会回滚 | catch 后手动 `throw` 或 `setRollbackOnly()` |
| 5 | **抛出 checked 异常** | 默认只回滚 `RuntimeException` 和 `Error` | `@Transactional(rollbackFor = Exception.class)` |
| 6 | **数据库引擎不支持事务** | MyISAM 不支持事务 | 用 InnoDB |
| 7 | **Bean 未被 Spring 管理** | 没加 `@Service` 等注解，不是 Spring Bean | 加上注解 |
| 8 | **传播行为设错** | `NOT_SUPPORTED` / `NEVER` 本身就不用事务 | 检查 propagation 设置 |

> 面试说前 5 个就够了，第 5 个顺带提一嘴 `rollbackFor = Exception.class` 是最佳实践。
### 37. 自调用导致事务失效怎么解决？除了把方法拆到另一个类还有别的办法吗？

> 原理同第 33 题：`this.methodB()` 绕过代理，事务不生效。核心就是拿到代理对象来调用。

**三种解决方式：**

**① 注入自身（最常用）**

```java
@Service
public class UserService {
    @Autowired
    private UserService self;  // 注入自己的代理对象

    public void methodA() {
        self.methodB();  // 通过代理调用，事务生效
    }

    @Transactional
    public void methodB() { ... }
}
```

> 注意：不会循环依赖，因为 Spring 三级缓存会先暴露早期引用。但如果构造器里就用会报 NPE。

**② AopContext 获取当前代理**

```java
// 启动类加：
@EnableAspectJAutoProxy(exposeProxy = true)

// 使用：
((UserService) AopContext.currentProxy()).methodB();
```

> 优点：不用注入自己，代码侵入小。缺点：依赖 Spring AOP 内部 API，且必须在 Spring 管理的线程内调用。

**③ 拆到另一个类（最干净）**

```java
@Service
public class UserService {
    @Autowired
    private OrderService orderService;

    public void methodA() {
        orderService.methodB();  // 天然走代理
    }
}
```

> 优点：没有 hack，符合设计原则。缺点：有时业务上两个方法就是属于同一个类，强行拆不合理。

**面试怎么答**：先说三种方式，然后说"生产中简单场景用 `AopContext`，复杂场景优先拆类"。
### 38. 事务的隔离级别有哪些？分别能解决什么并发读问题？

**三种并发读问题：**

| 问题 | 含义 | 场景 |
|------|------|------|
| **脏读** | 读到了其他事务未提交的数据 | A 修改了余额但未提交，B 读到了修改后的值，A 回滚，B 读到的就是脏数据 |
| **不可重复读** | 同一事务内两次读同一行，结果不同 | A 两次查余额，中间 B 修改并提交了，两次结果不一样 |
| **幻读** | 同一事务内两次查询，行数不同 | A 查 age>20 的用户有 5 条，中间 B 插入了一条，再查变成 6 条 |

**四个隔离级别：**

| 隔离级别 | 脏读 | 不可重复读 | 幻读 | 性能 |
|---------|------|-----------|------|------|
| **READ UNCOMMITTED**（读未提交） | ❌ 可能 | ❌ 可能 | ❌ 可能 | 最快 |
| **READ COMMITTED**（读已提交） | ✅ 避免 | ❌ 可能 | ❌ 可能 | 快 |
| **REPEATABLE READ**（可重复读） | ✅ 避免 | ✅ 避免 | ❌ 可能 | 较慢 |
| **SERIALIZABLE**（串行化） | ✅ 避免 | ✅ 避免 | ✅ 避免 | 最慢 |

> MySQL InnoDB 默认 **REPEATABLE READ**，但通过 MVCC + Next-Key Lock 实际上也能避免大部分幻读。

**Spring 怎么设置**：`@Transactional(isolation = Isolation.REPEATABLE_READ)`

> 面试一句话：四个级别逐级增强，代价是并发性能下降。MySQL 默认 REPEATABLE READ，一般不需要改。Oracle 默认 READ COMMITTED。
### 39. 分布式事务你是怎么处理的？Seata 的 AT 模式和 TCC 模式有什么区别？
### 40. 为什么说"不要在事务里做 RPC 调用和 IO 操作"？你遇到过这个问题吗？
### 41. 声明式事务和编程式事务各有什么优缺点？你一般在什么场景用编程式事务？

## 六、Spring MVC 核心技术（6 题）

### 42. Spring MVC 一次请求的完整处理流程是怎样的？从 DispatcherServlet 开始一步步说清楚。
### 43. 拦截器（Interceptor）和过滤器（Filter）的区别是什么？执行顺序是怎样的？
### 44. 如何在 Spring Boot 中做统一的参数校验？`@Valid`、`@Validated`、自定义校验注解怎么用？
### 45. 全局异常处理怎么实现？`@ControllerAdvice` + `@ExceptionHandler` 的原理是什么？
### 46. 统一返回格式怎么封装？你在项目中是怎么做的？
### 47. 如何在 Spring Boot 中做接口的幂等性校验？有几种方案？

## 七、数据库与持久层（7 题）

### 48. Spring Boot 怎么整合 MyBatis / MyBatis-Plus？你做过哪些配置？
### 49. MyBatis-Plus 的分页插件原理是什么？你在项目中怎么用的？
### 50. MyBatis 的 `#{}` 和 `${}` 有什么区别？为什么要尽量用 `#{}`？
### 51. SQL 执行慢你怎么排查和优化？说说你的思路和工具。
### 52. 什么时候该建索引？联合索引的"最左前缀"原则是什么？你在项目中有没有因为索引使用不对导致过慢查询？
### 53. 分库分表后，怎么处理跨库的分页、排序、聚合查询？ShardingSphere 是怎么解决这些问题的？
### 54. 读写分离你是怎么做的？主从延迟导致读到旧数据怎么处理？

## 八、缓存实战（9 题）

### 55. 缓存穿透、缓存击穿、缓存雪崩分别是什么意思？你在项目中怎么解决的？
### 56. 如何保证数据库和缓存的双写一致性？Cache-Aside 模式的具体步骤是什么？延迟双删怎么做？
### 57. Redis 在你的项目中具体用了哪些场景？每个场景用的什么数据结构？为什么选这个结构？
### 58. 热点 Key 突然过期导致大量请求打到数据库，怎么处理？
### 59. 大 Key（Big Key）有什么危害？怎么发现和拆解？
### 60. Redis 分布式锁是怎么实现的？`SETNX` + Lua 脚本和 Redisson 的 RedLock 有什么区别？
### 61. 用 Redis 实现一个延时队列怎么做？有哪些方案？
### 62. 本地缓存（Caffeine）和分布式缓存（Redis）怎么搭配使用？多级缓存的更新策略是什么？
### 63. Redis 的内存淘汰策略有哪些？你在项目中用的是哪个？为什么？

## 九、消息队列实战（7 题）

### 64. 消息队列在你的项目中解决了什么问题？为什么不用同步调用而要用 MQ？
### 65. 如何保证消息不丢失？从生产者、Broker、消费者三端分别说说。
### 66. 消息重复消费怎么处理？你的系统是怎么做幂等的？
### 67. 消息堆积了怎么办？积压几百万条消息你怎么快速处理？
### 68. 顺序消息怎么保证？什么场景需要顺序消息？
### 69. Kafka 的消费者组（Consumer Group）和分区（Partition）之间是什么关系？
### 70. Kafka 的 ISR 机制是什么？`acks=all` 和 `min.insync.replicas` 怎么配合保证可靠性？

## 十、分布式与微服务（10 题）

### 71. 你把单体项目拆成微服务的依据是什么？你是怎么划分服务边界的？
### 72. 服务注册与发现是怎么工作的？Nacos 和 Eureka 有什么核心区别？
### 73. 负载均衡策略有哪些？Ribbon 的轮询、随机、加权轮询分别怎么用？
### 74. 服务间调用（OpenFeign / Dubbo）你怎么选的？底层原理是什么？
### 75. 微服务网关（Gateway / Zuul）的作用是什么？你在网关层做了哪些事（鉴权、限流、日志、路由）？
### 76. 如何实现一个分布式 ID 生成器？雪花算法有什么优缺点？时钟回拨怎么处理？
### 77. 分布式 Session 怎么解决？Spring Session + Redis 的原理是什么？
### 78. 分布式锁除了 Redis 实现还有哪些方案？ZooKeeper 实现和 Redis 实现各有什么优缺点？
### 79. CAP 定理和 BASE 理论怎么理解？在你的项目里是怎么权衡的？
### 80. 微服务链路追踪怎么实现？TraceID 如何在服务间传递？

## 十一、安全与鉴权（4 题）

### 81. Spring Security 的认证和授权流程是怎样的？过滤器链里有哪几个关键的 Filter？
### 82. 无状态登录（JWT Token）怎么实现？Token 过期刷新机制你怎么设计的？
### 83. 如何防止 CSRF 和 XSS 攻击？Spring Security 默认做了什么？你还做了哪些额外防护？
### 84. OAuth2.0 的授权码模式和密码模式有什么区别？你在项目中用的是哪种？

## 十二、性能优化与监控（6 题）

### 85. Spring Boot 应用启动慢怎么排查和优化？
### 86. 线上接口响应突然变慢，你的排查思路是什么？
### 87. 怎么定位 JVM 的 CPU 飙高和内存泄漏问题？用什么工具？
### 88. Spring Boot Actuator 你用了哪些端点？如何自定义健康检查？
### 89. 如何建设一个统一的日志收集和告警体系？你的项目里是怎么做的？
### 90. 线上出了问题你怎么快速回滚和止损？有什么预案？

## 十三、场景设计题（7 题）

### 91. 如何设计一个秒杀系统？从限流、库存扣减、下单、支付整个链路说说你的方案。

> 详见：[如何设计一个秒杀系统](../场景题/如何设计一个秒杀系统.md)

### 92. 接口的 QPS 从 1000 突然涨到 10000，你有哪些手段保证系统不崩？

> **核心思路**：不要硬扛，分层设防——能挡的挡在外面，能等的排队慢慢处理，实在扛不住了也能体面降级。

---

**全景防御链（从外到内）：**

```
用户请求
  → ① CDN / 静态化 —— 能用缓存顶的绝不打到服务端
  → ② 限流 —— 超出能力的请求直接拒绝，保护系统
  → ③ 缓存 —— Redis 扛读，别让流量穿透到 DB
  → ④ 削峰填谷 —— MQ 把突发请求熨平，慢慢消费
  → ⑤ 降级 —— 非核心功能关掉，保核心链路
  → ⑥ 扩容 —— 加机器，但这需要时间
```

---

**① 限流 —— 第一道防线，最重要的手段**

| 限流算法 | 原理 | 场景 |
|---------|------|------|
| **令牌桶** | 固定速率放令牌，请求拿不到令牌就拒绝 | 容忍突发，最常用 |
| **漏桶** | 请求进桶，固定速率流出 | 严格平滑，不允许突发 |
| **滑动窗口** | 统计最近 N 秒的请求数，超过阈值拒绝 | 简单精确，Sentinel 默认 |

```java
// Sentinel 限流 —— 注解一行搞定
@SentinelResource(
    value = "getOrderDetail",
    blockHandler = "rateLimitBlock"   // 被限流时走这个方法
)
public OrderDetail getOrderDetail(String orderId) {
    return orderService.getDetail(orderId);
}

// 限流后的处理：返回提示
public OrderDetail rateLimitBlock(String orderId, BlockException e) {
    throw new BizException("系统繁忙，请稍后重试");
}
```

```yaml
# Sentinel 规则（控制台/配置文件）
# QPS 超过 8000 就限流
- resource: getOrderDetail
  grade: QPS
  count: 8000           # 单机 QPS 上限
  controlBehavior: 0    # 直接拒绝
```

**面试要点**：限流不是为了让用户体验更好，是**为了保护系统不被冲垮**——少部分人被拒绝，好过所有人一起挂。

---

**② 缓存 —— 把流量挡在 DB 前面**

> 10 倍流量打过来，绝大部分是读。Redis 单机扛 10w+ QPS 很轻松，MySQL 可能几千就顶不住了。

```
用户 → Redis（有就返回） → 没有 → MySQL → 回写 Redis
        ↑ 99% 命中                        ↑ 1% 穿透
```

| 手段 | 做什么 |
|------|--------|
| **热点数据预加载** | 凌晨把今天要用的数据提前加载到 Redis |
| **多级缓存** | 本地 Caffeine（微秒级）→ Redis（毫秒级）→ DB |
| **缓存不过期** | 热点 Key 逻辑过期（异步刷新），不设物理 TTL |
| **布隆过滤器** | 拦截不存在的 Key，防缓存穿透打到 DB |

---

**③ 削峰填谷 —— MQ 把突发请求熨平**

> 限流是"直接拒绝"，MQ 是"排队等着"。写操作不适合直接拒绝（用户下单总不能说"系统繁忙"就丢了），应该用 MQ 缓冲。

```
瞬时 1w QPS → 1w 条消息进 MQ → 消费者稳定 2000/s 消费 → 5 秒处理完
                                   ↑ 峰值被熨平了
```

```java
// 下单不直接写 DB，先丢 MQ
@PostMapping("/order")
public Result createOrder(@RequestBody OrderDTO order) {
    // 快速校验后直接发 MQ，立即返回
    rocketMQTemplate.send("order-topic", order);
    return Result.ok("下单成功，处理中");
}

// 消费者稳速处理
@RocketMQMessageListener(topic = "order-topic")
public class OrderConsumer {
    @Override
    public void onMessage(OrderDTO order) {
        orderService.process(order);  // 按自己的节奏处理
    }
}
```

---

**④ 降级 —— 保证核心链路**

| 降级手段 | 做什么 | 例子 |
|---------|--------|------|
| **关非核心功能** | 流量暴涨时关掉推荐、评论、积分等 | 商品详情页只保留商品信息和库存 |
| **返回缓存/默认值** | 不调上游了，用上次的结果 | 用户昵称显示默认"用户***" |
| **读本地缓存** | Redis 挂了就读本地存的一份 | 配置信息本地存一份 |
| **熔断隔离** | 下游扛不住直接断掉，不拖累主流程 | 推荐服务超时就直接返回空列表 |

```java
// 统一降级开关（配置中心控制）
@Value("${degrade.recommend:false}")
private boolean degradeRecommend;

public List<Recommend> getRecommend(String productId) {
    if (degradeRecommend) {
        return Collections.emptyList();   // 降级：直接返回空
    }
    return recommendService.get(productId);
}
```

---

**⑤ 扩容 —— 最后的底牌**

| 方式 | 速度 | 说明 |
|------|------|------|
| **K8s HPA** | 分钟级 | 自动检测 CPU/内存，自动加 Pod |
| **提前预留资源** | 秒级 | 平时低配跑，大促前手动扩 |
| **弹性伸缩** | 分钟级 | 云厂商按量付费，平时不用留 |

---

**⑥ 面试回答框架：分层设防**

```
问：QPS 从 1000 涨到 10000 怎么办？

答：分层设防，从外到内四层：

第一层限流——Sentinel 设定单机 QPS 上限，超出的直接拒绝，
        返回"系统繁忙"，保护核心链路不被冲垮。

第二层缓存——Redis 扛读流量，热点数据预加载 + 逻辑过期，
         99% 流量挡在 DB 前面。Redis 单机 10w QPS 无压力。

第三层削峰——写操作丢 MQ（RocketMQ/Kafka），消费者按自己
         的节奏慢慢处理，峰值被熨平。

第四层降级——非核心功能关掉（推荐、评论），保核心商品和
         下单链路，返回缓存兜底。
```

| 层 | 手段 | 一句话 |
|----|------|--------|
| 1 | **限流** | 超出的直接拒，保系统不崩 |
| 2 | **缓存** | 读流量 Redis 扛，别透到 DB |
| 3 | **削峰** | 写流量进 MQ，排队慢慢处理 |
| 4 | **降级** | 关非核心，保核心链路 |

### 93. 如何设计一个短链接生成系统？

> 详见：[如何设计一个短链接生成系统](../场景题/如何设计一个短链接生成系统.md)

### 94. 如何设计一个分布式定时任务调度系统？

> 详见：[如何设计一个分布式定时任务调度系统](../场景题/如何设计一个分布式定时任务调度系统.md)

### 95. 一个接口依赖多个上游服务，怎么设计才能保证高可用（兜底、降级、熔断）？

> **问题场景**：商品详情接口依赖 5 个上游——商品服务、库存服务、用户服务、推荐服务、评论服务。任意一个挂了，页面就 500？显然不行。

---

**① 三步走：先搞清楚谁是核心、谁可牺牲**

第一步不是写代码，是把依赖分成三类：

| 等级 | 依赖 | 挂了怎么办 | 例子 |
|------|------|-----------|------|
| **P0 强依赖** | 缺了它整个接口没意义 | 必须兜底，否则整体失败 | 商品服务（没商品数据看啥？） |
| **P1 弱依赖** | 缺了影响体验但页面还能看 | 静默降级 | 推荐服务、评论服务 |
| **P2 可异步** | 跟当前请求无关 | 异步处理，失败不影响本次 | 埋点上报、访问记录 |

**依赖梳理表（以商品详情页为例）：**

| 上游服务 | 依赖等级 | 超时时间 | 降级策略 |
|---------|---------|---------|---------|
| 商品服务 | P0 强依赖 | 2s | 读 Redis 缓存 |
| 库存服务 | P0 强依赖 | 1s | 缓存兜底，都不行就显示"已售罄" |
| 用户服务 | P1 弱依赖 | 500ms | 缓存/默认昵称"用户***" |
| 推荐服务 | P1 弱依赖 | 500ms | 返回空列表 |
| 评论服务 | P1 弱依赖 | 500ms | 返回空列表，提示"评论加载中" |

---

**② 三大武器：超时、熔断、降级，缺一不可**

```
请求来了
  → 调商品服务（P0） → 超时 2s → 熔断打开 → 降级：读缓存
  → 调库存服务（P0） → 超时 1s → 熔断打开 → 降级：缓存/默认值
  → 调用户服务（P1） → 超时 500ms → 熔断打开 → 降级：默认昵称
  → 调推荐服务（P1） → 成功返回
  → 调评论服务（P1） → 熔断半开中 → 降级：空列表
  → 组装结果返回（至少商品信息必须有）
```

| 手段 | 做什么 | 解决什么问题 |
|------|------|------------|
| **超时** | 等太久就别等了 | 防止线程池被耗尽的雪崩 |
| **熔断** | 连续失败就断掉，不调了 | 保护自己不被下游拖死 |
| **降级** | 断掉后返回什么 | 保证用户至少能看到东西 |

> **关键**：三个必须一起用。只设超时不熔断 = 每次还是等满超时，资源照样耗光；只熔断不降级 = 断了就报错，用户体验差。

---

**③ 实际代码：Sentinel 实现**

```java
@Service
public class ProductDetailService {

    // 并行调用多个上游，统一超时
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public ProductDetailVO getDetail(String productId) {
        // 并行调上游，设置超时
        CompletableFuture<Product> productFuture = asyncGet(productId, () ->
            productFeignClient.get(productId), 2000);
        CompletableFuture<Stock> stockFuture = asyncGet(productId, () ->
            stockFeignClient.getStock(productId), 1000);
        CompletableFuture<UserInfo> userFuture = asyncGet(productId, () ->
            userFeignClient.getUser(userId), 500);
        CompletableFuture<List<Recommend>> recFuture = asyncGet(productId, () ->
            recommendFeignClient.getRecommend(productId), 500);
        CompletableFuture<List<Comment>> cmtFuture = asyncGet(productId, () ->
            commentFeignClient.getComments(productId), 500);

        // 组装结果：P0 强依赖必须成功，P1 失败用降级值
        try {
            Product product = productFuture.get(2, TimeUnit.SECONDS);
            Stock stock = stockFuture.get(1, TimeUnit.SECONDS);
            return ProductDetailVO.builder()
                .product(product)
                .stock(stock)
                .userInfo(getOrDefault(userFuture, UserInfo.defaultUser()))
                .recommends(getOrDefault(recFuture, Collections.emptyList()))
                .comments(getOrDefault(cmtFuture, Collections.emptyList()))
                .build();
        } catch (Exception e) {
            // P0 依赖兜底也失败了，返回降级页面
            return ProductDetailVO.fallback(productId);
        }
    }

    private <T> T getOrDefault(Future<T> future, T fallback) {
        try { return future.get(500, TimeUnit.MILLISECONDS); }
        catch (Exception e) { return fallback; }
    }
}
```

```java
// Sentinel 熔断 + 降级 —— 注解版（也可用这种方式）
@SentinelResource(
    value = "getProduct",
    fallback = "getProductFallback",    // 降级方法
    fallbackClass = ProductFallback.class
)
public Product getProduct(String productId) {
    return productFeignClient.get(productId);
}

// 降级方法
public Product getProductFallback(String productId, Throwable t) {
    // 1. 先查 Redis 缓存
    Product cached = redisTemplate.opsForValue().get("product:" + productId);
    if (cached != null) return cached;
    
    // 2. 缓存也没有，返回静态兜底
    log.warn("商品服务不可用，返回兜底数据 productId={}", productId);
    return Product.empty();
}
```

```yaml
# Sentinel 熔断规则（控制台/配置文件）
# 1 分钟内调用超过 5 次，且异常率 > 50% → 熔断 10 秒
- resource: getProduct
  grade: EXCEPTION_RATIO    # 按异常比例
  count: 0.5                # 异常率 > 50%
  minRequestAmount: 5       # 最小请求数
  statIntervalMs: 60000     # 统计窗口 1 分钟
  timeWindow: 10            # 熔断 10 秒
```

---

**④ 再加两道保险**

| 保险 | 做什么 | 怎么实现 |
|------|--------|---------|
| **缓存** | 依赖全挂时也能返回上一份数据 | Redis 存最近一次成功的结果，TTL 适中 |
| **限流** | 上游服务慢时，减少对它的调用量 | Sentinel 流控规则，QPS 阈值 |

```
降级策略优先级：正常返回 → Redis 缓存 → 默认兜底 → 空/假数据
```

---

**⑤ 设计 Checklist**

| 步骤 | 做了什么 |
|------|---------|
| ① 依赖分级 | P0（强依赖）/ P1（弱依赖）/ P2（可异步） |
| ② 逐一定超时 | P0 给 1-2s，P1 给 300-500ms，不能无限等 |
| ③ 逐一定降级 | 缓存兜底 / 默认值 / 空列表 / 静默忽略 |
| ④ 配置熔断 | 异常比例/慢调用比例 超阈值自动断开 |
| ⑤ 加缓存 | 每个上游的结果存 Redis，TTL 适度 |
| ⑥ 加限流 | 上游扛不住时限制调用量，不要雪上加霜 |
| ⑦ 监控告警 | 降级比例超过阈值发告警，知道出事了 |

**面试一句话**：先给依赖分级（P0/P1），P0 缓存兜底、P1 静默降级；同时设好超时防止线程耗光、配好熔断防止雪崩，缺一个都不行。

### 96. 如何设计一个支持千万级 DAU 的网站 UV 统计功能？

> **先搞清楚概念：**
>
> | 术语 | 全称 | 含义 | 举例 |
> |------|------|------|------|
> | **DAU** | Daily Active Users | 一天内使用过产品的**独立用户数** | 今天有 1000w 个不同用户打开了 App |
> | **UV** | Unique Visitors | 一段时间内访问网站的**独立访客数** | 今天网站被 800w 个不同用户访问了 |
>
> 两者本质一样——都是**去重计数**。区别是 DAU 叫法偏 App 运营，UV 叫法偏 Web 统计。本题核心问题是：**千万级用户量下，怎么又快又省内存地做去重计数。**

---

**① 直接方案：数据库 COUNT DISTINCT —— 千万级直接跪**

```sql
SELECT COUNT(DISTINCT user_id) FROM page_visit WHERE date = '2026-06-11';
-- 千万行数据，COUNT DISTINCT 要全表扫描或索引扫描，几十秒都跑不完
```

---

**② HyperLogLog —— UV 统计的标准答案**

HyperLogLog 是概率算法，用**极小的固定内存（~12KB）** 估算亿级 UV，误差约 0.81%。

| | 传统 COUNT DISTINCT | Redis HyperLogLog |
|---|---|---|
| **千万 UV 占内存** | 几百 MB ~ GB | **12 KB**（固定） |
| **查询速度** | 秒级~分钟级 | **毫秒级** |
| **精确度** | 100% 精确 | 约 0.81% 误差 |
| **适用场景** | 财务对账等要求精确的场景 | **UV 统计，1% 误差完全可接受** |

```java
// Redis HyperLogLog
// 记录访问：每个用户访问时调用
redisTemplate.opsForHyperLogLog().add("uv:2026-06-11", userId);

// 查询 UV：毫秒级返回
long uv = redisTemplate.opsForHyperLogLog().size("uv:2026-06-11");

// 合并多天：把每天的 HLL 合并
redisTemplate.opsForHyperLogLog()
    .union("uv:week-24", "uv:2026-06-09", "uv:2026-06-10", "uv:2026-06-11");
```

> **为什么 UV 统计允许误差？** 运营看的是趋势——"今天 UV 是 800w 还是 850w"对决策影响不大，但 12KB vs 几百MB 的内存差距是实打实的。极少场景要求 UV 精确到个位数。

---

**③ Redis Bitmap —— 中小规模精确去重**

如果 userId 是连续自增的数字（比如自增主键），可以用 Bitmap 做精确去重。每个用户占 1 bit。

```
千万用户 → 1000w bit ≈ 12.5 MB（固定）
亿级用户 → 1亿 bit ≈ 125 MB（也还好）
```

```java
// Redis Bitmap
// user_id=9527 今天访问了 → 把第 9527 位设为 1
redisTemplate.opsForValue()
    .setBit("uv:2026-06-11", userId, true);

// 查询 UV → BITCOUNT
Long uv = redisTemplate.execute((RedisCallback<Long>) conn ->
    conn.bitCount("uv:2026-06-11".getBytes()));
```

| 场景 | 推荐方案 |
|------|---------|
| userId 是连续自增数字，量不大 | **Bitmap**（精确，12.5MB 撑千万） |
| userId 是 UUID/雪花ID/手机号，或量很大 | **HyperLogLog**（省内存，12KB 固定） |
| 需要按地域/设备多维度分析 | HLL（每个维度一个 key） |

> **注意：** Bitmap 的 offset 对应 userId 值。如果 userId 离散很大（比如用雪花 ID，最大值几十亿），Bitmap 会产生大量空洞，内存直接爆炸——这种场景必须用 HLL。

---

**④ 系统架构：数据怎么从客户端流入存储**

千万级 DAU 一天产生的 PV 是几十亿级别，不可能每次都写 Redis。需要管道化处理：

```
客户端 SDK（埋点上报）
    ↓ （批量，每 5s 一次）
Nginx / API Gateway
    ↓
Kafka（削峰，扛住突发流量）
    ↓
Flink / Spark Streaming（实时计算）
    ↓ — 写入 — → Redis HLL（热数据，当天实时查询）
    ↓ — 写入 — → ClickHouse / HBase（冷数据，历史趋势分析）
```

```java
// Flink 中写入 Redis HLL
stream
    .keyBy(VisitEvent::getDate)   // 按天分组
    .process(new KeyedProcessFunction<>() {
        private transient Jedis jedis;
        
        @Override
        public void processElement(VisitEvent event, Context ctx, Collector<Void> out) {
            String key = "uv:" + event.getDate();
            jedis.pfadd(key, event.getUserId());  // HLL 自动去重
        }
    });
```

---

**⑤ 面试答法（一句话总结）**

> **千万级 DAU 的 UV 统计 = HyperLogLog（核心算法）+ Kafka（削峰）+ Flink（实时计算）+ Redis（热数据存储）。** HLL 用 12KB 固定内存估算亿级 UV，误差不到 1%，对运营分析完全够用；要求精确就用 Bitmap（限连续 userId），千万用户才 12.5MB。

### 97. 定时任务扫表处理过期订单，数据量太大扫不过来怎么办？

> 详见：[定时任务扫表处理过期订单，数据量太大扫不过来怎么办](../场景题/定时任务扫表处理过期订单，数据量太大扫不过来怎么办.md)

## 十四、项目实战深挖（3 题）

### 98. 介绍一个你觉得最有挑战的项目。中间遇到了什么问题？怎么解决的？用到了哪些 Spring Boot 技术？
### 99. 你的项目中如果突然流量翻了 10 倍，哪些地方会最先出问题？你会怎么改造？
### 100. 你平时怎么学习 Spring Boot 的？看过哪些源码？有什么学习习惯？

---

## 题目分类速览

| 分类 | 题号 | 题数 |
|------|------|------|
| Spring Boot 基础与核心原理 | 1-13 | 13 |
| 自动配置与 Starter 机制 | 14-21 | 8 |
| IoC 容器与 Bean 生命周期 | 22-29 | 8 |
| AOP 面向切面编程 | 30-34 | 5 |
| 事务管理 | 35-41 | 7 |
| Spring MVC 核心技术 | 42-47 | 6 |
| 数据库与持久层 | 48-54 | 7 |
| 缓存实战 | 55-63 | 9 |
| 消息队列实战 | 64-70 | 7 |
| 分布式与微服务 | 71-80 | 10 |
| 安全与鉴权 | 81-84 | 4 |
| 性能优化与监控 | 85-90 | 6 |
| 场景设计题 | 91-97 | 7 |
| 项目实战深挖 | 98-100 | 3 |

> **共计：100 题**

---

> **使用建议：**
> 1. 先按分类逐个吃透，基础类 → 原理类 → 实战类 → 场景设计类
> 2. 每道题自己先答一遍，再到牛客网上搜对应面经对比
> 3. 场景题没有标准答案，重点练"分析问题 → 提出方案 → 对比优缺点"的思路
> 4. 结合你实际做的项目，每道题想想"我在项目中遇到过类似的问题吗"
