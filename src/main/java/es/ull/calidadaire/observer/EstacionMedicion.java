package es.ull.calidadaire.observer;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sujeto concreto: una estación de medición. Guarda los niveles de PM10 y NO2 y, cada vez que
 * recibe nuevas mediciones ({@link #setMediciones}), notifica a todos sus observadores.
 * Los observadores se pueden añadir y quitar en tiempo de ejecución sin modificar esta clase.
 */
public class EstacionMedicion implements Sujeto {
    private final String ubicacion;
    private volatile double nivelPM10 = Double.NaN;
    private volatile double nivelNO2 = Double.NaN;
    // CopyOnWriteArrayList: se puede (des)suscribir desde la web mientras se notifica.
    private final List<Observador> observadores = new CopyOnWriteArrayList<>();

    public EstacionMedicion(String ubicacion) {
        this.ubicacion = Objects.requireNonNull(ubicacion);
    }

    @Override
    public void agregarObservador(Observador o) {
        Objects.requireNonNull(o);
        if (!observadores.contains(o)) observadores.add(o);
    }

    @Override
    public void eliminarObservador(Observador o) { observadores.remove(o); }

    @Override
    public void notificar() {
        for (Observador o : observadores) {
            try {
                o.actualizar(nivelPM10, nivelNO2);
            } catch (RuntimeException e) {   // un observador defectuoso no debe afectar a los demás
                System.err.println("Observador " + o.getClass().getSimpleName() + " falló: " + e);
            }
        }
    }

    /** Recibe nuevas mediciones y avisa a los observadores. */
    public synchronized void setMediciones(double pm10, double no2) {
        this.nivelPM10 = pm10;
        this.nivelNO2 = no2;
        notificar();
    }

    public String getUbicacion() { return ubicacion; }

    public double getNivelPM10() { return nivelPM10; }

    public double getNivelNO2() { return nivelNO2; }

    public int numeroObservadores() { return observadores.size(); }
}
