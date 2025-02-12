package com.romrom.romback.global.docs;

import com.romrom.romback.domain.object.constant.HashType;
import com.romrom.romback.global.object.GithubIssue;
import com.romrom.romback.global.object.GithubIssueRepository;
import com.romrom.romback.global.object.HashRegistry;
import com.romrom.romback.global.object.HashRegistryRepository;
import com.romrom.romback.global.util.CommonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubIssueService {

  public static final String ISSUE_BASE_URL = "https://github.com/TEAM-ROMROM/RomRom-BE/issues/";
  private static final OkHttpClient okHttpClient = new OkHttpClient();

  private final GithubIssueRepository githubIssueRepository;
  private final HashRegistryRepository hashRegistryRepository;

  /**
   * DB에 존재하면 반환, 없으면 GitHub에서 파싱 후 저장한 후 반환합니다.
   */
  public GithubIssue getOrFetchIssue(Integer issueNumber) {
    Optional<GithubIssue> maybeIssue = githubIssueRepository.findByIssueNumber(issueNumber);
    if (maybeIssue.isPresent()) {
      log.debug("DB에서 이슈 {} 조회 성공", issueNumber);
      return maybeIssue.get();
    } else {
      try {
        return fetchAndSaveIssue(issueNumber);
      } catch (Exception e) {
        log.error("이슈 {} 파싱 실패: {}", issueNumber, e.getMessage());
        throw new RuntimeException("이슈 " + issueNumber + " 파싱 실패", e);
      }
    }
  }

  private GithubIssue fetchAndSaveIssue(Integer issueNumber) throws IOException {
    String issueUrl = ISSUE_BASE_URL + issueNumber;
    String htmlContent = fetchGithubIssuePageContent(issueUrl);
    String rawTitle = getRawTitleFromGithubIssueHtml(htmlContent);
    String cleanTitle = processIssueTitle(rawTitle);
    GithubIssue newIssue = GithubIssue.builder()
        .issueNumber(issueNumber)
        .rawTitle(rawTitle)
        .cleanTitle(cleanTitle)
        .pageUrl(issueUrl)
        .build();
    log.debug("새로운 이슈 {} 파싱 및 저장", issueNumber);
    return githubIssueRepository.save(newIssue);
  }

  private String fetchGithubIssuePageContent(String url) throws IOException {
    Request request = new Request.Builder().url(url).get().build();
    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("예상치 못한 응답 코드 " + response);
      }
      return response.body().string();
    }
  }

  private String getRawTitleFromGithubIssueHtml(String html) {
    Document doc = Jsoup.parse(html);
    return doc.title();
  }

  private String processIssueTitle(String title) {
    if (title == null || title.isEmpty()) {
      return "";
    }
    // GitHub 메타정보 제거: "· Issue"를 기준으로 앞쪽 부분만 사용
    int issueIndex = title.indexOf("· Issue");
    if (issueIndex != -1) {
      title = title.substring(0, issueIndex).trim();
    }

    // 모든 대괄호([ ... ])와 내부 내용 제거
    title = title.replaceAll("\\[[^\\]]*\\]", "").trim();

    // 앞쪽에 남아 있는 이모지나 기타 기호 제거 (첫 문자가 문자/숫자가 아니면 제거)
    title = title.replaceAll("^[^\\p{L}\\p{N}]+", "").trim();

    // 불필요한 중복 공백 제거
    title = title.replaceAll("\\s{2,}", " ");

    return title;
  }


  /**
   * 컨트롤러 패키지 내의 모든 @ApiChangeLogs 어노테이션을 스캔하여 이슈번호를 TreeSet(정렬 및 중복제거)으로 반환
   */
  private Set<Integer> getDocIssueNumbers() {
    Reflections reflections = new Reflections("com.romrom.romback.domain.controller", new MethodAnnotationsScanner());
    Set<Method> methods = reflections.getMethodsAnnotatedWith(ApiChangeLogs.class);
    Set<Integer> issueNumbers = new TreeSet<>();
    for (Method method : methods) {
      ApiChangeLogs apiChangeLogs = method.getAnnotation(ApiChangeLogs.class);
      for (ApiChangeLog log : apiChangeLogs.value()) {
        if (log.issueNumber() > 0) {
          issueNumbers.add(log.issueNumber());
        }
      }
    }
    log.debug("Docs 이슈 번호 집계: {}", issueNumbers);
    return issueNumbers;
  }

  /**
   * Docs에 기록된 이슈번호들을 연결한 문자열의 SHA-256 해시값을 계산합니다.
   */
  private String calculateDocsIssueHash() {
    Set<Integer> issueNumbers = getDocIssueNumbers();
    String concatenated = issueNumbers.stream().map(String::valueOf).collect(Collectors.joining());
    String hash = CommonUtil.calculateSha256ByStr(concatenated);
    log.debug("Docs 해시 계산 (문자열: {}) -> 해시: {}", concatenated, hash);
    return hash;
  }

  /**
   * Docs 기준 해시와 DB에 저장된 해시가 다르면, Docs에 명시된 이슈번호마다 getOrFetchIssue()를 호출하여
   * DB에 누락된 이슈가 있으면 파싱/저장하고, 이후 해시 레지스트리를 업데이트합니다.
   */
  @Transactional
  public void syncGithubIssues() {
    log.debug("GitHub 이슈 동기화 시작");
    String aggregatedHash = calculateDocsIssueHash();
    String currentHash = getCurrentHash();
    log.info("Docs 기준 해시: {}", aggregatedHash);
    log.info("DB 기준 해시: {}", currentHash);

    if (!aggregatedHash.equals(currentHash)) {
      log.info("해시 불일치 → Docs에 명시된 이슈번호에 대해 DB 조회/파싱 수행");
      for (Integer issueNumber : getDocIssueNumbers()) {
        getOrFetchIssue(issueNumber);
      }
      updateHashRegistry(aggregatedHash);
      log.info("GitHub 이슈 동기화 완료. 새로운 해시: {}", aggregatedHash);
    } else {
      log.debug("해시 일치 → 업데이트 생략");
    }
  }

  private void updateHashRegistry(String newHash) {
    Optional<HashRegistry> registryOpt = hashRegistryRepository.findByHashType(HashType.GITHUB_ISSUES);
    if (registryOpt.isPresent()) {
      HashRegistry registry = registryOpt.get();
      registry.setHashValue(newHash);
      registry.setMessage("업데이트됨: " + LocalDateTime.now());
      hashRegistryRepository.save(registry);
    } else {
      HashRegistry newRegistry = HashRegistry.builder()
          .hashType(HashType.GITHUB_ISSUES)
          .hashValue(newHash)
          .message("생성됨: " + LocalDateTime.now())
          .build();
      hashRegistryRepository.save(newRegistry);
    }
  }

  public String getCurrentHash() {
    return hashRegistryRepository.findByHashType(HashType.GITHUB_ISSUES)
        .map(HashRegistry::getHashValue)
        .orElse("");
  }
}
