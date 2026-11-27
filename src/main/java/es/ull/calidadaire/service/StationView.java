package es.ull.calidadaire.service;

import es.ull.calidadaire.model.station.Station;
import java.util.List;

/** Detalle de una estación: datos, zona y últimas lecturas. */
public record StationView(Station station, String zoneName, List<ParameterReading> readings) {}
