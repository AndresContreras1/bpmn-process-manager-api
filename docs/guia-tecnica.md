# Guia tecnica del backend — Sistema de Gestion de Procesos

Documento interno del equipo. Describe el estado actual del backend, las
decisiones vigentes, la infraestructura de CI/CD, el despliegue en Docker y
las guias que todo colaborador debe seguir para contribuir sin romper las
garantias que ya estan en pie.

Ultima actualizacion: 2026-09-01 (rama `entrega-1-backend`).

---

## 1. Estado actual del proyecto

### Stack tecnologico

| Componente | Version |
|---|---|
| Java | 21 (Temurin) |
| Spring Boot | 4.1.0 |
| Motor de templates | Thymeleaf |
| ORM | Hibernate (via Spring Data JPA) |
| BD desarrollo | H2 embebida en archivo (`./data/`) |
| BD produccion | PostgreSQL (via perfil `prod`) |
| Cifrado de passwords | BCrypt (`spring-security-crypto`) |
| Tests de arquitectura | ArchUnit 1.4.0 |
| Cobertura | JaCoCo 0.8.13 |
| CI/CD | GitHub Actions |
| Contenedorizacion | Docker (multi-stage build) |

### Entidades implementadas (13)

**Bloque gestion** (5): `Empresa`, `Usuario`, `Proceso`, `HistorialCambio`,
`RolProceso`.

**Bloque modelado** (8): `Pool`, `Lane`, `NodoFlujo` (abstracta), `Actividad`,
`Gateway`, `Arco`, `Mensaje`, `Correlacion`.

**Enums** (4): `RolAcceso`, `EstadoProceso`, `TipoParticipante`, `TipoGateway`.

Todas las entidades excepto `Empresa` extienden `EntidadEmpresa`
(`@MappedSuperclass`) que aporta la columna `empresa_id` con
`updatable = false`.

### Servicios implementados (12)

Bloque gestion: `EmpresaService`, `UsuarioService`, `ProcesoService`,
`HistorialCambioService`, `RolProcesoService`.

Bloque modelado: `PoolService`, `LaneService`, `ActividadService`,
`GatewayService`, `ArcoService`, `MensajeService`, `CorrelacionService`.

Todos reciben `empresaId` del controlador y lo usan para acotar cada
operacion al tenant correcto.

### Controladores y vistas (Entrega 1)

22 rutas Thymeleaf para el bloque gestion. El bloque modelado no tiene
controladores en esta entrega — se expone via API REST en la Entrega 2.

| Modulo | Rutas | Controlador |
|---|---|---|
| Sesion | `/login`, `/logout` | `SesionController` |
| Empresas | `/empresas/registro` | `EmpresaController` |
| Usuarios | `/usuarios/**` | `UsuarioController` |
| Procesos | `/procesos/**` | `ProcesoController` |
| Roles de proceso | `/roles/**` | `RolProcesoController` |

---

## 2. Decisiones de arquitectura vigentes

Estas decisiones estan protegidas por tests de ArchUnit. Si un PR las
viola, el CI falla automaticamente. No se deben cambiar sin un ADR nuevo
discutido con el equipo.

### 2.1 Empaquetado modular por dominio

```
com.facimus.procesos
├── common/       Clases transversales (EntidadEmpresa, excepciones, RepositorioTenant)
├── config/       Configuracion de Spring (sesion, interceptor, seed)
├── gestion/      Entidades de gestion del sistema
│   ├── model/
│   ├── repository/
│   ├── service/
│   └── controller/
└── modelado/     Entidades del modelado BPMN
    ├── model/
    ├── repository/
    └── service/
```

**Reglas verificadas por CI:**
- Los `@Controller` viven en paquetes `controller/`.
- Los `@Service` viven en paquetes `service/`.
- Los `@Entity` viven en paquetes `model/`.
- Los controllers no acceden directamente a repositorios (deben pasar por
  servicios).
- `modelado` no depende de `gestion.controller`.

### 2.2 Multi-tenencia por columna directa

