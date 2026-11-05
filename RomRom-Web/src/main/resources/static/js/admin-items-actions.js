/**
 * 관리자 물품 목록 > 행 작업: 삭제(사유 선택), 거래 상태 변경, 등록자 정지
 * items.html 의 공통 상태·함수 사용: deleteTargetId, statusUpdateTargetId, suspendFromItemTargetMemberId, deleteReasonMap, loadItems
 */

function confirmDelete(itemId) {
    deleteTargetId = itemId;
    document.getElementById('deleteReasonSelect').value = '';
    document.getElementById('deleteDetailInput').value = '';
    document.getElementById('deleteConfirmModal').showModal();
}

function populateDeleteReasons() {
    const select = document.getElementById('deleteReasonSelect');
    if (!select || select.options.length > 1) return;
    Object.entries(deleteReasonMap).forEach(([code, label]) => {
        const opt = document.createElement('option');
        opt.value = code;
        opt.textContent = label;
        select.appendChild(opt);
    });
}

function executeDelete(itemId) {
    const reason = document.getElementById('deleteReasonSelect').value;
    if (!reason) {
        alert('삭제 사유를 선택하세요.');
        return;
    }
    const detail = document.getElementById('deleteDetailInput').value.trim();
    document.getElementById('deleteConfirmModal').close();

    adminFetch.post('/api/admin/items/delete', {
        itemId: itemId,
        itemAdminDeleteReason: reason,
        itemAdminDeleteDetail: detail,
    })
        .then(r => {
            if (!r.ok) throw new Error(r.status);
            return r.text();
        })
        .then(() => loadItems())
        .catch(e => AdminApi.alertError(e, '물품 삭제'));

    deleteTargetId = null;
}

function confirmStatusUpdate(itemId, currentStatus) {
    statusUpdateTargetId = itemId;
    document.getElementById('statusAvailableBtn').classList.toggle('btn-active', currentStatus === 'AVAILABLE');
    document.getElementById('statusExchangedBtn').classList.toggle('btn-active', currentStatus === 'EXCHANGED');
    document.getElementById('statusUpdateModal').showModal();
}

function executeStatusUpdate(newStatus) {
    if (!statusUpdateTargetId) return;
    const targetId = statusUpdateTargetId;
    statusUpdateTargetId = null;
    document.getElementById('statusUpdateModal').close();

    adminFetch.post('/api/admin/items/update-status', { itemId: targetId, itemStatus: newStatus })
        .then(r => {
            if (!r.ok) throw new Error(r.status);
            return r.text();
        })
        .then(() => loadItems())
        .catch(e => AdminApi.alertError(e, '거래 상태 변경'));
}

function confirmSuspendFromItem(memberId, nickname) {
    if (!memberId) {
        alert('회원 정보를 찾을 수 없습니다.');
        return;
    }
    suspendFromItemTargetMemberId = memberId;
    document.getElementById('suspendTargetNickname').textContent = nickname || memberId;
    document.getElementById('suspendReasonInput').value = '';
    document.getElementById('suspendedUntilInput').value = '';
    document.getElementById('suspendFromItemModal').showModal();
}

function executeSuspendFromItem() {
    if (!suspendFromItemTargetMemberId) return;
    const suspendReason = document.getElementById('suspendReasonInput').value.trim();
    if (!suspendReason) {
        alert('제재 사유를 입력해주세요.');
        return;
    }
    const suspendedUntil = document.getElementById('suspendedUntilInput').value;
    const targetMemberId = suspendFromItemTargetMemberId;
    suspendFromItemTargetMemberId = null;
    document.getElementById('suspendFromItemModal').close();

    const suspendParams = { memberId: targetMemberId, suspendReason: suspendReason };
    if (suspendedUntil) suspendParams.suspendedUntil = suspendedUntil;

    adminFetch.post('/api/admin/members/suspend', suspendParams)
        .then(r => {
            if (!r.ok) throw new Error(r.status);
            return r.json();
        })
        .then(() => alert('제재가 적용되었습니다.'))
        .catch(e => AdminApi.alertError(e, '제재'));
}
