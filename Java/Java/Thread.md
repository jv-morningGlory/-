# Java 线程状态（Thread.State）

---

## 一、6 种状态总览

| 状态 | 含义 | 触发方式 |
|------|------|----------|
| **NEW** | 已创建未启动 | `new Thread()` 后，未调 `start()` |
| **RUNNABLE** | 可运行（含就绪 + 运行中） | 调 `start()` 后进入 |
| **BLOCKED** | 阻塞，等 monitor 锁 | 等 `synchronized` 锁，进 ObjectMonitor 的 _EntryList |
| **WAITING** | 无限期等待 | `wait()` / `join()` / `LockSupport.park()` |
| **TIMED_WAITING** | 限期等待 | `sleep(ms)` / `wait(ms)` / `join(ms)` |
| **TERMINATED** | 终止 | `run()` 执行完或异常退出 |

---

## 二、WAITING vs BLOCKED

> **核心区别**：BLOCKED 是"抢锁抢不到，被动等"；WAITING 是"主动挂起，等条件"。一个卡在锁门口，一个不一定跟锁有关。

### 1. 本质对比

| 维度 | BLOCKED | WAITING |
|---|---|---|
| 含义 | 等 synchronized 的 monitor 锁 | 主动挂起，等被唤醒 |
| 触发原因 | 抢锁失败（锁被别人占着） | 调 `wait()` / `join()` / `park()` 等 |
| 是否持锁 | 否（还没抢到） | `wait()` 释放锁；`join/park` 不涉及锁 |
| 唤醒方式 | 持锁者释放锁时自动唤醒 | `notify` / `interrupt` / `unpark` |
| 发生位置 | monitorenter 入口（_EntryList） | 同步块内 `wait()`，或与锁无关 |

### 2. synchronized 场景下的联动（最容易混）

同一把锁 `obj` 上：

```
线程A: 进同步块持锁，调 obj.wait()
       → 释放锁 + 进 _WaitSet                    → 状态 WAITING

线程B: 来抢 obj 锁，抢不到 → 进 _EntryList        → 状态 BLOCKED

A 被 notify 唤醒:
       移出 _WaitSet → 重新抢锁                   → 变 BLOCKED
       抢到锁 → 继续执行                          → 变 RUNNABLE
```

> **关键**：`wait()` 被唤醒后不会直接 RUNNABLE，而是先变 **BLOCKED** 去重新抢锁。一个 wait 过的线程可能经历：`RUNNABLE → WAITING → BLOCKED → RUNNABLE`。

### 3. 一句话记忆

- **BLOCKED** = 进不去锁，被动（门外排队）
- **WAITING** = 主动 `wait()` 等人叫（门内候客），或 `join/park` 等条件

> 区分依据是**等待的原因**，不是等的时间长短。抢不到锁永远是 BLOCKED；主动挂起等条件才是 WAITING（带超时的那部分叫 TIMED_WAITING）。

---

## 三、守护线程 vs 非守护线程

> **为什么叫"守护"**：守护线程是在后台**为用户线程提供服务**的线程，名字来自希腊神话的 daimon（伴随精灵），Linux 后台服务进程也叫 daemon（httpd、mysqld 末尾的 d）。典型代表 **GC**——默默在后台清理，**服务对象是用户线程**；用户线程都结束了，它就失去存在意义，JVM 直接退。所以"守护"不是说它被保护，恰恰相反——**地位低、是服务者**。

| 维度 | 非守护（用户线程） | 守护线程（Daemon） |
|---|---|---|
| 对 JVM 退出 | **会等它结束** | 不等，直接终止 |
| 代表 | main、业务线程 | GC、编译线程 |
| 设置 | 默认 | `setDaemon(true)`，**须在 start() 前** |

---

## 四、常用方法速查（状态扭转 + 锁）

| 方法 | 作用 | 状态变化 | 释放锁？ |
|---|---|---|---|
| `start()` | 启动线程 | NEW → RUNNABLE | — |
| `run()` | 线程体（直接调不启线程） | 无变化 | — |
| `yield()` | 让出 CPU 时间片 | RUNNABLE → RUNNABLE | ❌ |
| `sleep(ms)` | 睡眠指定时间 | → TIMED_WAITING → RUNNABLE | ❌ |
| `join()` | 等目标线程结束 | 调用者 → WAITING | ❌（不涉及业务锁） |
| `wait()` | 等待（须在 synchronized 内） | → WAITING | ✅ |
| `wait(ms)` | 超时等待 | → TIMED_WAITING | ✅ |
| `notify()/notifyAll()` | 唤醒等待线程 | 被唤醒者 → BLOCKED | ❌（不立即释放） |
| `interrupt()` | 设中断标志 | 不直接改 | — |
| `LockSupport.park()` | 挂起当前线程 | → WAITING | ❌ |

