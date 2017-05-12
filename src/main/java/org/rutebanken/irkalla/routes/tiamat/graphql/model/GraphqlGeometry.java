package org.rutebanken.irkalla.routes.tiamat.graphql.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphqlGeometry {
    public String type;

    public List<List<Double>> coordinates;


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GraphqlGeometry that = (GraphqlGeometry) o;

        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        return coordinates != null ? coordinates.equals(that.coordinates) : that.coordinates == null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (coordinates != null ? coordinates.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Geometry{" +
                       "type='" + type + '\'' +
                       ", coordinates=" + coordinates +
                       '}';
    }
}