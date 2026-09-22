import { Component } from '@angular/core';

/** Tarjeta de la portada: algo que se puede hacer con la aplicacion. */
interface Caracteristica {
  icono: string;
  titulo: string;
  descripcion: string;
}

@Component({
  selector: 'app-inicio',
  imports: [],
  templateUrl: './inicio.component.html',
  styleUrl: './inicio.component.scss',
})
export class InicioComponent {
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
