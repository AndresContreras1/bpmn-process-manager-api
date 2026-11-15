package com.facimus.procesos.modelado.bpmn;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.modelado.bpmn.ModeloLeido.DatosDelMensaje;
import com.facimus.procesos.modelado.bpmn.ModeloLeido.Flujo;
import com.facimus.procesos.modelado.bpmn.ModeloLeido.FlujoDeMensaje;
import com.facimus.procesos.modelado.bpmn.ModeloLeido.Participante;
import com.facimus.procesos.modelado.bpmn.ModeloLeido.Proceso;
import com.facimus.procesos.modelado.bpmn.PlanBpmn.Arco;
import com.facimus.procesos.modelado.bpmn.PlanBpmn.Clase;
import com.facimus.procesos.modelado.bpmn.PlanBpmn.Correlacion;
import com.facimus.procesos.modelado.bpmn.PlanBpmn.Lane;
import com.facimus.procesos.modelado.bpmn.PlanBpmn.Mensaje;
import com.facimus.procesos.modelado.bpmn.PlanBpmn.Nodo;
import com.facimus.procesos.modelado.bpmn.PlanBpmn.Pool;
import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.Artefacto;
import com.facimus.procesos.modelado.model.BpmnId;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.DireccionAsociacion;
import com.facimus.procesos.modelado.model.DisposicionBpmn;
import com.facimus.procesos.modelado.model.DisposicionBpmn.Limites;
import com.facimus.procesos.modelado.model.ExtensionXml;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.PoliticaSinCaso;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.model.TipoAsociacion;
import com.facimus.procesos.modelado.model.TipoDestino;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.model.TipoTemporizador;

/**
 * PR 54: de lo que trae un archivo a lo que el modelo puede guardar. Decide tres cosas por cada elemento:
 *
 * <ul>
 * <li><b>soportado</b>, tal cual: tareas de usuario, de servicio, de envio y de recepcion, los tres gateways, los
 * inicios, fines y esperas de mensaje, flujos, lanes, pools y mensajes;</li>
 * <li><b>convertido</b> a lo mas cercano, diciendo que cambio: una tarea manual pasa a ser de usuario, un inicio de
 * temporizador a un inicio simple, un mensaje anclado a una tarea que no envia sale del borde de su pool...;</li>
 * <li><b>no soportado</b>: se deja fuera, con lo que dependia de el (un subproceso y los flujos que lo tocan).</li>
 * </ul>
 *
 * <p>Las reglas que el modelo no deja romper se comprueban todas antes de escribir nada: un flujo que llega a un
 * inicio o sale de un fin, uno que cruza de un pool a otro, un mensaje dentro de un mismo pool. Si alguna se rompe,
 * el archivo no se importa y la respuesta las dice todas. Lo que el modelo deja guardar pero no publicar (un gateway
 * con salidas sin condicion) se importa y el diagnostico lo marca, como a cualquier borrador.
 */
public final class ConversionBpmn {

    static final int NOMBRE = 120;
    static final int DESCRIPCION = 1000;
    static final int DESCRIPCION_DEL_PROCESO = 4000;
    static final int CATEGORIA = 80;
    static final int ROL = 80;
    static final int CONTENIDO = 2000;
    static final int CONDICION = 500;
    static final int USO = 1000;
    static final int CRITERIO = 120;
    static final int CAMPO = 80;
    static final int CAMPO_DEL_MENSAJE = 60;

