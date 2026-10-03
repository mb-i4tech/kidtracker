/*
 * Copyright 2020 Sergey Shadchin (sergei.shadchin@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.mecotrade.kidtracker.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.util.UriTemplate;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@Slf4j
public class UserDeviceFilter extends GenericFilterBean {

    private final static UriTemplate URI_TEMPLATE = new UriTemplate("/api/device/{deviceId}/{:.*}");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;

        String uri = httpServletRequest.getRequestURI().substring(httpServletRequest.getContextPath().length());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (uri.startsWith("/api/device/") && (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal))) {
            httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {

            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();


            Map<String, String> uriParams = URI_TEMPLATE.match(uri);
            String deviceId = uriParams.get("deviceId");
            if (deviceId != null && userPrincipal.getUserInfo().getKids().stream().noneMatch(k -> k.getDevice().getId().equals(deviceId))) {
                log.warn("User {} attempts to access unauthorized device {} in request {}", userPrincipal.getUserInfo().getUsername(), deviceId, uri);
                throw new org.springframework.security.access.AccessDeniedException("Device access denied");
            }
        }

        chain.doFilter(request, response);
    }
}
