package com.sumkor.demo.core;

import io.netty.channel.AbstractChannel;
import org.apache.dubbo.monitor.support.MonitorFilter;
import org.apache.dubbo.registry.integration.RegistryProtocol;
import org.apache.dubbo.remoting.Dispatcher;
import org.apache.dubbo.remoting.exchange.codec.ExchangeCodec;
import org.apache.dubbo.remoting.exchange.support.DefaultFuture;
import org.apache.dubbo.remoting.exchange.support.header.HeaderExchangeClient;
import org.apache.dubbo.remoting.exchange.support.header.HeaderExchangeHandler;
import org.apache.dubbo.remoting.telnet.support.TelnetHandlerAdapter;
import org.apache.dubbo.remoting.transport.AbstractClient;
import org.apache.dubbo.remoting.transport.AbstractPeer;
import org.apache.dubbo.remoting.transport.DecodeHandler;
import org.apache.dubbo.remoting.transport.dispatcher.ChannelEventRunnable;
import org.apache.dubbo.remoting.transport.dispatcher.all.AllChannelHandler;
import org.apache.dubbo.remoting.transport.dispatcher.all.AllDispatcher;
import org.apache.dubbo.remoting.transport.netty4.NettyClient;
import org.apache.dubbo.remoting.transport.netty4.NettyServer;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.support.AbstractClusterInvoker;
import org.apache.dubbo.rpc.cluster.support.FailoverClusterInvoker;
import org.apache.dubbo.rpc.cluster.support.wrapper.AbstractCluster;
import org.apache.dubbo.rpc.cluster.support.wrapper.MockClusterInvoker;
import org.apache.dubbo.rpc.filter.ConsumerContextFilter;
import org.apache.dubbo.rpc.listener.ListenerInvokerWrapper;
import org.apache.dubbo.rpc.protocol.AbstractInvoker;
import org.apache.dubbo.rpc.protocol.AsyncToSyncInvoker;
import org.apache.dubbo.rpc.protocol.InvokerWrapper;
import org.apache.dubbo.rpc.protocol.dubbo.*;
import org.apache.dubbo.rpc.protocol.dubbo.filter.FutureFilter;
import org.apache.dubbo.rpc.proxy.AbstractProxyInvoker;
import org.apache.dubbo.rpc.proxy.InvokerInvocationHandler;
import org.apache.dubbo.rpc.proxy.javassist.JavassistProxyFactory;

import java.util.concurrent.CompletableFuture;

/**
 * 服务调用过程
 * https://dubbo.apache.org/zh/docs/v2.7/dev/source/service-invoking-process/
 *
 * Dubbo 服务调用过程比较复杂，包含众多步骤，比如发送请求、编解码、服务降级、过滤器链处理、序列化、线程派发以及响应请求等步骤。
 *
 * 远程调用请求过程：
 * 服务消费者通过代理对象 Proxy 发起远程调用，接着通过网络客户端 Client 将编码后的请求发送给服务提供方的网络层上，也就是 Server。
 * Server 在收到请求后，首先要做的事情是对数据包进行解码。
 * 然后将解码后的请求发送至分发器 Dispatcher，再由分发器将请求派发到指定的线程池上，最后由线程池调用具体的服务。
 *
 * 服务调用方式（2.6.x）：
 * Dubbo 支持同步和异步两种调用方式，其中异步调用还可细分为“有返回值”的异步调用和“无返回值”的异步调用。
 * 所谓“无返回值”异步调用是指服务消费方只管调用，但不关心调用结果，此时 Dubbo 会直接返回一个空的 RpcResult。
 * 若要使用异步特性，需要服务消费方手动进行配置。
 * 默认情况下，Dubbo 使用同步调用方式。
 *
 *
 * @author Sumkor
 * @since 2021/1/6
 */
public class ServiceInvokeTest {

