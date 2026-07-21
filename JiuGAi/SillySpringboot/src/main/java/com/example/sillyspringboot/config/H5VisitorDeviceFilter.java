package com.example.sillyspringboot.config;

import com.example.sillyspringboot.compat.h5.service.H5VisitorDeviceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class H5VisitorDeviceFilter extends OncePerRequestFilter {

    private final H5VisitorDeviceService visitorDeviceService;

    public H5VisitorDeviceFilter(H5VisitorDeviceService visitorDeviceService) {
        this.visitorDeviceService = visitorDeviceService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (request == null || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        H5VisitorDeviceService.DeviceTouchContext context = visitorDeviceService.resolveOrIssue(request);
        if (context != null && context.deviceToken() != null && !context.deviceToken().isBlank()) {
            request.setAttribute(H5VisitorDeviceService.REQUEST_ATTR_DEVICE_TOKEN, context.deviceToken());
            if (context.deviceId() != null) {
                request.setAttribute(H5VisitorDeviceService.REQUEST_ATTR_DEVICE_ID, context.deviceId());
            }
            response.setHeader(H5VisitorDeviceService.DEVICE_TOKEN_HEADER, context.deviceToken());
        }
        filterChain.doFilter(request, response);
    }
}
