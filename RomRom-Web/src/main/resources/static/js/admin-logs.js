/**
 * 관리자 로그 관리 화면 전용 JS.
 * 브라우저 부하 최소화: DOM 라인 캡, 탭 활성 시에만 WebSocket, requestAnimationFrame 배칭.
 */
const LogAdmin = (function () {
  const MAX_LIVE_DOM_LINES = 500;
  const ALL_TAB_IDS = ['query', 'errors', 'live', 'files'];

  let activeTab = 'query';
  let liveWebSocket = null;
  let pendingLiveLines = [];
  let isRafScheduled = false;
  let liveReconnectTimer = null;
  let isLiveManuallyDisconnected = false;
  // AOP 상세 로그 토글 상태 — 재연결 시 서버에 다시 통보하기 위해 클라이언트가 기억
  let isAopLogEnabled = false;

  function capitalize(text) {
    return text.charAt(0).toUpperCase() + text.slice(1);
  }

  function switchTab(tabName) {
    activeTab = tabName;
    ALL_TAB_IDS.forEach(function (tabId) {
      const tabEl = document.getElementById('tab' + capitalize(tabId));
      const panelEl = document.getElementById('panel' + capitalize(tabId));
      if (tabEl) tabEl.classList.toggle('tab-active', tabId === tabName);
      if (panelEl) panelEl.classList.toggle('hidden', tabId !== tabName);
    });
    if (tabName === 'live') {
      connectLive();
    } else {
      disconnectLive();
    }
    if (tabName === 'files') runFiles();
  }

  function escapeHtml(text) {
    const holder = document.createElement('div');
    holder.textContent = text == null ? '' : text;
    return holder.innerHTML;
  }

  function levelClass(logLevel) {
    if (logLevel === 'ERROR') return 'text-error';
    if (logLevel === 'WARN') return 'text-warning';
    return '';
  }

  function levelClassFromRawLine(rawLine) {
    if (rawLine.indexOf(' ERROR ') !== -1) return 'text-error';
    if (rawLine.indexOf(' WARN ') !== -1) return 'text-warning';
    return '';
  }

  async function runQuery() {
    const queryParams = {
      logLineCount: document.getElementById('queryLineCount').value,
      logLevelFilter: document.getElementById('queryLevel').value,
      logKeyword: document.getElementById('queryKeyword').value
    };
    const response = await adminFetch.post('/api/admin/logs/query', queryParams);
    const responseData = await response.json();
    const outputEl = document.getElementById('queryOutput');
    outputEl.innerHTML = (responseData.logLines || [])
      .map(function (logLine) {
        return '<div class="' + levelClassFromRawLine(logLine) + '">' + escapeHtml(logLine) + '</div>';
      })
      .join('');
  }

  async function runErrors() {
    const errorParams = {
      logErrorWithinMinutes: document.getElementById('errorWithinMinutes').value,
      logErrorSortBy: document.getElementById('errorSortBy').value
    };
    const response = await adminFetch.post('/api/admin/logs/errors', errorParams);
    const responseData = await response.json();
    const errorTableBody = document.getElementById('errorTableBody');
    errorTableBody.innerHTML = (responseData.logErrorSummaries || []).map(function (errorSummary) {
      return '<tr class="cursor-pointer hover" onclick="LogAdmin.jumpToKeyword(\'' +
        escapeHtml(errorSummary.exceptionClassName) + '\')">' +
        '<td>' + escapeHtml(errorSummary.exceptionClassName) + '</td>' +
        '<td>' + errorSummary.occurrenceCount + '</td>' +
        '<td>' + (errorSummary.lastOccurredAt || '') + '</td>' +
        '<td class="truncate max-w-md">' + escapeHtml(errorSummary.representativeMessage || '') + '</td>' +
        '</tr>';
    }).join('');
  }

  function jumpToKeyword(keyword) {
    switchTab('query');
    document.getElementById('queryKeyword').value = keyword;
    runQuery();
  }

  function setLiveStatus(statusText, badgeClass) {
    const statusEl = document.getElementById('liveStatus');
    if (statusEl) {
      statusEl.textContent = statusText;
      statusEl.className = 'badge ' + badgeClass;
    }
  }

  function connectLive() {
    if (liveWebSocket) return;
    isLiveManuallyDisconnected = false;
    setLiveStatus('연결 중…', 'badge-warning');

    // 현재 페이지 스킴에 맞춰 ws/wss 선택 (https → wss). 쿠키 accessToken은 핸드셰이크 시 자동 전송됨
    const wsScheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const wsUrl = wsScheme + '://' + window.location.host + '/ws/admin-logs';
    liveWebSocket = new WebSocket(wsUrl);

    liveWebSocket.onopen = function () {
      setLiveStatus('연결됨', 'badge-success');
      // 재연결 시 이전 AOP 토글 상태를 서버에 다시 통보 (서버는 세션별로 상태를 들고 있어 새 세션엔 기본 OFF)
      if (isAopLogEnabled) {
        sendAopToggle(true);
      }
    };

    // 서버가 보내는 모든 메시지(connected 알림 + 로그 데이터)는 텍스트 프레임으로 수신
    liveWebSocket.onmessage = function (messageEvent) {
      pendingLiveLines.push(messageEvent.data);
      scheduleLiveFlush();
    };

    // 연결이 닫히면(서버 종료/네트워크 단절) onclose 발생 → 직접 재연결.
    // WebSocket은 자동 재연결이 없으므로 수동으로 1초 후 재시도한다.
    liveWebSocket.onclose = function () {
      liveWebSocket = null;
      if (isLiveManuallyDisconnected) return;
      setLiveStatus('재연결 중…', 'badge-warning');
      scheduleLiveReconnect();
    };

    // 오류 발생 시 onclose가 이어서 호출되므로 별도 정리는 onclose에 위임
    liveWebSocket.onerror = function () {
      setLiveStatus('재연결 중…', 'badge-warning');
    };
  }

  function scheduleLiveReconnect() {
    if (liveReconnectTimer) return;
    liveReconnectTimer = setTimeout(function () {
      liveReconnectTimer = null;
      // 실시간 탭이 활성이고 화면이 보일 때만 재연결
      if (activeTab === 'live' && !document.hidden && !isLiveManuallyDisconnected) {
        connectLive();
      }
    }, 1000);
  }

  function disconnectLive() {
    isLiveManuallyDisconnected = true;
    if (liveReconnectTimer) {
      clearTimeout(liveReconnectTimer);
      liveReconnectTimer = null;
    }
    if (liveWebSocket) {
      liveWebSocket.close();
      liveWebSocket = null;
    }
    setLiveStatus('연결 안 됨', 'badge-ghost');
  }

  // AOP 상세 로그 토글 — 체크박스 onchange에서 호출. 상태를 기억하고 서버에 통보
  function toggleAop(enabled) {
    isAopLogEnabled = enabled;
    sendAopToggle(enabled);
  }

  // 현재 WebSocket으로 토글 명령 전송 (연결 안 된 경우 onopen에서 재전송됨)
  function sendAopToggle(enabled) {
    if (liveWebSocket && liveWebSocket.readyState === WebSocket.OPEN) {
      liveWebSocket.send(JSON.stringify({ action: 'toggleAop', enabled: enabled }));
    }
  }

  function scheduleLiveFlush() {
    if (isRafScheduled) return;
    isRafScheduled = true;
    requestAnimationFrame(flushLiveLines);
  }

  function flushLiveLines() {
    isRafScheduled = false;
    const outputEl = document.getElementById('liveOutput');
    const fragment = document.createDocumentFragment();
    pendingLiveLines.forEach(function (rawData) {
      let displayText = rawData;
      let parsedLevel = '';
      let isAopSource = false;
      try {
        const parsedEvent = JSON.parse(rawData);
        parsedLevel = parsedEvent.level || '';
        isAopSource = parsedEvent.source === 'AOP';
        displayText = (parsedEvent.timestamp || '') + ' ' + parsedLevel + ' ' +
          (parsedEvent.loggerName || '') + ' - ' + (parsedEvent.message || '');
      } catch (parseError) {
        // connected 알림 등 JSON 파싱 실패 메시지는 원문 그대로 표시
      }
      const lineDiv = document.createElement('div');
      // AOP 상세 로그는 일반 로그와 구분되게 표시 (들여쓰기 + 강조색)
      if (isAopSource) {
        lineDiv.className = 'text-info pl-3 border-l-2 border-info/40';
        displayText = '⟫ ' + displayText;
      } else {
        lineDiv.className = levelClass(parsedLevel);
      }
      lineDiv.textContent = displayText;
      fragment.appendChild(lineDiv);
    });
    pendingLiveLines = [];
    outputEl.appendChild(fragment);
    while (outputEl.childNodes.length > MAX_LIVE_DOM_LINES) {
      outputEl.removeChild(outputEl.firstChild);
    }
    if (document.getElementById('liveAutoScroll').checked) {
      outputEl.scrollTop = outputEl.scrollHeight;
    }
  }

  function clearLive() {
    document.getElementById('liveOutput').innerHTML = '';
  }

  function formatBytes(byteCount) {
    if (!byteCount) return '0 B';
    const sizeUnits = ['B', 'KB', 'MB', 'GB', 'TB'];
    let scaledValue = byteCount, unitIndex = 0;
    while (scaledValue >= 1024 && unitIndex < sizeUnits.length - 1) {
      scaledValue /= 1024;
      unitIndex++;
    }
    return scaledValue.toFixed(1) + ' ' + sizeUnits[unitIndex];
  }

  async function runFiles() {
    const response = await adminFetch.post('/api/admin/logs/files', {});
    const responseData = await response.json();
    document.getElementById('diskStatus').textContent =
      '로그 총 용량: ' + formatBytes(responseData.logTotalSizeBytes) +
      ' / 파일 수: ' + (responseData.logFileCount || 0) +
      ' / 디스크 여유: ' + formatBytes(responseData.diskFreeBytes) +
      ' (전체 ' + formatBytes(responseData.diskTotalBytes) + ')';
    const fileTableBody = document.getElementById('fileTableBody');
    fileTableBody.innerHTML = (responseData.logFiles || []).map(function (fileInfo) {
      const isGzFile = fileInfo.fileName.endsWith('.gz');
      const gzButton = isGzFile
        ? '<button class="btn btn-xs" onclick="LogAdmin.viewGz(\'' + escapeHtml(fileInfo.fileName) + '\')">조회</button>'
        : '';
      return '<tr>' +
        '<td>' + escapeHtml(fileInfo.fileName) + '</td>' +
        '<td>' + formatBytes(fileInfo.fileSizeBytes) + '</td>' +
        '<td>' + (fileInfo.lastModifiedAt || '') + '</td>' +
        '<td class="flex gap-1">' +
        '<a class="btn btn-xs btn-primary" href="/api/admin/logs/download-file?fileName=' +
        encodeURIComponent(fileInfo.fileName) + '">다운</a>' + gzButton +
        '</td></tr>';
    }).join('');
  }

  async function viewGz(fileName) {
    const gzParams = { logFileName: fileName, logLineCount: 500 };
    const response = await adminFetch.post('/api/admin/logs/gz-query', gzParams);
    const responseData = await response.json();
    const joinedLines = (responseData.logLines || []).join('\n');
    const previewText = joinedLines.substring(0, 4000) + (joinedLines.length > 4000 ? '\n... (생략)' : '');
    alert(fileName + '\n\n' + previewText);
  }

  function downloadRange(range) {
    window.location.href = '/api/admin/logs/download?range=' + range;
  }

  function init() {
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) {
        disconnectLive();
      } else if (activeTab === 'live') {
        connectLive();
      }
    });
    runQuery();
  }

  return {
    init: init, switchTab: switchTab, runQuery: runQuery, runErrors: runErrors,
    jumpToKeyword: jumpToKeyword, clearLive: clearLive, downloadRange: downloadRange,
    viewGz: viewGz, toggleAop: toggleAop
  };
})();
