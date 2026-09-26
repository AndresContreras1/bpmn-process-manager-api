package com.facimus.procesos.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import org.springframework.cache.annotation.Cacheable;

import com.facimus.procesos.common.EntidadEmpresa;
import com.facimus.procesos.common.model.Empresa;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MappedSuperclass;

/**
 * ADR-002: toda entidad JPA excepto Empresa debe extender EntidadEmpresa.
 * La columna empresa_id debe ser non-nullable y non-updatable.
 * Y lo mismo en memoria: toda clave de cache empieza por la tienda.
 */
@AnalyzeClasses(packages = "com.facimus.procesos", importOptions = ImportOption.DoNotIncludeTests.class)
class MultitenenciaTest {

    @ArchTest
    static final ArchRule entidades_extienden_EntidadEmpresa = classes()
            .that().areAnnotatedWith(Entity.class)
            .and().doNotHaveFullyQualifiedName(Empresa.class.getName())
            .should().beAssignableTo(EntidadEmpresa.class)
            .because("ADR-002: multi-tenencia requiere empresa_id en toda entidad");

    @ArchTest
    static final ArchRule empresa_id_no_es_updatable = classes()
            .that().areAnnotatedWith(MappedSuperclass.class)
            .and().haveSimpleNameContaining("EntidadEmpresa")
            .should(tenerJoinColumnNoUpdatable())
            .because("ADR-002: empresa_id no debe cambiar despues de crearse");

    /**
     * Guardar tambien es una forma de leer. Los ids de version son unicos en todo el sistema, asi que hoy la tienda
     * en la clave no hace falta para acertar; hace falta para que una clave escrita manana con el id de un recurso
     * que si se repite entre tiendas no le sirva a una lo que guardo otra.
     */
    @ArchTest
    static final ArchRule claves_de_cache_empiezan_por_la_empresa = methods()
            .that().areAnnotatedWith(Cacheable.class)
            .should(llevarLaEmpresaEnLaClave())
            .because("ADR-002: lo que se guarda por tienda se pide por tienda");

    private static ArchCondition<JavaMethod> llevarLaEmpresaEnLaClave() {
        return new ArchCondition<>("llevar #empresaId en la clave") {
            @Override
            public void check(JavaMethod metodo, ConditionEvents events) {
                String clave = metodo.getAnnotationOfType(Cacheable.class).key();
                if (!clave.contains("#empresaId")) {
                    events.add(SimpleConditionEvent.violated(metodo,
                            metodo.getFullName() + " guarda con la clave \"" + clave + "\", sin la tienda"));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> tenerJoinColumnNoUpdatable() {
        return new ArchCondition<>("tener @JoinColumn(updatable=false) en el campo empresa") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaField field : javaClass.getFields()) {
                    if (!field.getName().equals("empresa")) continue;
                    field.tryGetAnnotationOfType(JoinColumn.class).ifPresent(jc -> {
                        if (jc.updatable()) {
                            events.add(SimpleConditionEvent.violated(field,
                                    field.getFullName() + " tiene updatable=true"));
                        }
                    });
                }
            }
        };
    }
}
