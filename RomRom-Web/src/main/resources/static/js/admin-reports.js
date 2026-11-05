/**
 * 관리자 신고 관리 > 목록: 공통 상태(탭·페이지·캐시·상태 배지), 탭 전환, 통계·대기 배지, 목록 조회·렌더링(물품·회원), 페이지, CSV 내보내기
 * 상세·처리는 admin-reports-detail.js, 채팅 신고 탭은 admin-reports-chat.js
 */

let currentTab = 'item';
let currentPage = 0;
let currentPageSize = 20;
let reportsCache = {};

const statusMap = {
    'PENDING':    { text: '대기중', cls: 'badge-warning' },
    'PROCESSING': { text: '처리중', cls: 'badge-info' },
    'COMPLETED':  { text: '완료',   cls: 'badge-success' },
    'REJECTED':   { text: '반려',   cls: 'badge-error' }
};

// 회원 닉네임 > 회원 상세 링크 (행 클릭 전파 차단)
function buildMemberLinkHtml(member) {
    if (!member) return '-';
    const nicknameText = escapeHtml(member.nickname || '-');
    if (!member.memberId) return nicknameText;
    return `<a class="link link-hover" href="/admin/members/${escapeHtml(member.memberId)}" onclick="event.stopPropagation()">${nicknameText}</a>`;
}

// 물품명 > 물품 상세 링크 (행 클릭 전파 차단)
function buildItemLinkHtml(item) {
    if (!item) return '-';
    const itemNameText = escapeHtml(item.itemName || '-');
    if (!item.itemId) return itemNameText;
    return `<a class="link link-hover" href="/admin/items/${escapeHtml(item.itemId)}" onclick="event.stopPropagation()">${itemNameText}</a>`;
}

// URL로 탭을 지정했는지 (아니면 대기 신고가 있는 탭으로 자동 이동)
let hasTabFromUrl = false;

document.addEventListener('DOMContentLoaded', function() {
    // URL 필터 반영 (대시보드 바로가기: reportStatus, tab=item|member|chatMessage)
    const urlParams = new URLSearchParams(location.search);
    const reportStatusParam = urlParams.get('reportStatus');
    if (reportStatusParam && statusMap[reportStatusParam]) {
        document.getElementById('statusFilter').value = reportStatusParam;
    }
    const tabParam = (urlParams.get('tab') || urlParams.get('reportType') || '').toLowerCase();
    const tabByUrlParam = { item: 'item', member: 'member', chatmessage: 'chatMessage', chat_message: 'chatMessage' };
    hasTabFromUrl = !!tabByUrlParam[tabParam];
    if (hasTabFromUrl) {
        currentTab = tabByUrlParam[tabParam];
        toggleTabActive(currentTab);
    }

    loadStats();
    loadReports();

    document.getElementById('statusFilter').addEventListener('change', function() {
        currentPage = 0;
        loadReports();
    });
});

function switchTab(tab) {
    currentTab = tab;
    currentPage = 0;

    toggleTabActive(tab);
    loadReports();
}

function toggleTabActive(tab) {
    document.getElementById('tabItem').classList.toggle('tab-active', tab === 'item');
    document.getElementById('tabMember').classList.toggle('tab-active', tab === 'member');
    document.getElementById('tabChatMessage').classList.toggle('tab-active', tab === 'chatMessage');
}

function filterByStatus(status) {
    document.getElementById('statusFilter').value = status;
    currentPage = 0;
    loadReports();
}

