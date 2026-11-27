package es.ull.calidadaire;

import es.ull.calidadaire.api.AirQualityApi;
import es.ull.calidadaire.api.CanariasAirQualityClient;
import es.ull.calidadaire.api.MockAirQualityApi;
import es.ull.calidadaire.service.AirQualityService;
import es.ull.calidadaire.web.WebServer;

/**
 * Punto de entrada. Opciones:
 *   --port=8080        puerto HTTP de la GUI
 *   --token=XXXX       token Bearer (o variable de entorno AIRQUALITY_TOKEN)
 *   --base-url=URL     URL base de la API (por defecto la del Gobierno de Canarias)
 *   --mock             usar datos simulados (por defecto si no hay token)
 */
public final class App {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        String token = System.getenv("AIRQUALITY_TOKEN");
        String baseUrl = CanariasAirQualityClient.DEFAULT_BASE_URL;
        boolean mock = false;
        for (String a : args) {
            if (a.startsWith("--port=")) port = Integer.parseInt(a.substring(7));
            else if (a.startsWith("--token=")) token = a.substring(8);
            else if (a.startsWith("--base-url=")) baseUrl = a.substring(11);
            else if (a.equals("--mock")) mock = true;
            else { System.err.println("Opción desconocida: " + a); System.exit(2); }
        }
        boolean noToken = token == null || token.isBlank();
        if (noToken && !mock) System.err.println("Aviso: no hay token (AIRQUALITY_TOKEN); se usan datos simulados.");
        AirQualityApi api = (mock || noToken) ? new MockAirQualityApi() : new CanariasAirQualityClient(baseUrl, token);

        WebServer server = new WebServer(new AirQualityService(api), port);
        server.start();
        System.out.println("GUI disponible en http://localhost:" + server.port() + "  [" + api.sourceName() + "]");
    }
}
