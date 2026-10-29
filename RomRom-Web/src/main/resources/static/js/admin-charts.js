/**
 * 관리자 차트 공통 (#827): 테마 색 읽기 > Chart.js 생성·보관 > 테마 전환 시 다시 그림 > 데이터 없음 안내
 * 차트 설정은 "색을 받아 config를 만드는 함수"로 넘겨 테마가 바뀌면 같은 함수로 다시 만든다
 */
(function () {
    // 계열 색 순서 (브랜드 노랑 먼저, 다크에서도 구분되는 채도)
    const SERIES_COLORS = ['#FFC300', '#3B82F6', '#22C55E', '#EF4444', '#8B5CF6', '#14B8A6', '#F97316', '#EC4899', '#64748B', '#A3E635'];

    const chartById = new Map();
    const configBuilderById = new Map();
    const renderOptionsById = new Map();

    /**
     * CSS 변수 > 계산된 색 문자열 (oklch 그대로 canvas에 사용)
     */
    function readCssColor(cssVariableName, alpha) {
        const probe = document.createElement('span');
        probe.style.color = alpha === undefined
            ? `var(${cssVariableName})`
            : `color-mix(in oklch, var(${cssVariableName}) ${Math.round(alpha * 100)}%, transparent)`;
        document.body.appendChild(probe);
        const computedColor = getComputedStyle(probe).color;
        probe.remove();
        return computedColor;
    }

    /**
     * 현재 테마 색 묶음: 글자, 흐린 글자, 격자, 카드 배경, 계열 색
     */
    function readThemeColors() {
        return {
            text: readCssColor('--color-base-content'),
            mutedText: readCssColor('--color-base-content', 0.55),
            grid: readCssColor('--color-base-content', 0.08),
            surface: readCssColor('--color-base-100'),
            series: SERIES_COLORS,
        };
    }

    function applyGlobalDefaults(themeColors) {
        Chart.defaults.font.family = "'Pretendard', system-ui, sans-serif";
        Chart.defaults.font.size = 12;
        Chart.defaults.color = themeColors.mutedText;
        Chart.defaults.borderColor = themeColors.grid;
        Chart.defaults.plugins.legend.labels.boxWidth = 10;
        Chart.defaults.plugins.legend.labels.boxHeight = 10;
        Chart.defaults.plugins.legend.labels.usePointStyle = true;
        Chart.defaults.plugins.tooltip.padding = 10;
        Chart.defaults.plugins.tooltip.cornerRadius = 8;
        Chart.defaults.maintainAspectRatio = false;
        Chart.defaults.animation.duration = 400;
        // 곡선이 값보다 튀어 0 아래로 내려가지 않도록 (드문 정수 데이터)
        Chart.defaults.elements.line.cubicInterpolationMode = 'monotone';
    }

    /**
     * 데이터 없음 안내 토글 (캔버스 부모에 겹침)
     */
    function toggleEmptyState(canvasElement, isEmpty) {
        const chartContainer = canvasElement.parentElement;
        let emptyStateElement = chartContainer.querySelector('.dashboard-chart-empty');
        if (isEmpty && !emptyStateElement) {
            emptyStateElement = document.createElement('div');
            emptyStateElement.className = 'dashboard-chart-empty';
            emptyStateElement.textContent = '이 기간에 데이터가 없습니다';
            chartContainer.appendChild(emptyStateElement);
        } else if (!isEmpty && emptyStateElement) {
            emptyStateElement.remove();
        }
    }

    function hasAnyValue(chartConfig) {
        return (chartConfig.data.datasets || []).some(dataset =>
            (dataset.data || []).some(value => Number(value) > 0));
    }

    /**
     * 차트 생성·교체: configBuilder(themeColors) > Chart.js config
     * renderOptions.isEmptyStateShown: 데이터 없음 안내 표시 여부 (미니 추세선은 false)
     */
    function render(canvasId, configBuilder, renderOptions) {
        const canvasElement = document.getElementById(canvasId);
        if (!canvasElement || typeof Chart === 'undefined') return null;
        configBuilderById.set(canvasId, configBuilder);
        renderOptionsById.set(canvasId, renderOptions || {});

        const themeColors = readThemeColors();
        applyGlobalDefaults(themeColors);
        const chartConfig = configBuilder(themeColors);

        const previousChart = chartById.get(canvasId);
        if (previousChart) previousChart.destroy();
        const chart = new Chart(canvasElement, chartConfig);
        chartById.set(canvasId, chart);
        const isEmptyStateShown = (renderOptions || {}).isEmptyStateShown !== false;
        toggleEmptyState(canvasElement, isEmptyStateShown && !hasAnyValue(chartConfig));
        return chart;
    }

    function rerenderAll() {
        configBuilderById.forEach((configBuilder, canvasId) => render(canvasId, configBuilder, renderOptionsById.get(canvasId)));
    }

    /**
     * 색 + 투명도 > rgba 문자열 (#RRGGBB 전용)
     */
    function withAlpha(hexColor, alpha) {
        const red = parseInt(hexColor.slice(1, 3), 16);
        const green = parseInt(hexColor.slice(3, 5), 16);
        const blue = parseInt(hexColor.slice(5, 7), 16);
        return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
    }

    // ===== 공통 차트 설정 조각 (대시보드 외 화면에서도 재사용) =====

    function sparklineConfig(seriesValues) {
        return themeColors => ({
            type: 'line',
            data: { labels: seriesValues.map((unusedValue, valueIndex) => valueIndex),
                datasets: [{ data: seriesValues, borderColor: themeColors.series[0], backgroundColor: withAlpha(themeColors.series[0], 0.18),
                    fill: true, tension: 0.4, pointRadius: 0, borderWidth: 1.5 }] },
            options: { plugins: { legend: { display: false }, tooltip: { enabled: false } },
                scales: { x: { display: false }, y: { display: false, beginAtZero: true } }, animation: false },
        });
    }

    function doughnutConfig(labeledCounts, themeColors) {
        const safeLabeledCounts = labeledCounts || [];
        return {
            type: 'doughnut',
            data: {
                labels: safeLabeledCounts.map(labeledCount => labeledCount.label),
                datasets: [{ data: safeLabeledCounts.map(labeledCount => labeledCount.count),
                    backgroundColor: safeLabeledCounts.map((unusedValue, colorIndex) => themeColors.series[colorIndex % themeColors.series.length]),
                    borderColor: themeColors.surface, borderWidth: 2 }],
            },
            options: { cutout: '62%', plugins: { legend: { position: 'bottom' } } },
        };
    }

    function horizontalBarConfig(labeledCounts, barColor, datasetLabel) {
        return {
            type: 'bar',
            data: {
                labels: labeledCounts.map(labeledCount => labeledCount.label),
                datasets: [{ label: datasetLabel, data: labeledCounts.map(labeledCount => labeledCount.count),
                    backgroundColor: withAlpha(barColor, 0.8), borderRadius: 4, barThickness: 14 }],
            },
            options: { indexAxis: 'y', plugins: { legend: { display: false } },
                scales: { x: { beginAtZero: true, ticks: { precision: 0 } }, y: { grid: { display: false } } } },
        };
    }

    function integerScales(xTickOptions) {
        return {
            x: { grid: { display: false }, ticks: Object.assign({ maxTicksLimit: 10 }, xTickOptions || {}) },
            y: { beginAtZero: true, ticks: { precision: 0 } },
        };
    }

    // 테마 전환 시 모든 차트 다시 그림 (common.js ThemeUtils가 알림)
    document.addEventListener('admin-theme-change', () => setTimeout(rerenderAll, 0));

    window.AdminCharts = { render, rerenderAll, withAlpha, SERIES_COLORS, sparklineConfig, doughnutConfig, horizontalBarConfig, integerScales };
})();
