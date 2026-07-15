# Java 锁机制详解

> **两条技术路线**:Java 的锁分两条路线实现——**synchronized**(JVM 内置,关键字)和 **J.U.C 显式锁**(基于 AQS,如 ReentrantLock)。理解锁从四问切入:**有哪些锁 → 锁有什么特性 → 底层怎么实现 → 什么场景用什么锁**。
>
> **核心认知**:"公平/可重入/共享"等词**不是具体某把锁**,而是描述锁的**维度**;同一把锁往往同时具备多个维度的特性。

---

## 一、Java 有哪些锁

JDK 提供了多种锁,各有不同的特性组合(各特性详解见第二节):

### 1.1 JDK 里的具体锁

| 锁 | 特性组合 | 来源 | 典型用途 |
|------|----------|------|----------|
| **synchronized** | 独占、可重入、不可中断、非公平 | JVM 关键字 | 简单同步,首选 |
| **ReentrantLock** | 独占、可重入、可中断、可公平、可超时 | J.U.C(AQS) | 需要灵活控制时 |
| **ReentrantReadWriteLock** | 读共享 + 写独占、可重入 | J.U.C(AQS) | 读多写少(缓存) |
| **StampedLock** | 乐观读 + 悲观读 + 写 | J.U.C | 读多写少且要求高性能 |
| **Semaphore** | 共享(许可数) | J.U.C(AQS) | 限流、资源池 |
| **CountDownLatch** | 共享(倒计数) | J.U.C(AQS) | 等一批任务完成 |
| **CyclicBarrier** | 基于 ReentrantLock+Condition | J.U.C | 多线程到齐后一起跑 |

> **判断标准**:看到 `Reentrant/Lock/Latch/Semaphore/Barrier`,底层基本都是 **AQS**;`synchronized` 是例外,走 JVM 内置 monitor。

---

## 二、锁的核心特性

逐个特性讲透:是什么 + 为什么 + 怎么体现。

### 2.1 可重入(Reentrant)

- **是什么**:同一线程获取已持有的锁时,直接放行,`state` 累加;释放时递减到 0 才真正解锁。
- **为什么必须**:方法 A 持锁调用方法 B,若 B 也要同一把锁,不可重入会**自死锁**。
- **体现**:`synchronized` 和 `ReentrantLock` 都可重入。

```java
synchronized void methodA() { methodB(); }   // 不会死锁,B 能再次获取同一把锁
synchronized void methodB() { /* ... */ }
```

### 2.2 可中断(Interruptible)

- **是什么**:等待锁时能响应 `thread.interrupt()`,抛 `InterruptedException`,**避免无限等待**。
- **体现**:`synchronized` **不可中断**;`ReentrantLock.lockInterruptibly()` 可中断。

```java
try {
    lock.lockInterruptibly();   // 等锁期间被中断会抛异常,跳出死等
} catch (InterruptedException e) {
    // 被中断,放弃抢锁
}
```

### 2.3 公平 / 非公平(Fair)

- **公平锁**:严格按 CLH 队列 FIFO,先到先得。优点是不饿死,缺点是吞吐低(频繁线程切换)。
- **非公平锁**(默认):新线程先尝试插队抢锁,抢不到才排队。吞吐高,但队尾线程可能长期拿不到锁。
- **体现**:`synchronized` 只能非公平;`ReentrantLock` 可选(`new ReentrantLock(true)` 为公平)。

### 2.4 共享 / 独占(Shared / Exclusive)

- **独占**:读读/读写/写写全互斥,如 `ReentrantLock`。
- **共享**:允许多线程同时读,但写独占。读写锁的**读锁是共享锁**——这是"读多写少"场景提性能的关键。

### 2.5 乐观 / 悲观

| | 悲观锁 | 乐观锁 |
|---|---|---|
| 策略 | 先锁再操作 | 不锁,提交时 CAS 验证 |
| 适用 | 写多、冲突频繁 | 读多、冲突少 |
| 代价 | 锁开销、可能阻塞 | 无锁,但竞争激烈时 CAS 自旋开销大 |
| 例子 | synchronized、ReentrantLock | AtomicXXX、数据库 version 字段 |

