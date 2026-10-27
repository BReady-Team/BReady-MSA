package com.bready.catalog.architecture;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import com.bready.catalog.global.exception.ApplicationException;
import com.bready.catalog.global.exception.ErrorCase;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = ArchitectureTest.BASE_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String BASE_PACKAGE = "com.bready.catalog";

    @ArchTest
    static final ArchRule controllers_should_not_use_repositories = noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..")
            .because("컨트롤러는 서비스에 위임만 한다");

    @ArchTest
    static final ArchRule services_and_repositories_should_not_depend_on_controllers = noClasses()
            .that()
            .resideInAnyPackage("..service..", "..repository..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..controller..")
            .because("의존은 controller → service → repository 한 방향이다");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_outer_layers = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..dto..", "..service..", "..controller..", "..external..")
            .because("의존은 바깥 → domain 한쪽뿐이다");

    @ArchTest
    static final ArchRule global_should_not_depend_on_place = noClasses()
            .that()
            .resideInAPackage(BASE_PACKAGE + ".global..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(BASE_PACKAGE + ".place..")
            .because("global은 기술 공통만 담는다");

    @ArchTest
    static final ArchRule no_field_injection =
            GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.because("생성자 주입만 쓴다 (@RequiredArgsConstructor)");

    @ArchTest
    static final ArchRule no_standard_streams =
            GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.because("출력은 로거로만 한다 (logging.md)");

    @ArchTest
    static final ArchRule application_exception_should_be_created_with_from = noClasses()
            .that()
            .doNotHaveFullyQualifiedName(ApplicationException.class.getName())
            .should()
            .callConstructor(ApplicationException.class, ErrorCase.class, Throwable.class)
            .because("예외는 ApplicationException.from(...)으로만 만든다");

    @ArchTest
    static final ArchRule domain_should_not_throw_standard_exceptions = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .callConstructor(IllegalArgumentException.class, String.class)
            .orShould()
            .callConstructor(IllegalStateException.class, String.class)
            .because("도메인 규칙 위반은 ErrorCase로 던진다. 표준 예외는 500이 된다");

    @ArchTest
    static final ArchRule domain_and_services_should_use_clock = noClasses()
            .that()
            .resideInAnyPackage("..domain..", "..service..")
            .should()
            .callMethod(LocalDateTime.class, "now")
            .orShould()
            .callMethod(LocalDate.class, "now")
            .because("현재 시각은 Clock에서 얻고 엔티티는 파라미터로 받는다");

    @ArchTest
    static final ArchRule entities_should_not_have_public_no_arg_constructor = constructors()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(Entity.class)
            .and()
            .haveRawParameterTypes(new Class<?>[0])
            .should()
            .notBePublic()
            .because("엔티티는 @NoArgsConstructor(access = PROTECTED) + 정적 팩토리로만 만든다");

    @ArchTest
    static final ArchRule entities_should_not_have_public_setters = methods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(Entity.class)
            .and()
            .haveNameMatching("set[A-Z].*")
            .should()
            .notBePublic()
            .because("상태 변경은 의도를 드러내는 메서드로 한다");

    @ArchTest
    static final ArchRule entity_enums_should_be_mapped_as_string = fields().that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(Entity.class)
            .and()
            .haveRawType(DescribedPredicate.describe("enum", JavaClass::isEnum))
            .should()
            .beAnnotatedWith(Enumerated.class)
            .because("ORDINAL은 enum 순서가 바뀌면 데이터가 깨진다");

    @ArchTest
    static final ArchRule transactional_should_not_be_on_private_methods = methods()
            .that()
            .areAnnotatedWith(Transactional.class)
            .should()
            .notBePrivate()
            .because("프록시를 거치지 않는 private 메서드에서는 @Transactional이 무시된다");

    @ArchTest
    static final ArchRule repositories_should_be_used_only_by_services = classes()
            .that()
            .resideInAPackage("..repository..")
            .should()
            .onlyBeAccessed()
            .byAnyPackage("..service..", "..repository..")
            .because("리포지토리는 자기 모듈의 서비스에서만 쓴다");

    @ArchTest
    static final ArchRule dto_should_be_request_or_response_records = classes()
            .that()
            .resideInAPackage("..dto..")
            .and()
            .areTopLevelClasses()
            .should(beRecordNamedRequestOrResponse())
            .because("DTO는 {Resource}{Action}Request/Response record다 (dto.md 1·2번)");

    @ArchTest
    static final ArchRule public_controllers_should_implement_api_interface = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .and()
            .haveSimpleNameNotEndingWith("InternalController")
            .should(implementMatchingApiInterface())
            .because("Swagger 문서는 {Domain}Api 인터페이스에 둔다");

    @ArchTest
    static final ArchRule error_codes_should_be_unique = classes()
            .that()
            .implement(ErrorCase.class)
            .and()
            .areEnums()
            .should(haveUniqueErrorCodes())
            .because("에러 코드는 서비스 안에서 유일해야 한다");

    private static ArchCondition<JavaClass> beRecordNamedRequestOrResponse() {
        return new ArchCondition<>("be a record named *Request or *Response") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String name = javaClass.getSimpleName();
                boolean named = name.endsWith("Request") || name.endsWith("Response");
                if (!javaClass.reflect().isRecord() || !named) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass, javaClass.getName() + " is not a record named *Request/*Response"));
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

    private static ArchCondition<JavaClass> haveUniqueErrorCodes() {
        return new ArchCondition<>("have unique error codes") {
            private final Map<Integer, List<String>> ownersByCode = new HashMap<>();

            @Override
            public void init(Collection<JavaClass> errorCaseEnums) {
                ownersByCode.clear();
                for (JavaClass errorCaseEnum : errorCaseEnums) {
                    for (Object constant : errorCaseEnum.reflect().getEnumConstants()) {
                        ownersByCode
                                .computeIfAbsent(((ErrorCase) constant).getErrorCode(), ignored -> new ArrayList<>())
                                .add(errorCaseEnum.getSimpleName() + "." + constant);
                    }
                }
            }

            @Override
            public void check(JavaClass errorCaseEnum, ConditionEvents events) {
                for (Object constant : errorCaseEnum.reflect().getEnumConstants()) {
                    Integer code = ((ErrorCase) constant).getErrorCode();
                    if (ownersByCode.get(code).size() > 1) {
                        events.add(SimpleConditionEvent.violated(
                                errorCaseEnum, "code " + code + " is shared by " + ownersByCode.get(code)));
                    }
                }
            }
        };
    }
}
