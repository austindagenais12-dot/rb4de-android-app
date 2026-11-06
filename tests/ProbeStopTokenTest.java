package dev.rb4dx.standalone;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;

public final class ProbeStopTokenTest {
    public static void main(String[] args) {
        ProbeStopToken earlyStop = new ProbeStopToken();
        AtomicInteger earlyCalls = new AtomicInteger();
        if (!earlyStop.requestStop() || !earlyStop.isStopped()) {
            throw new AssertionError("early Stop not recorded");
        }
        earlyStop.onStop(earlyCalls::incrementAndGet);
        if (earlyCalls.get() != 1 || earlyStop.requestStop()) {
            throw new AssertionError("pre-launch stop must run late cleanup exactly once");
        }

        ProbeStopToken normalStop = new ProbeStopToken();
        AtomicInteger socketStops = new AtomicInteger();
        AtomicInteger processStops = new AtomicInteger();
        normalStop.onStop(socketStops::incrementAndGet);
        normalStop.onStop(processStops::incrementAndGet);
        if (normalStop.isStopped() || !normalStop.requestStop()) {
            throw new AssertionError("Stop failed to change active session state");
        }
        normalStop.requestStop();
        if (socketStops.get() != 1 || processStops.get() != 1) {
            throw new AssertionError("Stop must signal both resources only once");
        }
        for (int run = 0; run < 200; run++) {
            ProbeStopToken race = new ProbeStopToken();
            AtomicInteger calls = new AtomicInteger();
            CountDownLatch gate = new CountDownLatch(1);
            Thread register = new Thread(() -> {
                await(gate);
                race.onStop(calls::incrementAndGet);
            });
            Thread cancel = new Thread(() -> {
                await(gate);
                race.requestStop();
            });
            register.start();
            cancel.start();
            gate.countDown();
            try {
                register.join(1000);
                cancel.join(1000);
            } catch (InterruptedException unexpected) {
                throw new AssertionError(unexpected);
            }
            if (register.isAlive() || cancel.isAlive() || calls.get() != 1) {
                throw new AssertionError("Stop/registration race lost or repeated action");
            }
        }
        System.out.println("PASS: cancellation before launch and idempotent multi-resource Stop");
    }

    private static void await(CountDownLatch gate) {
        try {
            gate.await();
        } catch (InterruptedException unexpected) {
            throw new AssertionError(unexpected);
        }
    }
}
