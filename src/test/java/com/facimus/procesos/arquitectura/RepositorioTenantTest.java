package com.facimus.procesos.arquitectura;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.repository.EmpresaRepository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ADR-002: todo repositorio de entidades tenant debe extender RepositorioTenant.
 * EmpresaRepository es la unica excepcion (Empresa es la raiz de tenencia).
 */
class RepositorioTenantTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.facimus.procesos");
    }

    @Test
    @DisplayName("Todo *Repository excepto EmpresaRepository extiende RepositorioTenant")
    void repositorios_extienden_RepositorioTenant() {
        classes()
                .that().haveSimpleNameEndingWith("Repository")
                .and().areInterfaces()
                .and().doNotHaveFullyQualifiedName(EmpresaRepository.class.getName())
                .and().doNotHaveFullyQualifiedName(RepositorioTenant.class.getName())
                .should(extenderRepositorioTenant())
                .because("ADR-002: toda consulta debe filtrar por empresa_id via RepositorioTenant")
                .check(clases);
    }

    private static ArchCondition<JavaClass> extenderRepositorioTenant() {
        return new ArchCondition<>("extender RepositorioTenant") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean extiende = javaClass.getAllRawInterfaces().stream()
                        .anyMatch(i -> i.isAssignableTo(RepositorioTenant.class));
                if (!extiende) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            javaClass.getName() + " no extiende RepositorioTenant"));
                }
            }
        };
    }
}
