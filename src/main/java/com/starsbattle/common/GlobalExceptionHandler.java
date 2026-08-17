package com.starsbattle.common;

import com.starsbattle.common.exception.ApiException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global error envelope (design: "Response Envelope" mapping table). Every
 * branch emits {@code {success:false, message, data:null}} with the mapped
 * HTTP status. {@code OptimisticLockingFailureException} is handled here
 * (409) rather than inside a service's {@code @Transactional} method — it is
 * thrown at flush/commit as the transactional proxy unwinds and therefore
 * cannot be caught inside the method itself (design: "Data Flow — turn
 * request").
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String VALIDATION_FALLBACK_MESSAGE = "Datos invalidos";
    private static final String UNREADABLE_BODY_MESSAGE = "Campos no permitidos o cuerpo invalido";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Credenciales invalidas";
    private static final String FORBIDDEN_FALLBACK_MESSAGE = "No tienes permiso para realizar esta accion";
    private static final String OPTIMISTIC_LOCK_MESSAGE =
            "La batalla fue modificada por otra peticion, vuelve a intentarlo";
    private static final String METHOD_NOT_SUPPORTED_MESSAGE = "Metodo no soportado";
    private static final String MEDIA_TYPE_NOT_SUPPORTED_MESSAGE = "Tipo de contenido no soportado";
    private static final String TYPE_MISMATCH_MESSAGE = "Parametro invalido";
    private static final String MISSING_PARAMETER_MESSAGE = "Parametro requerido faltante";
    private static final String UNEXPECTED_ERROR_MESSAGE = "Error interno del servidor";
    private static final String NOT_FOUND_ROUTE_MESSAGE = "Recurso no encontrado";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse(VALIDATION_FALLBACK_MESSAGE);
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse(VALIDATION_FALLBACK_MESSAGE);
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, UNREADABLE_BODY_MESSAGE);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, METHOD_NOT_SUPPORTED_MESSAGE);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, MEDIA_TYPE_NOT_SUPPORTED_MESSAGE);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, TYPE_MISMATCH_MESSAGE);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, MISSING_PARAMETER_MESSAGE);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        return error(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, FORBIDDEN_FALLBACK_MESSAGE);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockFailure(OptimisticLockingFailureException ex) {
        return error(HttpStatus.CONFLICT, OPTIMISTIC_LOCK_MESSAGE);
    }

    /**
     * Thrown by Spring MVC's static-resource fallback when no
     * {@code @RequestMapping} matches AND no static resource exists either
     * — e.g. a route whose controller bean is absent under the active
     * profile, such as {@code DevResetController}'s {@code @Profile("dev")}
     * gating (spec: "Dev-Only Reset Endpoint" / "Non-dev profile"). Without
     * this explicit handler, the catch-all {@link #handleUnexpected(Exception)}
     * below would swallow it into an incorrect 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, NOT_FOUND_ROUTE_MESSAGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception reached GlobalExceptionHandler", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_MESSAGE);
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(message));
    }
}