- Toda `@Entity` excepto `Empresa` extiende `EntidadEmpresa`.
- `empresa_id` es `nullable = false, updatable = false`.
- Todo repositorio excepto `EmpresaRepository` extiende `RepositorioTenant<T>`,
  que expone `findByIdAndEmpresaId`, `findAllByEmpresaId` y
  `existsByIdAndEmpresaId`.
- **Toda query nueva debe filtrar por `empresaId`. Sin excepciones.** La unica
  excepcion documentada es `UsuarioRepository.findByEmail()` para el login.

### 2.3 Herencia JPA

- `NodoFlujo` es abstracta con `@Inheritance(SINGLE_TABLE)` y
  `@DiscriminatorColumn(name = "tipo_nodo")`.
- Solo tiene dos subtipos: `Actividad` y `Gateway`.

### 2.4 Enums

Todos los campos `@Enumerated` usan `EnumType.STRING`. Nunca `ORDINAL`.
Agregar un valor al enum o reordenarlo no corrompe datos existentes.

### 2.5 Eliminacion logica

`Proceso` y `RolProceso` usan `boolean activo`. Nunca se hace `DELETE`
fisico sobre estas entidades.

---

## 3. Suite de tests

### 3.1 Inventario

| Tipo | Clase | Tests | Que valida |
|---|---|---|---|
| Contexto | `ProcesosApplicationTests` | 1 | Spring Boot arranca con las 13 entidades |
| Arquitectura | `EmpaquetadoTest` | 6 | Ubicacion de controllers, services, repos, entidades; controllers no usan repos directamente |
| Arquitectura | `MultitenenciaTest` | 2 | Toda entity extiende EntidadEmpresa; updatable=false |
| Arquitectura | `RepositorioTenantTest` | 1 | Todo repo extiende RepositorioTenant |
| Arquitectura | `HerenciaJpaTest` | 3 | NodoFlujo SINGLE_TABLE; subtipos correctos; enums STRING |
| Unitario | `EmpresaServiceTest` | 2 | Registro con admin, NIT duplicado |
| Unitario | `UsuarioServiceTest` | 6 | CRUD, autenticacion, email duplicado, usuario inactivo |
| Unitario | `ProcesoServiceTest` | 5 | Crear con pool, nombre duplicado, publicar, eliminar logico, tenant isolation |
| Unitario | `RolProcesoServiceTest` | 3 | Crear, eliminar con uso, eliminar sin uso |
| **Total** | | **30** | |

### 3.2 Ejecucion local

```bash
# Todos los tests
./mvnw test

# Solo tests de arquitectura
./mvnw test -Dtest="MultitenenciaTest,EmpaquetadoTest,HerenciaJpaTest,RepositorioTenantTest"

# Solo tests unitarios de servicios
./mvnw test -Dtest="EmpresaServiceTest,UsuarioServiceTest,ProcesoServiceTest,RolProcesoServiceTest"
```

El reporte de cobertura JaCoCo se genera en `target/site/jacoco/index.html`
despues de correr los tests.

### 3.3 Agregar tests nuevos

- Tests de arquitectura van en `src/test/java/com/facimus/procesos/arquitectura/`.
- Tests unitarios de servicio van en el paquete espejo del servicio bajo
  `src/test/`. Ejemplo: servicio en `gestion.service.FooService` → test en
  `gestion.service.FooServiceTest`.
- Usar `@ExtendWith(MockitoExtension.class)` con `@Mock` e `@InjectMocks`.
  No usar `@SpringBootTest` para tests unitarios — solo para tests de
  integracion que necesiten el contexto completo.

---

## 4. CI/CD con GitHub Actions

### 4.1 Pipeline actual (`.github/workflows/ci.yml`)

```
Push o PR a main/develop/entrega-1-backend
│
├── Job 1: Build & Test (ubuntu + windows, en paralelo)
│   ├── Compilar con Maven
│   ├── Ejecutar los 30 tests
│   ├── Generar reporte de tests (dorny/test-reporter)
│   ├── Subir reporte JaCoCo como artefacto
│   └── Empaquetar JAR
│
├── Job 2: Architecture Guard (despues de Job 1)
│   ├── Ejecutar solo los 12 tests de ArchUnit
│   └── Reporte separado de reglas de arquitectura
│
└── Job 3: Docker Build (despues de Jobs 1 y 2)
    ├── Construir imagen Docker
    └── Levantar contenedor y verificar que arranca
```

