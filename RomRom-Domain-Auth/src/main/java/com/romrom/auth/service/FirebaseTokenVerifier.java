package com.romrom.auth.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FirebaseTokenVerifier {

  /**
   * Firebase ID Token 검증
   * FirebaseApp은 RomRom-Web의 FirebaseConfig에서 초기화됨
   *
   * @param idToken 클라이언트로부터 전달받은 Firebase ID Token
   * @return 검증된 FirebaseToken (uid, email, picture 등 포함)
   */
  public FirebaseToken verify(String idToken) {
    try {
      FirebaseToken firebaseToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
      log.debug("Firebase 토큰 검증 성공: uid={}, email={}", firebaseToken.getUid(), firebaseToken.getEmail());
      return firebaseToken;
    } catch (FirebaseAuthException e) {
      log.error("Firebase 토큰 검증 실패: code={}, message={}", e.getAuthErrorCode(), e.getMessage());
      // 만료된 토큰 처리
      if (e.getAuthErrorCode() != null &&
          e.getAuthErrorCode().name().contains("EXPIRED")) {
        throw new CustomException(ErrorCode.EXPIRED_FIREBASE_TOKEN);
      }
      throw new CustomException(ErrorCode.INVALID_FIREBASE_TOKEN);
    }
  }
}
