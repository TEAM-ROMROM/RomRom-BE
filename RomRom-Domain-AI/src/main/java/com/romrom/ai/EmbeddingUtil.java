package com.romrom.ai;

import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentResponse;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@UtilityClass
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

  /**
   * 응답에서 벡터 값만 추출해 float[] 로 변환
   *
   * @param response EmbedContentResponse 객체
   * @return 임베딩 float[]
   */
  public static float[] extractVector(EmbedContentResponse response) {
    List<Float> embeddingValues = response.embeddings()
        .flatMap(list -> list.stream().findFirst())
        .flatMap(ContentEmbedding::values)
        .orElseThrow(() -> new CustomException(ErrorCode.EMBEDDING_NOT_FOUND));
    int size = embeddingValues.size();

    float[] vector = new float[size];
    for (int i = 0; i < size; i++) {
      vector[i] = embeddingValues.get(i);
    }
    return vector;
  }
}