### 4.2 Que bloquea un merge

Si se activa branch protection en `main`/`develop`, un PR no se puede
mergear si:
- Falla cualquier test unitario o de integracion.
- Se viola alguna regla de arquitectura (ArchUnit).
- La imagen Docker no se construye correctamente.

**Se recomienda activar branch protection** en `main` requiriendo que los
tres jobs pasen antes de permitir el merge.

---

## 5. Despliegue con Docker

### 5.1 Imagen Docker

El `Dockerfile` usa build multi-stage:

```
Stage 1 — build (maven:3.9-eclipse-temurin-21)
  1. Copia pom.xml y descarga dependencias (capa cacheada)
  2. Copia codigo fuente
  3. Ejecuta mvn package (compila + corre tests)
  4. Si algun test falla, el build se detiene aqui

Stage 2 — runtime (eclipse-temurin:21-jre)
  1. Copia solo el JAR del stage anterior
  2. Corre como usuario no-root (appuser, uid 1001)
  3. Expone puerto 8080
```

**Construir la imagen localmente:**

```bash
docker build -t facimus/procesos-back .
```

### 5.2 Perfiles de Spring

| Perfil | BD | Uso |
|---|---|---|
| (default) | H2 en archivo `./data/` | Desarrollo local sin Docker |
| `prod` | PostgreSQL externo | Contenedores Docker / VMs |

El perfil se activa con la variable de entorno `SPRING_PROFILES_ACTIVE=prod`.

### 5.3 Variables de entorno del perfil prod

| Variable | Default | Descripcion |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | (ninguno) | Debe ser `prod` para activar PostgreSQL |
| `DB_HOST` | `localhost` | IP o hostname de la VM donde corre PostgreSQL |
| `DB_PORT` | `5432` | Puerto de PostgreSQL |
| `DB_NAME` | `procesos` | Nombre de la base de datos |
| `DB_USER` | `procesos` | Usuario de la base de datos |
| `DB_PASSWORD` | (vacio) | Contrasena de la base de datos |

### 5.4 Topologia de despliegue planificada

Tres servicios en contenedores Docker, cada uno en su propia VM de la
universidad. Todas las VMs comparten la misma red.

```
VM 1 — Base de datos          VM 2 — Backend              VM 3 — Frontend
┌──────────────────┐          ┌──────────────────┐        ┌──────────────────┐
│  PostgreSQL 17   │          │  procesos-back   │        │  Angular (nginx) │
│  puerto 5432     │◄─────────│  puerto 8080     │◄───────│  puerto 80       │
│                  │   JDBC   │                  │  HTTP  │                  │
└──────────────────┘          └──────────────────┘        └──────────────────┘
```

### 5.5 Comandos de despliegue por VM

**VM 1 — Base de datos:**

```bash
docker run -d \
  --name procesos-db \
  --restart unless-stopped \
  -p 5432:5432 \
  -e POSTGRES_DB=procesos \
  -e POSTGRES_USER=procesos \
  -e POSTGRES_PASSWORD=<password-segura> \
  -v pgdata:/var/lib/postgresql/data \
  postgres:17
```

El volumen `pgdata` persiste los datos entre reinicios del contenedor.

**VM 2 — Backend:**

```bash
docker run -d \
  --name procesos-back \
  --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=<ip-vm-1> \
  -e DB_PORT=5432 \
  -e DB_NAME=procesos \
  -e DB_USER=procesos \
  -e DB_PASSWORD=<password-segura> \
  facimus/procesos-back
```

**VM 3 — Frontend (Entrega 2):**

```bash
docker run -d \
  --name procesos-front \
  --restart unless-stopped \
  -p 80:80 \
  facimus/procesos-front
```

