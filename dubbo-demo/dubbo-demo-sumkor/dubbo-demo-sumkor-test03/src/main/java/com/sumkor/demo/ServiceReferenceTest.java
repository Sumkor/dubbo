package com.sumkor.demo;

import com.alibaba.spring.beans.factory.annotation.AbstractAnnotationBeanPostProcessor;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.beans.factory.annotation.AnnotatedInterfaceConfigBeanBuilder;
import org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
import org.apache.dubbo.registry.integration.RegistryDirectory;
import org.apache.dubbo.registry.integration.RegistryProtocol;
import org.apache.dubbo.registry.support.AbstractRegistry;
import org.apache.dubbo.registry.support.FailbackRegistry;
import org.apache.dubbo.registry.zookeeper.ZookeeperRegistry;
import org.apache.dubbo.rpc.Protocol$Adaptive;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.support.FailoverCluster;
import org.apache.dubbo.rpc.cluster.support.wrapper.AbstractCluster;
import org.apache.dubbo.rpc.protocol.AbstractProtocol;
import org.apache.dubbo.rpc.protocol.ProtocolFilterWrapper;
import org.apache.dubbo.rpc.protocol.ProtocolListenerWrapper;
import org.apache.dubbo.rpc.protocol.injvm.InjvmProtocol;
import org.apache.dubbo.rpc.proxy.InvokerInvocationHandler;
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
     * 2.1 创建代理类
     * @see ReferenceConfig#createProxy(java.util.Map)
     *
     * 入参 map 内容：
     * {"side":"consumer","application":"dubbo-demo-api-consumer","register.ip":"172.20.3.201","release":"","sticky":"false","dubbo":"2.0.2","pid":"4668","interface":"org.apache.dubbo.demo.DemoService","generic":"true","timestamp":"1609727434950"}
     *
     * 先看结果，得到的代理类，其中包含了 {@link InvokerInvocationHandler} 实例。
     * 如果在 @DubboReference 或 ReferenceConfig.setGeneric 配置了使用泛化，
     * 则得到
     * serviceInterfaceClass = org.apache.dubbo.rpc.service.GenericService
     * 否则
     * serviceInterfaceClass = org.apache.dubbo.demo.DemoService 具体的接口
     *
     *
     * A.非泛化
     *
     * A.1 本地服务引入
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
     * A.2 远程服务引入
     *
     * A.2.1 构造 url 进行 SPI
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
     * 通过 SPI ，进入
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
     * 根据 url，由 SPI 得到 Registry 实例为 {@link ZookeeperRegistry}
     * 根据 url，由于 CLUSTER_KEY 为空，使用 {@link Cluster#DEFAULT}，得到 Cluster 实例为 {@link FailoverCluster}
     * ！！！得到包装类
     * MockClusterWrapper(FailoverCluster)
     *
     * A.2.2 Protocol#refer
     *
     * 关注真正执行 Protocol#refer 的位置！
     * @see RegistryProtocol#doRefer(org.apache.dubbo.rpc.cluster.Cluster, org.apache.dubbo.registry.Registry, java.lang.Class, org.apache.dubbo.common.URL)
     *
     * 其中的 url 相关值：
     *    url = zookeeper://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-consumer&dubbo=2.0.2&pid=13116&refer=application%3Ddubbo-demo-api-consumer%26dubbo%3D2.0.2%26generic%3Dfalse%26interface%3Dorg.apache.dubbo.demo.DemoService%26methods%3DsayHello%2CsayHelloAsync%26pid%3D13116%26register.ip%3D172.20.3.201%26side%3Dconsumer%26sticky%3Dfalse%26timestamp%3D1609745306260&timestamp=1609745474785
     *    subscribeUrl = consumer://172.20.3.201/org.apache.dubbo.demo.DemoService?application=dubbo-demo-api-consumer&dubbo=2.0.2&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=14212&side=consumer&sticky=false&timestamp=1609752919230
     *    registeredConsumerUrl = consumer://172.20.3.201/org.apache.dubbo.demo.DemoService?application=dubbo-demo-api-consumer&category=consumers&check=false&dubbo=2.0.2&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=14212&side=consumer&sticky=false&timestamp=1609752919230
     *
     * 把 registeredConsumerUrl 注册到 zk 上，不知道是做啥
     * @see ZookeeperRegistry#doRegister(org.apache.dubbo.common.URL)
     *
     * 对 subscribeUrl 添加参数，并进行订阅
     *     subscribeUrl = consumer://172.20.3.201/org.apache.dubbo.demo.DemoService?application=dubbo-demo-api-consumer&category=providers,configurators,routers&dubbo=2.0.2&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=14212&side=consumer&sticky=false&timestamp=1609752919230
     * @see RegistryDirectory#subscribe(org.apache.dubbo.common.URL)
     * @see ZookeeperRegistry#doSubscribe(org.apache.dubbo.common.URL, org.apache.dubbo.registry.NotifyListener)
     * @see FailbackRegistry#notify(org.apache.dubbo.common.URL, org.apache.dubbo.registry.NotifyListener, java.util.List)
     * @see AbstractRegistry#notify(org.apache.dubbo.common.URL, org.apache.dubbo.registry.NotifyListener, java.util.List)
     *
     * 使用 cluster 和 directory 构造 invoker：
     *
     * Invoker<T> invoker = cluster.join(directory);
     *
     * 这里的 cluster 为 MockClusterWrapper(FailoverCluster)，而 directory 则是 RegistryDirectory
     *
     * @see AbstractCluster#join(org.apache.dubbo.rpc.cluster.Directory)
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     * B.泛化
     *
     * B.1 本地服务引入
     *
     * B.2 远程服务引入
     *
     *
     * C. 生成代理
     *
     *
     *
     */


}
