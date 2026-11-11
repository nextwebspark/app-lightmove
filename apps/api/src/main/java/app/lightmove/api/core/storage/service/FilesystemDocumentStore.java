package app.lightmove.api.core.storage.service;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Files on local disk, for {@code npm run dev} and the tests — never a deployment, where the disk dies
 * with the instance. Keys are built by the application, but a key is still refused if it would resolve
 * outside the root.
 */
public class FilesystemDocumentStore implements DocumentStore {

    private final Path root;

    public FilesystemDocumentStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, InputStream content, long sizeBytes, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        Files.copy(content, partial, StandardCopyOption.REPLACE_EXISTING);
        Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public OutputStream create(String key, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        return new FilterOutputStream(Files.newOutputStream(partial)) {
            private boolean closed;

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                out.write(bytes, offset, length);
            }

            @Override
            public void close() throws IOException {
                if (closed) {
                    return;
                }
                closed = true;
                super.close();
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
        };
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    @Override
    public long deletePrefix(String prefix) throws IOException {
        DocumentStore.requireFolderPrefix(prefix);
        Path folder = resolve(prefix);
        if (!Files.isDirectory(folder)) {
            return 0;
        }
        List<Path> deepestFirst;
        try (Stream<Path> walk = Files.walk(folder)) {
            deepestFirst = walk.sorted(Comparator.reverseOrder()).toList();
        }
        long files = 0;
        for (Path path : deepestFirst) {
            if (Files.isRegularFile(path)) {
                files++;
            }
            Files.deleteIfExists(path);
        }
        return files;
    }

    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("storage key escapes the document root: " + key);
        }
        return resolved;
    }
}
