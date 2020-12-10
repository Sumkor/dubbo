package com.sumkor.demo;

import com.sumkor.demo.impl.DuplicateExtImplTest01;
import com.sumkor.demo.impl.DuplicateExtImplTest02;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.demo.DuplicateExt;
import org.junit.jupiter.api.Test;

/**
 * @author Sumkor
 * @since 2020/12/10
 */
public class ExtensionLoaderTest {

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
