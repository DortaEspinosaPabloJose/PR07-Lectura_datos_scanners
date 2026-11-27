package es.ull.calidadaire.api;

import es.ull.calidadaire.model.*;
import es.ull.calidadaire.model.station.Station;
import es.ull.calidadaire.model.station.StationDetail;
import es.ull.calidadaire.model.values.Measurement;
import es.ull.calidadaire.util.Json;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP de la API pública v1 de calidad del aire del Gobierno de Canarias.
 * Todos los endpoints son GET y requieren cabecera "Authorization: Bearer &lt;token&gt;".
 */
public class CanariasAirQualityClient implements AirQualityApi {
    public static final String DEFAULT_BASE_URL =
            "https://www3.gobiernodecanarias.org/calidadambiental/calidadaire/anemo";

    private final String baseUrl;
    private final String token;
    private final HttpClient http;
    private final Duration timeout;

    public CanariasAirQualityClient(String baseUrl, String token) {
        this(baseUrl, token, Duration.ofSeconds(90));
    }

    public CanariasAirQualityClient(String baseUrl, String token, Duration timeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Override public String sourceName() { return "API Gobierno de Canarias"; }

    @Override public List<Zone> getZones() {
        return ApiMapper.list(get("/api/v1/zones", new Query()), ApiMapper::zone);
    }

    @Override public List<Station> getStations(Collection<Long> zoneIds, Collection<Long> parameterIds) {
        Query q = new Query().addAll("zones", zoneIds).addAll("parameters", parameterIds);
        return ApiMapper.list(get("/api/v1/stations", q), ApiMapper::station);
    }

    @Override public StationDetail getStation(long id) {
        return ApiMapper.stationDetail(Json.object(get("/api/v1/stations/" + id, new Query())));
    }

    @Override public List<Parameter> getParameters() {
        return ApiMapper.list(get("/api/v1/parameters", new Query()), ApiMapper::parameter);
    }

    @Override public List<StationIca> getIca() {
        return ApiMapper.list(get("/api/v1/ica", new Query()), ApiMapper::stationIca);
    }

    @Override public StationIca getStationIca(long stationId) {
        return ApiMapper.stationIca(Json.object(get("/api/v1/ica/stations/" + stationId, new Query())));
    }

    @Override
    public List<Measurement> getDatasets(LocalDateTime start, LocalDateTime end, Collection<Long> stationIds,
                                         Collection<Long> parameterIds, PeriodType periodType) {
        Query q = new Query().add("start_date", fmt(start)).add("end_date", fmt(end))
                .addAll("stations", stationIds).addAll("parameters", parameterIds)
                .add("period_type", periodType.name());
        return ApiMapper.list(get("/api/v1/datasets", q), ApiMapper::dataset);
    }

    @Override
    public List<Measurement> getRealtime(LocalDateTime start, Collection<Long> stationIds, Collection<Long> parameterIds) {
        Query q = new Query().add("start_date", fmt(start))
                .addAll("stations", stationIds).addAll("parameters", parameterIds);
        return ApiMapper.list(get("/api/v1/datasets/realtime", q), ApiMapper::realtime);
    }

    @Override public List<Measurement> getLatestRealtime() {
        return ApiMapper.list(get("/api/v1/datasets/realtime/latest", new Query()), ApiMapper::realtime);
    }

    // ---------- HTTP ----------
    private Object get(String path, Query query) {
        String url = baseUrl + path + query.build();
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Accept", "application/json").GET();
        if (token != null && !token.isBlank()) rb.header("Authorization", "Bearer " + token);
        HttpResponse<String> resp;
        try {
            resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiException(0, "No se pudo conectar con la API: " + e.getMessage(), null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "Consulta interrumpida", null, e);
        }
        int status = resp.statusCode();
        if (status == 200) {
            try {
                return Json.parse(resp.body());
            } catch (IllegalArgumentException e) {
                throw new ApiException(status, "Respuesta no válida de la API: " + e.getMessage(), null, e);
            }
        }
        throw error(status, resp);
    }

    private static ApiException error(int status, HttpResponse<String> resp) {
        return switch (status) {
            case 401 -> new ApiException(401, "Credencial ausente o no válida (token Bearer)");
            case 429 -> {
                Integer retry = resp.headers().firstValue("Retry-After").map(v -> {
                    try { return Integer.valueOf(v.trim()); } catch (NumberFormatException e) { return null; }
                }).orElse(null);
                yield new ApiException(429, "Demasiadas peticiones; reintentar más tarde", retry, null);
            }
            default -> new ApiException(status, "Error HTTP " + status + errorMessage(resp.body()));
        };
    }

    /** Extrae "message" del ErrorResponse de la API si el cuerpo es JSON. */
    private static String errorMessage(String body) {
        try {
            Object o = Json.parse(body);
            if (o instanceof Map<?, ?>) {
                String m = Json.string(Json.object(o), "message");
                if (m != null) return ": " + m;
            }
        } catch (RuntimeException ignored) { /* cuerpo no JSON */ }
        return "";
    }

    private static String fmt(LocalDateTime t) {
        return t.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /** Construye query string; los arrays se envían repetidos (zones=1&zones=2). */
    private static final class Query {
        private final StringBuilder sb = new StringBuilder();

        Query add(String key, Object value) {
            sb.append(sb.length() == 0 ? '?' : '&')
              .append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append('=')
              .append(URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
            return this;
        }

        Query addAll(String key, Collection<Long> values) {
            if (values != null) for (Long v : values) add(key, v);
            return this;
        }

        String build() { return sb.toString(); }
    }
}
