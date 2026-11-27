package es.ull.calidadaire.api;

import static es.ull.calidadaire.util.Json.*;

import es.ull.calidadaire.model.*;
import es.ull.calidadaire.model.station.Station;
import es.ull.calidadaire.model.station.StationDetail;
import es.ull.calidadaire.model.values.Coordinates;
import es.ull.calidadaire.model.values.IcaParameter;
import es.ull.calidadaire.model.values.Measurement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Convierte el JSON de la API (esquemas *V1Response) en el modelo del dominio. */
final class ApiMapper {
    private ApiMapper() {}

    static <T> List<T> list(Object json, Function<Map<String, Object>, T> f) {
        List<T> out = new ArrayList<>();
        for (Object o : array(json)) out.add(f.apply(object(o)));
        return out;
    }

    static Zone zone(Map<String, Object> m) {
        return new Zone(longValue(m, "id"), string(m, "name"), longValue(m, "networkId"));
    }

    static Coordinates coordinates(Map<String, Object> m) {
        if (m == null) return null;
        return new Coordinates(doubleValue(m, "latitude"), doubleValue(m, "longitude"), doubleValue(m, "altitude"));
    }

    static Station station(Map<String, Object> m) {
        return new Station(longValue(m, "id"), longValue(m, "zoneId"), string(m, "name"), string(m, "tag"),
                coordinates(m.get("coordinates") == null ? null : object(m.get("coordinates"))),
                string(m, "cnac"), bool(m, "active"), date(m, "firstDate"), date(m, "lastDate"));
    }

    static Parameter parameter(Map<String, Object> m) {
        return new Parameter(longValue(m, "id"), string(m, "name"), string(m, "description"));
    }

    static Unit unit(Map<String, Object> m) {
        if (m == null) return null;
        return new Unit(longValue(m, "id"), string(m, "name"), string(m, "symbol"));
    }

    static StationParameter stationParameter(Map<String, Object> m) {
        return new StationParameter(string(m, "description"), string(m, "tag"),
                m.get("parameter") == null ? null : parameter(object(m.get("parameter"))),
                unit(m.get("unit") == null ? null : object(m.get("unit"))));
    }

    static StationDetail stationDetail(Map<String, Object> m) {
        List<StationParameter> params = m.get("parameters") == null
                ? List.of() : list(m.get("parameters"), ApiMapper::stationParameter);
        return new StationDetail(station(m), params);
    }

    static StationIca stationIca(Map<String, Object> m) {
        List<IcaParameter> params = m.get("parameters") == null ? List.of()
                : list(m.get("parameters"), p -> new IcaParameter(string(p, "parameter"), string(p, "ica"),
                        date(p, "date"), intValue(p, "period"), doubleOrNull(p, "measurementValue")));
        return new StationIca(longValue(m, "stationId"), string(m, "periodType"), params);
    }

    /** Dato validado (DatasetV1Response). */
    static Measurement dataset(Map<String, Object> m) {
        String flag = m.get("validatedFlag") == null ? null : string(object(m.get("validatedFlag")), "value");
        return new Measurement(longValue(m, "stationId"), longValue(m, "parameterId"), string(m, "periodType"),
                date(m, "date"), intValue(m, "period"), doubleOrNull(m, "validatedValue"), flag);
    }

    /** Dato temporal (RealtimeDatasetV1Response). */
    static Measurement realtime(Map<String, Object> m) {
        String flag = m.get("temporalFlag") == null ? null : string(object(m.get("temporalFlag")), "value");
        return new Measurement(longValue(m, "stationId"), longValue(m, "parameterId"), string(m, "periodType"),
                date(m, "date"), intValue(m, "period"), doubleOrNull(m, "temporalValue"), flag);
    }
}
