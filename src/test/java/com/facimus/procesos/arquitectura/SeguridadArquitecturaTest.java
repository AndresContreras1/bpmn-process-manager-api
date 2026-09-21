package com.facimus.procesos.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** README §18: security desacoplado de repositories. */
@AnalyzeClasses(packages = "com.facimus.procesos", importOptions = ImportOption.DoNotIncludeTests.class)
class SeguridadArquitecturaTest {

    @ArchTest
    static final ArchRule seguridad_no_depende_de_repositorios = noClasses()
            .that().resideInAPackage("..security..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .because("README §18: security desacoplado de repositories");
}
