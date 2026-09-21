package com.facimus.procesos.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import com.facimus.procesos.common.RepositorioTenant;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** README §7, §10 y §18: acceso tenant-aware, API sin sesion y empresa que nunca elige el cliente. */
@AnalyzeClasses(packages = "com.facimus.procesos", importOptions = ImportOption.DoNotIncludeTests.class)
class AislamientoTenantTest {

    // Heredados de JpaRepository: llegan a la entidad sin filtrar por empresa.
    private static final Set<String> CONSULTAS_SIN_EMPRESA =
            Set.of("findById", "existsById", "deleteById", "getReferenceById", "findAllById");

    @ArchTest
    static final ArchRule services_consultan_con_empresaId = noClasses()
            .that().resideInAPackage("..service..")
            .should().callMethodWhere(consultaSinEmpresa())
            .because("README §10: todo recurso identificado por id comprueba que pertenece a la empresa");

    @ArchTest
    static final ArchRule aplicacion_sin_HttpSession = noClasses()
            .should().dependOnClassesThat().haveFullyQualifiedName("jakarta.servlet.http.HttpSession")
            .because("README §7: la identidad sale del JWT y la API es stateless");

    @ArchTest
    static final ArchRule requests_sin_empresa = noFields()
            .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Request")
            .should().haveNameMatching("empresa(Id)?")
            .because("README §10: la empresa sale del ApiPrincipal, nunca del request");

    private static DescribedPredicate<JavaMethodCall> consultaSinEmpresa() {
        return DescribedPredicate.describe("una consulta sin empresaId sobre un RepositorioTenant",
                llamada -> llamada.getTargetOwner().isAssignableTo(RepositorioTenant.class)
                        && (CONSULTAS_SIN_EMPRESA.contains(llamada.getName())
                                || llamada.getName().equals("findAll") && llamada.getTarget().getRawParameterTypes()
                                        .stream().noneMatch(tipo -> tipo.isAssignableTo(Specification.class))));
    }
}
