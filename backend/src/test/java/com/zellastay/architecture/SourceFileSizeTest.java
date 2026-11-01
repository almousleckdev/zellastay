package com.zellastay.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceFileSizeTest {

  private static final int MAX_LINES = 300;

  @Test
  void javaSourcesStayFocusedAndSmall() throws IOException {
    assertThat(oversized(Path.of("src"), MAX_LINES)).isEmpty();
  }

  @Test
  void oversizedFilesAreDetected(@TempDir Path root) throws IOException {
    Files.writeString(root.resolve("Small.java"), "class Small {}\n");
    Files.writeString(root.resolve("Big.java"), "x\n".repeat(MAX_LINES + 1));

    assertThat(oversized(root, MAX_LINES)).containsExactly("Big.java");
  }

  private static List<String> oversized(Path root, int limit) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(path -> path.toString().endsWith(".java"))
          .filter(path -> lineCount(path) > limit)
          .map(path -> root.relativize(path).toString())
          .sorted()
          .toList();
    }
  }

  private static long lineCount(Path path) {
    try (Stream<String> lines = Files.lines(path)) {
      return lines.count();
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }
}
