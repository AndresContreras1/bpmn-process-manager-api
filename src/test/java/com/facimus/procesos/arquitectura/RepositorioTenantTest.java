package com.facimus.procesos.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * ADR-002: todo repositorio de entidades tenant debe extender RepositorioTenant.
 * EmpresaRepository es la unica excepcion (Empresa es la raiz de tenencia).
 */
@AnalyzeClasses(packages = "com.facimus.procesos", importOptions = ImportOption.DoNotIncludeTests.class)
class RepositorioTenantTest {

    @ArchTest
    static final ArchRule repositorios_extienden_RepositorioTenant = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .and().areInterfaces()
            .and().doNotHaveFullyQualifiedName(EmpresaRepository.class.getName())
            .and().doNotHaveFullyQualifiedName(RepositorioTenant.class.getName())
            .should(extenderRepositorioTenant())
            .because("ADR-002: toda consulta debe filtrar por empresa_id via RepositorioTenant");

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
