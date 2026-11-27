# Calidad del aire · Canarias (Etapa 1: parte común)

Lector de calidad del aire de las Islas Canarias en Java 17+. Esta etapa entrega la **parte común**:
conexión con la API abierta del Gobierno de Canarias y un esqueleto de GUI web con datos conectados
(sin patrón observador todavía).

- API: https://www3.gobiernodecanarias.org/calidadambiental/calidadaire/anemo/api/v3/api-docs/public-v1
- Autenticación: cabecera `Authorization: Bearer <token>` en todos los endpoints.

## Ejecutar

```bash
# Sin dependencias externas. Con Maven:
mvn package && java -jar target/calidad-aire-canarias-0.1.0.jar

# O sin Maven:
mkdir out && javac -d out $(find src/main/java -name '*.java') && cp -r src/main/resources/* out/
java -cp out es.ull.calidadaire.App
```

Opciones: `--port=8080`, `--token=XXXX` (o variable `AIRQUALITY_TOKEN`), `--base-url=...`, `--mock`.
Sin token se usan **datos simulados** (`MockAirQualityApi`) para poder trabajar sin conexión; la GUI
muestra la fuente en uso. Abrir http://localhost:8080.

## Arquitectura

```
navegador ──HTTP──> web.WebServer ──> service.AirQualityService ──> api.AirQualityApi
 (index.html,       (JSON /api/...)    (caché + combinación)         ├─ CanariasAirQualityClient (real)
  app.js)                                                            └─ MockAirQualityApi (simulado)
```

| Paquete | Contenido |
|---|---|
| `model` | Records inmutables: `Zone`, `Station`, `StationDetail`, `Parameter`, `Unit`, `Measurement`, `StationIca`... |
| `api` | Interfaz `AirQualityApi`, cliente HTTP real, `ApiMapper` (JSON→modelo), `ApiException`, mock |
| `service` | `AirQualityService` (caché TTL, unión de datos) y DTOs para la GUI |
| `web` | `WebServer` (JDK `HttpServer`): estáticos + API JSON interna |
| `util` | `Json` (lector/escritor mínimo), `TtlCache` |
| `resources/web` | GUI: `index.html`, `styles.css`, `app.js` |

Endpoints cubiertos del cliente: zones, stations (+detalle), parameters, ica (+por estación),
datasets (validados) y datasets/realtime (+`latest`). Los de `parameters/{id}` y `zones/{id}` no se usan aún.

Gestión de errores: 401 (token), 429 (con `Retry-After`), 503 de `realtime/latest` (el servicio recurre
a `datasets/realtime`), y fallos de red, todos como `ApiException`; la GUI los muestra en un aviso.

## Puntos de enganche para la etapa 2 (observador)

- `AirQualityService.refresh()` y la caché de la foto en tiempo real son el origen natural del **sujeto**
  (estación de medición): al llegar nuevas mediciones se notifica a los observadores.
- Las pestañas **Alertas** e **Histórico** de la GUI están reservadas para los observadores concretos;
  el **Panel** actual sería el observador "panel informativo".

## Reparto de tareas y flujo Git (propuesta)

Ramas: `main` (estable) · `develop` (integración) · `feature/<nombre>` (una por persona).
Cada persona: `git switch -c feature/x develop` → commits → push → Pull Request a `develop`;
al cerrar la etapa, `develop` → `main`.

| Tarea | Archivos |
|---|---|
| 1. Modelo y contrato (acordar primero, es lo que desbloquea al resto) | `model/*`, `api/AirQualityApi` |
| 2. Cliente HTTP real y errores | `api/CanariasAirQualityClient`, `ApiMapper`, `ApiException` |
| 3. Mock y servicio con caché | `api/MockAirQualityApi`, `service/*`, `util/TtlCache` |
| 4. Servidor web e integración | `web/WebServer`, `App` |
| 5. GUI (HTML/CSS/JS) | `resources/web/*` |

## Pendiente / supuestos a validar con el token real

- No se pudo probar contra la API real (sin token ni acceso de red desde el entorno de desarrollo);
  el cliente se verificó contra un servidor local que imita el contrato OpenAPI.
- Se asume que `start_date`/`end_date` aceptan fecha-hora ISO sin zona (`2026-11-27T00:00:00`) y que los
  arrays se envían repetidos (`stations=1&stations=2`).
- Los colores del ICA se deducen del texto de la categoría (Buena, Razonablemente buena, Regular,
  Desfavorable, Muy desfavorable, Extremadamente desfavorable); revisar si la API usa otros textos.
- `period` se muestra tal cual (franja dentro de la fecha); confirmar si es hora 1–24.
