package com.sumkor.demo.cluster;

import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.rpc.RpcStatus;
import org.apache.dubbo.rpc.cluster.Constants;
import org.apache.dubbo.rpc.cluster.LoadBalance;
import org.apache.dubbo.rpc.cluster.loadbalance.*;
import org.apache.dubbo.rpc.filter.ActiveLimitFilter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 负载均衡
 * https://dubbo.apache.org/zh/docs/v2.7/dev/source/loadbalance/
 *
 * LoadBalance 中文意思为负载均衡，它的职责是将网络请求，或者其他形式的负载“均摊”到不同的机器上。避免集群中部分服务器压力过大，而另一些服务器比较空闲的情况。
 * 通过负载均衡，可以让每台服务器获取到适合自己处理能力的负载。在为高负载服务器分流的同时，还可以避免资源浪费，一举两得。
 * Dubbo 需要对服务消费者的调用请求进行分配，避免少数服务提供者负载过大。服务提供者负载过大，会导致部分请求超时。因此将负载均衡到每个服务提供者上，是非常必要的。
 *
 * Dubbo 提供了4种负载均衡实现，分别是：
 * 基于权重随机算法的 RandomLoadBalance
 * 基于最少活跃调用数算法的 LeastActiveLoadBalance
 * 基于 hash 一致性的 ConsistentHashLoadBalance
 * 基于加权轮询算法的 RoundRobinLoadBalance
 * 最短响应时间负载均衡 ShortestResponseLoadBalance
 *
 * @author Sumkor
 * @since 2021/1/14
 */
public class ServiceLoadBalanceTest {