    /**
     * 1. 服务消费方发起调用请求
     *
     * 由服务引入可知，在调用方生成的代理对象为
     * proxy0 -> InvokerInvocationHandler -> MockClusterInvoker
     * 其中具有两个属性：
     * (directory属性) RegistryDirectory -> (invokers属性) RegistryDirectory.InvokerDelegate -> ProtocolFilterWrapper -> ProtocolListenerWrapper -> AsyncToSyncInvoker -> DubboInvoker
     * (invoker属性) AbstractCluster.InterceptorInvokerNode -> FailoverClusterInvoker -> (directory属性) RegistryDirectory -> ...
     *
     * @see JavassistProxyFactory#getProxy(org.apache.dubbo.rpc.Invoker, java.lang.Class[])
     * @see RegistryProtocol#doRefer(org.apache.dubbo.rpc.cluster.Cluster, org.apache.dubbo.registry.Registry, java.lang.Class, org.apache.dubbo.common.URL)
     *
     * 2. 发起调用入口
     * @see InvokerInvocationHandler#invoke(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
     * 入参：
     * Object = proxy0实例
     * Method = public abstract java.lang.String org.apache.dubbo.demo.DemoService.sayHello(java.lang.String)
     *
     * 注意这里创建了 RpcInvocation 作为 Invoker#invoke 入参。
     *
     * 2.1 进入集群容错的处理逻辑
     * @see MockClusterInvoker#invoke(org.apache.dubbo.rpc.Invocation)
     * @see AbstractCluster.InterceptorInvokerNode#invoke(org.apache.dubbo.rpc.Invocation)
     * @see AbstractClusterInvoker#invoke(org.apache.dubbo.rpc.Invocation)
     * @see FailoverClusterInvoker#doInvoke(org.apache.dubbo.rpc.Invocation, java.util.List, org.apache.dubbo.rpc.cluster.LoadBalance)
     *
     * 根据负载策略，选择其中一个 invoker
     *
     * 执行 RegistryDirectory.InvokerDelegate#invoke
     * @see InvokerWrapper#invoke(org.apache.dubbo.rpc.Invocation)
     *
     * 2.2 执行过滤器链
     * 执行 ProtocolFilterWrapper#invoke
     * @see Invoker#invoke(org.apache.dubbo.rpc.Invocation)
     * @see ConsumerContextFilter#invoke(org.apache.dubbo.rpc.Invoker, org.apache.dubbo.rpc.Invocation)
     * @see FutureFilter#invoke(org.apache.dubbo.rpc.Invoker, org.apache.dubbo.rpc.Invocation)
     * @see MonitorFilter#invoke(org.apache.dubbo.rpc.Invoker, org.apache.dubbo.rpc.Invocation)
     *
     * 2.3 后续执行
     * @see ListenerInvokerWrapper#invoke(org.apache.dubbo.rpc.Invocation)
     * @see AsyncToSyncInvoker#invoke(org.apache.dubbo.rpc.Invocation)
     * @see AbstractInvoker#invoke(org.apache.dubbo.rpc.Invocation)
     *
     * 3. DubboInvoker 发起服务调用
     * @see DubboInvoker#doInvoke(org.apache.dubbo.rpc.Invocation)
     *
     * 双向请求，返回 {@link CompletableFuture} 对象，再包装在 {@link AsyncRpcResult} 对象中返回。
     *
     * Dubbo 实现同步和异步调用比较关键的一点就在于由谁调用 {@link CompletableFuture#get()} 方法。
     * 同步调用模式下，由框架自身调用该方法，见 {@link AsyncToSyncInvoker}。异步调用模式下，则由用户调用该方法。
     *
     *
     *
     * 4. Netty Client 发送请求
     *
     * ReferenceCountExchangeClient 内部仅实现了一个引用计数的功能
     * @see org.apache.dubbo.rpc.protocol.dubbo.ReferenceCountExchangeClient#request(java.lang.Object, int, java.util.concurrent.ExecutorService)
     *
     * HeaderExchangeClient 封装了一些关于心跳检测的逻辑
     * @see HeaderExchangeClient#request(java.lang.Object, int, java.util.concurrent.ExecutorService)
     *
     * 通过 Exchange 层为框架引入 Request 语义（服务消费方）
     * @see org.apache.dubbo.remoting.exchange.support.header.HeaderExchangeChannel#request(java.lang.Object, int, java.util.concurrent.ExecutorService)
     *
     * 这里将请求数据 RpcInvocation 封装在请求对象 Request，并创建 CompletableFuture 用于接收响应结果。
     * 在 {@link DefaultFuture#FUTURES} 之中存储请求 id 和 CompletableFuture 实例的映射关系。
     *
     * 接着将请求数据送至 NettyChannel
     * NettyClient 中并未实现 send 方法，该方法继承自父类 AbstractPeer
     *
     * @see AbstractPeer#send(java.lang.Object)
     * @see AbstractClient#send(java.lang.Object, boolean)
     * @see org.apache.dubbo.remoting.transport.netty4.NettyChannel#send(java.lang.Object, boolean)
     * @see AbstractChannel#writeAndFlush(java.lang.Object)
     *
     * 经历多次调用，到这里请求数据的发送过程就结束了，过程漫长。为了便于大家阅读代码，这里以 DemoService 为例，将 sayHello 方法的整个调用路径贴出来。
     *
     * proxy0#sayHello(String)
     *   —> InvokerInvocationHandler#invoke(Object, Method, Object[])
     *     —> MockClusterInvoker#invoke(Invocation)
     *       —> AbstractClusterInvoker#invoke(Invocation)
     *         —> FailoverClusterInvoker#doInvoke(Invocation, List<Invoker<T>>, LoadBalance)
     *           —> Filter#invoke(Invoker, Invocation)  // 包含多个 Filter 调用
     *             —> ListenerInvokerWrapper#invoke(Invocation)
     *               —> AbstractInvoker#invoke(Invocation)
     *                 —> DubboInvoker#doInvoke(Invocation)
     *                   —> ReferenceCountExchangeClient#request(Object, int)
     *                     —> HeaderExchangeClient#request(Object, int)
     *                       —> HeaderExchangeChannel#request(Object, int)
     *                         —> AbstractPeer#send(Object)
     *                           —> AbstractClient#send(Object, boolean)
     *                             —> NettyChannel#send(Object, boolean)
     */

