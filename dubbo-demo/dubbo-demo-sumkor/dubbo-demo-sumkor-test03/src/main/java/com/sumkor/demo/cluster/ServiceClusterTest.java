package com.sumkor.demo.cluster;

import org.apache.dubbo.common.timer.HashedWheelTimer;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.registry.integration.RegistryDirectory;
import org.apache.dubbo.registry.integration.RegistryProtocol;
import org.apache.dubbo.registry.zookeeper.ZookeeperRegistry;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.support.*;
import org.apache.dubbo.rpc.cluster.support.wrapper.MockClusterWrapper;
import org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol;

/**
 * 集群容错
 * https://dubbo.apache.org/zh/docs/v2.7/dev/source/cluster/
 *
 * 对于服务消费者来说，同一环境下出现了多个服务提供者。这时会出现一个问题，服务消费者需要决定选择哪个服务提供者进行调用。
 * 另外服务调用失败时的处理措施也是需要考虑的，是重试呢，还是抛出异常，亦或是只打印异常等。为了处理这些问题，Dubbo 定义了集群接口 Cluster 以及 Cluster Invoker。
 *
 * @see FailoverCluster
 * @see FailoverClusterInvoker
 * 集群 Cluster 用途是将多个服务提供者合并为一个 Cluster Invoker，并将这个 Invoker 暴露给服务消费者。
 * 这样一来，服务消费者只需通过这个 Invoker 进行远程调用即可，至于具体调用哪个服务提供者，以及调用失败后如何处理等问题，现在都交给集群模块去处理。
 * Cluster 是接口，而 Cluster Invoker 是一种 Invoker。服务提供者的选择逻辑，以及远程调用失败后的的处理逻辑均是封装在 Cluster Invoker 中。
 *
 * 集群模块是服务提供者和服务消费者的中间层，为服务消费者屏蔽了服务提供者的情况，
 * 这样服务消费者就可以专心处理远程调用相关事宜。比如发请求，接受服务提供者返回的数据等。这就是集群的作用。
 *
 *
 * 集群工作过程可分为两个阶段：
 * 第一个阶段是在服务消费者初始化期间，集群 Cluster 实现类为服务消费者创建 Cluster Invoker 实例，即 merge 操作。
 * 第二个阶段是在服务消费者进行远程调用时。以 FailoverClusterInvoker 为例，该类型 Cluster Invoker 首先会调用 Directory 的 list 方法列举 Invoker 列表（可将 Invoker 简单理解为服务提供者）。
 * Directory 的用途是保存 Invoker，可简单类比为 List<Invoker>。
 * 当 FailoverClusterInvoker 拿到 Directory 返回的 Invoker 列表后，它会通过 LoadBalance 从 Invoker 列表中选择一个 Invoker。
 * 最后 FailoverClusterInvoker 会将参数传给 LoadBalance 选择出的 Invoker 实例的 invoke 方法，进行真正的远程调用。
 *
 *
 *
 * Dubbo 主要提供了这样几种容错方式：
 *
 *     Failover Cluster - 失败自动切换
 *     Failfast Cluster - 快速失败
 *     Failsafe Cluster - 失败安全
 *     Failback Cluster - 失败自动恢复
 *     Forking Cluster - 并行调用多个服务提供者
 *     Broadcast Cluster - 广播
 *
 *
 *
 * Dubbo 容错策略既可以在服务提供方配置，也可以服务调用方进行配置。
 * 而重试次数的配置则更为灵活，既可以在服务级别进行配置，也可以在方法级别进行配置。
 * 具体优先顺序为：
 * 服务调用方方法级配置 > 服务调用方服务级配置 > 服务提供方方法级配置 > 服务提供方服务级配置
 * https://dubbo.apache.org/zh/blog/2018/08/22/dubbo-%E9%9B%86%E7%BE%A4%E5%AE%B9%E9%94%99/
 *
 *
 * @author Sumkor
 * @since 2021/1/6
 */
public class ServiceClusterTest {

