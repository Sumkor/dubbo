package com.sumkor.demo;

import com.alibaba.spring.beans.factory.annotation.AbstractAnnotationBeanPostProcessor;
import org.apache.dubbo.common.bytecode.Proxy;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.beans.factory.annotation.AnnotatedInterfaceConfigBeanBuilder;
import org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
import org.apache.dubbo.config.utils.ConfigValidationUtils;
import org.apache.dubbo.registry.integration.RegistryDirectory;
import org.apache.dubbo.registry.integration.RegistryProtocol;
import org.apache.dubbo.registry.support.AbstractRegistry;
import org.apache.dubbo.registry.support.FailbackRegistry;
import org.apache.dubbo.registry.zookeeper.ZookeeperRegistry;
import org.apache.dubbo.remoting.exchange.Exchangers;
import org.apache.dubbo.remoting.exchange.support.header.HeaderExchanger;
import org.apache.dubbo.remoting.transport.netty4.NettyClient;
import org.apache.dubbo.remoting.transport.netty4.NettyTransporter;
import org.apache.dubbo.rpc.Protocol$Adaptive;
import org.apache.dubbo.rpc.ProxyFactory$Adaptive;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.interceptor.ConsumerContextClusterInterceptor;
import org.apache.dubbo.rpc.cluster.support.FailoverCluster;
import org.apache.dubbo.rpc.cluster.support.FailoverClusterInvoker;
import org.apache.dubbo.rpc.cluster.support.wrapper.AbstractCluster;
import org.apache.dubbo.rpc.cluster.support.wrapper.MockClusterWrapper;
import org.apache.dubbo.rpc.protocol.AbstractProtocol;
import org.apache.dubbo.rpc.protocol.ProtocolFilterWrapper;
import org.apache.dubbo.rpc.protocol.ProtocolListenerWrapper;
import org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol;
import org.apache.dubbo.rpc.protocol.injvm.InjvmProtocol;
import org.apache.dubbo.rpc.proxy.AbstractProxyFactory;
import org.apache.dubbo.rpc.proxy.InvokerInvocationHandler;
import org.apache.dubbo.rpc.proxy.javassist.JavassistProxyFactory;
import org.apache.dubbo.rpc.proxy.wrapper.StubProxyFactoryWrapper;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;

/**
 * Dubbo 服务引用
 * https://dubbo.apache.org/zh/docs/v2.7/dev/source/refer-service/
 *
 * Dubbo 服务引用的时机有两个：
 * 第一个是在 Spring 容器调用 ReferenceBean 的 afterPropertiesSet 方法时引用服务，
 * 第二个是在 ReferenceBean 对应的服务被注入到其他类中时引用。
 * 这两个引用服务的时机区别在于，第一个是饿汉式的，第二个是懒汉式的。
 * 默认情况下，Dubbo 使用懒汉式引用服务。如果需要使用饿汉式，可通过配置 <dubbo:reference> 的 init 属性开启。
 *
 *
 * 服务引用的大致原理
 *
 * 按照惯例，在进行具体工作之前，需先进行配置检查与收集工作。
 * 根据收集到的信息决定服务用的方式，有三种：
 * 第一种是引用本地 (JVM) 服务；
 * 第二是通过直连方式引用远程服务；
 * 第三是通过注册中心引用远程服务。
 * 不管是哪种引用方式，最后都会得到一个 Invoker 实例。
 * 如果有多个注册中心，多个服务提供者，这个时候会得到一组 Invoker 实例，此时需要通过集群管理类 Cluster 将多个 Invoker 合并成一个实例。
 * Invoker 实例已经具备调用本地或远程服务的能力了，但并不能将此实例暴露给用户使用，这会对用户业务代码造成侵入。
 * 此时框架还需要通过代理工厂类 (ProxyFactory) 为服务接口生成代理类，并让代理类去调用 Invoker 逻辑。避免了 Dubbo 框架代码对业务代码的侵入，同时也让框架更容易使用。
 *
 * Invoker 是 Dubbo 的核心模型，代表一个可执行体。在服务提供方，Invoker 用于调用服务提供类。在服务消费方，Invoker 用于执行远程调用。
 *
 * @author Sumkor
 * @since 2020/12/23
 */
