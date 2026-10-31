# Canvas de UPAO S: política de acceso vigente

La política GET-only anterior quedó sustituida por `CanvasRequestPolicy`. Se mantienen HTTPS, puerto 443 y origen `upao.instructure.com` para toda petición con Bearer. La API permite GET bajo `/api/v1/` y únicamente cuatro rutas POST: nueva intervención, respuesta a intervención, nueva conversación y respuesta a conversación. No se habilitan entregas, uploads, administración ni ejecución de cuestionarios.

Las escrituras solo se disparan tras una acción y confirmación del estudiante. No usan backoff, reintentos de transporte ni redirecciones automáticas. Una respuesta de red ambigua bloquea el reenvío hasta que el estudiante confirma que revisó el hilo y decide redactar nuevamente. El estado de envío es local al ViewModel: no constituye idempotencia garantizada por Canvas ni sobrevive la terminación del proceso.

Las lecturas siguen la paginación opaca y conservan datos anteriores ante fallos. Se respeta Retry-After hasta 30 segundos; esperas mayores se presentan como servicio ocupado. No se añade ningún worker. Las conversaciones se leen con auto_mark_as_read=false.

Las descargas usan un transporte sin Bearer ni cookies para URLs de archivo obtenidas por API. Se validan cada redirección HTTPS y los dominios de Canvas/CDN admitidos. El límite es 32 MiB. Un CDN institucional diferente se rechaza y requiere validación antes de ampliar la lista. Los archivos se guardan en caché privada por usuario; la limpieza Canvas elimina ese directorio. Exportar una copia usa el selector del sistema; esa copia pasa a ser administrada por el usuario. Office se abre mediante FileProvider y permiso de lectura temporal.

HTML se sanea con Jsoup. Texto y enlaces usan componentes Android; tablas usan un WebView de visualización sin JavaScript, red, almacenamiento, archivos, cookies de autenticación ni bridges. Los enlaces a recursos compatibles se resuelven nativamente. Las imágenes de archivos Canvas pueden consultarse nativamente; se precargan hasta ocho imágenes con tamaño informado de hasta 2 MiB. Fórmulas basadas en JavaScript, iframes, vídeo/LTI y contenido externo necesitan su flujo externo autorizado. No se ejecuta HTML arbitrario de curso con privilegios de Android.

Tareas: consulta nativa, entrega web y actualización al retornar. Classic/New Quizzes: metadatos e intentos disponibles; ejecución externa, sin acceso a preguntas ni inicio automático de intentos. Las notas se identifican como Canvas y no se convierten en notas oficiales de Banner.

No se han usado cuentas reales ni se han realizado publicaciones reales. Las pruebas con MockWebServer verifican el contrato local y no demuestran los permisos de UPAO. Consultar CANVAS_IMPLEMENTACION.md y CANVAS_COBERTURA.csv para resultados y pendientes.
