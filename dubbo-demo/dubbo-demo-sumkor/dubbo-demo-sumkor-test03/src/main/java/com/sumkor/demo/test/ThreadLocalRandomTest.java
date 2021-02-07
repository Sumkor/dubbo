package com.sumkor.demo.test;

import org.apache.dubbo.rpc.cluster.loadbalance.RandomLoadBalance;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dubbo 的负载均衡算法，使用了 ThreadLocalRandom 代替 Random 生成随机数
 * @see RandomLoadBalance
 *
 * 为什么要使用 ThreadLocalRandom 代替 Random 生成随机数
 * https://www.cnblogs.com/shamo89/p/8052161.html
 *
 * @see Random
 * 在多线程下，使用 java.util.Random 产生的同一个实例来产生随机数是线程安全的，但深挖 Random 的实现过程，会发现多个线程会竞争同一 seed 而造成性能降低。
 * 其原因在于：
 * Random 生成新的随机数需要两步：
 *     根据老的 seed 生成新的 seed
 *     由新的 seed 计算出新的随机数
 * 其中，第二步的算法是固定的，如果每个线程并发地获取同样的 seed，那么得到的随机数也是一样的。
 * 为了避免这种情况，Random 使用 CAS 操作保证每次只有一个线程可以获取并更新 seed，失败的线程则需要自旋重试。
 * CAS 在资源高度竞争时的表现依然糟糕，后面的测试结果中可以看到它的糟糕表现。
 *
 *
 * @see ThreadLocalRandom
 * 1.7增加该类，企图将它和 Random 结合以克服所有的性能问题，该类继承自 Random。
 * 主要实现细节：
 *     使用一个普通的 long 而不是使用 Random 中的 AtomicLong 作为 seed
 *     不能自己创建 ThreadLocalRandom 实例，因为它的构造函数是私有的，可以使用它的静态工厂 ThreadLocalRandom.current()
 *     它是 CPU 缓存感知式的，使用 8个 long 虚拟域来填充 64位 L1高速缓存行
 *
 * @author Sumkor
 * @since 2021/2/7
 */
public class ThreadLocalRandomTest {

    private static final long COUNT = 10000000;
    private static final int THREADS = 2;

    /**
     * 一个单独的 Random 被 N 个线程共享
     * <p>
     * seed = 100，线程数 = 1 时，sum = 2024089153，执行多次都一样
     * seed = 100，线程数 = 2 时，每个线程的结果不一样，但是多个线程的结果相加，总和总是一样的，如下：
     * <p>
     * Thread #1 Time = 0.384 sec, sum = -1403399476
     * Thread #0 Time = 0.459 sec, sum = 1505576262
     */
    @Test
    public void testRandom() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(THREADS);
        final Random r = new Random(100);
        for (int i = 0; i < THREADS; ++i) {
            final Thread thread = new Thread(new RandomTask(r, i, COUNT, latch));
            thread.start();
        }
        latch.await();
    }

    /**
     * Random[], 其中每个线程 N 使用一个数组下标为 N 的 Random
     * <p>
     * Thread #0 Time = 0.138 sec, sum = 2024089153
     * Thread #1 Time = 0.172 sec, sum = 2024089153
     */
    @Test
    public void testRandomArray() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(THREADS);
        int padding = 1; // 不管值是多少，结果总是一样
        final Random[] rnd = new Random[THREADS * padding];
        for (int i = 0; i < THREADS * padding; ++i) {
            rnd[i] = new Random(100);
        }
        for (int i = 0; i < THREADS; ++i) {
            final Thread thread = new Thread(new RandomTask(rnd[i * padding], i, COUNT, latch));
            thread.start();
        }
        latch.await();
    }

    /**
     * 一个 ThreadLocal<Random> 被 N 个线程共享，每个线程使用各自的 Random 实例
     * <p>
     * 多次执行，结果一致：
     * Thread #0 Time = 0.107 sec, sum = 2024089153
     * Thread #1 Time = 0.274 sec, sum = 2024089153
     */
    @Test
    public void testThreadLocalAndRandom() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(THREADS);
        final ThreadLocal<Random> rnd = new ThreadLocal<Random>() {
            @Override
            protected Random initialValue() {
                return new Random(100);
            }
        };
        for (int i = 0; i < THREADS; ++i) {
            final Thread thread = new Thread(new RandomTask(null, i, COUNT, latch) {
                @Override
                protected Random getRandom() {
                    return rnd.get();
                }
            });
            thread.start();
        }
        latch.await();
    }

    /**
     * ThreadLocalRandom
     * <p>
     * 执行结果，没有规律：
     * Thread #1 Time = 0.021 sec, sum = 1177363424
     * Thread #0 Time = 0.025 sec, sum = -1910537305
     */
    @Test
    public void testThreadLocalRandom() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(THREADS);
        for (int i = 0; i < THREADS; ++i) {
            final Thread thread = new Thread(new RandomTask(null, i, COUNT, latch) {
                @Override
                protected Random getRandom() {
                    ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current(); // 每个线程使用各自的 ThreadLocalRandom
                    // threadLocalRandom.setSeed(100); // 不允许手动设置 seed
                    return threadLocalRandom;
                }
            });
            thread.start();
        }
        latch.await();
    }


    private static class RandomTask implements Runnable {
        private final Random random;
        protected final int id;
        private final long count;
        private final CountDownLatch latch;

        private RandomTask(Random random, int id, long count,
                           CountDownLatch latch) {
            super();
            this.random = random;
            this.id = id;
            this.count = count;
            this.latch = latch;
        }

        protected Random getRandom() {
            return random;
        }

        @Override
        public void run() {
            try {
                final Random r = getRandom();
                final long start = System.currentTimeMillis();
                int sum = 0;
                for (long j = 0; j < count; j++) {
                    sum += r.nextInt();
//                    System.out.print(sum + " ");
                }
//                System.out.println();
                final long time = System.currentTimeMillis() - start;
                System.out.println("Thread #" + id + " Time = " + time / 1000.0 + " sec, sum = " + sum);
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
