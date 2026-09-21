package com.facimus.procesos.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/** Los tests que levantan la aplicacion completa eligen su perfil en vez de heredar el de por defecto. */
class PerfilesDePruebaTest {

    @Test
    @DisplayName("Todo @SpringBootTest declara su perfil con @ActiveProfiles")
    void springBootTests_declaranSuPerfil() {
        JavaClasses tests = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("com.facimus.procesos");

        classes()
                .that().areAnnotatedWith(SpringBootTest.class)
                .should().beAnnotatedWith(ActiveProfiles.class)
                .because("sin perfil arrancarian en dev, que siembra la tienda demo y escribe en ./data")
                .check(tests);
    }
}
