import { Routes } from '@angular/router';

import { authGuard } from './guards/auth.guard';
import { CuentaComponent } from './pages/cuenta/cuenta.component';
import { InicioComponent } from './pages/inicio/inicio.component';
import { LoginComponent } from './pages/login/login.component';
import { NoEncontradaComponent } from './pages/no-encontrada/no-encontrada.component';
import { RegistroComponent } from './pages/registro/registro.component';

// Angular evalua las rutas de arriba hacia abajo: las especificas van primero y el comodin al final
export const routes: Routes = [
  { path: '', component: InicioComponent, title: 'BPMN Process Manager' },
  { path: 'login', component: LoginComponent, title: 'Sign in · BPMN Process Manager' },
  { path: 'registro', component: RegistroComponent, title: 'Create a store · BPMN Process Manager' },
  { path: 'cuenta', component: CuentaComponent, canActivate: [authGuard], title: 'My account · BPMN Process Manager' },
  { path: '**', component: NoEncontradaComponent, title: 'Page not found · BPMN Process Manager' },
];
