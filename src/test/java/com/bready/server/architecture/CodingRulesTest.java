package com.bready.server.architecture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import com.bready.server.global.exception.ApplicationException;
import com.bready.server.global.exception.ErrorCase;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;

import static com.bready.server.architecture.ArchitectureRules.BASE_PACKAGE;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

// 규칙 설명(because 포함)이 기준선 파일의 키다. 문구를 바꾸면 기준선을 다시 만들어야 한다.
@AnalyzeClasses(packages = BASE_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class CodingRulesTest {

    private static final String ENTITY = "jakarta.persistence.Entity";

    @ArchTest
    static final ArchRule no_field_injection = freeze(
            GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.because("생성자 주입만 쓴다 (@RequiredArgsConstructor)"));

    @ArchTest
    static final ArchRule no_standard_streams =
            freeze(GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.because("출력은 로거로만 한다 (logging.md)"));

    @ArchTest
    static final ArchRule entities_should_not_have_public_no_arg_constructor = freeze(constructors()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(ENTITY)
            .and(withoutParameters())
            .should()
            .notBePublic()
            .because("엔티티는 @NoArgsConstructor(access = PROTECTED) + 정적 팩토리로만 만든다 (entity.md 1번)"));

    @ArchTest
    static final ArchRule entities_should_not_have_public_setters = freeze(methods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(ENTITY)
            .and()
            .haveNameMatching("set[A-Z].*")
            .should()
            .notBePublic()
            .because("상태 변경은 의도를 드러내는 메서드로 한다 (entity.md 3번)"));

    @ArchTest
    static final ArchRule entity_enums_should_be_mapped_as_string = freeze(fields().that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(ENTITY)
            .and(ofEnumType())
            .should(beMappedAsStringEnum())
            .because("ORDINAL은 enum 순서가 바뀌면 데이터가 깨진다 (entity.md 5번)"));

    @ArchTest
    static final ArchRule transactional_should_not_be_on_private_methods = freeze(methods()
            .that()
            .areAnnotatedWith(Transactional.class)
            .should()
            .notBePrivate()
            .because("프록시를 거치지 않는 private 메서드에서는 @Transactional이 무시된다 (service-transaction.md 8번)"));

    @ArchTest
    static final ArchRule transactional_services_should_be_read_only_by_default = freeze(classes()
            .that()
            .areAnnotatedWith(Service.class)
            .and(useTransactions())
            .should(beReadOnlyAtClassLevel())
            .because("클래스는 readOnly, 쓰기 메서드만 @Transactional로 덮어쓴다 (service-transaction.md 2번)"));

    @ArchTest
    static final ArchRule application_exception_should_be_created_with_from = freeze(noClasses()
            .that()
            .doNotHaveFullyQualifiedName(ApplicationException.class.getName())
            .should()
            .callConstructor(ApplicationException.class, ErrorCase.class)
            .orShould()
            .callConstructor(ApplicationException.class, ErrorCase.class, Throwable.class)
            .because("예외는 ApplicationException.from(...)으로만 만든다 (exception.md 3번)"));

    @ArchTest
    static final ArchRule domain_should_not_throw_standard_exceptions = freeze(noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .callConstructor(IllegalArgumentException.class, String.class)
            .orShould()
            .callConstructor(IllegalStateException.class, String.class)
            .because("도메인 규칙 위반은 ErrorCase로 던진다. 표준 예외는 500이 된다 (entity.md 2번)"));

    @ArchTest
    static final ArchRule domain_and_services_should_use_clock = freeze(noClasses()
            .that()
            .resideInAnyPackage("..domain..", "..service..")
            .should()
            .callMethod(LocalDateTime.class, "now")
            .orShould()
            .callMethod(LocalDate.class, "now")
            .because("현재 시각은 Clock에서 얻고 엔티티는 파라미터로 받는다 (service-transaction.md 5번, entity.md 8번)"));

    @ArchTest
    static final ArchRule dto_should_be_request_or_response_records = freeze(classes()
            .that()
            .resideInAPackage("..dto..")
            .and()
            .areTopLevelClasses()
            .and()
            .areNotEnums()
            .should(beRecordNamedRequestOrResponse())
            .because("DTO는 {Resource}{Action}Request/Response record다 (dto.md 1·2번)"));

    @ArchTest
    static final ArchRule controllers_should_implement_api_interface = freeze(classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should(implementMatchingApiInterface())
            .because("Swagger 문서는 {Domain}Api 인터페이스에 둔다 (controller-api.md 2번)"));

    @ArchTest
    static final ArchRule error_codes_should_be_unique = freeze(classes()
            .that()
            .implement(ErrorCase.class)
            .and()
            .areEnums()
            .should(haveGloballyUniqueErrorCodes())
            .because("에러 코드는 전역에서 유일해야 한다 (exception.md 2번)"));

    private static DescribedPredicate<JavaConstructor> withoutParameters() {
        return DescribedPredicate.describe(
                "have no parameters",
                constructor -> constructor.getRawParameterTypes().isEmpty());
    }

    private static DescribedPredicate<JavaField> ofEnumType() {
        return DescribedPredicate.describe(
                "are of enum type", field -> field.getRawType().isEnum());
    }

    private static ArchCondition<JavaField> beMappedAsStringEnum() {
        return new ArchCondition<>("be annotated with @Enumerated(EnumType.STRING)") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                boolean string = field.isAnnotatedWith(Enumerated.class)
                        && field.getAnnotationOfType(Enumerated.class).value() == EnumType.STRING;
                if (!string) {
                    events.add(SimpleConditionEvent.violated(
                            field, field.getFullName() + " is not mapped with @Enumerated(EnumType.STRING)"));
                }
            }
        };
    }

    private static DescribedPredicate<JavaClass> useTransactions() {
        return DescribedPredicate.describe(
                "use @Transactional",
                javaClass -> javaClass.isAnnotatedWith(Transactional.class)
                        || javaClass.getMethods().stream()
                                .anyMatch(method -> method.isAnnotatedWith(Transactional.class)));
    }

    private static ArchCondition<JavaClass> beReadOnlyAtClassLevel() {
        return new ArchCondition<>("be annotated with @Transactional(readOnly = true) at class level") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean readOnly = javaClass.isAnnotatedWith(Transactional.class)
                        && javaClass.getAnnotationOfType(Transactional.class).readOnly();
                if (!readOnly) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass, javaClass.getName() + " is not annotated with @Transactional(readOnly = true)"));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> beRecordNamedRequestOrResponse() {
        return new ArchCondition<>("be a record named *Request or *Response") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean record = javaClass.reflect().isRecord();
                String name = javaClass.getSimpleName();
                boolean named = name.endsWith("Request") || name.endsWith("Response");
                if (!record || !named) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            javaClass.getName() + " is " + (record ? "a record" : "not a record")
                                    + (named ? "" : " and not named *Request/*Response")));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> implementMatchingApiInterface() {
        return new ArchCondition<>("implement {Domain}Api interface") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String expected = javaClass.getSimpleName().replaceFirst("Controller$", "Api");
                boolean implemented = javaClass.getRawInterfaces().stream()
                        .anyMatch(api -> api.getSimpleName().equals(expected));
                if (!implemented) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass, javaClass.getName() + " does not implement " + expected));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> haveGloballyUniqueErrorCodes() {
        return new ArchCondition<>("have globally unique error codes") {
            private final Map<String, List<String>> ownersByCode = new HashMap<>();

            @Override
            public void init(Collection<JavaClass> errorCaseEnums) {
                ownersByCode.clear();
                for (JavaClass errorCaseEnum : errorCaseEnums) {
                    for (Object constant : errorCaseEnum.reflect().getEnumConstants()) {
                        String code = String.valueOf(((ErrorCase) constant).getErrorCode());
                        ownersByCode
                                .computeIfAbsent(code, ignored -> new ArrayList<>())
                                .add(errorCaseEnum.getSimpleName() + "." + constant);
                    }
                }
            }

            @Override
            public void check(JavaClass errorCaseEnum, ConditionEvents events) {
                for (Object constant : errorCaseEnum.reflect().getEnumConstants()) {
                    String code = String.valueOf(((ErrorCase) constant).getErrorCode());
                    String self = errorCaseEnum.getSimpleName() + "." + constant;
                    List<String> others = ownersByCode.get(code).stream()
                            .filter(owner -> !owner.equals(self))
                            .toList();
                    if (!others.isEmpty()) {
                        events.add(SimpleConditionEvent.violated(
                                errorCaseEnum, self + " code " + code + " duplicates " + others));
                    }
                }
            }
        };
    }
}
