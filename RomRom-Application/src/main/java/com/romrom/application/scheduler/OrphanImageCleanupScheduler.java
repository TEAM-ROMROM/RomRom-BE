package com.romrom.application.scheduler;

import com.romrom.item.repository.postgres.ItemImageRepository;
import com.romrom.storage.properties.MinioProperties;
import com.romrom.storage.service.MinIoFileServiceImpl;
import com.romrom.storage.util.MinioUtil;
import io.minio.messages.Item;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 고아(Orphan) 이미지 자동 정리 스케줄러
 *
 * MinIO 저장소에 존재하지만 DB(ItemImage)에 참조되지 않는 파일을 주기적으로 탐지 및 삭제합니다.
 * - 실행 주기: 매주 일요일 새벽 3시
 * - 삭제 대상: 생성일이 7일 이상 경과한 고아 이미지
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrphanImageCleanupScheduler {

  private static final int ORPHAN_THRESHOLD_DAYS = 7;

  private final MinioUtil minioUtil;
  private final MinioProperties minioProperties;
  private final MinIoFileServiceImpl minIoFileService;
  private final ItemImageRepository itemImageRepository;

  @Scheduled(cron = "0 0 3 * * SUN")
  public void cleanupOrphanImages() {
    log.info("고아 이미지 정리 스케줄러 시작");

    try {
      // 1. MinIO 저장소의 전체 이미지 파일 목록 조회
      String bucketName = minioProperties.getBucket();
      List<Item> minioObjectList = minioUtil.listObjects(bucketName, MinioUtil.PUBLIC_IMAGES_PATH, true);
      log.info("MinIO 저장소 파일 수: {}", minioObjectList.size());

      // 2. DB에 저장된 전체 이미지 URL을 Set으로 조회
      Set<String> dbImageUrlSet = new HashSet<>(itemImageRepository.findAllImageUrls());

      // 3. 고아 이미지 탐지 및 삭제
      ZonedDateTime orphanThreshold = ZonedDateTime.now().minusDays(ORPHAN_THRESHOLD_DAYS);
      int orphanCount = 0;
      int deletedCount = 0;

      for (Item minioObject : minioObjectList) {
        // 디렉토리(prefix) 객체는 건너뜀
        if (minioObject.isDir()) {
          continue;
        }

        // 7일 미만 파일은 건너뜀
        ZonedDateTime objectLastModified = minioObject.lastModified();
        if (objectLastModified.isAfter(orphanThreshold)) {
          continue;
        }

        // MinIO 파일의 URL을 빌드하여 DB에 존재하는지 확인
        String objectName = minioObject.objectName();
        String imageUrl = minIoFileService.buildImageUrl(objectName);

        if (!dbImageUrlSet.contains(imageUrl)) {
          orphanCount++;
          try {
            minIoFileService.deleteFile(objectName);
            deletedCount++;
          } catch (Exception deleteException) {
            log.warn("고아 이미지 삭제 실패: objectName={}, error={}", objectName, deleteException.getMessage());
          }
        }
      }

      log.info("고아 이미지 정리 스케줄러 완료: 전체 파일={}, 고아 이미지={}, 삭제 성공={}", minioObjectList.size(), orphanCount, deletedCount);
    } catch (Exception schedulerException) {
      log.error("고아 이미지 정리 스케줄러 실행 중 오류 발생: {}", schedulerException.getMessage(), schedulerException);
    }
  }
}