要点：

1. **`start()` 只能调一次**；直接调 `run()` 只是普通方法调用，不会启动新线程。
2. **`yield()` 是"暗示"**，调度器可以无视；状态仍是 RUNNABLE，不释放锁。
3. **`wait()/notify()` 必须在 `synchronized(obj)` 内**、且操作同一个 obj，否则抛 `IllegalMonitorStateException`。
4. **`notify()` 不立即释放锁**——要等当前同步块执行完，唤醒的线程才能抢到锁（所以被唤醒者先变 BLOCKED）。
5. **`interrupt()` 是"请求"不是"强制停止"**；只有线程正卡在 `sleep/wait/join` 时才会抛 `InterruptedException`，正常运行的线程只是拿到一个标志位，要自己判断处理。

---

## 五、JUC

### 1. ReentrantLock

`synchronized` 的进阶替代，**可重入独占锁**。关键差异：

- 比 synchronized 多：**可中断**（`lockInterruptibly`）、**可超时**（`tryLock`）、**可公平**（`new ReentrantLock(true)`）、**多 Condition**
- 释放**必须手动**：`finally { unlock() }`（synchronized 自动）
- 等锁线程是 **WAITING**（synchronized 是 BLOCKED），底层 AQS + `LockSupport.park()`

```java
lock.lock();
try { /* 临界区 */ } finally { lock.unlock(); }
```

### 2. Condition：await / signalAll

`ReentrantLock` 的条件变量，对应 Object 的 wait/notify：

| synchronized | Condition |
|---|---|
| `synchronized(obj)` | `lock.lock()` |
| `obj.wait()` | `condition.await()` |
| `obj.notify()` | `condition.signal()` |
| `obj.notifyAll()` | `condition.signalAll()` |

- 都须**先持锁**才能调，否则抛 `IllegalMonitorStateException`
- `await()` 释放锁进 WAITING，被唤醒后重新抢锁 → BLOCKED → RUNNABLE（状态扭转同 wait）
- 判断条件用 `while`，防虚假唤醒

> 优势：一把锁能 `newCondition()` 出**多个独立等待队列**（如生产者一个、消费者一个），`signal` 精准唤醒目标队列，不像 `notifyAll` 把所有人都叫醒。 


### 3. Semaphore（信号量）

控制**同时访问某资源的线程数量**——本质是一组"许可"（permits）。`ReentrantLock` 是独占（许可=1），Semaphore 是共享（许可=N），可理解为**共享锁的计数器**。

| 方法 | 作用 |
|---|---|
| `acquire()` | 拿 1 个许可，没有就阻塞（可中断） |
| `tryAcquire(timeout)` | 尝试拿，超时返回 false |
| `release()` | 释放 1 个许可 |
| `availablePermits()` | 当前剩余许可数 |

```java
// 3 个许可 = 同时最多 3 个线程进入
Semaphore semaphore = new Semaphore(3);

public void access() throws InterruptedException {
    semaphore.acquire();          // 拿许可，拿不到就等
    try {
        // 临界区（最多 3 个线程同时在里面）
    } finally {
        semaphore.release();      // 必须 finally 释放
    }
}
```

要点：

1. acquire 拿不到许可的线程进 AQS 队列 park → 状态 **WAITING**（同 ReentrantLock，不是 BLOCKED）。
2. **release 不强制配对 acquire**：一个线程 acquire、另一个也能 release——可做"通知"用（初始许可设 0，一个线程 release 唤醒等待者）。
3. **公平模式**：`new Semaphore(3, true)` 走 FIFO，默认非公平。
4. **典型场景**：限流、连接池容量控制、限制并发任务数。

> 对比：锁是"同一时刻 1 个"（互斥），Semaphore 是"同一时刻 N 个"（限流）。许可=1 的 Semaphore 退化成互斥锁，但**不支持重入**。