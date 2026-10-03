package ru.mecotrade.kidtracker.security;
import org.junit.jupiter.api.Test;
import ru.mecotrade.kidtracker.task.*;
import static org.junit.jupiter.api.Assertions.*;
class PendingConfirmationCapacityTest {
    @Test void oneUserCannotFillPendingConfirmationStorage() throws Exception {
        JobExecutor jobs = new JobExecutor();
        for (int i = 0; i < 20; i++) jobs.apply(UserToken.of(1L, "t" + i), () -> {});
        assertThrows(IllegalStateException.class, () -> jobs.apply(UserToken.of(1L, "excess"), () -> {}));
        assertDoesNotThrow(() -> jobs.apply(UserToken.of(2L, "independent"), () -> {}));
        jobs.execute(UserToken.of(1L, "t0"), 60000);
        assertDoesNotThrow(() -> jobs.apply(UserToken.of(1L, "replacement"), () -> {}));
    }
}