function loadStats() {
    adminFetch.post('/api/admin/reports/stats')
    .then(r => {
        if (!r.ok) throw new Error(r.status);
        return r.json();
    })
    .then(data => {
        if (!data || !data.reportStats) return;
        const item = data.reportStats.item || {};
        const member = data.reportStats.member || {};
        const chatMessage = data.reportStats.chatMessage || {};
        const sumStatusCount = statusCode => (item[statusCode] || 0) + (member[statusCode] || 0) + (chatMessage[statusCode] || 0);

        document.getElementById('statPending').textContent = sumStatusCount('PENDING').toLocaleString();
        document.getElementById('statProcessing').textContent = sumStatusCount('PROCESSING').toLocaleString();
        document.getElementById('statCompleted').textContent = sumStatusCount('COMPLETED').toLocaleString();
        document.getElementById('statRejected').textContent = sumStatusCount('REJECTED').toLocaleString();

        // 탭별 대기 건수 배지 + 물품 대기 0이면 대기 있는 첫 탭(회원 > 채팅)으로
        const pendingCountByTab = { item: item.PENDING || 0, member: member.PENDING || 0, chatMessage: chatMessage.PENDING || 0 };
        const pendingBadgeIdByTab = { item: 'tabItemPendingBadge', member: 'tabMemberPendingBadge', chatMessage: 'tabChatMessagePendingBadge' };
        Object.entries(pendingBadgeIdByTab).forEach(([tab, badgeId]) => {
            document.getElementById(badgeId).textContent = pendingCountByTab[tab];
            document.getElementById(badgeId).classList.toggle('hidden', pendingCountByTab[tab] === 0);
        });
        const firstPendingTab = ['member', 'chatMessage'].find(tab => pendingCountByTab[tab] > 0);
        if (!hasTabFromUrl && currentTab === 'item' && pendingCountByTab.item === 0 && firstPendingTab) {
            hasTabFromUrl = true;
            switchTab(firstPendingTab);
        }
    })
    .catch(e => console.error('통계 로드 실패:', e));
}

function loadReports() {
    document.getElementById('loading').style.display = 'flex';
    document.getElementById('itemTableContainer').style.display = 'none';
    document.getElementById('memberTableContainer').style.display = 'none';
    document.getElementById('chatMessageTableContainer').style.display = 'none';
    document.getElementById('emptyMessage').style.display = 'none';
    document.getElementById('paginationContainer').style.display = 'none';

    const statusFilter = document.getElementById('statusFilter').value;

    const reportListParams = {
        pageNumber: currentPage,
        pageSize: currentPageSize,
    };
    if (statusFilter) reportListParams.reportStatus = statusFilter;

    const reportListUrlByTab = {
        item: '/api/admin/reports/item-list',
        member: '/api/admin/reports/member-list',
        chatMessage: '/api/admin/reports/chat-message-list'
    };
    const reportListUrl = reportListUrlByTab[currentTab];

    adminFetch.post(reportListUrl, reportListParams)
    .then(r => {
        if (!r.ok) throw new Error(r.status);
        return r.json();
    })
    .then(data => {
        if (currentTab === 'item') {
            renderItemReports(data);
        } else if (currentTab === 'chatMessage') {
            renderChatMessageReports(data);
        } else {
            renderMemberReports(data);
        }
    })
    .catch(e => {
        console.error('신고 목록 로드 실패:', e);
        document.getElementById('loading').style.display = 'none';
    });
}

function renderItemReports(data) {
    const reports = data.itemReports || [];
    reportsCache = {};
    reports.forEach(r => { reportsCache[r.itemReportId] = r; });

    document.getElementById('totalCount').textContent = `총 ${(data.totalElements || 0).toLocaleString()}건`;

    const tbody = document.getElementById('itemTableBody');
    tbody.innerHTML = '';

    if (reports.length > 0) {
        tbody.innerHTML = reports.map(r => {
            const shortId = escapeHtml(r.itemReportId.substring(0, 8));
            const status = statusMap[r.status] || { text: r.status, cls: 'badge-neutral' };
            const itemName = buildItemLinkHtml(r.item);
            const reporter = buildMemberLinkHtml(r.member);
            const reasons = (r.itemReportReasons || [])
                .map(reason => `<span class="badge badge-outline badge-xs">${escapeHtml(AdminLabels.label('ItemReportReason', reason))}</span>`)
                .join(' ');

            return `<tr class="hover cursor-pointer" onclick="viewReportDetail('${escapeHtml(r.itemReportId)}', 'item')">
                <td class="text-xs font-mono">${shortId}</td>
                <td class="max-w-32 truncate">${itemName}</td>
                <td>${reporter}</td>
                <td><div class="flex flex-wrap gap-1">${reasons}</div></td>
                <td><span class="badge ${status.cls} badge-sm">${status.text}</span></td>
                <td class="text-sm">${TimeUtils.formatDate(r.createdDate)}</td>
                <td>
                    <button class="btn btn-ghost btn-xs" onclick="event.stopPropagation(); viewReportDetail('${escapeHtml(r.itemReportId)}', 'item')">
                        <i data-lucide="eye" class="size-4"></i>
                    </button>
                </td>
            </tr>`;
        }).join('');

        renderPagination(data);
        document.getElementById('itemTableContainer').style.display = 'block';
        document.getElementById('paginationContainer').style.display = 'flex';
    } else {
        document.getElementById('emptyMessage').style.display = 'flex';
    }

    document.getElementById('loading').style.display = 'none';
    lucide.createIcons();
}

