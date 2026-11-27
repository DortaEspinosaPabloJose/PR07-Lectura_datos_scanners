package es.ull.calidadaire.model.values;

import java.time.LocalDate;

/** Índice de calidad del aire de un parámetro (categoría textual vigente). */
public record IcaParameter(String parameter, String ica, LocalDate date, int period, Double measurementValue) {}
