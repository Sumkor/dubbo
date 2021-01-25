package com.sumkor.demo.core;

import com.alibaba.spring.beans.factory.annotation.ConfigurationBeanBindingRegistrar;
import com.alibaba.spring.beans.factory.annotation.ConfigurationBeanBindingsRegister;
import com.alibaba.spring.beans.factory.annotation.EnableConfigurationBeanBindings;
import com.sumkor.demo.DemoServiceImpl;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.bytecode.Wrapper;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.context.ConfigManager;
import org.apache.dubbo.config.spring.ServiceBean;
import org.apache.dubbo.config.spring.beans.factory.annotation.ServiceClassPostProcessor;
import org.apache.dubbo.config.spring.context.DubboBootstrapApplicationListener;
import org.apache.dubbo.demo.DemoService;
import org.apache.dubbo.registry.ListenerRegistryWrapper;
import org.apache.dubbo.registry.integration.RegistryProtocol;
import org.apache.dubbo.registry.support.AbstractRegistryFactory;
import org.apache.dubbo.registry.support.FailbackRegistry;
import org.apache.dubbo.registry.zookeeper.ZookeeperRegistry;
import org.apache.dubbo.registry.zookeeper.ZookeeperRegistryFactory;
import org.apache.dubbo.remoting.Transporters;
import org.apache.dubbo.remoting.exchange.Exchangers;
import org.apache.dubbo.remoting.exchange.support.header.HeaderExchanger;
import org.apache.dubbo.remoting.transport.netty4.NettyTransporter;
import org.apache.dubbo.rpc.*;
import org.apache.dubbo.rpc.listener.ListenerExporterWrapper;
import org.apache.dubbo.rpc.protocol.ProtocolFilterWrapper;
import org.apache.dubbo.rpc.protocol.ProtocolListenerWrapper;
import org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol;
import org.apache.dubbo.rpc.protocol.injvm.InjvmProtocol;
import org.apache.dubbo.rpc.proxy.javassist.JavassistProxyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * Dubbo 服务发布
 * https://dubbo.apache.org/zh/docs/v2.7/dev/source/export-service/
 *
 * Dubbo 服务导出过程始于 Spring 容器发布刷新事件，Dubbo 在接收到事件后，会立即执行服务导出逻辑。
 * 整个逻辑大致可分为三个部分，
 * 第一部分是前置工作，主要用于检查参数，组装 URL。
 * 第二部分是导出服务，包含导出服务到本地 (JVM)，和导出服务到远程两个过程。
 * 第三部分是向注册中心注册服务，用于服务发现。
 *
 *
 * 由于不少关键类是由 dubbo SPI、JavassistProxyFactory 动态生成的，调试过程需要掌握查看动态生成的字节码的技能！
 *
 * 使用 arthas 获取动态生成的字节码
 * java -jar arthas-boot.jar
 * sc *Wrapper*
 * jad org.apache.dubbo.common.bytecode.Wrapper0
 *
 * @author Sumkor
 * @since 2020/12/14
 */
public class ServiceExportTest {

