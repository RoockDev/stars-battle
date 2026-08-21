package com.starsbattle.devtools;

import com.starsbattle.common.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only reset endpoint (proposal decision #1, design D-devtools). Gated
 * by BOTH {@code @Profile("dev")} (the bean, and therefore the route, does
 * not exist under any other profile -&gt; 404) AND
 * {@code @PreAuthorize("hasRole('ADMIN')")} through the same
 * {@code SecurityConfig} filter chain every other route uses (no bypass)
 * -&gt; 403 for a non-ADMIN caller. This closes the source's security hole,
 * where the equivalent endpoints were open and unauthenticated.
 */
@RestController
@RequestMapping("/dev")
@Profile("dev")
public class DevResetController {

    private static final String RESET_SUCCESS_MESSAGE = "Datos de desarrollo reiniciados";

    private final DevResetService devResetService;

    public DevResetController(DevResetService devResetService) {
        this.devResetService = devResetService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/reset")
    public ApiResponse<Void> reset() {
        devResetService.reset();
        return ApiResponse.success(RESET_SUCCESS_MESSAGE, null);
    }
}
