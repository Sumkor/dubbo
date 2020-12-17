package com.sumkor.demo;

import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.spring.context.DubboBootstrapApplicationListener;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
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
     * @see DubboBootstrap#start()
     * @see org.apache.dubbo.config.spring.ServiceBean#exported()
     *
     * 是否配置延迟发布 && 是否已发布 && 是不是已被取消发布，否者发布服务
     * @see org.apache.dubbo.config.ServiceConfig#export()
     * @see org.apache.dubbo.config.ServiceConfig#doExport()
     * @see ServiceConfig#doExportUrls()
     *
     *
     * 重写 toString 方法
     * @see AbstractConfig#toString()
     *
     */
}
