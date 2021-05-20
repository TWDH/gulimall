package com.atguigu.gulimall.seckill.scheduled;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.atguigu.gulimall.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SeckillSkuScheduled {
    @Autowired
    private SeckillService seckillService;

    @Autowired
    private RedissonClient redissonClient;

    private final String upload_lock = "seckill:upload:lock";

    /**
     * 这里应该是幂等的
     *  三秒执行一次：* /3 * * * * ?
     *  8小时执行一次：0 0 0-8 * * ?
     */
    @Scheduled(cron = "*/3 * * * * ?")
    public void uploadSeckillSkuLatest3Day(){
        log.info("\n上架秒杀商品的信息");

        // 1.重复上架无需处理 加上分布式锁 状态已经更新 释放锁以后其他人才获取到最新状态
        RLock lock = redissonClient.getLock(upload_lock);
        lock.lock(10, TimeUnit.SECONDS);
        // 成功
        try {
            // 最近3天上架商品
            seckillService.uploadSeckillSkuLatest3Day();
        } finally {
            //解锁
            lock.unlock();
        }
    }
}







