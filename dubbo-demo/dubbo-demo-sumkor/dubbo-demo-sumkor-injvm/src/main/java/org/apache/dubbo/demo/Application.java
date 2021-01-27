package org.apache.dubbo.demo;

import org.apache.dubbo.config.*;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.utils.ReferenceConfigCache;

/**
 * 使用 injvm 协议，只有在同一进程的情况下，才可调用成功
 *
 * @author Sumkor
 * @since 2021/1/27
 */
public class Application {

    public static void main(String[] args) {
        startWithBootstrap();
        runWithBootstrap();
    }

    private static void startWithBootstrap() {
        ServiceConfig<DemoServiceImpl> service = new ServiceConfig<>();
        service.setInterface(DemoService.class);
        service.setRef(new DemoServiceImpl());
        service.setProtocol(new ProtocolConfig("injvm"));

        DubboBootstrap bootstrap = DubboBootstrap.getInstance();
        bootstrap.application(new ApplicationConfig("dubbo-demo-api-provider"))
                .service(service)
                .start();
    }

    private static void runWithBootstrap() {
        ReferenceConfig<DemoService> reference = new ReferenceConfig<>();
        reference.setInterface(DemoService.class);
        reference.setGeneric("false");// 不使用泛化
        reference.setInjvm(true);// 本地服务引入

        DubboBootstrap bootstrap = DubboBootstrap.getInstance();
        bootstrap.application(new ApplicationConfig("dubbo-demo-api-consumer"))
                .reference(reference)
                .start();

        DemoService demoService = ReferenceConfigCache.getCache().get(reference);
        String message = demoService.sayHello("dubbo");
        System.out.println(message);

    }
}