### 2.6 自旋(Spin)

- **是什么**:抢不到锁不挂起,而是**循环重试**(自旋),赌锁很快释放,省掉线程切换开销。
- **适用**:锁持有时间**极短**,否则白白消耗 CPU。
- **体现**:`synchronized` 轻量级锁、`LongAdder`/JVM **自适应自旋**(根据历史成功率动态调整自旋次数)。

### 2.7 锁升级(synchronized 专属)

见第三节,`synchronized` 独有:无锁 → 偏向锁 → 轻量级锁 → 重量级锁,只能升级不能降级。

---

## 三、底层实现

### 3.1 synchronized 底层:Monitor + 对象头 + 锁升级

**字节码层面**:代码块用 `monitorenter`/`monitorexit`,方法级用 `ACC_SYNCHRONIZED` 标志位。每个对象都关联一个 **Monitor**(ObjectMonitor,C++ 实现)。

**锁状态记录在对象头的 Mark Word 里**,根据竞争激烈程度**自动升级**:

| 锁状态 | 触发条件 | 实现方式 | 性能 |
|--------|----------|----------|------|
| **偏向锁** | 首个线程获取,无竞争 | Mark Word 记录线程 ID,下次进入直接比对,**无 CAS** | 最快 |
| **轻量级锁** | 出现轻度竞争 | **CAS + 自旋**竞争锁 | 较快 |
| **重量级锁** | 自旋失败/竞争激烈 | OS **互斥量(Mutex)**,抢不到的线程**挂起** | 最慢 |

```
无锁 --首个线程进入--> 偏向锁 --出现竞争--> 轻量级锁(CAS自旋) --自旋失败--> 重量级锁(OS互斥量)
                                                                    (只能升级,不可降级)
```

> **JDK 15** 起**废弃偏向锁**(JEP 374),现代多核服务器上偏向锁收益已不明显。

### 3.2 CAS(Compare And Swap)—— 乐观锁的基石

**是什么**:无锁编程的原子操作。三个操作数:**内存值 V、期望值 A、新值 B**。当 `V == A` 时把 V 更新为 B,否则什么都不做。整个比较替换过程由 CPU 指令保证原子。

```java
// AtomicInt.incrementAndGet() 本质就是 CAS 循环
public final int incrementAndGet() {
    int oldValue, newValue;
    do {
        oldValue = get();          // 读取当前值
        newValue = oldValue + 1;
    } while (!compareAndSet(oldValue, newValue));  // CAS 失败就重试
    return newValue;
}
```

**底层**:Java 通过 `Unsafe.compareAndSwapInt/Long/Object`,对应 CPU 的 `LOCK CMPXCHG` 指令。`AtomicXXX` 全家桶、`ReentrantLock` 抢锁、`ConcurrentHashMap` 节点操作都基于 CAS。

**CAS 三大问题与解决**:

| 问题 | 说明 | 解决 |
|------|------|------|
| **ABA 问题** | 值从 A→B→A,CAS 认为没变过 | `AtomicStampedReference`(加版本号) |
| **自旋开销大** | 竞争激烈时大量空转耗 CPU | 限制自旋次数;高并发计数用 `LongAdder`(分段累加) |
| **只能保证单变量原子** | 无法同时原子更新多个字段 | `AtomicReference` 封装整个对象 |

### 3.3 AQS(AbstractQueuedSynchronizer)—— J.U.C 锁的基石

**是什么**:J.U.C 的核心同步框架,用 **`volatile int state` + CLH 双向等待队列**实现同步语义,采用**模板方法模式**(排队/阻塞/唤醒由 AQS 负责,获取/释放逻辑由子类重写)。

- **state** 的含义随同步器变化:

| 同步器 | state 含义 |
|--------|------------|
| ReentrantLock | 锁重入次数 |
| Semaphore | 剩余许可数 |
| CountDownLatch | 剩余计数 |
| ReentrantReadWriteLock | 高 16 位=读锁数,低 16 位=写锁重入次数 |

