package es.ull.calidadaire.observer;

/** Interfaz Sujeto (Figura 1): gestiona la suscripción de observadores y les notifica los cambios. */
public interface Sujeto {
    void agregarObservador(Observador o);

    void eliminarObservador(Observador o);

    void notificar();
}
