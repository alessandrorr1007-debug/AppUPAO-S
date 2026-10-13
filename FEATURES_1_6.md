# UPAO S 1.6.0

- La nueva pestaña **Hoy** reúne la próxima clase (incluye aula y clase en curso), novedades de notas y entregas próximas de Canvas.
- **Hoy → Agenda de entregas** muestra pendientes, entregadas o todas. Prioriza próximas fechas, conserva tareas sin fecha y consulta todas las páginas de cursos y entregas. El estado de entrega corresponde al estudiante actual, no al indicador general del curso.
- Recordatorios por cuenta: desactivados, una hora o un día antes. WorkManager revisa aproximadamente cada 15 minutos; Android puede retrasarlo. Los avisos requieren permiso, evitan duplicados y muestran la fecha de los datos utilizados. Sin una actualización de las últimas 24 horas no se emiten avisos basados en datos antiguos.
- Horario, asistencia, notas, Canvas, Hoy y Agenda muestran el estado de datos guardados y la última actualización disponible. Las cachés anteriores sin fecha muestran «sin registro» hasta una consulta exitosa. Un fallo conserva la última respuesta completa.
- **Ajustes → Privacidad y datos** permite borrar caché, quitar una cuenta guardada del dispositivo o eliminar los datos propios del backend y revocar las sesiones. La eliminación del backend exige sesión válida de esa cuenta y confirmación. No elimina la cuenta universitaria ni la de Canvas. Si el servidor falla, conserva los datos locales.
- Se eliminó el uso de cachés de otras cuentas como respaldo en notas y horario.

## Validación

Compilar y ejecutar pruebas JVM con `gradlew.bat :app:assembleDebug :app:testDebugUnitTest`. No ejecutar `connectedDebugAndroidTest` en un teléfono personal: el ejecutor puede desinstalar la aplicación.

El backend requiere `DELETE /cuenta/datos?usuario=...` y el token Bearer correspondiente. Una actualización posterior de la app puede instalarse con `adb install -r`, conservando datos.

Prueba funcional con una cuenta real: conectar Canvas, revisar una entrega pendiente y otra enviada, cortar la conexión y actualizar, habilitar recordatorios y verificar permisos. Probar eliminación destructiva únicamente con una cuenta de prueba que se quiera borrar.
