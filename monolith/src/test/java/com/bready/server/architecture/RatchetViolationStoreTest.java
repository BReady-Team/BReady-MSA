package com.bready.server.architecture;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatchetViolationStoreTest {

    private static final List<String> VIOLATIONS = List.of("옛 위반 한 줄");

    @TempDir
    Path storeDirectory;

    @Test
    @DisplayName("실패 - 기준선에 없는 규칙을 저장하려 하면 기준선을 만들지 않고 멈춘다")
    void save_unknownRule() {
        RatchetViolationStore store = storeWith(false);

        assertThatThrownBy(() -> store.save(ruleDescribedAs("처음 보는 규칙"), VIOLATIONS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("기준선에 없는 규칙")
                .hasMessageContaining("처음 보는 규칙");
        assertThat(store.contains(ruleDescribedAs("처음 보는 규칙"))).isFalse();
    }

    @Test
    @DisplayName("실패 - 이미 있는 규칙이라도 설명 문구가 달라지면 없는 규칙으로 본다")
    void save_changedDescription() {
        storeWith(true).save(ruleDescribedAs("규칙 (문서 2번)"), VIOLATIONS);
        RatchetViolationStore store = storeWith(false);

        assertThatThrownBy(() -> store.save(ruleDescribedAs("규칙"), VIOLATIONS))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("성공 - 이미 기준선이 있는 규칙은 위반 목록을 갱신할 수 있다")
    void save_knownRule() {
        ArchRule rule = ruleDescribedAs("이미 있는 규칙");
        storeWith(true).save(rule, List.of("옛 위반 1", "옛 위반 2"));
        RatchetViolationStore store = storeWith(false);

        store.save(rule, List.of("옛 위반 1"));

        assertThat(store.getViolations(rule)).containsExactly("옛 위반 1");
    }

    @Test
    @DisplayName("성공 - 새 규칙을 허용하면 기준선을 만든다")
    void save_unknownRuleWhenAllowed() {
        RatchetViolationStore store = storeWith(true);
        ArchRule rule = ruleDescribedAs("새로 추가한 규칙");

        store.save(rule, VIOLATIONS);

        assertThat(store.contains(rule)).isTrue();
        assertThat(store.getViolations(rule)).containsExactlyElementsOf(VIOLATIONS);
    }

    private RatchetViolationStore storeWith(boolean newRulesAllowed) {
        Properties properties = new Properties();
        properties.setProperty("default.path", storeDirectory.toString());
        properties.setProperty("default.allowStoreCreation", "true");
        properties.setProperty(RatchetViolationStore.ALLOW_NEW_RULES_PROPERTY, String.valueOf(newRulesAllowed));

        RatchetViolationStore store = new RatchetViolationStore();
        store.initialize(properties);
        return store;
    }

    private ArchRule ruleDescribedAs(String description) {
        return noClasses().should().bePublic().as(description);
    }
}
