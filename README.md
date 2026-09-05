# App UPAO S

Aplicación Android moderna (Kotlin + Jetpack Compose con Material 3) diseñada para consultar **notas, horario y asistencias** de la Universidad Privada Antenor Orrego (Campus UPAO), comunicándose con el backend optimizado de UPAO conectado al sistema Banner.

---

## 📲 Descargar la App (APK)

Puedes descargar e instalar la versión más reciente del APK directamente en tu celular Android:

👉 **[Descargar última versión de UPAO S (Releases)](https://github.com/alessandrorr1007-debug/AppUPAO-S/releases/latest)**

1. Entra al enlace anterior.
2. En la sección **Assets**, pulsa sobre **`UPAO-S.apk`**.
3. Abre el archivo en tu dispositivo Android para instalar o actualizar la app.

---

## ✨ Características Principales

- **📊 Mis Notas**:
  - Consulta de notas actuales por ciclo y carrera.
  - Promedio general progresivo ponderado (EP1 / Parcial / EP2 / Final).
  - Desglose interactivo por curso y componente de evaluación.

- **📅 Horario Semanal**:
  - Visualización completa por días de la semana con formato de 12 horas.
  - Bloques de clases fusionados con detalle de curso, aula, docente y sección.
  - Navegación fluida entre pestañas.

- **📋 Asistencia y Control de Inasistencias**:
  - Detección y desglose de cursos con **Teoría** y **Laboratorio**.
  - Cálculo preciso de **veces asistidas** y **veces que faltaste** por componente y consolidado.
  - Porcentaje de asistencia en tiempo real y alerta de límite de inasistencias.

- **🧮 Calculadora de Notas**:
  - Simula las notas que necesitas en tus exámenes finales para aprobar según la fórmula ponderada de tu carrera.

- **🔔 Notificaciones y Monitoreo**:
  - Notificaciones en segundo plano para cambios en notas e inasistencias.
  - Servicio en background configurable desde Ajustes.

- **⚙️ Ajustes y Multi-cuenta**:
  - Soporte multi-cuenta con cambio rápido de usuario.
  - Tema claro y tema oscuro adaptativo.
  - Panel de administración y buzón de sugerencias.

---

## 🛠️ Stack Tecnológico

- **Lenguaje & UI**: Kotlin, Jetpack Compose, Material 3, Navigation Compose.
- **Conexión a Red**: Retrofit 2 + OkHttp + Gson.
- **Almacenamiento Local**: Jetpack DataStore y EncryptedSharedPreferences (credenciales seguras).
- **Notificaciones**: Firebase Cloud Messaging (FCM) + Android WorkManager.
- **Versión de SDK**: `minSdk 24` (Android 7.0+) | `targetSdk / compileSdk 37`.
- **CI/CD**: GitHub Actions para compilación y despliegue automático de APKs en GitHub Releases.

---

## 🔒 Privacidad y Seguridad

- **Cero secretos en el repositorio**: Claves de API, credenciales privadas, keystores y archivos locales (`google-services.json`, `local.properties`) están estrictamente excluidos en `.gitignore`.
- **Cifrado local**: Las credenciales se guardan de forma local en el dispositivo utilizando almacenamiento cifrado del sistema Android (`EncryptedSharedPreferences`).

---

## 📂 Estructura del Código

```text
app/src/main/java/com/example/upaos/
├── MainActivity.kt            # Punto de entrada y navegación (NavHost)
├── data/
│   ├── api/                   # RetrofitClient, ApiService, interceptores de sesión
│   ├── local/                 # TokenManager, preferencias y persistencia local
│   └── model/                 # Modelos de datos (notas, horario, asistencia, etc.)
├── service/                   # Notificaciones push (FCM) y sincronización en segundo plano
└── ui/
    ├── home/                  # Contenedor principal (HorizontalPager para Notas, Horario, Asistencia)
    ├── grades/                # Pantalla y detalle de notas
    ├── horario/               # Pantalla de horario semanal
    ├── asistencia/            # Pantalla de asistencias con desglose de teoría y laboratorio
    ├── calculadora/           # Calculadora predictiva de notas
    ├── settings/              # Ajustes de la app y preferencias
    ├── sugerencias/           # Buzón de sugerencias
    ├── admin/                 # Panel de administración
    ├── login/                 # Autenticación y selector multi-cuenta
    ├── components/            # Componentes reutilizables de UI
    └── theme/                 # Paleta de colores UPAO, tipografías y temas
```

---

## 🚀 Compilación Local

Si eres desarrollador y deseas compilar el código fuente por tu cuenta:

1. Clona el repositorio:
   ```bash
   git clone https://github.com/alessandrorr1007-debug/AppUPAO-S.git
   ```
2. Abre el proyecto en **Android Studio**.
3. Asegúrate de contar con el archivo `google-services.json` de tu proyecto Firebase dentro de la carpeta `app/`.
4. Compila el APK:
   ```bash
   ./gradlew assembleDebug
   ```
