package com.sumkor.demo.core;

/**
 * 服务调用过程
 * https://dubbo.apache.org/zh/docs/v2.7/dev/source/service-invoking-process/
 *
 * Dubbo 服务调用过程比较复杂，包含众多步骤，比如发送请求、编解码、服务降级、过滤器链处理、序列化、线程派发以及响应请求等步骤。
 *
 * 服务消费者通过代理对象 Proxy 发起远程调用，接着通过网络客户端 Client 将编码后的请求发送给服务提供方的网络层上，也就是 Server。
 * Server 在收到请求后，首先要做的事情是对数据包进行解码。然后将解码后的请求发送至分发器 Dispatcher，再由分发器将请求派发到指定的线程池上，最后由线程池调用具体的服务。
 * 这就是一个远程调用请求的发送与接收过程。
 *
 * 服务调用方式：
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
     *
     *
     *
     *
     */
}
