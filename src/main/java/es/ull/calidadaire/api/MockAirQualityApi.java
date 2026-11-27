package es.ull.calidadaire.api;

import es.ull.calidadaire.model.*;
import es.ull.calidadaire.model.station.Station;
import es.ull.calidadaire.model.station.StationDetail;
import es.ull.calidadaire.model.values.Coordinates;
import es.ull.calidadaire.model.values.IcaParameter;
import es.ull.calidadaire.model.values.Measurement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Fuente simulada con datos deterministas. Permite desarrollar la GUI y los servicios sin token
 * ni conexión. Los datos NO son reales.
 */
public class MockAirQualityApi implements AirQualityApi {
    private static final List<Zone> ZONES = List.of(
            new Zone(1, "Santa Cruz de Tenerife", 1), new Zone(2, "Las Palmas de Gran Canaria", 1),
            new Zone(3, "Lanzarote", 1));

    private static final List<Parameter> PARAMS = List.of(
            new Parameter(1, "PM10", "Partículas en suspensión < 10 µm"),
            new Parameter(2, "PM2.5", "Partículas en suspensión < 2,5 µm"),
            new Parameter(3, "NO2", "Dióxido de nitrógeno"),
            new Parameter(4, "O3", "Ozono"),
            new Parameter(5, "SO2", "Dióxido de azufre"));

    private static final Unit UG = new Unit(1, "Microgramos por metro cúbico", "µg/m³");

    private static final List<Station> STATIONS = List.of(
            st(10, 1, "Tena Artigas", "TENA", 28.4636, -16.2518),
            st(11, 1, "Parque La Granja", "GRAN", 28.4565, -16.2569),
            st(20, 2, "Schamann", "SCHA", 28.1350, -15.4400),
            st(21, 2, "Mercado Central", "MERC", 28.1280, -15.4260),
            st(30, 3, "Arrecife", "ARRE", 28.9630, -13.5477));

    // parámetros que mide cada estación
    private static final Map<Long, long[]> STATION_PARAMS = Map.of(
            10L, new long[]{1, 2, 3, 4}, 11L, new long[]{1, 3, 4, 5}, 20L, new long[]{1, 2, 3},
            21L, new long[]{1, 3, 5}, 30L, new long[]{1, 2, 4});

    private static final double[] BASE = {0, 22, 10, 25, 55, 4};      // por parameterId
    private static final double[] AMPL = {0, 12, 6, 18, 20, 3};

    private static Station st(long id, long zone, String name, String tag, double lat, double lon) {
        return new Station(id, zone, name, tag, new Coordinates(lat, lon, 10), "ES" + id, true,
                LocalDate.of(2010, 1, 1), LocalDate.now().minusDays(1));
    }

    @Override public String sourceName() { return "Datos simulados (sin conexión)"; }

    @Override public List<Zone> getZones() { return ZONES; }

    @Override public List<Station> getStations(Collection<Long> zoneIds, Collection<Long> parameterIds) {
        return STATIONS.stream()
                .filter(s -> zoneIds == null || zoneIds.isEmpty() || zoneIds.contains(s.zoneId()))
                .filter(s -> parameterIds == null || parameterIds.isEmpty()
                        || Arrays.stream(STATION_PARAMS.get(s.id())).anyMatch(parameterIds::contains))
                .toList();
    }

    @Override public StationDetail getStation(long id) {
        Station s = STATIONS.stream().filter(x -> x.id() == id).findFirst()
                .orElseThrow(() -> new ApiException(404, "Estación no encontrada: " + id));
        List<StationParameter> ps = new ArrayList<>();
        for (long pid : STATION_PARAMS.get(id)) {
            Parameter p = PARAMS.get((int) pid - 1);
            ps.add(new StationParameter(p.description(), p.name(), p, UG));
        }
        return new StationDetail(s, ps);
    }

    @Override public List<Parameter> getParameters() { return PARAMS; }

    @Override public List<StationIca> getIca() { return STATIONS.stream().map(s -> getStationIca(s.id())).toList(); }

    @Override public StationIca getStationIca(long stationId) {
        LocalDateTime now = LocalDateTime.now();
        List<IcaParameter> out = new ArrayList<>();
        for (long pid : STATION_PARAMS.get(stationId)) {
            double v = value(stationId, pid, now);
            out.add(new IcaParameter(PARAMS.get((int) pid - 1).name(), category(pid, v), now.toLocalDate(),
                    now.getHour() + 1, v));
        }
        return new StationIca(stationId, "HOURLY", out);
    }

    @Override
    public List<Measurement> getDatasets(LocalDateTime start, LocalDateTime end, Collection<Long> stationIds,
                                         Collection<Long> parameterIds, PeriodType periodType) {
        return series(start, end, stationIds, parameterIds, periodType.name());
    }

    @Override
    public List<Measurement> getRealtime(LocalDateTime start, Collection<Long> stationIds, Collection<Long> parameterIds) {
        return series(start, LocalDateTime.now(), stationIds, parameterIds, "HOURLY");
    }

    @Override public List<Measurement> getLatestRealtime() {
        LocalDateTime now = LocalDateTime.now();
        return series(now.minusHours(24), now, null, null, "HOURLY");
    }

    private List<Measurement> series(LocalDateTime from, LocalDateTime to, Collection<Long> stationIds,
                                     Collection<Long> parameterIds, String periodType) {
        List<Measurement> out = new ArrayList<>();
        LocalDateTime t = from.withMinute(0).withSecond(0).withNano(0);
        for (; !t.isAfter(to); t = t.plusHours(1)) {
            for (Station s : STATIONS) {
                if (stationIds != null && !stationIds.isEmpty() && !stationIds.contains(s.id())) continue;
                for (long pid : STATION_PARAMS.get(s.id())) {
                    if (parameterIds != null && !parameterIds.isEmpty() && !parameterIds.contains(pid)) continue;
                    out.add(new Measurement(s.id(), pid, periodType, t.toLocalDate(), t.getHour() + 1,
                            value(s.id(), pid, t), "V"));
                }
            }
        }
        return out;
    }

    /** Onda diaria + desfase por estación, determinista. */
    private static double value(long stationId, long parameterId, LocalDateTime t) {
        double phase = (stationId % 7) * 0.7 + parameterId;
        double hour = t.getHour() + t.getDayOfYear() * 0.37;
        return Math.max(0, Math.round((BASE[(int) parameterId]
                + AMPL[(int) parameterId] * Math.sin(hour / 24.0 * 2 * Math.PI + phase)) * 10) / 10.0);
    }

    private static String category(long parameterId, double v) {
        double ref = BASE[(int) parameterId] + AMPL[(int) parameterId];
        double r = v / ref;
        return r < 0.4 ? "Buena" : r < 0.7 ? "Razonablemente buena" : r < 0.9 ? "Regular" : "Desfavorable";
    }
}
