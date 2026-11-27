package es.ull.calidadaire.model.values;

import java.time.LocalDate;

/**
 * Medición de un parámetro en una estación (datos en tiempo real o validados).
 * period: franja horaria dentro de la fecha. value puede ser null si no hay dato.
 */
public record Measurement(long stationId, long parameterId, String periodType, LocalDate date,
                          int period, Double value, String flag) {}
