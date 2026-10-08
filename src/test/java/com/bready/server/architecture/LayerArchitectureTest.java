package com.bready.server.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.bready.server.architecture.ArchitectureRules.BASE_PACKAGE;
import static com.bready.server.architecture.ArchitectureRules.allModules;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

// 규칙 설명(because 포함)이 기준선 파일의 키다. 문구를 바꾸면 기준선을 다시 만들어야 한다.
@AnalyzeClasses(packages = BASE_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class LayerArchitectureTest {

    @ArchTest
    static final ArchRule controllers_should_not_use_repositories = freeze(noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..")
            .because("컨트롤러는 서비스에 위임만 한다 (controller-api.md 1번)"));

    @ArchTest
    static final ArchRule services_and_repositories_should_not_depend_on_controllers = freeze(noClasses()
            .that()
            .resideInAnyPackage("..service..", "..repository..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..controller..")
            .because("의존은 controller → service → repository 한 방향이다"));

    @ArchTest
    static final ArchRule repositories_should_not_depend_on_services = freeze(noClasses()
            .that()
            .resideInAPackage("..repository..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..service..")
            .because("의존은 controller → service → repository 한 방향이다"));

    @ArchTest
    static final ArchRule domain_should_not_depend_on_dto_service_controller = freeze(noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..dto..", "..service..", "..controller..")
            .because("변환은 DTO의 from()이 맡고 의존은 dto → domain 한쪽뿐이다 (dto.md 4번)"));

    @ArchTest
    static final ArchRule global_should_not_depend_on_modules = freeze(noClasses()
            .that()
            .resideInAPackage(BASE_PACKAGE + ".global..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(allModules())
            .because("global은 기술 공통만 담는다 (msa-boundary.md 1번, exception.md 5번)"));
}
