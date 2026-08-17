package com.starsbattle.common;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Wraps every successful controller response in {@link ApiResponse} (design:
 * "Response Envelope"). {@code supports()} opts out for
 * {@link StringHttpMessageConverter} — writing an {@code ApiResponse} through
 * it throws {@code ClassCastException} — and for actuator endpoints, whose
 * bodies are not application DTOs. Bodies that are already an
 * {@link ApiResponse} pass through unchanged so this advice is idempotent.
 */
@ControllerAdvice
public class EnvelopeResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    static final String DEFAULT_SUCCESS_MESSAGE = "Operación realizada con éxito";

    private static final String ACTUATOR_PACKAGE_PREFIX = "org.springframework.boot.actuate";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (StringHttpMessageConverter.class.isAssignableFrom(converterType)) {
            return false;
        }
        String packageName = returnType.getContainingClass().getPackageName();
        return !packageName.startsWith(ACTUATOR_PACKAGE_PREFIX);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof ApiResponse<?>) {
            return body;
        }
        ApiMessage apiMessage = returnType.getMethodAnnotation(ApiMessage.class);
        String message = apiMessage != null ? apiMessage.value() : DEFAULT_SUCCESS_MESSAGE;
        return ApiResponse.success(message, body);
    }
}
