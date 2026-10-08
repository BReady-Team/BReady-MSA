package com.bready.server.architecture;

import java.util.Arrays;
import java.util.stream.Stream;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.bready.server.architecture.ArchitectureRules.BASE_PACKAGE;
import static com.bready.server.architecture.ArchitectureRules.module;
import static com.bready.server.architecture.ArchitectureRules.otherModulesSubPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

// 모듈마다 규칙을 따로 둔다. 기준선 파일이 모듈별로 나뉘어 어느 모듈의 경계가 얼마나 남았는지 보인다.
// 규칙 설명(because 포함)이 기준선 파일의 키다. 문구를 바꾸면 기준선을 다시 만들어야 한다.
@AnalyzeClasses(packages = BASE_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule auth_should_not_use_other_modules_internals = boundaryOf("auth");

    @ArchTest
    static final ArchRule user_should_not_use_other_modules_internals = boundaryOf("user");

    @ArchTest
    static final ArchRule plan_should_not_use_other_modules_internals = boundaryOf("plan");

    @ArchTest
    static final ArchRule place_should_not_use_other_modules_internals = boundaryOf("place");

    @ArchTest
    static final ArchRule trigger_should_not_use_other_modules_internals = boundaryOf("trigger");

    @ArchTest
    static final ArchRule recommendation_should_not_use_other_modules_internals = boundaryOf("recommendation");

    @ArchTest
    static final ArchRule stats_should_not_use_other_modules_internals = boundaryOf("stats");

    @ArchTest
    static final ArchRule s3_should_not_use_other_modules_internals = boundaryOf("s3");

    @ArchTest
    static final ArchRule events_should_be_created_by_owning_module = freeze(noClasses()
            .should()
            .callConstructorWhere(eventOfAnotherModule())
            .because("이벤트 클래스는 발행하는 모듈이 소유한다 (service-transaction.md 6번, msa-boundary.md V5)"));

    private static ArchRule boundaryOf(String self) {
        String[] internals = Stream.concat(
                        Arrays.stream(otherModulesSubPackage(self, "repository")),
                        Arrays.stream(otherModulesSubPackage(self, "domain")))
                .toArray(String[]::new);

        return freeze(noClasses()
                .that()
                .resideInAPackage(module(self))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(internals)
                .because("다른 모듈의 Repository·Entity를 직접 쓰지 않는다. ID와 공개 API로 (msa-boundary.md 2번)"));
    }

    private static DescribedPredicate<JavaConstructorCall> eventOfAnotherModule() {
        return DescribedPredicate.describe("create an event class of another module", call -> {
            JavaClass event = call.getTargetOwner();
            JavaClass creator = call.getOriginOwner();
            return event.getPackageName().contains(".event") && !moduleOf(event).equals(moduleOf(creator));
        });
    }

    private static String moduleOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(BASE_PACKAGE + ".")) {
            return packageName;
        }
        String rest = packageName.substring(BASE_PACKAGE.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
