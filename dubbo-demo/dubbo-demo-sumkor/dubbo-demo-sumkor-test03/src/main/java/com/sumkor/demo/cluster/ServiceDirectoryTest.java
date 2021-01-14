package com.sumkor.demo.cluster;

import org.apache.dubbo.common.Node;
import org.apache.dubbo.registry.integration.RegistryDirectory;
import org.apache.dubbo.registry.integration.RegistryProtocol;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.directory.AbstractDirectory;
import org.apache.dubbo.rpc.cluster.directory.StaticDirectory;

/**
 * 服务目录
 * https://dubbo.apache.org/zh/docs/v2.7/dev/source/directory/
 *
 * 服务目录中存储了一些和服务提供者有关的信息，通过服务目录，服务消费者可获取到服务提供者的信息，比如 ip、端口、服务协议等。
 * 通过这些信息，服务消费者就可通过 Netty 等客户端进行远程调用。
 * 在一个服务集群中，服务提供者数量并不是一成不变的，如果集群中新增了一台机器，相应地在服务目录中就要新增一条服务提供者记录。
 * 或者，如果服务提供者的配置修改了，服务目录中的记录也要做相应的更新。
 *
 * 如果这样说，服务目录和注册中心的功能不就雷同了吗？确实如此，这里这么说是为了方便大家理解。
 * 实际上服务目录在获取注册中心的服务配置信息后，会为每条配置信息生成一个 Invoker 对象，并把这个 Invoker 对象存储起来，这个 Invoker 才是服务目录最终持有的对象。
 * Invoker 有什么用呢？看名字就知道了，这是一个具有远程调用功能的对象。
 * 讲到这大家应该知道了什么是服务目录了，它可以看做是 Invoker 集合，且这个集合中的元素会随注册中心的变化而进行动态调整。
 *
 * @author Sumkor
 * @since 2021/1/13
 */
public class ServiceDirectoryTest {

    /**
     * @see AbstractDirectory
     *
     * AbstractDirectory 实现了 {@link Directory} 接口，这个接口包含了一个重要的方法定义，即 list(Invocation)，用于列举 Invoker。
     * 服务目录目前内置的实现有两个，分别为 StaticDirectory 和 RegistryDirectory，它们均是 AbstractDirectory 的子类。
     *
     * Directory 继承自 {@link Node} 接口，Node 这个接口继承者比较多，像 Registry、Monitor、Invoker 等均继承了这个接口。
     * 这个接口包含了一个获取配置信息的方法 getUrl，实现该接口的类可以向外提供配置信息。
     *
     * AbstractDirectory 封装了 Invoker 列举流程，具体的列举逻辑则由子类实现，这是典型的模板模式。
     * @see AbstractDirectory#list(org.apache.dubbo.rpc.Invocation)
     */

    /**
     * 静态服务目录
     * @see StaticDirectory
     *
     * 内部存放的 Invoker 是不会变动的。所以，理论上它和不可变 List 的功能很相似。
     */

    /**
     * 动态服务目录
     * @see RegistryDirectory
     *
     * 实现了 NotifyListener 接口。当注册中心服务配置发生变化后，RegistryDirectory 可收到与当前服务相关的变化。
     * 收到变更通知后，RegistryDirectory 可根据配置变更信息刷新 Invoker 列表。
     *
     * RegistryDirectory 中有几个比较重要的逻辑，
     * 第一是 Invoker 的列举逻辑，
     * 第二是接收服务配置变更的逻辑，
     * 第三是 Invoker 列表的刷新逻辑。
     *
     * 1. 列举 Invoker
     * @see RegistryDirectory#doList(org.apache.dubbo.rpc.Invocation)
     *
     * 已知 Invoker 是根据 url 转换而来的，生成之后，存储在 RegistryDirectory 之中，代码见 {@link RegistryDirectory#refreshInvoker(java.util.List)}
     *
     *
     *
     * 2. 接收服务变更通知
     * @see RegistryDirectory#notify(java.util.List)
     *
     * 已知在服务消费者的初始化期间，{@link RegistryProtocol#doRefer} 执行 directory.subscribe(toSubscribeUrl(subscribeUrl)) 监听 zk 地址，并触发服务变更通知。
     *
     * 根据 invokerUrls，生成、刷新 invoker 实例，关注以下两个方法。
     * 入参 urls 是从 zk 目录 /dubbo/org.apache.dubbo.demo.DemoService/providers 下读取到的多个服务提供方地址
     *
     * @see RegistryDirectory#refreshInvoker(java.util.List)
     * @see RegistryDirectory#toInvokers(java.util.List)
     *
     *
     *
     */
}
