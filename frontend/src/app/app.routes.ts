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
  { path: 'login', component: LoginComponent, title: 'Sign in · BPMN Process Manager' },
  {
    path: 'registro',
    loadComponent: () => import('./pages/registro/registro.component').then((modulo) => modulo.RegistroComponent),
    title: 'Create a store · BPMN Process Manager',
  },
  {
    path: 'cuenta',
    loadComponent: () => import('./pages/cuenta/cuenta.component').then((modulo) => modulo.CuentaComponent),
    canActivate: [authGuard],
    title: 'My account · BPMN Process Manager',
  },
  {
    path: 'procesos',
    loadComponent: () => import('./pages/procesos/procesos.component').then((modulo) => modulo.ProcesosComponent),
    canActivate: [authGuard],
    title: 'Processes · BPMN Process Manager',
  },
  {
    path: 'casos',
    loadComponent: () => import('./pages/casos/casos.component').then((modulo) => modulo.CasosComponent),
    canActivate: [authGuard],
    title: 'Cases · BPMN Process Manager',
  },
  {
    path: 'casos/:id',
    loadComponent: () => import('./pages/caso-detalle/caso-detalle.component').then((modulo) => modulo.CasoDetalleComponent),
    canActivate: [authGuard],
    title: 'Case · BPMN Process Manager',
  },
  {
    path: 'simulacion',
    loadComponent: () => import('./pages/simulacion/simulacion.component').then((modulo) => modulo.SimulacionComponent),
    canActivate: [authGuard],
    title: 'Simulation · BPMN Process Manager',
  },
  {
    path: 'tablero',
    loadComponent: () => import('./pages/tablero/tablero.component').then((modulo) => modulo.TableroComponent),
    canActivate: [authGuard],
    title: 'Dashboard · BPMN Process Manager',
  },
  {
    path: 'tareas',
    loadComponent: () => import('./pages/tareas/tareas.component').then((modulo) => modulo.TareasComponent),
    canActivate: [authGuard],
    title: 'Task tray · BPMN Process Manager',
  },
  {
    path: 'usuarios',
    loadComponent: () => import('./pages/usuarios/usuarios.component').then((modulo) => modulo.UsuariosComponent),
    canActivate: [authGuard],
    title: 'Users · BPMN Process Manager',
  },
  {
    path: 'roles',
    loadComponent: () => import('./pages/roles/roles.component').then((modulo) => modulo.RolesComponent),
    canActivate: [authGuard],
    title: 'Process roles · BPMN Process Manager',
  },
  {
    path: 'configuracion',
    loadComponent: () => import('./pages/configuracion/configuracion.component').then((modulo) => modulo.ConfiguracionComponent),
    canActivate: [authGuard],
    title: 'Store settings · BPMN Process Manager',
  },
  {
    path: 'historial',
    loadComponent: () => import('./pages/historial/historial.component').then((modulo) => modulo.HistorialComponent),
    canActivate: [authGuard],
    title: 'Store history · BPMN Process Manager',
  },
  // nuevo va antes de :id, o Angular tomaria "nuevo" como el id de un proceso
  {
    path: 'procesos/nuevo',
    loadComponent: () => import('./pages/proceso-form/proceso-form.component').then((modulo) => modulo.ProcesoFormComponent),
    canActivate: [authGuard],
    title: 'New process · BPMN Process Manager',
  },
  {
    path: 'procesos/:id/editar',
    loadComponent: () => import('./pages/proceso-form/proceso-form.component').then((modulo) => modulo.ProcesoFormComponent),
    canActivate: [authGuard],
    title: 'Edit process · BPMN Process Manager',
  },
  {
    path: 'procesos/:id/compartir',
    loadComponent: () => import('./pages/compartir/compartir.component').then((modulo) => modulo.CompartirComponent),
    canActivate: [authGuard],
    title: 'Share the process · BPMN Process Manager',
  },
  {
    path: 'procesos/:id/editar-diagrama',
    loadComponent: () => import('./pages/proceso-editor/proceso-editor.component').then((modulo) => modulo.ProcesoEditorComponent),
    canActivate: [authGuard],
    title: 'Model the diagram · BPMN Process Manager',
  },
  {
    path: 'procesos/:id',
    loadComponent: () => import('./pages/proceso-detalle/proceso-detalle.component').then((modulo) => modulo.ProcesoDetalleComponent),
    canActivate: [authGuard],
    title: 'Process · BPMN Process Manager',
  },
  {
    path: '**',
    loadComponent: () => import('./pages/no-encontrada/no-encontrada.component').then(
      (modulo) => modulo.NoEncontradaComponent),
    title: 'Page not found · BPMN Process Manager',
  },
];
