package com.sumkor.demo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.spring.context.DubboBootstrapApplicationListener;
import org.apache.dubbo.demo.DemoService;
import org.apache.dubbo.registry.integration.RegistryProtocol;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.listener.ListenerExporterWrapper;
import org.apache.dubbo.rpc.protocol.ProtocolFilterWrapper;
import org.apache.dubbo.rpc.protocol.ProtocolListenerWrapper;
import org.apache.dubbo.rpc.protocol.injvm.InjvmProtocol;
import org.apache.dubbo.rpc.proxy.javassist.JavassistProxyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * Dubbo 服务发布
 * https://dubbo.apache.org/zh/docs/v2.7/dev/source/export-service/
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
     * @see AbstractApplicationContext#finishRefresh()
     * @see AbstractApplicationContext#publishEvent(org.springframework.context.ApplicationEvent)
     * @see SimpleApplicationEventMulticaster#invokeListener(org.springframework.context.ApplicationListener, org.springframework.context.ApplicationEvent)
     *
     * 触发监听器
     * @see DubboBootstrapApplicationListener#onApplicationContextEvent(org.springframework.context.event.ApplicationContextEvent)
     * @see DubboBootstrapApplicationListener#onContextRefreshedEvent(org.springframework.context.event.ContextRefreshedEvent)
     *
     *
     * 1. 服务发布入口
     * @see DubboBootstrap#start()
     * @see org.apache.dubbo.config.spring.ServiceBean#exported()
     *
     * 是否配置延迟发布 && 是否已发布 && 是不是已被取消发布，否者发布服务
     * @see org.apache.dubbo.config.ServiceConfig#export()
     *
     * 观察 ServiceConfig 实例的变化，注意它重写 toString 方法
     * @see AbstractConfig#toString()
     *
     * 继续看服务发布
     * @see org.apache.dubbo.config.ServiceConfig#doExport()
     * @see ServiceConfig#doExportUrls()
     *
     * 2. 构造参数 map，再利用 map 构造 url，得到：
     * @see ServiceConfig#doExportUrlsFor1Protocol(org.apache.dubbo.config.ProtocolConfig, java.util.List)
     * dubbo://172.168.1.1:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=172.168.1.1&bind.port=20880&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=3500&release=&side=provider&timestamp=1608473481377
     *
     *
     *
     * 3. 本地发布
     * @see ServiceConfig#exportLocal(org.apache.dubbo.common.URL)
     *
     * 3.1 修改 url 中的协议为 injvm
     * injvm://127.0.0.1/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=172.168.1.1&bind.port=20880&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=3500&release=&side=provider&timestamp=1608473481377
     *
     * 3.2 PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, local)
     *
     * 这里的 PROXY_FACTORY 为 ProxyFactory$Adaptive 实例（代码见下方）
     * 得到 {@link ProxyFactory} 默认的实现类 {@link JavassistProxyFactory}
     * 执行 ProxyFactory$Adaptive#getInvoker，实际执行
     * @see JavassistProxyFactory#getInvoker(java.lang.Object, java.lang.Class, org.apache.dubbo.common.URL)
     *
     * 3.3 ROTOCOL.export(PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, local))
     *
     * SPI 文件为 dubbo-rpc/dubbo-rpc-api/src/main/resources/META-INF/dubbo/internal/org.apache.dubbo.rpc.Protocol
     * 这里的 ROTOCOL 为 Protocol$Adaptive 实例（代码见下方）
     * 执行 Protocol$Adaptive#export 方法，由于 url.getProtocol() 为 injvm，即实际执行
     * ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension("injvm")
     *
     * 得到 {@link Protocol} 具体的实现类 {@link InjvmProtocol}，再得到包装类 {@link ProtocolListenerWrapper}，再得到包装类 {@link ProtocolFilterWrapper}
     * 即 ProtocolFilterWrapper(ProtocolListenerWrapper(InjvmProtocol))
     *
     * 3.3.1 执行过滤器链
     * @see ProtocolFilterWrapper#export(org.apache.dubbo.rpc.Invoker)
     * @see ProtocolFilterWrapper#buildInvokerChain(org.apache.dubbo.rpc.Invoker, java.lang.String, java.lang.String)
     *
     * 3.3.2 继续执行
     * @see ProtocolListenerWrapper#export(org.apache.dubbo.rpc.Invoker)
     * @see InjvmProtocol#export(org.apache.dubbo.rpc.Invoker)
     * 最后得到 {@link ListenerExporterWrapper}
     *
     *
     *
     * 4. 远程发布
     *
     * 4.1 PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString()));
     *
     * 其中 registryURL 为
     * registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-provider&dubbo=2.0.2&export=dubbo%3A%2F%2F172.168.1.1%3A20880%2Forg.apache.dubbo.demo.DemoService%3Fanyhost%3Dtrue%26application%3Ddubbo-demo-api-provider%26bind.ip%3D172.168.1.1%26bind.port%3D20880%26default%3Dtrue%26deprecated%3Dfalse%26dubbo%3D2.0.2%26dynamic%3Dtrue%26generic%3Dfalse%26interface%3Dorg.apache.dubbo.demo.DemoService%26methods%3DsayHello%2CsayHelloAsync%26pid%3D7500%26release%3D%26side%3Dprovider%26timestamp%3D1608480121748&pid=7500&registry=zookeeper&timestamp=1608480120620
     *
     * 这里的 PROXY_FACTORY 为 ProxyFactory$Adaptive 实例（代码见下方）
     * 执行 ProxyFactory$Adaptive#getInvoker，实际执行
     * @see JavassistProxyFactory#getInvoker(java.lang.Object, java.lang.Class, org.apache.dubbo.common.URL)
     *
     * 4.2 PROTOCOL.export(wrapperInvoker)
     *
     * 这里的 ROTOCOL 为 Protocol$Adaptive 实例（代码见下方）
     * 执行 Protocol$Adaptive#export 方法，由于 url.getProtocol() 为 registry，即实际执行
     * ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.Protocol.class).getExtension("registry")
     * 得到包装类 ProtocolFilterWrapper(ProtocolListenerWrapper(RegistryProtocol))
     *
     * 4.2.1 执行过滤器链
     * @see ProtocolFilterWrapper#export(org.apache.dubbo.rpc.Invoker)
     * @see ProtocolFilterWrapper#buildInvokerChain(org.apache.dubbo.rpc.Invoker, java.lang.String, java.lang.String)
     *
     * 4.2.2 继续执行
     * @see ProtocolListenerWrapper#export(org.apache.dubbo.rpc.Invoker)
     * @see RegistryProtocol#export(org.apache.dubbo.rpc.Invoker)
     *
     * 其中 registryUrl
     * zookeeper://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-provider&dubbo=2.0.2&export=dubbo%3A%2F%2F172.168.1.1%3A20880%2Forg.apache.dubbo.demo.DemoService%3Fanyhost%3Dtrue%26application%3Ddubbo-demo-api-provider%26bind.ip%3D172.168.1.1%26bind.port%3D20880%26default%3Dtrue%26deprecated%3Dfalse%26dubbo%3D2.0.2%26dynamic%3Dtrue%26generic%3Dfalse%26interface%3Dorg.apache.dubbo.demo.DemoService%26methods%3DsayHello%2CsayHelloAsync%26pid%3D4856%26release%3D%26side%3Dprovider%26timestamp%3D1608481215162&pid=4856&timestamp=1608481214730
     * 其中 providerUrl
     * dubbo://172.168.1.1:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=172.168.1.1&bind.port=20880&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=4856&release=&side=provider&timestamp=1608481215162
     * 其中 overrideSubscribeUrl
     * provider://172.168.1.1:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=172.168.1.1&bind.port=20880&category=configurators&check=false&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=4856&release=&side=provider&timestamp=1608481215162
     *
     * A. 建立并发布 dubbo 协议的服务
     * @see RegistryProtocol#doLocalExport(org.apache.dubbo.rpc.Invoker, org.apache.dubbo.common.URL)
     *
     * B. 向 zookeeper 注册服务
     * @see RegistryProtocol#register(org.apache.dubbo.common.URL, org.apache.dubbo.common.URL)
     */

    /**
     * 服务发布，根据 injvm/dubbo 协议获取 protocol 实现类
     *
     * @see ServiceConfig#exportLocal(org.apache.dubbo.common.URL)
     */
    @Test
    public void spi_protocal() {
        Protocol injvm = ExtensionLoader.getExtensionLoader(Protocol.class).getExtension("injvm");
        System.out.println(injvm instanceof ProtocolFilterWrapper);

        Protocol dubbo = ExtensionLoader.getExtensionLoader(Protocol.class).getExtension("dubbo");
        System.out.println(dubbo instanceof ProtocolFilterWrapper);
    }

    /**
     * 服务发布，将服务实现类转换成 Invoker
     *
     * obj.instanceof(class)
     * class.inInstance(obj)
     *
     * @see ServiceConfig#exportLocal(org.apache.dubbo.common.URL)
     * @see JavassistProxyFactory#getInvoker(java.lang.Object, java.lang.Class, org.apache.dubbo.common.URL)
     *
     *
     * 使用 arthas 获取动态生成的字节码
     * java -jar arthas-boot.jar
     * sc *Wrapper*
     * jad org.apache.dubbo.common.bytecode.Wrapper0
     *
     * 动态生成 org.apache.dubbo.common.bytecode.Wrapper0 代码见下方
     */
    @Test
    public void getInvoker() throws Exception {
        ProxyFactory PROXY_FACTORY = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

        DemoServiceImpl ref = new DemoServiceImpl();
        Class<?> interfaceClass = DemoService.class;
        URL local = new URL("injvm", "127.0.0.1", 8888);
        local.setServiceInterface(interfaceClass.getSimpleName());
        local.setPath(interfaceClass.getSimpleName());

        Invoker invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, local);

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
