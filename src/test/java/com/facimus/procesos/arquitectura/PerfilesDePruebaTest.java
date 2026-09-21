package com.facimus.procesos.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Los tests que levantan la aplicacion completa eligen su perfil en vez de heredar el de por defecto. */
@AnalyzeClasses(packages = "com.facimus.procesos", importOptions = ImportOption.OnlyIncludeTests.class)
class PerfilesDePruebaTest {

    @ArchTest
    static final ArchRule springBootTests_declaranSuPerfil = classes()
            .that().areAnnotatedWith(SpringBootTest.class)
            .should().beAnnotatedWith(ActiveProfiles.class)
            .because("sin perfil arrancarian en dev, que siembra la tienda demo y escribe en ./data");
}