    /** Un bpmn_id que se conserva: un xsd:ID de hasta 64 caracteres. */
    private static final String MENSAJE = "messageEventDefinition";
    private static final String TIEMPO = "timerEventDefinition";
    private static final String TERMINACION = "terminateEventDefinition";
    private static final int TEMPORIZADOR = 120;
    private static final int TEXTO_ARTEFACTO = 1000;
    /** Lo que separa los artefactos que vienen sin dibujo. */
    private static final double SEPARACION_SIN_DIBUJO = 140;
    private static final Pattern ID = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,63}");
    private static final Pattern VARIABLE = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,59}");

    private ConversionBpmn() {
    }

    /**
     * @throws ReglaNegocioException con todo lo que impide importar el archivo, si algo lo impide
     */
    public static PlanBpmn convertir(ModeloLeido leido, List<String> avisosDelEsquema) {
        return convertir(leido, avisosDelEsquema, null);
    }

    /** @param codificacion la de un byte en la que venia el archivo antes de pasarlo a UTF-8, si venia en una */
    public static PlanBpmn convertir(ModeloLeido leido, List<String> avisosDelEsquema, String codificacion) {
        return new Conversion(leido).convertir(avisosDelEsquema, codificacion);
    }

    /** Una conversion: el archivo, el informe que se va llenando y los ids que va tomando cada elemento. */
    private static final class Conversion {

        private final ModeloLeido leido;
        private final InformeBpmn.Notas notas = new InformeBpmn.Notas();
        /** El id de cada elemento del archivo y el que tendra en el modelo. */
        private final Map<String, String> ids = new HashMap<>();
        private final Set<String> usados = new HashSet<>();
        private final List<Pool> pools = new ArrayList<>();
        private final List<Lane> lanes = new ArrayList<>();
        private final List<Nodo> nodos = new ArrayList<>();
        private final List<Arco> arcos = new ArrayList<>();
        private final List<Mensaje> mensajes = new ArrayList<>();
        /** Para cada nodo que queda, su pool y su clase; para cada pool, si es caja negra. */
        private final Map<String, String> poolDelNodo = new HashMap<>();
        private final Map<String, Nodo> nodoPorId = new HashMap<>();
        private final Map<String, String> poolDelParticipante = new HashMap<>();
        /** El pool de cada participante, sintetico o no: uno sin id no se puede buscar por el. */
        private final Map<Participante, String> poolDe = new IdentityHashMap<>();
        /** El id en el archivo del participante de cada pool, para leer su dibujo. */
        private final Map<String, String> participanteDelPool = new HashMap<>();
        private final Map<String, String> poolDelProceso = new HashMap<>();
        /** Lo que se dejo fuera, para decir por que se cae lo que dependia de ello. */
        private final Set<String> descartados = new HashSet<>();
        private final List<PlanBpmn.Artefacto> artefactos = new ArrayList<>();
        private final List<PlanBpmn.Asociacion> asociaciones = new ArrayList<>();
        private final Set<String> idsDeArtefactos = new HashSet<>();
        private final Set<String> idsDeDatos = new HashSet<>();
        private final Set<String> idsDeArcos = new HashSet<>();
        private final Set<String> idsDeAsociaciones = new HashSet<>();
        /** El marco de las posiciones: la menor x de los nodos y la menor y de los nodos de cada lane. */
        private Double menorX;
        /** PR 56: los subprocesos del archivo que llevan algo dentro. */
        private final Set<String> contenedores = new HashSet<>();
        private final Map<String, Double> menorYDeLaLane = new HashMap<>();

        Conversion(ModeloLeido leido) {
            this.leido = leido;
        }

        PlanBpmn convertir(List<String> avisosDelEsquema, String codificacion) {
            if (codificacion != null) {
                notas.convertido("archivo", "Venia en " + codificacion + ": se leyo pasado a UTF-8.");
            }
            leido.noSoportados().forEach(ajeno -> {
                descartados.add(ajeno.id());
                notas.noSoportado(Objects.requireNonNullElse(ajeno.id(), ajeno.elemento()),
                        "Un " + ajeno.elemento() + (ajeno.nombre() == null ? "" : " (" + ajeno.nombre() + ")")
                                + " en " + ajeno.donde() + ": el modelo no lo tiene, asi que se deja fuera.");
            });
            List<Participante> participantes = participantes();
            Participante organizacion = organizacion(participantes);
            if (organizacion == null) {
                notas.error("El archivo no tiene ningun proceso modelado: hace falta al menos un participante con "
                        + "su proceso, o un proceso.");
                throw rechazo();
            }
            poolsDe(participantes, organizacion);
            for (Participante participante : participantes) {
                String pool = poolDe.get(participante);
                Proceso proceso = procesoDe(participante);
                if (pool != null && proceso != null && !esCajaNegra(pool)) {
                    contenidoDe(proceso, pool);
                }
            }
            pegarBordes();
            posiciones();
            for (Participante participante : participantes) {
                Proceso proceso = procesoDe(participante);
                String pool = poolDe.get(participante);
                if (proceso != null && pool != null && !esCajaNegra(pool)) {
                    flujosDe(proceso, pool);
                }
            }
            mensajes();
            artefactos();
            asociaciones();
            if (!notas.errores().isEmpty()) {
                throw rechazo();
            }
            Map<String, String> extensiones = extensiones();
            ModoProceso modo = modo(organizacion);
            InformeBpmn informe = notas.informe(new ArrayList<>(extensiones.keySet()), avisosDelEsquema);
            return new PlanBpmn(nombreDelProceso(organizacion), recortar(leido.descripcion(), DESCRIPCION_DEL_PROCESO),
                    recortar(leido.categoria(), CATEGORIA), pools, lanes, nodos, arcos, mensajes, extensiones,
                    informe, modo, artefactos, asociaciones);
        }

        /**
         * PR 55: lo que el archivo dice de su proceso principal. {@code isExecutable="false"} es uno documental; sin
         * decirlo, el borrador se queda como este.
         */
        private ModoProceso modo(Participante organizacion) {
            Proceso proceso = procesoDe(organizacion);
            if (proceso == null || proceso.ejecutable() == null) {
                return null;
            }
            // Un archivo propio ya era documental; uno de otra herramienta puede sorprender, y se avisa
            if (!proceso.ejecutable() && organizacion.tipo() == null) {
                notas.convertido(proceso.id(), "Viene con isExecutable en false: el proceso queda documental.");
            }
            return proceso.ejecutable() ? ModoProceso.EJECUTABLE : ModoProceso.DOCUMENTAL;
        }

        private ReglaNegocioException rechazo() {
            List<String> errores = notas.errores();
            return new ReglaNegocioException("El archivo no se puede importar: " + errores.size()
                    + (errores.size() == 1 ? " problema." : " problemas."), errores);
        }

        // ---------- Participantes y pools ----------

        /**
         * Los del archivo, mas uno por cada proceso que ninguno nombra (BPMN lo permite: un proceso dibujado fuera de
         * todo pool). Sin colaboracion, uno para el proceso principal.
         */
        private List<Participante> participantes() {
            if (!leido.participantes().isEmpty()) {
                List<Participante> todos = new ArrayList<>(leido.participantes());
                for (Proceso proceso : leido.procesos()) {
                    boolean nombrado = todos.stream().anyMatch(uno -> Objects.equals(proceso.id(), uno.proceso()));
                    if (proceso.id() != null && !nombrado && !proceso.nodos().isEmpty()) {
                        todos.add(new Participante(null, proceso.nombre(), proceso.id(), null, null, null));
                        notas.convertido(proceso.id(), "Un proceso que ningun participante nombra: entra como un pool "
                                + "mas.");
                    }
                }
                return todos;
            }
            Proceso principal = leido.procesos().stream()
                    .max(Comparator.comparing((Proceso proceso) -> Boolean.TRUE.equals(proceso.ejecutable()))
                            .thenComparingInt(proceso -> proceso.nodos().size()))
                    .orElse(null);
            if (principal == null) {
                return List.of();
            }
            for (Proceso otro : leido.procesos()) {
                if (otro != principal) {
                    descartados.add(otro.id());
                    notas.noSoportado(otro.id(), "Un segundo proceso sin colaboracion: solo se importa "
                            + Objects.requireNonNullElse(principal.nombre(), principal.id()) + ".");
                }
            }
            return List.of(new Participante(null, principal.nombre(), principal.id(), null, null, 0));
        }

        /**
         * El participante de la organizacion: el que lo dice ({@code bpm:participante tipo="EMPRESA"}), o el que tiene
         * el proceso ejecutable con mas nodos.
         */
        private Participante organizacion(List<Participante> participantes) {
            for (Participante participante : participantes) {
                if ("EMPRESA".equals(participante.tipo()) && procesoDe(participante) != null) {
                    return participante;
                }
            }
            Participante elegido = null;
            int mejor = -1;
            for (Participante participante : participantes) {
                Proceso proceso = procesoDe(participante);
                // Gana el ejecutable; entre iguales, el de mas nodos
                int puntos = proceso == null ? -1
                        : (Boolean.TRUE.equals(proceso.ejecutable()) ? 1_000_000 : 0) + proceso.nodos().size();
                if (puntos > mejor) {
                    mejor = puntos;
                    elegido = participante;
                }
            }
            return elegido;
        }

        private void poolsDe(List<Participante> participantes, Participante organizacion) {
            Set<String> procesosUsados = new HashSet<>();
            List<Participante> otros = new ArrayList<>(participantes);
            otros.remove(organizacion);
            otros.sort(Comparator.comparing((Participante participante) ->
                    participante.orden() == null ? Integer.MAX_VALUE : participante.orden()));
            List<Participante> enOrden = new ArrayList<>();
            enOrden.add(organizacion);
            enOrden.addAll(otros);
            int orden = 0;
            for (Participante participante : enOrden) {
                boolean esLaOrganizacion = participante == organizacion;
                Proceso proceso = procesoDe(participante);
                if (participante.proceso() != null && proceso == null) {
                    notas.convertido(participante.id(), "Su proceso no esta en el archivo: queda como caja negra.");
                }
                boolean cajaNegra = proceso == null || !procesosUsados.add(proceso.id());
                if (proceso != null && cajaNegra) {
                    notas.convertido(participante.id(), "Su proceso ya es de otro participante: queda como caja negra.");
                }
                String id = id(participante.id(), "Participant", participante.id() == null ? "Participant" : null);
                // El pool de la organizacion ya existe: sin un nombre en el archivo, conserva el suyo
                String nombre = esLaOrganizacion
                        ? recortar(participante.nombre() != null ? participante.nombre()
                                : proceso == null ? null : proceso.nombre(), NOMBRE)
                        : nombre(participante.nombre(), proceso == null ? null : proceso.nombre(), "Participante",
                                participante.id(), "el participante");
                TipoParticipante tipo = esLaOrganizacion ? TipoParticipante.EMPRESA : tipoDeSocio(participante,
                        organizacion);
                Integracion integracion = valor(Integracion.class, participante.integracion(), Integracion.NINGUNA,
                        participante.id(), "integracion");
                pools.add(new Pool(id, nombre, tipo, integracion, orden++, cajaNegra && !esLaOrganizacion));
                notas.soportado("pools");
                poolDe.put(participante, id);
                if (participante.id() != null) {
                    poolDelParticipante.put(participante.id(), id);
                    participanteDelPool.put(id, participante.id());
                }
                if (proceso != null && !cajaNegra) {
                    poolDelProceso.put(proceso.id(), id);
                }
            }
        }

        /** El tipo que dice el archivo; si no lo dice, cliente si se dibuja arriba de la organizacion, si no proveedor. */
        private TipoParticipante tipoDeSocio(Participante participante, Participante organizacion) {
            TipoParticipante dicho = valor(TipoParticipante.class, participante.tipo(), null, participante.id(),
                    "tipo de participante");
            if (dicho != null && dicho != TipoParticipante.EMPRESA) {
                return dicho;
            }
            Limites suyo = leido.formas().get(participante.id());
            Limites deLaOrganizacion = organizacion.id() == null ? null : leido.formas().get(organizacion.id());
            TipoParticipante deducido = suyo != null && deLaOrganizacion != null && suyo.y() < deLaOrganizacion.y()
                    ? TipoParticipante.CLIENTE : TipoParticipante.PROVEEDOR;
            notas.convertido(participante.id(), "El archivo no dice que es: por donde se dibuja, queda como "
                    + (deducido == TipoParticipante.CLIENTE ? "cliente." : "proveedor."));
            return deducido;
        }

        private Proceso procesoDe(Participante participante) {
            if (participante.proceso() == null) {
                return null;
            }
            return leido.procesos().stream().filter(proceso -> participante.proceso().equals(proceso.id()))
                    .findFirst().orElse(null);
        }

        private boolean esCajaNegra(String pool) {
            return pools.stream().anyMatch(uno -> uno.bpmnId().equals(pool) && uno.cajaNegra());
        }

        // ---------- Lanes y nodos ----------

        private void contenidoDe(Proceso proceso, String pool) {
            if (proceso.lanesAnidadas()) {
                notas.convertido(proceso.id(), "Las lanes dentro de otras lanes se aplanan: queda cada una de abajo.");
            }
            List<ModeloLeido.Lane> suyas = new ArrayList<>(proceso.lanes());
            if (suyas.isEmpty() && !proceso.nodos().isEmpty()) {
                String delPool = pools.stream().filter(uno -> uno.bpmnId().equals(pool)).findFirst().orElseThrow()
                        .nombre();
                String nombre = delPool != null ? delPool : proceso.nombre() != null ? recortar(proceso.nombre(), NOMBRE)
                        : "General";
                suyas.add(new ModeloLeido.Lane(null, nombre, null, 0,
                        proceso.nodos().stream().map(ModeloLeido.Nodo::id).toList()));
                notas.convertido(proceso.id(), "El proceso no tiene lanes: sus nodos van en una lane " + nombre + ".");
            }
            suyas.sort(Comparator.comparing((ModeloLeido.Lane lane) ->
                    lane.orden() == null ? Integer.MAX_VALUE : lane.orden()));
            Map<String, String> laneDelNodo = new LinkedHashMap<>();
            int orden = 0;
            for (ModeloLeido.Lane lane : suyas) {
                String id = id(lane.id(), "Lane", lane.id() == null ? "Lane" : null);
                String nombre = nombre(lane.nombre(), null, "Lane " + (orden + 1), lane.id(), "la lane");
                String rol = recortar(lane.rol() != null ? lane.rol() : nombre, ROL);
                lanes.add(new Lane(id, pool, nombre, rol, orden++));
                notas.soportado("lanes");
                for (String nodo : lane.nodos()) {
                    laneDelNodo.putIfAbsent(nodo, id);
                }
            }
            String primera = lanes.stream().filter(lane -> lane.pool().equals(pool)).findFirst()
                    .map(Lane::bpmnId).orElse(null);
            Set<String> conContenido = new HashSet<>();
            proceso.nodos().forEach(nodo -> conContenido.add(nodo.padre()));
            contenedores.addAll(conContenido);
            for (ModeloLeido.Nodo nodo : proceso.nodos()) {
                if (nodo.padre() != null) {
                    delContenido(nodo, pool);
                    continue;
                }
                String lane = laneDelNodo.get(nodo.id());
                if (lane == null) {
                    lane = primera;
                    notas.convertido(nodo.id(), "No esta en ninguna lane: va en la primera del pool.");
                }
                nodo(nodo, pool, lane);
            }
        }

        /** PR 56: lo de dentro de un subproceso esta en su lane; si el subproceso se dejo fuera, tambien lo de dentro. */
        private void delContenido(ModeloLeido.Nodo nodo, String pool) {
            String padre = ids.get(nodo.padre());
            Nodo contenedor = padre == null ? null : nodoPorId.get(padre);
            if (contenedor == null) {
                descartados.add(nodo.id());
                notas.noSoportado(Objects.requireNonNullElse(nodo.id(), nodo.elemento()),
                        "Esta dentro de algo que se dejo fuera, asi que tambien se deja fuera.");
                return;
            }
            nodo(nodo, pool, contenedor.lane());
        }

        private void nodo(ModeloLeido.Nodo leidoNodo, String pool, String lane) {
            String elemento = leidoNodo.elemento();
            String original = leidoNodo.id();
            Clase clase;
            TipoActividad actividad = null;
            TipoGateway gateway = null;
            TipoEvento evento = null;
            if (LectorBpmn.TAREAS.contains(elemento)) {
                clase = Clase.ACTIVIDAD;
                actividad = tipoDeTarea(elemento, original);
            } else if (LectorBpmn.GATEWAYS.contains(elemento)) {
                clase = Clase.GATEWAY;
                gateway = switch (elemento) {
                    case "parallelGateway" -> TipoGateway.PARALELO;
                    case "inclusiveGateway" -> TipoGateway.INCLUSIVO;
                    default -> TipoGateway.EXCLUSIVO;
                };
            } else {
                clase = Clase.EVENTO;
                evento = tipoDeEvento(leidoNodo);
                if (evento == null) {
                    descartados.add(original);
                    return;
                }
            }
            String prefijo = switch (clase) {
                case ACTIVIDAD -> "Activity";
                case GATEWAY -> "Gateway";
                case EVENTO -> "Event";
            };
            String id = id(original, prefijo, null);
            String porDefecto = switch (clase) {
                case ACTIVIDAD -> "Task";
                case GATEWAY -> "Gateway";
                case EVENTO -> evento.empiezaElProceso() ? "Start" : evento.terminaElProceso() ? "End" : "Event";
            };
            boolean deBorde = evento != null && evento.esDeBorde();
            boolean conTiempo = evento != null && evento.esTemporizador();
            Nodo nodo = new Nodo(id, lane, clase, actividad, gateway, evento,
                    nombre(leidoNodo.nombre(), null, porDefecto, original, "el nodo"),
                    recortar(leidoNodo.descripcion(), DESCRIPCION), 0, 0, deBorde ? leidoNodo.adjuntoA() : null,
                    deBorde ? Boolean.valueOf(leidoNodo.interrumpe()) : null,
                    conTiempo ? tipoDeTiempo(leidoNodo) : null, conTiempo ? expresion(leidoNodo) : null,
                    leidoNodo.padre() == null ? null : ids.get(leidoNodo.padre()),
                    actividad == TipoActividad.LLAMADA ? procesoLlamado(leidoNodo) : null);
            nodos.add(nodo);
            nodoPorId.put(id, nodo);
            poolDelNodo.put(id, pool);
            notas.soportado(switch (clase) {
                case ACTIVIDAD -> "tareas";
                case GATEWAY -> "gateways";
                case EVENTO -> "eventos";
            });
        }

        private TipoActividad tipoDeTarea(String elemento, String id) {
            return switch (elemento) {
                case "userTask" -> TipoActividad.USUARIO;
                case "serviceTask" -> TipoActividad.SERVICIO;
                case "sendTask" -> TipoActividad.ENVIO;
                case "receiveTask" -> TipoActividad.RECEPCION;
                case "task" -> TipoActividad.SIN_TIPO;
                case "callActivity" -> TipoActividad.LLAMADA;
                case "subProcess" -> tipoDeSubproceso(id);
                case "businessRuleTask", "scriptTask" -> {
                    notas.convertido(id, "Una " + elemento + " corre sola: queda como tarea de servicio.");
                    yield TipoActividad.SERVICIO;
                }
                default -> {
                    notas.convertido(id, "Una " + elemento + " la hace una persona: queda como tarea de usuario.");
                    yield TipoActividad.USUARIO;
                }
            };
        }

        /**
         * PR 56: el modelo dibuja un solo plano, asi que un subproceso colapsado con contenido se despliega; sin
         * contenido se queda como esta.
         */
        private TipoActividad tipoDeSubproceso(String id) {
            boolean expandido = leido.expandidos().contains(id);
            if (contenedores.contains(id)) {
                if (!expandido) {
                    notas.convertido(id, "Un subproceso colapsado con contenido: se despliega.");
                }
                return TipoActividad.SUBPROCESO;
            }
            return expandido ? TipoActividad.SUBPROCESO : TipoActividad.SUBPROCESO_COLAPSADO;
        }

        /** El proceso que llama, si el archivo lo nombra como lo escribe este producto. */
        private Long procesoLlamado(ModeloLeido.Nodo nodo) {
            String llamado = nodo.llamado();
            String numero = llamado != null && llamado.startsWith(EspaciosBpmn.PREFIJO_PROCESO_LLAMADO)
                    ? llamado.substring(EspaciosBpmn.PREFIJO_PROCESO_LLAMADO.length()) : null;
            try {
                if (numero != null) {
                    return Long.valueOf(numero);
                }
            } catch (NumberFormatException noEsUnId) {
                // Lo nombra otra herramienta: se avisa igual que si no dijera ninguno
            }
            notas.convertido(nodo.id(), "Una callActivity que no nombra un proceso de esta tienda: elige cual llama.");
            return null;
        }

        /** El tipo de evento, o nulo si el modelo no tiene uno parecido y se deja fuera. */
        private TipoEvento tipoDeEvento(ModeloLeido.Nodo nodo) {
            List<String> definiciones = nodo.definiciones();
            boolean deMensaje = definiciones.contains(MENSAJE);
            boolean deTiempo = definiciones.contains(TIEMPO);
            boolean deTerminacion = definiciones.contains(TERMINACION);
            switch (nodo.elemento()) {
                case "startEvent" -> {
                    String otras = otras(definiciones, deMensaje ? MENSAJE : TIEMPO);
                    if (otras != null) {
                        notas.convertido(nodo.id(), "Un inicio con " + otras + ": queda como "
                                + (deMensaje ? "inicio de mensaje." : deTiempo ? "inicio de temporizador."
                                        : "inicio simple."));
                    }
                    if (deMensaje) {
                        return TipoEvento.MENSAJE_INICIO;
                    }
                    return deTiempo ? TipoEvento.TEMPORIZADOR_INICIO : TipoEvento.INICIO;
                }
                case "endEvent" -> {
                    String otras = otras(definiciones, deMensaje ? MENSAJE : TERMINACION);
                    if (otras != null) {
                        notas.convertido(nodo.id(), "Un fin con " + otras + ": queda como "
                                + (deMensaje ? "fin de mensaje." : deTerminacion ? "fin de terminacion."
                                        : "fin simple."));
                    }
                    if (deMensaje) {
                        return TipoEvento.MENSAJE_FIN;
                    }
                    return deTerminacion ? TipoEvento.FIN_TERMINACION : TipoEvento.FIN;
                }
                case "boundaryEvent" -> {
                    if (deTiempo) {
                        return TipoEvento.TEMPORIZADOR_BORDE;
                    }
                    String otras = otras(definiciones, null);
                    notas.noSoportado(nodo.id(), "Un boundaryEvent" + (otras == null ? " sin definicion"
                            : " de " + otras) + ": el modelo solo tiene los de temporizador, asi que se deja fuera.");
                    return null;
                }
                default -> {
                    String otras = otras(definiciones, deMensaje ? MENSAJE : TIEMPO);
                    if (deMensaje || deTiempo) {
                        if (otras != null) {
                            notas.convertido(nodo.id(), "Una espera con " + otras + ": queda como espera de "
                                    + (deMensaje ? "mensaje." : "temporizador."));
                        }
                        return deMensaje ? TipoEvento.MENSAJE_INTERMEDIO : TipoEvento.TEMPORIZADOR_INTERMEDIO;
                    }
                    notas.noSoportado(nodo.id(), "Un evento intermedio" + (otras == null ? " sin definicion"
                            : " de " + otras) + ": el modelo solo espera mensajes y temporizadores, asi que se deja "
                            + "fuera.");
                    return null;
                }
            }
        }

        private static String otras(List<String> definiciones, String elegida) {
            return definiciones.stream().filter(definicion -> !definicion.equals(elegida))
                    .reduce((una, otra) -> una + ", " + otra).orElse(null);
        }

        private static TipoTemporizador tipoDeTiempo(ModeloLeido.Nodo nodo) {
            if (nodo.tipoTemporizador() == null) {
                return null;
            }
            return switch (nodo.tipoTemporizador()) {
                case "timeDate" -> TipoTemporizador.FECHA;
                case "timeCycle" -> TipoTemporizador.CICLO;
                case ModeloLeido.Nodo.DURACION_HABIL -> TipoTemporizador.DURACION_HABIL;
                default -> TipoTemporizador.DURACION;
            };
        }

        /** Una expresion recortada ya no diria lo mismo: si no cabe, se deja sin ella y el diagnostico lo dira. */
        private String expresion(ModeloLeido.Nodo nodo) {
            String expresion = nodo.temporizador();
            if (expresion != null && expresion.length() > TEMPORIZADOR) {
                notas.convertido(nodo.id(), "La expresion del temporizador pasa de " + TEMPORIZADOR
                        + " caracteres: queda sin ella.");
                return null;
            }
            return expresion;
        }

        /**
         * PR 55: cada evento de borde con su actividad, ya con todos los nodos leidos: el archivo puede traerlo antes
         * que a ella. Queda en la lane de la actividad, y si ella se dejo fuera, el tambien.
         */
        private void pegarBordes() {
            Map<String, String> originales = originales();
            List<Nodo> pegados = new ArrayList<>(nodos.size());
            for (Nodo borde : nodos) {
                if (borde.tipoEvento() == null || !borde.tipoEvento().esDeBorde()) {
                    pegados.add(borde);
                    continue;
                }
                String original = borde.adjuntoA();
                Nodo actividad = original == null || ids.get(original) == null ? null
                        : nodoPorId.get(ids.get(original));
                if (actividad == null || actividad.clase() != Clase.ACTIVIDAD
                        || !poolDelNodo.get(actividad.bpmnId()).equals(poolDelNodo.get(borde.bpmnId()))) {
                    nodoPorId.remove(borde.bpmnId());
                    poolDelNodo.remove(borde.bpmnId());
                    String enElArchivo = originales.getOrDefault(borde.bpmnId(), borde.bpmnId());
                    descartados.add(enElArchivo);
                    notas.noSoportado(enElArchivo, "Un boundaryEvent pegado a algo que no es una actividad del "
                            + "modelo: se deja fuera.");
                    continue;
                }
                Nodo pegado = new Nodo(borde.bpmnId(), actividad.lane(), borde.clase(), null, null, borde.tipoEvento(),
                        borde.nombre(), borde.descripcion(), 0, 0, actividad.bpmnId(), borde.interrumpe(),
                        borde.tipoTemporizador(), borde.temporizador(), actividad.padre(), null);
                pegados.add(pegado);
                nodoPorId.put(pegado.bpmnId(), pegado);
            }
            nodos.clear();
            nodos.addAll(pegados);
        }

        /** El id en el archivo de cada elemento que se quedo. */
        private Map<String, String> originales() {
            Map<String, String> originales = new HashMap<>();
            ids.forEach((original, suyo) -> originales.put(suyo, original));
            return originales;
        }

        /**
         * La posicion de cada nodo como la guarda el modelo: el centro de su forma, con la x comun a todo el diagrama
         * y la y relativa a su lane, y la menor de cada una en cero. Un nodo sin forma va a la derecha de lo que esta
         * antes que el en el flujo.
         */
        private void posiciones() {
            Map<String, double[]> centros = new HashMap<>();
            Map<String, String> originalDe = new HashMap<>();
            ids.forEach((original, suyo) -> originalDe.put(suyo, original));
            Map<String, Integer> niveles = niveles();
            // Sin dibujo, los que estan a la misma distancia del inicio en una lane van uno debajo de otro
            Map<String, Integer> ocupados = new HashMap<>();
            Set<String> girados = new HashSet<>();
            for (Nodo nodo : nodos) {
                Limites forma = leido.formas().get(originalDe.get(nodo.bpmnId()));
                String pool = poolDelNodo.get(nodo.bpmnId());
                boolean vertical = leido.verticales().contains(originalDe.get(nodo.lane()))
                        || leido.verticales().contains(participanteDelPool.get(pool));
                if (forma != null && vertical) {
                    // Un dibujo vertical va de arriba abajo: su y es el avance del flujo, y su x la altura en la lane
                    centros.put(nodo.bpmnId(), new double[] {forma.y() + forma.alto() / 2,
                            forma.x() + forma.ancho() / 2});
                    if (girados.add(pool)) {
                        notas.convertido(Objects.requireNonNullElse(participanteDelPool.get(pool), pool),
                                "Se dibuja en vertical: se gira para que el flujo vaya de izquierda a derecha.");
                    }
                } else if (forma != null) {
                    centros.put(nodo.bpmnId(), new double[] {forma.x() + forma.ancho() / 2,
                            forma.y() + forma.alto() / 2});
                } else {
                    int nivel = niveles.getOrDefault(nodo.bpmnId(), 0);
                    int antes = ocupados.merge(nodo.lane() + "#" + nivel, 1, Integer::sum) - 1;
                    centros.put(nodo.bpmnId(), new double[] {nivel * 180.0, antes * 100.0});
                }
            }
            // Un evento de borde no cuenta para el marco: se dibuja sobre su actividad, no donde este
            Set<String> cajas = new HashSet<>();
            nodos.forEach(nodo -> cajas.add(nodo.padre()));
            List<Nodo> enElFlujo = nodos.stream()
                    .filter(nodo -> nodo.adjuntoA() == null && !cajas.contains(nodo.bpmnId())).toList();
            menorX = enElFlujo.isEmpty() ? null
                    : enElFlujo.stream().mapToDouble(nodo -> centros.get(nodo.bpmnId())[0]).min().orElse(0);
            for (Nodo nodo : enElFlujo) {
                menorYDeLaLane.merge(nodo.lane(), centros.get(nodo.bpmnId())[1], Math::min);
            }
            nodos.replaceAll(nodo -> {
                if (nodo.adjuntoA() != null || cajas.contains(nodo.bpmnId())) {
                    return nodo;
                }
                double[] centro = centros.get(nodo.bpmnId());
                Nodo ubicado = nodo.en(nodo.lane(), (int) Math.round(centro[0] - menorX),
                        (int) Math.round(centro[1] - menorYDeLaLane.get(nodo.lane())));
                nodoPorId.put(ubicado.bpmnId(), ubicado);
                return ubicado;
            });
            nodos.replaceAll(nodo -> {
                if (nodo.adjuntoA() == null) {
                    return nodo;
                }
                Nodo actividad = nodoPorId.get(nodo.adjuntoA());
                Nodo ubicado = nodo.en(actividad.lane(), actividad.x(), actividad.y());
                nodoPorId.put(ubicado.bpmnId(), ubicado);
                return ubicado;
            });
        }

        // ---------- Artefactos y asociaciones ----------

        /** Un artefacto mientras se decide donde va: su esquina en el dibujo, ya girada si el dibujo es vertical. */
        private record Colocado(String id, TipoArtefacto tipo, String texto, String pool, String lane, double x,
                double y, int ancho, int alto) {
        }

        /**
         * PR 55: los artefactos que el modelo puede guardar. Un {@code dataObject} solo cuenta si nadie lo referencia
         * y esta dibujado: lo normal es dibujar su referencia. Los que caen dentro de una lane quedan en ella, con el
         * marco de sus nodos; los demas quedan sueltos.
         */
        private void artefactos() {
            Set<String> referenciados = new HashSet<>();
            leido.artefactos().forEach(artefacto -> referenciados.add(artefacto.objeto()));
            Map<String, String> originales = originales();
            Map<String, String> poolDeLaLane = new HashMap<>();
            lanes.forEach(lane -> poolDeLaLane.put(lane.bpmnId(), lane.pool()));
            List<Colocado> colocados = new ArrayList<>();
            int sinDibujo = 0;
            for (ModeloLeido.Artefacto artefacto : leido.artefactos()) {
                if ("dataObject".equals(artefacto.elemento()) && (referenciados.contains(artefacto.id())
                        || !leido.formas().containsKey(artefacto.id()))) {
                    continue;
                }
                TipoArtefacto tipo = switch (artefacto.elemento()) {
                    case "textAnnotation" -> TipoArtefacto.ANOTACION;
                    case "group" -> TipoArtefacto.GRUPO;
                    case "dataStoreReference" -> TipoArtefacto.ALMACEN_DATOS;
                    default -> TipoArtefacto.OBJETO_DATOS;
                };
                String pool = artefacto.proceso() == null ? null : poolDelProceso.get(artefacto.proceso());
                if (pool != null && esCajaNegra(pool)) {
                    pool = null;
                }
                boolean esDato = tipo == TipoArtefacto.OBJETO_DATOS || tipo == TipoArtefacto.ALMACEN_DATOS;
                if (esDato && pool == null) {
                    descartados.add(artefacto.id());
                    notas.noSoportado(Objects.requireNonNullElse(artefacto.id(), artefacto.elemento()), "Un "
                            + artefacto.elemento() + " de un proceso que no se importa: se deja fuera.");
                    continue;
                }
                Limites forma = leido.formas().get(artefacto.id());
                int[] porDefecto = Artefacto.tamanoPorDefecto(tipo);
                int ancho = forma == null || forma.ancho() < 1 ? porDefecto[0] : (int) Math.round(forma.ancho());
                int alto = forma == null || forma.alto() < 1 ? porDefecto[1] : (int) Math.round(forma.alto());
                String lane = laneDe(forma, pool, artefacto.proceso(), originales);
                if (lane != null) {
                    pool = poolDeLaLane.get(lane);
                }
                double x = forma == null ? (sinDibujo++) * SEPARACION_SIN_DIBUJO : forma.x();
                double y = forma == null ? 0 : forma.y();
                if (forma != null && lane != null && (leido.verticales().contains(originales.get(lane))
                        || leido.verticales().contains(participanteDelPool.get(pool)))) {
                    x = forma.y() + forma.alto() / 2 - ancho / 2.0;
                    y = forma.x() + forma.ancho() / 2 - alto / 2.0;
                }
                String id = id(artefacto.id(), Artefacto.prefijoBpmn(tipo), null);
                colocados.add(new Colocado(id, tipo, recortar(artefacto.texto(), TEXTO_ARTEFACTO), pool, lane, x, y,
                        ancho, alto));
                notas.soportado("artefactos");
            }
            enSuMarco(colocados);
        }

        /**
         * Cada artefacto en el marco que guarda el modelo. Un nodo guarda su centro y un artefacto su esquina, y se
         * dibujan alineados; una lane sin nodos toma el marco de sus artefactos, y los sueltos el de los sueltos.
         */
        private void enSuMarco(List<Colocado> colocados) {
            double menorXSuelta = Double.MAX_VALUE;
            double menorYSuelta = Double.MAX_VALUE;
            double menorXEnLanes = Double.MAX_VALUE;
            Map<String, Double> menorYDeArtefactos = new HashMap<>();
            for (Colocado uno : colocados) {
                if (uno.lane() == null) {
                    menorXSuelta = Math.min(menorXSuelta, uno.x());
                    menorYSuelta = Math.min(menorYSuelta, uno.y());
                } else {
                    menorXEnLanes = Math.min(menorXEnLanes, uno.x());
                    menorYDeArtefactos.merge(uno.lane(), uno.y(), Math::min);
                }
            }
            for (Colocado uno : colocados) {
                double x;
                double y;
                if (uno.lane() == null) {
                    x = uno.x() - menorXSuelta;
                    y = uno.y() - menorYSuelta;
                } else {
                    x = menorX != null ? uno.x() + DisposicionBpmn.ANCHO_TAREA / 2 - menorX : uno.x() - menorXEnLanes;
                    Double menorY = menorYDeLaLane.get(uno.lane());
                    y = menorY != null ? uno.y() + DisposicionBpmn.ALTO_TAREA / 2 - menorY
                            : uno.y() - menorYDeArtefactos.get(uno.lane());
                }
                artefactos.add(new PlanBpmn.Artefacto(uno.id(), uno.tipo(), uno.texto(), uno.pool(), uno.lane(),
                        (int) Math.round(x), (int) Math.round(y), uno.ancho(), uno.alto()));
                idsDeArtefactos.add(uno.id());
                if (uno.tipo() == TipoArtefacto.OBJETO_DATOS || uno.tipo() == TipoArtefacto.ALMACEN_DATOS) {
                    idsDeDatos.add(uno.id());
                }
            }
        }

        /**
         * La lane donde cae un artefacto: la que contiene su centro; si esta en un pool sin lanes dibujadas, la unica
         * que tenga. Si dice de que pool es, solo cuenta una lane de ese pool.
         */
        private String laneDe(Limites forma, String pool, String proceso, Map<String, String> originales) {
            if (forma != null) {
                double cx = forma.x() + forma.ancho() / 2;
                double cy = forma.y() + forma.alto() / 2;
                for (Lane lane : lanes) {
                    Limites suya = leido.formas().get(originales.get(lane.bpmnId()));
                    if (suya != null && contiene(suya, cx, cy) && (pool == null || pool.equals(lane.pool()))) {
                        return lane.bpmnId();
                    }
                }
                for (Pool candidato : pools) {
                    Limites suya = leido.formas().get(participanteDelPool.get(candidato.bpmnId()));
                    if (suya != null && contiene(suya, cx, cy) && (pool == null || pool.equals(candidato.bpmnId()))) {
                        return unicaLane(candidato.bpmnId());
                    }
                }
            }
            boolean poolDibujado = pool != null && leido.formas().containsKey(participanteDelPool.get(pool));
            return proceso != null && pool != null && !poolDibujado ? unicaLane(pool) : null;
        }

        private String unicaLane(String pool) {
            List<Lane> suyas = lanes.stream().filter(lane -> lane.pool().equals(pool)).toList();
            return suyas.size() == 1 ? suyas.getFirst().bpmnId() : null;
        }

        private static boolean contiene(Limites caja, double x, double y) {
            return x >= caja.x() && x <= caja.x() + caja.ancho() && y >= caja.y() && y <= caja.y() + caja.alto();
        }

        /**
         * PR 55: las asociaciones cuyos dos extremos se quedaron. Una asociacion lleva un artefacto en algun lado, y
         * una de datos une un dato con una actividad.
         */
        private void asociaciones() {
            Set<String> repetidas = new HashSet<>();
            arcos.forEach(arco -> idsDeArcos.add(arco.bpmnId()));
            for (ModeloLeido.Asociacion asociacion : leido.asociaciones()) {
                String donde = Objects.requireNonNullElse(asociacion.id(), asociacion.elemento());
                TipoAsociacion tipo = switch (asociacion.elemento()) {
                    case "dataInputAssociation" -> TipoAsociacion.ENTRADA_DATOS;
                    case "dataOutputAssociation" -> TipoAsociacion.SALIDA_DATOS;
                    default -> TipoAsociacion.ASOCIACION;
                };
                String origen = extremoDeAsociacion(asociacion.origen());
                String destino = extremoDeAsociacion(asociacion.destino());
                if (origen == null || destino == null || origen.equals(destino)) {
                    descartados.add(asociacion.id());
                    notas.noSoportado(donde, "Una " + asociacion.elemento() + " con un extremo que no se importa: se "
                            + "deja fuera.");
                    continue;
                }
                if (!tieneSentido(tipo, origen, destino)) {
                    descartados.add(asociacion.id());
                    notas.noSoportado(donde, "Una " + asociacion.elemento() + " entre elementos que el modelo no une "
                            + "asi: se deja fuera.");
                    continue;
                }
                if (!repetidas.add(tipo + "|" + origen + "|" + destino)) {
                    descartados.add(asociacion.id());
                    notas.convertido(donde, "Ya hay una igual entre esos dos elementos: se deja solo la primera.");
                    continue;
                }
                String prefijo = tipo == TipoAsociacion.ASOCIACION ? "Association" : "DataAssociation";
                String id = id(asociacion.id(), prefijo, null);
                asociaciones.add(new PlanBpmn.Asociacion(id, tipo, origen, destino,
                        direccion(tipo, asociacion.direccion())));
                idsDeAsociaciones.add(id);
                notas.soportado("asociaciones");
            }
        }

        private String extremoDeAsociacion(String original) {
            String id = original == null ? null : ids.get(original);
            boolean queda = id != null && (nodoPorId.containsKey(id) || idsDeArtefactos.contains(id)
                    || idsDeArcos.contains(id));
            return queda ? id : null;
        }

        private boolean esActividad(String id) {
            Nodo nodo = nodoPorId.get(id);
            return nodo != null && nodo.clase() == Clase.ACTIVIDAD;
        }

        private boolean tieneSentido(TipoAsociacion tipo, String origen, String destino) {
            return switch (tipo) {
                case ASOCIACION -> idsDeArtefactos.contains(origen) || idsDeArtefactos.contains(destino);
                case ENTRADA_DATOS -> idsDeDatos.contains(origen) && esActividad(destino);
                case SALIDA_DATOS -> esActividad(origen) && idsDeDatos.contains(destino);
            };
        }

        private static DireccionAsociacion direccion(TipoAsociacion tipo, String dicha) {
            if (tipo != TipoAsociacion.ASOCIACION || dicha == null) {
                return DireccionAsociacion.NINGUNA;
            }
            return switch (dicha.strip()) {
                case "One" -> DireccionAsociacion.UNA;
                case "Both" -> DireccionAsociacion.AMBAS;
                default -> DireccionAsociacion.NINGUNA;
            };
        }

        /** Que tan lejos del inicio esta cada nodo siguiendo los flujos: lo que ordena un archivo sin dibujo. */
        private Map<String, Integer> niveles() {
            Map<String, List<String>> salidas = new HashMap<>();
            for (Proceso proceso : leido.procesos()) {
                for (Flujo flujo : proceso.flujos()) {
                    String origen = ids.get(flujo.origen());
                    String destino = ids.get(flujo.destino());
                    if (origen != null && destino != null) {
                        salidas.computeIfAbsent(origen, clave -> new ArrayList<>()).add(destino);
                    }
                }
            }
            // A un evento de borde se llega por su actividad, y lo que sale de el va despues de ella
            nodos.stream().filter(nodo -> nodo.adjuntoA() != null).forEach(borde -> salidas
                    .computeIfAbsent(borde.adjuntoA(), clave -> new ArrayList<>()).add(borde.bpmnId()));
            Map<String, Integer> niveles = new HashMap<>();
            Deque<String> pendientes = new ArrayDeque<>();
            for (Nodo nodo : nodos) {
                if (nodo.tipoEvento() != null && nodo.tipoEvento().empiezaElProceso()) {
                    niveles.put(nodo.bpmnId(), 0);
                    pendientes.add(nodo.bpmnId());
                }
            }
            while (!pendientes.isEmpty()) {
                String actual = pendientes.poll();
                for (String siguiente : salidas.getOrDefault(actual, List.of())) {
                    if (!niveles.containsKey(siguiente)) {
                        niveles.put(siguiente, niveles.get(actual) + 1);
                        pendientes.add(siguiente);
                    }
                }
            }
            return niveles;
        }

        // ---------- Flujos ----------

        private void flujosDe(Proceso proceso, String pool) {
            Set<String> pares = new HashSet<>();
            List<Arco> delProceso = new ArrayList<>();
            for (Flujo flujo : proceso.flujos()) {
                String origen = ids.get(flujo.origen());
                String destino = ids.get(flujo.destino());
                if (origen == null || destino == null || !nodoPorId.containsKey(origen)
                        || !nodoPorId.containsKey(destino)) {
                    descartados.add(flujo.id());
                    boolean porAlgoQueSeDejo = descartados.contains(flujo.origen())
                            || descartados.contains(flujo.destino());
                    notas.noSoportado(flujo.id(), porAlgoQueSeDejo
                            ? "Toca un elemento que se dejo fuera, asi que tambien se deja fuera."
                            : "Apunta a un elemento que no esta en el proceso: se deja fuera.");
                    continue;
                }
                if (!pool.equals(poolDelNodo.get(origen)) || !pool.equals(poolDelNodo.get(destino))) {
                    notas.error("El flujo " + flujo.id() + " cruza de un pool a otro: entre pools solo van mensajes.");
                    continue;
                }
                Nodo de = nodoPorId.get(origen);
                Nodo a = nodoPorId.get(destino);
                if (de.tipoEvento() != null && de.tipoEvento().terminaElProceso()) {
                    notas.error("El flujo " + flujo.id() + " sale de un evento de fin (" + de.nombre() + ").");
                    continue;
                }
                if (a.tipoEvento() != null && a.tipoEvento().empiezaElProceso()) {
                    notas.error("El flujo " + flujo.id() + " llega a un evento de inicio (" + a.nombre() + ").");
                    continue;
                }
                if (!Objects.equals(de.padre(), a.padre())) {
                    notas.error("El flujo " + flujo.id() + " cruza el borde de un subproceso.");
                    continue;
                }
                if (a.tipoEvento() != null && a.tipoEvento().esDeBorde()) {
                    notas.error("El flujo " + flujo.id() + " llega a un evento de borde (" + a.nombre() + ").");
                    continue;
                }
                if (origen.equals(destino)) {
                    descartados.add(flujo.id());
                    notas.convertido(flujo.id(), "Un flujo de un nodo a si mismo: el modelo no lo tiene, se deja fuera.");
                    continue;
                }
                if (!pares.add(origen + "→" + destino)) {
                    descartados.add(flujo.id());
                    notas.convertido(flujo.id(), "Ya hay un flujo entre esos dos nodos: se deja solo el primero.");
                    continue;
                }
                String condicion = flujo.condicion();
                if (condicion != null && flujo.lenguaje() != null
                        && !EspaciosBpmn.CONDICIONES.equals(flujo.lenguaje())) {
                    notas.convertido(flujo.id(), "La condicion esta escrita en " + flujo.lenguaje()
                            + ": se conserva el texto, revisalo antes de publicar.");
                }
                if (condicion != null && condicion.length() > CONDICION) {
                    notas.convertido(flujo.id(), "La condicion pasa de " + CONDICION + " caracteres: se recorta.");
                    condicion = condicion.substring(0, CONDICION);
                }
                String id = id(flujo.id(), "Flow", null);
                delProceso.add(new Arco(id, pool, origen, destino, recortar(flujo.nombre(), NOMBRE), condicion, false,
                        flujo.orden() == null ? 0 : flujo.orden()));
            }
            salidasPorDefecto(proceso, delProceso);
            arcos.addAll(delProceso);
            delProceso.forEach(arco -> notas.soportado("flujos"));
        }

        /**
         * R-35 y R-36: de un gateway exclusivo o inclusivo, cada salida lleva condicion menos la de por defecto, que es
         * una y no lleva. Si una sola salida no tiene condicion y el archivo no marca ninguna, esa es la de por
         * defecto, que es lo que BPMN hace con ella.
         */
        private void salidasPorDefecto(Proceso proceso, List<Arco> delProceso) {
            for (ModeloLeido.Nodo leidoNodo : proceso.nodos()) {
                String id = ids.get(leidoNodo.id());
                Nodo nodo = id == null ? null : nodoPorId.get(id);
                if (nodo == null) {
                    continue;
                }
                String dicho = leidoNodo.porDefecto() == null ? null : ids.get(leidoNodo.porDefecto());
                boolean elige = nodo.tipoGateway() == TipoGateway.EXCLUSIVO || nodo.tipoGateway() == TipoGateway.INCLUSIVO;
                if (!elige) {
                    if (dicho != null) {
                        notas.convertido(leidoNodo.id(), "Solo un gateway exclusivo o inclusivo tiene salida por "
                                + "defecto: se quita la marca.");
                    }
                    continue;
                }
                List<Arco> salidas = delProceso.stream().filter(arco -> arco.origen().equals(id)).toList();
                String marcado = dicho != null && salidas.stream().anyMatch(arco -> arco.bpmnId().equals(dicho))
                        ? dicho : null;
                String elegida = marcado;
                List<Arco> sinCondicion = salidas.stream()
                        .filter(arco -> arco.condicion() == null && !arco.bpmnId().equals(elegida)).toList();
                if (marcado == null && sinCondicion.size() == 1) {
                    marcado = sinCondicion.getFirst().bpmnId();
                    notas.convertido(marcado, "La unica salida sin condicion del gateway " + nodo.nombre()
                            + " pasa a ser su salida por defecto.");
                } else if (!sinCondicion.isEmpty()) {
                    notas.convertido(leidoNodo.id(), "El gateway " + nodo.nombre() + " tiene " + sinCondicion.size()
                            + " salidas sin condicion: se importan asi, y el diagnostico (E-07) las pide antes de "
                            + "publicar.");
                }
                String porDefecto = marcado;
                delProceso.replaceAll(arco -> {
                    if (!arco.bpmnId().equals(porDefecto)) {
                        return arco;
                    }
                    if (arco.condicion() != null) {
                        notas.convertido(arco.bpmnId(), "La salida por defecto no lleva condicion: se quita.");
                    }
                    return new Arco(arco.bpmnId(), arco.pool(), arco.origen(), arco.destino(), arco.etiqueta(), null,
                            true, arco.orden());
                });
            }
        }

        // ---------- Mensajes ----------

        private void mensajes() {
            Map<String, FlujoDeMensaje> porId = new LinkedHashMap<>();
            for (FlujoDeMensaje flujo : leido.flujosDeMensaje()) {
                String origen = extremo(flujo.origen());
                String destino = extremo(flujo.destino());
                if (origen == null || destino == null) {
                    notas.noSoportado(flujo.id(), "Uno de sus extremos se dejo fuera o no esta en el archivo: se deja "
                            + "fuera.");
                    continue;
                }
                String poolOrigen = poolDe(origen);
                String poolDestino = poolDe(destino);
                if (poolOrigen.equals(poolDestino)) {
                    notas.error("El mensaje " + flujo.id() + " va de un pool a si mismo: un mensaje une dos pools.");
                    continue;
                }
                String id = id(flujo.id(), "MessageFlow", null);
                porId.put(id, flujo);
                mensajes.add(mensaje(id, flujo, origen, destino, poolOrigen, poolDestino));
                notas.soportado("mensajes");
            }
            // La respuesta se resuelve cuando ya estan todos: puede venir antes o despues en el archivo
            mensajes.replaceAll(mensaje -> conRespuesta(mensaje, porId.get(mensaje.bpmnId())));
        }

        private Mensaje mensaje(String id, FlujoDeMensaje flujo, String origen, String destino, String poolOrigen,
                String poolDestino) {
            DatosDelMensaje datos = flujo.datos();
            String nodoOrigen = nodoPorId.containsKey(origen) ? origen : null;
            String nodoDestino = nodoPorId.containsKey(destino) ? destino : null;
            if (nodoOrigen != null && !puedeEnviar(nodoPorId.get(nodoOrigen))) {
                notas.convertido(flujo.id(), nodoPorId.get(nodoOrigen).nombre() + " no manda mensajes: el mensaje "
                        + "sale del borde de su pool.");
                nodoOrigen = null;
            }
            if (nodoDestino != null && !puedeRecibir(nodoPorId.get(nodoDestino))) {
                notas.convertido(flujo.id(), nodoPorId.get(nodoDestino).nombre() + " no espera mensajes: el mensaje "
                        + "llega al borde de su pool.");
                nodoDestino = null;
            }
            String nombreDelMensaje = flujo.mensaje() == null ? null : leido.mensajes().get(flujo.mensaje());
            String nombre = nombre(flujo.nombre(), nombreDelMensaje, "Mensaje", flujo.id(), "el mensaje");
            String contenido = recortar(datos != null && datos.contenido() != null ? datos.contenido() : nombre,
                    CONTENIDO);
            TipoDestino tipoDestino = datos == null ? null
                    : valor(TipoDestino.class, datos.destino(), null, flujo.id(), "destino");
            AccionSiFalla siFalla = datos == null ? AccionSiFalla.CONTINUAR
                    : valor(AccionSiFalla.class, datos.siFalla(), AccionSiFalla.CONTINUAR, flujo.id(), "fallo");
            String manejoError = null;
            if (siFalla.necesitaActividad()) {
                manejoError = datos.manejoError() == null ? null : ids.get(datos.manejoError());
                Nodo actividad = manejoError == null ? null : nodoPorId.get(manejoError);
                if (actividad == null || actividad.clase() != Clase.ACTIVIDAD
                        || !poolOrigen.equals(poolDelNodo.get(manejoError))) {
                    notas.convertido(flujo.id(), "La actividad que maneja su error no esta en el pool que lo manda: "
                            + "si falla, sigue.");
                    siFalla = AccionSiFalla.CONTINUAR;
                    manejoError = null;
                }
            }
            String variable = variable(datos, nombre, flujo.id());
            return new Mensaje(id, nombre, contenido, poolOrigen, poolDestino, nodoOrigen, nodoDestino, tipoDestino,
                    siFalla, manejoError, datos != null && datos.origenExterno(), campos(datos, flujo.id()),
                    datos == null ? null : recortar(datos.usoDeLosDatos(), USO), variable, null,
                    correlacion(datos, flujo.id()));
        }

        /** R-40: la respuesta es otro mensaje del archivo que vuelve del pool de destino. */
        private Mensaje conRespuesta(Mensaje mensaje, FlujoDeMensaje flujo) {
            DatosDelMensaje datos = flujo.datos();
            if (datos == null || datos.respuesta() == null) {
                return mensaje;
            }
            String respuesta = ids.get(datos.respuesta());
            Mensaje vuelta = respuesta == null ? null : mensajes.stream()
                    .filter(otro -> otro.bpmnId().equals(respuesta)).findFirst().orElse(null);
            if (vuelta == null || vuelta == mensaje || !vuelta.poolOrigen().equals(mensaje.poolDestino())
                    || !vuelta.poolDestino().equals(mensaje.poolOrigen())) {
                notas.convertido(flujo.id(), "Su respuesta no es un mensaje que vuelva del pool de destino: se quita.");
                return mensaje;
            }
            return new Mensaje(mensaje.bpmnId(), mensaje.nombre(), mensaje.contenido(), mensaje.poolOrigen(),
                    mensaje.poolDestino(), mensaje.nodoOrigen(), mensaje.nodoDestino(), mensaje.tipoDestino(),
                    mensaje.siFalla(), mensaje.manejoError(), mensaje.origenExterno(), mensaje.campos(),
                    mensaje.usoDeLosDatos(), mensaje.variable(), respuesta, mensaje.correlacion());
        }

        /** El id en el modelo del nodo o el pool del extremo, o nulo si se dejo fuera. */
        private String extremo(String original) {
            if (original == null) {
                return null;
            }
            String nodo = ids.get(original);
            if (nodo != null && nodoPorId.containsKey(nodo)) {
                return nodo;
            }
            return poolDelParticipante.get(original);
        }

        private String poolDe(String extremo) {
            return nodoPorId.containsKey(extremo) ? poolDelNodo.get(extremo) : extremo;
        }

        private static boolean puedeEnviar(Nodo nodo) {
            return nodo.tipoActividad() != null ? nodo.tipoActividad().puedeEnviar()
                    : nodo.tipoEvento() != null && nodo.tipoEvento().puedeEnviar();
        }

        private static boolean puedeRecibir(Nodo nodo) {
            return nodo.tipoActividad() != null ? nodo.tipoActividad().puedeRecibir()
                    : nodo.tipoEvento() != null && nodo.tipoEvento().puedeRecibir();
        }

        private List<CampoDeMensaje> campos(DatosDelMensaje datos, String flujo) {
            if (datos == null) {
                return List.of();
            }
            List<CampoDeMensaje> campos = new ArrayList<>();
            Set<String> vistos = new HashSet<>();
            for (CampoDeMensaje campo : datos.campos()) {
                String nombre = recortar(campo.nombre(), CAMPO_DEL_MENSAJE);
                if (!vistos.add(nombre.toLowerCase(Locale.ROOT))) {
                    notas.convertido(flujo, "El campo " + nombre + " se repite: queda el primero.");
                    continue;
                }
                campos.add(new CampoDeMensaje(nombre, campo.tipo()));
            }
            return campos;
        }

        private String variable(DatosDelMensaje datos, String nombre, String flujo) {
            String dicha = datos == null ? null : datos.variable();
            if (dicha != null && VARIABLE.matcher(dicha.strip()).matches()) {
                return dicha.strip();
            }
            if (dicha != null) {
                notas.convertido(flujo, "La variable " + dicha + " no es un nombre valido: se usa la de su nombre.");
            }
            return com.facimus.procesos.modelado.model.Mensaje.variablePorDefecto(nombre);
        }

        private Correlacion correlacion(DatosDelMensaje datos, String flujo) {
            if (datos == null || datos.correlacion() == null) {
                return null;
            }
            ModeloLeido.Correlacion leida = datos.correlacion();
            if (leida.criterio() == null) {
                notas.convertido(flujo, "Su correlacion no dice el criterio: se quita.");
                return null;
            }
            return new Correlacion(recortar(leida.criterio(), CRITERIO), recortar(leida.campo(), CAMPO),
                    valor(PoliticaSinCaso.class, leida.sinCaso(), PoliticaSinCaso.DESCARTAR, flujo, "politica"));
        }

        // ---------- Extensiones ----------

        /** Lo ajeno de cada elemento que queda, con la clave del modelo y dentro de los topes. */
        private Map<String, String> extensiones() {
            Map<String, String> extensiones = new LinkedHashMap<>();
            int total = 0;
            for (Map.Entry<String, String> leida : leido.extensiones().entrySet()) {
                String clave = clave(leida.getKey());
                if (clave == null) {
                    continue;
                }
                int bytes = leida.getValue().getBytes(StandardCharsets.UTF_8).length;
                if (bytes > ExtensionesAjenas.TOPE_POR_ELEMENTO || total + bytes > ExtensionesAjenas.TOPE_POR_ARCHIVO) {
                    notas.noSoportado(leida.getKey(), "Lo que traia de otra herramienta pasa de "
                            + (bytes > ExtensionesAjenas.TOPE_POR_ELEMENTO
                                    ? ExtensionesAjenas.TOPE_POR_ELEMENTO / 1024 + " KB por elemento"
                                    : ExtensionesAjenas.TOPE_POR_ARCHIVO / 1024 + " KB en el archivo")
                            + ": no se conserva.");
                    continue;
                }
                total += bytes;
                extensiones.put(clave, leida.getValue());
            }
            return extensiones;
        }

        private String clave(String leida) {
            if (ExtensionXml.DEFINICIONES.equals(leida) || ExtensionXml.COLABORACION.equals(leida)) {
                return leida;
            }
            if (leida.startsWith("process:")) {
                String pool = poolDelProceso.get(leida.substring("process:".length()));
                return pool == null ? null : ExtensionXml.delProceso(pool);
            }
            if (leida.endsWith("#di")) {
                String elemento = suyo(leida.substring(0, leida.length() - "#di".length()));
                return elemento == null ? null : ExtensionXml.delDibujo(elemento);
            }
            return suyo(leida);
        }

        /** El id en el modelo de un elemento del archivo que se quedo, sea pool, lane, nodo, flujo o mensaje. */
        private String suyo(String original) {
            String pool = poolDelParticipante.get(original);
            if (pool != null) {
                return pool;
            }
            String id = ids.get(original);
            if (id == null) {
                return null;
            }
            boolean queda = nodoPorId.containsKey(id) || lanes.stream().anyMatch(lane -> lane.bpmnId().equals(id))
                    || arcos.stream().anyMatch(arco -> arco.bpmnId().equals(id))
                    || mensajes.stream().anyMatch(mensaje -> mensaje.bpmnId().equals(id))
                    || idsDeArtefactos.contains(id) || idsDeAsociaciones.contains(id);
            return queda ? id : null;
        }

        // ---------- Ids, nombres y valores ----------

        /**
         * El id que tendra el elemento: el del archivo si es un xsd:ID de hasta 64 caracteres que nadie mas usa, si
         * no uno nuevo de su tipo.
         */
        private String id(String original, String prefijo, String sinId) {
            if (original != null && ID.matcher(original).matches() && !usados.contains(original)) {
                usados.add(original);
                ids.put(original, original);
                return original;
            }
            String nuevo;
            do {
                nuevo = BpmnId.nuevo(prefijo);
            } while (usados.contains(nuevo));
            usados.add(nuevo);
            if (original != null) {
                ids.put(original, nuevo);
                notas.convertido(original, "Su id no sirve en el modelo (o se repite): pasa a ser " + nuevo + ".");
            } else if (sinId == null) {
                notas.convertido(nuevo, "Venia sin id: se le dio " + nuevo + ".");
            }
            return nuevo;
        }

        private String nombre(String dicho, String otro, String porDefecto, String elemento, String queEs) {
            String nombre = dicho != null ? dicho : otro;
            if (nombre == null) {
                notas.convertido(Objects.requireNonNullElse(elemento, porDefecto), "Venia sin nombre: se llama "
                        + porDefecto + ".");
                return porDefecto;
            }
            if (nombre.length() > NOMBRE) {
                notas.convertido(Objects.requireNonNullElse(elemento, nombre), "El nombre de " + queEs + " pasa de "
                        + NOMBRE + " caracteres: se recorta.");
                return nombre.substring(0, NOMBRE);
            }
            return nombre;
        }

        private String nombreDelProceso(Participante organizacion) {
            Proceso proceso = procesoDe(organizacion);
            String nombre = leido.nombre() != null ? leido.nombre()
                    : proceso != null && proceso.nombre() != null ? proceso.nombre() : organizacion.nombre();
            return recortar(nombre, NOMBRE);
        }

        /** El valor de un enumerado; uno que no existe se anota y queda el de por defecto. */
        private <E extends Enum<E>> E valor(Class<E> tipo, String dicho, E porDefecto, String elemento, String que) {
            if (dicho == null) {
                return porDefecto;
            }
            try {
                return Enum.valueOf(tipo, dicho.strip());
            } catch (IllegalArgumentException noExiste) {
                notas.convertido(Objects.requireNonNullElse(elemento, que), "El " + que + " " + dicho
                        + " no existe: queda " + (porDefecto == null ? "sin " + que : porDefecto) + ".");
                return porDefecto;
            }
        }
    }

    static String recortar(String texto, int largo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.strip();
        return limpio.length() > largo ? limpio.substring(0, largo) : limpio;
    }
}