El frontend Angular debera configurar el `environment.ts` con la URL del
backend (`http://<ip-vm-2>:8080`).

### 5.6 Verificacion del despliegue

Desde cualquier VM en la red:

```bash
# Verificar que la BD responde
pg_isready -h <ip-vm-1> -p 5432

# Verificar que el backend responde
curl http://<ip-vm-2>:8080/login
```

---

## 6. Guia para contribuir

### 6.1 Flujo de trabajo Git

1. Crear rama desde `develop` (o desde `main` si no existe `develop`).
2. Nombrar la rama: `feature/<descripcion>`, `fix/<descripcion>`,
   `hotfix/<descripcion>`.
3. Hacer commits atomicos (un cambio logico por commit).
4. Abrir PR contra `develop` (o `main`).
5. Esperar que los 3 jobs del CI pasen en verde.
6. Merge con `--no-ff` para preservar el historial de la rama.

### 6.2 Agregar una entidad nueva

1. Crear la clase en el paquete `model/` del modulo correspondiente.
2. Si pertenece a una empresa, extender `EntidadEmpresa`. Si no lo haces,
   el test `MultitenenciaTest` fallara en CI.
3. Si tiene campos enum, mapearlos con `@Enumerated(EnumType.STRING)`. Si
   usas `ORDINAL`, el test `HerenciaJpaTest` fallara.
4. Crear el repositorio extendiendo `RepositorioTenant<T>`. Si no lo haces,
   el test `RepositorioTenantTest` fallara.
5. Crear el servicio en `service/`. Si lo pones en otro paquete, el test
   `EmpaquetadoTest` fallara.
6. Si necesita controlador, crearlo en `controller/`. Que solo dependa de
   servicios, no de repositorios directamente.
7. Agregar tests unitarios para el servicio nuevo.

### 6.3 Agregar una regla de arquitectura nueva

Si el equipo toma una decision de arquitectura nueva que debe cumplirse en
todo el codigo:

1. Documentarla en esta guia (seccion 2).
2. Crear un test de ArchUnit en `src/test/.../arquitectura/` que la valide.
3. A partir de ese commit, cualquier PR que viole la regla sera rechazado
   por el CI automaticamente.

### 6.4 Cosas que no se deben hacer

- **No crear queries sin filtro `empresaId`** en ningun repositorio (excepto
  `EmpresaRepository`). Un tenant veria datos de otro.
- **No usar `@Enumerated(EnumType.ORDINAL)`**. Reordenar el enum corrompe
  datos existentes.
- **No acceder a repositorios desde controllers**. Toda la logica pasa por
  la capa de servicio.
- **No hacer DELETE fisico** sobre `Proceso` ni `RolProceso`. Usar
  `setActivo(false)`.
- **No hacer `git push --force`** a `main` o `develop`.
- **No saltarse los hooks** con `--no-verify`.

---

## 7. Proximos pasos

### Entrega 2 — REST + Angular (fecha: 21/10/2026)

- [ ] API REST para el bloque modelado (pools, lanes, nodos, arcos, mensajes,
      correlaciones). Los servicios ya existen, falta exponer los endpoints.
- [ ] Controladores REST compartiendo la misma capa de servicio que los
      Thymeleaf (no duplicar logica).
- [ ] Frontend Angular como SPA en su propio repositorio.
- [ ] Dockerfile para el frontend (nginx sirviendo el build de Angular).
- [ ] Publicar las imagenes Docker en un registry (GitHub Packages o Docker
      Hub) desde el CI.

### Entrega final — Seguridad y despliegue (fecha: 25/11/2026)

- [ ] Migrar de `HttpSession` manual a Spring Security con
      `@PreAuthorize`.
- [ ] Tests de integracion con `@SpringBootTest` + H2 para los endpoints
      REST.
- [ ] Tests E2E del flujo completo.
- [ ] CD automatico: el CI construye la imagen, la publica en el registry,
      y las VMs hacen pull de la nueva version.
- [ ] Health check endpoint (`/actuator/health`) para monitoreo.
