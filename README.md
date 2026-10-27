# 🎓 App UPAO S

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Web Netlify](https://img.shields.io/badge/Web%20Oficial-Netlify-00C7B7?style=for-the-badge&logo=netlify&logoColor=white)](https://upaos.netlify.app/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

**UPAO S** es la aplicación móvil integral y de alto rendimiento diseñada para la comunidad estudiantil de la **Universidad Privada Antenor Orrego (UPAO)**. Desarrollada 100% en Kotlin con **Jetpack Compose y Material Design 3**, conecta de forma directa y optimizada con los servicios institucionales (**Banner UPAO** y **Canvas LMS**), ofreciendo una experiencia moderna, fluida y segura.

---

## 🌐 Descargar la App (Sitio Web Oficial)

La forma principal y oficial para descargar la aplicación, ver novedades y obtener el archivo APK actualizado es a través de nuestra web alojada en **Netlify**:

👉 **[Entrar a la Web Oficial de UPAO S en Netlify (Descargar APK)](https://upaos.netlify.app/)**

### Pasos para instalar:
1. Ingresa a **[upaos.netlify.app](https://upaos.netlify.app/)** desde tu celular Android.
2. Pulsa en el botón **Descargar APK**.
3. Una vez finalizada la descarga, abre el archivo `.apk` e instálalo (o actualízalo si ya cuentas con una versión anterior).

---

## ✨ Características Principales

### 📊 Mis Notas y Rendimiento Académico
- **Cálculo de Promedios**: Promedio ponderado acumulado y de ciclo en tiempo real.
- **Desglose por Componente**: Detalle curso por curso de evaluaciones parciales, exámenes finales, prácticas y componentes de evaluación según la fórmula oficial de la carrera.
- **Semáforo de Rendimiento**: Indicadores visuales claros sobre el estado de aprobación de cada materia.
- **Tarjetas para Compartir**: Generación de tarjetas estéticas (`ShareCards`) para compartir tus logros y notas con amigos o redes.

### 📅 Horario Semanal y Dashboard de Clases
- **Línea de Tiempo del Día (`ClassTimelineRow`)**: Visualiza en la pantalla principal las clases que te tocan hoy, el aula, pabellón, hora de inicio y tiempo restante.
- **Horario Semanal Completo**: Vista por días con formato de 12 horas, bloques unificados, docente asignado, sede y sección.
- **Navegación Táctil Fluida**: Desplazamiento por pestañas o gestos de swipe entre secciones.

### 📋 Asistencia y Control de Inasistencias
- **Separación Inteligente**: Desglose automático de asistencias para cursos con **Teoría** y **Laboratorio**.
- **Contador Preciso**: Registro de clases asistidas vs. inasistencias acumuladas.
- **Semáforo de Límite (DPI)**: Alerta predictiva sobre el límite máximo de inasistencias permitidas antes de la inhabilitación por curso.

### 🎨 Canvas LMS Integrado
- Consulta rápida y directa de cursos activos en Canvas.
- Acceso a anuncios importantes, tareas pendientes, fechas límite y calificaciones registradas en la plataforma virtual sin necesidad de abrir el navegador.

### 🧮 Calculadora Predictiva de Notas
- Simulador dinámico de notas finales: calcula exactamente la nota requerida en el Examen Final o EP2 para alcanzar la nota mínima aprobatoria según el silabo y fórmula de tu curso.

### 📱 Widgets para la Pantalla de Inicio (Jetpack Glance)
- **Widget de Próxima Clase**: Conoce qué clase tienes a continuación, el aula y docente sin abrir la aplicación.
- **Widget de Resumen de Notas**: Tu promedio y avance académico visible directamente en el escritorio de tu Android.

### 🔔 Sincronización en Segundo Plano y Notificaciones
- Monitoreo inteligente en background mediante **Android WorkManager**.
- **Políticas de Sincronización Adaptativas (`SyncPolicy`)**: Notificaciones inmediatas en caso de publicación de nuevas notas o registro de inasistencias.

### 🔒 Privacidad, Biometría y Multi-cuenta
- **Bloqueo Biométrico (`AppLockScreen`)**: Protege el acceso a tu app mediante huella digital, reconocimiento facial o PIN.
- **Multi-cuenta Instantáneo**: Guarda y alterna entre múltiples cuentas de estudiante de forma ágil desde el selector de perfiles.
- **Controles de Privacidad**: Opciones para ocultar datos sensibles en pantalla en lugares públicos.

---

## 🛠️ Stack Tecnológico

| Capa | Tecnologías |
|---|---|
| **Lenguaje** | Kotlin 2.0+ (Coroutines, Flow, Serialization) |
| **Interfaz de Usuario** | Jetpack Compose + Material 3, Navigation Compose, Compose Foundation |
| **Widgets de Sistema** | Android Jetpack Glance (Material 3 AppWidgets) |
| **Red y Conexión** | Retrofit 2, OkHttp 4 (Connection Pooling, Logging Interceptors), Jsoup |
| **Persistencia y Seguridad** | Jetpack DataStore Preferences, EncryptedSharedPreferences (`SecureStore`), BiometricPrompt |
| **Tareas en Background** | Android WorkManager (Background sync periódico y adaptable) |
| **Compatibilidad** | `minSdk 24` (Android 7.0 Nougat+) \| `compileSdk / targetSdk 37` |
| **Hosting Web** | [Netlify](https://upaos.netlify.app/) (Landing page y distribución del APK) |

---

## 🔒 Privacidad y Seguridad

- **Conexión Directa**: La aplicación se conecta directamente a los servidores institucionales de la universidad (Banner y Canvas). No existen servidores intermedios de terceros recolectando información de acceso.
- **Almacenamiento Cifrado Local**: Las credenciales y tokens de sesión se almacenan en el hardware seguro del dispositivo (`Android Keystore` + `EncryptedSharedPreferences`).
- **Seguridad en el Repositorio**: El repositorio no contiene contraseñas, secretos, keystores ni archivos privados de configuración; todos están excluidos en `.gitignore`.

---

## 📂 Arquitectura del Proyecto

```text
app/src/main/java/com/example/upaos/
├── MainActivity.kt                # Punto de entrada y orquestador de navegación
├── data/
│   ├── agenda/                    # Gestión de eventos y calendario
│   ├── api/                       # Clientes HTTP e interfaces de red
│   ├── canvas/                    # Modelos y repositorio de Canvas LMS
│   ├── local/                     # TokenManager, SecureStore y persistencia DataStore
│   ├── model/                     # Modelos de datos (Notas, Horario, Asistencia, Banner)
│   ├── profile/                   # Gestión de perfil del estudiante
│   ├── refresh/                   # Lógica de refresco y caché de datos
│   └── upao/                      # BannerApi, UpaoRepository y parsing institucional
├── domain/academic/               # Casos de uso y reglas de negocio académicas
├── service/                       # WorkManager, SyncPolicy, GradeSyncCheck y notificaciones
├── ui/
│   ├── home/                      # Dashboard principal (TodayCards, Timeline, Pagers)
│   ├── grades/                    # Pantalla de notas y detalle interactivo por curso
│   ├── horario/                   # Pantalla de horario semanal y bloques de clase
│   ├── asistencia/                # Desglose de asistencias (Teoría y Laboratorio)
│   ├── calculadora/               # Calculadora predictiva de notas
│   ├── canvas/                    # Vistas y componentes para Canvas LMS
│   ├── login/                     # Autenticación y selección multi-cuenta
│   ├── notificaciones/            # Historial y gestión de alertas
│   ├── profile/                   # Vista de perfil y datos del alumno
│   ├── settings/                  # Ajustes generales, sincronización y privacidad
│   ├── share/                     # Generador de tarjetas visuales de progreso
│   ├── components/                # Componentes comunes (CampusChrome, ClassTimelineRow, etc.)
│   └── theme/                     # Tema institucional Campus UPAO, colores y tipografías
├── util/                          # Extensiones y utilitarios generales
└── widget/                        # Widgets para launcher con Jetpack Glance
```

---

## 🚀 Compilación y Desarrollo Local

Si deseas clonar el proyecto y compilarlo en tu entorno local:

1. **Clonar el repositorio**:
   ```bash
   git clone https://github.com/alessandrorr1007-debug/UPAOS-APK.git
   ```

2. **Abrir en Android Studio**:
   - Recomendado: Android Studio Ladybug / Koala o superior.
   - JDK 17 o 21 configurado en Gradle.

3. **Ejecutar pruebas unitarias**:
   ```bash
   ./gradlew test
   ```

4. **Compilar APK de depuración**:
   ```bash
   ./gradlew assembleDebug
   ```

El archivo APK resultante se generará en `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📄 Licencia

Este proyecto está distribuido bajo la licencia [MIT](LICENSE). Consulta el archivo para más detalles.
