package com.bready.server.architecture;

import java.util.List;

final class ArchitectureRules {

    static final String BASE_PACKAGE = "com.bready.server";

    static final List<String> MODULES =
            List.of("auth", "user", "plan", "place", "trigger", "recommendation", "stats", "s3");

    private ArchitectureRules() {}

    static String module(String name) {
        return BASE_PACKAGE + "." + name + "..";
    }

    static String[] allModules() {
        return MODULES.stream().map(ArchitectureRules::module).toArray(String[]::new);
    }

    static String[] otherModulesSubPackage(String self, String subPackage) {
        return MODULES.stream()
                .filter(name -> !name.equals(self))
                .map(name -> BASE_PACKAGE + "." + name + "." + subPackage + "..")
                .toArray(String[]::new);
    }
}
