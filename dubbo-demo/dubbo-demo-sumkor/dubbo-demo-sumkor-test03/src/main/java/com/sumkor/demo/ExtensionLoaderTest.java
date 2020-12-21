package com.sumkor.demo;

import com.sumkor.demo.impl.DuplicateExtImplTest01;
import com.sumkor.demo.impl.DuplicateExtImplTest02;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.demo.DuplicateExt;
import org.junit.jupiter.api.Test;

/**
 * Dubbo SPI
 *
 * @author Sumkor
 * @since 2020/12/10
 */
public class ExtensionLoaderTest {

    /**
     * Dubbo SPI 官方文档
     * https://dubbo.apache.org/zh/docs/v2.7/dev/source/dubbo-spi/#m-zhdocsv27devsourcedubbo-spi
     * @see org.apache.dubbo.common.extension.ExtensionLoaderTest
     *
     * Dubbo SPI 自适应扩展
     * https://dubbo.apache.org/zh/docs/v2.7/dev/source/adaptive-extension/
     * @see org.apache.dubbo.common.extension.ExtensionLoader_Adaptive_Test
     *
     *
     * Dubbo IOC
     * @see org.apache.dubbo.common.extension.ExtensionLoaderTest#testInjectExtension()
     *
     * Dubbo AOP
     * @see org.apache.dubbo.common.extension.ExtensionLoaderTest#test_getExtension_WithWrapper()
     *
     * Dubbo Activate
     * @see org.apache.dubbo.common.extension.ExtensionLoaderTest#testLoadActivateExtension()
     *
     * Dubbo Adaptive
     * @see org.apache.dubbo.common.extension.ExtensionLoader_Adaptive_Test
     *
     *
     *
     * 为什么要设计 adaptive？注解在类上和注解在方法上的区别？
     * https://www.cnblogs.com/histlyb/p/7717557.html
     *
     * adaptive 设计的目的是为了识别固定已知类和扩展未知类。
     *
     * 1.注解在类上：代表人工实现，实现一个装饰类（设计模式中的装饰模式），它主要作用于固定已知类，
     * 目前整个系统只有 2 个，AdaptiveCompiler、AdaptiveExtensionFactory。
     * a.为什么 AdaptiveCompiler 这个类是固定已知的？因为整个框架仅支持 Javassist 和 JdkCompiler。
     * b.为什么 AdaptiveExtensionFactory 这个类是固定已知的？因为整个框架仅支持 2 个 objFactory：一个是 spi，另一个是 spring。
     *
     * 2.注解在方法上：代表自动生成和编译一个动态的 Adpative 类，它主要是用于 SPI，因为 SPI 接口实现类是不固定、未知的扩展类，所以设计了动态 $Adaptive 类。
     * 例如 Protocol 的 SPI 类有 injvm dubbo registry filter listener等等 很多扩展未知类，
     * 它设计了 Protocol$Adaptive 的类，通过 ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(spi类);来提取对象。
     *
     * 具体实现见 {@link ExtensionLoader#getAdaptiveExtensionClass()}
     * 当 @adaptive 注解在类上时，固定只返回该类；
     * 否者判断接口方法上是否存在 @adaptive 注解，存在则创建并返回动态 $Adaptive 类。
     */

    /**
     * test03 项目依赖了 test01、test02 项目
     * 接口 DuplicateExt 在 test01、test02 项目中都具有实现类
     *
     * 测试在接口实现类存在冲突的情况下，如何通过 dubbo SPI 取到唯一值
     */
    @Test
    public void test_getExtension_delicate() throws Exception {
        ExtensionLoader<DuplicateExt> extensionLoader = ExtensionLoader.getExtensionLoader(DuplicateExt.class);
        DuplicateExt impl = extensionLoader.getExtension("impl");
        System.out.println(impl instanceof DuplicateExtImplTest01);
        System.out.println(impl instanceof DuplicateExtImplTest02);
        /**
         * @see ExtensionLoader#loadDirectory(java.util.Map, String, String, boolean, boolean, String...)
         * 代码中得到 fileName = "META-INF/dubbo/internal/org.apache.dubbo.demo.DuplicateExt"
         * 根据classLoader.getResources(fileName)，实际可以读到两份 SPI 配置文件
         * 加载第一份文件时，把实现类放在 extensionClasses Map 之中
         *
         * @see ExtensionLoader#saveInExtensionClass(java.util.Map, Class, String, boolean)
         * 加载第二份文件时，由于 extensionClasses Map 之中已有实现类，发生冲突，直接抛异常
         *
         * 后续把异常吞掉了，最终只返回 extensionClasses Map 之中加载到的第一个实现类
         */
    }
}
