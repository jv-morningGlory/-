# Thread 经典题

> 多线程面试高频手撕题。核心套路就两个：**管程（wait/notify 或 Condition）协调执行顺序**、**锁 / Semaphore 保护共享资源**。

---

## 一、交替打印

> 多线程按指定顺序轮流输出（如 3 个线程轮流打印 1~100）。

**核心套路**：维护一个"轮到谁"的标记，打印后切换标记 + 唤醒下一个。

### 方式 1：synchronized + wait/notifyAll

```java
package com.cxsk;

public class PrintBySync {

    private static int NUMBER = 1;
    private static final int MAX = 100;
    // 0=A, 1=B, 2=C —— 标记当前该谁打印
    private static int turn = 0;

    public static void main(String[] args) {
        new Thread(() -> print(0), "thread-A").start();
        new Thread(() -> print(1), "thread-B").start();
        new Thread(() -> print(2), "thread-C").start();
    }

    private static void print(int myTurn) {
        synchronized (PrintBySync.class) {
            while (NUMBER <= MAX) {
                while (turn != myTurn && NUMBER <= MAX) {
                    try {
                        PrintBySync.class.wait();   // 不是我，继续等
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (NUMBER > MAX) {
                    PrintBySync.class.notifyAll();   // 叫醒还在等的，让它们也能退出
                    return;
                }
                System.out.println(Thread.currentThread().getName() + ":" + NUMBER);
                NUMBER++;
                turn = (turn + 1) % 3;   // 换下一个人
                PrintBySync.class.notifyAll();
            }
        }
    }
}
```

**要点**：

- 外层 `while (NUMBER <= MAX)` 控制总进度，内层 `while (turn != myTurn)` 守护"轮到我才动"
- 每次打印完 `turn = (turn+1)%3` 切换 + `notifyAll` 唤醒所有人，各线程自判是否轮到自己
- 到 MAX 后再 `notifyAll` 一次，叫醒仍在 `wait` 的线程退出，避免死等

### 方式 2：ReentrantLock + Condition（两 Condition 互相唤醒）

```java
package com.cxsk;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class PrintBySync {

    private static int NUMBER = 1;
    private static final int MAX = 100;

    public static void main(String[] args) {
        ReentrantLock lock = new ReentrantLock();
        Condition condition1 = lock.newCondition();
        Condition condition2 = lock.newCondition();

        new Thread(() -> print(lock, condition1, condition2), "thread-1").start();
        new Thread(() -> print(lock, condition2, condition1), "thread-2").start();
    }

    private static void print(ReentrantLock lock, Condition self, Condition other) {
        while (true) {
            lock.lock();
            try {
                if (NUMBER >= MAX) {
                    other.signal();
                    return;
                }
                System.out.println(Thread.currentThread().getName() + ": " + NUMBER);
                NUMBER++;
                other.signal();   // 叫醒对方
                self.await();     // 挂起自己
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
        }
    }
}
```

**要点**：

- 两个 Condition：线程 1 以 `condition1` 为 self、`condition2` 为 other；线程 2 反之
- 打印后 `other.signal()` 叫醒对方、`self.await()` 挂起自己，定向交替
- 比方式 1 精准（不用全吵醒），但只适合**固定两个线程**的交替

> 两种方式本质一致：**打印 → 切换 → 唤醒下一个 → 自己等待**。

---

## 二、生产者-消费者模型

> 共享队列上：满了生产者等、空了消费者等，靠 `signal` 互相通知。

**核心套路**：`ReentrantLock` + 两个 Condition（`notFull`、`notEmpty`）。

```java
package com.cxsk;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ProducerConsumer {

    private final Queue<Integer> queue = new LinkedList<>();
    private final int CAPACITY = 5;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull  = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    public void produce() throws InterruptedException {
        lock.lock();
        try {
            while (queue.size() == CAPACITY) {
                System.out.println("Queue is full, waiting...");
                notFull.await();
            }
            int data = (int) (Math.random() * 100);
            queue.add(data);
            System.out.println("Produced: " + data);
            notEmpty.signal();   // 有货了，通知消费者
        } finally {
            lock.unlock();
        }
    }

    public void consume() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                System.out.println("Queue is empty, waiting...");
                notEmpty.await();
            }
            int data = queue.remove();
            System.out.println("Consumed: " + data);
            notFull.signal();    // 有空位了，通知生产者
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) {
        ProducerConsumer pc = new ProducerConsumer();

        // 2 个生产者
        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                try {
                    while (true) {
                        pc.produce();
                        Thread.sleep(300);   // 模拟生产耗时
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Producer-" + i).start();
        }

        // 2 个消费者
        for (int i = 0; i < 2; i++) {
            new Thread(() -> {
                try {
                    while (true) {
                        pc.consume();
                        Thread.sleep(500);   // 比生产慢，会看到"仓库满"现象
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Consumer-" + i).start();
        }
    }
}
```

