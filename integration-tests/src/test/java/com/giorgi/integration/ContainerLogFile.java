package com.giorgi.integration;

import org.testcontainers.containers.output.OutputFrame;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

public class ContainerLogFile implements Consumer<OutputFrame> {

    private final Path file;

    public ContainerLogFile(Path file) {
        this.file = file;
        try {
            Files.createDirectories(file.getParent());
            Files.deleteIfExists(file);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Override
    public synchronized void accept(OutputFrame frame) {
        try {
            Files.writeString(file, frame.getUtf8String(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
