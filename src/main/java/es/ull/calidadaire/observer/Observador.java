package es.ull.calidadaire.observer;

/** Interfaz Observador (Figura 1): recibe los nuevos niveles cuando la estación los actualiza. */
public interface Observador {
    /**
     * @param pm10 nivel de PM10 en µg/m³ ({@code Double.NaN} si la estación no tiene dato)
     * @param no2  nivel de NO2 en µg/m³ ({@code Double.NaN} si la estación no tiene dato)
     */
    void actualizar(double pm10, double no2);
}
