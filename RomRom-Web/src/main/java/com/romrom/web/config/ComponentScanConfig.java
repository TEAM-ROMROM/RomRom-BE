package com.romrom.web.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

// 컴포넌트 스캔 설정
@Configuration
@ComponentScan(basePackages = {
    "com.romrom.common",
    "com.romrom.member",
    "com.romrom.auth",
    "com.romrom.item",
    "com.romrom.ai",
    "com.romrom.report",
    "com.romrom.notification",
    "com.romrom.application",
    "com.romrom.web"
})
public class ComponentScanConfig {
}