import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

import { Usuario } from '../../models/usuario.model';
import { AuthService } from '../../service/auth.service';

/** Tarjeta de la portada: algo que se puede hacer con la aplicacion. */
interface Caracteristica {
  icono: string;
  titulo: string;
  descripcion: string;
}

@Component({
  selector: 'app-inicio',
  imports: [AsyncPipe, RouterLink],
  templateUrl: './inicio.component.html',
  styleUrl: './inicio.component.scss',
})
export class InicioComponent {
  /** La cuenta de demostracion solo existe cuando la API corre en dev; no se ofrece lo que no hay. */
  readonly demo: boolean = environment.demo;
  readonly usuario$: Observable<Usuario | null> = inject(AuthService).usuario$;

  readonly caracteristicas: Caracteristica[] = [
    {
      icono: 'fa-list-check',
      titulo: 'Processes',
      descripcion: 'Create processes, edit them as drafts and publish them. Every change keeps its author.',
    },
    {
      icono: 'fa-diagram-project',
      titulo: 'BPMN diagrams',
      descripcion: 'Pools, lanes, tasks, gateways, sequence flows and messages between participants.',
    },
    {
      icono: 'fa-user-shield',
      titulo: 'Roles and isolation',
      descripcion: 'Administrators, editors and read-only users. A store never sees the data of another.',
    },
  ];
}
