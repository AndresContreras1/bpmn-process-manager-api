package com.facimus.procesos.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/** README §18: security desacoplado de repositories. */
class SeguridadArquitecturaTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.facimus.procesos");
    }

    @Test
    @DisplayName("El paquete security no accede a repositorios: pasa por los servicios")
    void seguridad_no_depende_de_repositorios() {
        noClasses()
                .that().resideInAPackage("..security..")
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .because("README §18: security desacoplado de repositories")
                .check(clases);
    }
}
