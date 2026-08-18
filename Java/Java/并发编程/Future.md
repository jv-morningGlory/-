# Future 异步框架

## 是什么

**Future** 是 Java 表示"异步结果"的接口：把耗时的任务交给别的线程去做，你先拿到一个"凭证"，之后再凭证取结果。

```java
Future<String> future = executor.submit(() -> doQuery());  // 交出去，立即返回
String result = future.get();                              // 需要时取结果（阻塞）
```

## 两个实现

| 类 | 特点 | 一句话 |
|---|---|---|
| `FutureTask` | JDK 1.5，老实现 | 只能阻塞等结果，不能链式操作 |
| `CompletableFuture` | JDK 8，增强版 | 支持回调、链式编排、组合多个任务 |

> Future 的痛点：`get()` 会阻塞，也没法在完成后自动执行下一步——所以实际开发基本都用 `CompletableFuture`。

---

## CompletableFuture 常用方案

| 选择 | 方法 |
|---|---|
| 下一步是纯计算 | `thenApply` |
| 下一步还要调异步 | `thenCompose` |
| 合并两个任务 | `thenCombine` |
| 等全部完成 | `allOf` |
| 取最快的 | `anyOf` |
| 出异常给默认值 | `exceptionally` / `handle` |

> 所有无参线程池的方法（`thenApply` 等）默认跑在上一步的线程或 commonPool 上；IO 任务统一显式传自定义线程池，别用默认的。

---

## 实战场景：并行查询 5 家机票渠道，3s 内汇总结果

```java
List<CompletableFuture<Fare>> futures = channels.stream()
        .map(ch -> CompletableFuture
                .supplyAsync(() -> queryOne(ch, req), pool)      // 并行发起
                .completeOnTimeout(null, 3, TimeUnit.SECONDS)    // 单渠道 3s 兜底，超时给 null
                .exceptionally(e -> null))                      // 异常兜底，失败给 null
        .toList();

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();  // 最多等 3s

// 汇总，过滤掉超时/失败的渠道
return futures.stream()
        .map(CompletableFuture::join)
        .filter(Objects::nonNull)
        .toList();
```

