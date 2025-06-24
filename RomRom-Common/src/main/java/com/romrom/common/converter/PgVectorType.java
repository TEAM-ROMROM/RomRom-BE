package com.romrom.common.converter;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

public class PgVectorType implements UserType<float[]> {

  @Override
  public int getSqlType() {
    return Types.OTHER;
  }

  @Override
  public Class<float[]> returnedClass() {
    return float[].class;
  }

  @Override
  public boolean equals(float[] x, float[] y) {
    return Arrays.equals(x, y);
  }

  @Override
  public int hashCode(float[] x) {
    return Arrays.hashCode(x);
  }

  @Override
  public float[] nullSafeGet(ResultSet resultSet, int position, SharedSessionContractImplementor sharedSessionContractImplementor, Object o) throws SQLException {
    String vector = resultSet.getString(position);
    if (resultSet.wasNull() || vector == null) {
      return null;
    }
    // [1.0,2.0,3.0] 형태의 문자열을 파싱
    String[] values = vector.substring(1, vector.length() - 1).split(",");
    float[] result = new float[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = Float.parseFloat(values[i]);
    }
    return result;
  }

  @Override
  public void nullSafeSet(PreparedStatement preparedStatement, float[] floats, int index, SharedSessionContractImplementor sharedSessionContractImplementor) throws SQLException {
    if (floats == null) {
      preparedStatement.setNull(index, Types.OTHER, "vector");
      return;
    }
    StringBuilder stringBuilder = new StringBuilder("[");
    for (int i = 0; i < floats.length; i++) {
      if (i > 0) {
        stringBuilder.append(",");
      }
      stringBuilder.append(floats[i]);
    }
    stringBuilder.append("]");
    preparedStatement.setObject(index, stringBuilder.toString(), Types.OTHER);
  }

  @Override
  public float[] deepCopy(float[] floats) {
    return floats == null ? null : Arrays.copyOf(floats, floats.length);
  }

  @Override
  public boolean isMutable() {
    return true;
  }

  @Override
  public Serializable disassemble(float[] floats) {
    return floats == null ? null : Arrays.copyOf(floats, floats.length);
  }

  @Override
  public float[] assemble(Serializable serializable, Object o) {
    return serializable == null ? null : Arrays.copyOf((float[]) serializable, ((float[]) serializable).length);
  }

  @Override
  public float[] replace(float[] detached, float[] managed, Object owner) {
    return detached == null ? null : Arrays.copyOf(detached, detached.length);
  }
}