public class ServiceReferenceTest {

    /**
     * Spring 注解方式引用服务
     * dubbo-demo/dubbo-demo-annotation/dubbo-demo-annotation-provider/src/main/java/org/apache/dubbo/demo/provider/Application.java
     *
     * Spring 容器启动时，创建 bean (beanName = demoServiceComponent)
     * @see org.apache.dubbo.demo.consumer.comp.DemoServiceComponent
     *
     * 创建 bean 过程中，进行依赖注入，处理 @DubboReference 注解（以下均为 Spring 源码内容）：
     * @see AbstractAutowireCapableBeanFactory#populateBean(java.lang.String, org.springframework.beans.factory.support.RootBeanDefinition, org.springframework.beans.BeanWrapper)
     * @see AbstractAnnotationBeanPostProcessor#postProcessPropertyValues(org.springframework.beans.PropertyValues, java.beans.PropertyDescriptor[], java.lang.Object, java.lang.String)
     * @see AbstractAnnotationBeanPostProcessor#getInjectedObject(org.springframework.core.annotation.AnnotationAttributes, java.lang.Object, java.lang.String, java.lang.Class, org.springframework.beans.factory.annotation.InjectionMetadata.InjectedElement)
     *
     * 进入 Dubbo 与 Spring 框架整合的位置，这里创建 ReferenceBean，用于 @DubboReference 注解的属性注入。
     * @see ReferenceAnnotationBeanPostProcessor#doGetInjectedBean(org.springframework.core.annotation.AnnotationAttributes, java.lang.Object, java.lang.String, java.lang.Class, org.springframework.beans.factory.annotation.InjectionMetadata.InjectedElement)
     *
     * 一步一步调试进去
     * @see ReferenceAnnotationBeanPostProcessor#buildReferenceBeanIfAbsent(java.lang.String, org.springframework.core.annotation.AnnotationAttributes, java.lang.Class)
     * @see AnnotatedInterfaceConfigBeanBuilder#configureBean(org.apache.dubbo.config.AbstractInterfaceConfig)
     * @see org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceBeanBuilder#postConfigureBean(org.springframework.core.annotation.AnnotationAttributes, org.apache.dubbo.config.spring.ReferenceBean)
     * @see ReferenceBean#afterPropertiesSet()
     * @see ReferenceBean#getObject()
     *
     * 1. 服务引用入口
     * @see ReferenceConfig#get()
     *
     * 2. init 方法主要用于处理配置，以及调用 createProxy 生成代理类
     * @see ReferenceConfig#init()
     *
     * 实例化 ConsumerConfig 等，猜测各种 config 只是用于构造 url
     * @see ReferenceConfig#checkAndUpdateSubConfigs()
     *
     *
     * 3. 创建代理类！！！！！！！！
     * @see ReferenceConfig#createProxy(java.util.Map)
     *
     * 入参 map 内容：
     * {"side":"consumer","application":"dubbo-demo-api-consumer","register.ip":"172.20.3.201","release":"","sticky":"false","dubbo":"2.0.2","pid":"4668","interface":"org.apache.dubbo.demo.DemoService","generic":"true","timestamp":"1609727434950"}
     *
     * 先看结果，得到的代理类 Proxy0，其中包含了 {@link InvokerInvocationHandler} 实例。
     * 如果在 @DubboReference 或 ReferenceConfig.setGeneric 配置了使用泛化，
     * 则得到
     * serviceInterfaceClass = org.apache.dubbo.rpc.service.GenericService
     * 否则
     * serviceInterfaceClass = org.apache.dubbo.demo.DemoService 具体的接口
     *
     * 注意，进入 createProxy 的时候，urls 和 url 都为空！
     *
     */

