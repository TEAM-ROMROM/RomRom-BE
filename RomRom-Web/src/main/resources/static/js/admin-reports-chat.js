/**
 * 관리자 신고 관리 > 채팅 메시지 신고 탭 (#873): 목록 렌더링, 상세(메시지 원문 스냅샷), 상태 변경, 정지·반려 처리
 * reports.html 의 공통 상태·함수 사용: reportsCache, statusMap, buildMemberLinkHtml, renderPagination,
 * updateStatus, openSuspendFromReport, loadStats, loadReports
 */

// 채팅 메시지 신고 목록 > 표 렌더링
function renderChatMessageReports(data) {
    const reports = data.chatMessageReports || [];
    reportsCache = {};
    reports.forEach(report => { reportsCache[report.chatMessageReportId] = report; });

    document.getElementById('totalCount').textContent = `총 ${(data.totalElements || 0).toLocaleString()}건`;

    const tableBody = document.getElementById('chatMessageTableBody');
    tableBody.innerHTML = '';

    if (reports.length > 0) {
        tableBody.innerHTML = reports.map(report => {
            const reportIdText = escapeHtml(report.chatMessageReportId);
            const status = statusMap[report.status] || { text: report.status, cls: 'badge-neutral' };
            const messagePreviewText = escapeHtml((report.messageContentSnapshot || '').substring(0, 40))
                + (report.messageContentSnapshot && report.messageContentSnapshot.length > 40 ? '...' : '');
            return `<tr class="hover cursor-pointer" onclick="viewReportDetail('${reportIdText}', 'chatMessage')">
                <td class="text-xs font-mono">${escapeHtml(report.chatMessageReportId.substring(0, 8))}</td>
                <td>${buildMemberLinkHtml(report.targetMember)}</td>
                <td>${buildMemberLinkHtml(report.reporter)}</td>
                <td class="max-w-48 truncate">${messagePreviewText || '<span class="text-base-content/40">없음</span>'}</td>
                <td><div class="flex flex-wrap gap-1">${buildChatMessageReasonBadgesHtml(report.reasons, 'badge-xs')}</div></td>
                <td><span class="badge ${status.cls} badge-sm">${status.text}</span></td>
                <td class="text-sm">${TimeUtils.formatDate(report.createdDate)}</td>
                <td>
                    <button class="btn btn-ghost btn-xs" onclick="event.stopPropagation(); viewReportDetail('${reportIdText}', 'chatMessage')">
                        <i data-lucide="eye" class="size-4"></i>
                    </button>
                </td>
            </tr>`;
        }).join('');

        renderPagination(data);
        document.getElementById('chatMessageTableContainer').style.display = 'block';
        document.getElementById('paginationContainer').style.display = 'flex';
    } else {
        document.getElementById('emptyMessage').style.display = 'flex';
    }

    document.getElementById('loading').style.display = 'none';
    lucide.createIcons();
}

// 신고 사유 배지 (회원 신고 사유 enum 재사용)
function buildChatMessageReasonBadgesHtml(reasons, badgeSizeClass) {
    return (reasons || [])
        .map(reason => `<span class="badge badge-outline ${badgeSizeClass}">${escapeHtml(AdminLabels.label('MemberReportReason', reason))}</span>`)
        .join(' ');
}