    /**
     * 5. 请求编码
     *
     * Dubbo 数据包分为消息头和消息体。
     * 消息头用于存储一些元信息，比如魔数（Magic），数据包类型（Request/Response），消息体长度（Data Length）等。
     * 消息体中用于存储具体的调用消息，比如方法名称，参数列表等。
     * https://dubbo.apache.org/zh/docs/v2.7/dev/source/service-invoking-process/#222-%E8%AF%B7%E6%B1%82%E7%BC%96%E7%A0%81
     *
     * 启用 Netty 服务和客户端时，需要配置编码器和解码器
     * @see NettyServer#doOpen()
     * @see NettyClient#doOpen()
     *
     * 编码逻辑位置：
     * @see ExchangeCodec#encode(org.apache.dubbo.remoting.Channel, org.apache.dubbo.remoting.buffer.ChannelBuffer, java.lang.Object)
     *
     * 请求对象的编码过程：
     * 该过程首先会通过位运算将消息头写入到 header 数组中。
     * 然后对 Request 对象的 data 字段执行序列化操作，序列化后的数据最终会存储到 ChannelBuffer 中。
     * 序列化操作执行完后，可得到数据序列化后的长度 len，紧接着将 len 写入到 header 指定位置处。
     * 最后再将消息头字节数组 header 写入到 ChannelBuffer 中，整个编码过程就结束了。
     * @see ExchangeCodec#encodeRequest(org.apache.dubbo.remoting.Channel, org.apache.dubbo.remoting.buffer.ChannelBuffer, org.apache.dubbo.remoting.exchange.Request)
     *
     * 注意写入过程，先给消息头预留位置，从预留位置之后写入序列化后的消息体，获取消息体的长度之后，再从预留位置的起始位写入消息头信息。
     *
     * Request 对象的 data 字段序列化过程：
     * @see DubboCodec#encodeRequestData(org.apache.dubbo.remoting.Channel, org.apache.dubbo.common.serialize.ObjectOutput, java.lang.Object, java.lang.String)
     */

    /**
     * 6. 请求解码
     *
     * 服务提供方接收请求，默认情况下 Dubbo 使用 Netty 作为底层的通信框架。
     * Netty 检测到有数据入站后，首先会通过解码器对数据进行解码，并将解码后的数据传递给下一个入站处理器的指定方法。
     *
     * 解码逻辑位置：
     * @see ExchangeCodec#decode(org.apache.dubbo.remoting.Channel, org.apache.dubbo.remoting.buffer.ChannelBuffer)
     * @see ExchangeCodec#decode(org.apache.dubbo.remoting.Channel, org.apache.dubbo.remoting.buffer.ChannelBuffer, int, byte[])
     * 如上，通过检测消息头中的魔数是否与规定的魔数相等，提前拦截掉非常规数据包，比如通过 telnet 命令行发出的数据包。
     *
     * 补充：服务端对 telnet 的支持
     * @see TelnetHandlerAdapter#telnet(org.apache.dubbo.remoting.Channel, java.lang.String)
     *
     * 继续看解码逻辑，ExchangeCodec 中实现了 decodeBody 方法，但因其子类 DubboCodec 覆写了该方法，实际执行：
     * @see DubboCodec#decodeBody(org.apache.dubbo.remoting.Channel, java.io.InputStream, byte[])
     * 如上，decodeBody 对部分字段进行了解码，并将解码得到的字段封装到 Request 中（服务提供方）。
     *
     * 从数据包中解析：调用方法名、调用参数等
     * @see DecodeableRpcInvocation#decode()
     * @see DecodeableRpcInvocation#decode(org.apache.dubbo.remoting.Channel, java.io.InputStream)
     * 如上，最终得到一个具有完整调用信息的 DecodeableRpcInvocation 对象。
     *
     * 到这里，请求数据解码的过程就分析完了。此时我们得到了一个 Request 对象，这个对象会被传送到下一个入站处理器中，我们继续往下看
     */

