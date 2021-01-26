package org.apache.dubbo.demo.provider;

import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.demo.AnimalService;
import org.apache.dubbo.rpc.RpcContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Sumkor
 * @since 2021/1/26
 */
@DubboService
public class AnimalServiceImpl implements AnimalService {

    private static final Logger logger = LoggerFactory.getLogger(AnimalServiceImpl.class);

    @Override
    public String eat(String food) {
        logger.info("eating " + food + ", request from consumer: " + RpcContext.getContext().getRemoteAddress());
        return "haha";
    }
}
