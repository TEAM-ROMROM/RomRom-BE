/**
 * 관리자 신고 관리 > 상세·처리: 상세 모달(물품·회원), 신고 연결 정지, 상태 변경, 신고 물품 삭제
 * admin-reports.js 의 공통 상태·함수 사용: reportsCache, statusMap, buildMemberLinkHtml, buildItemLinkHtml, loadReports, loadStats
 */

function viewReportDetail(reportId, type) {
    const modal = document.getElementById('reportDetailModal');
    const content = document.getElementById('reportDetailContent');
    const title = document.getElementById('modalTitle');
    modal.showModal();

    if (type === 'chatMessage') {
        viewChatMessageReportDetail(reportId);
        return;
    }

    const report = reportsCache[reportId];
    if (!report) {
        content.innerHTML = '<p class="text-error">신고 정보를 불러올 수 없습니다.</p>';
        return;
    }

    const status = statusMap[report.status] || { text: report.status, cls: 'badge-neutral' };

    if (type === 'item') {
        title.textContent = '물품 신고 상세';
        const itemName = buildItemLinkHtml(report.item);
        const reporter = buildMemberLinkHtml(report.member);
        const itemOwnerLinkHtml = report.item && report.item.member ? buildMemberLinkHtml(report.item.member) : '-';
        // 신고 물품 삭제 (원스톱 처리 DELETE_ITEM): 물품 있음 + 미종결 신고만
        const isReportClosed = report.status === 'COMPLETED' || report.status === 'REJECTED';
        const canDeleteReportedItem = !!(report.item && report.item.itemId) && !isReportClosed;
        const deleteItemButtonHtml = `<button class="btn btn-outline btn-error btn-sm" ${canDeleteReportedItem ? '' : 'disabled'}
                onclick="resolveReportByDeletingItem('${escapeHtml(report.itemReportId)}')">
                <i data-lucide="trash-2" class="size-4"></i> 신고 물품 삭제
            </button>`;
        const reasons = (report.itemReportReasons || [])
            .map(reason => `<span class="badge badge-outline badge-sm">${escapeHtml(AdminLabels.label('ItemReportReason', reason))}</span>`)
            .join(' ');

        const itemOwnerMemberId = report.item && report.item.member ? report.item.member.memberId : null;
        const itemOwnerNickname = report.item && report.item.member ? (report.item.member.nickname || '익명') : null;
        const itemOwnerEmail = report.item && report.item.member ? (report.item.member.email || '') : null;
        const suspendButtonHtml = itemOwnerMemberId
            ? `<button class="btn btn-error btn-sm" data-member-id="${escapeHtml(itemOwnerMemberId)}" data-nickname="${escapeHtml(itemOwnerNickname)}" data-email="${escapeHtml(itemOwnerEmail)}" data-report-id="${escapeHtml(report.itemReportId)}" data-report-type="ITEM" onclick="openSuspendFromReport(this.dataset)">
                   <i data-lucide="shield-ban" class="size-4"></i> 이 사용자 정지
               </button>`
            : `<button class="btn btn-error btn-sm btn-disabled" disabled title="물품 등록자 정보 없음">
                   <i data-lucide="shield-ban" class="size-4"></i> 이 사용자 정지
               </button>`;

        content.innerHTML = `
            <div class="grid grid-cols-2 gap-3 text-sm">
                <div class="text-base-content/60">신고 ID</div>
                <div class="font-mono text-xs">${escapeHtml(report.itemReportId)}</div>
                <div class="text-base-content/60">신고 대상 물품</div>
                <div class="font-medium">${itemName}</div>
                <div class="text-base-content/60">물품 등록자</div>
                <div>${itemOwnerLinkHtml}</div>
                <div class="text-base-content/60">신고자</div>
                <div>${reporter}</div>
                <div class="text-base-content/60">신고 사유</div>
                <div class="flex flex-wrap gap-1">${reasons}</div>
                <div class="text-base-content/60">기타 의견</div>
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
                    <option value="PENDING" ${report.status === 'PENDING' ? 'selected' : ''}>대기중</option>
                    <option value="PROCESSING" ${report.status === 'PROCESSING' ? 'selected' : ''}>처리중</option>
                    <option value="COMPLETED" ${report.status === 'COMPLETED' ? 'selected' : ''}>완료</option>
                    <option value="REJECTED" ${report.status === 'REJECTED' ? 'selected' : ''}>반려</option>
                </select>
                <button class="btn btn-primary btn-sm" onclick="updateStatus('${escapeHtml(report.itemReportId)}', 'ITEM')">저장</button>
            </div>
            <div class="divider"></div>
            <div class="flex items-center gap-3">
                ${suspendButtonHtml}
                ${deleteItemButtonHtml}
            </div>`;
    } else {
        title.textContent = '회원 신고 상세';
        const targetName = buildMemberLinkHtml(report.targetMember);
        const reporter = buildMemberLinkHtml(report.reporter);
        const reasons = (report.memberReportReasons || [])
            .map(reason => `<span class="badge badge-outline badge-sm">${escapeHtml(AdminLabels.label('MemberReportReason', reason))}</span>`)
            .join(' ');

        const targetMemberId = report.targetMember ? report.targetMember.memberId : null;
        const targetMemberNickname = report.targetMember ? (report.targetMember.nickname || '익명') : null;
        const targetMemberEmail = report.targetMember ? (report.targetMember.email || '') : null;
        const memberSuspendButtonHtml = targetMemberId
            ? `<button class="btn btn-error btn-sm" data-member-id="${escapeHtml(targetMemberId)}" data-nickname="${escapeHtml(targetMemberNickname)}" data-email="${escapeHtml(targetMemberEmail)}" data-report-id="${escapeHtml(report.memberReportId)}" data-report-type="MEMBER" onclick="openSuspendFromReport(this.dataset)">
                   <i data-lucide="shield-ban" class="size-4"></i> 이 사용자 정지
               </button>`
            : `<button class="btn btn-error btn-sm btn-disabled" disabled title="대상 회원 정보 없음">
                   <i data-lucide="shield-ban" class="size-4"></i> 이 사용자 정지
               </button>`;

        content.innerHTML = `
            <div class="grid grid-cols-2 gap-3 text-sm">
                <div class="text-base-content/60">신고 ID</div>
                <div class="font-mono text-xs">${escapeHtml(report.memberReportId)}</div>
                <div class="text-base-content/60">신고 대상 회원</div>
                <div class="font-medium">${targetName}</div>
                <div class="text-base-content/60">신고자</div>
                <div>${reporter}</div>
                <div class="text-base-content/60">신고 사유</div>
                <div class="flex flex-wrap gap-1">${reasons}</div>
                <div class="text-base-content/60">기타 의견</div>
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
                    <option value="PENDING" ${report.status === 'PENDING' ? 'selected' : ''}>대기중</option>
                    <option value="PROCESSING" ${report.status === 'PROCESSING' ? 'selected' : ''}>처리중</option>
                    <option value="COMPLETED" ${report.status === 'COMPLETED' ? 'selected' : ''}>완료</option>
                    <option value="REJECTED" ${report.status === 'REJECTED' ? 'selected' : ''}>반려</option>
                </select>
                <button class="btn btn-primary btn-sm" onclick="updateStatus('${escapeHtml(report.memberReportId)}', 'MEMBER')">저장</button>
            </div>
            <div class="divider"></div>
            <div class="flex items-center gap-3">
                ${memberSuspendButtonHtml}
            </div>`;
    }
    lucide.createIcons();
}

