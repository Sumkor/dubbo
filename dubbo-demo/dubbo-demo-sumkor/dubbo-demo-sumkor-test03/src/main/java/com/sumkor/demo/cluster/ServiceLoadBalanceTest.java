package com.sumkor.demo.cluster;

import org.apache.dubbo.rpc.cluster.LoadBalance;
import org.apache.dubbo.rpc.cluster.loadbalance.AbstractLoadBalance;

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
 *
 * @author Sumkor
 * @since 2021/1/14
 */
public class ServiceLoadBalanceTest {

    /**
     * 在 Dubbo 中，所有负载均衡实现类均继承自 AbstractLoadBalance，该类实现了 LoadBalance 接口，并封装了一些公共的逻辑。
     *
     * @see LoadBalance#select(java.util.List, org.apache.dubbo.common.URL, org.apache.dubbo.rpc.Invocation)
     * @see AbstractLoadBalance#select(java.util.List, org.apache.dubbo.common.URL, org.apache.dubbo.rpc.Invocation)
     *
     *
     */
}
