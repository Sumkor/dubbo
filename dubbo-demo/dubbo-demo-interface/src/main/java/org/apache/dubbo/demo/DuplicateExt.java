package org.apache.dubbo.demo;

import org.apache.dubbo.common.extension.SPI;

/**
 * @author Sumkor
 * @since 2020/12/9
 */
@SPI
public interface DuplicateExt {

    String hello(String name);
}
