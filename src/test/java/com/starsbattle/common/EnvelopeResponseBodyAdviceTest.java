package com.starsbattle.common;

import com.starsbattle.common.web.TestEnvelopeController;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests for {@link EnvelopeResponseBodyAdvice} that don't need a
 * Spring context: the allowlist decision in {@code supports()} and the
 * null-body guard in {@code beforeBodyWrite()}.
 */
class EnvelopeResponseBodyAdviceTest {

    private final EnvelopeResponseBodyAdvice advice = new EnvelopeResponseBodyAdvice();

    @Test
    void supportsReturnsTrueForControllerInBasePackage() throws NoSuchMethodException {
        Method method = TestEnvelopeController.class.getMethod("defaultMessage");
        MethodParameter returnType = new MethodParameter(method, -1);

        boolean supported = advice.supports(returnType, MappingJackson2HttpMessageConverter.class);

        assertThat(supported).isTrue();
    }

    @Test
    void supportsReturnsFalseForFrameworkControllerOutsideBasePackage() throws NoSuchMethodException {
        // BasicErrorController is Spring Boot's default handler for /error
        // (unmapped routes, uncaught container-level errors). It lives in
        // org.springframework.boot.autoconfigure.web.servlet.error, not
        // com.starsbattle, so the allowlist must exclude it — otherwise a
        // 404 on an unmapped path would be wrapped as {success:true,...}.
        Method method = BasicErrorController.class.getMethod("error", HttpServletRequest.class);
        MethodParameter returnType = new MethodParameter(method, -1);

        boolean supported = advice.supports(returnType, MappingJackson2HttpMessageConverter.class);

        assertThat(supported).isFalse();
    }

    @Test
    void beforeBodyWriteReturnsNullUnchangedForNullBody() throws NoSuchMethodException {
        Method method = TestEnvelopeController.class.getMethod("defaultMessage");
        MethodParameter returnType = new MethodParameter(method, -1);

        Object result = advice.beforeBodyWrite(null, returnType, null, null, null, null);

        assertThat(result).isNull();
    }
}