    /**
     * 集群工作第一阶段：在服务消费者的初始化期间，合并 Invoker
     *
     *
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
     * 关注 directory#invokers
     * 在执行了 toSubscribeUrl(subscribeUrl) 之后，得到了两个 invoker！！！
     *
     * 2. 生成 invoker
     *
     * @see ZookeeperRegistry#doSubscribe(org.apache.dubbo.common.URL, org.apache.dubbo.registry.NotifyListener)
     *
     * zkClient.addChildListener(path, zkListener);
     * 读取 zk 目录 /dubbo/org.apache.dubbo.demo.DemoService/providers
     * 得到两个子节点（这里的 pid 和时间戳不严谨，理解即可）：
     *     dubbo://172.20.3.201:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=13660&release=&side=provider&timestamp=1610422184655
     *     dubbo://172.20.3.201:20881/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-annotation-provider&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=14916&release=&side=provider&timestamp=1610422205251
     *
     * 设置 {@link RegistryDirectory#overrideDirectoryUrl} 即 {@link Directory#getConsumerUrl()} 的值：
     * @see RegistryDirectory#refreshOverrideAndInvoker(java.util.List)
     *     zookeeper://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-consumer&dubbo=2.0.2&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=29644&register.ip=172.20.3.201&side=consumer&sticky=false&timestamp=1610526156942
     *
     * 根据 provider url 创建 invoker
     * @see RegistryDirectory#refreshInvoker(java.util.List)
     * @see RegistryDirectory#toInvokers(java.util.List)
     *
     * 对 provider url 进行转换
     * URL url = mergeUrl(providerUrl);
     *     dubbo://172.20.3.201:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-consumer&check=false&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=16284&register.ip=172.20.3.201&release=&remote.application=dubbo-demo-api-provider&side=consumer&sticky=false&timestamp=1610422184655
     *     dubbo://172.20.3.201:20881/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-consumer&check=false&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=16284&register.ip=172.20.3.201&release=&remote.application=dubbo-demo-annotation-provider&side=consumer&sticky=false&timestamp=1610422205251
     *
     * 执行 DubboProtocol#refer，得到 DubboInvoker
     * @see DubboProtocol#protocolBindingRefer(java.lang.Class, org.apache.dubbo.common.URL)
     *
     *
     * 3. 集群合并 invoker
     *
     * @see RegistryProtocol#doRefer(org.apache.dubbo.rpc.cluster.Cluster, org.apache.dubbo.registry.Registry, java.lang.Class, org.apache.dubbo.common.URL)
     *
     * 当 RegistryDirectory 中包含了两个 DubboInvoker 之后，要进行合并操作
     * @see MockClusterWrapper#join(org.apache.dubbo.rpc.cluster.Directory)
     * @see FailoverCluster#doJoin(org.apache.dubbo.rpc.cluster.Directory)
     *
     * 即，把两个 DubboInvoker 封装在 FailoverClusterInvoker 之中返回
     *
     */

