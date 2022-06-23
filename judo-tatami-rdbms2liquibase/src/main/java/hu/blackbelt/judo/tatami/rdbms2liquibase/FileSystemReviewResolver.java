package hu.blackbelt.judo.tatami.rdbms2liquibase;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

@AllArgsConstructor
public class FileSystemReviewResolver implements ReviewResolver {

    private final File root;

    @Override
    public boolean exists(String name) {
        return new File(root, name).exists();
    }

    @Override
    public String resolve(String name) {
        return readLines(new File(root, name));
    }

    @SneakyThrows(IOException.class)
    private static String readLines(File file) {
        StringBuilder contentBuilder = new StringBuilder();

        try (Stream<String> stream = Files.lines( file.toPath(), StandardCharsets.UTF_8)) {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
        }
        return contentBuilder.toString();
    }
}