    /**
     * 7. 服务提供方调用服务
     *
     * 解码器将数据包解析成 Request 对象后，NettyHandler 的 messageReceived 方法紧接着会收到这个对象，并将这个对象继续向下传递。
     * @see org.apache.dubbo.remoting.transport.netty.NettyHandler#messageReceived(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.MessageEvent)
     *
     * 这期间该对象会被依次传递给 NettyServer、MultiMessageHandler、HeartbeatHandler 以及 AllChannelHandler。
     * 最后由 AllChannelHandler 将该对象封装到 Runnable 实现类对象中，并将 Runnable 放入线程池中执行后续的调用逻辑。
     * @see AllChannelHandler#received(org.apache.dubbo.remoting.Channel, java.lang.Object)
     *
     * 整个调用栈如下：
     * NettyHandler#messageReceived(ChannelHandlerContext, MessageEvent)
     *   —> AbstractPeer#received(Channel, Object)
     *     —> MultiMessageHandler#received(Channel, Object)
     *       —> HeartbeatHandler#received(Channel, Object)
     *         —> AllChannelHandler#received(Channel, Object)
     *           —> ExecutorService#execute(Runnable)    // 由线程池执行后续的调用逻辑
     *
     * 看 AllChannelHandler 之前，先了解一下 Dubbo 中的线程派发模型。
     *
     * 7.1 线程派发模型
     *
     * Dubbo 将底层通信框架中接收请求的线程称为 IO 线程。
     * 如果一些事件处理逻辑可以很快执行完，比如只在内存打一个标记，此时直接在 IO 线程上执行该段逻辑即可。
     * 但如果事件的处理逻辑比较耗时，比如该段逻辑会发起数据库查询或者 HTTP 请求。此时我们就不应该让事件处理逻辑在 IO 线程上执行，而是应该派发到线程池中去执行。
     * 原因也很简单，IO 线程主要用于接收请求，如果 IO 线程被占满，将导致它不能接收新的请求。
     *
     * {@link Dispatcher} 即为线程派发器。
     * 其职责是，创建具有线程派发能力的 ChannelHandler，比如 AllChannelHandler、MessageOnlyChannelHandler 和 ExecutionChannelHandler 等，其本身并不具备线程派发能力。
     *
     * Dubbo 支持 5 种不同的线程派发策略：
     *     all：所有消息都派发到线程池，包括请求，响应，连接事件，断开事件等
     *     direct：所有消息都不派发到线程池，全部在 IO 线程上直接执行
     *     message：只有请求和响应消息派发到线程池，其它消息均在 IO 线程上执行
     *     execution：只有请求消息派发到线程池，不含响应。其它消息均在 IO 线程上执行
     *     connection：在 IO 线程上，将连接断开事件放入队列，有序逐个执行，其它消息派发到线程池
     *
     * 7.2 AllDispatcher
     *
     * 默认配置下，Dubbo 使用 all 派发策略 {@link AllDispatcher}，即将所有的消息都派发到线程池中。
     * 下面我们来分析一下 {@link AllChannelHandler} 的代码。
     *
     * @see AllChannelHandler#received(org.apache.dubbo.remoting.Channel, java.lang.Object)
     * 如上，请求对象会被封装 ChannelEventRunnable 中，接下来我们以 ChannelEventRunnable 为起点向下探索。
     *
     *
     * 7.3 调用服务，发送响应结果
     *
     * @see ChannelEventRunnable#run()
     * ChannelEventRunnable 仅是一个中转站，它的 run 方法中并不包含具体的调用逻辑，仅用于将参数传给其他 ChannelHandler 对象进行处理。
     * 该对象类型为 DecodeHandler。
     *
     * @see DecodeHandler#received(org.apache.dubbo.remoting.Channel, java.lang.Object)
     * DecodeHandler 存在的意义就是保证请求或响应对象可在线程池中被解码。
     * 上一节分析请求解码 {@link DubboCodec#decodeBody} 可知，请求解码可在 IO 线程上执行，也可在线程池中执行，这个取决于运行时配置。
     * 解码完毕后，完全解码后的 Request 对象会继续向后传递，下一站是 HeaderExchangeHandler。
     *
     * @see HeaderExchangeHandler#received(org.apache.dubbo.remoting.Channel, java.lang.Object)
     * @see HeaderExchangeHandler#handleRequest(org.apache.dubbo.remoting.exchange.ExchangeChannel, org.apache.dubbo.remoting.exchange.Request)
     * 到这里，我们看到了比较清晰的请求和响应逻辑。
     * 对于双向通信，HeaderExchangeHandler 首先向后进行调用，得到调用结果。然后将调用结果封装到 Response 对象中，最后再将该对象返回给服务消费方。
     * 如果请求不合法，或者调用失败，则将错误信息封装到 Response 对象中，并返回给服务消费方。
     *
     * 下面分析定义在 DubboProtocol 类中的匿名类，它对服务实现类进行真正地调用。
     *
     * @see DubboProtocol#requestHandler
     *
     *
     * 7.4 Invoker#invoke
     *
     * @see AbstractProxyInvoker#invoke(org.apache.dubbo.rpc.Invocation)
     *
     * Invoker 实例是在运行时通过 JavassistProxyFactory 创建的
     * @see JavassistProxyFactory#getInvoker(java.lang.Object, java.lang.Class, org.apache.dubbo.common.URL)
     *
     * 到这里，整个服务调用过程就分析完了。最后把调用过程贴出来，如下：
     * ChannelEventRunnable#run()
     *   —> DecodeHandler#received(Channel, Object)
     *     —> HeaderExchangeHandler#received(Channel, Object)
     *       —> HeaderExchangeHandler#handleRequest(ExchangeChannel, Request)
     *         —> DubboProtocol.requestHandler#reply(ExchangeChannel, Object)
     *           —> Filter#invoke(Invoker, Invocation)
     *             —> AbstractProxyInvoker#invoke(Invocation)
     *               —> Wrapper0#invokeMethod(Object, String, Class[], Object[])
     *                 —> DemoServiceImpl#sayHello(String)
     *
     */