    /**
     * @see AbstractLoadBalance
     *
     * 在 Dubbo 中，所有负载均衡实现类均继承自 AbstractLoadBalance，该类实现了 LoadBalance 接口，并封装了一些公共的逻辑。
     *
     * @see LoadBalance#select(java.util.List, org.apache.dubbo.common.URL, org.apache.dubbo.rpc.Invocation)
     * @see AbstractLoadBalance#select(java.util.List, org.apache.dubbo.common.URL, org.apache.dubbo.rpc.Invocation)
     *
     * AbstractLoadBalance 除了实现了 LoadBalance 接口方法，还封装了一些公共逻辑，比如服务提供者权重计算逻辑：
     * @see AbstractLoadBalance#getWeight(org.apache.dubbo.rpc.Invoker, org.apache.dubbo.rpc.Invocation)
     *
     * 权重的计算过程，该过程主要用于保证当服务运行时长小于服务预热时间时，对服务进行降权，避免让服务在启动之初就处于高负载状态。
     * 服务预热是一个优化手段，与此类似的还有 JVM 预热。主要目的是让服务启动后“低功率”运行一段时间，使其效率慢慢提升至最佳状态。
     *
     *
     * 1. 加权随机算法
     *
     * @see RandomLoadBalance
     *
     * 算法思想：
     * 假设我们有一组服务器 servers = [A, B, C]，他们对应的权重为 weights = [5, 3, 2]，权重总和为10。
     * 现在把这些权重值平铺在一维坐标值上，[0, 5) 区间属于服务器 A，[5, 8) 区间属于服务器 B，[8, 10) 区间属于服务器 C。
     * 接下来通过随机数生成器生成一个范围在 [0, 10) 之间的随机数，然后计算这个随机数会落到哪个区间上。
     * 比如数字3会落到服务器 A 对应的区间上，此时返回服务器 A 即可。
     *
     * 缺点：
     * 当调用次数比较少时，Random 产生的随机数可能会比较集中，此时多数请求会落到同一台服务器上。
     * 这个缺点并不是很严重，多数情况下可以忽略。RandomLoadBalance 是一个简单，高效的负载均衡实现，因此 Dubbo 选择它作为缺省实现。
     *
     * 权重通过 {@link DubboService} 注解配置在服务提供者的接口实现类上，默认权重是 100 {@link Constants#DEFAULT_WEIGHT}
     *
     *
     * 2. 最小活跃数负载均衡
     *
     * @see LeastActiveLoadBalance
     *
     * 算法思想：
     * 活跃调用数越小，表明该服务提供者效率越高，单位时间内可处理更多的请求。此时应优先将请求分配给该服务提供者。
     * 在具体实现中，每个服务提供者对应一个活跃数 active。初始情况下，所有服务提供者活跃数均为 0。每收到一个请求，活跃数加 1，完成请求后则将活跃数减 1。
     * 在服务运行一段时间后，性能好的服务提供者处理请求的速度更快，因此活跃数下降的也越快，此时这样的服务提供者能够优先获取到新的服务请求。
     *
     * 除了最小活跃数，LeastActiveLoadBalance 在实现上还引入了权重值。所以准确的来说，LeastActiveLoadBalance 是基于加权最小活跃数算法实现的。
     * 举个例子说明一下，在一个服务提供者集群中，有两个性能优异的服务提供者。
     * 某一时刻它们的活跃数相同，此时 Dubbo 会根据它们的权重去分配请求，权重越大，获取到新请求的概率就越大。
     * 如果两个服务提供者权重相同，此时随机选择一个即可。
     *
     * 代码流程：
     *     遍历 invokers 列表，寻找活跃数最小的 Invoker
     *     如果有多个 Invoker 具有相同的最小活跃数，此时记录下这些 Invoker 在 invokers 集合中的下标，并累加它们的权重，比较它们的权重值是否相等
     *     如果只有一个 Invoker 具有最小的活跃数，此时直接返回该 Invoker 即可
     *     如果有多个 Invoker 具有最小活跃数，且它们的权重不相等，此时处理方式和 RandomLoadBalance 一致
     *     如果有多个 Invoker 具有最小活跃数，但它们的权重相等，此时随机返回一个即可
     *
     * 最小活跃数如何赋值？如何获取？
     * 需要在客户端配置 {@link ActiveLimitFilter} 使得 {@link RpcStatus#active} 生效。
     * 即，最少活跃数负载均衡算法必须配合 ActiveLimitFilter 使用。
     * 客户端发送请求时，记录对应的服务端活跃数 + 1；客户端接收到响应后，记录对应服务端的活跃数 - 1。
     * 这里的最小活跃数，是基于同一个客户端的视角，观察多个服务端的结果。并不代表客观上的服务端的活跃数！
     *
     *
     * 3. 一致性 hash 算法
     *
     * @see ConsistentHashLoadBalance
     *
     * 首先根据 ip 或者其他的信息为缓存节点生成一个 hash，并将这个 hash 投射到 [0, 2^32 - 1] 的圆环上。
     * 当有查询或写入请求时，则为缓存项的 key 生成一个 hash 值。然后查找第一个大于或等于该 hash 值的缓存节点，并到这个节点中查询或写入缓存项。
     * 如果当前节点挂了，则在下一次查询或写入缓存时，为缓存项查找另一个大于其 hash 值的缓存节点即可。
     *
     * 需要特别说明的是，ConsistentHashLoadBalance 的负载均衡逻辑只受参数值影响，具有相同参数值的请求将会被分配给同一个服务提供者。
     * ConsistentHashLoadBalance 不关心权重，因此使用时需要注意一下。
     *
     * 一致性哈希的应用场景：
     * 当大家谈到一致性哈希算法的时候，首先的第一印象应该是在缓存场景下的使用，因为在一个优秀的哈希算法加持下，其上下线节点对整体数据的影响(迁移)都是比较友好的。
     * 但是想一下为什么 Dubbo 在负载均衡策略里面提供了基于一致性哈希的负载均衡策略？它的实际使用场景是什么？
     * 我最开始也想不明白。我想的是在 Dubbo 的场景下，假设需求是想要一个用户的请求一直让一台服务器处理，那我们可以采用一致性哈希负载均衡策略，把用户号进行哈希计算，可以实现这样的需求。但是这样的需求未免有点太牵强了，适用场景略小。
     * 直到有天晚上，我睡觉之前，电光火石之间突然想到了一个稍微适用的场景了。
     * 如果需求是需要保证【某一类请求必须顺序处理】呢？
     * 如果你用其他负载均衡策略，请求分发到了不同的机器上去，就很难保证请求的顺序处理了。比如A，B请求要求顺序处理，现在A请求先发送，被负载到了A服务器上，B请求后发送，被负载到了B服务器上。
     * 而B服务器由于性能好或者当前没有其他请求或者其他原因极有可能在A服务器还在处理A请求之前就把B请求处理完成了。这样不符合我们的要求。
     * 这时，一致性哈希负载均衡策略就上场了，它帮我们保证了某一类请求都发送到固定的机器上去执行。比如把同一个用户的请求发送到同一台机器上去执行，就意味着把某一类请求发送到同一台机器上去执行。
     * 所以我们只需要在该机器上运行的程序中保证顺序执行就行了，比如你加一个队列。
     * 一致性哈希算法+队列，可以实现顺序处理的需求。
     * https://blog.csdn.net/qq_27243343/article/details/106459095
     *
     *
     * 4. 加权轮询负载均衡
     *
     * @see RoundRobinLoadBalance
     *
     * 所谓【轮询】是指将请求轮流分配给每台服务器。
     * 举个例子，我们有三台服务器 A、B、C。我们将第一个请求分配给服务器 A，第二个请求分配给服务器 B，第三个请求分配给服务器 C，第四个请求再次分配给服务器 A。
     * 轮询是一种无状态负载均衡算法，实现简单，适用于每台服务器性能相近的场景下。
     *
     * 但现实情况下，我们并不能保证每台服务器性能均相近。需要对轮询过程进行【加权】，以调控每台服务器的负载。
     * 经过加权后，每台服务器能够得到的请求数比例，接近或等于他们的权重比。
     * 比如服务器 A、B、C 权重比为 5:2:1。那么在 8次请求中，服务器 A 将收到其中的 5次请求，服务器 B 会收到其中的 2次请求，服务器 C 则收到其中的 1次请求。
     *
     * 参考 Nginx 的【平滑加权轮询负载均衡】。
     * 每个服务器对应两个权重，分别为 weight 和 currentWeight。其中 weight 是固定的，currentWeight 会动态调整，初始值为 0。
     * 当有新的请求进来时，遍历服务器列表，让它的 currentWeight 加上自身权重。
     * 遍历完成后，找到最大的 currentWeight，并将其减去权重总和，然后返回相应的服务器即可。
     *
     * 上面描述不是很好理解，下面还是举例进行说明。
     * 这里使用服务器 [A, B, C] 对应权重 [5, 1, 1] 的例子说明，现在有 7个请求依次进入负载均衡逻辑，选择过程如下：
     *
     * 请求编号 currentWeight数组  选择结果 减去权重总和后的currentWeight数组
     *   1	    [5, 1, 1]	         A	    [-2, 1, 1]
     *   2	    [3, 2, 2]	         A	    [-4, 2, 2]
     *   3	    [1, 3, 3]	         B	    [1, -4, 3]
     *   4	    [6, -3, 4]           A	    [-1, -3, 4]
     *   5	    [4, -2, 5]	         C	    [4, -2, -2]
     *   6	    [9, -1, -1]	         A	    [2, -1, -1]
     *   7   	[7, 0, 0]	         A	    [0, 0, 0]
     *
     *
     * 5. 最短响应时间负载均衡
     *
     * @see ShortestResponseLoadBalance
     *
     * 从多个服务提供者中选择出调用成功的且响应时间最短的服务提供者，由于满足这样条件的服务提供者有可能有多个。所以当选择出多个服务提供者后要根据他们的权重做分析。
     * 但是如果只选择出来了一个，直接用选出来这个。
     * 如果真的有多个，看它们的权重是否一样，如果不一样，则走加权随机算法的逻辑。
     * 如果它们的权重是一样的，则随机调用一个。
     *
     * 最短响应时间如何获取？
     * 请求当前服务提供者的 预计等待响应时间 = 获取调用成功的平均时间 * 活跃数
     * 获取调用成功的平均时间 = 调用成功的请求数总数对应的总耗时 / 调用成功的请求数总数
     * @see RpcStatus#getSucceededAverageElapsed()
     * @see RpcStatus#endCount(org.apache.dubbo.rpc.RpcStatus, long, boolean)
     */

