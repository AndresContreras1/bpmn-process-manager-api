package com.facimus.procesos.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.mapstruct.Mapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import jakarta.persistence.Entity;

/**
 * ADR-001: empaquetado modular por dominio (gestion + modelado), cada modulo en capas:
 * controller -> service (interfaz) -> service.impl -> repository -> model, con dto y mapper como contrato y traduccion.
 */
@AnalyzeClasses(packages = "com.facimus.procesos", importOptions = ImportOption.DoNotIncludeTests.class)
class EmpaquetadoTest {

    @ArchTest
    static final ArchRule controllers_en_paquete_controller = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..controller..")
            .because("ADR-001: cada modulo tiene su propio controller/");

    @ArchTest
    static final ArchRule services_implementados_en_service_impl = classes()
            .that().areAnnotatedWith(Service.class)
            .should().resideInAPackage("..service.impl..")
            .andShould().haveSimpleNameEndingWith("Impl")
            .because("cada service se publica como interfaz en service/ y se implementa en service/impl/");

    @ArchTest
    static final ArchRule nadie_depende_de_una_implementacion_de_service = noClasses()
            .that().resideOutsideOfPackage("..service.impl..")
            .should().dependOnClassesThat().resideInAPackage("..service.impl..")
            .because("controllers, seguridad y configuracion trabajan con la interfaz del service");

    @ArchTest
    static final ArchRule repositorios_en_paquete_repository = classes()
            .that().areAnnotatedWith(Repository.class)
            .or().areInterfaces().and().haveSimpleNameEndingWith("Repository")
            .should().resideInAnyPackage("..repository..", "..common..")
            .because("ADR-001: cada modulo tiene su propio repository/");

    @ArchTest
    static final ArchRule entidades_en_paquete_model = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..model..")
            .because("ADR-001: las entidades viven en model/");

    @ArchTest
    static final ArchRule mappers_en_paquete_mapper = classes()
            .that().areAnnotatedWith(Mapper.class)
            .should().resideInAPackage("..mapper..")
            .because("los mappers de MapStruct traducen entidades a DTOs en un solo lugar");

    @ArchTest
    static final ArchRule solo_las_implementaciones_usan_los_mappers = noClasses()
            .that().resideOutsideOfPackages("..service.impl..", "..mapper..")
            .should().dependOnClassesThat().resideInAPackage("..mapper..")
            .because("los services mapean dentro de su transaccion y entregan DTOs ya armados");

    @ArchTest
    static final ArchRule controllers_no_usan_repositorios = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .because("los controllers pasan por la capa de servicio");

    @ArchTest
    static final ArchRule controllers_no_usan_entidades = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
            .because("los controllers solo reciben y devuelven DTOs: una entidad nunca sale de su transaccion");

    @ArchTest
    static final ArchRule modelado_no_depende_de_gestion_controller = noClasses()
            .that().resideInAPackage("..modelado..")
            .should().dependOnClassesThat().resideInAPackage("..gestion.controller..")
            .because("ADR-001: modelado no depende de los controllers de gestion");

    @ArchTest
    static final ArchRule gestion_no_depende_de_modelado = noClasses()
            .that().resideInAPackage("..gestion..")
            .should().dependOnClassesThat().resideInAPackage("..modelado..")
            .because("modelado se apoya en los procesos y roles de gestion; al reves se formaria un ciclo");

    @ArchTest
    static final ArchRule nadie_depende_de_ejecucion = noClasses()
            .that().resideInAnyPackage("..common..", "..security..", "..gestion..", "..modelado..")
            .should().dependOnClassesThat().resideInAPackage("..ejecucion..")
            .because("D1: la ejecucion se apoya en el modelo publicado y en los roles; al reves seria un ciclo");