// 채팅 메시지 신고 상세: 신고자, 대상 회원, 채팅방 ID, 메시지 원문, 사유, 추가 의견 + 상태 변경·정지·반려
function viewChatMessageReportDetail(reportId) {
    const content = document.getElementById('reportDetailContent');
    document.getElementById('modalTitle').textContent = '채팅 메시지 신고 상세';

    const report = reportsCache[reportId];
    if (!report) {
        content.innerHTML = '<p class="text-error">신고 정보를 불러올 수 없습니다.</p>';
        return;
    }

    const reportIdText = escapeHtml(report.chatMessageReportId);
    const status = statusMap[report.status] || { text: report.status, cls: 'badge-neutral' };
    const isReportClosed = report.status === 'COMPLETED' || report.status === 'REJECTED';
    const targetMember = report.targetMember;
    const suspendButtonHtml = targetMember && targetMember.memberId
        ? `<button class="btn btn-error btn-sm" data-member-id="${escapeHtml(targetMember.memberId)}" data-nickname="${escapeHtml(targetMember.nickname || '익명')}" data-email="${escapeHtml(targetMember.email || '')}" data-report-id="${reportIdText}" data-report-type="CHAT_MESSAGE" onclick="openSuspendFromReport(this.dataset)">
               <i data-lucide="shield-ban" class="size-4"></i> 이 사용자 정지
           </button>`
        : `<button class="btn btn-error btn-sm btn-disabled" disabled title="대상 회원 정보 없음">
               <i data-lucide="shield-ban" class="size-4"></i> 이 사용자 정지
           </button>`;
    const rejectButtonHtml = `<button class="btn btn-outline btn-sm" ${isReportClosed ? 'disabled' : ''}
            onclick="rejectChatMessageReport('${reportIdText}')">
            <i data-lucide="x-circle" class="size-4"></i> 신고 반려
        </button>`;
    const messageSnapshotHtml = report.messageContentSnapshot
        ? `<div class="whitespace-pre-wrap break-words bg-base-200 rounded-box p-2">${escapeHtml(report.messageContentSnapshot)}</div>`
        : '<span class="text-base-content/40">없음 (이미지·약속 등 텍스트 없는 메시지)</span>';

    content.innerHTML = `
        <div class="grid grid-cols-2 gap-3 text-sm">
            <div class="text-base-content/60">신고 ID</div>
            <div class="font-mono text-xs">${reportIdText}</div>
            <div class="text-base-content/60">신고 대상 회원</div>
            <div class="font-medium">${buildMemberLinkHtml(targetMember)}</div>
            <div class="text-base-content/60">신고자</div>
            <div>${buildMemberLinkHtml(report.reporter)}</div>
            <div class="text-base-content/60">채팅방 ID</div>
            <div class="font-mono text-xs break-all">${report.chatRoomId
                ? `<a class="link link-hover" href="/admin/chat-rooms?chatRoomId=${encodeURIComponent(report.chatRoomId)}">${escapeHtml(report.chatRoomId)}</a>`
                : '-'}</div>
            <div class="text-base-content/60">메시지 ID</div>
            <div class="font-mono text-xs break-all">${escapeHtml(report.chatMessageId || '-')}</div>
            <div class="text-base-content/60">메시지 원문 (신고 시점)</div>
            <div>${messageSnapshotHtml}</div>
            <div class="text-base-content/60">신고 사유</div>
            <div class="flex flex-wrap gap-1">${buildChatMessageReasonBadgesHtml(report.reasons, 'badge-sm')}</div>
            <div class="text-base-content/60">추가 의견</div>
            <div>${report.extraComment ? escapeHtml(report.extraComment) : '<span class="text-base-content/40">없음</span>'}</div>
            <div class="text-base-content/60">신고일시</div>
            <div>${TimeUtils.formatDateTime(report.createdDate)}</div>
            <div class="text-base-content/60">현재 상태</div>
            <div><span class="badge ${status.cls} badge-sm">${status.text}</span></div>
        </div>
        <div class="divider"></div>
        <div class="flex items-center gap-3">
            <span class="text-sm font-medium">상태 변경:</span>
            <select id="statusSelect" class="select select-sm">
                ${Object.entries(statusMap).map(([statusCode, statusInfo]) =>
                    `<option value="${statusCode}" ${report.status === statusCode ? 'selected' : ''}>${statusInfo.text}</option>`).join('')}
            </select>
            <button class="btn btn-primary btn-sm" onclick="updateStatus('${reportIdText}', 'CHAT_MESSAGE')">저장</button>
        </div>
        <div class="divider"></div>
        <div class="flex items-center gap-3">
            ${suspendButtonHtml}
            ${rejectButtonHtml}
        </div>`;
    lucide.createIcons();
}

// 신고 반려: 확인 > resolve(REJECT) > 목록·통계 갱신
async function rejectChatMessageReport(reportId) {
    if (!confirm('이 채팅 메시지 신고를 반려합니다. 계속할까요?')) return;
    try {
        await AdminApi.call('/api/admin/reports/resolve', {
            reportId: reportId,
            reportType: 'CHAT_MESSAGE',
            resolveAction: 'REJECT'
        });
        document.getElementById('reportDetailModal').close();
        loadStats();
        loadReports();
    } catch (error) {
        AdminApi.alertError(error, '채팅 메시지 신고 반려');
    }
}