    /**
     * API 方式发布服务
     * dubbo-demo/dubbo-demo-api/dubbo-demo-api-provider/src/main/java/org/apache/dubbo/demo/provider/Application.java
     *
     * Spring 注解方式发布服务
     * dubbo-demo/dubbo-demo-annotation/dubbo-demo-annotation-provider/src/main/java/org/apache/dubbo/demo/provider/Application.java
     *
     * Spring 容器启动
     * @see AbstractApplicationContext#refresh()
     * @see AbstractApplicationContext#invokeBeanFactoryPostProcessors(org.springframework.beans.factory.config.ConfigurableListableBeanFactory)
     *
     * 1. 扫描 @DubboService 注解并注册为 BeanDefinition
     * @see ServiceClassPostProcessor#postProcessBeanDefinitionRegistry(org.springframework.beans.factory.support.BeanDefinitionRegistry)
     * @see ServiceClassPostProcessor#registerServiceBeans(java.util.Set, org.springframework.beans.factory.support.BeanDefinitionRegistry)
     * @see ServiceClassPostProcessor#findServiceBeanDefinitionHolders(org.springframework.context.annotation.ClassPathBeanDefinitionScanner, java.lang.String, org.springframework.beans.factory.support.BeanDefinitionRegistry, org.springframework.beans.factory.support.BeanNameGenerator)
     *
     * 将扫描到的 @DubboService 标注的类，beanName = ServiceBean:org.apache.dubbo.demo.DemoService，beanClass = {@link ServiceBean}
     * @see ServiceClassPostProcessor#registerServiceBean(org.springframework.beans.factory.config.BeanDefinitionHolder, org.springframework.beans.factory.support.BeanDefinitionRegistry, org.apache.dubbo.config.spring.context.annotation.DubboClassPathBeanDefinitionScanner)
     *
     *
     * 2. 解析配置文件 dubbo-provider.properties
     * @see ConfigurationClassPostProcessor#postProcessBeanDefinitionRegistry(org.springframework.beans.factory.support.BeanDefinitionRegistry)
     * @see ConfigurationClassPostProcessor#processConfigBeanDefinitions(org.springframework.beans.factory.support.BeanDefinitionRegistry)
     *
     * 解析配置文件
     * beanName = dubboConfigConfiguration.Single、dubboConfigConfiguration.Multiple
     * @see org.springframework.context.annotation.ConfigurationClassParser#parse(java.util.Set)
     *
     * 注册 BeanDefinion
     * @see org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader#loadBeanDefinitions(java.util.Set)
     * @see org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader#loadBeanDefinitionsFromRegistrars(java.util.Map)
     * @see ConfigurationBeanBindingsRegister#registerBeanDefinitions(org.springframework.core.type.AnnotationMetadata, org.springframework.beans.factory.support.BeanDefinitionRegistry)
     *
     * 可知 {@link ConfigurationBeanBindingsRegister} 用于处理注解 {@link EnableConfigurationBeanBindings}，从注解上读取到的值是从配置文件解析而来的，依次将解析到的 BeanDefinion 进行注册
     * @see ConfigurationBeanBindingRegistrar#registerConfigurationBeanDefinitions(java.util.Map, org.springframework.beans.factory.support.BeanDefinitionRegistry)
     *
     *
     * 3. 实例化 bean，其中 beanName = registryConfig、applicationConfig、protocolConfig、ServiceBean:org.apache.dubbo.demo.DemoService 等
     * @see DefaultSingletonBeanRegistry#getSingleton(java.lang.String, org.springframework.beans.factory.ObjectFactory)
     *
     * 需要执行父类的 @PostConstruct 方法
     * @see AbstractConfig#addIntoConfigManager()
     * @see ConfigManager#addConfig(org.apache.dubbo.config.AbstractConfig, boolean)
     * 执行之后，将 beanName 的实例存储在 configsCache 之中
     *
     *
     * 4. 触发监听器
     * @see AbstractApplicationContext#finishRefresh()
     * @see AbstractApplicationContext#publishEvent(org.springframework.context.ApplicationEvent)
     * @see SimpleApplicationEventMulticaster#invokeListener(org.springframework.context.ApplicationListener, org.springframework.context.ApplicationEvent)
     *
     * 启用 DubboBootstrap
     * @see DubboBootstrapApplicationListener#onApplicationContextEvent(org.springframework.context.event.ApplicationContextEvent)
     * @see DubboBootstrapApplicationListener#onContextRefreshedEvent(org.springframework.context.event.ContextRefreshedEvent)
     *
     */

