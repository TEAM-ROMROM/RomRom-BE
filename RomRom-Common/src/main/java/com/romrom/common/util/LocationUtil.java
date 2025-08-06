package com.romrom.common.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;


@Slf4j
@UtilityClass
public class LocationUtil {

  /**
   * 위도·경도로부터 JTS Point 객체를 만들어 반환
   */
  public Point convertToPoint(Double longitude, Double latitude) {
    GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
    return gf.createPoint(new Coordinate(longitude, latitude));
  }
}
