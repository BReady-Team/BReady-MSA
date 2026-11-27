package com.bready.recommendation.architecture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.RestController;

import com.bready.recommendation.global.exception.ApplicationException;
import com.bready.recommendation.global.exception.ErrorCase;

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
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

@AnalyzeClasses(packages = ArchitectureTest.BASE_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String BASE_PACKAGE = "com.bready.recommendation";
    private static final String RECOMMENDATION = BASE_PACKAGE + ".recommendation..";
    private static final String PLANNING = BASE_PACKAGE + ".planning..";
    private static final String CATALOG = BASE_PACKAGE + ".catalog..";

    @ArchTest
    static final ArchRule recommendation_should_use_other_services_through_api_only = noClasses()
            .that()
            .resideInAPackage(RECOMMENDATION)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    BASE_PACKAGE + ".planning.external..",
                    BASE_PACKAGE + ".planning.exception..",
                    BASE_PACKAGE + ".catalog.external..",
                    BASE_PACKAGE + ".catalog.exception..")
            .because("추천 로직은 Planning·Catalog를 api 패키지의 계약으로만 쓴다. HTTP는 어댑터가 맡는다");

    @ArchTest
    static final ArchRule clients_should_not_depend_on_recommendation = noClasses()
            .that()
            .resideInAnyPackage(PLANNING, CATALOG)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(RECOMMENDATION)
            .because("다른 서비스를 부르는 쪽 코드는 추천 로직을 모른다");

    @ArchTest
    static final ArchRule services_should_not_depend_on_controllers = noClasses()
            .that()
            .resideInAPackage("..service..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..controller..")
            .because("의존은 controller → service 한 방향이다");

    @ArchTest
    static final ArchRule global_should_not_depend_on_domains = noClasses()
            .that()
            .resideInAPackage(BASE_PACKAGE + ".global..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(RECOMMENDATION, PLANNING, CATALOG)
            .because("global은 기술 공통만 담는다");

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
    static final ArchRule error_codes_should_be_unique = classes()
            .that()
            .implement(ErrorCase.class)
            .and()
            .areEnums()
            .should(haveUniqueErrorCodes())
            .because("에러 코드는 서비스 안에서 유일해야 한다");

    @ArchTest
    static final ArchRule no_field_injection = freeze(
            GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.because("생성자 주입만 쓴다 (@RequiredArgsConstructor)"));

    @ArchTest
    static final ArchRule dto_should_be_request_or_response_records = freeze(classes()
            .that()
            .resideInAPackage("..dto..")
            .and()
            .areTopLevelClasses()
            .should(beRecordNamedRequestOrResponse())
            .because("DTO는 {Resource}{Action}Request/Response record다 (dto.md 1·2번)"));

    @ArchTest
    static final ArchRule controllers_should_implement_api_interface = freeze(classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should(implementMatchingApiInterface())
            .because("Swagger 문서는 {Domain}Api 인터페이스에 둔다 (controller-api.md 2번)"));

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
