/* Copyright (c) 2026 Team Galacticraft. Licensed under the MIT license. */
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Month;

/** Reproduces a rebuild deleting an enum-switch helper before the JVM first needs it. */
public final class RuntimeClasspathIsolationProbe {
    // An external enum makes javac emit the same switch helper used by the meteor manager.
    private static final class DelayedSwitch {
        static String announce(Month phase) {
            return switch (phase) {
                case JANUARY -> "incoming";
                case FEBRUARY -> "began";
                default -> "quiet";
            };
        }
    }

    public static void main(String[] args) throws Exception {
        Path original = Path.of(args[0]).toAbsolutePath().normalize();
        Path runtime = Path.of(RuntimeClasspathIsolationProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (runtime.equals(original)) throw new AssertionError("Dev runtime still uses mutable build outputs");
        Path helper = original.resolve("RuntimeClasspathIsolationProbe$1.class");
        byte[] compiled = Files.readAllBytes(helper);
        try {
            Files.delete(helper);
            if (!DelayedSwitch.announce(Month.JANUARY).equals("incoming")
                    || !DelayedSwitch.announce(Month.FEBRUARY).equals("began")
                    || !DelayedSwitch.announce(Month.MARCH).equals("quiet")) {
                throw new AssertionError("Phase announcements changed");
            }
            System.out.println("RUNTIME CLASSPATH ISOLATION CHECK PASSED: enum helper loaded after original output was removed");
        } finally {
            Files.write(helper, compiled);
        }
    }
}