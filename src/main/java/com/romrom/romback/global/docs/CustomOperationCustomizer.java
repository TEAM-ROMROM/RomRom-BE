package com.romrom.romback.global.docs;

import io.swagger.v3.oas.models.Operation;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * Swagger 의 API 변경 이력 관리
 * @ApiChangeLog 어노테이션에 대한 Table 생성 (이슈번호, 이슈 제목 포함)
 */
@Component
public class CustomOperationCustomizer implements OperationCustomizer {

  // 이슈 BASE URL
  private static final String ISSUE_BASE_URL = "https://github.com/TEAM-ROMROM/RomRom-BE/issues/";
  // OkHttpClient
  private static final OkHttpClient okHttpClient = new OkHttpClient();

  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    ApiChangeLogs apiChangeLogs = handlerMethod.getMethodAnnotation(ApiChangeLogs.class);

    if (apiChangeLogs != null) {
      StringBuilder tableBuilder = new StringBuilder();

      tableBuilder.append("\n\n**변경 관리 이력:**\n");
      tableBuilder.append("<table>\n");
      tableBuilder.append("<thead>\n");
      tableBuilder.append("<tr>")
          .append("<th>날짜</th>")
          .append("<th>작성자</th>")
          .append("<th>이슈번호</th>")
          .append("<th>이슈 제목</th>")
          .append("<th>변경 내용</th>")
          .append("</tr>\n");
      tableBuilder.append("</thead>\n");
      tableBuilder.append("<tbody>\n");

      for (ApiChangeLog log : apiChangeLogs.value()) {
        String description = log.description()
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");

        String issueNumberCell = "";
        String issueTitleCell = "";

        // 이슈번호가 0보다 큰 경우 -> 링크 연결 및 이슈 제목 파싱
        if (log.issueNumber() > 0) {
          // 이슈번호 링크 생성
          issueNumberCell = String.format("<a href=\"%s%d\" target=\"_blank\">#%d</a>",
              ISSUE_BASE_URL, log.issueNumber(), log.issueNumber());

          // 이슈 URL 생성
          String issueUrl = ISSUE_BASE_URL + log.issueNumber();

          // 이슈 제목 추출
          try {
            String htmlContent = fetchGithubIssuePageContent(issueUrl);
            String rawTitle = getRawTitleFromGithubIssueHtml(htmlContent);
            String processedTitle = processIssueTitleFromTitleTag(rawTitle);
            issueTitleCell = processedTitle;
          } catch (Exception e) {
            // 실패 시 이슈 제목
            issueTitleCell = "ERROR";
          }
        }

        tableBuilder.append(String.format(
            "<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>\n",
            log.date(), log.author().getDisplayName(), issueNumberCell, issueTitleCell, description));
      }

      tableBuilder.append("</tbody>\n");
      tableBuilder.append("</table>\n");

      String originalDescription = operation.getDescription() != null ? operation.getDescription() : "";
      operation.setDescription(originalDescription + tableBuilder.toString());
    }

    return operation;
  }

  /**
   * 지정된 URL의 HTML 내용을 OkHttp를 이용해 가져옵니다.
   */
  private String fetchGithubIssuePageContent(String url) throws IOException {
    Request request = new Request.Builder()
        .url(url)
        .get()
        .build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Unexpected code " + response);
      }
      return response.body().string();
    }
  }

  /**
   * 이슈 페이지 <title> 태그 내용
   */
  private String getRawTitleFromGithubIssueHtml(String html) {
    Document doc = Jsoup.parse(html);
    return doc.title();
  }

  /**
   * 제목 가공
   * - 제목에 "·" 기호가 있으면 그 이전 부분만 사용 (제목추출)
   * - 모든 대괄호 그룹 제거
   * - 나머지 특수문자(알파벳, 숫자, 한글, 공백 제외) 제거하고, 여러 공백은 하나의 공백으로 정리
   */
  private String processIssueTitleFromTitleTag(String title) {
    if (title == null || title.isEmpty()) {
      return "";
    }
    // "·" 기호가 있다면 그 이전 부분만 추출
    int delimiterIndex = title.indexOf("·");
    if (delimiterIndex != -1) {
      title = title.substring(0, delimiterIndex).trim();
    }
    String removedBracketGroups = title.replaceAll("\\[[^\\]]*\\]", "").trim();
    String cleaned = removedBracketGroups.replaceAll("[^\\p{L}\\p{Nd}\\s]", "").trim();
    return cleaned.replaceAll("\\s+", " ");
  }

}
