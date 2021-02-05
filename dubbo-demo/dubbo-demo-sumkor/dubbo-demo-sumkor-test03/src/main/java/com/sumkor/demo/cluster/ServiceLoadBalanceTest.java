package com.sumkor.demo.cluster;

import org.apache.dubbo.rpc.RpcStatus;
import org.apache.dubbo.rpc.cluster.LoadBalance;
import org.apache.dubbo.rpc.cluster.loadbalance.*;

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
     * TODO 最小活跃数赋值
     * @see RpcStatus
     *
     *
     * 3. TODO 一致性 hash 算法
     *
     * @see ConsistentHashLoadBalance
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
     */
}
