package ru.mecotrade.kidtracker.task;
import org.junit.jupiter.api.Test;
import ru.mecotrade.kidtracker.exception.KidTrackerInvalidTokenException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class JobExecutorTest {
    @Test void tokenIsBoundToUserAndSingleUse() throws Exception {
        JobExecutor jobs = new JobExecutor(); AtomicInteger executed = new AtomicInteger();
        UserToken owner = UserToken.of(1L, "012345"); jobs.apply(owner, executed::incrementAndGet);
        assertThrows(KidTrackerInvalidTokenException.class, () -> jobs.execute(UserToken.of(2L, "012345"), 60000));
        jobs.execute(owner, 60000); assertEquals(1, executed.get());
        assertThrows(KidTrackerInvalidTokenException.class, () -> jobs.execute(owner, 60000));
    }
    @Test void expiredTokenNeverRuns() {
        JobExecutor jobs = new JobExecutor(); UserToken token = UserToken.of(1L, "012345");
        jobs.apply(token, () -> fail("expired job ran"));
        assertThrows(KidTrackerInvalidTokenException.class, () -> jobs.execute(token, 0));
        assertThrows(KidTrackerInvalidTokenException.class, () -> jobs.execute(token, 60000));
    }
    @Test void collisionDoesNotReplacePendingOwnershipOperation() throws Exception {
        JobExecutor jobs = new JobExecutor(); UserToken token = UserToken.of(1L, "012345");
        AtomicInteger count = new AtomicInteger(); jobs.apply(token, count::incrementAndGet);
        assertThrows(IllegalStateException.class, () -> jobs.apply(token, () -> fail("replaced")));
        jobs.execute(token, 60000); assertEquals(1, count.get());
    }
    @Test void concurrentConfirmationCannotRunJobTwice() throws Exception {
        JobExecutor jobs = new JobExecutor(); UserToken token = UserToken.of(1L, "012345");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        jobs.apply(token, () -> { entered.countDown(); try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new AssertionError(e); } });
        try {
            Future<?> first = worker.submit(() -> { try { jobs.execute(token, 60000); } catch (Exception e) { throw new AssertionError(e); } });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertThrows(KidTrackerInvalidTokenException.class, () -> jobs.execute(token, 60000));
            release.countDown(); first.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); worker.shutdownNow(); }
    }
}