    /**
     * ------------ 非泛化 ------------
     *
     *
     * 3.1 本地服务引入
     *
     * @see ReferenceConfig#createProxy(java.util.Map)
     *
     * invoker = REF_PROTOCOL.refer(interfaceClass, url);
     * 其中：
     *     url = injvm://127.0.0.1/org.apache.dubbo.demo.DemoService?application=dubbo-demo-api-consumer&dubbo=2.0.2&generic=false&injvm=true&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=12876&register.ip=172.20.3.201&release=&side=consumer&sticky=false&timestamp=1609743210808
     *     interfaceClass = interface org.apache.dubbo.demo.DemoService
     *
     * 通过 SPI ，进入
     * @see Protocol$Adaptive#refer(java.lang.Class, org.apache.dubbo.common.URL)
     * 执行
     * ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension("injvm");
     * ！！！得到包装类
     * ProtocolFilterWrapper(ProtocolListenerWrapper(InjvmProtocol))
     *
     * 执行 Protocol#refer
     * @see ProtocolFilterWrapper#refer(java.lang.Class, org.apache.dubbo.common.URL)
     * @see ProtocolListenerWrapper#refer(java.lang.Class, org.apache.dubbo.common.URL)
     * @see AbstractProtocol#refer(java.lang.Class, org.apache.dubbo.common.URL)
     * @see InjvmProtocol#protocolBindingRefer(java.lang.Class, org.apache.dubbo.common.URL)
     *
     * 得到层层包装的 InjvmInvoker，最后再与过滤器一同组装，并返回
     * @see ProtocolFilterWrapper#buildInvokerChain(org.apache.dubbo.rpc.Invoker, java.lang.String, java.lang.String)
     *
     *
     * 3.2 远程服务引入
     *
     * 3.2.1 构造 url 进行 SPI
     *
     * 通过 referenceConfig 构造 url
     * @see ConfigValidationUtils#loadRegistries(org.apache.dubbo.config.AbstractInterfaceConfig, boolean)
     *
     * 不考虑点对点的远程服务引入
     * 处理之前：
     *     url = registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-consumer&dubbo=2.0.2&pid=13116&registry=zookeeper&timestamp=1609745474785
     *     refer = application=dubbo-demo-api-consumer&dubbo=2.0.2&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=13116&register.ip=172.20.3.201&side=consumer&sticky=false&timestamp=1609745306260
     * 为 url 添加 monitor、refer 参数：
     *     url = registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-consumer&dubbo=2.0.2&pid=13116&refer=application%3Ddubbo-demo-api-consumer%26dubbo%3D2.0.2%26generic%3Dfalse%26interface%3Dorg.apache.dubbo.demo.DemoService%26methods%3DsayHello%2CsayHelloAsync%26pid%3D13116%26register.ip%3D172.20.3.201%26side%3Dconsumer%26sticky%3Dfalse%26timestamp%3D1609745306260&registry=zookeeper&timestamp=1609745474785
     *
     * 执行：
     * invoker = REF_PROTOCOL.refer(interfaceClass, urls.get(0));
     *
     * 通过 SPI，进入
     * @see Protocol$Adaptive#refer(java.lang.Class, org.apache.dubbo.common.URL)
     * 执行
     * ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension("registry");
     * ！！！得到包装类
     * ProtocolFilterWrapper(ProtocolListenerWrapper(RegistryProtocol))
     *
     * 执行 Protocol#refer
     * @see ProtocolFilterWrapper#refer(java.lang.Class, org.apache.dubbo.common.URL)
     * 满足 UrlUtils.isRegistry(invoker.getUrl())，因而执行下一个
     * @see ProtocolListenerWrapper#refer(java.lang.Class, org.apache.dubbo.common.URL)
     * 满足 UrlUtils.isRegistry(invoker.getUrl())，因而执行下一个
     * @see RegistryProtocol#refer(java.lang.Class, org.apache.dubbo.common.URL)
     *
     * 首先获取到 url 如下：
     *     url = zookeeper://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-consumer&dubbo=2.0.2&pid=13116&refer=application%3Ddubbo-demo-api-consumer%26dubbo%3D2.0.2%26generic%3Dfalse%26interface%3Dorg.apache.dubbo.demo.DemoService%26methods%3DsayHello%2CsayHelloAsync%26pid%3D13116%26register.ip%3D172.20.3.201%26side%3Dconsumer%26sticky%3Dfalse%26timestamp%3D1609745306260&timestamp=1609745474785
     *
     * Cluster cluster = Cluster.getCluster(qs.get(CLUSTER_KEY));
     *
     * 根据 url，由 SPI 得到 Registry 实例为 {@link ZookeeperRegistry}
     * 根据 url，由于 CLUSTER_KEY 为空，使用 {@link Cluster#DEFAULT}，得到 Cluster 实例为 {@link FailoverCluster}
     * ！！！得到包装类
     * MockClusterWrapper(FailoverCluster)
     *
     * 3.2.2 Protocol#doRefer
     *
     * 关注真正执行 Protocol#refer 的位置！
     * @see RegistryProtocol#doRefer(org.apache.dubbo.rpc.cluster.Cluster, org.apache.dubbo.registry.Registry, java.lang.Class, org.apache.dubbo.common.URL)
     *
     * 其中的 url 相关值：
     *    url = zookeeper://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-consumer&dubbo=2.0.2&pid=13116&refer=application%3Ddubbo-demo-api-consumer%26dubbo%3D2.0.2%26generic%3Dfalse%26interface%3Dorg.apache.dubbo.demo.DemoService%26methods%3DsayHello%2CsayHelloAsync%26pid%3D13116%26register.ip%3D172.20.3.201%26side%3Dconsumer%26sticky%3Dfalse%26timestamp%3D1609745306260&timestamp=1609745474785
     *    subscribeUrl = consumer://172.20.3.201/org.apache.dubbo.demo.DemoService?application=dubbo-demo-api-consumer&dubbo=2.0.2&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=14212&side=consumer&sticky=false&timestamp=1609752919230
     *    registeredConsumerUrl = consumer://172.20.3.201/org.apache.dubbo.demo.DemoService?application=dubbo-demo-api-consumer&category=consumers&check=false&dubbo=2.0.2&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=14212&side=consumer&sticky=false&timestamp=1609752919230
     *
     * A. 注册服务消费者，在 consumers 目录下新节点
     *
     * 把 registeredConsumerUrl 注册到 zk 上，不知道有什么用
     * @see ZookeeperRegistry#doRegister(org.apache.dubbo.common.URL)
     *
     * B. 订阅 providers、configurators、routers 等节点数据
     *
     * 对 subscribeUrl 添加参数，并进行订阅
     *     subscribeUrl = consumer://172.20.3.201/org.apache.dubbo.demo.DemoService?application=dubbo-demo-api-consumer&category=providers,configurators,routers&dubbo=2.0.2&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=14212&side=consumer&sticky=false&timestamp=1609752919230
     * @see RegistryDirectory#subscribe(org.apache.dubbo.common.URL)
     * @see ZookeeperRegistry#doSubscribe(org.apache.dubbo.common.URL, org.apache.dubbo.registry.NotifyListener)
     *
     * 其中，toCategoriesPath(subscribeUrl) 解析得到三个路径：
     *     /dubbo/org.apache.dubbo.demo.DemoService/providers
     *     /dubbo/org.apache.dubbo.demo.DemoService/configurators
     *     /dubbo/org.apache.dubbo.demo.DemoService/routers
     *
     * 分别在 zk 上创建路径、添加监听器，并触发监听器
     *
     * @see FailbackRegistry#notify(org.apache.dubbo.common.URL, org.apache.dubbo.registry.NotifyListener, java.util.List)
     * @see AbstractRegistry#notify(org.apache.dubbo.common.URL, org.apache.dubbo.registry.NotifyListener, java.util.List)
     *
     * 这里，入参（注意 application 参数的差别，猜测是从 zk 上各目录获取到的 url 值）
     *     url = subscribeUrl = consumer://172.20.3.201/org.apache.dubbo.demo.DemoService?application=dubbo-demo-api-consumer&category=providers,configurators,routers&dubbo=2.0.2&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=15508&side=consumer&sticky=false&timestamp=1610013025303
     *     urls:
     *         providers = dubbo://172.20.3.201:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=5844&release=&side=provider&timestamp=1610011492987
     *         configurators = empty://172.20.3.201/org.apache.dubbo.demo.DemoService?application=dubbo-demo-api-consumer&category=configurators&dubbo=2.0.2&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=15508&side=consumer&sticky=false&timestamp=1610013025303
     *         routers = empty://172.20.3.201/org.apache.dubbo.demo.DemoService?application=dubbo-demo-api-consumer&category=routers&dubbo=2.0.2&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=15508&side=consumer&sticky=false&timestamp=1610013025303
     *
     * 对于 urls 中的三个地址，依次进行 listener.notify 操作：
     * 关注 providers url
     * @see RegistryDirectory#notify(java.util.List)
     * @see RegistryDirectory#refreshOverrideAndInvoker(java.util.List)
     * @see RegistryDirectory#refreshInvoker(java.util.List)
     * @see RegistryDirectory#toInvokers(java.util.List)
     *
     * 其中，URL url = mergeUrl(providerUrl);
     * 执行之前
     *     providerUrl = dubbo://172.20.3.201:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=5844&release=&side=provider&timestamp=1610011492987
     * 执行之后得到
     *     url = dubbo://172.20.3.201:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-consumer&check=false&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=15508&register.ip=172.20.3.201&release=&remote.application=dubbo-demo-api-provider&side=consumer&sticky=false&timestamp=1610011492987
     *
     * 注意到这里从 side=provider 改成 side=consumer
     *
     * 重点来了！！！
     * invoker = new InvokerDelegate<>(protocol.refer(serviceType, url), url, providerUrl);
     * 这里 serviceType = org.apache.dubbo.demo.DemoService 类
     *
     * @see Protocol$Adaptive#refer(java.lang.Class, org.apache.dubbo.common.URL)
     *
     * 通过 SPI 得到 {@link DubboProtocol}，执行 DubboProtocol#refer
     *
     * @see ProtocolFilterWrapper#refer(java.lang.Class, org.apache.dubbo.common.URL)
     * @see ProtocolListenerWrapper#refer(java.lang.Class, org.apache.dubbo.common.URL)
     * @see AbstractProtocol#refer(java.lang.Class, org.apache.dubbo.common.URL)
     *
     * @see DubboProtocol#protocolBindingRefer(java.lang.Class, org.apache.dubbo.common.URL)
     *
     * 创建 DubboInvoker！
     * DubboInvoker<T> invoker = new DubboInvoker<T>(serviceType, url, getClients(url), invokers);
     *
     * 关于 getClients，一步步调试进去
     * @see DubboProtocol#getClients(org.apache.dubbo.common.URL)
     * @see DubboProtocol#getSharedClient(org.apache.dubbo.common.URL, int)
     * @see DubboProtocol#buildReferenceCountExchangeClientList(org.apache.dubbo.common.URL, int)
     * @see DubboProtocol#buildReferenceCountExchangeClient(org.apache.dubbo.common.URL)
     * @see Exchangers#connect(org.apache.dubbo.common.URL, org.apache.dubbo.remoting.exchange.ExchangeHandler)
     * @see HeaderExchanger#connect(org.apache.dubbo.common.URL, org.apache.dubbo.remoting.exchange.ExchangeHandler)
     * @see NettyTransporter#connect(org.apache.dubbo.common.URL, org.apache.dubbo.remoting.ChannelHandler)
     * @see NettyClient#doOpen()
     *
     *
     *
     * 记录本地文件：
     * C:\Users\huangzb\.dubbo\dubbo-registry-dubbo-demo-api-consumer-127.0.0.1-2181.cache
     *
     * #Dubbo Registry Cache
     * #Thu Jan 07 19:35:14 CST 2021
     * org.apache.dubbo.demo.DemoService=empty\://172.20.3.201/org.apache.dubbo.demo.DemoService?application\=dubbo-demo-api-consumer&category\=routers&dubbo\=2.0.2&generic\=false&interface\=org.apache.dubbo.demo.DemoService&methods\=sayHello,sayHelloAsync&pid\=15508&side\=consumer&sticky\=false&timestamp\=1610013025303 empty\://172.20.3.201/org.apache.dubbo.demo.DemoService?application\=dubbo-demo-api-consumer&category\=configurators&dubbo\=2.0.2&generic\=false&interface\=org.apache.dubbo.demo.DemoService&methods\=sayHello,sayHelloAsync&pid\=15508&side\=consumer&sticky\=false&timestamp\=1610013025303 dubbo\://172.20.3.201\:20880/org.apache.dubbo.demo.DemoService?anyhost\=true&application\=dubbo-demo-api-provider&deprecated\=false&dubbo\=2.0.2&dynamic\=true&generic\=false&interface\=org.apache.dubbo.demo.DemoService&methods\=sayHello,sayHelloAsync&pid\=5844&release\=&side\=provider&timestamp\=1610011492987
     *
     *
     * C. 一个注册中心可能有多个服务提供者，因此这里需要将多个服务提供者合并为一个
     *
     * 使用 cluster 和 directory 构造 invoker：
     * Invoker<T> invoker = cluster.join(directory);
     *
     * 这里的 cluster 为 MockClusterWrapper(FailoverCluster)，而 directory 则是 RegistryDirectory
     *
     * @see MockClusterWrapper#join(org.apache.dubbo.rpc.cluster.Directory)
     *
     * @see AbstractCluster#join(org.apache.dubbo.rpc.cluster.Directory)
     * @see FailoverCluster#doJoin(org.apache.dubbo.rpc.cluster.Directory) 得到 {@link FailoverClusterInvoker}
     * @see AbstractCluster#buildClusterInterceptors(org.apache.dubbo.rpc.cluster.support.AbstractClusterInvoker, java.lang.String) 得到拦截器 {@link ConsumerContextClusterInterceptor}
     *
     * 最后得到 invoker 为 MockClusterInvoker 对象实例，其中：
     * directory 属性 RegistryDirectory 实例，该对象包含远程服务接口信息，实例化过程见 {@link RegistryProtocol#doRefer}；
     * invoker 属性是 AbstractCluster.InterceptorInvokerNode 实例，该实例包含了 FailoverClusterInvoker 和 ConsumerContextClusterInterceptor 对象；
     *
     * Invoker 创建完毕后，接下来要做的事情是为服务接口生成代理对象。有了代理对象，即可进行远程调用。
     *
     *
     * 3.3 生成代理
     *
     * PROXY_FACTORY.getProxy(invoker, ProtocolUtils.isGeneric(generic));
     *
     * 3.3.1 通过 url 进行 SPI
     *
     * @see ProxyFactory$Adaptive#getProxy(org.apache.dubbo.rpc.Invoker, boolean)
     *
     * 这里
     *     url = invoker.getUrl();
     *     url = zookeeper://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?anyhost=true&application=dubbo-demo-api-consumer&check=false&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=9984&register.ip=172.20.3.201&release=&remote.application=dubbo-demo-api-provider&side=consumer&sticky=false&timestamp=1609816826065
     *     String string = url.getParameter("proxy", "javassist");// javassist
     *     ProxyFactory proxyFactory = (ProxyFactory) ExtensionLoader.getExtensionLoader(ProxyFactory.class).getExtension(string);
     * ！！！得到包装类
     * StubProxyFactoryWrapper(JavassistProxyFactory)
     *
     * 3.3.2 执行 proxyFactory#getProxy
     *
     * @see StubProxyFactoryWrapper#getProxy(org.apache.dubbo.rpc.Invoker, boolean)
     *
     * @see AbstractProxyFactory#getProxy(org.apache.dubbo.rpc.Invoker, boolean)
     * 这里是非泛化，一波操作之后，得到 interfaces 集合如下：
     * interfaces = [interface com.alibaba.dubbo.rpc.service.EchoService, interface org.apache.dubbo.rpc.service.Destroyable, interface org.apache.dubbo.demo.DemoService]
     *
     * 3.3.3 生成 proxy 类并实例化
     *
     * @see JavassistProxyFactory#getProxy(org.apache.dubbo.rpc.Invoker, java.lang.Class[])
     * 这里入参 invoker 为 MockClusterInvoker 对象实例，interfaces 为上一步得到的接口类集合
     *
     * @see Proxy#getProxy(java.lang.ClassLoader, java.lang.Class[])
     *
     * 将入参 interfaces 集合处理为一下字符串
     * String key = com.alibaba.dubbo.rpc.service.EchoService;org.apache.dubbo.rpc.service.Destroyable;org.apache.dubbo.demo.DemoService;
     *
     * 生成的代理类类名如下，代码见下方。
     * org.apache.dubbo.common.bytecode.Proxy0
     *
     * 将代理类实例化，并存入 cache 缓存：
     * proxy = Proxy0.class.newInstance()
     * cache.put(key, new WeakReference<Proxy>(proxy));
     *
     * 3.3.3 执行 proxy 类实例的 newInstance 方法
     *
     * @see JavassistProxyFactory#getProxy(org.apache.dubbo.rpc.Invoker, java.lang.Class[])
     * proxy.newInstance(new InvokerInvocationHandler(invoker));
     *
     * 可见代理类只是对 InvokerInvocationHandler 对象包了一层，而该 Handler 又对 Invoker 包了一层，最终还是调用到 Invoker。
     *
     * 3.3.4 得到 proxy 实例之后，如果是非泛化接口，进行下一步处理
     *
     * @see StubProxyFactoryWrapper#getProxy(org.apache.dubbo.rpc.Invoker, boolean)
     *
     * 由于 invoker.getUrl().getParameter(STUB_KEY, url.getParameter(LOCAL_KEY))，得到为空。
     * 即 url 中没有参数 stub 和 local，因此直接跳出了。
     *
     * 至此，生成代理类的代码结束。
     *
     */

