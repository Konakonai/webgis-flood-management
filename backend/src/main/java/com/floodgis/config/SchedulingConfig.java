package com.floodgis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务配置
 * 用于模拟监测数据生成和 WebSocket 推送
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
