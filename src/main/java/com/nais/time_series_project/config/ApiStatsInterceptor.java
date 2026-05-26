package com.nais.time_series_project.config;

import com.nais.time_series_project.service.RedisStatsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class ApiStatsInterceptor implements HandlerInterceptor {

    private final RedisStatsService redisStatsService;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        if (uri.startsWith("api/weather/stats")) {
            return true;
        }

        String endpoint = request.getMethod() + " " + uri;
        redisStatsService.incrementApiCallCount(endpoint);
        return true;
    }
}
