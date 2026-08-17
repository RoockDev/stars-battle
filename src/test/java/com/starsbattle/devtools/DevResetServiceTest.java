package com.starsbattle.devtools;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Pure unit proof of the reset sequence (proposal decision #1 / design:
 * "Flyway" -&gt; {@code DevResetController is @Profile("dev") +
 * @PreAuthorize("hasRole('ADMIN')") and calls flyway.clean(); flyway.migrate();}).
 * {@link Flyway} is mocked so this test never touches a real database.
 */
@ExtendWith(MockitoExtension.class)
class DevResetServiceTest {

    @Mock
    private Flyway flyway;

    @Test
    void resetCallsFlywayCleanThenMigrateInOrder() {
        DevResetService service = new DevResetService(flyway);

        service.reset();

        InOrder order = inOrder(flyway);
        order.verify(flyway).clean();
        order.verify(flyway).migrate();
        verifyNoMoreInteractions(flyway);
    }
}
