package app.lightmove.api.notetaker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The backup recorder's webhook as the controller sees it. */
@Service
@RequiredArgsConstructor
public class NotetakerBackupWebhook {

    private final NotetakerBackups backups;

    public void receive(byte[] body, String id, String timestamp, String signature) {
        backups.receive(body, id, timestamp, signature);
    }
}
