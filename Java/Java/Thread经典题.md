1.交替打印
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
                other.signal();
                self.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }

        }

    }


}


5. ⭐ 生产者-消费者模型



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
            queue.add( data);
            System.out.println("Produced: " + data);
            notEmpty.signal();
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
            notFull.signal();
        } finally {
            lock.unlock();
        }
    }

    // 5. 启动
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
                        Thread.sleep(500);   // 模拟消费耗时（比生产慢，会看到"仓库满"现象）
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Consumer-" + i).start();
        }
    }

}