    /**
     * 8. 服务提供方返回调用结果
     *
     * 服务提供方调用指定服务后，会将调用结果封装到 Response 对象中，并将该对象返回给服务消费方。
     * 服务提供方也是通过 NettyChannel 的 send 方法将 Response 对象返回。
     *
     * 本节我们仅需关注 Response 对象的编码过程即可，这里仍然省略一些中间调用，直接分析具体的编码逻辑。
     *
     * @see ExchangeCodec#encode(org.apache.dubbo.remoting.Channel, org.apache.dubbo.remoting.buffer.ChannelBuffer, java.lang.Object)
     * @see ExchangeCodec#encodeResponse(org.apache.dubbo.remoting.Channel, org.apache.dubbo.remoting.buffer.ChannelBuffer, org.apache.dubbo.remoting.exchange.Response)
     *
     * @see DubboCodec#encodeResponseData(org.apache.dubbo.remoting.Channel, org.apache.dubbo.common.serialize.ObjectOutput, java.lang.Object, java.lang.String)
     *
     */

    /**
     * 9. 服务消费方接收调用结果
     *
     * 服务消费方在收到响应数据后，首先要做的事情是对响应数据进行解码，得到 Response 对象。
     * 然后再将该对象传递给下一个入站处理器，这个入站处理器就是 NettyHandler。接下来 NettyHandler 会将这个对象继续向下传递，
     * 最后 AllChannelHandler 的 received 方法会收到这个对象，并将这个对象派发到线程池中。
     *
     * 这个过程和服务提供方接收请求的过程是一样的，因此这里就不重复分析了。
     * 本节我们重点分析两个方面的内容，一是响应数据的解码过程，二是 Dubbo 如何将调用结果传递给用户线程的。
     *
     * 9.1 响应数据解码
     *
     * 响应数据解码逻辑主要的逻辑封装在 DubboCodec 中，解码后的数据存储在 Response 对象中。
     * @see ExchangeCodec#decode(org.apache.dubbo.remoting.Channel, org.apache.dubbo.remoting.buffer.ChannelBuffer)
     * @see ExchangeCodec#decode(org.apache.dubbo.remoting.Channel, org.apache.dubbo.remoting.buffer.ChannelBuffer, int, byte[])
     * @see DubboCodec#decodeBody(org.apache.dubbo.remoting.Channel, java.io.InputStream, byte[])
     *
     * 调用结果的反序列化过程
     * @see DecodeableRpcResult#decode()
     * @see DecodeableRpcResult#decode(org.apache.dubbo.remoting.Channel, java.io.InputStream)
     *
     * 9.2 将调用结果传递给用户线程
     *
     * 响应数据解码完成后，Dubbo 会将响应对象 Response 派发到线程池上。
     * 要注意的是，线程池中的线程并非用户的调用线程，所以要想办法将响应对象从线程池线程传递到用户线程上。
     * @see HeaderExchangeHandler#received(org.apache.dubbo.remoting.Channel, java.lang.Object)
     * @see HeaderExchangeHandler#handleResponse(org.apache.dubbo.remoting.Channel, org.apache.dubbo.remoting.exchange.Response)
     *
     * 根据请求 id 从 {@link DefaultFuture#FUTURES} 之中获取 DefaultFuture 对象。
     * @see DefaultFuture#received(org.apache.dubbo.remoting.Channel, org.apache.dubbo.remoting.exchange.Response, boolean)
     *
     * 将响应对象保存到相应的 DefaultFuture 实例中
     * @see DefaultFuture#doReceived(org.apache.dubbo.remoting.exchange.Response)
     *
     * 随后用户线程即可从 DefaultFuture 实例中获取到相应结果。
     * @see AsyncToSyncInvoker#invoke(org.apache.dubbo.rpc.Invocation)
     *
     *
     * 补充：
     * 官方文档对 {@link DefaultFuture#FUTURES} 的说明：
     * https://dubbo.apache.org/zh/docs/v2.7/dev/source/service-invoking-process/#25-%E6%9C%8D%E5%8A%A1%E6%B6%88%E8%B4%B9%E6%96%B9%E6%8E%A5%E6%94%B6%E8%B0%83%E7%94%A8%E7%BB%93%E6%9E%9C
     * 如何将每个响应对象传递给相应的 DefaultFuture 对象，且不出错。
     * 答案是通过调用编号。
     * DefaultFuture 被创建时，会要求传入一个 Request 对象。
     * 此时 DefaultFuture 可从 Request 对象中获取调用编号，并将 <调用编号, DefaultFuture 对象> 映射关系存入到静态 Map 中，即 FUTURES。
     * 线程池中的线程在收到 Response 对象后，会根据 Response 对象中的调用编号到 FUTURES 集合中取出相应的 DefaultFuture 对象，然后再将 Response 对象设置到 DefaultFuture 对象中。
     * 最后再唤醒用户线程，这样用户线程即可从 DefaultFuture 对象中获取调用结果了。
     *
     * 注意，在 dubbo 2.7.8 之中，并没有手动唤醒用户线程的逻辑。
     * 而是通过执行 CompletableFuture#complete 方法，可以直接唤醒一直阻塞在 CompletableFuture#get 方法上的用户线程，得到执行结果。
     */

