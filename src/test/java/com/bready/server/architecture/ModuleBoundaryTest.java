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
import static com.bready.server.architecture.ArchitectureRules.contextOf;
import static com.bready.server.architecture.ArchitectureRules.otherContextsPackages;
import static com.bready.server.architecture.ArchitectureRules.otherContextsSubPackage;
import static com.bready.server.architecture.ArchitectureRules.packagesOf;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

// 경계는 패키지가 아니라 바운디드 컨텍스트 단위로 본다
// 컨텍스트마다 규칙을 따로 둬서 기준선 파일도 컨텍스트별로 나뉜다. 어느 경계가 얼마나 남았는지 파일 하나로 보인다.
// 규칙 설명(because 포함)이 기준선 파일의 키다. 문구를 바꾸면 기준선을 다시 만들어야 한다.
@AnalyzeClasses(packages = BASE_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule identity_should_not_use_other_contexts_internals = boundaryOf("identity");

    @ArchTest
    static final ArchRule planning_should_not_use_other_contexts_internals = boundaryOf("planning");

    @ArchTest
    static final ArchRule catalog_should_not_use_other_contexts_internals = boundaryOf("catalog");

    @ArchTest
    static final ArchRule recommendation_should_not_use_other_contexts_internals = boundaryOf("recommendation");

    @ArchTest
    static final ArchRule stats_should_not_use_other_contexts_internals = boundaryOf("stats");

    @ArchTest
    static final ArchRule file_should_not_use_other_contexts_internals = boundaryOf("file");

    @ArchTest
    static final ArchRule catalog_should_be_used_only_through_api = apiOnly("catalog");

    @ArchTest
    static final ArchRule identity_should_be_used_only_through_api = apiOnly("identity");

    @ArchTest
    static final ArchRule planning_should_be_used_only_through_api = apiOnly("planning");

    @ArchTest
    static final ArchRule events_should_be_created_by_owning_context = freeze(
            noClasses().should().callConstructorWhere(eventOfAnotherContext()).because("이벤트 클래스는 발행하는 컨텍스트가 소유한다"));

    private static ArchRule boundaryOf(String context) {
        String[] internals = Stream.concat(
                        Arrays.stream(otherContextsSubPackage(context, "repository")),
                        Arrays.stream(otherContextsSubPackage(context, "domain")))
                .toArray(String[]::new);

        return freeze(noClasses()
                .that()
                .resideInAnyPackage(packagesOf(context))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(internals)
                .because("다른 컨텍스트의 Repository·Entity를 직접 쓰지 않는다. ID와 공개 API로"));
    }

    private static ArchRule apiOnly(String context) {
        return freeze(noClasses()
                .that()
                .resideInAnyPackage(otherContextsPackages(context))
                .should()
                .dependOnClassesThat(resideInAnyPackage(packagesOf(context)).and(not(resideInAPackage("..api.."))))
                .because("공개 API를 연 컨텍스트는 api 패키지로만 쓴다"));
    }

    private static DescribedPredicate<JavaConstructorCall> eventOfAnotherContext() {
        return DescribedPredicate.describe("create an event class of another context", call -> {
            JavaClass event = call.getTargetOwner();
            JavaClass creator = call.getOriginOwner();
            return event.getPackageName().contains(".event")
                    && !contextOf(event.getPackageName()).equals(contextOf(creator.getPackageName()));
        });
    }
}
