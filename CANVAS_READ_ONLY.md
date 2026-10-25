# Canvas de UPAO S: consultas de estudiante

Todas las peticiones del cliente Canvas son GET y se restringen a HTTPS, puerto 443 y `upao.instructure.com`. El token solo viaja en Authorization y el logging HTTP permanece en NONE. El detalle de conversaciones añade `auto_mark_as_read=false`: un GET de ese recurso puede marcar leído si se omite ese parámetro.

Las listas nuevas usan CanvasPagedApi/collectCanvasPages, incluyendo el sobre `quiz_submissions`. Anuncios y calendario dividen los contextos en lotes de diez. Ventanas: anuncios desde hoy−180 hasta hoy+30; calendario desde hoy−30 hasta hoy+120. Se usa la fecha local del dispositivo. La caché por usuario y los indicadores locales de anuncios viven en CanvasCache; se limpian con su método limpiar.

El HTML recibido por API se presenta con HtmlText (texto nativo, sin JavaScript). Sus enlaces se abren en el navegador; no se envía el Bearer al navegador. Los enlaces de entrega, respuesta, exámenes, LTI y otras páginas completas de Canvas requieren la sesión web que mantenga el navegador y pueden pedir login. Las páginas de curso y los hilos se consultan por API. Nunca se muestran preguntas de exámenes.

Los intentos se distinguen entre −1, null y cero. Las notas de curso provienen de enrollments; no se recalculan promedios. La etiqueta de aprobado por actividad solo aparece con score y puntos positivos, usando la constante 10.5/20. Las fechas dueAt nunca se sustituyen por lockAt. Si falta dueAt solo se utiliza una fecha base explícita; no se adivinan overrides de secciones o grupos sin información aplicable.

New Quizzes no ofrece un identificador universal en estos endpoints. Las actividades LTI se rotulan como posibles New Quizzes y se abren externamente, sin afirmar que todo LTI sea un examen. Los hilos no visibles por requerir una publicación previa muestran el error 403; la app no publica para desbloquearlos.

El diagnóstico de Ajustes solo se ejecuta a petición. Usa la primera página para medir cada endpoint y el primer curso activo, y los detalles solo si hay elementos que permitan identificarlos. Copia nombres de endpoint con placeholders, código HTTP, conteo y tiempo; no incluye parámetros, identificadores, cuerpos, nombres, correos, tokens ni mensajes de excepción. Un guion en HTTP indica que no hubo respuesta HTTP. No verifica transporte de descarga ni continuaciones opacas como endpoints separados.

Las pruebas y `canvas_models_synthetic.json` usan exclusivamente datos sintéticos. No se ha accedido al Canvas real de UPAO: disponibilidad, permisos, respuestas institucionales y presentación en un teléfono son **NO VERIFICADO**. Los avisos opcionales de anuncios/notas no se implementan sin verificar primero las fases obligatorias. No se añade exportador ICS ni otro worker.

Referencias oficiales consultadas:

- [Conversaciones: auto_mark_as_read](https://developerdocs.instructure.com/services/canvas/resources/conversations)
- [Anuncios](https://developerdocs.instructure.com/services/canvas/resources/announcements)
- [Entregas y comentarios](https://developerdocs.instructure.com/services/canvas/resources/submissions)
- [Hilos de discusión](https://developerdocs.instructure.com/services/canvas/resources/discussion_topics)
- [Páginas e identificadores](https://developerdocs.instructure.com/services/canvas/resources/pages)
- [Intentos de quizzes](https://developerdocs.instructure.com/services/canvas/resources/quiz_submissions)
