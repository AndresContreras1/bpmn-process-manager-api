import { Routes } from '@angular/router';

import { authGuard } from './guards/auth.guard';
import { InicioComponent } from './pages/inicio/inicio.component';
import { LoginComponent } from './pages/login/login.component';

/*
 * Angular evalua las rutas de arriba hacia abajo: las especificas van primero y el comodin al final.
 *
 * Cada pantalla llega cuando alguien entra en ella, no al abrir la aplicacion: asi el paquete inicial lleva el
 * armazon y poco mas, en vez de las veintiuna pantallas juntas -incluido el editor de diagramas, que es la mas
 * grande y la que menos gente abre-. Las dos que siguen viajando dentro son la portada y el login, que es lo
 * primero que ve cualquiera: cargarlas aparte costaria una peticion mas justo en el primer segundo.
 */
export const routes: Routes = [
  { path: '', component: InicioComponent, title: 'BPMN Process Manager' },
  { path: 'login', component: LoginComponent, title: $localize`Sign in · BPMN Process Manager` },
  {
    path: 'registro',
    loadComponent: () => import('./pages/registro/registro.component').then((modulo) => modulo.RegistroComponent),
    title: $localize`Create an organization · BPMN Process Manager`,
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
  {
    path: 'procesos/:id/editar-diagrama',
    loadComponent: () =>
      import('./pages/proceso-editor/proceso-editor.component').then((modulo) => modulo.ProcesoEditorComponent),
    canActivate: [authGuard],
    title: $localize`Model the diagram · BPMN Process Manager`,
    data: {
      migas: [
        { etiqueta: $localize`Processes`, ruta: '/procesos' },
        { etiqueta: $localize`Process`, ruta: '/procesos/:id' },
        { etiqueta: $localize`Diagram` },
      ],
    },
  },
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