function renderMemberReports(data) {
    const reports = data.memberReports || [];
    reportsCache = {};
    reports.forEach(r => { reportsCache[r.memberReportId] = r; });

    document.getElementById('totalCount').textContent = `총 ${(data.totalElements || 0).toLocaleString()}건`;

    const tbody = document.getElementById('memberTableBody');
    tbody.innerHTML = '';

    if (reports.length > 0) {
        tbody.innerHTML = reports.map(r => {
            const shortId = escapeHtml(r.memberReportId.substring(0, 8));
            const status = statusMap[r.status] || { text: r.status, cls: 'badge-neutral' };
            const targetName = buildMemberLinkHtml(r.targetMember);
            const reporter = buildMemberLinkHtml(r.reporter);
            const reasons = (r.memberReportReasons || [])
                .map(reason => `<span class="badge badge-outline badge-xs">${escapeHtml(AdminLabels.label('MemberReportReason', reason))}</span>`)
                .join(' ');

            return `<tr class="hover cursor-pointer" onclick="viewReportDetail('${escapeHtml(r.memberReportId)}', 'member')">
                <td class="text-xs font-mono">${shortId}</td>
                <td>${targetName}</td>
                <td>${reporter}</td>
                <td><div class="flex flex-wrap gap-1">${reasons}</div></td>
                <td><span class="badge ${status.cls} badge-sm">${status.text}</span></td>
                <td class="text-sm">${TimeUtils.formatDate(r.createdDate)}</td>
                <td>
                    <button class="btn btn-ghost btn-xs" onclick="event.stopPropagation(); viewReportDetail('${escapeHtml(r.memberReportId)}', 'member')">
                        <i data-lucide="eye" class="size-4"></i>
                    </button>
                </td>
            </tr>`;
        }).join('');

        renderPagination(data);
        document.getElementById('memberTableContainer').style.display = 'block';
        document.getElementById('paginationContainer').style.display = 'flex';
    } else {
        document.getElementById('emptyMessage').style.display = 'flex';
    }

    document.getElementById('loading').style.display = 'none';
    lucide.createIcons();
}

function renderPagination(data) {
    const pagination = document.getElementById('pagination');
    pagination.innerHTML = '';

    if (!data.totalPages || data.totalPages <= 1) return;

    const currentPageNumber = data.currentPage;
    const totalPageCount = data.totalPages;

    pagination.innerHTML += `<button class="join-item btn btn-sm ${currentPageNumber === 0 ? 'btn-disabled' : ''}"
        onclick="changePage(${currentPageNumber - 1})">&laquo;</button>`;

    const start = Math.max(0, currentPageNumber - 2);
    const end = Math.min(totalPageCount - 1, currentPageNumber + 2);

    for (let i = start; i <= end; i++) {
        pagination.innerHTML += `<button class="join-item btn btn-sm ${i === currentPageNumber ? 'btn-active' : ''}"
            onclick="changePage(${i})">${i + 1}</button>`;
    }

    pagination.innerHTML += `<button class="join-item btn btn-sm ${currentPageNumber === totalPageCount - 1 ? 'btn-disabled' : ''}"
        onclick="changePage(${currentPageNumber + 1})">&raquo;</button>`;

    const pageStart = currentPageNumber * currentPageSize + 1;
    const pageEnd = Math.min(pageStart + currentPageSize - 1, data.totalElements);
    document.getElementById('pageInfo').textContent = `${pageStart}-${pageEnd} / ${data.totalElements}`;
}

function changePage(page) {
    if (page < 0) return;
    currentPage = page;
    loadReports();
}

// 현재 탭·상태 필터 > CSV 다운로드 (최대 10,000행)
function exportReportsCsv() {
    const reportExportUrlByTab = {
        item: '/api/admin/reports/item-export-csv',
        member: '/api/admin/reports/member-export-csv',
        chatMessage: '/api/admin/reports/chat-message-export-csv'
    };
    const statusFilter = document.getElementById('statusFilter').value;
    AdminCsvExport.download(reportExportUrlByTab[currentTab], statusFilter ? { reportStatus: statusFilter } : {});
}