    /**
     * 集群工作第二阶段：在服务消费者的远程调用期间，进行负载均衡和集群容错处理。
     *
     * 发起调用，入口
     * @see AbstractClusterInvoker#invoke(org.apache.dubbo.rpc.Invocation)
     *
     * 其中，列举 Invoker
     * @see AbstractClusterInvoker#list(org.apache.dubbo.rpc.Invocation)
     *
     * 加载 LoadBalance
     * @see AbstractClusterInvoker#initLoadBalance(java.util.List, org.apache.dubbo.rpc.Invocation)
     *
     *
     *
     * 1. 失败自动切换，入口
     * @see FailoverClusterInvoker#doInvoke(org.apache.dubbo.rpc.Invocation, java.util.List, org.apache.dubbo.rpc.cluster.LoadBalance)
     * FailoverClusterInvoker 在调用失败时，会自动切换 Invoker 进行重试。默认配置下，Dubbo 会使用这个类作为缺省 Cluster Invoker。
     *
     * FailoverClusterInvoker 的 doInvoke 方法首先是获取重试次数，然后根据重试次数进行循环调用，失败后进行重试。
     * 在 for 循环内，首先是通过负载均衡组件选择一个 Invoker，然后再通过这个 Invoker 的 invoke 方法进行远程调用。
     * 如果失败了，记录下异常，并进行重试。重试时会再次列举 Invoker。
     * 整个流程大致如此，不是很难理解。
     *
     * 负载均衡选择节点：
     * @see AbstractClusterInvoker#select(org.apache.dubbo.rpc.cluster.LoadBalance, org.apache.dubbo.rpc.Invocation, java.util.List, java.util.List)
     *
     * 这里的粘滞指的是：多次服务调用过程中，如果第一次服务调用成功，则第二次调用，服务消费者依旧会调用到同一个服务提供者。
     * 即，粘滞 是作用在多次服务调用过程中的，而不是作用在同一次服务调用的 重试 过程中。
     *
     * 关于 FailoverClusterInvoker 是否是单例？
     * 已知在 {@link RegistryProtocol#doRefer} 中执行 Invoker<T> invoker = cluster.join(directory);
     * 会执行 {@link FailoverCluster#doJoin} 利用 FailoverCluster 生成 FailoverClusterInvoker 实例。
     * 可知，对于一个服务提供者接口，生成一个 ClusterInvoker 实例，服务消费者对该接口发起多次调用，使用的是同一个 ClusterInvoker 实例。
     * 即，对于同一个接口来说，FailoverClusterInvoker 是单例的。
     * 因此可以使用 {@link AbstractClusterInvoker#stickyInvoker} 这个 volatile 变量来存储上一次调用使用的 invoker，达到粘滞连接的效果。
     *
     *
     *
     * 2. 失败自动恢复，入口
     * @see FailbackClusterInvoker#doInvoke(org.apache.dubbo.rpc.Invocation, java.util.List, org.apache.dubbo.rpc.cluster.LoadBalance)
     * FailbackClusterInvoker 会在调用失败后，返回一个空结果给服务消费者。并通过定时任务对失败的调用进行重传，适合执行消息通知等操作。
     *
     * 若远程调用失败，则通过 addFailed 方法将调用信息存入到 failed 中，等待定时重试。
     * @see FailbackClusterInvoker#addFailed(org.apache.dubbo.rpc.cluster.LoadBalance, org.apache.dubbo.rpc.Invocation, java.util.List, org.apache.dubbo.rpc.Invoker)
     * @see FailbackClusterInvoker.RetryTimerTask#run(org.apache.dubbo.common.timer.Timeout)
     *
     * TODO 时间轮算法 {@link HashedWheelTimer}
     *
     *
     *
     * 3. 快速失败，入口
     * @see FailfastClusterInvoker#doInvoke(org.apache.dubbo.rpc.Invocation, java.util.List, org.apache.dubbo.rpc.cluster.LoadBalance)
     * FailfastClusterInvoker 只会进行一次调用，失败后立即抛出异常。适用于幂等操作，比如新增记录。
     *
     *
     *
     * 4. 失败安全，入口
     * @see FailsafeClusterInvoker#doInvoke(org.apache.dubbo.rpc.Invocation, java.util.List, org.apache.dubbo.rpc.cluster.LoadBalance)
     * FailsafeClusterInvoker 是一种失败安全的 Cluster Invoker。所谓的失败安全是指，当调用过程中出现异常时，FailsafeClusterInvoker 仅会打印异常，而不会抛出异常。适用于写入审计日志等操作。
     *
     *
     *
     * 5. 并行调用，入口
     * @see ForkingClusterInvoker#doInvoke(org.apache.dubbo.rpc.Invocation, java.util.List, org.apache.dubbo.rpc.cluster.LoadBalance)
     * ForkingClusterInvoker 会在运行时通过线程池创建多个线程，并发调用多个服务提供者。只要有一个服务提供者成功返回了结果，doInvoke 方法就会立即结束运行。
     * 应用场景是在一些对实时性要求比较高读操作（注意是读操作，并行写操作可能不安全）下使用，但这将会耗费更多的资源。
     *
     *
     *
     * 6. 广播，入口
     * @see BroadcastClusterInvoker#doInvoke(org.apache.dubbo.rpc.Invocation, java.util.List, org.apache.dubbo.rpc.cluster.LoadBalance)
     * BroadcastClusterInvoker 会逐个调用每个服务提供者，如果其中一台报错，在循环调用结束后，BroadcastClusterInvoker 会抛出异常。
     * 该类通常用于通知所有提供者更新缓存或日志等本地资源信息。
     */

}
