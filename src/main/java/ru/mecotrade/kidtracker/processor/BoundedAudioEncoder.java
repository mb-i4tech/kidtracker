package ru.mecotrade.kidtracker.processor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator;

/** No shell, probe subprocess, network inputs, unbounded queues or stderr buffering. */
final class BoundedAudioEncoder {
    static final int MAX_OUTPUT = 2 * 1024 * 1024;
    private static final Semaphore SLOTS = new Semaphore(2);
    static void encode(Path source, Path target, String codec, int bitrate, int samplingRate, String format)
            throws IOException, InterruptedException {
        if (!SLOTS.tryAcquire()) throw new IOException("Audio conversion capacity reached");
        try {
            run(List.of(new DefaultFFMPEGLocator().getExecutablePath(), "-nostdin", "-v", "error", "-y",
                    "-protocol_whitelist", "file", "-threads", "1", "-i", source.toString(),
                    "-vn", "-threads", "1", "-acodec", codec, "-b:a", Integer.toString(bitrate),
                    "-ac", "1", "-ar", Integer.toString(samplingRate), "-fs", Integer.toString(MAX_OUTPUT),
                    "-f", format, target.toString()), 30, TimeUnit.SECONDS);
        } finally { SLOTS.release(); }
    }
    static void run(List<String> command, long timeout, TimeUnit unit) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (!process.waitFor(timeout, unit)) throw new IOException("Audio conversion timed out");
            if (process.exitValue() != 0) throw new IOException("Audio conversion failed");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }
}
