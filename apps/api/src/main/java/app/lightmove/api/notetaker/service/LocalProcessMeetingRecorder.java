package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.notetaker.model.NotetakerLaunch;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code npm run dev}'s runner: the bot as a process on this machine, its output in
 * {@code .data/notetaker/<run id>.log}. Never in a deployment: the API's instance would host every call's browser.
 */
@RequiredArgsConstructor
@Slf4j
public class LocalProcessMeetingRecorder implements MeetingRecorder {

    /** What the bot needs from this machine to find node and Chromium. */
    static final List<String> INHERITED = List.of("PATH", "HOME", "CHROME_PATH", "TMPDIR", "LANG");

    private final LightMoveProperties properties;
    private final Path workingDirectory;

    @Override
    public String launch(NotetakerLaunch launch) {
        try {
            Path logs = workingDirectory.resolve(".data/notetaker");
            Files.createDirectories(logs);
            File output = logs.resolve(launch.recordingId() + ".log").toFile();
            ProcessBuilder builder = new ProcessBuilder(List.of(properties.notetaker().localCommand().split("\\s+")))
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(output);
            // The API's own environment holds its database password and every vendor key; the bot gets none of it.
            Map<String, String> environment = builder.environment();
            environment.clear();
            INHERITED.forEach(name -> {
                String value = System.getenv(name);
                if (value != null) {
                    environment.put(name, value);
                }
            });
            environment.putAll(NotetakerBotEnvironment.of(launch, properties));
            Process process = builder.start();
            log.info("Notetaker run {} started locally as process {}, logging to {}", launch.recordingId(),
                    process.pid(), output);
            return "local-" + process.pid();
        } catch (IOException failed) {
            throw new IllegalStateException("The local Notetaker bot could not be started", failed);
        }
    }

    /** The bot hears it on its next heartbeat. */
    @Override
    public void requestLeave(String externalId) {
    }
}
