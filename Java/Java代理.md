# Java 代理

> Java 运行时动态代理主要有两种实现：
> - **JDK 动态代理**：基于**接口**，目标类必须实现接口
> - **CGLIB 动态代理**：基于**继承**，生成目标类的子类，不需要接口

---

## 一、JDK 动态代理

> **原理：** `Proxy.newProxyInstance()` 在运行时生成一个**实现了目标接口**的代理类，所有方法调用转发给 `InvocationHandler.invoke()`，在里面做增强 + 反射调用原方法。

```java
package com.cxsk;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class TestProxy {

    interface A {
        void a_1();
    }

    public static class A1 implements A {
        @Override
        public void a_1() {
            System.out.println("a_1");
        }
    }

    public static class BeforeHandler implements InvocationHandler {

        private final Object target;

        public BeforeHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            System.out.println("before");
            return method.invoke(target, args);
        }
    }

    public static class AfterHandler implements InvocationHandler {

        private final Object target;

        public AfterHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = method.invoke(target, args);
            System.out.println("after");
            return result;
        }
    }


    public static void main(String[] args) {
        A proxy1 = (A) Proxy.newProxyInstance(
                A.class.getClassLoader(),
                new Class[]{A.class},
                new BeforeHandler(new A1())
        );
        A proxy2 = (A) Proxy.newProxyInstance(
                A.class.getClassLoader(),
                new Class[]{A.class},
                new AfterHandler(proxy1)
        );
        proxy2.a_1();
    }


}
```

**三个要点：**

1. 目标类**必须实现接口**（`A1 implements A`）
2. 代理对象只能强转为**接口类型**（`A`），不能是具体类
3. `method.invoke(target, args)` 反射调用原始方法

---

## 二、CGLIB 动态代理

> **原理：** 通过 ASM 在运行时生成目标类的**子类**，重写非 final 方法，在子类里插入增强逻辑。`MethodInterceptor.intercept()` 相当于 JDK 的 `InvocationHandler`。

```java
package com.cxsk;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import java.lang.reflect.Method;

public class CglibTest {

    // 注意：A1 是普通类，没有实现任何接口
    public static class A1 {
        public void a_1() {
            System.out.println("a_1");
        }
    }

    public static class MyInterceptor implements MethodInterceptor {
        @Override
        public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
            System.out.println("before");
            Object result = proxy.invokeSuper(obj, args);  // 调用父类（原始）方法
            System.out.println("after");
            return result;
        }
    }

    public static void main(String[] args) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(A1.class);          // 设置父类（目标类）
        enhancer.setCallback(new MyInterceptor());
        A1 proxy = (A1) enhancer.create();         // 生成子类代理对象
        proxy.a_1();
    }
}
```

**三个要点：**

1. 目标类**不需要接口**（`A1` 是普通类）
2. 代理对象是目标类的**子类**，能强转为具体类 `A1`
3. `proxy.invokeSuper(obj, args)` 调用父类的原始方法

---

## 三、两者的区别

| 维度 | JDK 动态代理 | CGLIB 动态代理 |
|---|---|---|
| **底层原理** | 实现接口（`Proxy` 生成接口的实现类） | 继承目标类（ASM 生成子类） |
| **是否需要接口** | ✅ 必须有接口 | ❌ 不需要，普通类即可 |
| **依赖** | JDK 自带（`java.lang.reflect.Proxy`） | 需引入 `cglib` jar（Spring 已内置） |
| **性能** | 创建快，调用稍慢（反射） | 创建慢，调用快（直接调子类方法） |
| **final 限制** | 不受影响（基于接口） | final 类不能代理，final 方法不能增强 |
| **Spring 默认策略** | 有接口时用 JDK | 没接口时用 CGLIB |

> **Spring Boot 2.x 之后的变化：** 默认 `spring.aop.proxy-target-class=true`，**强制全部用 CGLIB**（即使目标类有接口）。所以现在 Spring Boot 项目里的 `@Transactional`、`@Async` 等基本都是 CGLIB 代理。

---

## 四、代理失效的场景

### 1. 最经典的坑：同类内部自调用（AOP 增强失效）

```java
@Service
public class UserService {

    @Transactional
    public void methodA() {        // 外部调用 → 经过代理 → 事务生效
        this.methodB();            // ❌ this 调用 → 绕过代理 → methodB 的事务失效！
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void methodB() {        // 期望开新事务，实际没开
        // ...
    }
}
```

> **原因：** `this.methodB()` 是**目标对象自己**在调，不是代理对象在调，所以 methodB 上的所有增强（事务、日志、权限）全部不生效。
>
> **解决：** 拿到代理对象再调，如 `((UserService) AopContext.currentProxy()).methodB()`，或把 methodB 拆到另一个 Bean。

### 2. 其他失效场景

| 场景 | 谁失效 | 原因 |
|---|---|---|
| **目标类没实现接口** | JDK 代理失效 | JDK 必须基于接口（会自动退回 CGLIB） |
| **`final` 类** | CGLIB 失效 | 无法被继承 |
| **`final` 方法** | 该方法 CGLIB 失效 | 子类无法重写 |
| **`private` 方法** | 增强失效 | 子类不可见，无法被重写/拦截 |
| **`static` 方法** | 增强失效 | 属于类，不属于实例方法 |
| **构造方法里调用增强方法** | 失效 | 对象还没完全初始化，代理未就绪 |
| **自己 `new` 出来的对象** | 失效 | 不在 Spring 容器里，没被代理（必须是容器管理的 Bean） |

> **一句话总结：** 代理要生效，必须保证——**调用经过代理对象**（别 `this` 自调用）、**目标是容器 Bean**（别自己 new）、**方法可被重写**（别 final/private/static）。
