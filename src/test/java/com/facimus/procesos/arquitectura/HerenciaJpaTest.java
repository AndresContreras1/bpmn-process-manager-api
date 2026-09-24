package com.facimus.procesos.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import org.hibernate.annotations.SQLDelete;

import com.facimus.procesos.modelado.model.NodoFlujo;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

/** Decisiones de mapeo JPA del consolidado de arquitectura: herencia, enums y carga de relaciones. */
@AnalyzeClasses(packages = "com.facimus.procesos", importOptions = ImportOption.DoNotIncludeTests.class)
class HerenciaJpaTest {

    @ArchTest
    static final ArchRule nodoFlujo_usa_single_table = classes()
            .that().haveSimpleName("NodoFlujo")
            .should(tenerInheritanceSingleTable())
            .because("Consolidado: NodoFlujo usa SINGLE_TABLE, 3 subtipos con pocos campos");

    @ArchTest
    static final ArchRule subtipos_extienden_NodoFlujo = classes()
            .that().haveSimpleNameStartingWith("Actividad")
            .or().haveSimpleNameStartingWith("Gateway")
            .or().haveSimpleNameStartingWith("Evento")
            .and().resideInAPackage("..model..")
            .and().areAnnotatedWith(Entity.class)
            .should().beAssignableTo(NodoFlujo.class)
            .because("Actividad, Gateway y Evento son los unicos subtipos de NodoFlujo");
    @ArchTest
    static final ArchRule subtipos_declaran_su_borrado = classes()
            .that().areAssignableTo(NodoFlujo.class)
            .and().areAnnotatedWith(Entity.class)
            .and().areNotInterfaces()
            .should(declararSuPropioSQLDelete())
            .because("con SINGLE_TABLE el @SQLDelete no se hereda: cada subtipo declara su baja logica");


    @ArchTest
    static final ArchRule enums_mapeados_como_string = classes()
            .that().areAnnotatedWith(Entity.class)
            .should(usarEnumTypeString())
            .because("Consolidado: enums siempre STRING para evitar corrupcion por reordenamiento");

    @ArchTest
    static final ArchRule relaciones_perezosas = fields()
            .that().areAnnotatedWith(ManyToOne.class)
            .or().areAnnotatedWith(OneToOne.class)
            .should(cargarsePerezosamente())
            .because("con EAGER cada consulta arrastra sus asociaciones; cada consulta pide lo que necesita");

    private static ArchCondition<JavaClass> declararSuPropioSQLDelete() {
        return new ArchCondition<>("declarar su propio @SQLDelete sobre nodos_flujo") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (javaClass.isEquivalentTo(NodoFlujo.class)) {
                    return;
                }
                javaClass.tryGetAnnotationOfType(SQLDelete.class).ifPresentOrElse(
                        borrado -> {
                            if (!borrado.sql().contains("update nodos_flujo set activo = false")) {
                                events.add(SimpleConditionEvent.violated(javaClass,
                                        javaClass.getName() + " borra con: " + borrado.sql()));
                            }
                        },
                        () -> events.add(SimpleConditionEvent.violated(javaClass,
                                javaClass.getName() + " no declara @SQLDelete"))
                );
            }
        };
    }

    private static ArchCondition<JavaClass> tenerInheritanceSingleTable() {
        return new ArchCondition<>("tener @Inheritance(SINGLE_TABLE)") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.tryGetAnnotationOfType(Inheritance.class).ifPresentOrElse(
                        inheritance -> {
                            if (inheritance.strategy() != InheritanceType.SINGLE_TABLE) {
                                events.add(SimpleConditionEvent.violated(javaClass,
                                        javaClass.getName() + " usa " + inheritance.strategy()
                                                + " en vez de SINGLE_TABLE"));
                            }
                        },
                        () -> events.add(SimpleConditionEvent.violated(javaClass,
                                javaClass.getName() + " no tiene @Inheritance"))
                );
            }
        };
    }

    private static ArchCondition<JavaClass> usarEnumTypeString() {
        return new ArchCondition<>("usar @Enumerated(STRING) en todos los campos enum") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.getAllFields().stream()
                        .filter(f -> f.tryGetAnnotationOfType(Enumerated.class).isPresent())
                        .forEach(f -> {
                            Enumerated ann = f.tryGetAnnotationOfType(Enumerated.class).get();
                            if (ann.value() != EnumType.STRING) {
                                events.add(SimpleConditionEvent.violated(f,
                                        f.getFullName() + " usa EnumType." + ann.value()
                                                + " en vez de STRING"));
                            }
                        });
            }
        };
    }

    private static ArchCondition<JavaField> cargarsePerezosamente() {
        return new ArchCondition<>("cargarse con fetch = LAZY") {
            @Override
            public void check(JavaField campo, ConditionEvents events) {
                FetchType fetch = campo.tryGetAnnotationOfType(ManyToOne.class).map(ManyToOne::fetch)
                        .or(() -> campo.tryGetAnnotationOfType(OneToOne.class).map(OneToOne::fetch))
                        .orElseThrow();
                if (fetch != FetchType.LAZY) {
                    events.add(SimpleConditionEvent.violated(campo, campo.getFullName() + " usa fetch = " + fetch));
                }
            }
        };
    }
}
