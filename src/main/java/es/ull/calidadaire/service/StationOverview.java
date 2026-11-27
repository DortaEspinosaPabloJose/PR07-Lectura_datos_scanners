package es.ull.calidadaire.service;

import es.ull.calidadaire.model.values.IcaParameter;
import es.ull.calidadaire.model.station.Station;
import java.util.List;

/** Estación con el nombre de su zona y su ICA vigente (tarjeta del panel principal). */
public record StationOverview(Station station, String zoneName, List<IcaParameter> ica) {}
