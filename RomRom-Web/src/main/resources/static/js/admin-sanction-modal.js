/**
 * 회원 정지 모달 공통: 대상 지정 > 유형·기간·사유 입력 > /members/suspend 호출 > 완료 콜백
 * 마크업: templates/admin/fragments/sanction-modal.html (layout.html에서 1회 포함)
 * 사용: AdminSanctionModal.open({ memberId, nickname, email, reportId, reportType, onCompleted })
 */
const AdminSanctionModal = (function () {
    // 기간 버튼 (일)
    const DURATION_DAY_OPTIONS = [1, 3, 7, 14, 30, 90];
    // 사유 선택지 ('직접입력' > 자유 입력)
    const REASON_PRESETS = ['욕설 및 혐오 발언', '사기 및 허위 거래', '스팸 및 광고', '개인정보 침해', '성적 부적절 콘텐츠'];
    const CUSTOM_REASON_VALUE = '직접입력';

    let currentOptions = {};

    function element(elementId) {
        return document.getElementById(elementId);
    }

    function selectedSanctionType() {
        const checkedRadio = document.querySelector('input[name="adminSanctionType"]:checked');
        return checkedRadio ? checkedRadio.value : 'temporary';
    }

    // datetime-local 값: 지금 + N일 (YYYY-MM-DDTHH:mm, 브라우저 시간대)
    function toDateTimeLocalAfterDays(dayCount) {
        const untilDate = new Date();
        untilDate.setDate(untilDate.getDate() + dayCount);
        const pad = numberValue => String(numberValue).padStart(2, '0');
        return `${untilDate.getFullYear()}-${pad(untilDate.getMonth() + 1)}-${pad(untilDate.getDate())}T${pad(untilDate.getHours())}:${pad(untilDate.getMinutes())}`;
    }

    function renderStaticControls() {
        element('adminSanctionDurationButtons').innerHTML = DURATION_DAY_OPTIONS
            .map(dayCount => `<button type="button" class="btn btn-outline btn-xs" data-days="${dayCount}">${dayCount}일</button>`)
            .join('');
        element('adminSanctionDurationButtons').querySelectorAll('button[data-days]').forEach(durationButton => {
            durationButton.addEventListener('click', () => {
                element('adminSanctionUntilInput').value = toDateTimeLocalAfterDays(Number(durationButton.dataset.days));
            });
        });
        element('adminSanctionReasonPreset').innerHTML = '<option value="">사유 선택</option>'
            + REASON_PRESETS.map(reason => `<option value="${reason}">${reason}</option>`).join('')
            + `<option value="${CUSTOM_REASON_VALUE}">기타 (직접 입력)</option>`;
        element('adminSanctionReasonPreset').addEventListener('change', () => {
            element('adminSanctionCustomReason').classList.toggle('hidden', element('adminSanctionReasonPreset').value !== CUSTOM_REASON_VALUE);
        });
        document.querySelectorAll('input[name="adminSanctionType"]').forEach(typeRadio => {
            typeRadio.addEventListener('change', () => {
                element('adminSanctionDurationArea').classList.toggle('hidden', selectedSanctionType() !== 'temporary');
            });
        });
    }

    function resetForm() {
        document.querySelectorAll('input[name="adminSanctionType"]').forEach(typeRadio => {
            typeRadio.checked = typeRadio.value === 'temporary';
        });
        element('adminSanctionDurationArea').classList.remove('hidden');
        element('adminSanctionUntilInput').value = '';
        element('adminSanctionReasonPreset').value = '';
        element('adminSanctionCustomReason').value = '';
        element('adminSanctionCustomReason').classList.add('hidden');
        element('adminSanctionMemberIdInput').value = '';
    }

    function open(options = {}) {
        currentOptions = options;
        resetForm();
        const hasTargetMember = !!options.memberId;
        element('adminSanctionTarget').classList.toggle('hidden', !hasTargetMember);
        element('adminSanctionMemberIdArea').classList.toggle('hidden', hasTargetMember);
        element('adminSanctionTargetNickname').textContent = options.nickname || '-';
        element('adminSanctionTargetEmail').textContent = options.email || '-';
        element('adminSanctionModal').showModal();
        if (window.lucide) lucide.createIcons();
    }

    function close() {
        element('adminSanctionModal').close();
    }

    // 입력 검증 (대상, 사유, 기한) > 실패 사유 문자열 또는 요청 파라미터
    function buildSuspendRequest() {
        const targetMemberId = currentOptions.memberId || element('adminSanctionMemberIdInput').value.trim();
        if (!targetMemberId) return { failReason: '회원 ID를 입력하세요.' };

        const reasonPreset = element('adminSanctionReasonPreset').value;
        const suspendReason = reasonPreset === CUSTOM_REASON_VALUE
            ? element('adminSanctionCustomReason').value.trim()
            : reasonPreset;
        if (!suspendReason) return { failReason: '정지 사유를 선택하거나 입력하세요.' };

        let suspendedUntil = AdminSuspension.PERMANENT_UNTIL_VALUE;
        if (selectedSanctionType() === 'temporary') {
            suspendedUntil = element('adminSanctionUntilInput').value;
            if (!suspendedUntil) return { failReason: '정지 종료 일시를 지정하세요.' };
        }

        return {
            requestParams: {
                memberId: targetMemberId,
                suspendReason: suspendReason,
                suspendedUntil: suspendedUntil,
                reportId: currentOptions.reportId,
                reportType: currentOptions.reportType
            }
        };
    }

    async function submit() {
        const suspendRequest = buildSuspendRequest();
        if (suspendRequest.failReason) {
            alert(suspendRequest.failReason);
            return;
        }
        const submitButton = element('adminSanctionSubmitButton');
        submitButton.disabled = true;
        try {
            await AdminApi.call('/api/admin/members/suspend', suspendRequest.requestParams);
            close();
            if (typeof currentOptions.onCompleted === 'function') currentOptions.onCompleted();
        } catch (error) {
            AdminApi.alertError(error, '회원 정지');
        } finally {
            submitButton.disabled = false;
        }
    }

    document.addEventListener('DOMContentLoaded', () => {
        if (element('adminSanctionModal')) renderStaticControls();
    });

    return { open, close, submit };
})();

window.AdminSanctionModal = AdminSanctionModal;
