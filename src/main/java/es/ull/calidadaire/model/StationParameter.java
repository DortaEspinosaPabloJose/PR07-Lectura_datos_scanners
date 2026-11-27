package es.ull.calidadaire.model;

/** Parámetro que mide una estación concreta, con su unidad. */
public record StationParameter(String description, String tag, Parameter parameter, Unit unit) {}
