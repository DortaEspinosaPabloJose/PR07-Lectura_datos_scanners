package es.ull.calidadaire.observer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Observador concreto: muestra al público los últimos valores medidos. */
public class PanelInformativo implements Observador {
    private final String ubicacion;
    private volatile double pm10 = Double.NaN;
    private volatile double no2 = Double.NaN;
    private volatile LocalDateTime actualizado;

    public PanelInformativo(String ubicacion) { this.ubicacion = ubicacion; }

    @Override
    public void actualizar(double pm10, double no2) {
        this.pm10 = pm10;
        this.no2 = no2;
        this.actualizado = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        mostrar();
    }

    public void mostrar() {
        System.out.println("[PANEL] " + ubicacion + " · PM10: " + fmt(pm10) + " · NO2: " + fmt(no2));
    }

    static String fmt(double v) { return Double.isNaN(v) ? "—" : String.format("%.1f µg/m³", v); }

    public String getUbicacion() { return ubicacion; }

    public double getPm10() { return pm10; }

    public double getNo2() { return no2; }

    public LocalDateTime getActualizado() { return actualizado; }
}
