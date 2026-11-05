/**
 * 관리자 작업 기록 (#874): 필터(기간·관리자·작업·대상), 최근순 목록·페이지, 상세 모달(요청 파라미터 요약)
 */
const AdminAuditLogs = (function () {
    const PAGE_SIZE = 20;
    // 목록 행 데이터 (adminAuditLogId > 기록)
    const auditLogById = {};
    const state = { currentPage: 0 };

    // 입력한 필터만 전송
    function buildFilterParams() {
        return {
            startDate: document.getElementById('auditStartDate').value,
            endDate: document.getElementById('auditEndDate').value,
            adminKeyword: document.getElementById('auditAdminKeyword').value.trim(),
            adminAuditAction: document.getElementById('auditActionFilter').value,
            adminAuditTargetType: document.getElementById('auditTargetTypeFilter').value,
            targetId: document.getElementById('auditTargetId').value.trim(),
        };
    }

    function targetText(adminAuditLog) {
        const targetTypeText = AdminLabels.label('AdminAuditTargetType', adminAuditLog.targetType);
        if (!adminAuditLog.targetId) return targetTypeText;
        return `${targetTypeText} · ${adminAuditLog.targetId.substring(0, 8)}`;
    }

    function renderRow(adminAuditLog) {
        auditLogById[adminAuditLog.adminAuditLogId] = adminAuditLog;
        const auditLogIdText = escapeHtml(adminAuditLog.adminAuditLogId);
        return `<tr class="hover">
            <td class="text-xs whitespace-nowrap">${escapeHtml(TimeUtils.formatDateTime(adminAuditLog.createdDate))}</td>
            <td class="text-xs max-w-48 truncate" title="${escapeHtml(adminAuditLog.adminEmail || '')}">${escapeHtml(adminAuditLog.adminEmail || '-')}</td>
            <td class="text-xs whitespace-nowrap">${escapeHtml(AdminLabels.label('AdminAuditAction', adminAuditLog.action))}</td>
            <td class="text-xs whitespace-nowrap font-mono" title="${escapeHtml(adminAuditLog.targetId || '')}">${escapeHtml(targetText(adminAuditLog))}</td>
            <td class="text-xs max-w-56 truncate">${escapeHtml(adminAuditLog.summary || '-')}</td>
            <td class="text-xs font-mono">${escapeHtml(adminAuditLog.clientIp || '-')}</td>
            <td>
                <button class="btn btn-ghost btn-xs" title="상세" onclick="AdminAuditLogs.openDetail('${auditLogIdText}')">
                    <i data-lucide="file-text" class="size-4"></i>
                </button>
            </td>
        </tr>`;
    }

    async function load(pageNumber) {
        state.currentPage = pageNumber || 0;
        try {
            const listResponse = await AdminApi.call('/api/admin/audit-logs/list',
                { ...buildFilterParams(), pageNumber: state.currentPage, pageSize: PAGE_SIZE });
            const adminAuditLogs = listResponse.adminAuditLogs || [];
            document.getElementById('auditLogTableBody').innerHTML = adminAuditLogs.map(renderRow).join('');
            document.getElementById('auditLogEmptyMessage').classList.toggle('hidden', adminAuditLogs.length > 0);
            document.getElementById('auditLogTotalCount').textContent = `총 ${NumberUtils.formatNumber(listResponse.totalElements)}건`;
            AdminPager.render(document.getElementById('auditLogPagination'), listResponse.currentPage || 0,
                listResponse.totalPages || 0, load);
            if (window.lucide) lucide.createIcons();
        } catch (error) {
            AdminApi.alertError(error, '작업 기록 조회');
        }
    }

    // 요청 요약: JSON이면 들여쓰기, 잘린 값이면 원문
    function formatRequestSummary(requestSummary) {
        if (!requestSummary) return '-';
        try {
            return JSON.stringify(JSON.parse(requestSummary), null, 2);
        } catch (notJsonError) {
            return requestSummary;
        }
    }

    function detailRowHtml(labelText, valueHtml) {
        return `<div class="grid grid-cols-3 gap-2 py-1 border-b border-base-200">
            <span class="text-base-content/60">${escapeHtml(labelText)}</span><span class="col-span-2 break-all">${valueHtml}</span></div>`;
    }

    function openDetail(adminAuditLogId) {
        const adminAuditLog = auditLogById[adminAuditLogId];
        if (!adminAuditLog) return;
        document.getElementById('auditLogDetailContent').innerHTML = [
            detailRowHtml('시각', escapeHtml(TimeUtils.formatDateTime(adminAuditLog.createdDate))),
            detailRowHtml('관리자', escapeHtml(`${adminAuditLog.adminEmail || '-'} (${adminAuditLog.adminMemberId || '-'})`)),
            detailRowHtml('작업', escapeHtml(`${AdminLabels.label('AdminAuditAction', adminAuditLog.action)} (${adminAuditLog.action})`)),
            detailRowHtml('대상', escapeHtml(`${AdminLabels.label('AdminAuditTargetType', adminAuditLog.targetType)} ${adminAuditLog.targetId || ''}`)),
            detailRowHtml('요약', escapeHtml(adminAuditLog.summary || '-')),
            detailRowHtml('IP', escapeHtml(adminAuditLog.clientIp || '-')),
            detailRowHtml('요청 파라미터', `<pre class="text-xs bg-base-200 rounded p-2 whitespace-pre-wrap">${escapeHtml(formatRequestSummary(adminAuditLog.requestSummary))}</pre>`),
        ].join('');
        document.getElementById('auditLogDetailModal').showModal();
    }

    function resetFilters() {
        document.getElementById('auditLogFilterForm').reset();
        load(0);
    }

    document.addEventListener('DOMContentLoaded', () => {
        document.getElementById('auditActionFilter').innerHTML = AdminLabels.options('AdminAuditAction', '전체');
        document.getElementById('auditTargetTypeFilter').innerHTML = AdminLabels.options('AdminAuditTargetType', '전체');
        document.getElementById('auditLogFilterForm').addEventListener('submit', event => {
            event.preventDefault();
            load(0);
        });
        load(0);
    });

    return { load, openDetail, resetFilters };
})();

window.AdminAuditLogs = AdminAuditLogs;
