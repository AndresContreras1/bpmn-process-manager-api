package com.facimus.procesos.gestion;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.almacen.EnlacesFirmados;
import com.facimus.procesos.common.archivos.Archivos;
import com.facimus.procesos.common.correo.Correo;
import com.facimus.procesos.common.correo.CorreoSaliente;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.Base32;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.common.security.Totp;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.ejecucion.TiendaConMensajeria;
import com.facimus.procesos.ejecucion.service.DatosDelEntrante;
import com.facimus.procesos.ejecucion.service.MensajeriaService;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.dto.response.InscripcionTotpResponse;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.AnclaDeBitacoraService;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ExportacionService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.SegundoFactorService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.service.ComentarioService;
import com.facimus.procesos.modelado.service.DatosDeCaracterizacion;
import com.facimus.procesos.modelado.service.DocumentacionService;
import com.facimus.procesos.security.ApiPrincipalRequestPostProcessor;
import com.facimus.procesos.security.SesionEnCookies;
import com.facimus.procesos.security.TokenDeAcceso;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 66: la exportacion completa de punta a punta, por HTTP y con la seguridad de verdad: una tienda con procesos,
 * versiones, documentos, comentarios, casos, personas con segundo factor y sesiones, y su bitacora con un ancla. La
 * cola arma el ZIP, el correo lleva el enlace, el enlace lo baja sin sesion, y el ZIP se abre y se revisa entero:
 * su disposicion, el manifiesto contra cada archivo, la cadena de la bitacora recalculada fuera, el CSV sin formulas
 * y ningun secreto de nadie. Despues, quien puede pedirla, el step-up, lo de otra tienda, el vencimiento, y la baja con
 * su certificado de borrado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExportacionIntegracionTest {

    private static final String CLAVE = "un-faro-sobre-la-bahia-quieta";
    private static final String EXPORTACIONES = "/api/v1/organizacion/exportaciones";
    private static final String WEB = "https://tienda.example";
    private static final Path ALMACEN = carpeta();
    private static int tiendas;

    /**
     * Un almacen propio y nuevo en cada corrida, como el de la prueba del ancla: el ancla de un dia se escribe una
     * vez y queda de solo lectura, y una corrida anterior con los mismos ids la dejaria escrita.
     */
    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("almacen.directorio", ALMACEN::toString);
        registro.add("app.url-publica", () -> WEB);
    }

    private static Path carpeta() {
        try {
            return Files.createTempDirectory("almacen-exportaciones-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

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
    private SegundoFactorService segundoFactorService;
    @Autowired
    private DocumentacionService documentacionService;
    @Autowired
    private ComentarioService comentarioService;
    @Autowired
    private MensajeriaService mensajeriaService;
    @Autowired
    private AnclaDeBitacoraService anclas;
    @Autowired
    private ExportacionService exportacionService;
    @Autowired
    private EnlacesFirmados enlaces;
    @Autowired
    private ColaDeTrabajos cola;
    @Autowired
    private ApplicationContext contexto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;

    private int numero;
    private long documentoBorrado;
    private String nombre;
    private Long empresaId;
    private ApiPrincipal ana;
    private ApiPrincipal beto;
    private ApiPrincipal carla;
    private Long otraId;
    private ApiPrincipal ajeno;

    @BeforeEach
    void registrar() {
        numero = ++tiendas;
        nombre = "Exporta " + numero;
        String correoAna = "ana" + numero + "@exporta.test";
        empresaId = empresaService.registrar(nombre, "9613" + numero + "000-1", correoAna, "Ana", correoAna, CLAVE)
                .id();
        ana = persona(correoAna, RolAcceso.ADMINISTRADOR);
        beto = persona(usuarioService.crearColaborador(empresaId, null, "Beto", "beto" + numero + "@exporta.test",
                CLAVE, RolAcceso.EDITOR).email(), RolAcceso.EDITOR);
        carla = persona(usuarioService.crearColaborador(empresaId, null, "Carla", "carla" + numero
                + "@exporta.test", CLAVE, RolAcceso.SOLO_LECTURA).email(), RolAcceso.SOLO_LECTURA);
        String otro = "otra" + numero + "@exporta.test";
        otraId = empresaService.registrar("Otra " + numero, "9614" + numero + "000-1", otro, "Otra", otro, CLAVE).id();
        ajeno = persona(otro, RolAcceso.ADMINISTRADOR);
        procesoService.crear(otraId, ajeno.usuarioId(), "Ajeno " + numero, "De la otra tienda", "Fulfillment");
        clearInvocations(correo);
    }

    private ApiPrincipal persona(String correoDeLaPersona, RolAcceso rol) {
        var usuario = usuarioRepository.findByEmail(correoDeLaPersona).orElseThrow();
        return new ApiPrincipal(usuario.getId(), usuario.getEmpresa().getId(), rol, correoDeLaPersona,
                ApiPrincipalRequestPostProcessor.SESION, false);
    }

    private static RequestPostProcessor como(ApiPrincipal quien) {
        return principal(quien);
    }

    /** La misma persona, con su ultima verificacion de identidad de hace ese tiempo. */
    private static RequestPostProcessor verificadoHace(ApiPrincipal quien, Duration hace) {
        return SecurityMockMvcRequestPostProcessors.authentication(new UsernamePasswordAuthenticationToken(quien, null,
                new TokenDeAcceso(quien, EnumSet.of(Factor.PASSWORD), Instant.now().minus(hace)).autoridades()));
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private void vaciarLaCola() {
        while (cola.procesarUno()) {
            // la cola, hasta vaciarse
        }
    }

    private long pedir(ApiPrincipal quien) throws Exception {
        return leer(mvc.perform(post(EXPORTACIONES).with(como(quien))).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))).get("id").asLong();
    }

    /** El enlace del ultimo correo de una exportacion a esa persona, sin la direccion de la web. */
    private String enlaceDelCorreo(ApiPrincipal quien) {
        ArgumentCaptor<CorreoSaliente> enviados = ArgumentCaptor.forClass(CorreoSaliente.class);
        verify(correo, atLeastOnce()).enviar(enviados.capture());
        CorreoSaliente enviado = enviados.getAllValues().stream()
                .filter(uno -> uno.para().equals(quien.email()) && uno.asunto().contains("exportación"))
                .reduce((primero, segundo) -> segundo).orElseThrow();
        String texto = enviado.texto();
        int desde = texto.indexOf(WEB + "/api/v1/descargas/exportaciones/");
        assertThat(desde).as("el correo lleva el enlace a la API por la direccion de la web").isNotNegative();
        return texto.substring(desde + WEB.length(), texto.indexOf('\n', desde)).strip();
    }

    /** La tienda con un poco de cada cosa, y lo que no tiene que salir de ella. */
    private List<String> llenarLaTienda() throws Exception {
        Long proceso = new TiendaConMensajeria(contexto).publicarLaDemo(empresaId, ana.usuarioId(),
                "Fulfillment " + numero);
        documentacionService.guardar(empresaId, ana.usuarioId(), proceso, null, "# Despacho\n\nDel pago a la entrega.",
                null);
        documentacionService.guardarCaracterizacion(empresaId, ana.usuarioId(), proceso, new DatosDeCaracterizacion(
                "Entregar a tiempo", null, null, null, null, null, null, null, null, null));
        comentarioService.comentar(empresaId, beto.usuarioId(), proceso, new ComentarioService.Nuevo(
                "Quien revisa la **direccion**?", null, null, List.of()));
        mvc.perform(post("/api/v1/procesos/{id}/documentos", proceso).param("nombre", "Guía de empaque.pdf")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(Archivos.PDF).with(como(beto)))
                .andExpect(status().isCreated());
        // Uno que se borra antes de exportar: no sale ni en la lista ni con sus bytes
        long borrado = leer(mvc.perform(post("/api/v1/procesos/{id}/documentos", proceso)
                .param("nombre", "Borrador viejo.pdf").contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(Archivos.PDF).with(como(beto))).andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(delete("/api/v1/documentos/{id}", borrado).with(como(ana))).andExpect(status().isNoContent());
        documentoBorrado = borrado;
        mensajeriaService.recibir(empresaId, proceso, DatosDelEntrante.aMano(TiendaConMensajeria.PEDIDO, null,
                Map.of("orderId", "=1+2"), "exporta-" + numero));
        vaciarLaCola();

        // Los secretos de la gente: el TOTP de Beto con sus codigos, y la sesion de verdad de Ana
        InscripcionTotpResponse totp = segundoFactorService.inscribir(empresaId, beto.usuarioId());
        List<String> codigos = segundoFactorService.confirmar(empresaId, beto.usuarioId(), Totp.codigo(
                Base32.decodificar(totp.secreto()), Totp.paso(Instant.now()), Totp.DIGITOS)).codigos();
        mvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(ana.email(), CLAVE)))).andExpect(status().isOk());
        List<String> secretos = new ArrayList<>();
        secretos.add(CLAVE);
        secretos.add(totp.secreto());
        secretos.addAll(codigos);
        secretos.addAll(columna("select password_hash from usuarios where empresa_id = ?"));
        secretos.addAll(columna("select secreto_cifrado from segundos_factores where empresa_id = ?"));
        secretos.addAll(columna("select codigo_hash from codigos_de_recuperacion where empresa_id = ?"));
        secretos.addAll(columna("select codigo from sesiones where empresa_id = ?"));
        secretos.addAll(columna("select token_hash from refresh_tokens where empresa_id = ?"));
        secretos.addAll(columna("select token_hash from enlaces_de_un_uso where empresa_id = ?"));
        assertThat(secretos).hasSizeGreaterThan(15);

        // La bitacora se escribe despues de cada commit; con algo en ella, el ancla de hoy
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "select count(*) from bitacora where empresa_id = ?", Integer.class, empresaId) > 2);
        ContextoDeTienda.comoSistema(() -> anclas.anclar(LocalDate.now(ZoneOffset.UTC)));
        return secretos;
    }

    private List<String> columna(String consulta) {
        return jdbc.queryForList(consulta, String.class, empresaId);
    }

    @Test
    @DisplayName("se pide, la cola la arma, el correo lleva el enlace, el enlace baja el ZIP sin sesion, y el ZIP "
            + "tiene cada cosa de la tienda con sus huellas, su bitacora verificable y ningun secreto")
    void deIdaYVuelta() throws Exception {
        List<String> secretos = llenarLaTienda();

        long exportacion = pedir(ana);
        mvc.perform(get(EXPORTACIONES).with(como(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(exportacion))
                .andExpect(jsonPath("$.size").value(20));
        vaciarLaCola();

        JsonNode lista = leer(mvc.perform(get(EXPORTACIONES + "/{id}", exportacion).with(como(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LISTA"))
                .andExpect(jsonPath("$.pedidaPor").value(ana.usuarioId())));
        byte[] zip = bajarSinSesion(enlaceDelCorreo(ana));

        assertThat(Huella.de(zip)).isEqualTo(lista.get("sha256").asString());
        assertThat((long) zip.length).isEqualTo(lista.get("tamano").asLong());
        Map<String, byte[]> archivos = abrir(zip);
        String carpeta = disposicion(archivos);
        loDelProceso(archivos, carpeta);
        sinElDocumentoBorrado(archivos, carpeta);
        anclasSinClaves(archivos);
        manifiestoContraCadaArchivo(archivos, exportacion);
        personasSinSecretos(archivos);
        cadenaVerificableFuera(archivos);
        assertThat(json.readTree(archivos.get("bitacora/verificacion.json")).get("integra").asBoolean()).isTrue();
        assertThat(texto(archivos, "casos/casos.csv")).startsWith("id,procesoId,versionDelProceso,referencia,")
                .contains(",'=1+2,").doesNotContain(",=1+2,");
        ningunSecretoNiNadaAjeno(archivos, secretos);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForList(
                "select accion from bitacora where empresa_id = ? and recurso = 'EXPORTACION' and recurso_id = ? "
                        + "order by secuencia", String.class, empresaId, exportacion))
                .containsExactly("EXPORTACION_PEDIDA", "EXPORTACION_LISTA", "EXPORTACION_DESCARGADA"));
    }

    /** El enlace baja el ZIP sin sesion, como un adjunto que el navegador no interpreta ni guarda en cache. */
    private byte[] bajarSinSesion(String enlace) throws Exception {
        return mvc.perform(get(enlace))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition",
                        startsWith("attachment; filename=\"exportacion-")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
    }

    /** Cada archivo de la tienda en su lugar; devuelve la carpeta del proceso. */
    private String disposicion(Map<String, byte[]> archivos) {
        String carpeta = archivos.keySet().stream().filter(ruta -> ruta.endsWith("/proceso.json"))
                .map(ruta -> ruta.substring(0, ruta.lastIndexOf('/'))).findFirst().orElseThrow();
        assertThat(carpeta).matches("procesos/\\d+-fulfillment-" + numero + "-\\d+");
        assertThat(archivos).containsKeys("manifest.json", "organizacion.json", "personas.json", "roles.json",
                "membresias.json", "carpetas.json", "concesiones.json", "procesos/procesos.json", "historial.json",
                "comentarios.json", "bitacora/bitacora.json", "bitacora/verificacion.json", "bitacora/anclas.json",
                "casos/casos.json", "casos/casos.csv", "casos/actividades.json", "casos/eventos.json",
                "casos/mensajes-entrantes.json", "casos/mensajes-salientes.json",
                carpeta + "/versiones/versiones.json", carpeta + "/versiones/v1.json", carpeta + "/versiones/v1.bpmn",
                carpeta + "/borrador.bpmn", carpeta + "/borrador.json", carpeta + "/documentacion.json",
                carpeta + "/caracterizacion.json", carpeta + "/documentos/documentos.json");
        return carpeta;
    }

    /** El modelo, la documentacion, la caracterizacion, los comentarios y el documento, con su contenido. */
    private void loDelProceso(Map<String, byte[]> archivos, String carpeta) {
        assertThat(texto(archivos, carpeta + "/versiones/v1.bpmn")).contains("definitions", "Fulfillment " + numero);
        assertThat(texto(archivos, carpeta + "/borrador.bpmn")).contains("definitions");
        assertThat(texto(archivos, carpeta + "/documentacion.json")).contains("Del pago a la entrega.");
        assertThat(texto(archivos, carpeta + "/caracterizacion.json")).contains("Entregar a tiempo");
        assertThat(texto(archivos, "comentarios.json")).contains("Quien revisa la **direccion**?");
        assertThat(archivos).containsEntry(carpeta + "/documentos/" + documento(archivos, carpeta)
                + "/v1-Guia-de-empaque.pdf", Archivos.PDF);
    }

    /** El documento borrado antes de exportar no sale ni en la lista ni con sus bytes. */
    private void sinElDocumentoBorrado(Map<String, byte[]> archivos, String carpeta) {
        JsonNode documentos = json.readTree(archivos.get(carpeta + "/documentos/documentos.json"));
        assertThat(documentos).as("el borrado no esta en la lista").hasSize(1);
        assertThat(documentos.get(0).get("id").asLong()).isNotEqualTo(documentoBorrado);
        assertThat(archivos.keySet()).as("ni sus bytes").noneMatch(ruta -> ruta.startsWith(carpeta + "/documentos/"
                + documentoBorrado + "/") || ruta.contains("Borrador-viejo"));
    }

    /** Las anclas salen con su dia y su hash, y con el archivo del almacen inmutable, pero sin su clave. */
    private void anclasSinClaves(Map<String, byte[]> archivos) {
        assertThat(json.readTree(archivos.get("bitacora/anclas.json"))).allSatisfy(ancla -> assertThat(
                ancla.propertyNames()).as("ninguna clave del almacen sale").contains("dia", "hash")
                .doesNotContain("clave"));
        assertThat(archivos.keySet()).anyMatch(ruta -> ruta.startsWith("bitacora/anclas/") && ruta.endsWith(".json"));
    }

    /** Ningun archivo lleva un secreto de la gente ni algo de la otra tienda. */
    private void ningunSecretoNiNadaAjeno(Map<String, byte[]> archivos, List<String> secretos) {
        for (Map.Entry<String, byte[]> archivo : archivos.entrySet()) {
            String contenido = new String(archivo.getValue(), StandardCharsets.UTF_8);
            assertThat(secretos).as(archivo.getKey()).noneMatch(contenido::contains);
            assertThat(contenido).as(archivo.getKey()).doesNotContain("Ajeno " + numero, ajeno.email());
        }
    }

    private String documento(Map<String, byte[]> archivos, String carpeta) {
        return json.readTree(archivos.get(carpeta + "/documentos/documentos.json")).get(0).get("id").asString();
    }

    /** El manifiesto lista cada archivo con su tamano y su SHA-256, la tienda, quien la pidio, cuando y el formato. */
    private void manifiestoContraCadaArchivo(Map<String, byte[]> archivos, long exportacion) {
        JsonNode manifiesto = json.readTree(archivos.get("manifest.json"));
        assertThat(manifiesto.get("formato").asInt()).isEqualTo(1);
        assertThat(manifiesto.get("exportacion").asLong()).isEqualTo(exportacion);
        assertThat(manifiesto.get("tienda").get("id").asLong()).isEqualTo(empresaId);
        assertThat(manifiesto.get("tienda").get("nombre").asString()).isEqualTo(nombre);
        assertThat(manifiesto.get("pedidaPor").get("email").asString()).isEqualTo(ana.email());
        assertThat(manifiesto.get("pedidaEn").asString()).isNotBlank();
        List<String> listados = new ArrayList<>();
        for (JsonNode archivo : manifiesto.get("archivos")) {
            String ruta = archivo.get("ruta").asString();
            listados.add(ruta);
            assertThat(archivo.get("tamano").asLong()).as(ruta).isEqualTo(archivos.get(ruta).length);
            assertThat(archivo.get("sha256").asString()).as(ruta).isEqualTo(Huella.de(archivos.get(ruta)));
        }
        List<String> enElZip = new ArrayList<>(archivos.keySet());
        enElZip.remove("manifest.json");
        assertThat(listados).containsExactlyElementsOf(enElZip);
    }

    /** Las personas de la tienda, y de cada una nada que sirva para entrar. */
    private void personasSinSecretos(Map<String, byte[]> archivos) {
        JsonNode personas = json.readTree(archivos.get("personas.json"));
        assertThat(personas).hasSize(3);
        List<String> correos = new ArrayList<>();
        personas.forEach(persona -> {
            correos.add(persona.get("email").asString());
            assertThat(persona.propertyNames()).doesNotContain("passwordHash", "webauthnId")
                    .contains("nombre", "email", "rolAcceso", "activo");
        });
        assertThat(correos).containsExactlyInAnyOrder(ana.email(), beto.email(), carla.email());
    }

    /**
     * Fuera de la API, solo con el JSON: cada registro sigue al anterior, empieza en el genesis, y su hash es el
     * SHA-256 del arreglo JSON de sus campos en orden, como dice docs/export-format.md.
     */
    private void cadenaVerificableFuera(Map<String, byte[]> archivos) {
        JsonNode bitacora = json.readTree(archivos.get("bitacora/bitacora.json"));
        assertThat(bitacora).hasSizeGreaterThan(2);
        String anterior = "0".repeat(64);
        long secuencia = 0;
        for (JsonNode registro : bitacora) {
            assertThat(registro.get("secuencia").asLong()).isEqualTo(++secuencia);
            assertThat(registro.get("hashAnterior").asString()).isEqualTo(anterior);
            List<Object> campos = new ArrayList<>();
            for (String campo : List.of("empresaId", "secuencia", "momento", "actorTipo", "actorId", "accion",
                    "recurso", "recursoId", "exito", "ip", "userAgent", "requestId", "traceId", "detalle",
                    "hashAnterior")) {
                campos.add(comoJava(registro.get(campo)));
            }
            assertThat(Huella.de(json.writeValueAsString(campos))).as("registro %d", secuencia)
                    .isEqualTo(registro.get("hash").asString());
            anterior = registro.get("hash").asString();
        }
        JsonNode ancla = json.readTree(archivos.get("bitacora/anclas.json")).get(0);
        assertThat(texto(archivos, "bitacora/anclas/" + ancla.get("dia").asString() + ".json"))
                .contains(ancla.get("hash").asString());
    }

    /** Un valor del JSON como lo tenia Java al calcular el hash: nulo, numero, booleano o texto. */
    private static Object comoJava(JsonNode valor) {
        if (valor.isNull()) {
            return null;
        }
        if (valor.isNumber()) {
            return valor.asLong();
        }
        return valor.isBoolean() ? valor.asBoolean() : valor.asString();
    }

    @Test
    @DisplayName("solo el administrador la pide y la mira; sin sesion, 401; un enlace sin firma no baja nada")
    void soloElAdministrador() throws Exception {
        mvc.perform(post(EXPORTACIONES).with(como(beto))).andExpect(status().isForbidden());
        mvc.perform(post(EXPORTACIONES).with(como(carla))).andExpect(status().isForbidden());
        mvc.perform(get(EXPORTACIONES).with(como(beto))).andExpect(status().isForbidden());
        mvc.perform(get(EXPORTACIONES).with(como(carla))).andExpect(status().isForbidden());
        mvc.perform(post(EXPORTACIONES).with(SesionEnCookies.conCsrf())).andExpect(status().isUnauthorized());
        long exportacion = pedir(ana);
        mvc.perform(get(EXPORTACIONES + "/{id}", exportacion).with(como(beto))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/descargas/exportaciones/{id}", exportacion).param("empresa", empresaId.toString())
                .param("vence", String.valueOf(Instant.now().plusSeconds(60).getEpochSecond()))
                .param("firma", "inventada")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from exportaciones where empresa_id = ?", Integer.class,
                empresaId)).isOne();
    }

    @Test
    @DisplayName("pedirla pide haber confirmado la identidad hace menos de diez minutos; mirarla no")
    void conStepUp() throws Exception {
        mvc.perform(post(EXPORTACIONES).with(verificadoHace(ana, Duration.ofMinutes(11))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Verificación requerida"));
        mvc.perform(get(EXPORTACIONES).with(verificadoHace(ana, Duration.ofMinutes(11)))).andExpect(status().isOk());
        mvc.perform(post(EXPORTACIONES).with(verificadoHace(ana, Duration.ofMinutes(9))))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("una de otra tienda no existe: ni por id, ni con su enlace cambiado de tienda, ni para la base")
    void deOtraTienda() throws Exception {
        long exportacion = pedir(ana);
        vaciarLaCola();
        String enlace = enlaceDelCorreo(ana);

        mvc.perform(get(EXPORTACIONES + "/{id}", exportacion).with(como(ajeno))).andExpect(status().isNotFound());
        mvc.perform(get(EXPORTACIONES).with(como(ajeno))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + exportacion + ")]").isEmpty());
        mvc.perform(get(enlace.replace("empresa=" + empresaId, "empresa=" + otraId)))
                .andExpect(status().isNotFound());
        Integer vistas = ContextoDeTienda.deLaTienda(otraId, () -> jdbc.queryForObject(
                "select count(*) from exportaciones where empresa_id = ?", Integer.class, empresaId));
        assertThat(vistas).as("la base no le muestra la fila a otra tienda").isZero();
    }

    @Test
    @DisplayName("una a la vez por tienda: con una en curso la segunda responde 409, y lista ya se puede pedir otra")
    void unaALaVez() throws Exception {
        pedir(ana);
        mvc.perform(post(EXPORTACIONES).with(como(ana))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("en curso")));
        mvc.perform(post(EXPORTACIONES).with(como(ajeno))).andExpect(status().isAccepted());

        vaciarLaCola();

        pedir(ana);
    }

    @Test
    @DisplayName("el enlace vence: vencida no se baja, la limpieza la borra del almacen y lo anota; un enlace con el "
            + "vencimiento cambiado tampoco vale")
    void vence() throws Exception {
        long exportacion = pedir(ana);
        vaciarLaCola();
        String enlace = enlaceDelCorreo(ana);
        String clave = jdbc.queryForObject("select clave from exportaciones where id = ?", String.class, exportacion);
        assertThat(clave).matches("empresas/" + empresaId + "/exportaciones/" + exportacion
                + "/[0-9a-f-]{36}/organizacion");
        Path zip = ALMACEN.resolve(clave);
        assertThat(zip).exists();
        Instant antes = Instant.now().minusSeconds(5).truncatedTo(ChronoUnit.SECONDS);
        mvc.perform(get("/api/v1/descargas/exportaciones/{id}", exportacion).param("empresa", empresaId.toString())
                .param("vence", String.valueOf(antes.getEpochSecond()))
                .param("firma", enlaces.firmar("exportacion:" + empresaId + ":" + exportacion, antes)))
                .andExpect(status().isNotFound());

        // En UTC, como Hibernate guarda las fechas (hibernate.jdbc.time_zone)
        jdbc.update("update exportaciones set vence_en = ? where id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), exportacion);
        mvc.perform(get(enlace)).andExpect(status().isNotFound());
        assertThat(ContextoDeTienda.comoSistema(exportacionService::limpiar)).isPositive();

        mvc.perform(get(EXPORTACIONES + "/{id}", exportacion).with(como(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VENCIDA"))
                .andExpect(jsonPath("$.sha256").isNotEmpty());
        assertThat(zip).doesNotExist();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForList(
                "select accion from bitacora where empresa_id = ? and recurso_id = ? and recurso = 'EXPORTACION'",
                String.class, empresaId, exportacion)).contains("EXPORTACION_VENCIDA"));
    }

    @Test
    @DisplayName("la lista va de a una pagina de hasta 50, de la mas nueva a la mas vieja")
    void listaPaginada() throws Exception {
        long primera = pedir(ana);
        vaciarLaCola();
        long segunda = pedir(ana);

        mvc.perform(get(EXPORTACIONES).param("tamano", "1").with(como(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(segunda))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(EXPORTACIONES).param("tamano", "1").param("pagina", "1").with(como(ana)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(primera));
        mvc.perform(get(EXPORTACIONES).param("tamano", "51").with(como(ana))).andExpect(status().isBadRequest());
        mvc.perform(get(EXPORTACIONES).param("pagina", "-1").with(como(ana))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("la base garantiza una en curso por tienda y que una LISTA tenga su archivo y su huella")
    void laBaseLoGarantiza() throws Exception {
        pedir(ana);
        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);

        assertThatThrownBy(() -> ContextoDeTienda.comoSistema(() -> jdbc.update(
                "insert into exportaciones (empresa_id, estado, pedida_por, pedida_en) values (?, 'PENDIENTE', ?, ?)",
                empresaId, ana.usuarioId(), ahora))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> ContextoDeTienda.comoSistema(() -> jdbc.update("""
                insert into exportaciones (empresa_id, estado, pedida_por, pedida_en, vence_en, tamano)
                values (?, 'LISTA', ?, ?, ?, 1)
                """, empresaId, ana.usuarioId(), ahora, ahora.plusHours(24))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(ContextoDeTienda.comoSistema(() -> jdbc.update(
                "insert into exportaciones (empresa_id, estado, pedida_por, pedida_en) values (?, 'FALLIDA', ?, ?)",
                empresaId, ana.usuarioId(), ahora))).as("otra que no esta en curso, si").isOne();
    }

    @Test
    @DisplayName("el enlace es de un administrador activo: si a quien la pidio le quitan el rol, el correo no sale y "
            + "el enlace que ya tenia deja de bajar")
    void soloUnAdministradorActivo() throws Exception {
        long primera = pedir(ana);
        vaciarLaCola();
        String enlace = enlaceDelCorreo(ana);
        mvc.perform(get(enlace)).andExpect(status().isOk());

        jdbc.update("update usuarios set rol_acceso = 'EDITOR' where id = ?", ana.usuarioId());
        mvc.perform(get(enlace)).andExpect(status().isNotFound());

        jdbc.update("update usuarios set rol_acceso = 'ADMINISTRADOR' where id = ?", ana.usuarioId());
        long segunda = pedir(ana);
        jdbc.update("update usuarios set rol_acceso = 'EDITOR' where id = ?", ana.usuarioId());
        clearInvocations(correo);
        vaciarLaCola();
        verify(correo, never()).enviar(argThat(uno -> uno.asunto()
                .contains("exportación")));
        assertThat(jdbc.queryForObject("select estado from exportaciones where id = ?", String.class, segunda))
                .isEqualTo("LISTA");
        assertThat(primera).isNotEqualTo(segunda);
    }

    @Test
    @DisplayName("dar de baja con exportar y una ya en curso: la baja sale y no se pide otra")
    void bajaConUnaEnCurso() throws Exception {
        long enCurso = pedir(ana);

        mvc.perform(post("/api/v1/empresas/actual/baja").with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("confirmacion", nombre, "exportar", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bajaSolicitadaEn").isNotEmpty());

        assertThat(jdbc.queryForList("select id from exportaciones where empresa_id = ?", Long.class, empresaId))
                .containsExactly(enCurso);
    }

    @Test
    @DisplayName("la baja ofrece exportar: con exportar llega el enlace, durante la gracia se puede pedir otra, y al "
            + "purgarse la tienda queda un certificado de borrado sin datos de nadie, con la huella de la ultima")
    void bajaConCertificado() throws Exception {
        mvc.perform(post("/api/v1/empresas/actual/baja").with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("confirmacion", nombre, "exportar", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bajaSolicitadaEn").isNotEmpty());
        vaciarLaCola();
        assertThat(enlaceDelCorreo(ana)).contains("/api/v1/descargas/exportaciones/");

        long otra = pedir(ana);
        mvc.perform(post("/api/v1/procesos").with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"En la gracia\",\"descripcion\":\"No\",\"categoria\":\"Ops\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("Tienda dada de baja"));
        vaciarLaCola();
        String ultima = jdbc.queryForObject("select sha256 from exportaciones where id = ?", String.class, otra);

        jdbc.update("update empresas set baja_solicitada_en = ? where id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusDays(31), empresaId);
        assertThat(empresaService.borrarLasDadasDeBaja()).isPositive();
        vaciarLaCola();

        Path archivo = ALMACEN.resolve("certificados/" + empresaId + "/borrado");
        assertThat(archivo).exists();
        assertThat(ALMACEN.resolve("empresas/" + empresaId)).doesNotExist();
        byte[] bytes = Files.readAllBytes(archivo);
        JsonNode certificado = json.readTree(bytes);
        assertThat(certificado.get("tienda").asLong()).isEqualTo(empresaId);
        assertThat(certificado.get("filas").get("exportaciones").asInt()).isEqualTo(2);
        assertThat(certificado.get("filas").get("usuarios").asInt()).isEqualTo(3);
        assertThat(certificado.get("almacen").get("objetosBorrados").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(certificado.get("ultimaExportacion").get("id").asLong()).isEqualTo(otra);
        assertThat(certificado.get("ultimaExportacion").get("sha256").asString()).isEqualTo(ultima);
        String texto = new String(bytes, StandardCharsets.UTF_8);
        assertThat(texto).doesNotContain(nombre, "9613" + numero, ana.email(), beto.email(), "Ana", "Beto");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForList(
                "select detalle from bitacora where empresa_id = ? and accion = 'TIENDA_BORRADA'", String.class,
                empresaId)).singleElement().asString().contains(Huella.de(bytes)));
    }

    private static String texto(Map<String, byte[]> archivos, String ruta) {
        assertThat(archivos).containsKey(ruta);
        return new String(archivos.get(ruta), StandardCharsets.UTF_8);
    }

    /** Cada archivo del ZIP, en su orden, con sus bytes. */
    private static Map<String, byte[]> abrir(byte[] zip) throws IOException {
        Map<String, byte[]> archivos = new LinkedHashMap<>();
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry archivo;
            while ((archivo = entrada.getNextEntry()) != null) {
                assertThat(archivo.getName()).doesNotStartWith("/").doesNotContain("..", "\\");
                archivos.put(archivo.getName(), entrada.readAllBytes());
            }
        }
        assertThat(new ArrayList<>(archivos.keySet())).last().isEqualTo("manifest.json");
        return archivos;
    }
}