- **CLH 队列**:抢锁失败的线程封装成 `Node` 入队尾,按 FIFO 排队;通过 `LockSupport.park()` 挂起,被前驱 `unpark()` 唤醒。
- **两种模式**:**独占**(ReentrantLock)和**共享**(Semaphore、CountDownLatch、读写锁的读锁)。

**基于 AQS 实现的组件**:ReentrantLock、ReentrantReadWriteLock、Semaphore、CountDownLatch、CyclicBarrier、ThreadPoolExecutor 的 Worker、FutureTask、Condition。

### 3.4 ReentrantLock 如何基于 AQS

- 内部类 `Sync extends AbstractQueuedSynchronizer`,再分 `FairSync`(公平)和 `NonfairSync`(非公平)。
- **加锁**:`CAS` 把 state 从 0 改 1,成功则设当前线程为持有者;已被自己持有则 state+1(重入);否则入 CLH 队列,`park` 挂起。
- **解锁**:state-1,减到 0 时唤醒后继节点。
- **公平 vs 非公平的区别**:公平锁抢锁前先判断队列是否有前驱(`hasQueuedPredecessors()`);非公平锁直接 CAS 抢。

---

## 四、场景选型(实战)

### 4.1 各场景该用什么

| 场景 | 选择 | 理由 |
|------|------|------|
| 简单同步,不需要灵活控制 | **synchronized** | 简单、自动释放、JVM 优化后性能足够 |
| 需要公平锁/可中断/超时/多条件等待 | **ReentrantLock** | API 灵活,提供高级特性 |
| 读多写少(缓存、配置) | **ReentrantReadWriteLock** | 读读并发,大幅提升读吞吐 |
| 读多写少且追求极致性能 | **StampedLock** | 乐观读无锁,性能更高 |
| 限流/控制并发数 | **Semaphore** | 共享许可 |
| 高并发计数/累加 | **AtomicXXX / LongAdder** | CAS 无锁,超高并发用 LongAdder |
| 等一批任务全部完成再继续 | **CountDownLatch** | 不可复用,一次性 |
| 多线程相互等待、到齐后一起执行 | **CyclicBarrier** | 可复用 |

### 4.2 synchronized vs ReentrantLock 选型

| 维度 | synchronized | ReentrantLock |
|------|--------------|---------------|
| 实现 | JVM 内置(monitor) | J.U.C,基于 AQS |
| 灵活性 | 低 | 高(可中断/超时/公平/多 Condition) |
| 释放锁 | 自动(出代码块或异常) | **必须手动** `finally { lock.unlock(); }` |
| 公平性 | 只能非公平 | 可选 |
| 可中断 | 否 | 是(`lockInterruptibly`) |
| 性能 | JDK 6+ 优化后与 ReentrantLock 接近 | 接近 |
| **选型建议** | **简单优先**,99% 场景够用 | 需要"可中断/超时/公平/多条件"时才用 |

> **选型动作**:默认用 `synchronized`;只有明确需要**可中断、超时获取、公平排队、多个条件队列**时,才换 `ReentrantLock`。**别为了显得高级而滥用 ReentrantLock**——它忘了 `unlock` 就是生产事故。

---

## 五、面试速答

> Java 锁分两条路线:`synchronized`(JVM 内置)和基于 AQS 的显式锁(`ReentrantLock` 等)。
>
> **特性维度**:可重入、可中断、公平/非公平、共享/独占、乐观/悲观、自旋——这些是描述锁的维度,不是单独的锁。`synchronized` 是独占+可重入+不可中断+非公平;`ReentrantLock` 全特性可选。
>
> **底层**:`synchronized` 靠对象头 Mark Word + Monitor,有**锁升级**(无锁→偏向→轻量级 CAS 自旋→重量级 OS 互斥量);`ReentrantLock` 靠 **AQS**(state + CLH 队列);乐观锁靠 **CAS**(`LOCK CMPXCHG`)。
>
> **场景**:简单同步用 `synchronized`;要可中断/超时/公平用 `ReentrantLock`;读多写少用读写锁或 `StampedLock`;限流用 `Semaphore`;高并发计数用 `AtomicXXX`/`LongAdder`。
