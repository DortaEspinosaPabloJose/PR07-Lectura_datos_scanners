package es.ull.calidadaire.model.station;

import es.ull.calidadaire.model.values.Coordinates;

import java.time.LocalDate;

/** Estación de medición (resumen). firstDate/lastDate pueden ser null. */
public record Station(long id, long zoneId, String name, String tag, Coordinates coordinates,
                      String cnac, boolean active, LocalDate firstDate, LocalDate lastDate) {}
