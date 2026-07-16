# Synchronized 原理与锁升级

> **一句话**：每个对象天生自带一个 **monitor**（管程），`synchronized` 就是去抢占目标对象的 monitor。谁拿到 monitor 的所有权，谁就持有锁。

---

## 一、synchronized 锁住的是什么

锁的是**对象的 monitor**，锁信息存在对象的**对象头（Object Header）**里。

```
对象头 (Object Header)
├── Mark Word      ← 存锁标志位、线程ID、HashCode、GC年龄、锁指针
└── Klass Pointer  ← 指向类元数据
```

---

## 二、对象锁 vs Class 对象锁

`Class` 对象本身也是一个**普通 Java 对象**（有对象头、有 monitor），所以"类锁"没有任何特殊机制——就是锁了这个 `Class` 对象。

| 写法 | 锁的是 | 等价形式 |
|------|--------|---------|
| `synchronized(obj)` | 实例对象 `obj` 的 monitor | — |
| `synchronized(this)` / 普通同步方法 | 当前实例 `this` 的 monitor | `public synchronized void m(){}` |
| `synchronized(A.class)` | `A` 的 `Class` 对象的 monitor | — |
| 静态同步方法 | 当前类的 `Class` 对象的 monitor | `public static synchronized void m(){}` |

```java
class Service {
    public synchronized void methodA() {}         // 对象锁：锁 this
    public synchronized void methodB() {}         // 对象锁：锁 this（与 A 同一把）
    public static synchronized void methodC() {}  // Class 锁：锁 Service.class
}
```

> **关键结论**：对象锁和 Class 锁是**两个不同的对象 = 两把不同的锁**，互不干扰。一个线程进 `methodA`、另一个线程进 `methodC` 可以同时跑。

---

## 三、锁的等级与获取流程

### 1. 锁的三个等级

| 锁级别 | 适用场景 | 加锁方式 | 到 OS 层吗 |
|--------|---------|---------|-----------|
| 偏向锁 | 只有一个线程访问，无竞争 | Mark Word 直接记线程 ID | 否（零开销） |
| 轻量级锁 | 多线程交替访问，偶尔撞、CAS 能搞定 | 用户态 CAS 自旋 | 否 |
| 重量级锁 | 激烈竞争，CAS 自旋也抢不到 | ObjectMonitor + OS 挂起 | **是** |

### 2. 判断标准（最容易踩坑）

**不是数线程数量定级别，而是看"CAS 自旋能否解决问题"**：

```
只有1个线程反复进出        → 偏向锁
多线程错峰交替、CAS能赢     → 轻量级锁
多线程同时死磕、CAS自旋失败 → 重量级锁
```

> 反例：同样是"2 个线程"，错峰交替来是轻量级锁，同时死磕抢就是重量级锁。决定权在"撞不撞、撞了 CAS 赢不赢"。

### 3. 各阶段获取锁的流程

#### 偏向锁

```
首次进入：CAS 把线程ID 写入 Mark Word
同线程再进：比对线程ID 相等 → 直接放行（零开销）
出现第二个线程竞争 → 撤销偏向，升级轻量级锁
```

#### 轻量级锁（CAS 自旋）

```
进入：CAS 尝试把对象头指向自己栈中的 Lock Record
  ├── 成功 → 持有锁，进入执行
  └── 失败 → 自旋重试 N 次
              ├── 自旋中抢到 → 进入执行
              └── 自旋超阈值还失败 → 升级重量级锁
```

#### 重量级锁（ObjectMonitor + OS 挂起）

激烈竞争时，JVM **按需创建** ObjectMonitor（不是一 `synchronized` 就有），对象头放一个指针指向它：

```
ObjectMonitor
├── _owner      → 持有锁的线程
├── _EntryList  → 排队抢锁的线程（状态 BLOCKED）
├── _WaitSet    → 调用 wait() 的线程（状态 WAITING）
└── _recursions → 重入次数
```

**完整抢锁流程**：

```
monitorenter
    │
    ▼
CAS 抢 ObjectMonitor._owner
    │
    ├── 抢到 → owner=自己，进入同步块执行
    │
    └── 没抢到 → 进 _EntryList → OS 挂起（BLOCKED，不占 CPU）
                                    │
                       持锁者 monitorexit，JVM unpark 唤醒
                                    │
                            线程醒来，再 CAS 抢
                                    │
                    （非公平：可能还抢不到，回去继续挂，反复几次）
                                    │
                              抢到 → 进入执行
```

> **唤醒 ≠ 给你锁**：JVM 只是 `unpark` 把线程叫醒，醒来后自己再 CAS 抢。`synchronized` 默认非公平，可能唤醒多个线程一起抢，也可能被插队——所以高竞争下线程状态会在 BLOCKED / RUNNABLE 间频繁抖动。

### 4. 锁升级触发点（不可逆）

```
偏向锁 ──出现第二个线程──► 轻量级锁 ──CAS自旋失败──► 重量级锁
```

- 升级**不可逆**（一般不会自动降级）
- 偏向锁撤销、轻量级锁自旋失败，是两个升级触发点

> **JDK 15 起偏向锁被废弃**：维护成本高、收益小，现在默认从轻量级锁起步。新项目实际只有：轻量级锁 ↔ 重量级锁。
