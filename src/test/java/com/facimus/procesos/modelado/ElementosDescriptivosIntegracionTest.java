package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.ArtefactoResponse;
import com.facimus.procesos.modelado.dto.response.AsociacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagnosticoResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.dto.response.HallazgoDiagnosticoResponse;
import com.facimus.procesos.modelado.dto.response.ImportacionResponse;
import com.facimus.procesos.modelado.model.DireccionAsociacion;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.model.TipoAsociacion;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.model.TipoTemporizador;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.ArtefactoService;
import com.facimus.procesos.modelado.service.AsociacionService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.DatosDeArtefacto;
import com.facimus.procesos.modelado.service.DatosDeTemporizador;
import com.facimus.procesos.modelado.service.DiagnosticoService;
import com.facimus.procesos.modelado.service.DiagramaService;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.modelado.service.ImportacionBpmnService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.ModeloBpmnService;
import com.facimus.procesos.modelado.service.PoolService;

/** PR 55: lo descriptivo contra la base de verdad: las reglas, lo que se va con que, el modo y el viaje por el XML. */
@SpringBootTest
@ActiveProfiles("test")
class ElementosDescriptivosIntegracionTest {

    private static final DatosDeTemporizador DOS_HORAS =
            new DatosDeTemporizador(TipoTemporizador.DURACION, "PT2H", null, null);

    private static int tiendas;

    @Autowired
    private EmpresaService empresaService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ProcesoService procesoService;
    @Autowired
    private RolProcesoService rolProcesoService;
    @Autowired
    private PoolService poolService;
    @Autowired
    private LaneService laneService;
    @Autowired
    private ActividadService actividadService;
    @Autowired
    private EventoService eventoService;
    @Autowired
    private GatewayService gatewayService;
    @Autowired
    private ArcoService arcoService;
    @Autowired
    private ArtefactoService artefactoService;
    @Autowired
    private AsociacionService asociacionService;
    @Autowired
    private DiagramaService diagramaService;
    @Autowired
    private DiagnosticoService diagnosticoService;
    @Autowired
    private ModeloBpmnService modeloBpmnService;
    @Autowired
    private ImportacionBpmnService importacionBpmnService;
    @Autowired
    private CasoService casoService;
    @Autowired
    private JdbcTemplate jdbc;

    private Long empresaId;
    private Long adminId;
    private Long procesoId;
    private Long tienda;
    private Long ventas;
    private Long almacen;
    private Long inicio;
    private ActividadResponse trabajo;
    private Long fin;

