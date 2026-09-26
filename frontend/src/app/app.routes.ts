import { Routes } from '@angular/router';

import { authGuard } from './guards/auth.guard';
import { CasoDetalleComponent } from './pages/caso-detalle/caso-detalle.component';
import { CasosComponent } from './pages/casos/casos.component';
import { CompartirComponent } from './pages/compartir/compartir.component';
import { ConfiguracionComponent } from './pages/configuracion/configuracion.component';
import { CuentaComponent } from './pages/cuenta/cuenta.component';
import { HistorialComponent } from './pages/historial/historial.component';
import { InicioComponent } from './pages/inicio/inicio.component';
import { LoginComponent } from './pages/login/login.component';
import { NoEncontradaComponent } from './pages/no-encontrada/no-encontrada.component';
import { ProcesoDetalleComponent } from './pages/proceso-detalle/proceso-detalle.component';
import { ProcesoEditorComponent } from './pages/proceso-editor/proceso-editor.component';
import { ProcesoFormComponent } from './pages/proceso-form/proceso-form.component';
import { ProcesosComponent } from './pages/procesos/procesos.component';
import { RegistroComponent } from './pages/registro/registro.component';
import { RolesComponent } from './pages/roles/roles.component';
import { SimulacionComponent } from './pages/simulacion/simulacion.component';
import { TareasComponent } from './pages/tareas/tareas.component';
import { UsuariosComponent } from './pages/usuarios/usuarios.component';

// Angular evalua las rutas de arriba hacia abajo: las especificas van primero y el comodin al final
export const routes: Routes = [
  { path: '', component: InicioComponent, title: 'BPMN Process Manager' },
  { path: 'login', component: LoginComponent, title: 'Sign in · BPMN Process Manager' },
  { path: 'registro', component: RegistroComponent, title: 'Create a store · BPMN Process Manager' },
  { path: 'cuenta', component: CuentaComponent, canActivate: [authGuard], title: 'My account · BPMN Process Manager' },
  { path: 'procesos', component: ProcesosComponent, canActivate: [authGuard], title: 'Processes · BPMN Process Manager' },
  { path: 'casos', component: CasosComponent, canActivate: [authGuard], title: 'Cases · BPMN Process Manager' },
  {
    path: 'casos/:id',
    component: CasoDetalleComponent,
    canActivate: [authGuard],
    title: 'Case · BPMN Process Manager',
  },
  {
    path: 'simulacion',
    component: SimulacionComponent,
    canActivate: [authGuard],
    title: 'Simulation · BPMN Process Manager',
  },
  { path: 'tareas', component: TareasComponent, canActivate: [authGuard], title: 'Task tray · BPMN Process Manager' },
  { path: 'usuarios', component: UsuariosComponent, canActivate: [authGuard], title: 'Users · BPMN Process Manager' },
  { path: 'roles', component: RolesComponent, canActivate: [authGuard], title: 'Process roles · BPMN Process Manager' },
  {
    path: 'configuracion',
    component: ConfiguracionComponent,
    canActivate: [authGuard],
    title: 'Store settings · BPMN Process Manager',
  },
  {
    path: 'historial',
    component: HistorialComponent,
    canActivate: [authGuard],
    title: 'Store history · BPMN Process Manager',
  },
  // nuevo va antes de :id, o Angular tomaria "nuevo" como el id de un proceso
  {
    path: 'procesos/nuevo',
    component: ProcesoFormComponent,
    canActivate: [authGuard],
    title: 'New process · BPMN Process Manager',
  },
  {
    path: 'procesos/:id/editar',
    component: ProcesoFormComponent,
    canActivate: [authGuard],
    title: 'Edit process · BPMN Process Manager',
  },
  {
    path: 'procesos/:id/compartir',
    component: CompartirComponent,
    canActivate: [authGuard],
    title: 'Share the process · BPMN Process Manager',
  },
  {
    path: 'procesos/:id/editar-diagrama',
    component: ProcesoEditorComponent,
    canActivate: [authGuard],
    title: 'Model the diagram · BPMN Process Manager',
  },
  {
    path: 'procesos/:id',
    component: ProcesoDetalleComponent,
    canActivate: [authGuard],
    title: 'Process · BPMN Process Manager',
  },
  { path: '**', component: NoEncontradaComponent, title: 'Page not found · BPMN Process Manager' },
];