    /**
     * 加权轮询负载均衡 自测
     */
    @Test
    public void roundRobin() {
        String[] nodes = new String[]{"A", "B", "C"}; // 节点
        int[] weights = new int[]{5, 1, 1}; // 权重

        List<String> resultList = new ArrayList<>();
        for (int i = 0; i < nodes.length; i++) {
            String node = nodes[i];
            int weight = weights[i];
            for (int j = 0; j < weight; j++) {
                resultList.add(node);
            }
        }
        System.out.println("resultList = " + resultList); // [A, A, A, A, A, B, C]
        Collections.shuffle(resultList); // 洗牌
        System.out.println("resultList = " + resultList); // [A, A, A, B, C, A, A]
    }

    @Test
    public void identityHashCode() {
        int hashCode01 = System.identityHashCode("HAHA");
        int hashCode02 = System.identityHashCode("HEHE");
        System.out.println("hashCode01 = " + hashCode01);
        System.out.println("hashCode02 = " + hashCode02);

        List<String> list = new ArrayList<>();
        list.add("AAA");
        System.out.println("list.hashCode() = " + list.hashCode());
        list.add("BBB");
        System.out.println("list.hashCode() = " + list.hashCode());

        /**
         * 执行结果：
         *
         * hashCode01 = 1122805102
         * hashCode02 = 1391942103
         * list.hashCode() = 64576
         * list.hashCode() = 2067394
         */
    }

}