// ─── 제재 모달 ────────────────────────────────────────────────

function openSuspendFromReport(reportTargetDataset) {
    // 신고 연결 정지: 처리 후 신고 상태 완료 (서버), 대상 값은 data-* 속성 (따옴표 포함 닉네임 안전)
    AdminSanctionModal.open({
        memberId: reportTargetDataset.memberId, nickname: reportTargetDataset.nickname, email: reportTargetDataset.email,
        reportId: reportTargetDataset.reportId, reportType: reportTargetDataset.reportType,
        onCompleted: () => {
            document.getElementById('reportDetailModal').close();
            loadReports();
            loadStats();
        }
    });
}

function updateStatus(reportId, type) {
    const newStatus = document.getElementById('statusSelect').value;

    adminFetch.post('/api/admin/reports/update-status', {
        reportId: reportId,
        reportType: type,
        newReportStatus: newStatus,
    })
    .then(r => {
        if (!r.ok) throw new Error(r.status);
        return r.json();
    })
    .then(data => {
        if (data !== null) {
            document.getElementById('reportDetailModal').close();
            loadStats();
            loadReports();
        }
    })
    .catch(e => AdminApi.alertError(e, '신고 상태 변경'));
}

// 신고 물품 삭제: 확인 > 상세 사유(선택) > resolve(DELETE_ITEM) > 신고 완료 처리 (서버)
async function resolveReportByDeletingItem(reportId) {
    if (!confirm('신고된 물품을 삭제하고 신고를 완료 처리합니다. 계속할까요?')) return;
    const itemAdminDeleteDetail = prompt('삭제 상세 사유 (선택, 내부 기록용)', '');
    if (itemAdminDeleteDetail === null) return;
    try {
        await AdminApi.call('/api/admin/reports/resolve', {
            reportId: reportId,
            reportType: 'ITEM',
            resolveAction: 'DELETE_ITEM',
            itemAdminDeleteDetail: itemAdminDeleteDetail.trim()
        });
        document.getElementById('reportDetailModal').close();
        loadStats();
        loadReports();
    } catch (error) {
        AdminApi.alertError(error, '신고 물품 삭제');
    }
}
