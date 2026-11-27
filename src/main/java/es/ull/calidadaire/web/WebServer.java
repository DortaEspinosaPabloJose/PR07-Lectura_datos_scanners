package es.ull.calidadaire.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import es.ull.calidadaire.api.ApiException;
import es.ull.calidadaire.service.AirQualityService;
import es.ull.calidadaire.util.Json;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servidor web embebido (JDK): sirve la GUI estática y una API JSON interna (/api/...)
 * que expone el AirQualityService al navegador.
 */
public class WebServer {
    private static final Pattern STATION = Pattern.compile("^/api/stations/(\\d+)$");
    private static final Pattern HISTORY = Pattern.compile("^/api/stations/(\\d+)/history$");
    private static final Map<String, String> STATIC = Map.of(
            "/", "index.html", "/index.html", "index.html", "/app.js", "app.js", "/styles.css", "styles.css");

    private final AirQualityService service;
    private final HttpServer server;

    public WebServer(AirQualityService service, int port) throws IOException {
        this.service = service;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        server.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() { server.start(); }

    public void stop() { server.stop(0); }

    public int port() { return server.getAddress().getPort(); }

    // ---------- API JSON ----------
    private void handleApi(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod())) { sendJson(ex, 405, Map.of("error", "Método no permitido")); return; }
            String path = ex.getRequestURI().getPath();
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());
            Matcher m;
            Object result;
            if (path.equals("/api/status")) {
                result = Map.of("source", service.sourceName());
            } else if (path.equals("/api/zones")) {
                result = service.zones();
            } else if (path.equals("/api/parameters")) {
                result = service.parameters();
            } else if (path.equals("/api/overview")) {
                result = service.overview(longParam(q, "zone"));
            } else if (path.equals("/api/refresh")) {
                service.refresh();
                result = Map.of("ok", true);
            } else if ((m = STATION.matcher(path)).matches()) {
                result = service.stationView(Long.parseLong(m.group(1)));
            } else if ((m = HISTORY.matcher(path)).matches()) {
                result = service.history(Long.parseLong(m.group(1)));
            } else {
                sendJson(ex, 404, Map.of("error", "Ruta no encontrada: " + path));
                return;
            }
            sendJson(ex, 200, result);
        } catch (ApiException e) {
            int code = e.status() == 404 ? 404 : e.status() == 429 ? 429 : 502;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", e.getMessage());
            body.put("upstreamStatus", e.status());
            sendJson(ex, code, body);
        } catch (NumberFormatException e) {
            sendJson(ex, 400, Map.of("error", "Parámetro numérico no válido"));
        } catch (RuntimeException e) {
            e.printStackTrace();
            sendJson(ex, 500, Map.of("error", "Error interno: " + e.getMessage()));
        }
    }

    private static Long longParam(Map<String, String> q, String key) {
        String v = q.get(key);
        return v == null || v.isBlank() ? null : Long.valueOf(v);
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null) return out;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            String k = i < 0 ? pair : pair.substring(0, i);
            String v = i < 0 ? "" : pair.substring(i + 1);
            out.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return out;
    }

    private void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        send(ex, status, "application/json; charset=utf-8", Json.write(body).getBytes(StandardCharsets.UTF_8));
    }

    // ---------- Estáticos (classpath: /web) ----------
    private void handleStatic(HttpExchange ex) throws IOException {
        String file = STATIC.get(ex.getRequestURI().getPath());
        if (file == null) { send(ex, 404, "text/plain; charset=utf-8", "No encontrado".getBytes(StandardCharsets.UTF_8)); return; }
        try (InputStream in = WebServer.class.getResourceAsStream("/web/" + file)) {
            if (in == null) { send(ex, 404, "text/plain; charset=utf-8", "No encontrado".getBytes(StandardCharsets.UTF_8)); return; }
            String type = file.endsWith(".html") ? "text/html; charset=utf-8"
                    : file.endsWith(".js") ? "text/javascript; charset=utf-8" : "text/css; charset=utf-8";
            send(ex, 200, type, in.readAllBytes());
        }
    }

    private void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, body.length);
        try (var os = ex.getResponseBody()) { os.write(body); }
    }
}