    /**
     * 服务调用过程总结
     *
     *                           服务消费方                 服务提供方
     *      Request ---编码--> NettyClient =====请求=====> NettyServer ---解码--> Request
     *     Response <--解码--- NettyClient <====响应====== NettyServer <--编码--- Response
     *
     * 按照通信顺序，通信过程包括服务消费方发送请求，服务提供方接收请求，服务提供方返回响应数据，服务消费方接收响应数据等过程。
     * 不管是【服务消费方还是服务提供方】，发送请求的过程都是同步的，接收请求的时候才做了线程派发。
     *
     * 【服务消费方】
     * 在发起调用时创建 CompletableFuture 对象并立即返回，在接收到响应时设置 CompletableFuture#complete。由此实现同步调用，异步获取返回。
     * 已知服务消费方在接收到响应时，执行了线程派发。这里的线程池的创建和使用过程如下：
     * 创建线程池 {@link DubboInvoker#doInvoke(org.apache.dubbo.rpc.Invocation)}
     * 设置线程池 {@link org.apache.dubbo.remoting.exchange.support.header.HeaderExchangeChannel#request(java.lang.Object, int, java.util.concurrent.ExecutorService)}
     * 取出线程池 {@link AllChannelHandler#received(org.apache.dubbo.remoting.Channel, java.lang.Object)}
     *
     */
}
