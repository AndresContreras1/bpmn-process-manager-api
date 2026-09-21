package com.facimus.procesos.arquitectura;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import com.facimus.procesos.common.EntidadEmpresa;
import com.facimus.procesos.gestion.model.Empresa;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MappedSuperclass;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ADR-002: toda entidad JPA excepto Empresa debe extender EntidadEmpresa.
 * La columna empresa_id debe ser non-nullable y non-updatable.
 */
class MultitenenciaTest {

    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.facimus.procesos");
    }

    @Test
    @DisplayName("Toda @Entity excepto Empresa debe extender EntidadEmpresa")
    void entidades_extienden_EntidadEmpresa() {
        classes()
                .that().areAnnotatedWith(Entity.class)
                .and().doNotHaveFullyQualifiedName(Empresa.class.getName())
                .should().beAssignableTo(EntidadEmpresa.class)
                .because("ADR-002: multi-tenencia requiere empresa_id en toda entidad")
                .check(clases);
    }

    @Test
    @DisplayName("EntidadEmpresa tiene @JoinColumn con updatable=false")
    void empresa_id_no_es_updatable() {
        classes()
                .that().areAnnotatedWith(MappedSuperclass.class)
                .and().haveSimpleNameContaining("EntidadEmpresa")
                .should(tenerJoinColumnNoUpdatable())
                .because("ADR-002: empresa_id no debe cambiar despues de crearse")
                .check(clases);
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
