package com.romrom.common.util;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class EmbeddingUtil {

  /**
   * 임베딩 벡터를 문자열 리터럴로 변환
   * @param embedding 임베딩 벡터
   * @return 문자열 리터럴 형태의 임베딩 벡터
   */
  public static String toVectorLiteral(float[] embedding) {
    return IntStream.range(0, embedding.length)
        .mapToObj(i -> Float.toString(embedding[i]))
        .collect(Collectors.joining(",", "[", "]"));
  }
}
