package com.romrom.common.init;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.logServerInitDuration;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RomRomInitiation implements ApplicationRunner {

	@Override
	public void run(ApplicationArguments args) throws Exception {
		lineLog("SERVER START");
		lineLog("데이터 초기화 시작");
		LocalDateTime startTime = LocalDateTime.now();

		logServerInitDuration(startTime);
		log.info("서버 데이터 초기화 및 업데이트 완료");
	}
}
