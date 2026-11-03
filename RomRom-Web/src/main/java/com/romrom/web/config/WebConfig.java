package com.romrom.web.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    // 활동 이벤트 수집 대상: 앱 API (관리자 API 자기 호출 제외)
    private static final String ACTIVITY_TRACKED_PATH_PATTERN = "/api/**";
    private static final String ACTIVITY_EXCLUDED_ADMIN_PATH_PATTERN = "/api/admin/**";

    private final ApiCallActivityInterceptor apiCallActivityInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiCallActivityInterceptor)
                .addPathPatterns(ACTIVITY_TRACKED_PATH_PATTERN)
                .excludePathPatterns(ACTIVITY_EXCLUDED_ADMIN_PATH_PATTERN);
    }

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