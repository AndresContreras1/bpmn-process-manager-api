package com.facimus.procesos.modelado;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.facimus.procesos.common.archivos.ArchivosConTexto;
import com.facimus.procesos.common.correo.Correo;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.model.Clasificacion;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.SujetoConcesion;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.gestion.service.CarpetaService;
import com.facimus.procesos.gestion.service.ConcesionService;
import com.facimus.procesos.gestion.service.DatosDeFicha;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.PreferenciasService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.Recurso;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.ComentarioService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.DocumentacionService;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;
import com.facimus.procesos.security.ApiPrincipalRequestPostProcessor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 65: la busqueda de punta a punta, por HTTP y contra PostgreSQL: tildes, raices en los tres idiomas, el orden, el
 * fragmento sin HTML, el texto de un PDF y de un Word leido en la cola, comentarios, documentacion de elementos, la
 * version publicada, los permisos de cada persona, la seguridad por filas y la sintaxis de un buscador web.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BusquedaIntegracionTest {

    private static final String CLAVE = "un-faro-sobre-la-bahia-quieta";
    private static final Path ALMACEN = carpeta();
    private static final String BUSQUEDA = "/api/v1/busqueda";
    private static final String CONTENIDO = "$.content";
    private static final String TOTAL = "$.totalElements";
    private static int tiendas;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("almacen.directorio", ALMACEN::toString);
    }

    /** Los correos de cada alta no tienen a donde salir: la cola los da por enviados. */
    @MockitoBean
    private Correo correo;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EmpresaService empresaService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ProcesoService procesoService;
    @Autowired
    private PreferenciasService preferenciasService;
    @Autowired
    private CarpetaService carpetaService;
    @Autowired
    private ConcesionService concesionService;
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
    private ArcoService arcoService;
    @Autowired
    private DocumentacionService documentacionService;
    @Autowired
    private ComentarioService comentarioService;
    @Autowired
    private ColaDeTrabajos cola;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;

    private Long empresaId;
    private ApiPrincipal ana;
    private ApiPrincipal beto;
    private ApiPrincipal carla;
    private ApiPrincipal ajeno;

    private static Path carpeta() {
        try {
            return Files.createTempDirectory("almacen-busqueda-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeEach
    void registrar() {
        vaciarLaCola();
        int numero = ++tiendas;
        String correoDeAna = "ana" + numero + "@busqueda.test";
        empresaId = empresaService.registrar("Tienda " + numero, "9651" + numero + "000-1", correoDeAna, "Ana",
                correoDeAna, CLAVE).id();
        ana = persona(correoDeAna, RolAcceso.ADMINISTRADOR);
        beto = persona(usuarioService.crearColaborador(empresaId, null, "Beto", "beto" + numero + "@busqueda.test",
                CLAVE, RolAcceso.EDITOR).email(), RolAcceso.EDITOR);
        carla = persona(usuarioService.crearColaborador(empresaId, null, "Carla", "carla" + numero
                + "@busqueda.test", CLAVE, RolAcceso.SOLO_LECTURA).email(), RolAcceso.SOLO_LECTURA);
        String otro = "otra" + numero + "@busqueda.test";
        empresaService.registrar("Ajena " + numero, "9652" + numero + "000-1", otro, "Otra", otro, CLAVE);
        ajeno = persona(otro, RolAcceso.ADMINISTRADOR);
    }

    private ApiPrincipal persona(String correoDe, RolAcceso rol) {
        var usuario = usuarioRepository.findByEmail(correoDe).orElseThrow();
        return new ApiPrincipal(usuario.getId(), usuario.getEmpresa().getId(), rol, correoDe,
                ApiPrincipalRequestPostProcessor.SESION, false);
    }

    private void vaciarLaCola() {
        while (cola.procesarUno()) {
            // la cola, hasta vaciarse
        }
    }

    private Long proceso(String nombre, String descripcion) {
        return procesoService.crear(empresaId, ana.usuarioId(), nombre, descripcion, "General").id();
    }

    private ResultActions buscar(ApiPrincipal quien, String texto, String... parametros) throws Exception {
        MockHttpServletRequestBuilder pedido = get(BUSQUEDA).param("q", texto).with(principal(quien));
        for (int i = 0; i < parametros.length; i += 2) {
            pedido.param(parametros[i], parametros[i + 1]);
        }
        return mvc.perform(pedido);
    }

    private JsonNode resultados(ApiPrincipal quien, String texto, String... parametros) throws Exception {
        String cuerpo = buscar(quien, texto, parametros).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return json.readTree(cuerpo).get("content");
    }

    private static List<String> tipos(JsonNode resultados) {
        List<String> tipos = new ArrayList<>();
        resultados.forEach(resultado -> tipos.add(resultado.get("tipo").asString()));
        return tipos;
    }

    private static List<Long> procesos(JsonNode resultados) {
        List<Long> procesos = new ArrayList<>();
        resultados.forEach(resultado -> procesos.add(resultado.get("procesoId").asLong()));
        return procesos;
    }

    private static List<String> resaltados(JsonNode resultado) {
        List<String> resaltados = new ArrayList<>();
        resultado.get("fragmento").forEach(segmento -> {
            if (segmento.get("resaltado").asBoolean()) {
                resaltados.add(segmento.get("texto").asString());
            }
        });
        return resaltados;
    }

    private static String fragmento(JsonNode resultado) {
        StringBuilder texto = new StringBuilder();
        resultado.get("fragmento").forEach(segmento -> texto.append(segmento.get("texto").asString()));
        return texto.toString();
    }

    /** Un comentario nuevo en el proceso, de Ana, sobre un elemento si se dice. */
    private Long comentar(Long proceso, String markdown) {
        return comentarioService.comentar(empresaId, ana.usuarioId(), proceso,
                new ComentarioService.Nuevo(markdown, null, null, List.of())).id();
    }

    private Long subir(Long proceso, String nombre, byte[] contenido) throws Exception {
        String cuerpo = mvc.perform(post("/api/v1/procesos/{id}/documentos", proceso).param("nombre", nombre)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(contenido).with(principal(beto)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("id").asLong();
    }

    /** Una lane con su rol en el pool de la tienda, para poner tareas. */
    private Long lane(Long proceso) {
        Long tienda = poolService.listarPorProceso(empresaId, proceso).getFirst().id();
        Long rol = rolProcesoService.crear(empresaId, ana.usuarioId(), "Bodega " + proceso, null).id();
        return laneService.crear(empresaId, ana.usuarioId(), tienda, "Bodega", rol).id();
    }

    private String bpmnIdDeNodo(Long nodo) {
        return jdbc.queryForObject("select bpmn_id from nodos_flujo where id = ?", String.class, nodo);
    }

    @Test
    @DisplayName("sin tildes y sin mayusculas encuentra con tildes, y una palabra encuentra sus otras formas")
    void tildesYRaices() throws Exception {
        Long credito = proceso("Aprobación de crédito", "Revisar la solicitud del cliente");
        Long envio = proceso("Envío frágil", "Pedidos de la campaña de diciembre");
        proceso("Despacho", "Del pago a la entrega");

        assertThat(procesos(resultados(beto, "credito"))).containsExactly(credito);
        assertThat(procesos(resultados(beto, "CRÉDITOS"))).containsExactly(credito);
        assertThat(procesos(resultados(beto, "solicitudes clientes"))).containsExactly(credito);
        // Donde el lematizador no quita la tilde, ni la de la ene, lo hace unaccent
        assertThat(procesos(resultados(beto, "fragil campana"))).containsExactly(envio);
    }

    @Test
    @DisplayName("una tienda en ingles y otra en frances buscan con las raices de su idioma")
    void otrosIdiomas() throws Exception {
        int numero = ++tiendas;
        String ingles = "en" + numero + "@busqueda.test";
        Long tiendaEnIngles = empresaService.registrar("Shop " + numero, "9653" + numero + "000-1", ingles, "Ann",
                ingles, CLAVE, "en-US", "America/New_York").id();
        ApiPrincipal ann = persona(ingles, RolAcceso.ADMINISTRADOR);
        Long envios = procesoService.crear(tiendaEnIngles, ann.usuarioId(), "Shipping orders", "Pack the boxes",
                "Fulfillment").id();
        String frances = "fr" + numero + "@busqueda.test";
        Long tiendaEnFrances = empresaService.registrar("Boutique " + numero, "9654" + numero + "000-1", frances,
                "Anne", frances, CLAVE, "fr-FR", "Europe/Paris").id();
        ApiPrincipal anne = persona(frances, RolAcceso.ADMINISTRADOR);
        Long livraisons = procesoService.crear(tiendaEnFrances, anne.usuarioId(), "Livraisons des commandes",
                "Préparer les colis", "Logistique").id();

        assertThat(procesos(resultados(ann, "shipped order"))).containsExactly(envios);
        assertThat(procesos(resultados(ann, "box"))).containsExactly(envios);
        assertThat(procesos(resultados(anne, "livraison commande"))).containsExactly(livraisons);
        assertThat(procesos(resultados(anne, "preparer"))).containsExactly(livraisons);
    }

    @Test
    @DisplayName("al cambiar el idioma de la tienda, cada vector se rehace con el nuevo")
    void cambioDeIdioma() throws Exception {
        Long envios = proceso("Shipping orders", "Pack the boxes");
        Long elemento = actividadService.crear(empresaId, ana.usuarioId(), lane(envios), "Ship the boxes", null,
                TipoActividad.USUARIO, 160, 80).id();
        assertThat(resultados(beto, "shipped")).isEmpty();

        preferenciasService.cambiarLasDeLaOrganizacion(empresaId, ana.usuarioId(), "en-US", "America/New_York");

        JsonNode encontrados = resultados(beto, "shipped");
        assertThat(tipos(encontrados)).containsExactlyInAnyOrder("PROCESO", "ELEMENTO");
        assertThat(encontrados.findValuesAsString("bpmnId")).contains(bpmnIdDeNodo(elemento));
    }

    @Test
    @DisplayName("un nombre gana a una descripcion, y una descripcion a un comentario, con las mismas palabras")
    void orden() throws Exception {
        Long porNombre = proceso("Crédito", "Ventas mayoristas");
        Long porDescripcion = proceso("Ventas mayoristas", "Crédito");
        comentar(porDescripcion, "Crédito ventas mayoristas");

        JsonNode encontrados = resultados(beto, "crédito");

        assertThat(tipos(encontrados)).containsExactly("PROCESO", "PROCESO", "COMENTARIO");
        assertThat(procesos(encontrados)).containsExactly(porNombre, porDescripcion, porDescripcion);
    }

    @Test
    @DisplayName("el fragmento son tramos de texto plano: un script y un & salen como texto, y solo lo buscado resalta")
    void fragmentoSeguro() throws Exception {
        Long despacho = proceso("Despacho", "Del pago a la entrega");
        Long comentario = comentar(despacho, "Ojo: el crédito <script>alert(1)</script> <img src=x onerror=alert(1)> "
                + "& falso sigue pendiente");

        JsonNode encontrado = resultados(beto, "credito").get(0);

        assertThat(encontrado.get("tipo").asString()).isEqualTo("COMENTARIO");
        assertThat(encontrado.get("comentarioId").asLong()).isEqualTo(comentario);
        assertThat(encontrado.get("hiloId").asLong()).isEqualTo(comentario);
        assertThat(encontrado.get("titulo").asString()).isEqualTo("Ana");
        assertThat(resaltados(encontrado)).containsExactly("crédito");
        // ts_headline quita las etiquetas que reconoce y deja lo demas tal cual: por eso no se devuelve como HTML
        assertThat(fragmento(encontrado)).contains("alert(1)").contains("<img src=x onerror=alert(1)> &")
                .contains("falso").doesNotContain("&amp;").doesNotContain("").doesNotContain("");
    }

    @Test
    @DisplayName("el texto de un PDF y de un Word se encuentra cuando la cola lo lee, despues del antivirus")
    void documentos() throws Exception {
        Long despacho = proceso("Despacho", "Del pago a la entrega");
        Long pdf = subir(despacho, "Instrucciones de empaque.pdf",
                ArchivosConTexto.pdf("Usar cinta reforzada", "en cada caja frágil"));
        Long docx = subir(despacho, "Contrato.docx", ArchivosConTexto.docx("Cláusula de penalización por demora"));

        assertThat(resultados(beto, "reforzada")).isEmpty();
        assertThat(resultados(beto, "empaque").get(0).get("documentoId").asLong()).isEqualTo(pdf);

        vaciarLaCola();

        JsonNode enElPdf = resultados(beto, "cinta fragil").get(0);
        assertThat(enElPdf.get("tipo").asString()).isEqualTo("DOCUMENTO");
        assertThat(enElPdf.get("documentoId").asLong()).isEqualTo(pdf);
        assertThat(enElPdf.get("titulo").asString()).isEqualTo("Instrucciones de empaque.pdf");
        assertThat(resaltados(enElPdf)).contains("cinta", "frágil");
        assertThat(resultados(beto, "penalizacion demora").get(0).get("documentoId").asLong()).isEqualTo(docx);
        assertThat(jdbc.queryForObject("select count(*) from versiones_documento where documento_id in (?, ?) "
                + "and texto_extraido_en is not null", Integer.class, pdf, docx)).isEqualTo(2);
    }

    @Test
    @DisplayName("un documento borrado ya no se encuentra")
    void documentoBorrado() throws Exception {
        Long despacho = proceso("Despacho", "Del pago a la entrega");
        Long documento = subir(despacho, "Garantia.txt",
                "Cubre defectos de fabrica".getBytes(StandardCharsets.UTF_8));
        vaciarLaCola();
        assertThat(resultados(beto, "defectos")).hasSize(1);

        mvc.perform(delete("/api/v1/documentos/{id}", documento).with(principal(ana)))
                .andExpect(status().isNoContent());

        assertThat(resultados(beto, "defectos")).isEmpty();
    }

    @Test
    @DisplayName("los comentarios se encuentran, con su hilo y su elemento; uno borrado, no")
    void comentarios() throws Exception {
        Long despacho = proceso("Despacho", "Del pago a la entrega");
        Long empacar = actividadService.crear(empresaId, ana.usuarioId(), lane(despacho), "Empacar", null,
                TipoActividad.USUARIO, 160, 80).id();
        String tarea = bpmnIdDeNodo(empacar);
        Long hilo = comentarioService.comentar(empresaId, ana.usuarioId(), despacho,
                new ComentarioService.Nuevo("¿Quién revisa las etiquetas?", tarea, null, List.of())).id();
        Long respuesta = comentarioService.responder(empresaId, beto.usuarioId(), hilo,
                "Las etiquetas las revisa bodega", List.of()).id();
        Long borrado = comentar(despacho, "Etiquetas viejas en la caja");

        comentarioService.eliminar(empresaId, ana.usuarioId(), RolAcceso.ADMINISTRADOR, borrado);
        // Borrar un comentario lo vacia; aunque alguno quedara con su texto, marcado como borrado no se encuentra
        Long marcado = comentar(despacho, "Etiquetas de otro proveedor");
        jdbc.update("update comentarios set eliminado_en = localtimestamp where id = ?", marcado);
        JsonNode encontrados = resultados(carla, "etiquetas", "tipo", "COMENTARIO");

        assertThat(encontrados).hasSize(2);
        assertThat(encontrados.findValues("comentarioId")).extracting(JsonNode::asLong)
                .containsExactlyInAnyOrder(hilo, respuesta);
        assertThat(encontrados.findValues("hiloId")).extracting(JsonNode::asLong).containsOnly(hilo);
        assertThat(encontrados.findValuesAsString("bpmnId")).containsOnly(tarea);
    }

    @Test
    @DisplayName("la documentacion de un elemento lo encuentra por su bpmnId; la del proceso, al proceso")
    void documentacion() throws Exception {
        Long despacho = proceso("Despacho", "Del pago a la entrega");
        Long empacar = actividadService.crear(empresaId, ana.usuarioId(), lane(despacho), "Empacar", null,
                TipoActividad.USUARIO, 160, 80).id();
        String tarea = bpmnIdDeNodo(empacar);

        Long leida = documentacionService.guardar(empresaId, ana.usuarioId(), despacho, tarea,
                "Usar **cinta reforzada** en las cajas grandes", null).version();
        documentacionService.guardar(empresaId, ana.usuarioId(), despacho, null, "Aplica a pedidos nacionales", null);

        JsonNode elemento = resultados(beto, "reforzada").get(0);
        assertThat(elemento.get("tipo").asString()).isEqualTo("ELEMENTO");
        assertThat(elemento.get("bpmnId").asString()).isEqualTo(tarea);
        assertThat(elemento.get("titulo").asString()).isEqualTo("Empacar");
        assertThat(elemento.get("tipoDeElemento").asString()).isEqualTo("ACTIVIDAD");
        assertThat(elemento.get("version").isNull()).isTrue();
        assertThat(resaltados(elemento)).containsExactly("reforzada");
        // El nombre y la documentacion son un mismo elemento: las dos palabras juntas lo encuentran
        assertThat(tipos(resultados(beto, "empacar cajas"))).containsExactly("ELEMENTO");
        assertThat(tipos(resultados(beto, "nacionales"))).containsExactly("PROCESO");

        documentacionService.guardar(empresaId, ana.usuarioId(), despacho, tarea, "", leida);
        assertThat(resultados(beto, "reforzada")).isEmpty();
    }

    @Test
    @DisplayName("de la version publicada se encuentra lo que el borrador ya cambio; lo que esta en las dos, una vez")
    void versionPublicada() throws Exception {
        Long despacho = proceso("Despacho", "Del pago a la entrega");
        Long lane = lane(despacho);
        Long inicio = eventoService.crear(empresaId, ana.usuarioId(), lane, "Pedido recibido", TipoEvento.INICIO, 20,
                80).id();
        Long tarea = actividadService.crear(empresaId, ana.usuarioId(), lane, "Empacar", "Con cuidado",
                TipoActividad.USUARIO, 160, 80).id();
        Long fin = eventoService.crear(empresaId, ana.usuarioId(), lane, "Pedido enviado", TipoEvento.FIN, 320, 80)
                .id();
        arcoService.crear(empresaId, ana.usuarioId(), DatosDeArco.entre(inicio, tarea));
        arcoService.crear(empresaId, ana.usuarioId(), DatosDeArco.entre(tarea, fin));
        procesoService.cambiarEstado(empresaId, despacho, ana.usuarioId(), EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, despacho, false).version());

        JsonNode antes = resultados(beto, "empacar");
        assertThat(antes).hasSize(1);
        assertThat(antes.get(0).get("version").isNull()).isTrue();

        actividadService.editar(empresaId, ana.usuarioId(), tarea, "Embalar", "Con cuidado", TipoActividad.USUARIO,
                lane, 160, 80, actividadService.obtener(empresaId, tarea).version());

        JsonNode publicada = resultados(beto, "empacar");
        assertThat(publicada).hasSize(1);
        assertThat(publicada.get(0).get("version").asInt()).isEqualTo(1);
        assertThat(publicada.get(0).get("bpmnId").asString()).isEqualTo(bpmnIdDeNodo(tarea));
        assertThat(resultados(beto, "embalar").get(0).get("version").isNull()).isTrue();
        assertThat(resultados(beto, "cuidado")).hasSize(1);
    }

    @Test
    @DisplayName("quien no puede ver una carpeta no encuentra sus procesos, ni sus elementos, documentos o comentarios")
    void permisos() throws Exception {
        Long reservado = proceso("Nómina reservada", "Pagos del personal");
        Long publico = proceso("Nómina pública", "Calendario de pagos");
        Long carpeta = carpetaService.crear(empresaId, "Finanzas", null).id();
        procesoService.editarFicha(empresaId, reservado, ana.usuarioId(), new DatosDeFicha(carpeta, null, null,
                Clasificacion.CONFIDENCIAL, List.of(), procesoService.obtener(empresaId, reservado, false).version()));
        actividadService.crear(empresaId, ana.usuarioId(), lane(reservado), "Liquidar nómina", null,
                TipoActividad.USUARIO, 160, 80);
        comentar(reservado, "La nómina sale el viernes");
        subir(reservado, "Nomina.txt", "Planilla de nómina".getBytes(StandardCharsets.UTF_8));
        vaciarLaCola();
        concesionService.conceder(new Actor(ana.usuarioId(), empresaId, RolAcceso.ADMINISTRADOR),
                Recurso.carpeta(carpeta), Accion.VER, SujetoConcesion.PERSONA, beto.usuarioId(), null);

        assertThat(procesos(resultados(carla, "nomina"))).containsOnly(publico);
        assertThat(procesos(resultados(beto, "nomina"))).contains(reservado, publico);
        assertThat(tipos(resultados(beto, "nomina"))).contains("PROCESO", "ELEMENTO", "COMENTARIO", "DOCUMENTO");
        assertThat(procesos(resultados(ana, "nomina"))).contains(reservado, publico);
        // Otra tienda no encuentra nada de esta, aunque busque lo mismo
        assertThat(resultados(ajeno, "nomina")).isEmpty();
    }

    @Test
    @DisplayName("un proceso eliminado ya no se encuentra, ni lo que tiene adentro")
    void procesoEliminado() throws Exception {
        Long despacho = proceso("Despacho urgente", "Del pago a la entrega");
        comentar(despacho, "Despacho urgente por la tarde");

        procesoService.eliminarLogico(empresaId, despacho, ana.usuarioId());

        assertThat(resultados(ana, "urgente")).isEmpty();
    }

    @Test
    @DisplayName("la base tampoco deja ver el indice de otra tienda, aunque la consulta no filtre")
    void seguridadPorFilas() {
        Long despacho = proceso("Despacho", "Del pago a la entrega");
        Long otra = ajeno.empresaId();

        Integer propios = ContextoDeTienda.deLaTienda(empresaId,
                () -> jdbc.queryForObject("select count(*) from elementos_buscables where proceso_id = ?",
                        Integer.class, despacho));
        Integer ajenos = ContextoDeTienda.deLaTienda(otra,
                () -> jdbc.queryForObject("select count(*) from elementos_buscables where proceso_id = ?",
                        Integer.class, despacho));
        Integer vectores = ContextoDeTienda.deLaTienda(otra,
                () -> jdbc.queryForObject("select count(*) from procesos where busqueda is not null and id = ?",
                        Integer.class, despacho));

        assertThat(propios).isPositive();
        assertThat(ajenos).isZero();
        assertThat(vectores).isZero();
    }

    @Test
    @DisplayName("comillas, menos y or, como en un buscador web")
    void sintaxis() throws Exception {
        Long aprobacion = proceso("Aprobación de crédito", "Revisar la solicitud");
        Long hipotecario = proceso("Crédito hipotecario", "Avalúo de la vivienda");
        Long tarjeta = proceso("Pago con tarjeta", "Cobro en línea");

        assertThat(procesos(resultados(beto, "crédito -hipotecario"))).containsExactly(aprobacion);
        assertThat(procesos(resultados(beto, "\"aprobación de crédito\""))).containsExactly(aprobacion);
        assertThat(procesos(resultados(beto, "\"crédito de aprobación\""))).isEmpty();
        assertThat(procesos(resultados(beto, "tarjeta or hipotecario"))).containsExactlyInAnyOrder(tarjeta,
                hipotecario);
        // Lo que en to_tsquery seria un error de sintaxis aqui es solo texto
        buscar(beto, "crédito & | ! ( :*").andExpect(status().isOk());
    }

    @Test
    @DisplayName("vacia, o solo de palabras que no cuentan, responde una pagina vacia; mal pedida, 400; sin sesion, 401")
    void bordes() throws Exception {
        proceso("De la tienda", "Para el cliente");

        buscar(beto, "").andExpect(status().isOk()).andExpect(jsonPath(CONTENIDO).isEmpty())
                .andExpect(jsonPath(TOTAL).value(0));
        buscar(beto, "   ").andExpect(status().isOk()).andExpect(jsonPath(CONTENIDO).isEmpty());
        buscar(beto, "de la el").andExpect(status().isOk()).andExpect(jsonPath(CONTENIDO).isEmpty());
        mvc.perform(get(BUSQUEDA).with(principal(beto))).andExpect(status().isOk())
                .andExpect(jsonPath(CONTENIDO).isEmpty());
        buscar(beto, "x".repeat(201)).andExpect(status().isBadRequest());
        buscar(beto, "tienda", "tamano", "51").andExpect(status().isBadRequest());
        buscar(beto, "tienda", "pagina", "-1").andExpect(status().isBadRequest());
        buscar(beto, "tienda", "tipo", "OTRO").andExpect(status().isBadRequest());
        mvc.perform(get(BUSQUEDA).param("q", "tienda")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("se pagina de a 50 como mucho, y una pagina despues de la ultima dice cuantos hay")
    void paginas() throws Exception {
        Long primero = proceso("Inventario norte", "Bodega");
        Long segundo = proceso("Inventario sur", "Bodega");

        buscar(beto, "inventario", "tamano", "1").andExpect(jsonPath(TOTAL).value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content.length()").value(1));
        List<Long> dos = new ArrayList<>(procesos(resultados(beto, "inventario", "tamano", "1")));
        dos.addAll(procesos(resultados(beto, "inventario", "tamano", "1", "pagina", "1")));
        assertThat(dos).containsExactlyInAnyOrder(primero, segundo);
        buscar(beto, "inventario", "tamano", "1", "pagina", "5").andExpect(jsonPath(CONTENIDO).isEmpty())
                .andExpect(jsonPath(TOTAL).value(2));
    }

    @Test
    @DisplayName("cada rol busca, y el tipo deja solo lo de esa clase")
    void rolesYTipo() throws Exception {
        Long despacho = proceso("Despacho", "Revisar el despacho");
        comentar(despacho, "El despacho sale hoy");

        for (ApiPrincipal quien : List.of(ana, beto, carla)) {
            assertThat(tipos(resultados(quien, "despacho"))).contains("PROCESO", "COMENTARIO");
        }
        assertThat(tipos(resultados(beto, "despacho", "tipo", "PROCESO"))).containsOnly("PROCESO");
        assertThat(tipos(resultados(beto, "despacho", "tipo", "COMENTARIO"))).containsOnly("COMENTARIO");
        assertThat(resultados(beto, "despacho", "tipo", "DOCUMENTO")).isEmpty();
    }
}
