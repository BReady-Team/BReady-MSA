package com.bready.server.architecture;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ArchitectureRules {

    static final String BASE_PACKAGE = "com.bready.server";

    static final List<String> MODULES =
            List.of("auth", "user", "plan", "place", "trigger", "recommendation", "stats", "s3");

    // 바운디드 컨텍스트와 그 안의 패키지
    static final Map<String, List<String>> CONTEXTS = contexts();

    private ArchitectureRules() {}

    static String[] allModules() {
        return MODULES.stream().map(name -> BASE_PACKAGE + "." + name + "..").toArray(String[]::new);
    }

    static String[] packagesOf(String context) {
        return CONTEXTS.get(context).stream()
                .map(name -> BASE_PACKAGE + "." + name + "..")
                .toArray(String[]::new);
    }

    static String[] otherContextsSubPackage(String self, String subPackage) {
        return CONTEXTS.entrySet().stream()
                .filter(context -> !context.getKey().equals(self))
                .flatMap(context -> context.getValue().stream())
                .map(name -> BASE_PACKAGE + "." + name + "." + subPackage + "..")
                .toArray(String[]::new);
    }

    static String[] otherContextsPackages(String self) {
        return CONTEXTS.entrySet().stream()
                .filter(context -> !context.getKey().equals(self))
                .flatMap(context -> context.getValue().stream())
                .map(name -> BASE_PACKAGE + "." + name + "..")
                .toArray(String[]::new);
    }

    static String contextOf(String packageName) {
        if (!packageName.startsWith(BASE_PACKAGE + ".")) {
            return packageName;
        }
        String rest = packageName.substring(BASE_PACKAGE.length() + 1);
        int dot = rest.indexOf('.');
        String module = dot < 0 ? rest : rest.substring(0, dot);
        return CONTEXTS.entrySet().stream()
                .filter(context -> context.getValue().contains(module))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(module);
    }

    private static Map<String, List<String>> contexts() {
        Map<String, List<String>> contexts = new LinkedHashMap<>();
        contexts.put("identity", List.of("auth", "user"));
        contexts.put("planning", List.of("plan", "trigger"));
        contexts.put("catalog", List.of("place"));
        contexts.put("recommendation", List.of("recommendation"));
        contexts.put("stats", List.of("stats"));
        contexts.put("file", List.of("s3"));
        return contexts;
    }
}
