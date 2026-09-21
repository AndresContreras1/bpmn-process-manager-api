package com.facimus.procesos.arquitectura;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ADR-001: empaquetado modular por dominio (gestion + modelado).
 * Cada modulo tiene su propio paquete controller/, service/ y repository/.
 * Los servicios y repositorios viven en su paquete correspondiente.
 */
class EmpaquetadoTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.facimus.procesos");
    }

    @Test
    @DisplayName("Los @RestController viven en un paquete controller")
    void controllers_en_paquete_controller() {
        classes()
                .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should().resideInAPackage("..controller..")
                .because("ADR-001: cada modulo tiene su propio controller/")
                .check(clases);
    }

    @Test
    @DisplayName("Los @Service viven en un paquete service")
    void services_en_paquete_service() {
        classes()
                .that().areAnnotatedWith(org.springframework.stereotype.Service.class)
                .should().resideInAPackage("..service..")
                .because("ADR-001: cada modulo tiene su propio service/")
                .check(clases);
    }

    @Test
    @DisplayName("Los @Repository viven en un paquete repository")
    void repositorios_en_paquete_repository() {
        classes()
                .that().areAnnotatedWith(org.springframework.stereotype.Repository.class)
                .or().areInterfaces().and().haveSimpleNameEndingWith("Repository")
                .should().resideInAnyPackage("..repository..", "..common..")
                .because("ADR-001: cada modulo tiene su propio repository/")
                .check(clases);
    }

    @Test
    @DisplayName("Las @Entity viven en un paquete model")
    void entidades_en_paquete_model() {
        classes()
                .that().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().resideInAPackage("..model..")
                .because("ADR-001: las entidades viven en model/")
                .check(clases);
    }

    @Test
    @DisplayName("Los controllers no acceden directamente a repositorios")
    void controllers_no_usan_repositorios() {
        noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .because("Los controllers deben pasar por la capa de servicio")
                .check(clases);
    }

    @Test
    @DisplayName("El paquete modelado.model no depende de gestion.controller")
    void modelado_no_depende_de_gestion_controller() {
        noClasses()
                .that().resideInAPackage("..modelado..")
                .should().dependOnClassesThat().resideInAPackage("..gestion.controller..")
                .because("ADR-001: modelado no debe depender de controllers de gestion")
                .check(clases);
    }

    @Test
    @DisplayName("Los servicios no dependen de controllers")
    void servicios_no_dependen_de_controllers() {
        noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .because("La capa de servicio no debe conocer la capa de presentacion")
                .check(clases);
    }

    @Test
    @DisplayName("Los repositorios no dependen de controllers ni de services")
    void repositorios_no_dependen_de_capas_superiores() {
        noClasses()
                .that().resideInAPackage("..repository..")
                .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..")
                .because("La capa de persistencia no debe conocer capas superiores")
                .check(clases);
    }

    @Test
    @DisplayName("Las clases de modelo no dependen de services ni controllers")
    void modelo_no_depende_de_capas_superiores() {
        noClasses()
                .that().resideInAPackage("..model..")
                .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..")
                .because("El modelo de dominio debe ser independiente de la infraestructura")
                .check(clases);
    }

    @Test
    @DisplayName("Los DTOs viven en controller.dto")
    void dtos_en_paquete_dto() {
        classes()
                .that().haveSimpleNameEndingWith("Request")
                .or().haveSimpleNameEndingWith("Response")
                .should().resideInAnyPackage("..controller.dto..", "..common..")
                .because("Los DTOs de entrada/salida pertenecen a la capa de presentacion o common")
                .check(clases);
    }
}
