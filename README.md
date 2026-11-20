# 🤖 MediBot G1 — HMI/HRI para logística hospitalaria con Unitree G1 EDU

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-blue.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Room-2.6.1_(SQLite)-orange.svg?style=flat&logo=sqlite)](https://developer.android.com/training/data-storage/room)
[![Platform](https://img.shields.io/badge/Android-SDK_35_(min_28)-brightgreen.svg?style=flat&logo=android)](https://developer.android.com)

App Android nativa (**Kotlin + Jetpack Compose**) para **supervisar y comandar** un humanoide **Unitree G1 EDU** que traslada muestras críticas, hemocomponentes y medicación entre el Laboratorio Central (PB) y la UTI (Piso 2).

> **UADE — Desarrollo de Aplicaciones I.** Trabajo práctico HMI/HRI para robots de servicio.
> Funciona **sin robot, sin servidor y sin internet**: el robot se simula en el propio dispositivo.

📄 **Informe técnico:** [`TP HMI_HRI - MediBot G1 (Unitree G1 EDU) - Informe Técnico Completo.md`](TP%20HMI_HRI%20-%20MediBot%20G1%20(Unitree%20G1%20EDU)%20-%20Informe%20T%C3%A9cnico%20Completo.md) · 🏛️ **Arquitectura y contrato JSON:** [`DOCS_TECNICOS_TP.md`](DOCS_TECNICOS_TP.md) · 📸 **Evidencias:** [`docs/evidencias/`](docs/evidencias/README.md) · ✅ **Estado del TP:** [`ANALISIS_CUMPLIMIENTO_TP.md`](ANALISIS_CUMPLIMIENTO_TP.md)

---

## Funcionalidades

| Área | Qué hace |
| :--- | :--- |
| **Selección y sesión** | Flota del hospital (1 robot disponible y 2 no disponibles). Para conectar se ingresa el **ID de operador** y se elige el **rol**: el **operador** emite órdenes y el **supervisor** solo observa. Desconectar pide confirmación. La sesión sobrevive a la recreación de la pantalla. |
| **Supervisión** | Enlace (con latencia y señal), **última actualización siempre visible**, batería y autonomía, mapa de 6 nodos con planta y progreso, 3 variables del servicio (temperatura de cápsula, vibración y cerradura NFC), postura, velocidad y estado HRI (voz y luz). |
| **Datos antiguos** | A los 4 s sin telemetría se muestra **SIN ENLACE** y "hace Xs", y las tarjetas se atenúan. Nunca se muestra `ONLINE` con datos viejos. Dentro del ascensor el enlace se informa `DEGRADED`. |
| **Comandos** | Iniciar, pausar, reanudar, cancelar, regresar a punto seguro, detención operativa y reconocer alertas. Cada orden lleva `commandId` y `operatorId` y pasa por **Pendiente → Confirmada / Rechazada / Sin respuesta** (timeout real de 2,5 s). Hay una sola orden en curso a la vez, y la detención siempre puede emitirse. Las acciones con consecuencias piden confirmación. |
| **Misiones** | Catálogo con checklist de precondiciones. Cada misión define su rango térmico y la cápsula se acondiciona al despachar (sangre y plasma 2–6 °C, farmacia 15–25 °C). |
| **Seguridad operativa** | Bloqueo de misiones con batería < 20 %, **retorno forzado < 15 %**, agachado seguro < 8 % y recuperación presencial (rearme y cambio de batería). Punto seguro más cercano. Al cancelar, el robot vuelve a la base. Con manipulación detectada, la cápsula no se libera. |
| **Historial y auditoría** | Room: misiones (completadas, canceladas o fallidas, con id de ejecución y máximos de temperatura y vibración) y eventos (alertas, resultado de cada orden con su operador, sesiones y escenarios). Es de solo lectura para el operador; el borrado está en el panel de ingeniería y queda registrado. |
| **Panel de ingeniería** | Los 5 escenarios del TP, rechazo forzado, corte y restablecimiento del enlace, acciones presenciales simuladas, reinicio de la simulación y herramientas de auditoría. |

## Los 5 escenarios

| # | Escenario | Qué se demuestra |
| :- | :--- | :--- |
| 1 | Misión normal | Despacho → puerta cortafuego → ascensor (enlace degradado) → UTI → validación NFC → historial. |
| 2 | Batería baja | 19 %: alerta y acción "regresar". Si nadie actúa, retorno forzado al 15 %. Si está pausado y baja de 8 %, se agacha. |
| 3 | Desconexión | Datos antiguos, orden "sin respuesta" y recuperación con los eventos ocurridos durante el corte. |
| 4 | Incidente | Camilla en el pasillo. Se reconoce la alerta, el robot rechaza reanudar con el obstáculo presente, y reanuda cuando se despeja. |
| 5 | Propio: cadena térmica + manipulación | La cápsula sube a 8.4 °C y se detecta apertura no autorizada. El operador cancela (vuelve a la base) o desvía, y la carga manipulada no se libera. |

## Arquitectura

**MVVM + flujo unidireccional**, con capas separadas. El detalle y el diagrama están en [`DOCS_TECNICOS_TP.md`](DOCS_TECNICOS_TP.md).

- **UI (Compose + Navigation Compose):** observa `SupervisionUiState` con `collectAsStateWithLifecycle()` y envía eventos.
- **`SupervisionViewModel`:** `combine(...).stateIn(...)`, calcula la antigüedad del dato y el enlace mostrado, y guarda la sesión en `SavedStateHandle`.
- **`domain/CommandRules`:** condiciones de habilitación y umbrales, en Kotlin puro.
- **`RobotRepository`** (telemetría, comandos y eventos) y **`SimulatorControls`** (solo pruebas), implementados por `SimulatedRobotRepository`.
- **`AuditRepository`**, implementado por `RoomAuditRepository`, con esquema exportado y migración v1→v2.
- **`RobotEventRecorder`** (nivel aplicación): persiste alertas, misiones y resultados de órdenes.

```
app/src/main/java/com/medibot/g1/
├── MainActivity.kt / MediBotApplication.kt (AppContainer)
├── domain/CommandRules.kt
├── data/
│   ├── model/        Telemetría, comandos, sesión, flota, catálogo, eventos, registros
│   ├── repository/   RobotRepository, SimulatedRobotRepository, AuditRepository, RobotEventRecorder
│   └── local/        Room: AppDatabase, entidades y DAOs
└── ui/
    ├── screens/      RobotSelection, Dashboard, Missions, History
    ├── components/   TelemetryCard, AlertBanner, CommandControlPanel, HospitalMapCanvas, diálogos…
    └── viewmodel/    SupervisionViewModel
```

## Compilar y probar

Requisitos: Android Studio con JDK 17 o superior y Android SDK 35.

```powershell
.\gradlew.bat :app:assembleDebug
```

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

```powershell
.\gradlew.bat :app:installDebug
```

Hay **24 tests unitarios** (JVM, con tiempo virtual): reglas de habilitación, cada escenario del simulador y el ViewModel (roles y validación del operador).

**Demo rápida:** al abrir la app, conectar con un ID cualquiera (por ejemplo `OP-LAB-04`) como operador. Después usar el botón violeta (panel de ingeniería) para disparar los escenarios.

## Paleta (Dark Medical-Tech)

| Color | Hex | Uso |
| :--- | :--- | :--- |
| Dark Background | `#0B0F19` | Fondo |
| Neon Cyan | `#00F5D4` | Acento, telemetría normal |
| Terminal Green | `#00E676` | En línea, confirmaciones |
| Warning Amber | `#FFAB00` | Advertencias, datos antiguos, modo supervisor |
| Critical Red | `#FF1744` | Crítico, detención |
| Electric Violet | `#7C4DFF` | Panel de ingeniería, validación NFC |

> El contrato JSON es una propuesta de aplicación. **No es el protocolo oficial de Unitree.** La detención desde la app es un comando de software y **no reemplaza la parada de emergencia física**.
