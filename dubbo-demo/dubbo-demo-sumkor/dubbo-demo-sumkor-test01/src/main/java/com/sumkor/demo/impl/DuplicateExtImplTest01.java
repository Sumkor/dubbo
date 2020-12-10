package com.sumkor.demo.impl;

import org.apache.dubbo.demo.DuplicateExt;

/**
 * @author Sumkor
 * @since 2020/12/10
 */
public class DuplicateExtImplTest01 implements DuplicateExt {

    @Override
    public String hello(String name) {
        return "I am DuplicateExtImplTest01";
    }
}
