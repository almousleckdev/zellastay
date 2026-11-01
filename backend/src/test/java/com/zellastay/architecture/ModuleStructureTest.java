package com.zellastay.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.zellastay.ZellaStayApplication;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModuleStructureTest {

  private static final Path CONTRACT = Path.of("../docs/architecture/MODULAR_ARCHITECTURE.md");
  private static final String SHARED = "shared";

  private final ApplicationModules modules = ApplicationModules.of(ZellaStayApplication.class);

  @Test
  void modulithVerificationPasses() {
    modules.verify();
  }

  @Test
  void documentedModulesAreExactlyTheCodeModules() throws IOException {
    Set<String> inCode =
        modules.stream()
            .map(module -> module.getIdentifier().toString())
            .collect(Collectors.toCollection(TreeSet::new));
    Set<String> inDocs = new TreeSet<>(documentedGraph().keySet());
    inDocs.add(SHARED);

    assertThat(inCode).hasSize(22).isEqualTo(inDocs);
  }

  @Test
  void allowedDependenciesDeclaredInCodeEqualTheDocumentedContract() throws Exception {
    Map<String, Set<String>> documented = documentedGraph();

    for (Map.Entry<String, Set<String>> module : documented.entrySet()) {
      assertThat(declaredDependencies(module.getKey()))
          .as("allowed dependencies of module %s", module.getKey())
          .isEqualTo(module.getValue());
    }
    ApplicationModule shared =
        Class.forName("com.zellastay.shared.package-info").getAnnotation(ApplicationModule.class);
    assertThat(shared.type()).isEqualTo(ApplicationModule.Type.OPEN);
  }

  @Test
  void documentedGraphIsAcyclicAndRespectsLayers() throws IOException {
    Map<String, Set<String>> graph = documentedGraph();
    Map<String, Integer> layers = documentedLayers();

    assertThat(layers.keySet()).containsAll(graph.keySet());
    graph.forEach(
        (module, dependencies) ->
            dependencies.forEach(
                dependency ->
                    assertThat(layers.get(dependency))
                        .as(
                            "%s (L%d) must not depend on higher layer %s",
                            module, layers.get(module), dependency)
                        .isLessThanOrEqualTo(layers.get(module))));

    Set<String> visiting = new HashSet<>();
    Set<String> done = new HashSet<>();
    graph.keySet().forEach(module -> assertNoCycle(module, graph, visiting, done));
  }

  @Test
  void writesModuleDocumentation() throws IOException {
    Path output = Path.of("build/spring-modulith-docs");

    new Documenter(modules, Documenter.Options.defaults().withOutputFolder(output.toString()))
        .writeModulesAsPlantUml()
        .writeIndividualModulesAsPlantUml();

    try (var files = Files.list(output)) {
      assertThat(files.filter(file -> file.toString().endsWith(".puml")).count()).isPositive();
    }
  }

  private static void assertNoCycle(
      String module, Map<String, Set<String>> graph, Set<String> visiting, Set<String> done) {
    if (done.contains(module)) {
      return;
    }
    assertThat(visiting.add(module)).as("cycle through %s", module).isTrue();
    graph
        .getOrDefault(module, Set.of())
        .forEach(next -> assertNoCycle(next, graph, visiting, done));
    visiting.remove(module);
    done.add(module);
  }

  private static Set<String> declaredDependencies(String module) throws Exception {
    Class<?> packageInfo = Class.forName("com.zellastay." + module + ".package-info");
    ApplicationModule annotation = packageInfo.getAnnotation(ApplicationModule.class);
    assertThat(annotation).as("@ApplicationModule on %s", module).isNotNull();
    return Arrays.stream(annotation.allowedDependencies())
        .map(dependency -> dependency.replace("::api", ""))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static Map<String, Set<String>> documentedGraph() throws IOException {
    String section = contractBlock("(?s)## 4\\. Allowed dependency graph.*?```\\n(.*?)```");
    Map<String, Set<String>> graph = new TreeMap<>();
    Pattern line = Pattern.compile("^(\\w+)\\s*:?\\s*(?:→|:)\\s*(.*)$");
    for (String raw : section.split("\n")) {
      Matcher matcher = line.matcher(raw.trim());
      if (!matcher.matches() || matcher.group(1).equals(SHARED)) {
        continue;
      }
      Set<String> dependencies =
          Arrays.stream(matcher.group(2).replaceAll("\\(.*?\\)", "").split(","))
              .map(String::trim)
              .filter(value -> !value.isEmpty())
              .collect(Collectors.toCollection(TreeSet::new));
      graph.put(matcher.group(1), dependencies);
    }
    return graph;
  }

  private static Map<String, Integer> documentedLayers() throws IOException {
    String section = contractBlock("(?s)Layering \\(a module.*?```\\n(.*?)```");
    Map<String, Integer> layers = new HashMap<>();
    Pattern line = Pattern.compile("^L(\\d+)\\s+(.*)$");
    for (String raw : section.split("\n")) {
      Matcher matcher = line.matcher(raw.trim());
      if (matcher.matches()) {
        List<String> names = Arrays.stream(matcher.group(2).split(",")).map(String::trim).toList();
        names.forEach(name -> layers.put(name, Integer.parseInt(matcher.group(1))));
      }
    }
    return layers;
  }

  private static String contractBlock(String regex) throws IOException {
    Matcher matcher = Pattern.compile(regex).matcher(Files.readString(CONTRACT));
    assertThat(matcher.find()).as("contract block %s", regex).isTrue();
    return matcher.group(1);
  }
}
