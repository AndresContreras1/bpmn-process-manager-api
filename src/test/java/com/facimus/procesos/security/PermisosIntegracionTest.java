package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.Clasificacion;
import com.facimus.procesos.gestion.model.PoliticaEstructura;
import com.facimus.procesos.gestion.model.SujetoConcesion;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.gestion.service.CarpetaService;
import com.facimus.procesos.gestion.service.ConcesionService;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.DatosDeFicha;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.FiltrosDeProceso;
import com.facimus.procesos.gestion.service.MembresiaRolService;
import com.facimus.procesos.gestion.service.Permisos;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.Recurso;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;

import jakarta.persistence.EntityManagerFactory;

/** PR 60: la matriz actor x accion x recurso, la API de punta a punta y la suite IDOR. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermisosIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";

    private enum Quien {
        ADMIN, EDITOR, LECTOR, CON_PERSONA, CON_ROL, LECTOR_CON_PERSONA
    }

    private enum Donde {
        ABIERTO, PROPIO, HEREDADO, ANIDADO, DE_TODOS
    }

    /**
     * Lo que se espera, escrito a mano y sin mirar el evaluador. En cada recurso, las acciones restringidas y quienes
     * las tienen; lo que no esta aqui sigue el comportamiento por defecto.
     */
    private static final Map<Donde, Map<Accion, Set<Quien>>> RESTRINGIDO = Map.of(
            Donde.ABIERTO, Map.of(),
            Donde.PROPIO, Map.of(
                    Accion.MODELAR, Set.of(Quien.CON_PERSONA),
                    Accion.PUBLICAR, Set.of(Quien.CON_ROL),
                    Accion.EXPORTAR, Set.of(Quien.LECTOR_CON_PERSONA)),
            Donde.HEREDADO, Map.of(
                    Accion.VER, Set.of(Quien.CON_PERSONA, Quien.CON_ROL),
                    Accion.REVISAR, Set.of(Quien.CON_ROL)),
            Donde.ANIDADO, Map.of(
                    Accion.VER, Set.of(Quien.CON_PERSONA, Quien.CON_ROL),
                    Accion.REVISAR, Set.of(Quien.CON_ROL)),
            Donde.DE_TODOS, Map.of(Accion.MODELAR, Set.of(Quien.ADMIN, Quien.EDITOR, Quien.CON_PERSONA, Quien.CON_ROL)));

    private static final Set<Accion> DE_LECTURA = EnumSet.of(Accion.VER, Accion.COMENTAR, Accion.EXPORTAR);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmpresaService empresaService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ProcesoService procesoService;
    @Autowired
    private CarpetaService carpetaService;
    @Autowired
    private RolProcesoService rolProcesoService;
    @Autowired
    private MembresiaRolService membresiaRolService;
    @Autowired
    private ConcesionService concesionService;
    @Autowired
    private Permisos permisos;
    @Autowired
    private ConfiguracionTiendaService configuracionTiendaService;
    @Autowired
    private PoolService poolService;
    @Autowired
    private LaneService laneService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Long empresa;
    private Long otraEmpresa;
    private Long ajeno;
    private Long procesoAjeno;
    private Long carpetaAjena;
    private final Map<Quien, Actor> actores = new java.util.EnumMap<>(Quien.class);
    private final Map<Donde, Long> procesos = new java.util.EnumMap<>(Donde.class);
    private Long carpetaRaiz;
    private Long rol;

    private Long colaborador(Long tienda, String nombre, RolAcceso acceso) {
        return usuarioService.crearColaborador(tienda, null, nombre, nombre.toLowerCase() + tienda + "@permisos.test",
                CLAVE, acceso).id();
    }

    private ProcesoResponse proceso(Long tienda, Long autor, String nombre) {
        return procesoService.crear(tienda, autor, nombre, "Descripcion", "Fulfillment");
    }

    private void conceder(Recurso recurso, Accion accion, SujetoConcesion sujeto, Long usuario, Long rolId) {
        concesionService.conceder(actores.get(Quien.ADMIN), recurso, accion, sujeto, usuario, rolId);
    }

    private void archivar(ProcesoResponse proceso, Long carpeta, Long autor) {
        procesoService.editarFicha(empresa, proceso.id(), autor,
                new DatosDeFicha(carpeta, null, null, Clasificacion.INTERNA, List.of(), proceso.version()));
    }

    @BeforeAll
    void armarElMundo() {
        String correo = "admin@permisos.test";
        empresa = empresaService.registrar("Permisos", "95601000-1", correo, "Admin", correo, CLAVE).id();
        Long admin = usuarioRepository.findByEmail(correo).orElseThrow().getId();
        actores.put(Quien.ADMIN, new Actor(admin, empresa, RolAcceso.ADMINISTRADOR));
        actores.put(Quien.EDITOR, new Actor(colaborador(empresa, "Editor", RolAcceso.EDITOR), empresa, RolAcceso.EDITOR));
        actores.put(Quien.LECTOR,
                new Actor(colaborador(empresa, "Lector", RolAcceso.SOLO_LECTURA), empresa, RolAcceso.SOLO_LECTURA));
        actores.put(Quien.CON_PERSONA,
                new Actor(colaborador(empresa, "Persona", RolAcceso.EDITOR), empresa, RolAcceso.EDITOR));
        actores.put(Quien.CON_ROL, new Actor(colaborador(empresa, "Rol", RolAcceso.EDITOR), empresa, RolAcceso.EDITOR));
        actores.put(Quien.LECTOR_CON_PERSONA,
                new Actor(colaborador(empresa, "Lectora", RolAcceso.SOLO_LECTURA), empresa, RolAcceso.SOLO_LECTURA));

        rol = rolProcesoService.crear(empresa, admin, "Revisores", null).id();
        membresiaRolService.reemplazar(empresa, admin, actores.get(Quien.CON_ROL).usuarioId(), List.of(rol));

        procesos.put(Donde.ABIERTO, proceso(empresa, admin, "Abierto").id());

        ProcesoResponse propio = proceso(empresa, admin, "Propio");
        procesos.put(Donde.PROPIO, propio.id());
        Recurso enPropio = Recurso.proceso(propio.id());
        conceder(enPropio, Accion.MODELAR, SujetoConcesion.PERSONA, actores.get(Quien.CON_PERSONA).usuarioId(), null);
        conceder(enPropio, Accion.PUBLICAR, SujetoConcesion.ROL, null, rol);
        conceder(enPropio, Accion.EXPORTAR, SujetoConcesion.PERSONA, actores.get(Quien.LECTOR_CON_PERSONA).usuarioId(),
                null);

        carpetaRaiz = carpetaService.crear(empresa, "Reservada", null).id();
        Long hija = carpetaService.crear(empresa, "Hija", carpetaRaiz).id();
        ProcesoResponse heredado = proceso(empresa, admin, "Heredado");
        ProcesoResponse anidado = proceso(empresa, admin, "Anidado");
        archivar(heredado, carpetaRaiz, admin);
        archivar(anidado, hija, admin);
        procesos.put(Donde.HEREDADO, heredado.id());
        procesos.put(Donde.ANIDADO, anidado.id());
        Recurso enCarpeta = Recurso.carpeta(carpetaRaiz);
        conceder(enCarpeta, Accion.VER, SujetoConcesion.PERSONA, actores.get(Quien.CON_PERSONA).usuarioId(), null);
        conceder(enCarpeta, Accion.REVISAR, SujetoConcesion.ROL, null, rol);

        ProcesoResponse deTodos = proceso(empresa, admin, "De todos");
        procesos.put(Donde.DE_TODOS, deTodos.id());
        conceder(Recurso.proceso(deTodos.id()), Accion.MODELAR, SujetoConcesion.ORGANIZACION, null, null);

        String otro = "otra@permisos.test";
        otraEmpresa = empresaService.registrar("Otra", "95602000-1", otro, "Otra", otro, CLAVE).id();
        ajeno = usuarioRepository.findByEmail(otro).orElseThrow().getId();
        procesoAjeno = proceso(otraEmpresa, ajeno, "Ajeno").id();
        carpetaAjena = carpetaService.crear(otraEmpresa, "Ajena", null).id();
    }

    private static boolean porDefecto(Quien quien, Accion accion) {
        return switch (accion) {
            case VER, COMENTAR, EXPORTAR -> true;
            case MODELAR, REVISAR, APROBAR, PUBLICAR -> quien != Quien.LECTOR && quien != Quien.LECTOR_CON_PERSONA;
            case ADMINISTRAR -> quien == Quien.ADMIN;
        };
    }

    /** Ver lo trae cualquier concesion: quien puede modelar algo tiene que poder abrirlo. */
    private static boolean esperado(Quien quien, Accion accion, Donde donde) {
        if (quien == Quien.ADMIN) {
            return true;
        }
        if ((quien == Quien.LECTOR || quien == Quien.LECTOR_CON_PERSONA) && !DE_LECTURA.contains(accion)) {
            return false;
        }
        Map<Accion, Set<Quien>> restringido = RESTRINGIDO.get(donde);
        boolean tieneAlguna = restringido.values().stream().anyMatch(quienes -> quienes.contains(quien));
        if (accion == Accion.VER && tieneAlguna) {
            return true;
        }
        return restringido.containsKey(accion) ? restringido.get(accion).contains(quien) : porDefecto(quien, accion);
    }

    Stream<Arguments> matriz() {
        List<Arguments> casos = new ArrayList<>();
        for (Quien quien : Quien.values()) {
            for (Accion accion : Accion.values()) {
                for (Donde donde : Donde.values()) {
                    casos.add(Arguments.of(quien, accion, donde));
                }
            }
        }
        return casos.stream();
    }

    @ParameterizedTest(name = "{0} {1} en {2}")
    @MethodSource("matriz")
    void matrizActorAccionRecurso(Quien quien, Accion accion, Donde donde) {
        // El administrador creo y archivo todos: su propio aprobar se prueba aparte, en la separacion de funciones
        if (quien == Quien.ADMIN && accion == Accion.APROBAR) {
            return;
        }
        assertThat(permisos.puede(actores.get(quien), accion, Recurso.proceso(procesos.get(donde))))
                .isEqualTo(esperado(quien, accion, donde));
    }

    private RequestPostProcessor como(Quien quien) {
        Actor actor = actores.get(quien);
        return autenticado(new ApiPrincipal(actor.usuarioId(), actor.empresaId(), actor.rol(), quien + "@permisos.test",
                ApiPrincipalRequestPostProcessor.SESION, false));
    }

    private RequestPostProcessor delAjeno() {
        return autenticado(new ApiPrincipal(ajeno, otraEmpresa, RolAcceso.ADMINISTRADOR, "otra@permisos.test",
                ApiPrincipalRequestPostProcessor.SESION, false));
    }

    private static RequestPostProcessor autenticado(ApiPrincipal principal) {
        var autenticacion = new UsernamePasswordAuthenticationToken(principal, null,
                new TokenDeAcceso(principal, java.util.EnumSet.of(Factor.PASSWORD), Instant.now()).autoridades());
        return SecurityMockMvcRequestPostProcessors.authentication(autenticacion);
    }

    @Test
    @DisplayName("separacion de funciones: quien hizo el ultimo cambio no lo aprueba, ni administrando")
    void separacionDeFunciones() throws Exception {
        Actor admin = actores.get(Quien.ADMIN);
        // Por la API, para que la auditoria anote quien lo cambio
        String creado = mockMvc.perform(json(post("/api/v1/procesos"))
                .content("{\"nombre\":\"Por aprobar\",\"descripcion\":\"D\",\"categoria\":\"C\"}")
                .with(como(Quien.ADMIN))).andExpect(status().isCreated()).andReturn().getResponse()
                .getHeader("Location");
        Recurso recurso = Recurso.proceso(Long.valueOf(creado.substring(creado.lastIndexOf('/') + 1)));

        assertThat(permisos.puede(admin, Accion.APROBAR, recurso)).isFalse();
        assertThat(permisos.puede(actores.get(Quien.EDITOR), Accion.APROBAR, recurso)).isTrue();
    }

    @Test
    @DisplayName("sin poder verlo responde 404: no se dice que existe")
    void verNegadoEs404() throws Exception {
        Long heredado = procesos.get(Donde.HEREDADO);

        mockMvc.perform(get("/api/v1/procesos/" + heredado).with(como(Quien.EDITOR))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/procesos/" + heredado + "/diagrama").with(como(Quien.EDITOR)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/procesos/" + heredado).with(como(Quien.CON_PERSONA))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("lo que no se puede hacer responde 403, tambien sobre un elemento de adentro")
    void modelarNegadoEs403() throws Exception {
        Long propio = procesos.get(Donde.PROPIO);
        Long pool = poolService.listarPorProceso(empresa, propio).getFirst().id();
        String cuerpo = "{\"nombre\":\"Otro\",\"descripcion\":\"D\",\"categoria\":\"C\",\"version\":0}";

        mockMvc.perform(json(put("/api/v1/procesos/" + propio)).content(cuerpo).with(como(Quien.EDITOR)))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put("/api/v1/pools/" + pool)).content("{}").with(como(Quien.EDITOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("exportar se niega al descargar; la web igual puede dibujarlo")
    void exportarYDibujar() throws Exception {
        Long propio = procesos.get(Donde.PROPIO);

        mockMvc.perform(get("/api/v1/procesos/" + propio + "/modelo").with(como(Quien.EDITOR)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/procesos/" + propio + "/modelo").param("para", "dibujar").with(como(Quien.EDITOR)))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion) {
        return peticion.contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("la lista muestra solo lo visible")
    void listaFiltrada() {
        List<String> delEditor = nombres(Quien.EDITOR);
        List<String> dePersona = nombres(Quien.CON_PERSONA);

        assertThat(delEditor).contains("Abierto", "Propio", "De todos").doesNotContain("Heredado", "Anidado");
        assertThat(dePersona).contains("Abierto", "Heredado", "Anidado");
        assertThat(nombres(Quien.ADMIN)).contains("Heredado", "Anidado");
    }

    @Test
    @DisplayName("una concesion propia deja ver un proceso aunque su carpeta no se vea")
    void procesoVisibleEnCarpetaOculta() {
        Actor admin = actores.get(Quien.ADMIN);
        ProcesoResponse proceso = procesoService.crear(empresa, admin.usuarioId(), "Prestado", "D", "C");
        archivar(proceso, carpetaRaiz, admin.usuarioId());
        conceder(Recurso.proceso(proceso.id()), Accion.COMENTAR, SujetoConcesion.PERSONA,
                actores.get(Quien.LECTOR).usuarioId(), null);

        assertThat(nombres(Quien.LECTOR)).contains("Prestado").doesNotContain("Heredado");
        assertThat(nombres(Quien.EDITOR)).doesNotContain("Prestado");
    }

    private List<String> nombres(Quien quien) {
        return procesoService.buscar(empresa,
                new FiltrosDeProceso(null, null, null, false, null, null, null, null, null, actores.get(quien)),
                PageRequest.of(0, 50)).content().stream().map(ProcesoResponse::nombre).toList();
    }

    @Test
    @DisplayName("resolver lo visible no depende de cuantos procesos hay")
    void sinNmasUno() {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.setStatisticsEnabled(true);
        estadisticas.clear();
        permisos.visibilidad(actores.get(Quien.EDITOR));
        long antes = estadisticas.getPrepareStatementCount();

        Actor admin = actores.get(Quien.ADMIN);
        for (int numero = 0; numero < 5; numero++) {
            ProcesoResponse proceso = procesoService.crear(empresa, admin.usuarioId(), "Lote " + numero, "D", "C");
            conceder(Recurso.proceso(proceso.id()), Accion.VER, SujetoConcesion.PERSONA,
                    actores.get(Quien.CON_PERSONA).usuarioId(), null);
        }
        estadisticas.clear();
        permisos.visibilidad(actores.get(Quien.EDITOR));

        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(antes);
    }

    @Test
    @DisplayName("D16 es lo de por defecto: con solo administradores, una concesion de modelar deja tocar la estructura")
    void politicaComoConcesionPorDefecto() {
        String correo = "d16@permisos.test";
        Long tienda = empresaService.registrar("D16", "95603000-1", correo, "D16", correo, CLAVE).id();
        Long admin = usuarioRepository.findByEmail(correo).orElseThrow().getId();
        Long editor = colaborador(tienda, "Editora", RolAcceso.EDITOR);
        Long rolId = rolProcesoService.crear(tienda, admin, "Sales", null).id();
        Long procesoId = proceso(tienda, admin, "Estructura").id();
        Long pool = poolService.listarPorProceso(tienda, procesoId).getFirst().id();
        var actual = configuracionTiendaService.obtener(tienda);
        configuracionTiendaService.editar(tienda, admin, PoliticaEstructura.SOLO_ADMINISTRADOR, null, null,
                actual.version());

        assertThatThrownBy(() -> laneService.crear(tienda, editor, pool, "Sales", rolId))
                .isInstanceOf(SinPermisoException.class);

        concesionService.conceder(new Actor(admin, tienda, RolAcceso.ADMINISTRADOR), Recurso.proceso(procesoId),
                Accion.MODELAR, SujetoConcesion.PERSONA, editor, null);

        assertThat(laneService.crear(tienda, editor, pool, "Sales", rolId).id()).isNotNull();
    }

    @Test
    @DisplayName("mover un proceso entre carpetas con concesiones pide administrarlo")
    void moverPideAdministrar() {
        Actor admin = actores.get(Quien.ADMIN);
        Actor editor = actores.get(Quien.EDITOR);
        ProcesoResponse proceso = procesoService.crear(empresa, admin.usuarioId(), "Por mover", "D", "C");

        assertThatThrownBy(() -> procesoService.editarFicha(empresa, proceso.id(), editor.usuarioId(), new DatosDeFicha(
                carpetaRaiz, null, null, Clasificacion.INTERNA, List.of(), proceso.version())))
                .isInstanceOf(SinPermisoException.class);
    }

    @Test
    @DisplayName("el permiso propio se puede preguntar, y las concesiones se listan y se retiran")
    void apiDeConcesiones() throws Exception {
        Long propio = procesos.get(Donde.PROPIO);

        mockMvc.perform(get("/api/v1/procesos/" + propio + "/permisos").with(como(Quien.CON_PERSONA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acciones").isArray());
        mockMvc.perform(get("/api/v1/procesos/" + propio + "/concesiones").with(como(Quien.EDITOR)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/procesos/" + propio + "/concesiones").with(como(Quien.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));

        String cuerpo = "{\"accion\":\"COMENTAR\",\"sujeto\":\"ORGANIZACION\"}";
        String creada = mockMvc.perform(json(post("/api/v1/procesos/" + propio + "/concesiones")).content(cuerpo)
                .with(como(Quien.ADMIN))).andExpect(status().isCreated()).andReturn().getResponse()
                .getHeader("Location");
        mockMvc.perform(json(post("/api/v1/procesos/" + propio + "/concesiones")).content(cuerpo)
                .with(como(Quien.ADMIN))).andExpect(status().isConflict());
        mockMvc.perform(delete(creada).with(como(Quien.EDITOR))).andExpect(status().isForbidden());
        mockMvc.perform(delete(creada).with(como(Quien.ADMIN))).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("un sujeto incompleto se rechaza")
    void sujetoIncompleto() throws Exception {
        mockMvc.perform(json(post("/api/v1/procesos/" + procesos.get(Donde.ABIERTO) + "/concesiones"))
                .content("{\"accion\":\"VER\",\"sujeto\":\"PERSONA\"}").with(como(Quien.ADMIN)))
                .andExpect(status().isBadRequest());
    }

    Stream<Arguments> rutasAjenas() {
        return Stream.of(
                Arguments.of("GET", "/api/v1/procesos/%d/concesiones", true),
                Arguments.of("POST", "/api/v1/procesos/%d/concesiones", true),
                Arguments.of("PUT", "/api/v1/procesos/%d/ficha", true),
                Arguments.of("GET", "/api/v1/procesos/%d/permisos", true),
                Arguments.of("GET", "/api/v1/carpetas/%d/concesiones", false),
                Arguments.of("POST", "/api/v1/carpetas/%d/concesiones", false),
                Arguments.of("PUT", "/api/v1/carpetas/%d", false),
                Arguments.of("DELETE", "/api/v1/carpetas/%d", false));
    }

    /** IDOR: con un id de otra tienda, un administrador de esta no llega a nada. */
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("rutasAjenas")
    void idor(String metodo, String ruta, boolean esProceso) throws Exception {
        Long id = esProceso ? procesoAjeno : carpetaAjena;
        String url = ruta.formatted(id);
        String cuerpo = url.endsWith("ficha") ? "{\"clasificacion\":\"INTERNA\",\"etiquetas\":[],\"version\":0}"
                : url.endsWith("concesiones") ? "{\"accion\":\"VER\",\"sujeto\":\"ORGANIZACION\"}"
                : "{\"nombre\":\"X\",\"version\":0}";
        MockHttpServletRequestBuilder peticion = switch (metodo) {
            case "GET" -> get(url);
            case "POST" -> json(post(url)).content(cuerpo);
            case "PUT" -> json(put(url)).content(cuerpo);
            default -> delete(url);
        };

        int estado = mockMvc.perform(peticion.with(como(Quien.ADMIN))).andReturn().getResponse().getStatus();

        if (url.endsWith("permisos")) {
            // No hay nada que filtrar: un recurso ajeno no se evalua, y la respuesta no dice nada de el
            assertThat(estado).isEqualTo(200);
        } else {
            assertThat(estado).isEqualTo(404);
        }
    }

    @Test
    @DisplayName("IDOR: una concesion de otra tienda no se puede retirar, ni apuntar a alguien de otra tienda")
    void idorConcesiones() throws Exception {
        Actor delOtro = new Actor(ajeno, otraEmpresa, RolAcceso.ADMINISTRADOR);
        Long ajena = concesionService.conceder(delOtro, Recurso.proceso(procesoAjeno), Accion.VER,
                SujetoConcesion.ORGANIZACION, null, null).id();

        mockMvc.perform(delete("/api/v1/concesiones/" + ajena).with(como(Quien.ADMIN))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/concesiones/" + ajena).with(delAjeno())).andExpect(status().isNoContent());
        mockMvc.perform(json(post("/api/v1/procesos/" + procesos.get(Donde.ABIERTO) + "/concesiones"))
                .content("{\"accion\":\"VER\",\"sujeto\":\"PERSONA\",\"usuarioId\":" + ajeno + "}")
                .with(como(Quien.ADMIN))).andExpect(status().isNotFound());
    }
}