**要点**：

- `notFull`（没满才能生产）、`notEmpty`（非空才能消费）两个独立条件
- 生产者：满了 `notFull.await()`；生产完 `notEmpty.signal()`
- 消费者：空了 `notEmpty.await()`；消费完 `notFull.signal()`
- 判满 / 判空用 `while`（防**虚假唤醒**）
- 这是 Condition 多队列精准唤醒的典型应用（呼应 [Thread.md](./Thread.md) 第五节）

---

## 三、哲学家就餐问题

> 5 个哲学家围坐，每两人中间一根筷子（共 5 根），吃饭需同时拿起左右两根。

**死锁风险**：每人先拿左再拿右，5 人同时拿到左 → 全等右 → **死锁**（打破"循环等待"即可破）。

**本方案**：`Semaphore(4)` 限制最多 4 人进入"抢筷子"阶段，必有人拿不齐一双，打破循环等待。

```java
package com.cxsk;

import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DiningPhilosophers {

    private static final int NUM = 5;        // 哲学家/筷子数量
    private static final int MAX_MEALS = 3;  // 每人吃几次后退出（让程序自动结束）

    public static void main(String[] args) {
        // 1. 创建 5 根筷子（每根一把锁）
        Lock[] chopsticks = new ReentrantLock[NUM];
        for (int i = 0; i < NUM; i++) {
            chopsticks[i] = new ReentrantLock();
        }

        // 2. 信号量：最多允许 4 人同时进入"抢筷子"阶段，打破死锁
        Semaphore room = new Semaphore(NUM - 1);  // permits = 4

        // 3. 创建并启动 5 个哲学家线程
        Thread[] philosophers = new Thread[NUM];
        for (int i = 0; i < NUM; i++) {
            Runnable body = new PhilosopherSemaphore(i, chopsticks, room);
            philosophers[i] = new Thread(body, "哲学家-" + i);
            philosophers[i].start();
        }

        // 4. 等所有人吃完退出
        for (Thread p : philosophers) {
            try {
                p.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("所有哲学家都吃完了，程序结束。");
    }

    static class PhilosopherSemaphore implements Runnable {
        private final int id;
        private final Lock[] chopsticks;
        private final Semaphore room;
        private final Random rnd = new Random();

        PhilosopherSemaphore(int id, Lock[] chopsticks, Semaphore room) {
            this.id = id;
            this.chopsticks = chopsticks;
            this.room = room;
        }

        private void eat(int id, int meal) throws InterruptedException {
            int left = id;
            int right = (id + 1) % NUM;
            try {
                room.acquire();                  // 先抢"房间名额"
                try {
                    chopsticks[left].lock();
                    log(id, "拿起左筷子 " + left);
                    try {
                        chopsticks[right].lock();
                        log(id, "拿起右筷子 " + right + "，开吃（第 " + meal + " 次）");
                        Thread.sleep(rnd.nextInt(400) + 100);
                        log(id, "放下筷子 " + right + " 和 " + left);
                    } finally {
                        chopsticks[right].unlock();
                    }
                } finally {
                    chopsticks[left].unlock();
                }
            } finally {
                room.release();                  // 逐层释放
            }
        }

        @Override
        public void run() {
            for (int i = 0; i < MAX_MEALS; i++) {
                try {
                    eat(id, i);
                    think(id);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /* ============ 公共辅助方法 ============ */
    private static void think(int id) throws InterruptedException {
        log(id, "思考中...");
        Thread.sleep(new Random().nextInt(400) + 100);
    }

    private static void log(int id, String msg) {
        // 加锁只是为了控制台输出不串行混乱，与业务逻辑无关
        synchronized (DiningPhilosophers.class) {
            System.out.printf("[%d] %s%n", id, msg);
        }
    }
}
```

**要点**：

- 每根筷子一把 `ReentrantLock`；`Semaphore room = new Semaphore(NUM-1)`（permits=4）做人数限流
- 进餐前先 `room.acquire()` 抢名额 → 再抢左右筷子；吃完逐层 `release`（try-finally 嵌套保证释放）
- `log` 里用 `synchronized` 只为控制台不串行，与业务无关

> 其他破死锁思路：奇偶号哲学家拿筷顺序相反、一次性拿两根（CAS 思想破坏"占有等待"）、限就餐人数（本方案破坏"循环等待"）。死锁四条件破其一即可。
