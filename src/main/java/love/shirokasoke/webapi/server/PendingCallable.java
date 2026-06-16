package love.shirokasoke.webapi.server;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

class PendingCallable<T> {

    final Callable<T> task;
    final CountDownLatch latch;
    final AtomicReference<Exception> error;
    final AtomicReference<T> result;

    PendingCallable(Callable<T> task) {
        this.task = task;
        this.latch = new CountDownLatch(1);
        this.error = new AtomicReference<>();
        this.result = new AtomicReference<>();
    }
}
