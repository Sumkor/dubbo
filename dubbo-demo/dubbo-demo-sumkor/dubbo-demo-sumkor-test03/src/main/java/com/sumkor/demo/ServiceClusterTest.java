package com.sumkor.demo;

import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.registry.integration.RegistryProtocol;
import org.apache.dubbo.rpc.cluster.support.FailoverCluster;
import org.apache.dubbo.rpc.cluster.support.wrapper.MockClusterWrapper;

/**
 * 集群
 * https://dubbo.apache.org/zh/docs/v2.7/dev/source/cluster/
 *
 * 对于服务消费者来说，同一环境下出现了多个服务提供者。这时会出现一个问题，服务消费者需要决定选择哪个服务提供者进行调用。
 * 另外服务调用失败时的处理措施也是需要考虑的，是重试呢，还是抛出异常，亦或是只打印异常等。为了处理这些问题，Dubbo 定义了集群接口 Cluster 以及 Cluster Invoker。
 *
 * 集群 Cluster 用途是将多个服务提供者合并为一个 Cluster Invoker，并将这个 Invoker 暴露给服务消费者。
 * 这样一来，服务消费者只需通过这个 Invoker 进行远程调用即可，至于具体调用哪个服务提供者，以及调用失败后如何处理等问题，现在都交给集群模块去处理。
 * Cluster 是接口，而 Cluster Invoker 是一种 Invoker。服务提供者的选择逻辑，以及远程调用失败后的的处理逻辑均是封装在 Cluster Invoker 中。
 *
 * 集群模块是服务提供者和服务消费者的中间层，为服务消费者屏蔽了服务提供者的情况，
 * 这样服务消费者就可以专心处理远程调用相关事宜。比如发请求，接受服务提供者返回的数据等。这就是集群的作用。
 *
 * @author Sumkor
 * @since 2021/1/6
 */
public class ServiceClusterTest {

    /**
     * API 方式、注解方式分别启动同一个 dubbo 服务
     *
     * dubbo-demo/dubbo-demo-api/dubbo-demo-api-provider/src/main/java/org/apache/dubbo/demo/provider/Application.java
     * dubbo-demo/dubbo-demo-annotation/dubbo-demo-annotation-provider/src/main/java/org/apache/dubbo/demo/provider/Application.java
     *
     * 分别在 zk 上注册：
     *     dubbo://172.20.3.201:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=15620&release=&side=provider&timestamp=1609921334169
     *     dubbo://172.20.3.201:20881/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-annotation-provider&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=16108&release=&side=provider&timestamp=1609921150209
     *
     *
     * 1. 远程服务引入
     *
     * @see ReferenceConfig#createProxy(java.util.Map)
     * @see RegistryProtocol#refer(java.lang.Class, org.apache.dubbo.common.URL)
     * @see RegistryProtocol#doRefer(org.apache.dubbo.rpc.cluster.Cluster, org.apache.dubbo.registry.Registry, java.lang.Class, org.apache.dubbo.common.URL)
     *
     * Cluster 接口和相关实现类有什么用呢？用途比较简单，仅用于生成 Cluster Invoker。
     * 代码位置：
     *
     * Cluster cluster = Cluster.getCluster(qs.get(CLUSTER_KEY));
     * Invoker<T> invoker = cluster.join(directory);
     *
     * 其中
     *     url = zookeeper://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-consumer&dubbo=2.0.2&pid=14656&refer=application%3Ddubbo-demo-api-consumer%26dubbo%3D2.0.2%26generic%3Dfalse%26interface%3Dorg.apache.dubbo.demo.DemoService%26methods%3DsayHello%2CsayHelloAsync%26pid%3D14656%26register.ip%3D172.20.3.201%26side%3Dconsumer%26sticky%3Dfalse%26timestamp%3D1609928845850&timestamp=1609928848222
     *     registeredConsumerUrl = consumer://172.20.3.201/org.apache.dubbo.demo.DemoService?application=dubbo-demo-api-consumer&category=consumers&check=false&dubbo=2.0.2&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=14656&side=consumer&sticky=false&timestamp=1609928845850
     *     subscribeUrl = consumer://172.20.3.201/org.apache.dubbo.demo.DemoService?application=dubbo-demo-api-consumer&category=providers,configurators,routers&dubbo=2.0.2&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=14656&side=consumer&sticky=false&timestamp=1609928845850
     *     cluster = MockClusterWrapper(FailoverCluster)
     *
     * 关注 directory.invokers
     * 在执行了 toSubscribeUrl(subscribeUrl) 之后，得到了两个 invoker！！！
     *
     * 集群合并 invoker
     *
     * @see MockClusterWrapper#join(org.apache.dubbo.rpc.cluster.Directory)
     * @see FailoverCluster#doJoin(org.apache.dubbo.rpc.cluster.Directory)
     *
     *
     *
     */
}
