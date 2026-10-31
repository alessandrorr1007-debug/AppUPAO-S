# Canvas nativo: auditoría y seguimiento

Base revisada: 1.7.9 (25), 31 de octubre de 2026. Se conservan los cambios locales previos fuera de Canvas.

## Fase 0: cobertura inicial

- Cursos, búsqueda, actualización manual, multiusuario y tokens cifrados: implementados. Filtros de periodo y resumen: parciales.
- Módulos y paginación: implementados; tareas/discusiones enlazadas todavía abren web.
- Páginas, anuncios, calendario, notas y rúbricas: consulta implementada; HTML limitado a TextView.
- Archivos: metadatos y PDF privado implementados; rutas de descarga dispersas, carpetas sin navegación jerárquica.
- Discusiones: lectura parcial; respuestas y continuaciones pendientes.
- Conversaciones: lectura implementada; composición, respuestas y scopes pendientes.
- Navegación de curso: pestañas fijas, pendientes de aplicar visibilidad institucional.
- Tareas: consulta y entrega externa implementadas. Se mantiene entrega exclusivamente web.
- Cuestionarios: consulta de metadatos e intentos implementada; ejecución nativa no verificada. Se conserva ejecución externa para no ofrecer un examen incompleto.
- Agenda, widgets, sincronización, Banner y autenticación: existentes y reutilizados sin sustituirlos.

## Dependencias y restricciones

CanvasApiService → CanvasPagedApi → DefaultCanvasRepository → ViewModels → Compose. CanvasCache y TokenManager aíslan usuario/sesión/token. La antigua restricción GET-only se reemplazará exclusivamente para los POST de discusiones y conversaciones documentados. No se habilitan entregas, administración ni ejecución de exámenes.

Pruebas: fixtures sintéticos y MockWebServer; ninguna publicación ni credencial real. Primera ejecución bloqueada por JAVA_HOME ausente; se localizará el JDK instalado antes de validar.

## Referencias oficiales

- https://developerdocs.instructure.com/services/canvas/resources/discussion_topics
- https://developerdocs.instructure.com/services/canvas/resources/conversations
- https://developerdocs.instructure.com/services/canvas/resources/tabs
- https://developerdocs.instructure.com/services/canvas/resources/quiz_submissions
