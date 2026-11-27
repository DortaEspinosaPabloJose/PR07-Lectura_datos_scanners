package es.ull.calidadaire.api;

import es.ull.calidadaire.model.*;
import es.ull.calidadaire.model.station.Station;
import es.ull.calidadaire.model.station.StationDetail;
import es.ull.calidadaire.model.values.Measurement;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Contrato de acceso a los datos de calidad del aire. La GUI y los servicios dependen
 * solo de esta interfaz, de modo que puede sustituirse por un simulado (MockAirQualityApi).
 */
public interface AirQualityApi {
    enum PeriodType { HOURLY, DAILY }

    List<Zone> getZones();

    /** Estaciones; los filtros pueden ser null o vacíos. */
    List<Station> getStations(Collection<Long> zoneIds, Collection<Long> parameterIds);

    StationDetail getStation(long id);

    List<Parameter> getParameters();

    /** ICA vigente de todas las estaciones. */
    List<StationIca> getIca();

    StationIca getStationIca(long stationId);

    /** Datos validados (horarios o diarios). */
    List<Measurement> getDatasets(LocalDateTime start, LocalDateTime end, Collection<Long> stationIds,
                                  Collection<Long> parameterIds, PeriodType periodType);

    /** Datos temporales (últimas 24 h) filtrados. */
    List<Measurement> getRealtime(LocalDateTime start, Collection<Long> stationIds, Collection<Long> parameterIds);

    /** Foto completa de las últimas 24 h de todas las estaciones (puede pesar varios MB). */
    List<Measurement> getLatestRealtime();

    /** Nombre de la fuente, para mostrarlo en la GUI ("API Gobierno de Canarias", "Simulado"). */
    String sourceName();
}
