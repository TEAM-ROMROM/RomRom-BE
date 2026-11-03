/**
 * 관리자 대시보드 접속·방문자 (#772): 실시간 카드 3개(30초 갱신) + 시간별 동시접속자·일별 방문 회원 차트
 * 데이터: POST /api/admin/analytics/realtime, /analytics/timeseries
 */
(function () {
    // 실시간 카드 갱신 주기 (30초)
    const REALTIME_REFRESH_MILLISECONDS = 30 * 1000;

    async function fetchJson(url, params) {
        const response = await adminFetch.post(url, params || {});
        if (!response.ok) throw new Error(`${url} ${response.status}`);
        return response.json();
    }

    // ===== 실시간 카드 =====

    async function loadTrafficRealtime() {
        try {
            const realtimeResponse = await fetchJson('/api/admin/analytics/realtime');
            const trafficRealtime = realtimeResponse.trafficRealtime || {};
            setNumberText('trafficOnlineMemberCount', trafficRealtime.onlineMemberCount);
            setNumberText('trafficTodayActiveMemberCount', trafficRealtime.todayActiveMemberCount);
            setNumberText('trafficTodayApiCallCount', trafficRealtime.todayApiCallCount);
            document.getElementById('trafficMeasuredAt').textContent =
                trafficRealtime.measuredAt ? `${TimeUtils.formatDateTime(trafficRealtime.measuredAt)} 기준` : '';
        } catch (error) {
            document.getElementById('trafficMeasuredAt').textContent = '실시간 지표를 불러오지 못했습니다';
        }
    }

    function setNumberText(elementId, numberValue) {
        document.getElementById(elementId).textContent = numberValue == null ? '-' : NumberUtils.formatNumber(numberValue);
    }

    // ===== 차트 =====

    async function loadTrafficCharts() {
        const [concurrentUsersResponse, dailyVisitorsResponse] = await Promise.all([
            fetchJson('/api/admin/analytics/timeseries', { activityEventType: 'CONCURRENT_USERS', activityBucketUnit: 'HOUR' }).catch(() => ({})),
            fetchJson('/api/admin/analytics/timeseries', { activityEventType: 'API_CALL', activityAggregation: 'DISTINCT_MEMBERS', activityBucketUnit: 'DAY' }).catch(() => ({})),
        ]);
        renderTimeseriesChart('concurrentUsersChart', concurrentUsersResponse.trafficTimeseries, '동시접속자', 0,
            bucketLabel => `${bucketLabel.slice(11, 13)}시`);
        renderTimeseriesChart('dailyVisitorsChart', dailyVisitorsResponse.trafficTimeseries, '방문 회원', 1,
            bucketLabel => bucketLabel.slice(5).replace('-', '/'));
    }

    // 시계열 1개 > 막대 차트 (라벨은 KST 버킷 라벨을 짧게 변환)
    function renderTimeseriesChart(canvasId, trafficTimeseries, seriesLabel, seriesColorIndex, toShortBucketLabel) {
        const timeseries = trafficTimeseries || { bucketLabels: [], bucketValues: [] };
        AdminCharts.render(canvasId, themeColors => ({
            type: 'bar',
            data: {
                labels: (timeseries.bucketLabels || []).map(toShortBucketLabel),
                datasets: [{
                    label: seriesLabel,
                    data: timeseries.bucketValues || [],
                    backgroundColor: AdminCharts.withAlpha(themeColors.series[seriesColorIndex], 0.8),
                    borderRadius: 3,
                }],
            },
            options: { plugins: { legend: { display: false } }, scales: AdminCharts.integerScales({ maxTicksLimit: 8 }) },
        }));
    }

    loadTrafficRealtime();
    loadTrafficCharts();
    setInterval(loadTrafficRealtime, REALTIME_REFRESH_MILLISECONDS);
    document.getElementById('analyticsRefreshButton').addEventListener('click', () => {
        loadTrafficRealtime();
        loadTrafficCharts();
    });
})();
