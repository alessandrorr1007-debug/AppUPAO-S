# Distribución de UPAO S 1.7.1

La compilación `release` desactiva la depuración. Se conserva el identificador
`com.example.upaos`. No se cambia el formato del almacén de credenciales ni de
las cachés existentes.

## Firma

Para actualizar instalaciones anteriores sin borrar sus datos se debe usar la
misma clave con que se firmaron esas instalaciones. No generar otra clave para
sustituirla sin planear la migración.

La firma de producción se configura mediante variables de entorno:

- `UPAOS_KEYSTORE_PATH`: ruta al archivo de claves fuera del repositorio.
- `UPAOS_KEYSTORE_PASSWORD`: contraseña del almacén.
- `UPAOS_KEY_ALIAS`: alias existente.
- `UPAOS_KEY_PASSWORD`: contraseña de la clave.

Sin estas variables, `assembleRelease` genera un APK **sin firmar**, útil para
verificar la compilación, pero no instalable ni distribuible. No publicar ese
archivo ni un APK `debug`.

El flujo de GitHub usa los secretos `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` y `ANDROID_KEY_PASSWORD`.
Si no existe `ANDROID_KEYSTORE_BASE64`, conserva la clave anterior almacenada en
`ANDROID_DEBUG_KEYSTORE_BASE64` para mantener compatibilidad, pero construye un
APK release sin depuración. No eliminar ni reemplazar esa clave sin comprobar
primero las firmas. La contraseña conocida de un almacén debug no permite
firmar sin tener también el archivo de clave privada: ese archivo sigue siendo
un secreto.

Las publicaciones solo se producen con etiquetas `v*` o un `release_tag`
explícito en GitHub. Los cambios locales no publican nada.

## Protecciones

- Contraseñas SSO: solo HTTPS y nombres de servidor exactos; comprobación
  adicional en cada petición de red, incluidas redirecciones con formulario.
- Token de Canvas: solo el origen HTTPS `upao.instructure.com:443`, con
  comprobación antes de conectar y en cada salto de red.
- Sin registros HTTP de Canvas que expongan enlaces privados.
- Datos de la aplicación excluidos de respaldos y transferencias.
- Sin receptor público para solicitar sincronizaciones desde otras apps.
- FileProvider limitado a tarjetas compartidas, calendarios y APK descargados.
- Actualizaciones: origen GitHub del proyecto, descarga terminada correctamente,
  paquete propio, firma igual y código de versión superior. La rotación de clave
  requiere adaptar esta comprobación explícitamente.
- PDF: visor local en caché privada, sin Google Viewer; hasta 32 MiB por
  documento, con eliminación del temporal al cerrar. Para archivos mayores sigue
  disponible la descarga al dispositivo.

El archivo personal `canvas_todo_real.json` se ha movido a
`C:/Users/aless/UPAOS-private-backup` y se ignora para nuevas incorporaciones.
Esto no borra copias anteriores del historial Git ni modifica GitHub.

## Comprobación antes de entregar un APK

Ejecutar `testDebugUnitTest`, `assembleRelease` y revisar
el manifiesto generado: `debuggable=false`, versión 17 y respaldo desactivado.
Verificar la firma del APK final y compararla con la versión instalada.

En un Android de prueba: actualizar sin desinstalar, iniciar sesión, cambiar de
cuenta, revisar notas/asistencia/horario, abrir un PDF, exportar un calendario,
compartir una tarjeta y comprobar recordatorios tras reiniciar. Las pruebas
unitarias y la compilación no sustituyen esta comprobación con cuenta real.
