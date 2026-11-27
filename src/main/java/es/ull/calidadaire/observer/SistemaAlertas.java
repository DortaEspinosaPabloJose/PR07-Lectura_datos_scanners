package es.ull.calidadaire.observer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Observador concreto: compara las mediciones con unos umbrales y emite un aviso cuando se superan. */
public class SistemaAlertas implements Observador {
    /** Valores por defecto: valores límite de la normativa europea (PM10 media diaria, NO2 media horaria). */
    public static final double UMBRAL_PM10_POR_DEFECTO = 50.0;
    public static final double UMBRAL_NO2_POR_DEFECTO = 200.0;
    private static final int MAX_ALERTAS = 200;

    public record Alerta(LocalDateTime momento, String ubicacion, String parametro, double valor, double umbral) {
        @Override public String toString() {
            return momento + " | " + ubicacion + " | " + parametro + " = " + valor + " (umbral " + umbral + ")";
        }
    }

    private final String ubicacion;
    private final double umbralPM10;
    private final double umbralNO2;
    private final List<Alerta> alertas = new ArrayList<>();

    public SistemaAlertas(String ubicacion) {
        this(ubicacion, UMBRAL_PM10_POR_DEFECTO, UMBRAL_NO2_POR_DEFECTO);
    }

    public SistemaAlertas(String ubicacion, double umbralPM10, double umbralNO2) {
        this.ubicacion = ubicacion;
        this.umbralPM10 = umbralPM10;
        this.umbralNO2 = umbralNO2;
    }

    @Override
    public void actualizar(double pm10, double no2) {
        if (!Double.isNaN(pm10) && pm10 > umbralPM10) emitirAlerta("PM10", pm10, umbralPM10);
        if (!Double.isNaN(no2) && no2 > umbralNO2) emitirAlerta("NO2", no2, umbralNO2);
    }

    public void emitirAlerta(String parametro, double valor, double umbral) {
        Alerta a = new Alerta(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS), ubicacion, parametro, valor, umbral);
        synchronized (alertas) {
            alertas.add(a);
            if (alertas.size() > MAX_ALERTAS) alertas.remove(0);
        }
        System.out.println("[ALERTA] " + a);
    }

    public List<Alerta> getAlertas() {
        synchronized (alertas) { return List.copyOf(alertas); }
    }

    public double getUmbralPM10() { return umbralPM10; }

    public double getUmbralNO2() { return umbralNO2; }
}
