# JUC 同步工具指南

> JUC 按用途分四类，每类记住"用什么、干什么"即可。

---

## 一、锁与协调

| 工具 | 用途 |
|------|------|
| **ReentrantLock** | 互斥锁，可中断/可超时/可公平 |
| **Condition** | 锁上的条件队列，精确唤醒（生产者-消费者） |
| **Semaphore** | 限流，同时最多 N 个线程进入 |
| **CountDownLatch** | 倒计数到 0，主线程放行（一次性） |
| **CyclicBarrier** | N 个线程互相等齐一起走（可循环） |
| **ReadWriteLock / StampedLock** | 读写锁，读多写少场景 |

## 二、原子类

| 工具 | 用途 |
|------|------|
| **AtomicInteger / AtomicReference** | CAS 原子操作 |
| **LongAdder** | 高并发计数，比 AtomicLong 快 |
| **AtomicStampedReference** | 版本号解决 ABA |

## 三、并发容器

| 工具 | 用途 |
|------|------|
| **ConcurrentHashMap** | 并发 Map |
| **CopyOnWriteArrayList** | 读多写极少的 List |
| **BlockingQueue** 系列 | 阻塞队列，线程池的任务队列 |

## 四、异步执行框架

| 工具 | 用途 |
|------|------|
| **ThreadPoolExecutor** | 线程池 |
| **CompletableFuture** | 异步编排 |
| **ForkJoinPool** | 分治 + 任务窃取 |

### ThreadPoolExecutor 7 参数

```java
new ThreadPoolExecutor(
    corePoolSize,      // 1. 核心线程数:常驻,不回收
    maximumPoolSize,   // 2. 最大线程数:核心 + 临时工
    keepAliveTime,     // 3. 临时线程空闲存活时间
    TimeUnit.SECONDS,  // 4. 时间单位
    workQueue,         // 5. 任务队列:排队等执行
    threadFactory,     // 6. 线程工厂:起名字用
    handler            // 7. 拒绝策略:满了怎么办
);
```

**线程工厂怎么起名字**：

```java
AtomicInteger counter = new AtomicInteger(1);
ThreadFactory factory = r -> new Thread(r, "order-pool-" + counter.getAndIncrement());
```

> 不命名的话线程叫 `pool-1-thread-1`，出了问题看日志/线程 dump 分不清是哪个池的。
