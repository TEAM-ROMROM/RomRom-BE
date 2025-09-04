package com.romrom.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Admin 정적 리소스 (CSS, JS, 이미지) 매핑
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");
                
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");
                
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/");
                
        registry.addResourceHandler("/plugins/**")
                .addResourceLocations("classpath:/static/plugins/");
    }
}