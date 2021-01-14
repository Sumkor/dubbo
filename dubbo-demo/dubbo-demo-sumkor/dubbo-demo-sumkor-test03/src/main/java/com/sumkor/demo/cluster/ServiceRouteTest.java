package com.sumkor.demo.cluster;

import org.apache.dubbo.registry.integration.RegistryDirectory;
import org.apache.dubbo.rpc.cluster.RouterChain;
import org.apache.dubbo.rpc.cluster.router.condition.ConditionRouter;
import org.apache.dubbo.rpc.cluster.router.condition.config.AppRouter;
import org.apache.dubbo.rpc.cluster.router.condition.config.ListenableRouter;
import org.apache.dubbo.rpc.cluster.router.condition.config.ServiceRouter;
import org.apache.dubbo.rpc.cluster.router.mock.MockInvokersSelector;
import org.apache.dubbo.rpc.cluster.router.tag.TagRouter;
import org.apache.dubbo.rpc.cluster.support.AbstractClusterInvoker;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 服务路由
 * https://dubbo.apache.org/zh/docs/v2.7/dev/source/router/
 * <p>
 * 服务目录 Directory。服务目录在列举 Invoker 列表的过程中，会通过 Router 进行服务路由，筛选出符合路由规则的服务提供者。
 * 服务路由包含一条路由规则，路由规则决定了服务消费者的调用目标，即规定了服务消费者可调用哪些服务提供者。
 * Dubbo 目前提供了三种服务路由实现，分别为条件路由 ConditionRouter、脚本路由 ScriptRouter 和标签路由 TagRouter。
 * 其中条件路由是最常使用的。
 *
 * @author Sumkor
 * @since 2021/1/14
 */
public class ServiceRouteTest {

    /**
     * 服务消费者发起服务调用
     * @see AbstractClusterInvoker#invoke(org.apache.dubbo.rpc.Invocation)
     *
     * 列举 Invoker
     * @see RegistryDirectory#doList(org.apache.dubbo.rpc.Invocation)
     * @see RouterChain#route(org.apache.dubbo.common.URL, org.apache.dubbo.rpc.Invocation)
     * 入参：
     *     ConsumerUrl = zookeeper://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?anyhost=true&application=dubbo-demo-api-consumer&check=false&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&methods=sayHello,sayHelloAsync&pid=35060&register.ip=172.20.3.201&release=&remote.application=dubbo-demo-api-provider&side=consumer&sticky=false&timestamp=1610609006475
     *
     * 已知服务消费者初始化过程，得到的 4 个 routers 如下：
     * @see MockInvokersSelector
     * @see TagRouter
     * @see AppRouter
     * @see ServiceRouter
     *
     * 遍历 routers，依此对 DubboInvoker 的包装类执行 Router#route 操作：
     *
     * @see MockInvokersSelector#route(java.util.List, org.apache.dubbo.common.URL, org.apache.dubbo.rpc.Invocation)
     * @see TagRouter#route(java.util.List, org.apache.dubbo.common.URL, org.apache.dubbo.rpc.Invocation)
     * @see ListenableRouter#route(java.util.List, org.apache.dubbo.common.URL, org.apache.dubbo.rpc.Invocation)
     *
     * 由于 ConsumerUrl 中没有 route 参数，因此 Router 均并没有命中，没有做什么过滤操作。
     */

    /**
     * 条件路由 ConditionRouter
     * @see org.apache.dubbo.rpc.cluster.router.condition.ConditionRouterTest
     *
     * 条件路由规则由两个条件组成，分别用于对服务消费者和提供者进行匹配。比如有这样一条规则：
     *
     * host = 10.20.153.10 => host = 10.20.153.11
     *
     * 该条规则表示 IP 为 10.20.153.10 的服务消费者只可调用 IP 为 10.20.153.11 机器上的服务，不可调用其他机器上的服务。条件路由规则的格式如下：
     *
     * [服务消费者匹配条件] => [服务提供者匹配条件]
     *
     * @see ConditionRouter#parseRule(java.lang.String)
     */

    /**
     * 路由正则匹配
     */
    @Test
    public void routePattern() {
        Pattern ROUTE_PATTERN = Pattern.compile("([&!=,]*)\\s*([^&!=,\\s]+)");
        // 这个表达式看起来不是很好理解，第一个括号内的表达式用于匹配"&", "!", "=" 和 "," 等符号。
        // 第二括号内的用于匹配英文字母，数字等字符。举个例子说明一下：
        //    host = 2.2.2.2 & host != 1.1.1.1 & method = hello
        // 匹配结果如下：
        //     括号一      括号二
        // 1.  null       host
        // 2.   =         2.2.2.2
        // 3.   &         host
        // 4.   !=        1.1.1.1
        // 5.   &         method
        // 6.   =         hello
        Matcher matcher = ROUTE_PATTERN.matcher("host = 2.2.2.2 & host != 1.1.1.1 & method = hello");
        while (matcher.find()) {
            String separator = matcher.group(1);
            String content = matcher.group(2);
            System.out.println("separator = " + separator);
            System.out.println("content = " + content);
            System.out.println();
        }
    }
}
