# Java 语法糖速查

> **语法糖(Syntactic Sugar)**:让代码写起来更简洁的语法,编译期会被还原成等价的基础语法。本质是"你写得少,编译器帮你补全"。
>
> 两个核心方向:**Lambda / `::` 简化匿名类**、**`var` / 钻石符 / `record` 省类型声明**。

## 速查总表

| 简写 | 完整形式 | 作用 | 版本 |
|------|---------|------|------|
| `int[] a = {1,2,3}` | `new int[]{1,2,3}` | 数组初始化 | — |
| `List.of(1,2,3)` | new + add | 一行建不可变集合 | JDK9 |
| `() -> ...` | 匿名内部类 | Lambda | JDK8 |
| `Class::method` | `x -> x.method()` | 方法引用 | JDK8 |
| `new ArrayList<>()` | `new ArrayList<String>()` | 钻石符(省泛型) | JDK7 |
| `var list = ...` | 写全左边类型 | 局部变量类型推断 | JDK10 |
| `Integer i = 5` | `Integer.valueOf(5)` | 自动装箱 | JDK5 |
| `for (X x : list)` | Iterator 遍历 | 增强 for | JDK5 |
| `try (R r = ...)` | finally 手动 close | 自动关资源 | JDK7 |
| `instanceof X x` | instanceof + 强转 | 模式匹配 | JDK16 |
| `record P(int x)` | 手写构造/getter/equals | 记录类 | JDK16 |
| `switch(箭头)` | switch + break + 赋值 | switch 表达式 | JDK14 |
| `"""..."""` | 字符串拼接转义 | 文本块 | JDK15 |

---

## 一、数组与集合简写

### 1. 数组初始化

```java
int[] a = {1, 2, 3};              // ✅ 声明时简写,省略 new int[]
int[] b = new int[]{1, 2, 3};     // 完整写法(等价)

// ⚠️ 传给方法时不能省 new 类型[]{}
print(new int[]{1, 2, 3});         // ✅
// print({1, 2, 3});              // ❌ 编译器推断不出类型
```

> **踩坑**:数组简写 `{}` 只能在**声明语句**里用,作方法实参必须写全 `new 类型[]{}`。

### 2. 集合工厂方法(JDK9+)

```java
List<Integer> list = List.of(1, 2, 3);     // ✅ 一行搞定
Set<String>  set  = Set.of("a", "b");
Map<String,Integer> map = Map.of("k", 1);   // ≤10 对用 of(k,v,k,v...)
```

> **注意**:`of()` 返回的是**不可变集合**,调 `add/remove` 会抛 `UnsupportedOperationException`。要可变就包一层:`new ArrayList<>(List.of(1,2,3))`。

---

## 二、Lambda 与方法引用

### 1. Lambda 简化匿名内部类

```java
// 完整:匿名内部类
Runnable r1 = new Runnable() {
    public void run() { System.out.println("hi"); }
};

// 简写:Lambda
Runnable r2 = () -> System.out.println("hi");
```

**Lambda 的几层省略规则:**

```java
// 参数类型可省
(s) -> s.length()              // → s -> s.length()
// 单参数可省括号
s -> s.length()
// 单语句可省 return 和 {}
(a, b) -> a + b               // 等价 (a,b) -> { return a+b; }
```

> **本质**:Lambda 只能替代**函数式接口**(只有一个抽象方法的接口),如 `Runnable`、`Comparator`、`Consumer`。

### 2. 方法引用 `::`(Lambda 的极致简写)

当 Lambda 体**只是把参数透传给一个现成方法**时,用 `::`:

```java
list.forEach(s -> System.out.println(s));   // Lambda
list.forEach(System.out::println);          // 方法引用
```

**四种形式:**

| 形式 | 示例 | 等价 Lambda |
|------|------|------------|
| 静态方法 | `Integer::parseInt` | `s -> Integer.parseInt(s)` |
| 对象的实例方法 | `System.out::println` | `s -> System.out.println(s)` |
| 类的实例方法 | `String::length` | `s -> s.length()` |
| 构造方法 | `ArrayList::new` | `() -> new ArrayList()` |

> **判断标准**:Lambda 体只有一行且是 `对象.方法(参数)` 或 `类.静态方法(参数)` → 用 `::`。

---

## 三、类型声明简写

### 1. 钻石符 `<>`(JDK7)