    /**
     * ------------ 泛化 ------------
     *
     *
     * 3.1 本地服务引入
     *
     *
     * 3.2 远程服务引入
     *
     * @see ReferenceConfig#createProxy(java.util.Map)
     *
     *     url = registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-consumer&dubbo=2.0.2&pid=17336&registry=zookeeper&timestamp=1609915249805
     *     refer = application=dubbo-demo-api-consumer&dubbo=2.0.2&generic=true&interface=org.apache.dubbo.demo.DemoService&pid=17336&register.ip=172.20.3.201&side=consumer&sticky=false&timestamp=1609915206947
     * 拼接成：
     *     url = registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-consumer&dubbo=2.0.2&pid=17336&refer=application%3Ddubbo-demo-api-consumer%26dubbo%3D2.0.2%26generic%3Dtrue%26interface%3Dorg.apache.dubbo.demo.DemoService%26pid%3D17336%26register.ip%3D172.20.3.201%26side%3Dconsumer%26sticky%3Dfalse%26timestamp%3D1609915206947&registry=zookeeper&timestamp=1609915249805
     *
     * 进行 SPI：
     * @see RegistryProtocol#refer(java.lang.Class, org.apache.dubbo.common.URL)
     * @see RegistryProtocol#doRefer(org.apache.dubbo.rpc.cluster.Cluster, org.apache.dubbo.registry.Registry, java.lang.Class, org.apache.dubbo.common.URL)
     *
     *     type = org.apache.dubbo.rpc.service.GenericService
     *     url = zookeeper://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-consumer&dubbo=2.0.2&pid=17336&refer=application%3Ddubbo-demo-api-consumer%26dubbo%3D2.0.2%26generic%3Dtrue%26interface%3Dorg.apache.dubbo.demo.DemoService%26pid%3D17336%26register.ip%3D172.20.3.201%26side%3Dconsumer%26sticky%3Dfalse%26timestamp%3D1609915206947&timestamp=1609915249805
     *     registeredConsumerUrl = consumer://172.20.3.201/org.apache.dubbo.rpc.service.GenericService?application=dubbo-demo-api-consumer&category=consumers&check=false&dubbo=2.0.2&generic=true&interface=org.apache.dubbo.demo.DemoService&pid=17336&side=consumer&sticky=false&timestamp=1609915206947
     *     subscribeUrl = consumer://172.20.3.201/org.apache.dubbo.rpc.service.GenericService?application=dubbo-demo-api-consumer&category=providers,configurators,routers&dubbo=2.0.2&generic=true&interface=org.apache.dubbo.demo.DemoService&pid=17336&side=consumer&sticky=false&timestamp=1609915206947
     *
     * 3.3 生成代理
     *
     * @see ReferenceConfig#createProxy(java.util.Map)
     * @see AbstractProxyFactory#getProxy(org.apache.dubbo.rpc.Invoker, boolean)
     * @see JavassistProxyFactory#getProxy(org.apache.dubbo.rpc.Invoker, java.lang.Class[])
     *
     * interfaces = [interface org.apache.dubbo.demo.DemoService, interface org.apache.dubbo.rpc.service.GenericService, interface com.alibaba.dubbo.rpc.service.EchoService, interface org.apache.dubbo.rpc.service.Destroyable]
     *
     * 生成的代理类类名如下，代码见下方。
     * org.apache.dubbo.common.bytecode.Proxy0
     */