    /**
     * 1. 服务发布入口
     * @see DubboBootstrap#start()
     *
     * 是否配置延迟发布 && 是否已发布 && 是不是已被取消发布，否者发布服务
     * @see org.apache.dubbo.config.ServiceConfig#export()
     *
     * 实例化 ProviderConfig、ProtocolConfig、RegistryConfig、interfaceClass 等，猜测各种 config 只是用于构造 url
     * @see ServiceConfig#checkAndUpdateSubConfigs()
     *
     * 观察 ServiceConfig 实例的变化，注意它重写 toString 方法
     * @see AbstractConfig#toString()
     *
     * 继续看服务发布
     * @see org.apache.dubbo.config.ServiceConfig#doExport()
     *
     * 遍历协议 ProtocolConfig，每个协议都需要注册到注册中心，协议形如：<dubbo:protocol name="dubbo" />
     * @see ServiceConfig#doExportUrls()
     *
     * 2. 构造参数 map，再利用 map 构造 url
     * @see ServiceConfig#doExportUrlsFor1Protocol(org.apache.dubbo.config.ProtocolConfig, java.util.List)
     *     url = dubbo://172.168.1.1:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=172.168.1.1&bind.port=20880&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=3500&release=&side=provider&timestamp=1608473481377
     *
     *
     *
     * 3. 本地发布
     * @see ServiceConfig#doExportUrlsFor1Protocol(org.apache.dubbo.config.ProtocolConfig, java.util.List)
     * @see ServiceConfig#exportLocal(org.apache.dubbo.common.URL)
     *
     * 3.1 修改 url 中的协议为 injvm
     *     local = injvm://127.0.0.1/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=172.168.1.1&bind.port=20880&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=3500&release=&side=provider&timestamp=1608473481377
     *
     * 3.2 PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, local)
     *
     * 这里的 PROXY_FACTORY 为 ProxyFactory$Adaptive 实例（代码见下方）
     * 得到 {@link ProxyFactory} 默认的实现类 {@link JavassistProxyFactory}
     *
     * 执行 ProxyFactory$Adaptive#getInvoker，实际执行
     * @see ProxyFactory$Adaptive#getInvoker(java.lang.Object, java.lang.Class, org.apache.dubbo.common.URL)
     * @see JavassistProxyFactory#getInvoker(java.lang.Object, java.lang.Class, org.apache.dubbo.common.URL)
     *
     * 该操作的结果是
     * 将服务实现类包装成 Wrapper 实例（动态生成，代码见下方），
     * 再使用 AbstractProxyInvoker 代理 wrapper.invokeMethod 方法，返回 AbstractProxyInvoker 实例。
     *
     * 3.3 ROTOCOL.export(PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, local))
     *
     * 即 ROTOCOL.export(invoker)
     *
     * SPI 文件为 dubbo-rpc/dubbo-rpc-api/src/main/resources/META-INF/dubbo/internal/org.apache.dubbo.rpc.Protocol
     * 这里的 ROTOCOL 为 Protocol$Adaptive 实例（代码见下方）
     *
     * 执行 Protocol$Adaptive#export 方法，由于 url.getProtocol() 为 injvm，即实际执行
     * @see Protocol$Adaptive#export(org.apache.dubbo.rpc.Invoker)
     * Protocol protocol = ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension("injvm")
     * protocol.export(invoker)
     *
     * 这里得到 {@link Protocol} 具体的实现类 {@link InjvmProtocol}，再得到包装类 {@link ProtocolListenerWrapper}，再得到包装类 {@link ProtocolFilterWrapper}，即
     * ！！！得到包装类 ProtocolFilterWrapper(ProtocolListenerWrapper(InjvmProtocol))
     *
     * 执行 Protocol#export
     * @see ProtocolFilterWrapper#export(org.apache.dubbo.rpc.Invoker)
     * @see ProtocolFilterWrapper#buildInvokerChain(org.apache.dubbo.rpc.Invoker, java.lang.String, java.lang.String)
     * @see ProtocolListenerWrapper#export(org.apache.dubbo.rpc.Invoker)
     * @see InjvmProtocol#export(org.apache.dubbo.rpc.Invoker)
     *
     * 最后得到 {@link org.apache.dubbo.rpc.protocol.injvm.InjvmExporter} 并包装在 {@link ListenerExporterWrapper} 之中返回，本地发布结束。
     *
     *
     *
     * 4. 远程发布
     *
     * @see ServiceConfig#doExportUrlsFor1Protocol(org.apache.dubbo.config.ProtocolConfig, java.util.List)
     *
     * 遍历 registryURL，需要把服务远程发布并注册到 registryURL
     *
     * 4.1 PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString()));
     *
     * 执行之前：
     *     registryURL = registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-provider&dubbo=2.0.2&pid=20656&registry=zookeeper&timestamp=1608623898765
     *     url = dubbo://172.20.3.201:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=172.20.3.201&bind.port=20880&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=20656&release=&side=provider&timestamp=1608623898776
     * 给 registryURL 添加参数得到：
     *     registryURL = registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-provider&dubbo=2.0.2&export=dubbo%3A%2F%2F172.20.3.201%3A20880%2Forg.apache.dubbo.demo.DemoService%3Fanyhost%3Dtrue%26application%3Ddubbo-demo-api-provider%26bind.ip%3D172.20.3.201%26bind.port%3D20880%26default%3Dtrue%26deprecated%3Dfalse%26dubbo%3D2.0.2%26dynamic%3Dtrue%26generic%3Dfalse%26interface%3Dorg.apache.dubbo.demo.DemoService%26methods%3DsayHello%2CsayHelloAsync%26pid%3D20656%26release%3D%26side%3Dprovider%26timestamp%3D1608623898776&pid=20656&registry=zookeeper&timestamp=1608623898765
     *
     * 这里的 PROXY_FACTORY 为 ProxyFactory$Adaptive 实例（代码见下方）
     * 执行 ProxyFactory$Adaptive#getInvoker，实际执行
     * @see JavassistProxyFactory#getInvoker(java.lang.Object, java.lang.Class, org.apache.dubbo.common.URL)
     *
     * 这里的返参 Invoker 同样是对 Wrapper 实例的方法进行代理，并且本地发布和远程发布的 Wrapper 实例是相同的！
     * 其中的 Wrapper 包装了服务的实现类，跟入参 url 的值无关。
     *
     * 4.2 PROTOCOL.export(wrapperInvoker)
     *
     * 这里的 ROTOCOL 为 Protocol$Adaptive 实例（代码见下方）
     * 执行 Protocol$Adaptive#export 方法，由于 invoker.getUrl().getProtocol() 为 registry，即实际执行
     * ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension("registry")
     * ！！！得到包装类 ProtocolFilterWrapper(ProtocolListenerWrapper(RegistryProtocol))
     *
     * 执行 Protocol#export
     * @see ProtocolFilterWrapper#export(org.apache.dubbo.rpc.Invoker)
     * 满足 UrlUtils.isRegistry(invoker.getUrl())，因而执行下一个
     * @see ProtocolListenerWrapper#export(org.apache.dubbo.rpc.Invoker)
     * 满足 UrlUtils.isRegistry(invoker.getUrl())，因而执行下一个
     *
     * 4.3 远程发布关键代码
     *
     * @see RegistryProtocol#export(org.apache.dubbo.rpc.Invoker)
     *
     * 发布之前，首先获取各个 url 如下：
     *     registryUrl = zookeeper://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-provider&dubbo=2.0.2&export=dubbo%3A%2F%2F172.168.1.1%3A20880%2Forg.apache.dubbo.demo.DemoService%3Fanyhost%3Dtrue%26application%3Ddubbo-demo-api-provider%26bind.ip%3D172.168.1.1%26bind.port%3D20880%26default%3Dtrue%26deprecated%3Dfalse%26dubbo%3D2.0.2%26dynamic%3Dtrue%26generic%3Dfalse%26interface%3Dorg.apache.dubbo.demo.DemoService%26methods%3DsayHello%2CsayHelloAsync%26pid%3D4856%26release%3D%26side%3Dprovider%26timestamp%3D1608481215162&pid=4856&timestamp=1608481214730
     *     providerUrl = dubbo://172.168.1.1:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=172.168.1.1&bind.port=20880&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=4856&release=&side=provider&timestamp=1608481215162
     *     overrideSubscribeUrl = provider://172.168.1.1:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=172.168.1.1&bind.port=20880&category=configurators&check=false&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=4856&release=&side=provider&timestamp=1608481215162
     *
     * 4.3.1 建立并发布 dubbo 协议的服务
     * @see RegistryProtocol#doLocalExport(org.apache.dubbo.rpc.Invoker, org.apache.dubbo.common.URL)
     *
     * 使用 providerUrl 来构造 invoker，执行 Protocol$Adaptive#export 方法，实际是执行
     * ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension("dubbo");
     * ！！！得到包装类 ProtocolFilterWrapper(ProtocolListenerWrapper(DubboProtocol))
     *
     * 执行 Protocol#export
     * @see ProtocolFilterWrapper#export(org.apache.dubbo.rpc.Invoker)
     * @see ProtocolFilterWrapper#buildInvokerChain(org.apache.dubbo.rpc.Invoker, java.lang.String, java.lang.String)
     * @see ProtocolListenerWrapper#export(org.apache.dubbo.rpc.Invoker)
     * @see DubboProtocol#export(org.apache.dubbo.rpc.Invoker)
     *
     * 其中
     *     url = dubbo://172.20.3.201:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=172.20.3.201&bind.port=20880&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=17832&release=&side=provider&timestamp=1608626107025
     *     key = org.apache.dubbo.demo.DemoService:20880
     * 把 invoker 包装成 DubboExporter
     *
     * 打开服务端端口，建立服务端 Server，返回 DubboProtocolServer
     * @see DubboProtocol#openServer(org.apache.dubbo.common.URL)
     *
     * 疑问，如果有多个 dubbo接口 服务发布时，对多个 url 进行遍历的情况下，如何避免多次建立 netty 服务？
     * 在同一台机器上（单网卡），同一个端口上仅允许启动一个服务器实例。若某个端口上已有服务器实例，此时则调用 reset 方法重置服务器的一些配置。
     *
     * @see DubboProtocol#createServer(org.apache.dubbo.common.URL)
     *
     * 默认建立 Netty 服务，一步一步调试进去，扒开一层层 SPI，调用链如下：
     *
     * @see Exchangers#bind(org.apache.dubbo.common.URL, org.apache.dubbo.remoting.exchange.ExchangeHandler)
     * @see HeaderExchanger#bind(org.apache.dubbo.common.URL, org.apache.dubbo.remoting.exchange.ExchangeHandler)
     * @see Transporters#bind(org.apache.dubbo.common.URL, org.apache.dubbo.remoting.ChannelHandler...)
     * @see NettyTransporter#bind(org.apache.dubbo.common.URL, org.apache.dubbo.remoting.ChannelHandler)
     *
     *
     *
     * 4.3.2 向注册中心注册服务
     *
     * 根据 URL 加载 Registry 实现类，这里由 SPI 得到 ZookeeperRegistry
     * @see RegistryProtocol#getRegistry(org.apache.dubbo.rpc.Invoker)
     * @see AbstractRegistryFactory#getRegistry(org.apache.dubbo.common.URL)
     * @see ZookeeperRegistryFactory#createRegistry(org.apache.dubbo.common.URL)
     *
     * 向 zookeeper 注册服务
     * @see RegistryProtocol#register(org.apache.dubbo.common.URL, org.apache.dubbo.common.URL)
     * 这里
     *     registryUrl = zookeeper://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-provider&dubbo=2.0.2&export=dubbo%3A%2F%2F172.20.3.201%3A20880%2Forg.apache.dubbo.demo.DemoService%3Fanyhost%3Dtrue%26application%3Ddubbo-demo-api-provider%26bind.ip%3D172.20.3.201%26bind.port%3D20880%26default%3Dtrue%26deprecated%3Dfalse%26dubbo%3D2.0.2%26dynamic%3Dtrue%26generic%3Dfalse%26interface%3Dorg.apache.dubbo.demo.DemoService%26methods%3DsayHello%2CsayHelloAsync%26pid%3D25404%26release%3D%26side%3Dprovider%26timestamp%3D1608630211892&pid=25404&timestamp=1608630211885
     *     registeredProviderUrl = dubbo://172.20.3.201:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=25404&release=&side=provider&timestamp=1608630211892
     *
     * 一步一步调试进去
     *
     * @see ListenerRegistryWrapper#register(org.apache.dubbo.common.URL)
     * @see FailbackRegistry#register(org.apache.dubbo.common.URL)
     * @see ZookeeperRegistry#doRegister(org.apache.dubbo.common.URL)
     *
     * 在 zookeeper 上创建临时节点：
     * /dubbo/org.apache.dubbo.demo.DemoService/providers/dubbo%3A%2F%2F172.20.3.201%3A20880%2Forg.apache.dubbo.demo.DemoService%3Fanyhost%3Dtrue%26application%3Ddubbo-demo-api-provider%26default%3Dtrue%26deprecated%3Dfalse%26dubbo%3D2.0.2%26dynamic%3Dtrue%26generic%3Dfalse%26interface%3Dorg.apache.dubbo.demo.DemoService%26methods%3DsayHello%2CsayHelloAsync%26pid%3D25404%26release%3D%26side%3Dprovider%26timestamp%3D1608630211892
     */