    @BeforeEach
    void modelar() {
        int numero = ++tiendas;
        String admin = "admin" + numero + "@descriptivo.test";
        empresaId = empresaService.registrar("Tienda descriptiva " + numero, "9477" + numero + "000-1", admin,
                "Dora Describe", admin, "marea-violeta-del-sur").id();
        adminId = usuarioRepository.findByEmail(admin).orElseThrow().getId();
        procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        ventas = laneService.crear(empresaId, adminId, tienda, "Sales",
                rolProcesoService.crear(empresaId, adminId, "Sales", null).id()).id();
        almacen = laneService.crear(empresaId, adminId, tienda, "Warehouse",
                rolProcesoService.crear(empresaId, adminId, "Warehouse", null).id()).id();
        inicio = eventoService.crear(empresaId, adminId, ventas, "Start", TipoEvento.INICIO, 0, 0).id();
        trabajo = actividadService.crear(empresaId, adminId, ventas, "Work", null, TipoActividad.USUARIO, 180, 0);
        fin = eventoService.crear(empresaId, adminId, ventas, "End", TipoEvento.FIN, 360, 0).id();
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, trabajo.id()));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(trabajo.id(), fin));
    }

    // ---------- Eventos de borde y temporizadores ----------

    @Test
    @DisplayName("Un evento de borde queda en la lane de su actividad, e interrumpe si no se dice otra cosa")
    void borde_enLaLaneDeSuActividad() {
        EventoResponse borde = borde("Late", trabajo.id(), almacen);

        assertThat(borde.laneId()).isEqualTo(ventas);
        assertThat(borde.adjuntoAId()).isEqualTo(trabajo.id());
        assertThat(borde.interrumpe()).isTrue();
        assertThat(borde.tipoTemporizador()).isEqualTo(TipoTemporizador.DURACION);
        assertThat(borde.temporizador()).isEqualTo("PT2H");
    }

    @Test
    @DisplayName("Un evento de borde va pegado a una actividad de su pool, y solo el de borde va pegado")
    void borde_reglas() {
        Long gateway = gatewayService.crear(empresaId, adminId, ventas, "Gate", TipoGateway.EXCLUSIVO, 10, 10).id();
        Long otroPool = poolService.crear(empresaId, adminId, procesoId, "Carrier", TipoParticipante.PROVEEDOR, false,
                Integracion.NINGUNA).id();
        Long laneDeOtro = laneService.crear(empresaId, adminId, otroPool, "Dispatch",
                rolProcesoService.crear(empresaId, adminId, "Dispatch", null).id()).id();

        assertThatThrownBy(() -> borde("Sin nada", null, ventas)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> borde("A un gateway", gateway, ventas)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> borde("De otro pool", trabajo.id(), laneDeOtro))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> eventoService.crear(empresaId, adminId, ventas, "Pegado", TipoEvento.FIN, 0, 0,
                new DatosDeTemporizador(null, null, trabajo.id(), null))).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("Lo del temporizador solo lo guarda un evento de temporizador")
    void temporizador_soloEnSuTipo() {
        EventoResponse evento = eventoService.crear(empresaId, adminId, ventas, "Plain", TipoEvento.FIN, 0, 0,
                DOS_HORAS);

        assertThat(evento.tipoTemporizador()).isNull();
        assertThat(evento.temporizador()).isNull();
        assertThat(evento.interrumpe()).isNull();
    }

    @Test
    @DisplayName("A un evento de borde no le llega ningun flujo")
    void borde_sinEntrantes() {
        Long borde = borde("Late", trabajo.id(), ventas).id();

        assertThatThrownBy(() -> arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, borde)))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("Editar un evento de borde gasta una sola version")
    void borde_editarUnaVersion() {
        EventoResponse borde = borde("Late", trabajo.id(), ventas);

        EventoResponse editado = eventoService.editar(empresaId, adminId, borde.id(), "Very late",
                TipoEvento.TEMPORIZADOR_BORDE, null, 0, 0, borde.version(),
                new DatosDeTemporizador(TipoTemporizador.DURACION, "PT4H", trabajo.id(), false));

        assertThat(editado.version()).isEqualTo(borde.version() + 1);
        assertThat(editado.temporizador()).isEqualTo("PT4H");
        assertThat(editado.interrumpe()).isFalse();
    }

    @Test
    @DisplayName("Mudar una actividad se lleva sus eventos de borde, y gasta una sola version")
    void actividad_mudaSusBordes() {
        Long borde = borde("Late", trabajo.id(), ventas).id();

        ActividadResponse movida = actividadService.editar(empresaId, adminId, trabajo.id(), "Work", null,
                TipoActividad.USUARIO, almacen, 180, 0, trabajo.version());

        assertThat(movida.version()).isEqualTo(trabajo.version() + 1);
        assertThat(eventoService.obtener(empresaId, borde).laneId()).isEqualTo(almacen);
    }

    @Test
    @DisplayName("Una actividad con eventos de borde no se muda a otro pool")
    void actividad_conBordesNoCambiaDePool() {
        ActividadResponse suelta = actividadService.crear(empresaId, adminId, ventas, "Loose", null,
                TipoActividad.USUARIO, 500, 0);
        borde("Late", suelta.id(), ventas);
        Long otroPool = poolService.crear(empresaId, adminId, procesoId, "Carrier", TipoParticipante.PROVEEDOR, false,
                Integracion.NINGUNA).id();
        Long laneDeOtro = laneService.crear(empresaId, adminId, otroPool, "Dispatch",
                rolProcesoService.crear(empresaId, adminId, "Dispatch", null).id()).id();

        assertThatThrownBy(() -> actividadService.editar(empresaId, adminId, suelta.id(), "Loose", null,
                TipoActividad.USUARIO, laneDeOtro, 0, 0, suelta.version()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("eventos de borde");
    }

    @Test
    @DisplayName("Borrar una actividad se lleva sus eventos de borde, sus flujos y sus asociaciones")
    void actividad_seLlevaLoPegado() {
        EventoResponse borde = borde("Late", trabajo.id(), ventas);
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(borde.id(), fin));
        ArtefactoResponse nota = artefacto(TipoArtefacto.ANOTACION, null, null);
        asociacionService.crear(empresaId, adminId, procesoId, TipoAsociacion.ASOCIACION, nota.bpmnId(),
                trabajo.bpmnId(), DireccionAsociacion.NINGUNA);

        actividadService.eliminar(empresaId, adminId, trabajo.id());

        DiagramaResponse diagrama = diagramaService.obtener(empresaId, procesoId);
        assertThat(diagrama.eventos()).extracting(EventoResponse::id).doesNotContain(borde.id());
        assertThat(diagrama.arcos()).isEmpty();
        assertThat(diagrama.asociaciones()).isEmpty();
        assertThat(diagrama.artefactos()).hasSize(1);
    }

    // ---------- Artefactos ----------

    @Test
    @DisplayName("Sin tamano, un artefacto toma el de su tipo; en una lane, su pool es el de ella")
    void artefacto_porDefecto() {
        ArtefactoResponse dato = artefacto(TipoArtefacto.OBJETO_DATOS, null, ventas);

        assertThat(dato.ancho()).isEqualTo(36);
        assertThat(dato.alto()).isEqualTo(50);
        assertThat(dato.poolId()).isEqualTo(tienda);
        assertThat(dato.bpmnId()).startsWith("DataObjectReference_");
    }

    @Test
    @DisplayName("Un dato va en un pool modelado por dentro; una anotacion puede ir suelta")
    void artefacto_reglas() {
        Long cajaNegra = poolService.crear(empresaId, adminId, procesoId, "Customer", TipoParticipante.CLIENTE, true,
                Integracion.CLIENTE).id();
        Long otroProceso = procesoService.crear(empresaId, adminId, "Other", "Another", "Misc").id();
        Long poolAjeno = poolService.listarPorProceso(empresaId, otroProceso).getFirst().id();

        assertThatThrownBy(() -> artefacto(TipoArtefacto.OBJETO_DATOS, null, null))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> artefacto(TipoArtefacto.ALMACEN_DATOS, cajaNegra, null))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> artefacto(TipoArtefacto.ANOTACION, poolAjeno, null))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(artefacto(TipoArtefacto.ANOTACION, null, null).poolId()).isNull();
    }

    @Test
    @DisplayName("Editar un artefacto no le cambia el tipo")
    void artefacto_editar() {
        ArtefactoResponse nota = artefacto(TipoArtefacto.ANOTACION, null, null);

        ArtefactoResponse editada = artefactoService.editar(empresaId, adminId, nota.id(), new DatosDeArtefacto(
                TipoArtefacto.GRUPO, "New text", null, ventas, 40, 50, 200, 80), nota.version());

        assertThat(editada.tipo()).isEqualTo(TipoArtefacto.ANOTACION);
        assertThat(editada.texto()).isEqualTo("New text");
        assertThat(editada.laneId()).isEqualTo(ventas);
        assertThat(editada.ancho()).isEqualTo(200);
    }

    @Test
    @DisplayName("Borrar un artefacto se lleva sus asociaciones")
    void artefacto_seLlevaSusAsociaciones() {
        ArtefactoResponse nota = artefacto(TipoArtefacto.ANOTACION, null, null);
        asociacionService.crear(empresaId, adminId, procesoId, TipoAsociacion.ASOCIACION, nota.bpmnId(),
                trabajo.bpmnId(), DireccionAsociacion.UNA);

        artefactoService.eliminar(empresaId, adminId, nota.id());

        assertThat(asociacionService.listar(empresaId, procesoId)).isEmpty();
        assertThat(artefactoService.listar(empresaId, procesoId)).isEmpty();
    }

    @Test
    @DisplayName("Una lane con artefactos no se borra; un pool se los lleva")
    void lanesYPools() {
        Long otroPool = poolService.crear(empresaId, adminId, procesoId, "Carrier", TipoParticipante.PROVEEDOR, false,
                Integracion.NINGUNA).id();
        Long laneDeOtro = laneService.crear(empresaId, adminId, otroPool, "Dispatch",
                rolProcesoService.crear(empresaId, adminId, "Dispatch", null).id()).id();
        artefacto(TipoArtefacto.OBJETO_DATOS, null, laneDeOtro);

        assertThatThrownBy(() -> laneService.eliminar(empresaId, adminId, laneDeOtro))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("artefactos");

        poolService.eliminar(empresaId, adminId, otroPool);

        assertThat(artefactoService.listar(empresaId, procesoId)).isEmpty();
    }

    @Test
    @DisplayName("Otra tienda no ve los artefactos ni las asociaciones")
    void aislamiento() {
        ArtefactoResponse nota = artefacto(TipoArtefacto.ANOTACION, null, null);
        Long suyo = nota.id();
        int numero = ++tiendas;
        String correo = "admin" + numero + "@descriptivo.test";
        Long otra = empresaService.registrar("Otra tienda " + numero, "9477" + numero + "000-1", correo, "Otra",
                correo, "marea-violeta-del-sur").id();

        assertThatThrownBy(() -> artefactoService.obtener(otra, suyo)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> artefactoService.listar(otra, procesoId))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> asociacionService.listar(otra, procesoId))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Dar de baja un proceso da de baja sus artefactos y sus asociaciones")
    void bajaDelProceso() {
        ArtefactoResponse nota = artefacto(TipoArtefacto.ANOTACION, null, null);
        asociacionService.crear(empresaId, adminId, procesoId, TipoAsociacion.ASOCIACION, nota.bpmnId(),
                trabajo.bpmnId(), DireccionAsociacion.NINGUNA);

        procesoService.eliminarLogico(empresaId, procesoId, adminId);

        assertThat(jdbc.queryForObject("select count(*) from artefactos where proceso_id = ? and activo", Integer.class,
                procesoId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from asociaciones where proceso_id = ? and activo",
                Integer.class, procesoId)).isZero();
    }

    // ---------- Asociaciones ----------

    @Test
    @DisplayName("Una asociacion lleva un artefacto, y una de datos une un dato con una actividad")
    void asociacion_reglas() {
        ArtefactoResponse nota = artefacto(TipoArtefacto.ANOTACION, null, null);
        ArtefactoResponse dato = artefacto(TipoArtefacto.OBJETO_DATOS, null, ventas);
        String tarea = trabajo.bpmnId();
        String evento = eventoService.obtener(empresaId, inicio).bpmnId();

        assertThatThrownBy(() -> asociar(TipoAsociacion.ASOCIACION, tarea, evento))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> asociar(TipoAsociacion.ASOCIACION, nota.bpmnId(), nota.bpmnId()))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> asociar(TipoAsociacion.ASOCIACION, nota.bpmnId(), "Activity_nada"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> asociar(TipoAsociacion.ENTRADA_DATOS, nota.bpmnId(), tarea))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> asociar(TipoAsociacion.ENTRADA_DATOS, dato.bpmnId(), evento))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> asociar(TipoAsociacion.SALIDA_DATOS, dato.bpmnId(), tarea))
                .isInstanceOf(ReglaNegocioException.class);

        AsociacionResponse entrada = asociar(TipoAsociacion.ENTRADA_DATOS, dato.bpmnId(), tarea);
        assertThat(entrada.bpmnId()).startsWith("DataAssociation_");
        assertThat(entrada.direccion()).isEqualTo(DireccionAsociacion.NINGUNA);
        assertThatThrownBy(() -> asociar(TipoAsociacion.ENTRADA_DATOS, dato.bpmnId(), tarea))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya estan unidos");
    }

    // ---------- El modo ----------

    @Test
    @DisplayName("Un proceso documental se publica aunque no pueda correr, y no abre casos")
    void documental_sePublicaYNoAbreCasos() {
        actividadService.crear(empresaId, adminId, ventas, "Unknown", null, TipoActividad.SIN_TIPO, 500, 0);
        assertThat(codigos(diagnosticoService.diagnosticar(empresaId, procesoId, null))).contains("E-17");

        cambiarModo(ModoProceso.DOCUMENTAL);
        assertThat(diagnosticoService.diagnosticar(empresaId, procesoId, null).errores()).isZero();
        publicar();

        assertThatThrownBy(() -> casoService.abrir(empresaId, adminId, procesoId, "ORD-1", Map.of()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("documental");
    }

    @Test
    @DisplayName("PR 68: los temporizadores corren, asi que uno de borde ya no impide publicar un proceso ejecutable")
    void ejecutable_publicaUnTemporizador() {
        EventoResponse tarde = borde("Late", trabajo.id(), ventas);
        Long escalado = eventoService.crear(empresaId, adminId, ventas, "Escalated", TipoEvento.FIN, 360, 120).id();
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(tarde.id(), escalado));
        cambiarModo(ModoProceso.DOCUMENTAL);
        cambiarModo(ModoProceso.EJECUTABLE);

        publicar();

        assertThat(procesoService.obtener(empresaId, procesoId, false).versionPublicada()).isOne();
    }

    @Test
    @DisplayName("El modo es parte del modelo: cambiarlo deja el borrador pendiente, y mientras tanto manda la version")
    void modo_cuentaAlPublicar() {
        publicar();

        ProcesoResponse documental = cambiarModo(ModoProceso.DOCUMENTAL);

        assertThat(documental.modo()).isEqualTo(ModoProceso.DOCUMENTAL);
        assertThat(documental.borradorPendiente()).isTrue();
        assertThat(casoService.abrir(empresaId, adminId, procesoId, "ORD-2", Map.of()).id()).isNotNull();
    }

    @Test
    @DisplayName("Pedir el modo que ya tiene no cambia nada")
    void modo_igual() {
        ProcesoResponse antes = procesoService.obtener(empresaId, procesoId, false);

        assertThat(cambiarModo(ModoProceso.EJECUTABLE).version()).isEqualTo(antes.version());
    }

    // ---------- El viaje por el XML ----------

    @Test
    @DisplayName("Lo que exporta un proceso con todo lo descriptivo vuelve sin cambiar una fila")
    void exportarEImportar_noCambiaNada() {
        modelarLoDescriptivo();
        String exportado = modeloBpmnService.exportar(empresaId, procesoId).xml();
        String antes = versiones();

        ImportacionResponse importacion = importacionBpmnService.reemplazar(empresaId, adminId,
                RolAcceso.ADMINISTRADOR, procesoId, entrada(exportado));

        assertThat(importacion.creados() + importacion.editados() + importacion.dadosDeBaja()).isZero();
        assertThat(importacion.informe().noSoportados()).isEmpty();
        assertThat(importacion.informe().convertidos()).isEmpty();
        assertThat(importacion.informe().avisosDelEsquema()).isEmpty();
        assertThat(versiones()).isEqualTo(antes);
        assertThat(modeloBpmnService.exportar(empresaId, procesoId).xml()).isEqualTo(exportado);
    }

    @Test
    @DisplayName("Importado como proceso nuevo trae lo mismo, y vuelve a exportar lo mismo")
    void comoProcesoNuevo() {
        modelarLoDescriptivo();
        String exportado = modeloBpmnService.exportar(empresaId, procesoId).xml();

        ImportacionResponse importacion = importacionBpmnService.importar(empresaId, adminId,
                RolAcceso.ADMINISTRADOR, entrada(exportado), "Copy");

        DiagramaResponse original = diagramaService.obtener(empresaId, procesoId);
        DiagramaResponse copia = diagramaService.obtener(empresaId, importacion.procesoId());
        assertThat(copia.proceso().modo()).isEqualTo(ModoProceso.DOCUMENTAL);
        assertThat(copia.artefactos()).extracting(ArtefactoResponse::bpmnId, ArtefactoResponse::tipo,
                ArtefactoResponse::texto).containsExactlyInAnyOrderElementsOf(original.artefactos().stream()
                        .map(uno -> org.assertj.core.api.Assertions.tuple(uno.bpmnId(), uno.tipo(), uno.texto()))
                        .toList());
        assertThat(copia.asociaciones()).extracting(AsociacionResponse::bpmnId)
                .containsExactlyInAnyOrderElementsOf(original.asociaciones().stream().map(AsociacionResponse::bpmnId)
                        .toList());
        EventoResponse borde = copia.eventos().stream()
                .filter(evento -> evento.tipoEvento() == TipoEvento.TEMPORIZADOR_BORDE).findFirst().orElseThrow();
        assertThat(borde.adjuntoAId()).isNotNull();
        assertThat(borde.interrumpe()).isFalse();
        assertThat(borde.temporizador()).isEqualTo("PT2H");
    }

    @Test
    @DisplayName("Un archivo que quita lo descriptivo lo da de baja; a un editor no se le deja")
    void importarSinLoDescriptivo() {
        String sinNada = modeloBpmnService.exportar(empresaId, procesoId).xml();
        modelarLoDescriptivo();

        assertThatThrownBy(() -> importacionBpmnService.reemplazar(empresaId, adminId, RolAcceso.EDITOR, procesoId,
                entrada(sinNada))).hasMessageContaining("administrador");

        ImportacionResponse importacion = importacionBpmnService.reemplazar(empresaId, adminId,
                RolAcceso.ADMINISTRADOR, procesoId, entrada(sinNada));

        assertThat(importacion.dadosDeBaja()).isPositive();
        DiagramaResponse diagrama = diagramaService.obtener(empresaId, procesoId);
        assertThat(diagrama.artefactos()).isEmpty();
        assertThat(diagrama.asociaciones()).isEmpty();
        assertThat(diagrama.proceso().modo()).isEqualTo(ModoProceso.EJECUTABLE);
    }

    private void modelarLoDescriptivo() {
        EventoResponse borde = eventoService.crear(empresaId, adminId, ventas, "Late", TipoEvento.TEMPORIZADOR_BORDE, 0,
                0, new DatosDeTemporizador(TipoTemporizador.DURACION, "PT2H", trabajo.id(), false));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(borde.id(), fin));
        ArtefactoResponse nota = artefactoService.crear(empresaId, adminId, procesoId, new DatosDeArtefacto(
                TipoArtefacto.ANOTACION, "Check by hand", null, ventas, 200, 60, null, null));
        ArtefactoResponse dato = artefactoService.crear(empresaId, adminId, procesoId, new DatosDeArtefacto(
                TipoArtefacto.OBJETO_DATOS, "Order", null, ventas, 40, 90, null, null));
        ArtefactoResponse almacenDeDatos = artefactoService.crear(empresaId, adminId, procesoId, new DatosDeArtefacto(
                TipoArtefacto.ALMACEN_DATOS, "Stock", null, almacen, 120, 15, null, null));
        artefactoService.crear(empresaId, adminId, procesoId, new DatosDeArtefacto(TipoArtefacto.GRUPO, "Checks", null,
                null, 300, 500, null, null));
        artefactoService.crear(empresaId, adminId, procesoId, new DatosDeArtefacto(TipoArtefacto.ANOTACION, "Loose",
                null, null, 700, 620, null, null));
        asociacionService.crear(empresaId, adminId, procesoId, TipoAsociacion.ASOCIACION, nota.bpmnId(),
                trabajo.bpmnId(), DireccionAsociacion.UNA);
        asociar(TipoAsociacion.ENTRADA_DATOS, dato.bpmnId(), trabajo.bpmnId());
        asociar(TipoAsociacion.SALIDA_DATOS, trabajo.bpmnId(), almacenDeDatos.bpmnId());
        cambiarModo(ModoProceso.DOCUMENTAL);
    }

    private EventoResponse borde(String nombre, Long actividad, Long lane) {
        return eventoService.crear(empresaId, adminId, lane, nombre, TipoEvento.TEMPORIZADOR_BORDE, 0, 0,
                new DatosDeTemporizador(DOS_HORAS.tipo(), DOS_HORAS.expresion(), actividad, null));
    }

    private ArtefactoResponse artefacto(TipoArtefacto tipo, Long pool, Long lane) {
        return artefactoService.crear(empresaId, adminId, procesoId, new DatosDeArtefacto(tipo, "Text", pool, lane, 0,
                0, null, null));
    }

    private AsociacionResponse asociar(TipoAsociacion tipo, String origen, String destino) {
        return asociacionService.crear(empresaId, adminId, procesoId, tipo, origen, destino,
                DireccionAsociacion.NINGUNA);
    }

    private ProcesoResponse cambiarModo(ModoProceso modo) {
        return procesoService.cambiarModo(empresaId, procesoId, adminId, modo,
                procesoService.obtener(empresaId, procesoId, false).version());
    }

    private void publicar() {
        procesoService.cambiarEstado(empresaId, procesoId, adminId, EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, procesoId, false).version());
    }

    private static List<String> codigos(DiagnosticoResponse diagnostico) {
        return diagnostico.hallazgos().stream().map(HallazgoDiagnosticoResponse::codigo).toList();
    }

    private static ByteArrayInputStream entrada(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    /** La version de cada fila del modelo: si una importacion escribe algo, se nota. */
    private String versiones() {
        return String.join(",", jdbc.queryForList("""
                select 'p' || id || ':' || version from procesos where id = ?
                union all select 'n' || n.id || ':' || n.version || n.activo from nodos_flujo n
                    join lanes l on l.id = n.lane_id join pools p on p.id = l.pool_id where p.proceso_id = ?
                union all select 'x' || id || ':' || version || activo from artefactos where proceso_id = ?
                union all select 'y' || id || ':' || version || activo from asociaciones where proceso_id = ?
                order by 1
                """, String.class, procesoId, procesoId, procesoId, procesoId));
    }
}
