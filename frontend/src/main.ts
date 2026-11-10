/// <reference types="@angular/localize" />

// Del JavaScript de Bootstrap, solo los tres plugins que usan las pantallas: los modales, los menus desplegables (que
// traen Popper) y el colapso de la barra en pantallas chicas. Cada uno trae su API de atributos data-bs-*, asi que
// las plantillas no cambian; el paquete entero pesaba 80 kB en la carga inicial.
import 'bootstrap/js/src/collapse.js';
import 'bootstrap/js/src/dropdown.js';
import 'bootstrap/js/src/modal.js';

import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

bootstrapApplication(AppComponent, appConfig).catch((err) => console.error(err));
