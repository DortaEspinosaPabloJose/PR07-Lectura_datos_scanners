package es.ull.calidadaire.model.station;

import es.ull.calidadaire.model.StationParameter;

import java.util.List;

/** Estación con la lista de parámetros que mide. */
public record StationDetail(Station station, List<StationParameter> parameters) {}