    /**
     * 服务发布，根据 injvm/dubbo 协议获取 protocol 实现类
     */
    @Test
    public void getProtocol() {
        Protocol injvm = ExtensionLoader.getExtensionLoader(Protocol.class).getExtension("injvm");
        System.out.println(injvm instanceof ProtocolFilterWrapper);
        /**
         * @see ServiceConfig#exportLocal(org.apache.dubbo.common.URL)
         *
         * SPI 文件为 dubbo-rpc/dubbo-rpc-api/src/main/resources/META-INF/dubbo/internal/org.apache.dubbo.rpc.Protocol
         * 其中内容为：
         * filter=org.apache.dubbo.rpc.protocol.ProtocolFilterWrapper
         * listener=org.apache.dubbo.rpc.protocol.ProtocolListenerWrapper
         *
         * 根据 Wrapper 上的 order 排序，order 小的包装在外面，其优先级最高
         * @see ExtensionLoader#createExtension(java.lang.String, boolean)
         *
         * 这里 ProtocolFilterWrapper order = 100，ProtocolListenerWrapper order = 200
         * 最后得到包装类 ProtocolFilterWrapper(ProtocolListenerWrapper(InjvmProtocol))
         */

        Protocol dubbo = ExtensionLoader.getExtensionLoader(Protocol.class).getExtension("dubbo");
        System.out.println(dubbo instanceof ProtocolFilterWrapper);
    }