```java
List<String> list = new ArrayList<>();      // ✅ 右边省泛型
List<String> list = new ArrayList<String>();// 完整
```

### 2. `var` 局部变量类型推断(JDK10)

```java
var list = new ArrayList<String>();   // 编译器推断为 ArrayList<String>
var map  = new HashMap<String, User>();
var stream = list.stream().filter(x -> x > 0);
```

> **能用 / 不能用:**
>
> | 场景 | 能否用 var |
> |------|-----------|
> | 方法内局部变量 | ✅ |
> | try-with-resources、Lambda 参数 | ✅ |
> | **类的字段** | ❌ |
> | **方法参数 / 返回类型** | ❌ |
> | `var x = null;` | ❌(推断不出类型) |

### 3. 自动装箱 / 拆箱(JDK5)

```java
Integer i = 5;        // 装箱:Integer.valueOf(5)
int n = i;            // 拆箱:i.intValue()
```

> **两个坑**:
> 1. **Integer 缓存** `-128 ~ 127`,`Integer a=127, b=127; a==b` 为 `true`;超出范围 `a==b` 为 `false` → 包装类比较**永远用 `.equals()`**。
> 2. **NPE 风险**:`Integer` 为 null 时拆箱抛 `NullPointerException`。

### 4. 静态导入 `import static`

```java
import static java.lang.Math.*;
double r = sqrt(4);        // 不用写 Math.sqrt(4)
```

### 5. 可变参数 `...`

```java
void f(int... nums) {}    // 实参 f(1, 2, 3) 内部就是 int[]
```

> **本质**:可变参数就是数组,编译器帮你把 `f(1,2,3)` 包装成 `new int[]{1,2,3}`。

---

## 四、流程控制简写

### 1. 增强 for(JDK5)

```java
for (String s : list) { }    // 实现了 Iterable 就能用,底层是 Iterator
```

### 2. try-with-resources(JDK7)

```java
// ✅ 自动关闭(要求资源实现 AutoCloseable)
try (var br = new BufferedReader(new FileReader("f.txt"))) {
    br.readLine();
}
// 完整:手动 finally close,还可能漏关、嵌套难看
```

> **实战**:凡是 `InputStream` / `OutputStream` / `Connection` / `Reader` 等流和连接,**一律用 try-with-resources**,杜绝资源泄漏。

### 3. 三元运算符

```java
int max = a > b ? a : b;     // if-else 的单行版
```

### 4. instanceof 模式匹配(JDK16)

```java
// ✅ 简写:判断 + 绑定变量,省去强转
if (obj instanceof String s) {
    System.out.println(s.length());
}
// 完整:先判断再强转
if (obj instanceof String) {
    String s = (String) obj;
    System.out.println(s.length());
}
```

---

## 五、新版语法糖(JDK9+,JDK17 全可用)

### 1. record 记录类(JDK16)

```java
public record Point(int x, int y) {}
```

**一行等价于手写**:全参构造 + `x()` / `y()` getter + `equals` + `hashCode` + `toString`,且字段 `final`(不可变)。

> **使用场景**:纯数据载体(DTO、值对象)。getter 是 `x()` **不是** `getX()`。

### 2. switch 表达式(JDK14)

```java
// 箭头形式:无 fall-through,不用 break,可直接赋值
String type = switch (day) {
    case MON, TUE, WED, THU, FRI -> "工作日";
    case SAT, SUN -> "周末";
};
```

> 老的冒号形式要返回值用 `yield`:`case X -> ` 改成 `case X: ... yield 值;`。

### 3. 文本块(JDK15)

```java
String json = """
        {
          "name": "tom",
          "age": 18
        }
        """;                  // 不用再拼接 + 转义引号
```

---

## 六、编译器自动优化(你没写,但实际发生了)

| 你写的 | 编译器变成 | 注意 |
|--------|-----------|------|
| `"a" + b + "c"` | `new StringBuilder().append(...).toString()` | **循环内拼接仍要手动用 StringBuilder** |
| `Integer i = 127` | `Integer.valueOf(127)` | 命中 `-128~127` 缓存 |
| 增强 for 遍历 ArrayList | 用 Iterator | ArrayList 实现了 RandomAccess,for 索引遍历反而更快 |

---

> **记忆口诀**:Java 简写两类——**省对象用 `->` 和 `::`,省类型用 `var`、`<>`、`record`**。看到匿名内部类想 `->`,看到类型重复想 `var`,看到纯数据类想 `record`。
