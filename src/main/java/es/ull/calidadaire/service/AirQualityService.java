package es.ull.calidadaire.service;

import es.ull.calidadaire.api.AirQualityApi;
import es.ull.calidadaire.api.ApiException;
import es.ull.calidadaire.model.*;
import es.ull.calidadaire.model.station.Station;
import es.ull.calidadaire.model.station.StationDetail;
import es.ull.calidadaire.model.values.IcaParameter;
import es.ull.calidadaire.model.values.Measurement;
import es.ull.calidadaire.util.TtlCache;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Capa de aplicación entre la GUI y la API: cachea catálogos y combina datos.
 * Es el punto de enganche previsto para la etapa 2 (patrón observador).
 */
public class AirQualityService {
    private static final Comparator<Measurement> BY_TIME =
            Comparator.comparing(Measurement::date).thenComparingInt(Measurement::period);

    private final AirQualityApi api;
    private final TtlCache<List<Zone>> zones;
    private final TtlCache<List<Station>> stations;
    private final TtlCache<List<Parameter>> parameters;
    private final TtlCache<List<StationIca>> ica;
    private final TtlCache<List<Measurement>> snapshot;
    private final Map<Long, TtlCache<StationDetail>> details = new ConcurrentHashMap<>();

    public AirQualityService(AirQualityApi api) {
        this.api = api;
        this.zones = new TtlCache<>(Duration.ofHours(1), api::getZones);
        this.stations = new TtlCache<>(Duration.ofHours(1), () -> api.getStations(null, null));
        this.parameters = new TtlCache<>(Duration.ofHours(1), api::getParameters);
        this.ica = new TtlCache<>(Duration.ofMinutes(2), api::getIca);
        this.snapshot = new TtlCache<>(Duration.ofSeconds(60), api::getLatestRealtime);
    }

    public String sourceName() { return api.sourceName(); }

    public List<Zone> zones() { return zones.get(); }

    public List<Parameter> parameters() { return parameters.get(); }

    public List<Station> stations(Long zoneId) {
        return stations.get().stream().filter(s -> zoneId == null || s.zoneId() == zoneId).toList();
    }

    /** Tarjetas del panel principal. */
    public List<StationOverview> overview(Long zoneId) {
        Map<Long, String> zoneNames = zoneNames();
        Map<Long, List<IcaParameter>> icaByStation = ica.get().stream()
                .collect(Collectors.toMap(StationIca::stationId, StationIca::parameters, (a, b) -> a));
        return stations(zoneId).stream()
                .map(s -> new StationOverview(s, zoneNames.getOrDefault(s.zoneId(), "?"),
                        icaByStation.getOrDefault(s.id(), List.of())))
                .toList();
    }

    public StationView stationView(long id) {
        StationDetail d = detail(id);
        List<ParameterReading> readings = new ArrayList<>();
        for (ParameterSeries s : history(id)) {
            // Última medición con valor de cada parámetro
            s.points().stream().filter(m -> m.value() != null).max(BY_TIME).ifPresent(m ->
                    readings.add(new ParameterReading(s.parameterId(), s.name(), s.unit(), m.value(),
                            m.date(), m.period(), m.flag())));
        }
        readings.sort(Comparator.comparing(ParameterReading::name, String.CASE_INSENSITIVE_ORDER));
        return new StationView(d.station(), zoneNames().getOrDefault(d.station().zoneId(), "?"), readings);
    }

    /** Series de las últimas 24 h de todos los parámetros de la estación. */
    public List<ParameterSeries> history(long stationId) {
        StationDetail d = detail(stationId);
        Map<Long, StationParameter> byParam = paramIndex(d);
        List<Measurement> data;
        try {
            data = snapshot.get().stream().filter(m -> m.stationId() == stationId).toList();
        } catch (ApiException e) {
            if (e.status() != 503) throw e;     // foto no disponible u obsoleta: consulta directa
            data = api.getRealtime(LocalDateTime.now().minusHours(24), List.of(stationId), byParam.keySet());
        }
        Map<Long, List<Measurement>> grouped = data.stream()
                .collect(Collectors.groupingBy(Measurement::parameterId));
        List<ParameterSeries> out = new ArrayList<>();
        for (StationParameter sp : d.parameters()) {
            long pid = sp.parameter().id();
            List<Measurement> pts = new ArrayList<>(grouped.getOrDefault(pid, List.of()));
            pts.sort(BY_TIME);
            out.add(new ParameterSeries(pid, sp.parameter().name(),
                    sp.unit() == null ? "" : sp.unit().symbol(), pts));
        }
        out.sort(Comparator.comparing(ParameterSeries::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** Fuerza recarga de los datos cambiantes (botón "Actualizar"). */
    public void refresh() {
        snapshot.invalidate();
        ica.invalidate();
    }

    private StationDetail detail(long id) {
        return details.computeIfAbsent(id, k -> new TtlCache<>(Duration.ofHours(1), () -> api.getStation(k))).get();
    }

    private Map<Long, StationParameter> paramIndex(StationDetail d) {
        Map<Long, StationParameter> m = new LinkedHashMap<>();
        for (StationParameter sp : d.parameters()) m.put(sp.parameter().id(), sp);
        return m;
    }

    private Map<Long, String> zoneNames() {
        return zones().stream().collect(Collectors.toMap(Zone::id, Zone::name, (a, b) -> a));
    }
}
