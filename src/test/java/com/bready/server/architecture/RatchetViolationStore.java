package com.bready.server.architecture;

import java.util.List;
import java.util.Properties;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.TextFileBasedViolationStore;
import com.tngtech.archunit.library.freeze.ViolationStore;

// 기준선에 없는 규칙을 만나면 기준선을 만들어 주지 않고 실패시키는 저장소
public class RatchetViolationStore implements ViolationStore {

    static final String ALLOW_NEW_RULES_PROPERTY = "allowNewRules";

    private final ViolationStore delegate = new TextFileBasedViolationStore();
    private boolean newRulesAllowed;

    @Override
    public void initialize(Properties properties) {
        newRulesAllowed = Boolean.parseBoolean(properties.getProperty(ALLOW_NEW_RULES_PROPERTY, "false"));
        delegate.initialize(properties);
    }

    @Override
    public boolean contains(ArchRule rule) {
        return delegate.contains(rule);
    }

    @Override
    public void save(ArchRule rule, List<String> violations) {
        if (!newRulesAllowed && !delegate.contains(rule)) {
            throw new IllegalStateException(
                    """
                    기준선에 없는 규칙이다. 기준선을 새로 만들지 않고 멈춘다.
                    규칙: %s
                    - 기존 규칙의 문구(because 등)를 고쳤다면: 문구를 되돌리거나, stored.rules에서 그 규칙의 줄을 새 문구로 바꾼다.
                    - 규칙을 새로 추가했다면: archunit.properties의 freeze.store.allowNewRules를 true로 바꿔 한 번 실행하고 바로 false로 돌린다.
                      만들어진 기준선의 건수가 예상과 같은지 확인한다."""
                            .formatted(rule.getDescription()));
        }
        delegate.save(rule, violations);
    }

    @Override
    public List<String> getViolations(ArchRule rule) {
        return delegate.getViolations(rule);
    }
}
