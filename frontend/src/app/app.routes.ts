import { Routes } from '@angular/router';

import { InicioComponent } from './pages/inicio/inicio.component';
import { NoEncontradaComponent } from './pages/no-encontrada/no-encontrada.component';

// Angular evalua las rutas de arriba hacia abajo: las especificas van primero y el comodin al final
export const routes: Routes = [
  { path: '', component: InicioComponent, title: 'BPMN Process Manager' },
  { path: '**', component: NoEncontradaComponent, title: 'Page not found · BPMN Process Manager' },
];
