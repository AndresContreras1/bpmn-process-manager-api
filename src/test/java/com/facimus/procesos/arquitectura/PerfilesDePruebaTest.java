package com.facimus.procesos.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Los tests que tocan la base eligen su perfil en vez de heredar el de por defecto, que es dev. */
@AnalyzeClasses(packages = "com.facimus.procesos", importOptions = ImportOption.OnlyIncludeTests.class)
class PerfilesDePruebaTest {

    @ArchTest
    static final ArchRule springBootTests_declaranSuPerfil = classes()
            .that().areAnnotatedWith(SpringBootTest.class)
            .should().beAnnotatedWith(ActiveProfiles.class)
            .because("sin perfil arrancarian en dev, que siembra la tienda demo y escribe en ./data");

    @ArchTest
    static final ArchRule slicesDeJpa_conservanLaBaseDelPerfil = classes()
            .that().areAnnotatedWith(DataJpaTest.class)
            .should().beAnnotatedWith(ActiveProfiles.class)
            .andShould().beAnnotatedWith(AutoConfigureTestDatabase.class)
            .because("un slice de persistencia corre contra la base del perfil test, con el esquema de "
                    + "Flyway: la que pondria @DataJpaTest en su lugar no evalua las restricciones check");
}
