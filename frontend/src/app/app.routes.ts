import { Routes } from '@angular/router';

import { authGuard } from './guards/auth.guard';
import { InicioComponent } from './pages/inicio/inicio.component';

/*
 * Angular evalua las rutas de arriba hacia abajo: las especificas van primero y el comodin al final.
 *
 * Cada pantalla llega cuando alguien entra en ella, no al abrir la aplicacion: asi el paquete inicial lleva el
 * armazon y poco mas, en vez de las veintiuna pantallas juntas -incluido el editor de diagramas, que es la mas
 * grande y la que menos gente abre-. La portada sigue viajando dentro, que es lo primero que ve cualquiera. El login
 * tambien viajaba, hasta que F14 le sumo el segundo factor, las passkeys y los proveedores: ahora llega aparte, y la
 * carga inicial sigue bajo su tope.
 */
export const routes: Routes = [
  { path: '', component: InicioComponent, title: 'BPMN Process Manager' },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then((modulo) => modulo.LoginComponent),
    title: $localize`Sign in · BPMN Process Manager`,
  },
  {
    path: 'registro',
    loadComponent: () => import('./pages/registro/registro.component').then((modulo) => modulo.RegistroComponent),
    title: $localize`Create an organization · BPMN Process Manager`,
  },
  // F14: las pantallas de los enlaces del correo y de la vuelta de un proveedor, sin sesion
  {
    path: 'verificar-correo',
    loadComponent: () =>
      import('./pages/verificar-correo/verificar-correo.component').then((modulo) => modulo.VerificarCorreoComponent),
    title: $localize`Verify your e-mail · BPMN Process Manager`,
  },
  {
    path: 'recuperar-clave',
    loadComponent: () =>
      import('./pages/recuperar-clave/recuperar-clave.component').then((modulo) => modulo.RecuperarClaveComponent),
    title: $localize`Forgot your password? · BPMN Process Manager`,
  },
  {
    path: 'restablecer-clave',
    loadComponent: () =>
      import('./pages/restablecer-clave/restablecer-clave.component').then(
        (modulo) => modulo.RestablecerClaveComponent,
      ),
    title: $localize`Choose a new password · BPMN Process Manager`,
  },
  {
    path: 'aceptar-invitacion',
    loadComponent: () =>
      import('./pages/aceptar-invitacion/aceptar-invitacion.component').then(
        (modulo) => modulo.AceptarInvitacionComponent,
      ),
    title: $localize`Join your organization · BPMN Process Manager`,
  },
  {
    path: 'vincular',
    loadComponent: () => import('./pages/vincular/vincular.component').then((modulo) => modulo.VincularComponent),
    title: $localize`Link the provider · BPMN Process Manager`,
  },
  {
    path: 'cuenta/seguridad',
    loadComponent: () => import('./pages/seguridad/seguridad.component').then((modulo) => modulo.SeguridadComponent),
    canActivate: [authGuard],
    title: $localize`Security · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`My account`, ruta: '/cuenta' }, { etiqueta: $localize`Security` }] },
  },
  {
    path: 'bitacora',
    loadComponent: () => import('./pages/bitacora/bitacora.component').then((modulo) => modulo.BitacoraComponent),
    canActivate: [authGuard],
    title: $localize`Audit log · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`Administration` }, { etiqueta: $localize`Audit log` }] },
  },
  // F21: como llegan por correo los avisos de la campana
  {
    path: 'cuenta/notificaciones',
    loadComponent: () =>
      import('./pages/preferencias-notificaciones/preferencias-notificaciones.component').then(
        (modulo) => modulo.PreferenciasNotificacionesComponent,
      ),
    canActivate: [authGuard],
    title: $localize`Notifications · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`My account`, ruta: '/cuenta' }, { etiqueta: $localize`Notifications` }] },
  },
  {
    path: 'cuenta',
    loadComponent: () => import('./pages/cuenta/cuenta.component').then((modulo) => modulo.CuentaComponent),
    canActivate: [authGuard],
    title: $localize`My account · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`My account` }] },
  },
  {
    path: 'procesos',
    loadComponent: () => import('./pages/procesos/procesos.component').then((modulo) => modulo.ProcesosComponent),
    canActivate: [authGuard],
    title: $localize`Processes · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`Processes` }] },
  },
  // F22: el portal de lectura de lo publicado, sin nada que cambiar
  {
    path: 'portal',
    loadComponent: () => import('./pages/portal/portal.component').then((modulo) => modulo.PortalComponent),
    canActivate: [authGuard],
    title: $localize`Process portal · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`Process portal` }] },
  },
  {
    path: 'portal/procesos/:id',
    loadComponent: () =>
      import('./pages/portal-proceso/portal-proceso.component').then((modulo) => modulo.PortalProcesoComponent),
    canActivate: [authGuard],
    title: $localize`Published process · BPMN Process Manager`,
    data: {
      migas: [{ etiqueta: $localize`Process portal`, ruta: '/portal' }, { etiqueta: $localize`Published process` }],
    },
  },
  {
    path: 'casos',
    loadComponent: () => import('./pages/casos/casos.component').then((modulo) => modulo.CasosComponent),
    canActivate: [authGuard],
    title: $localize`Cases · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`Work` }, { etiqueta: $localize`Cases` }] },
  },
  {
    path: 'casos/:id',
    loadComponent: () =>
      import('./pages/caso-detalle/caso-detalle.component').then((modulo) => modulo.CasoDetalleComponent),
    canActivate: [authGuard],
    title: $localize`Case · BPMN Process Manager`,
    data: {
      migas: [
        { etiqueta: $localize`Work` },
        { etiqueta: $localize`Cases`, ruta: '/casos' },
        { etiqueta: $localize`Case` },
      ],
    },
  },
  // F21: la bandeja por aprobar y cada solicitud de revision, con su diferencia (PR 64)
  {
    path: 'solicitudes',
    loadComponent: () =>
      import('./pages/solicitudes/solicitudes.component').then((modulo) => modulo.SolicitudesComponent),
    canActivate: [authGuard],
    title: $localize`To approve · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`Work` }, { etiqueta: $localize`To approve` }] },
  },
  {
    path: 'solicitudes/:id',
    loadComponent: () => import('./pages/solicitud/solicitud.component').then((modulo) => modulo.SolicitudComponent),
    canActivate: [authGuard],
    title: $localize`Review request · BPMN Process Manager`,
    data: {
      migas: [
        { etiqueta: $localize`Work` },
        { etiqueta: $localize`To approve`, ruta: '/solicitudes' },
        { etiqueta: $localize`Review request` },
      ],
    },
  },
  {
    path: 'simulacion',
    loadComponent: () => import('./pages/simulacion/simulacion.component').then((modulo) => modulo.SimulacionComponent),
    canActivate: [authGuard],
    title: $localize`Simulation · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`Dashboard` }, { etiqueta: $localize`Simulation` }] },
  },
  {
    path: 'tablero',
    loadComponent: () => import('./pages/tablero/tablero.component').then((modulo) => modulo.TableroComponent),
    canActivate: [authGuard],
    title: $localize`Dashboard · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`Dashboard` }] },
  },
  {
    path: 'tareas',
    loadComponent: () => import('./pages/tareas/tareas.component').then((modulo) => modulo.TareasComponent),
    canActivate: [authGuard],
    title: $localize`Task tray · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`Work` }, { etiqueta: $localize`Task tray` }] },
  },
  {
    path: 'usuarios',
    loadComponent: () => import('./pages/usuarios/usuarios.component').then((modulo) => modulo.UsuariosComponent),
    canActivate: [authGuard],
    title: $localize`Users · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`Administration` }, { etiqueta: $localize`Users` }] },
  },
  {
    path: 'roles',
    loadComponent: () => import('./pages/roles/roles.component').then((modulo) => modulo.RolesComponent),
    canActivate: [authGuard],
    title: $localize`Process roles · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`Administration` }, { etiqueta: $localize`Process roles` }] },
  },
  {
    path: 'configuracion',
    loadComponent: () =>
      import('./pages/configuracion/configuracion.component').then((modulo) => modulo.ConfiguracionComponent),
    canActivate: [authGuard],
    title: $localize`Organization settings · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`Administration` }, { etiqueta: $localize`Organization settings` }] },
  },
  {
    path: 'politica-de-publicacion',
    loadComponent: () =>
      import('./pages/politica-publicacion/politica-publicacion.component').then(
        (modulo) => modulo.PoliticaPublicacionComponent,
      ),
    canActivate: [authGuard],
    title: $localize`Publishing policy · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`Administration` }, { etiqueta: $localize`Publishing policy` }] },
  },
  {
    path: 'historial',
    loadComponent: () => import('./pages/historial/historial.component').then((modulo) => modulo.HistorialComponent),
    canActivate: [authGuard],
    title: $localize`Organization history · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`Administration` }, { etiqueta: $localize`Organization history` }] },
  },
  // nuevo va antes de :id, o Angular tomaria "nuevo" como el id de un proceso
  {
    path: 'procesos/nuevo',
    loadComponent: () =>
      import('./pages/proceso-form/proceso-form.component').then((modulo) => modulo.ProcesoFormComponent),
    canActivate: [authGuard],
    title: $localize`New process · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`Processes`, ruta: '/procesos' }, { etiqueta: $localize`New process` }] },
  },
  {
    path: 'procesos/:id/editar',
    loadComponent: () =>
      import('./pages/proceso-form/proceso-form.component').then((modulo) => modulo.ProcesoFormComponent),
    canActivate: [authGuard],
    title: $localize`Edit process · BPMN Process Manager`,
    data: {
      migas: [
        { etiqueta: $localize`Processes`, ruta: '/procesos' },
        { etiqueta: $localize`Process`, ruta: '/procesos/:id' },
        { etiqueta: $localize`Edit` },
      ],
    },
  },
  {
    path: 'procesos/:id/compartir',
    loadComponent: () => import('./pages/compartir/compartir.component').then((modulo) => modulo.CompartirComponent),
    canActivate: [authGuard],
    title: $localize`Share the process · BPMN Process Manager`,
    data: {
      migas: [
        { etiqueta: $localize`Processes`, ruta: '/procesos' },
        { etiqueta: $localize`Process`, ruta: '/procesos/:id' },
        { etiqueta: $localize`Share` },
      ],
    },
  },
  // F19: quien puede hacer que con un proceso o una carpeta (PR 60)
  {
    path: 'procesos/:id/permisos',
    loadComponent: () => import('./pages/permisos/permisos.component').then((modulo) => modulo.PermisosComponent),
    canActivate: [authGuard],
    title: $localize`Permissions · BPMN Process Manager`,
    data: {
      recurso: 'procesos',
      migas: [
        { etiqueta: $localize`Processes`, ruta: '/procesos' },
        { etiqueta: $localize`Process`, ruta: '/procesos/:id' },
        { etiqueta: $localize`Permissions` },
      ],
    },
  },
  {
    path: 'carpetas/:id/permisos',
    loadComponent: () => import('./pages/permisos/permisos.component').then((modulo) => modulo.PermisosComponent),
    canActivate: [authGuard],
    title: $localize`Folder permissions · BPMN Process Manager`,
    data: {
      recurso: 'carpetas',
      migas: [
        { etiqueta: $localize`Processes`, ruta: '/procesos' },
        { etiqueta: $localize`Folder` },
        { etiqueta: $localize`Permissions` },
      ],
    },
  },
  {
    path: 'procesos/:id/modelar',
    loadComponent: () =>
      import('./pages/proceso-modelador/proceso-modelador.component').then(
        (modulo) => modulo.ProcesoModeladorComponent,
      ),
    canActivate: [authGuard],
    title: $localize`Model the process · BPMN Process Manager`,
    data: {
      migas: [
        { etiqueta: $localize`Processes`, ruta: '/procesos' },
        { etiqueta: $localize`Process`, ruta: '/procesos/:id' },
        { etiqueta: $localize`Modeler` },
      ],
    },
  },
  {
    path: 'procesos/:id/comparar',
    loadComponent: () =>
      import('./pages/proceso-comparar/proceso-comparar.component').then((modulo) => modulo.ProcesoCompararComponent),
    canActivate: [authGuard],
    title: $localize`Compare versions · BPMN Process Manager`,
    data: {
      migas: [
        { etiqueta: $localize`Processes`, ruta: '/procesos' },
        { etiqueta: $localize`Process`, ruta: '/procesos/:id' },
        { etiqueta: $localize`Compare` },
      ],
    },
  },
  // Los enlaces viejos al editor anterior siguen llevando al lugar donde se modela ahora
  { path: 'procesos/:id/editar-diagrama', redirectTo: 'procesos/:id/modelar' },
  {
    path: 'procesos/:id',
    loadComponent: () =>
      import('./pages/proceso-detalle/proceso-detalle.component').then((modulo) => modulo.ProcesoDetalleComponent),
    canActivate: [authGuard],
    title: $localize`Process · BPMN Process Manager`,
    data: { migas: [{ etiqueta: $localize`Processes`, ruta: '/procesos' }, { etiqueta: $localize`Process` }] },
  },
  {
    path: '**',
    loadComponent: () =>
      import('./pages/no-encontrada/no-encontrada.component').then((modulo) => modulo.NoEncontradaComponent),
    title: $localize`Page not found · BPMN Process Manager`,
  },
];
