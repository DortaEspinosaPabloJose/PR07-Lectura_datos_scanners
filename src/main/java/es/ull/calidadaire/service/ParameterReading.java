package es.ull.calidadaire.service;

import java.time.LocalDate;

/** Última medición de un parámetro en una estación, con nombre y unidad ya resueltos. */
public record ParameterReading(long parameterId, String name, String unit, Double value,
                               LocalDate date, int period, String flag) {}
