/**
 * 관리자 대시보드 (#827): 분석·동접 조회 > 처리 필요 > 핵심 지표 카드 > 차트 11개 (최근 목록은 admin-dashboard-recent.js)
 * 데이터: POST /api/admin/dashboard/analytics, /online-stats
 */
(function () {
    const PERIOD_LABELS = { 7: '최근 7일', 30: '최근 30일', 90: '최근 90일' };
    const GROWTH_SERIES = [
        { key: 'newMembers', label: '신규 회원' },
        { key: 'newItems', label: '신규 물품' },
        { key: 'tradeRequests', label: '거래 요청' },
        { key: 'completedTrades', label: '거래 완료' },
    ];

    let selectedPeriodDays = 30;
    let latestOnlineStats = { onlineMemberCount: null, chatOnlineMemberCount: null };

    // ===== 조회 =====

    async function fetchJson(url, params) {
        const response = await adminFetch.post(url, params || {});
        if (!response.ok) throw new Error(`${url} ${response.status}`);
        return response.json();
    }

    async function loadAnalytics() {
        setRefreshing(true);
        try {
            const [analyticsResponse, onlineStatsResponse] = await Promise.all([
                fetchJson('/api/admin/dashboard/analytics', { analyticsPeriodDays: selectedPeriodDays }),
                fetchJson('/api/admin/dashboard/online-stats').catch(() => ({})),
            ]);
            latestOnlineStats = onlineStatsResponse || {};
            renderDashboard(analyticsResponse.dashboardAnalytics);
            document.getElementById('analyticsErrorToast').classList.add('hidden');
        } catch (analyticsError) {
            console.error('대시보드 분석 로드 실패:', analyticsError);
            document.getElementById('analyticsErrorToast').classList.remove('hidden');
        } finally {
            setRefreshing(false);
        }
    }

    function setRefreshing(isRefreshing) {
        const refreshButton = document.getElementById('analyticsRefreshButton');
        refreshButton.disabled = isRefreshing;
        refreshButton.querySelector('[data-lucide], svg')?.classList.toggle('animate-spin', isRefreshing);
        if (!isRefreshing) {
            document.getElementById('analyticsUpdatedAt').textContent =
                new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) + ' 갱신';
        }
    }

    // ===== 화면 조립 =====

    function renderDashboard(analytics) {
        if (!analytics) return;
        document.getElementById('analyticsPeriodLabel').textContent =
            `${PERIOD_LABELS[analytics.periodDays] || `최근 ${analytics.periodDays}일`} (${analytics.startDate} ~ ${analytics.endDate})`;
        renderAttention(analytics);
        renderKpiCards(analytics);
        renderGrowthCharts(analytics);
        renderActivityCharts(analytics);
        renderCompositionCharts(analytics);
        renderSafetyCharts(analytics);
        lucide.createIcons();
    }

    function renderAttention(analytics) {
        document.getElementById('attentionPendingReports').textContent = formatNumber(analytics.pendingReports);
        document.getElementById('attentionCompleteRequested').textContent = formatNumber(analytics.completeRequestedTrades);
        document.getElementById('attentionNewReviews').textContent = formatNumber(metricValue(analytics, 'newReviews'));
    }

    /**
     * 핵심 지표 8개: 값 · 보조 설명 · 전기간 대비 · 미니 추세선
     */
    function renderKpiCards(analytics) {
        const metrics = analytics.metrics || {};
        const series = analytics.dailySeries || {};
        const monthlyActiveMembers = analytics.monthlyActiveMembers || 0;
        const stickinessPercent = monthlyActiveMembers > 0
            ? Math.round((analytics.dailyActiveMembers || 0) * 100 / monthlyActiveMembers) : null;
        const completionPercent = metricValue(analytics, 'tradeRequests') > 0
            ? Math.round(metricValue(analytics, 'completedTrades') * 100 / metricValue(analytics, 'tradeRequests')) : null;

        const kpiCards = [
            { icon: 'users', tone: 'text-primary', title: '전체 회원', value: analytics.totalMembers,
              sub: `기간 신규 ${formatNumber(metricValue(analytics, 'newMembers'))}명`, metric: metrics.newMembers, spark: series.newMembers, href: '/admin/members' },
            { icon: 'activity', tone: 'text-success', title: '활동 회원 (24시간)', value: analytics.dailyActiveMembers,
              sub: `주간 ${formatNumber(analytics.weeklyActiveMembers)} · 월간 ${formatNumber(analytics.monthlyActiveMembers)}`
                  + (stickinessPercent !== null ? ` · 고착도 ${stickinessPercent}%` : ''), metric: null, spark: series.loginMembers },
            { icon: 'radio', tone: 'text-info', title: '지금 접속', value: latestOnlineStats.onlineMemberCount,
              sub: `채팅 중 ${formatNumber(latestOnlineStats.chatOnlineMemberCount)}명 · 최근 5분`, metric: null, spark: null, isLive: true },
            { icon: 'package', tone: 'text-primary', title: '신규 물품', value: metricValue(analytics, 'newItems'),
              sub: `활성 물품 ${formatNumber(analytics.activeItems)}개`, metric: metrics.newItems, spark: series.newItems, href: '/admin/items' },
            { icon: 'repeat', tone: 'text-info', title: '거래 요청', value: metricValue(analytics, 'tradeRequests'),
              sub: '기간 내 생성', metric: metrics.tradeRequests, spark: series.tradeRequests, href: '/admin/trades' },
            { icon: 'circle-check', tone: 'text-success', title: '거래 완료', value: metricValue(analytics, 'completedTrades'),
              sub: completionPercent !== null ? `요청 대비 ${completionPercent}% (근사)` : '완료 처리일 기준 (근사)',
              metric: metrics.completedTrades, spark: series.completedTrades },
            { icon: 'messages-square', tone: 'text-secondary', title: '채팅 메시지', value: metricValue(analytics, 'chatMessages'),
              sub: '기간 내 전송', metric: metrics.chatMessages, spark: series.chatMessages },
            { icon: 'eye', tone: 'text-warning', title: '물품 조회', value: metricValue(analytics, 'itemViews'),
              sub: '기간 내 상세 조회', metric: metrics.itemViews, spark: series.itemViews },
        ];

        const kpiGrid = document.getElementById('kpiGrid');
        kpiGrid.innerHTML = kpiCards.map((kpiCard, kpiIndex) => `
            <${kpiCard.href ? `a href="${kpiCard.href}"` : 'div'} class="card bg-base-100 dashboard-kpi ${kpiCard.href ? 'hover:-translate-y-0.5 transition' : ''}">
                <div class="card-body p-4 gap-1">
                    <div class="flex items-center justify-between">
                        <span class="text-xs text-base-content/60 flex items-center gap-1.5">
                            <i data-lucide="${kpiCard.icon}" class="size-4 ${kpiCard.tone}"></i>${escapeHtml(kpiCard.title)}
                        </span>
                        ${kpiCard.isLive ? '<span class="status status-success animate-pulse" title="실시간"></span>' : renderDelta(kpiCard.metric)}
                    </div>
                    <div class="text-3xl font-extrabold tabular-nums tracking-tight">${formatNumber(kpiCard.value)}</div>
                    <div class="text-xs text-base-content/55 truncate" title="${escapeHtml(kpiCard.sub)}">${escapeHtml(kpiCard.sub)}</div>
                    ${kpiCard.spark ? `<div class="kpi-sparkline mt-1"><canvas id="kpiSpark${kpiIndex}"></canvas></div>` : '<div class="kpi-sparkline mt-1"></div>'}
                </div>
            </${kpiCard.href ? 'a' : 'div'}>`).join('');

        kpiCards.forEach((kpiCard, kpiIndex) => {
            if (!kpiCard.spark) return;
            AdminCharts.render(`kpiSpark${kpiIndex}`, AdminCharts.sparklineConfig(kpiCard.spark), { isEmptyStateShown: false });
        });
    }

    /**
     * 전기간 대비 표시: ▲ 증가(초록) · ▼ 감소(빨강) · 직전 0이면 증가 건수(▲ +N) 또는 "-"
     */
    function renderDelta(metricSummary) {
        if (!metricSummary) return '';
        if (metricSummary.changeRate === null || metricSummary.changeRate === undefined) {
            const currentValue = metricSummary.currentValue || 0;
            return currentValue > 0
                ? `<span class="text-xs font-semibold tabular-nums dashboard-delta-up" title="직전 기간 0">▲ +${formatNumber(currentValue)}</span>`
                : '<span class="text-xs dashboard-delta-flat" title="현재·직전 모두 0">-</span>';
        }
        const changeRate = metricSummary.changeRate;
        const toneClass = changeRate > 0 ? 'dashboard-delta-up' : changeRate < 0 ? 'dashboard-delta-down' : 'dashboard-delta-flat';
        const arrow = changeRate > 0 ? '▲' : changeRate < 0 ? '▼' : '–';
        return `<span class="text-xs font-semibold tabular-nums ${toneClass}"
                      title="직전 기간 ${formatNumber(metricSummary.previousValue)}">${arrow} ${Math.abs(changeRate)}%</span>`;
    }

    // ===== 차트 =====

    function renderGrowthCharts(analytics) {
        const dateLabels = toShortDateLabels(analytics.dates);
        AdminCharts.render('growthChart', themeColors => ({
            type: 'line',
            data: {
                labels: dateLabels,
                datasets: GROWTH_SERIES.map((growthSeries, seriesIndex) => ({
                    label: growthSeries.label,
                    data: (analytics.dailySeries || {})[growthSeries.key] || [],
                    borderColor: themeColors.series[seriesIndex],
                    backgroundColor: AdminCharts.withAlpha(themeColors.series[seriesIndex], seriesIndex === 0 ? 0.15 : 0),
                    fill: seriesIndex === 0,
                    tension: 0.35,
                    pointRadius: 0,
                    pointHoverRadius: 4,
                    borderWidth: 2,
                })),
            },
            options: { interaction: { mode: 'index', intersect: false }, scales: AdminCharts.integerScales(), plugins: { legend: { position: 'bottom' } } },
        }));
        AdminCharts.render('tradeStatusChart', themeColors => AdminCharts.doughnutConfig(analytics.tradeStatusCounts, themeColors));
    }

    function renderActivityCharts(analytics) {
        const dailySeries = analytics.dailySeries || {};
        AdminCharts.render('activityChart', themeColors => ({
            type: 'bar',
            data: {
                labels: toShortDateLabels(analytics.dates),
                datasets: [
                    { type: 'bar', label: '로그인 회원', data: dailySeries.loginMembers || [], backgroundColor: AdminCharts.withAlpha(themeColors.series[0], 0.75), borderRadius: 4, yAxisID: 'y' },
                    { type: 'line', label: '채팅 메시지', data: dailySeries.chatMessages || [], borderColor: themeColors.series[1], tension: 0.35, pointRadius: 0, borderWidth: 2, yAxisID: 'y1' },
                    { type: 'line', label: '물품 조회', data: dailySeries.itemViews || [], borderColor: themeColors.series[2], tension: 0.35, pointRadius: 0, borderWidth: 2, borderDash: [4, 3], yAxisID: 'y1' },
                ],
            },
            options: {
                interaction: { mode: 'index', intersect: false },
                plugins: { legend: { position: 'bottom' } },
                scales: {
                    x: { grid: { display: false } },
                    y: { beginAtZero: true, ticks: { precision: 0 }, title: { display: true, text: '회원' } },
                    y1: { beginAtZero: true, position: 'right', grid: { drawOnChartArea: false }, ticks: { precision: 0 }, title: { display: true, text: '건' } },
                },
            },
        }));
        AdminCharts.render('hourlyChart', themeColors => ({
            type: 'bar',
            data: {
                labels: Array.from({ length: 24 }, (unusedValue, hour) => `${hour}시`),
                datasets: [{ label: '로그인', data: analytics.loginHourlyCounts || [], backgroundColor: AdminCharts.withAlpha(themeColors.series[1], 0.8), borderRadius: 3 }],
            },
            options: { plugins: { legend: { display: false } }, scales: AdminCharts.integerScales({ maxTicksLimit: 8 }) },
        }));
    }

    function renderCompositionCharts(analytics) {
        AdminCharts.render('categoryChart', themeColors => AdminCharts.horizontalBarConfig((analytics.itemCategoryCounts || []).slice(0, 10), themeColors.series[0], '활성 물품'));
        AdminCharts.render('regionChart', themeColors => AdminCharts.horizontalBarConfig(analytics.regionCounts || [], themeColors.series[1], '회원'));
        AdminCharts.render('socialPlatformChart', themeColors => AdminCharts.doughnutConfig(analytics.socialPlatformCounts, themeColors));
        AdminCharts.render('deviceTypeChart', themeColors => AdminCharts.doughnutConfig(analytics.deviceTypeCounts, themeColors));
    }

    function renderSafetyCharts(analytics) {
        AdminCharts.render('reportStatusChart', themeColors => AdminCharts.doughnutConfig(analytics.reportStatusCounts, themeColors));
        AdminCharts.render('reviewRatingChart', themeColors => ({
            type: 'bar',
            data: {
                labels: (analytics.reviewRatingCounts || []).map(labeledCount => labeledCount.label),
                datasets: [{ label: '후기', data: (analytics.reviewRatingCounts || []).map(labeledCount => labeledCount.count),
                    backgroundColor: themeColors.series.slice(0, 3).map(seriesColor => AdminCharts.withAlpha(seriesColor, 0.8)), borderRadius: 6 }],
            },
            options: { plugins: { legend: { display: false } }, scales: AdminCharts.integerScales() },
        }));
        const dailySeries = analytics.dailySeries || {};
        const aiCalls = dailySeries.aiCalls || [];
        const aiFailureRates = aiCalls.map((callCount, dayIndex) =>
            callCount > 0 ? Math.round(((dailySeries.aiFailures || [])[dayIndex] || 0) * 1000 / callCount) / 10 : null);
        AdminCharts.render('aiUsageChart', themeColors => ({
            type: 'bar',
            data: {
                labels: toShortDateLabels(analytics.dates),
                datasets: [
                    { type: 'bar', label: 'AI 호출', data: aiCalls, backgroundColor: AdminCharts.withAlpha(themeColors.series[4], 0.7), borderRadius: 3, yAxisID: 'y' },
                    { type: 'line', label: '실패율 %', data: aiFailureRates, borderColor: themeColors.series[3], pointRadius: 0, borderWidth: 2, spanGaps: true, yAxisID: 'y1' },
                ],
            },
            options: {
                interaction: { mode: 'index', intersect: false },
                plugins: { legend: { position: 'bottom' } },
                scales: {
                    x: { grid: { display: false }, ticks: { maxTicksLimit: 7 } },
                    y: { beginAtZero: true, ticks: { precision: 0 } },
                    y1: { beginAtZero: true, max: 100, position: 'right', grid: { drawOnChartArea: false }, ticks: { callback: value => `${value}%` } },
                },
            },
        }));
    }

    // ===== 값 정리 =====

    function metricValue(analytics, metricKey) {
        return ((analytics.metrics || {})[metricKey] || {}).currentValue || 0;
    }

    function formatNumber(numberValue) {
        return numberValue === null || numberValue === undefined ? '-' : Number(numberValue).toLocaleString('ko-KR');
    }

    /**
     * yyyy-MM-dd > M/d
     */
    function toShortDateLabels(dates) {
        return (dates || []).map(date => {
            const [, month, day] = date.split('-');
            return `${Number(month)}/${Number(day)}`;
        });
    }

    // ===== 시작 =====

    document.querySelectorAll('.analytics-period').forEach(periodButton => {
        periodButton.addEventListener('click', () => {
            document.querySelectorAll('.analytics-period').forEach(button => button.classList.remove('btn-active'));
            periodButton.classList.add('btn-active');
            selectedPeriodDays = Number(periodButton.dataset.periodDays);
            loadAnalytics();
        });
    });
    document.getElementById('analyticsRefreshButton').addEventListener('click', loadAnalytics);

    loadAnalytics();
})();
