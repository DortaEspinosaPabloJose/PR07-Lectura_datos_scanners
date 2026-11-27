package es.ull.calidadaire.observer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Observador concreto: guarda cada medición recibida para su posterior análisis (en memoria). */
public class RegistroHistorico implements Observador {
    private final String ubicacion;
    private final List<String> registros = new ArrayList<>();

    public RegistroHistorico(String ubicacion) { this.ubicacion = ubicacion; }

    @Override
    public void actualizar(double pm10, double no2) { guardarRegistro(pm10, no2); }

    /** Formato: {@code 2026-11-27T03:20:11 | Estación | PM10=12.3 | NO2=24.8} (NaN se guarda como "NA"). */
    public void guardarRegistro(double pm10, double no2) {
        String linea = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS) + " | " + ubicacion
                + " | PM10=" + valor(pm10) + " | NO2=" + valor(no2);
        synchronized (registros) { registros.add(linea); }
    }

    private static String valor(double v) { return Double.isNaN(v) ? "NA" : Double.toString(v); }

    public List<String> getRegistros() {
        synchronized (registros) { return List.copyOf(registros); }
    }
}
