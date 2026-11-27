package es.ull.calidadaire.model;

import es.ull.calidadaire.model.values.IcaParameter;

import java.util.List;

public record StationIca(long stationId, String periodType, List<IcaParameter> parameters) {}