    /**
     * @see Proxy#getProxy(java.lang.ClassLoader, java.lang.Class[])
     *
     *
     * 非泛化 动态生成 org.apache.dubbo.common.bytecode.Proxy0 的代码如下：
     *
    package org.apache.dubbo.common.bytecode;

    import com.alibaba.dubbo.rpc.service.EchoService;
    import java.lang.reflect.InvocationHandler;
    import java.lang.reflect.Method;
    import java.util.concurrent.CompletableFuture;
    import org.apache.dubbo.common.bytecode.ClassGenerator;
    import org.apache.dubbo.demo.DemoService;
    import org.apache.dubbo.rpc.service.Destroyable;

    public class proxy0
    implements ClassGenerator.DC,
    Destroyable,
    EchoService,
    DemoService {
    public static Method[] methods;
    private InvocationHandler handler;

    public proxy0(InvocationHandler invocationHandler) {
    this.handler = invocationHandler;
    }

    public proxy0() {
    }

    public String sayHello(String string) {
    Object[] arrobject = new Object[]{string};
    Object object = this.handler.invoke(this, methods[0], arrobject);
    return (String)object;
    }

    public CompletableFuture sayHelloAsync(String string) {
    Object[] arrobject = new Object[]{string};
    Object object = this.handler.invoke(this, methods[1], arrobject);
    return (CompletableFuture)object;
    }

    public Object $echo(Object object) {
    Object[] arrobject = new Object[]{object};
    Object object2 = this.handler.invoke(this, methods[2], arrobject);
    return object2;
    }

    public void $destroy() {
    Object[] arrobject = new Object[]{};
    Object object = this.handler.invoke(this, methods[3], arrobject);
    }
    }

     *
     *
     * 泛化 动态生成 org.apache.dubbo.common.bytecode.Proxy0 的代码如下：
     *
     *
    package org.apache.dubbo.common.bytecode;

    import com.alibaba.dubbo.rpc.service.EchoService;
    import java.lang.reflect.InvocationHandler;
    import java.lang.reflect.Method;
    import java.util.concurrent.CompletableFuture;
    import org.apache.dubbo.common.bytecode.ClassGenerator;
    import org.apache.dubbo.demo.DemoService;
    import org.apache.dubbo.rpc.service.Destroyable;
    import org.apache.dubbo.rpc.service.GenericException;
    import org.apache.dubbo.rpc.service.GenericService;

    public class proxy0
    implements ClassGenerator.DC,
    GenericService,
    Destroyable,
    EchoService,
    DemoService {
    public static Method[] methods;
    private InvocationHandler handler;

    public String sayHello(String string) {
    Object[] arrobject = new Object[]{string};
    Object object = this.handler.invoke(this, methods[0], arrobject);
    return (String)object;
    }

    public CompletableFuture sayHelloAsync(String string) {
    Object[] arrobject = new Object[]{string};
    Object object = this.handler.invoke(this, methods[1], arrobject);
    return (CompletableFuture)object;
    }

    public Object $invoke(String string, String[] arrstring, Object[] arrobject) throws GenericException {
    Object[] arrobject2 = new Object[]{string, arrstring, arrobject};
    Object object = this.handler.invoke(this, methods[2], arrobject2);
    return object;
    }

    public CompletableFuture $invokeAsync(String string, String[] arrstring, Object[] arrobject) throws GenericException
    {
    Object[] arrobject2 = new Object[]{string, arrstring, arrobject};
    Object object = this.handler.invoke(this, methods[3], arrobject2);
    return (CompletableFuture)object;
    }

    public Object $echo(Object object) {
    Object[] arrobject = new Object[]{object};
    Object object2 = this.handler.invoke(this, methods[4], arrobject);
    return object2;
    }

    public void $destroy() {
    Object[] arrobject = new Object[]{};
    Object object = this.handler.invoke(this, methods[5], arrobject);
    }

    public proxy0() {
    }

    public proxy0(InvocationHandler invocationHandler) {
    this.handler = invocationHandler;
    }
    }
     *
     */
}
