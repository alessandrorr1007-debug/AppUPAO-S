# App UPAO S

Aplicación Android (Kotlin + Jetpack Compose) para consultar **notas, horario y asistencia** del Campus UPAO, consumiendo la API backend de UPAO que se conecta al portal oficial Ellucian Banner SSB / SSO WSO2 (sin captcha).

## Características

- **Mis Notas**: notas actuales por periodo, promedio general progresivo (EP1/Parcial/EP2/Final ponderados), desglose por componente, selección de periodo y carrera.
- **Horario**: horario semanal de clases agrupado por curso y día (con bloques fusionados y horas en formato 12h).
- **Asistencia**: inasistencias y porcentaje por curso desde `getRegisteredSections`.
- **Sesión transparente**: re-login automático si la sesión de Banner expira; manejo de error `sesion_expirada` con diálogo para volver a iniciar sesión.
- **Caché local**: las notas se guardan en el dispositivo (DataStore) y se muestran al instante aunque no haya conexión.
- **Notificaciones push (FCM)**: avisos cuando cambia una nota, con revisión automática configurable en segundo plano.
- **Semana académica**: etiqueta del ciclo (ej. "Semana 5 de 16", "Semana 17 · Sustitutorios").
- **Ranking de cursos**: posición relativa anónima por curso (opt-in, mínimo 5 participantes, nunca expone notas).
- **Buzón de sugerencias**: envía ideas con límite diario anti-spam y sigue su estado.
- **Calculadora de notas**, temas claro/oscuro y soporte multi-cuenta.

## Stack técnico

- **Lenguaje/UI**: Kotlin, Jetpack Compose (Material 3), Navigation Compose.
- **Red**: Retrofit 2 + OkHttp + Gson.
- **Persistencia local**: DataStore (caché de notas) y EncryptedSharedPreferences (sesión/credenciales).
- **Push**: Firebase Cloud Messaging.
- **SDK**: minSdk 24, target/compile 37.

## Requisitos

- Android Studio (con el JDK de JetBrains incluido).
- Android SDK 37.
- Una cuenta del Campus UPAO para usar la app.

## Configuración y build

1. **Clona el repositorio**:
   ```bash
   git clone https://github.com/alessandrorr1007-debug/AppUPAO-S.git
   ```

2. **Agrega `app/google-services.json`** (obligatorio para compilar):
   Este archivo contiene las credenciales de Firebase y está **excluido del repositorio** por seguridad.
   Cópialo desde tu proyecto existente o regenéralo en [Firebase Console](https://console.firebase.google.com/)
   (proyecto con el paquete `com.example.upaos`) y colócalo en `app/`.

3. **Apuntar al backend** (opcional):
   La `BASE_URL` está definida en `app/src/main/java/com/example/upaos/data/api/RetrofitClient.kt`
   (por defecto `https://upaos.onrender.com/`).

4. **Compilar el APK**:
   ```bash
   ./gradlew.bat :app:assembleDebug   # Windows
   # o
   ./gradlew :app:assembleDebug        # Linux/macOS
   ```
   El APK queda en `app/build/outputs/apk/debug/`.

## Estructura del proyecto

```
app/src/main/java/com/example/upaos/
├── MainActivity.kt            # Navegación (NavHost) y splash
├── data/
│   ├── api/                   # RetrofitClient, ApiService, manejo de errores
│   ├── local/                 # TokenManager, GradesCache, ThemePreferences
│   └── model/                 # Modelos Gson (notas, horario, asistencia, features)
├── service/                   # Servicio FCM (Notificaciones)
└── ui/
    ├── home/                  # Pantalla principal con pestañas
    ├── grades/                # Notas
    ├── horario/               # Horario
    ├── asistencia/            # Asistencia
    ├── settings/              # Ajustes (auto-check, ranking, perfil)
    ├── sugerencias/           # Buzón de sugerencias
    ├── ranking/               # Ranking anónimo
    ├── calculadora/           # Calculadora de notas
    ├── notificaciones/        # Historial de notificaciones
    ├── login/                 # Login y selección de cuenta
    └── components/            # Utilidades de UI compartidas
```

## Seguridad

- No se suben credenciales ni claves al repositorio: `google-services.json`, `local.properties`,
  keystores y outputs de build están en `.gitignore`.
- La contraseña del campus se guarda cifrada en el dispositivo (EncryptedSharedPreferences) y en el
  backend se almacena con cifrado Fernet, usada solo para el re-login automático.