    /**
     * 服务发布，将服务实现类转换成 Invoker
     */
    @Test
    public void getInvoker() throws Exception {
        ProxyFactory PROXY_FACTORY = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

        DemoServiceImpl ref = new DemoServiceImpl();
        Class<?> interfaceClass = DemoService.class;
        URL local = new URL("injvm", "127.0.0.1", 8888);
        local.setServiceInterface(interfaceClass.getSimpleName());
        local.setPath(interfaceClass.getSimpleName());

        /**
         * 服务发布，将服务实现类转换成 Invoker
         * @see ServiceConfig#exportLocal(org.apache.dubbo.common.URL)
         */
        Invoker invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, local);
        /**
         * @see JavassistProxyFactory#getInvoker(java.lang.Object, java.lang.Class, org.apache.dubbo.common.URL)
         * @see Wrapper#makeWrapper(java.lang.Class)
         *
         * 动态生成 org.apache.dubbo.common.bytecode.Wrapper0 代码见下方
         *
         * obj.instanceof(class)
         * class.inInstance(obj)
         */

        System.out.println("invoker = " + invoker);

        Thread.sleep(100000L);
    }


    /**
     * @see ExtensionLoader#createAdaptiveExtensionClass()
     *
     * 动态生成 Protocol$Adaptive 的代码如下：
     *
    package org.apache.dubbo.rpc;
    import org.apache.dubbo.common.extension.ExtensionLoader;
    public class Protocol$Adaptive implements org.apache.dubbo.rpc.Protocol {
    public void destroy()  {
    throw new UnsupportedOperationException("The method public abstract void org.apache.dubbo.rpc.Protocol.destroy() of interface org.apache.dubbo.rpc.Protocol is not adaptive method!");
    }
    public int getDefaultPort()  {
    throw new UnsupportedOperationException("The method public abstract int org.apache.dubbo.rpc.Protocol.getDefaultPort() of interface org.apache.dubbo.rpc.Protocol is not adaptive method!");
    }

    public org.apache.dubbo.rpc.Exporter export(org.apache.dubbo.rpc.Invoker arg0) throws org.apache.dubbo.rpc.RpcException {
    if (arg0 == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument == null");
    if (arg0.getUrl() == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument getUrl() == null");
    org.apache.dubbo.common.URL url = arg0.getUrl();
    String extName = ( url.getProtocol() == null ? "dubbo" : url.getProtocol() );
    if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.Protocol) name from url (" + url.toString() + ") use keys([protocol])");
    org.apache.dubbo.rpc.Protocol extension = (org.apache.dubbo.rpc.Protocol)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension(extName);
    return extension.export(arg0);
    }

    public java.util.List getServers()  {
    throw new UnsupportedOperationException("The method public default java.util.List org.apache.dubbo.rpc.Protocol.getServers() of interface org.apache.dubbo.rpc.Protocol is not adaptive method!");
    }
    public org.apache.dubbo.rpc.Invoker refer(java.lang.Class arg0, org.apache.dubbo.common.URL arg1) throws org.apache.dubbo.rpc.RpcException {
    if (arg1 == null) throw new IllegalArgumentException("url == null");
    org.apache.dubbo.common.URL url = arg1;
    String extName = ( url.getProtocol() == null ? "dubbo" : url.getProtocol() );
    if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.Protocol) name from url (" + url.toString() + ") use keys([protocol])");
    org.apache.dubbo.rpc.Protocol extension = (org.apache.dubbo.rpc.Protocol)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension(extName);
    return extension.refer(arg0, arg1);
    }
    }

    /**
     * @see ExtensionLoader#createAdaptiveExtensionClass()
     *
     * 动态生成的 ProxyFactory$Adaptive 代码如下：
     *
    package org.apache.dubbo.rpc;
    import org.apache.dubbo.common.extension.ExtensionLoader;
    public class ProxyFactory$Adaptive implements org.apache.dubbo.rpc.ProxyFactory {

    public java.lang.Object getProxy(org.apache.dubbo.rpc.Invoker arg0) throws org.apache.dubbo.rpc.RpcException {
    if (arg0 == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument == null");
    if (arg0.getUrl() == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument getUrl() == null");
    org.apache.dubbo.common.URL url = arg0.getUrl();
    String extName = url.getParameter("proxy", "javassist");
    if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.ProxyFactory) name from url (" + url.toString() + ") use keys([proxy])");
    org.apache.dubbo.rpc.ProxyFactory extension = (org.apache.dubbo.rpc.ProxyFactory)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.ProxyFactory.class).getExtension(extName);
    return extension.getProxy(arg0);
    }

    public java.lang.Object getProxy(org.apache.dubbo.rpc.Invoker arg0, boolean arg1) throws org.apache.dubbo.rpc.RpcException {
    if (arg0 == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument == null");
    if (arg0.getUrl() == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument getUrl() == null");
    org.apache.dubbo.common.URL url = arg0.getUrl();
    String extName = url.getParameter("proxy", "javassist");
    if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.ProxyFactory) name from url (" + url.toString() + ") use keys([proxy])");
    org.apache.dubbo.rpc.ProxyFactory extension = (org.apache.dubbo.rpc.ProxyFactory)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.ProxyFactory.class).getExtension(extName);
    return extension.getProxy(arg0, arg1);
    }

    public org.apache.dubbo.rpc.Invoker getInvoker(java.lang.Object arg0, java.lang.Class arg1, org.apache.dubbo.common.URL arg2) throws org.apache.dubbo.rpc.RpcException {
    if (arg2 == null) throw new IllegalArgumentException("url == null");
    org.apache.dubbo.common.URL url = arg2;
    String extName = url.getParameter("proxy", "javassist");
    if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.ProxyFactory) name from url (" + url.toString() + ") use keys([proxy])");
    org.apache.dubbo.rpc.ProxyFactory extension = (org.apache.dubbo.rpc.ProxyFactory)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.ProxyFactory.class).getExtension(extName);
    return extension.getInvoker(arg0, arg1, arg2);
    }
    }


    /**
     * @see JavassistProxyFactory#getInvoker(java.lang.Object, java.lang.Class, org.apache.dubbo.common.URL)
     *
     * 动态生成的 Wrapper 代码如下：
     *

    package org.apache.dubbo.common.bytecode;

    import com.sumkor.demo.DemoServiceImpl;
    import java.lang.reflect.InvocationTargetException;
    import java.util.Map;
    import org.apache.dubbo.common.bytecode.ClassGenerator;
    import org.apache.dubbo.common.bytecode.NoSuchMethodException;
    import org.apache.dubbo.common.bytecode.NoSuchPropertyException;
    import org.apache.dubbo.common.bytecode.Wrapper;

    public class Wrapper0
    extends Wrapper
    implements ClassGenerator.DC {
    public static String[] pns;
    public static Map pts;
    public static String[] mns;
    public static String[] dmns;
    public static Class[] mts0;
    public static Class[] mts1;

    public String[] getPropertyNames() {
    return pns;
    }

    public boolean hasProperty(String string) {
    return pts.containsKey(string);
    }

    public Class getPropertyType(String string) {
    return (Class)pts.get(string);
    }

    public String[] getMethodNames() {
    return mns;
    }

    public String[] getDeclaredMethodNames() {
    return dmns;
    }

    public void setPropertyValue(Object object, String string, Object object2) {
    try {
    DemoServiceImpl demoServiceImpl = (DemoServiceImpl)object;
    }
    catch (Throwable throwable) {
    throw new IllegalArgumentException(throwable);
    }
    throw new NoSuchPropertyException(new StringBuffer().append("Not found property \"").append(string).append("\" f
    ield or setter method in class com.sumkor.demo.DemoServiceImpl.").toString());
    }

    public Object getPropertyValue(Object object, String string) {
    try {
    DemoServiceImpl demoServiceImpl = (DemoServiceImpl)object;
    }
    catch (Throwable throwable) {
    throw new IllegalArgumentException(throwable);
    }
    throw new NoSuchPropertyException(new StringBuffer().append("Not found property \"").append(string).append("\" f
    ield or setter method in class com.sumkor.demo.DemoServiceImpl.").toString());
    }

    public Object invokeMethod(Object object, String string, Class[] arrclass, Object[] arrobject) throws InvocationTarg
    etException {
    DemoServiceImpl demoServiceImpl;
    try {
    demoServiceImpl = (DemoServiceImpl)object;
    }
    catch (Throwable throwable) {
    throw new IllegalArgumentException(throwable);
    }
    try {
    if ("sayHello".equals(string) && arrclass.length == 1) {
    return demoServiceImpl.sayHello((String)arrobject[0]);
    }
    if ("sayHelloAsync".equals(string) && arrclass.length == 1) {
    return demoServiceImpl.sayHelloAsync((String)arrobject[0]);
    }
    }
    catch (Throwable throwable) {
    throw new InvocationTargetException(throwable);
    }
    throw new NoSuchMethodException(new StringBuffer().append("Not found method \"").append(string).append("\" in cl
    ass com.sumkor.demo.DemoServiceImpl.").toString());
    }
    }

     */

}
