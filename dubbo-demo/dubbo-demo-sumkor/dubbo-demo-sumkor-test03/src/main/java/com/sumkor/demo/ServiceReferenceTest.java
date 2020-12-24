package com.sumkor.demo;

import com.alibaba.spring.beans.factory.annotation.AbstractAnnotationBeanPostProcessor;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.beans.factory.annotation.AnnotatedInterfaceConfigBeanBuilder;
import org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
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
     * init 方法主要用于处理配置，以及调用 createProxy 生成代理类
     * @see ReferenceConfig#init()
     *
     * 2. 创建代理类
     * @see ReferenceConfig#createProxy(java.util.Map)
     *
     *
     *
     *
     */


}
