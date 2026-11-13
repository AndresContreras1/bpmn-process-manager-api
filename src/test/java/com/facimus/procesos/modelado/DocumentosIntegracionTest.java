package com.facimus.procesos.modelado;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.stream.Stream;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.facimus.procesos.common.almacen.EnlacesFirmados;
import com.facimus.procesos.common.antivirus.ClamdFalso;
import com.facimus.procesos.common.archivos.Archivos;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.gestion.service.impl.BorradoDeTiendas;
import com.facimus.procesos.security.ApiPrincipalRequestPostProcessor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 61: los documentos de punta a punta, por HTTP y con la seguridad de verdad: subir, revisar en la cola con un
 * clamd que habla el protocolo, bajar con el enlace firmado y sin sesion, y cada cosa que no tiene que pasar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentosIntegracionTest {

    private static final String CLAVE = "un-faro-sobre-la-bahia-quieta";
    private static final ClamdFalso CLAMD = ClamdFalso.arrancar();
    private static final Path ALMACEN = carpeta();
    private static final long MEGA = 1024 * 1024;
    private static int tiendas;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("antivirus.host", () -> "127.0.0.1");
        registro.add("antivirus.puerto", CLAMD::puerto);
        registro.add("almacen.directorio", ALMACEN::toString);
        registro.add("documentos.tamano-maximo", () -> "2MB");
        registro.add("documentos.cuota-por-tienda", () -> "5MB");
    }

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
    private ColaDeTrabajos cola;
    @Autowired
    private EnlacesFirmados enlaces;
    @Autowired
    private BorradoDeTiendas borradoDeTiendas;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;

    private Long empresaId;
    private ApiPrincipal ana;
    private ApiPrincipal beto;
    private ApiPrincipal carla;
    private ApiPrincipal ajeno;
    private Long proceso;

    private static Path carpeta() {
        try {
            return Files.createTempDirectory("almacen-documentos-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeEach
    void registrar() {
        int numero = ++tiendas;
        String correo = "ana" + numero + "@documentos.test";
        empresaId = empresaService.registrar("Documentos " + numero, "9611" + numero + "000-1", correo, "Ana", correo,
                CLAVE).id();
        ana = persona(correo, RolAcceso.ADMINISTRADOR);
        beto = persona(usuarioService.crearColaborador(empresaId, null, "Beto", "beto" + numero + "@documentos.test",
                CLAVE, RolAcceso.EDITOR).email(), RolAcceso.EDITOR);
        carla = persona(usuarioService.crearColaborador(empresaId, null, "Carla", "carla" + numero
                + "@documentos.test", CLAVE, RolAcceso.SOLO_LECTURA).email(), RolAcceso.SOLO_LECTURA);
        String otro = "otra" + numero + "@documentos.test";
        empresaService.registrar("Otra " + numero, "9612" + numero + "000-1", otro, "Otra", otro, CLAVE);
        ajeno = persona(otro, RolAcceso.ADMINISTRADOR);
        proceso = procesoService.crear(empresaId, ana.usuarioId(), "Despacho", "Descripcion", "Fulfillment").id();
        CLAMD.contestar(null);
    }

    private ApiPrincipal persona(String correo, RolAcceso rol) {
        var usuario = usuarioRepository.findByEmail(correo).orElseThrow();
        return new ApiPrincipal(usuario.getId(), usuario.getEmpresa().getId(), rol, correo,
                ApiPrincipalRequestPostProcessor.SESION, false);
    }

    private static RequestPostProcessor como(ApiPrincipal quien) {
        return principal(quien);
    }

    private ResultActions subir(ApiPrincipal quien, String nombre, byte[] contenido) throws Exception {
        return mvc.perform(post("/api/v1/procesos/{id}/documentos", proceso).param("nombre", nombre)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(contenido).with(como(quien)));
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private void revisarTodo() {
        while (cola.procesarUno()) {
            // la cola, hasta vaciarse
        }
    }

    private String enlace(long documento) throws Exception {
        return leer(mvc.perform(get("/api/v1/documentos/{id}/enlace", documento).with(como(carla)))
                .andExpect(status().isOk())).get("url").asString();
    }

    @Test
    @DisplayName("se sube, espera su revision, queda disponible y se baja sin sesion, como adjunto y con su tipo fijo")
    void deIdaYVuelta() throws Exception {
        JsonNode subido = leer(subir(beto, "Instrucciones de empaque.pdf", Archivos.PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.nombre").value("Instrucciones de empaque.pdf"))
                .andExpect(jsonPath("$.tipo").value("application/pdf")));
        long documento = subido.get("id").asLong();
        mvc.perform(get("/api/v1/documentos/{id}/enlace", documento).with(como(carla)))
                .andExpect(status().isConflict());

        revisarTodo();

        mvc.perform(get("/api/v1/documentos/{id}", documento).with(como(carla))).andExpect(status().isOk())
                .andExpect(jsonPath("$.documento.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.documento.revisado").value(true))
                .andExpect(jsonPath("$.versiones[0].sha256").isString());
        mvc.perform(get(enlace(documento)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(Archivos.PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Instrucciones de "
                        + "empaque.pdf\"; filename*=UTF-8''Instrucciones%20de%20empaque.pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(CLAMD.revisados()).isPositive();
    }

    @Test
    @DisplayName("un enlace cambiado, de otra version, de otra tienda o vencido responde 404")
    void enlacesMalos() throws Exception {
        long documento = leer(subir(beto, "a.pdf", Archivos.PDF)).get("id").asLong();
        revisarTodo();
        String bueno = enlace(documento);
        String vencido = "/api/v1/descargas/documentos/" + documento + "/1?empresa=" + empresaId + "&vence="
                + Instant.now().minusSeconds(5).getEpochSecond() + "&firma="
                + enlaces.firmar("documento:" + empresaId + ":" + documento + ":1",
                        Instant.ofEpochSecond(Instant.now().minusSeconds(5).getEpochSecond()));

        mvc.perform(get(bueno + "x")).andExpect(status().isNotFound());
        mvc.perform(get(bueno.replace("/1?", "/2?"))).andExpect(status().isNotFound());
        mvc.perform(get(bueno.replace("empresa=" + empresaId, "empresa=" + ajeno.empresaId())))
                .andExpect(status().isNotFound());
        mvc.perform(get(vencido)).andExpect(status().isNotFound());
        mvc.perform(get(bueno)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("un SVG con un script se acepta, pero se baja como binario: el navegador nunca lo dibuja")
    void svgConScript() throws Exception {
        long documento = leer(subir(beto, "logo.svg", Archivos.SVG_CON_SCRIPT).andExpect(status().isCreated()))
                .get("id").asLong();
        revisarTodo();

        mvc.perform(get(enlace(documento))).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"logo.svg\"; filename*=UTF-8''logo.svg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("un ejecutable o un HTML disfrazados se rechazan con 415, y no queda nada guardado")
    void disfrazados() throws Exception {
        subir(beto, "informe.pdf", Archivos.EXE).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Tipo de archivo no permitido"));
        subir(beto, "foto.png", Archivos.HTML).andExpect(status().isUnsupportedMediaType());

        assertThat(jdbc.queryForObject("select count(*) from documentos where empresa_id = ?", Integer.class,
                empresaId)).isZero();
        assertThat(archivosDe(empresaId)).isZero();
    }

    @Test
    @DisplayName("lo que el antivirus da por virus queda retenido, sin enlace y en la bitacora")
    void virus() throws Exception {
        long documento = leer(subir(beto, "nota.txt", ("antes " + ClamdFalso.VIRUS).getBytes())
                .andExpect(status().isCreated())).get("id").asLong();

        revisarTodo();

        mvc.perform(get("/api/v1/documentos/{id}", documento).with(como(beto)))
                .andExpect(jsonPath("$.documento.estado").value("BLOQUEADO"))
                .andExpect(jsonPath("$.documento.motivo").value("El antivirus encontró Prueba.Marca-De-Virus."));
        mvc.perform(get("/api/v1/documentos/{id}/enlace", documento).with(como(beto)))
                .andExpect(status().isConflict());
        // La bitacora se escribe despues del commit, en otro hilo: se espera a que llegue
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from bitacora where empresa_id = ? and recurso_id = ? "
                        + "and accion = 'DOCUMENTO_BLOQUEADO'",
                Integer.class, empresaId, documento)).isEqualTo(1));
    }

    @Test
    @DisplayName("una bomba con forma de documento de Word se retiene sin llegar al antivirus")
    void bomba() throws Exception {
        int antes = CLAMD.revisados();
        long documento = leer(subir(beto, "contrato.docx", Archivos.bomba(30)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                .get("id").asLong();

        revisarTodo();

        mvc.perform(get("/api/v1/documentos/{id}", documento).with(como(beto)))
                .andExpect(jsonPath("$.documento.estado").value("BLOQUEADO"))
                .andExpect(jsonPath("$.documento.motivo").value("El archivo crece demasiado al abrirlo."));
        assertThat(CLAMD.revisados()).isEqualTo(antes);
    }

    @Test
    @DisplayName("si el antivirus no contesta, el trabajo falla y el documento sigue pendiente, sin quedar a la mano")
    void antivirusCaido() throws Exception {
        CLAMD.contestar("algo raro");
        long documento = leer(subir(beto, "a.pdf", Archivos.PDF)).get("id").asLong();

        revisarTodo();

        mvc.perform(get("/api/v1/documentos/{id}", documento).with(como(beto)))
                .andExpect(jsonPath("$.documento.estado").value("PENDIENTE"));
        assertThat(jdbc.queryForObject("select count(*) from trabajos where tipo = 'documentos.revisar' "
                + "and empresa_id = ? and estado = 'PENDIENTE' and intentos = 1", Integer.class, empresaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("mas grande que el tope responde 413; la cuota de la tienda tambien, con lo que ya ocupa")
    void topes() throws Exception {
        subir(beto, "grande.txt", texto(3 * MEGA)).andExpect(status().isContentTooLarge());
        subir(beto, "uno.txt", texto(2 * MEGA)).andExpect(status().isCreated());
        subir(beto, "dos.txt", texto(2 * MEGA)).andExpect(status().isCreated());

        subir(beto, "tres.txt", texto(2 * MEGA)).andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.detail").value("La organización llegó a su espacio para documentos."));
    }

    private static byte[] texto(long bytes) {
        return "a".repeat((int) bytes).getBytes();
    }

    @Test
    @DisplayName("quien solo lee los ve y los baja pero no sube; otra tienda no los ve ni por el proceso ni por el id")
    void permisos() throws Exception {
        long documento = leer(subir(beto, "a.pdf", Archivos.PDF)).get("id").asLong();
        revisarTodo();

        subir(carla, "b.pdf", Archivos.PDF).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/procesos/{id}/documentos", proceso).with(como(carla))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/procesos/{id}/documentos", proceso).with(como(ajeno)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documentos/{id}", documento).with(como(ajeno))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documentos/{id}/enlace", documento).with(como(ajeno)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/documentos/{id}", documento).with(como(ajeno))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/documentos/{id}/versiones", documento).param("nombre", "x.pdf")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(Archivos.PDF).with(como(ajeno)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/procesos/{id}/documentos", proceso)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un documento va pegado a un elemento del proceso por su bpmnId, y solo a uno que exista")
    void deUnElemento() throws Exception {
        String pool = jdbc.queryForObject("select bpmn_id from pools where proceso_id = ?", String.class, proceso);

        mvc.perform(post("/api/v1/procesos/{id}/documentos", proceso).param("nombre", "pool.pdf")
                .param("elemento", pool).contentType(MediaType.APPLICATION_OCTET_STREAM).content(Archivos.PDF)
                .with(como(beto))).andExpect(status().isCreated()).andExpect(jsonPath("$.bpmnId").value(pool));
        subir(beto, "proceso.pdf", Archivos.PDF).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/procesos/{id}/documentos", proceso).param("nombre", "x.pdf")
                .param("elemento", "Activity_no_existe").contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(Archivos.PDF).with(como(beto))).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/procesos/{id}/documentos", proceso).param("elemento", pool).with(como(beto)))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].nombre").value("pool.pdf"));
    }

    @Test
    @DisplayName("las versiones se acumulan, la mas nueva primero, y cada una se puede bajar")
    void versiones() throws Exception {
        long documento = leer(subir(beto, "a.pdf", Archivos.PDF)).get("id").asLong();
        mvc.perform(post("/api/v1/documentos/{id}/versiones", documento).param("nombre", "a v2.txt")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(Archivos.TEXTO).with(como(beto)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionActual").value(2));
        revisarTodo();

        mvc.perform(get("/api/v1/documentos/{id}", documento).with(como(beto)))
                .andExpect(jsonPath("$.documento.nombre").value("a v2.txt"))
                .andExpect(jsonPath("$.versiones[0].numero").value(2))
                .andExpect(jsonPath("$.versiones[1].numero").value(1));
        String primera = leer(mvc.perform(get("/api/v1/documentos/{id}/enlace", documento).param("version", "1")
                .with(como(beto)))).get("url").asString();
        mvc.perform(get(primera)).andExpect(content().bytes(Archivos.PDF));
    }

    @Test
    @DisplayName("borrado, el documento deja de verse y su enlace deja de servir")
    void borrar() throws Exception {
        long documento = leer(subir(beto, "a.pdf", Archivos.PDF)).get("id").asLong();
        revisarTodo();
        String url = enlace(documento);

        mvc.perform(delete("/api/v1/documentos/{id}", documento).with(como(beto))).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/documentos/{id}", documento).with(como(beto))).andExpect(status().isNotFound());
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/procesos/{id}/historial", proceso).with(como(ana)))
                .andExpect(jsonPath("$[*].descripcionCambio").value(hasItem("Documento \"a.pdf\" eliminado.")));
    }

    @Test
    @DisplayName("al borrar la tienda se van sus documentos, de la base y del almacen; los de otra se quedan")
    void borrarLaTienda() throws Exception {
        subir(beto, "a.pdf", Archivos.PDF).andExpect(status().isCreated());
        Long otra = ajeno.empresaId();
        Long procesoAjeno = procesoService.crear(otra, ajeno.usuarioId(), "Ajeno", "d", "c").id();
        mvc.perform(post("/api/v1/procesos/{id}/documentos", procesoAjeno).param("nombre", "b.pdf")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(Archivos.PDF).with(como(ajeno)))
                .andExpect(status().isCreated());
        assertThat(archivosDe(empresaId)).isEqualTo(1);

        borradoDeTiendas.borrar(empresaId, LocalDateTime.now());
        revisarTodo();

        assertThat(archivosDe(empresaId)).isZero();
        assertThat(archivosDe(otra)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from documentos where empresa_id = ?", Integer.class,
                empresaId)).isZero();
    }

    private static long archivosDe(Long empresa) throws IOException {
        Path carpeta = ALMACEN.resolve("empresas/" + empresa);
        if (!Files.exists(carpeta)) {
            return 0;
        }
        try (Stream<Path> archivos = Files.walk(carpeta)) {
            return archivos.filter(Files::isRegularFile).count();
        }
    }
}
