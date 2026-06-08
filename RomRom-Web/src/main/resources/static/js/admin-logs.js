/**
 * 관리자 로그 관리 화면 전용 JS.
 * 브라우저 부하 최소화: DOM 라인 캡, 탭 활성 시에만 SSE, requestAnimationFrame 배칭.
 */
const LogAdmin = (function () {
  const MAX_LIVE_DOM_LINES = 500;
  const ALL_TAB_IDS = ['query', 'errors', 'live', 'files'];

  let activeTab = 'query';
  let liveEventSource = null;
  let pendingLiveLines = [];
  let isRafScheduled = false;

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
    const errorParams = { logErrorWithinMinutes: document.getElementById('errorWithinMinutes').value };
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

  function connectLive() {
    if (liveEventSource) return;
    const statusEl = document.getElementById('liveStatus');
    liveEventSource = new EventSource('/api/admin/logs/stream', { withCredentials: true });
    liveEventSource.onopen = function () {
      statusEl.textContent = '연결됨';
      statusEl.className = 'badge badge-success';
    };
    liveEventSource.onmessage = function (messageEvent) {
      pendingLiveLines.push(messageEvent.data);
      scheduleLiveFlush();
    };
    liveEventSource.onerror = function () {
      statusEl.textContent = '연결 끊김';
      statusEl.className = 'badge badge-error';
    };
  }

  function disconnectLive() {
    if (liveEventSource) {
      liveEventSource.close();
      liveEventSource = null;
      const statusEl = document.getElementById('liveStatus');
      statusEl.textContent = '연결 안 됨';
      statusEl.className = 'badge badge-ghost';
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
      try {
        const parsedEvent = JSON.parse(rawData);
        parsedLevel = parsedEvent.level || '';
        displayText = (parsedEvent.timestamp || '') + ' ' + parsedLevel + ' ' +
          (parsedEvent.loggerName || '') + ' - ' + (parsedEvent.message || '');
      } catch (parseError) {
        // connected/heartbeat 등 평문은 그대로 표시
      }
      const lineDiv = document.createElement('div');
      lineDiv.className = levelClass(parsedLevel);
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
    viewGz: viewGz
  };
})();
