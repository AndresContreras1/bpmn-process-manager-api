package com.facimus.procesos.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.CarpetaResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoDelRolResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.Clasificacion;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.CarpetaService;
import com.facimus.procesos.gestion.service.DatosDeFicha;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.FiltrosDeProceso;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;

/** PR 59: carpetas, la ficha de cada proceso, los filtros y los procesos de un rol, contra la base. */
@SpringBootTest
@ActiveProfiles("test")
class RepositorioDeProcesosIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";

    private static int tiendas;

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
    private PoolService poolService;
    @Autowired
    private LaneService laneService;

    private Long empresaId;
    private Long ana;
    private Long beto;

    @BeforeEach
    void registrar() {
        int numero = ++tiendas;
        String correo = "ana" + numero + "@repositorio.test";
        empresaId = empresaService.registrar("Repositorio " + numero, "9591" + numero + "000-1", correo, "Ana", correo,
                CLAVE).id();
        ana = usuarioRepository.findByEmail(correo).orElseThrow().getId();
        beto = usuarioService.crearColaborador(empresaId, null, "Beto", "beto" + numero + "@repositorio.test", CLAVE,
                RolAcceso.EDITOR).id();
    }

    private Long otraTienda() {
        int numero = ++tiendas;
        String correo = "otra" + numero + "@repositorio.test";
        return empresaService.registrar("Otra " + numero, "9591" + numero + "000-1", correo, "Otra", correo, CLAVE)
                .id();
    }

    private ProcesoResponse proceso(String nombre) {
        return procesoService.crear(empresaId, ana, nombre, "Descripcion", "Fulfillment");
    }

    private ProcesoResponse archivar(ProcesoResponse proceso, Long carpeta, Long dueno, LocalDate revision,
            Clasificacion clasificacion, String... etiquetas) {
        return procesoService.editarFicha(empresaId, proceso.id(), ana,
                new DatosDeFicha(carpeta, dueno, revision, clasificacion, List.of(etiquetas), proceso.version()));
    }

    private List<String> filtrar(FiltrosDeProceso filtros) {
        return procesoService.buscar(empresaId, filtros, PageRequest.of(0, 50, Sort.by("nombre"))).content().stream()
                .map(ProcesoResponse::nombre).toList();
    }

    private static FiltrosDeProceso porCarpeta(Long carpeta) {
        return new FiltrosDeProceso(null, null, null, false, carpeta, null, null, null, null);
    }

    @Test
    @DisplayName("las carpetas forman un arbol y cuentan sus procesos")
    void arbol() {
        CarpetaResponse raiz = carpetaService.crear(empresaId, "Operations", null);
        CarpetaResponse hija = carpetaService.crear(empresaId, "Fulfillment", raiz.id());
        archivar(proceso("Picking"), hija.id(), null, null, Clasificacion.INTERNA);

        List<CarpetaResponse> carpetas = carpetaService.listar(empresaId);

        assertThat(carpetas).extracting(CarpetaResponse::nombre).containsExactly("Fulfillment", "Operations");
        assertThat(carpetas.getFirst().padreId()).isEqualTo(raiz.id());
        assertThat(carpetas.getFirst().procesos()).isEqualTo(1);
    }

    @Test
    @DisplayName("dos hermanas no se llaman igual, sin importar las mayusculas; en otro lugar si")
    void nombreRepetido() {
        CarpetaResponse raiz = carpetaService.crear(empresaId, "Operations", null);
        carpetaService.crear(empresaId, "Fulfillment", raiz.id());

        assertThatThrownBy(() -> carpetaService.crear(empresaId, "FULFILLMENT", raiz.id()))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(carpetaService.crear(empresaId, "Fulfillment", null).padreId()).isNull();
    }

    @Test
    @DisplayName("una carpeta no puede quedar dentro de si misma ni de una de las suyas")
    void sinCiclos() {
        CarpetaResponse raiz = carpetaService.crear(empresaId, "A", null);
        CarpetaResponse hija = carpetaService.crear(empresaId, "B", raiz.id());

        assertThatThrownBy(() -> carpetaService.editar(empresaId, raiz.id(), "A", raiz.id(), raiz.version()))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> carpetaService.editar(empresaId, raiz.id(), "A", hija.id(), raiz.version()))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("el arbol tiene ocho niveles como mucho, tambien al mover una rama")
    void profundidad() {
        Long padre = null;
        for (int nivel = 1; nivel <= 8; nivel++) {
            padre = carpetaService.crear(empresaId, "Nivel " + nivel, padre).id();
        }
        Long ultima = padre;
        assertThatThrownBy(() -> carpetaService.crear(empresaId, "Nivel 9", ultima))
                .isInstanceOf(ReglaNegocioException.class);

        CarpetaResponse rama = carpetaService.crear(empresaId, "Rama", null);
        carpetaService.crear(empresaId, "Hoja", rama.id());
        Long nivelSiete = carpetaService.listar(empresaId).stream().filter(una -> "Nivel 7".equals(una.nombre()))
                .findFirst().orElseThrow().id();
        assertThatThrownBy(() -> carpetaService.editar(empresaId, rama.id(), "Rama", nivelSiete, rama.version()))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("mover y renombrar respeta la version leida")
    void editarConVersion() {
        CarpetaResponse carpeta = carpetaService.crear(empresaId, "A", null);
        CarpetaResponse otra = carpetaService.crear(empresaId, "B", null);

        CarpetaResponse movida = carpetaService.editar(empresaId, carpeta.id(), "C", otra.id(), carpeta.version());

        assertThat(movida.nombre()).isEqualTo("C");
        assertThat(movida.padreId()).isEqualTo(otra.id());
        assertThatThrownBy(() -> carpetaService.editar(empresaId, carpeta.id(), "D", null, carpeta.version()))
                .isInstanceOf(ConflictoDeVersionException.class);
    }

    @Test
    @DisplayName("solo se borra vacia, y un proceso eliminado no cuenta")
    void borrarVacia() {
        CarpetaResponse raiz = carpetaService.crear(empresaId, "A", null);
        CarpetaResponse hija = carpetaService.crear(empresaId, "B", raiz.id());
        ProcesoResponse proceso = archivar(proceso("Picking"), hija.id(), null, null, Clasificacion.INTERNA);

        assertThatThrownBy(() -> carpetaService.eliminar(empresaId, raiz.id()))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> carpetaService.eliminar(empresaId, hija.id()))
                .isInstanceOf(ReglaNegocioException.class);

        procesoService.eliminarLogico(empresaId, proceso.id(), ana);
        carpetaService.eliminar(empresaId, hija.id());
        carpetaService.eliminar(empresaId, raiz.id());

        assertThat(carpetaService.listar(empresaId)).isEmpty();
        assertThat(procesoService.obtener(empresaId, proceso.id(), true).carpetaId()).isNull();
    }

    @Test
    @DisplayName("las carpetas de otra tienda no existen para esta")
    void aislamiento() {
        Long otra = otraTienda();
        CarpetaResponse ajena = carpetaService.crear(otra, "Ajena", null);
        ProcesoResponse proceso = proceso("Picking");

        assertThat(carpetaService.listar(empresaId)).isEmpty();
        assertThatThrownBy(() -> carpetaService.crear(empresaId, "Hija", ajena.id()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> carpetaService.eliminar(empresaId, ajena.id()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> archivar(proceso, ajena.id(), null, null, Clasificacion.INTERNA))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("la ficha guarda los cinco datos y deja las etiquetas limpias")
    void ficha() {
        CarpetaResponse carpeta = carpetaService.crear(empresaId, "A", null);
        LocalDate revision = LocalDate.of(2027, 3, 1);

        ProcesoResponse archivado = archivar(proceso("Picking"), carpeta.id(), beto, revision,
                Clasificacion.CONFIDENCIAL, " Logistics ", "logistics", "GDPR");

        assertThat(archivado.carpetaId()).isEqualTo(carpeta.id());
        assertThat(archivado.duenoId()).isEqualTo(beto);
        assertThat(archivado.proximaRevision()).isEqualTo(revision);
        assertThat(archivado.clasificacion()).isEqualTo(Clasificacion.CONFIDENCIAL);
        assertThat(archivado.etiquetas()).containsExactly("gdpr", "logistics");
        assertThat(procesoService.etiquetasEnUso(empresaId)).containsExactly("gdpr", "logistics");
        assertThat(procesoService.listarHistorial(empresaId, archivado.id()))
                .anyMatch(cambio -> cambio.descripcionCambio().contains("Ficha"));
    }

    @Test
    @DisplayName("un proceso nuevo es interno, sin carpeta, dueno ni etiquetas")
    void valoresIniciales() {
        ProcesoResponse nuevo = proceso("Picking");

        assertThat(nuevo.clasificacion()).isEqualTo(Clasificacion.INTERNA);
        assertThat(nuevo.carpetaId()).isNull();
        assertThat(nuevo.duenoId()).isNull();
        assertThat(nuevo.etiquetas()).isEmpty();
    }

    @Test
    @DisplayName("la ficha exige la version leida y un dueno de la tienda")
    void fichaInvalida() {
        ProcesoResponse proceso = proceso("Picking");
        archivar(proceso, null, null, null, Clasificacion.CONFIDENCIAL);

        assertThatThrownBy(() -> archivar(proceso, null, null, null, Clasificacion.RESTRINGIDA))
                .isInstanceOf(ConflictoDeVersionException.class);

        Long otra = otraTienda();
        Long ajeno = usuarioService.crearColaborador(otra, null, "Ajeno", "ajeno" + tiendas + "@repositorio.test",
                CLAVE, RolAcceso.EDITOR).id();
        ProcesoResponse actual = procesoService.obtener(empresaId, proceso.id(), false);
        assertThatThrownBy(() -> archivar(actual, null, ajeno, null, Clasificacion.INTERNA))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("filtrar por carpeta incluye las de adentro y deja afuera las hermanas")
    void filtroCarpeta() {
        CarpetaResponse raiz = carpetaService.crear(empresaId, "A", null);
        CarpetaResponse hija = carpetaService.crear(empresaId, "B", raiz.id());
        CarpetaResponse hermana = carpetaService.crear(empresaId, "C", null);
        archivar(proceso("En la raiz"), raiz.id(), null, null, Clasificacion.INTERNA);
        archivar(proceso("En la hija"), hija.id(), null, null, Clasificacion.INTERNA);
        archivar(proceso("En la hermana"), hermana.id(), null, null, Clasificacion.INTERNA);
        proceso("Sin carpeta");

        assertThat(filtrar(porCarpeta(raiz.id()))).containsExactly("En la hija", "En la raiz");
        assertThat(filtrar(porCarpeta(hija.id()))).containsExactly("En la hija");
    }

    @Test
    @DisplayName("etiqueta, dueno, clasificacion y fecha de revision filtran, y se combinan")
    void filtros() {
        LocalDate pronto = LocalDate.of(2027, 1, 1);
        LocalDate tarde = LocalDate.of(2028, 1, 1);
        archivar(proceso("Uno"), null, beto, pronto, Clasificacion.CONFIDENCIAL, "gdpr");
        archivar(proceso("Dos"), null, ana, tarde, Clasificacion.CONFIDENCIAL, "logistics");
        archivar(proceso("Tres"), null, beto, tarde, Clasificacion.RESTRINGIDA, "gdpr", "logistics");

        assertThat(filtrar(new FiltrosDeProceso(null, null, null, false, null, "GDPR", null, null, null)))
                .containsExactly("Tres", "Uno");
        assertThat(filtrar(new FiltrosDeProceso(null, null, null, false, null, null, beto, null, null)))
                .containsExactly("Tres", "Uno");
        assertThat(filtrar(new FiltrosDeProceso(null, null, null, false, null, null, null,
                Clasificacion.CONFIDENCIAL, null))).containsExactly("Dos", "Uno");
        assertThat(filtrar(new FiltrosDeProceso(null, null, null, false, null, null, null, null, pronto)))
                .containsExactly("Uno");
        assertThat(filtrar(new FiltrosDeProceso(null, null, null, false, null, "gdpr", beto,
                Clasificacion.RESTRINGIDA, tarde))).containsExactly("Tres");
    }

    @Test
    @DisplayName("se puede ordenar por la proxima revision")
    void ordenPorRevision() {
        archivar(proceso("Tarde"), null, null, LocalDate.of(2028, 1, 1), Clasificacion.INTERNA);
        archivar(proceso("Pronto"), null, null, LocalDate.of(2027, 1, 1), Clasificacion.INTERNA);

        List<String> nombres = procesoService.buscar(empresaId,
                new FiltrosDeProceso(null, null, null, false, null, null, null, null, null),
                PageRequest.of(0, 50, Sort.by("proximaRevision"))).content().stream()
                .map(ProcesoResponse::nombre).toList();

        assertThat(nombres).containsExactly("Pronto", "Tarde");
    }

    @Test
    @DisplayName("HU-20.4: un rol dice que procesos lo usan, sin los eliminados")
    void procesosDelRol() {
        ProcesoResponse usa = proceso("Usa el rol");
        ProcesoResponse eliminado = proceso("Eliminado");
        Long rol = rolProcesoService.crear(empresaId, ana, "Sales", null).id();
        laneService.crear(empresaId, ana, poolService.listarPorProceso(empresaId, usa.id()).getFirst().id(), "Sales",
                rol);
        laneService.crear(empresaId, ana, poolService.listarPorProceso(empresaId, eliminado.id()).getFirst().id(),
                "Sales", rol);
        procesoService.eliminarLogico(empresaId, eliminado.id(), ana);

        assertThat(rolProcesoService.obtener(empresaId, rol).procesos())
                .containsExactly(new ProcesoDelRolResponse(usa.id(), "Usa el rol"));
    }
}