    @ArchTest
    static final ArchRule condiciones_sin_lenguajes_que_ejecutan_codigo = noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.expression..", "javax.script..", "jdk.dynalink..")
            .because("D6: una condicion la escribe un usuario, y se lee con la gramatica del proyecto; SpEL o un "
                    + "motor de scripts permitirian llamar metodos desde ese texto");

    @ArchTest
    static final ArchRule lo_que_corre_solo_vive_en_config = methods()
            .that().areAnnotatedWith(Scheduled.class)
            .should().beDeclaredInClassesThat().resideInAPackage("..config..")
            .because("un trabajo que corre por su cuenta se registra en un solo sitio y fuera del perfil test: "
                    + "una prueba no puede tener algo por detras moviendole el reloj o borrandole filas");

    @ArchTest
    static final ArchRule los_simulados_no_miran_dentro_del_motor = noClasses()
            .that().resideInAPackage("..integracion..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..ejecucion.service..", "..ejecucion.repository..", "..ejecucion.model..",
                    "..ejecucion.controller..", "..ejecucion.dto..", "..gestion..", "..security..")
            .because("D7: un socio simulado recibe un mensaje y contesta; no sabe de casos, ni de bandejas, ni de "
                    + "quien lo llamo, y solo habla por su puerto");

    @ArchTest
    static final ArchRule los_simulados_no_hacen_red = noClasses()
            .that().resideInAPackage("..integracion..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("java.net..", "org.springframework.web.client..",
                    "org.springframework.web.reactive..")
            .because("D7: no hay socios de verdad. Uno que abriera una conexion dejaria de ser una simulacion, y "
                    + "las pruebas pasarian a depender de que algo de fuera conteste");

    @ArchTest
    static final ArchRule los_puertos_no_hablan_de_la_base = noClasses()
            .that().resideInAPackage("..puerto..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..service..", "..repository..", "..controller..", "..ejecucion.model..")
            .because("un puerto es el contrato con lo de fuera: si hablara de entidades, quien lo implemente "
                    + "tendria que conocer la base de datos de la ejecucion");

    @ArchTest
    static final ArchRule paquetes_de_cada_modulo_sin_ciclos = slices()
            .matching("com.facimus.procesos.(*).(*)..")
            .should().beFreeOfCycles()
            .because("cada capa de un modulo depende solo de las de abajo");

    @ArchTest
    static final ArchRule paquetes_de_primer_nivel_sin_ciclos = slices()
            .matching("com.facimus.procesos.(*)..")
            .should().beFreeOfCycles()
            .because("common, security, gestion y modelado se apilan: ninguno vuelve sobre el de abajo");

    @ArchTest
    static final ArchRule servicios_no_dependen_de_controllers = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..")
            .because("la capa de servicio no conoce la capa de presentacion");

    @ArchTest
    static final ArchRule repositorios_no_dependen_de_capas_superiores = noClasses()
            .that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..")
            .because("la capa de persistencia no conoce capas superiores");

    @ArchTest
    static final ArchRule modelo_no_depende_de_capas_superiores = noClasses()
            .that().resideInAPackage("..model..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..")
            .because("el modelo de dominio es independiente de la infraestructura");

    @ArchTest
    static final ArchRule dtos_de_entrada_en_dto_request = classes()
            .that().haveSimpleNameEndingWith("Request")
            .should().resideInAPackage("..dto.request..")
            .because("los DTOs son el contrato de cada modulo: los usan los controllers y los devuelven los services");

    @ArchTest
    static final ArchRule dtos_de_salida_en_dto_response = classes()
            .that().haveSimpleNameEndingWith("Response")
            .should().resideInAnyPackage("..dto.response..", "..common.api..")
            .because("los DTOs son el contrato de cada modulo: los usan los controllers y los devuelven los services");

    @ArchTest
    static final ArchRule dtos_sin_entidades = noClasses()
            .that().resideInAPackage("..dto..")
            .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
            .because("un DTO es un record plano: la traduccion desde la entidad la hace su mapper");
}
