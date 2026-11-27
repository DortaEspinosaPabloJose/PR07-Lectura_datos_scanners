package es.ull.calidadaire.service;

import es.ull.calidadaire.model.values.Measurement;
import java.util.List;

/** Serie temporal (ordenada) de un parámetro en una estación. */
public record ParameterSeries(long parameterId, String name, String unit, List<Measurement> points) {}
