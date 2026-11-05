/**
 * 관리자 푸시 발송 > 작성 (#874): 입력 수집, 딥링크 조합, 휴대폰 미리보기, 대상 수 확인, 테스트 발송, 즉시·예약 발송
 * 광고 표시 문구·야간 구간은 서버 AdvertisementPushRule 과 같은 값 (최종 판정은 서버)
 */
const PushCompose = (function () {
    const ADVERTISEMENT_TITLE_PREFIX = '(광고) ';
    const ADVERTISEMENT_BODY_SUFFIX = '\n수신거부: 설정 > 알림 설정';
    // 야간 금지 구간 (시, KST): 21시 포함 ~ 8시 제외
    const NIGHT_RESTRICTION_START_HOUR = 21;
    const NIGHT_RESTRICTION_END_HOUR = 8;
    // 딥링크 종류 > 접두 (앱 deep_link_router 와 같은 경로)
    const DEEP_LINK_PREFIX_BY_TYPE = {
        NOTICE: 'romrom://notice/detail?announcementId=',
        ITEM: 'romrom://item/detail?itemId=',
    };
    const DEEP_LINK_PLACEHOLDER_BY_TYPE = {
        NOTICE: '공지 ID (UUID)',
        ITEM: '물품 ID (UUID)',
        CUSTOM: 'romrom://... 또는 https://...',
    };
    // 발송 진행 중 여부 (중복 제출 방지)
    let isSubmitting = false;

    function getElement(elementId) {
        return document.getElementById(elementId);
    }

    function selectedRadioValue(radioName) {
        const checkedRadio = document.querySelector(`input[name="${radioName}"]:checked`);
        return checkedRadio ? checkedRadio.value : null;
    }

    function isAdvertisement() {
        return selectedRadioValue('pushPurpose') === 'ADVERTISEMENT';
    }

    function isNightRestrictedHour(hour) {
        return hour >= NIGHT_RESTRICTION_START_HOUR || hour < NIGHT_RESTRICTION_END_HOUR;
    }

    // 딥링크 종류 + 입력 > 최종 링크 (없음 > 빈 값)
    function buildDeepLink() {
        const deepLinkType = getElement('deepLinkTypeSelect').value;
        const deepLinkValue = getElement('deepLinkValueInput').value.trim();
        if (deepLinkType === 'NONE' || !deepLinkValue) return '';
        if (deepLinkType === 'CUSTOM') return deepLinkValue;
        return DEEP_LINK_PREFIX_BY_TYPE[deepLinkType] + deepLinkValue;
    }

    // 대상 조건 (선택한 대상 종류의 필드만)
    function collectTargetParams() {
        const targetType = getElement('targetTypeSelect').value;
        const targetParams = { targetType: targetType };
        if (targetType === 'SEGMENT') {
            targetParams.deviceType = getElement('segmentDeviceTypeSelect').value;
            targetParams.isMarketingAgreed = getElement('segmentMarketingSelect').value;
            const activityType = getElement('segmentActivityTypeSelect').value;
            const activityDays = getElement('segmentActivityDaysInput').value;
            if (activityType === 'ACTIVE') targetParams.activeWithinDays = activityDays;
            if (activityType === 'INACTIVE') targetParams.inactiveForDays = activityDays;
            targetParams.joinedFromDate = getElement('segmentJoinedFromInput').value;
            targetParams.joinedToDate = getElement('segmentJoinedToInput').value;
            targetParams.siDo = getElement('segmentSiDoSelect').value;
        }
        if (targetType === 'MEMBERS') {
            targetParams.memberIdentifiers = getElement('memberIdentifiersInput').value;
        }
        return targetParams;
    }

    function collectContentParams() {
        return {
            purpose: selectedRadioValue('pushPurpose'),
            pushTitle: getElement('pushTitleInput').value.trim(),
            pushBody: getElement('pushBodyInput').value.trim(),
            pushImageUrl: getElement('pushImageUrlInput').value.trim(),
            pushDeepLink: buildDeepLink(),
        };
    }

    function hasRequiredContent(contentParams) {
        if (!contentParams.pushTitle || !contentParams.pushBody) {
            alert('제목과 본문을 입력하세요.');
            return false;
        }
        return true;
    }

    // 휴대폰 미리보기·목적 안내·야간 경고 갱신
    function onInputChanged() {
        const contentParams = collectContentParams();
        const isAdvertisementPurpose = isAdvertisement();
        const titleText = contentParams.pushTitle || '제목';
        const bodyText = contentParams.pushBody || '본문';
        getElement('phonePreviewTitle').textContent = isAdvertisementPurpose ? ADVERTISEMENT_TITLE_PREFIX + titleText : titleText;
        getElement('phonePreviewBody').textContent = isAdvertisementPurpose ? bodyText + ADVERTISEMENT_BODY_SUFFIX : bodyText;
        const previewImage = getElement('phonePreviewImage');
        const isHttpsImage = contentParams.pushImageUrl.startsWith('https://');
        previewImage.classList.toggle('hidden', !isHttpsImage);
        if (isHttpsImage) previewImage.src = contentParams.pushImageUrl;
        getElement('purposeHint').textContent = isAdvertisementPurpose
            ? '마케팅 수신 동의 회원에게만, "(광고)"·수신거부 안내 자동, 21:00~08:00 금지'
            : '콘텐츠 알림 동의 회원에게 푸시, 대상 전원 알림함 기록';
        updateNightWarning();
    }

    function updateNightWarning() {
        const isScheduled = selectedRadioValue('sendTiming') === 'SCHEDULE';
        const scheduledAtText = getElement('scheduledAtInput').value;
        const sendHour = isScheduled && scheduledAtText ? new Date(scheduledAtText).getHours() : new Date().getHours();
        getElement('nightWarningText').classList.toggle('hidden', !(isAdvertisement() && isNightRestrictedHour(sendHour)));
    }

    function onDeepLinkTypeChanged() {
        const deepLinkType = getElement('deepLinkTypeSelect').value;
        const deepLinkValueInput = getElement('deepLinkValueInput');
        deepLinkValueInput.classList.toggle('hidden', deepLinkType === 'NONE');
        deepLinkValueInput.placeholder = DEEP_LINK_PLACEHOLDER_BY_TYPE[deepLinkType] || '';
        deepLinkValueInput.value = '';
    }

    function onTargetTypeChanged() {
        const targetType = getElement('targetTypeSelect').value;
        getElement('segmentPanel').classList.toggle('hidden', targetType !== 'SEGMENT');
        getElement('memberIdentifiersInput').classList.toggle('hidden', targetType !== 'MEMBERS');
        getElement('targetPreviewText').textContent = '-';
    }

    function onSendTimingChanged() {
        getElement('scheduledAtInput').classList.toggle('hidden', selectedRadioValue('sendTiming') !== 'SCHEDULE');
        updateNightWarning();
    }

    // 대상 수 확인 (dry run) > 표시 문장
    async function previewTarget() {
        const contentParams = collectContentParams();
        if (!hasRequiredContent(contentParams)) return null;
        try {
            const previewResponse = await AdminApi.call('/api/admin/push-campaigns/preview',
                { ...contentParams, ...collectTargetParams() });
            const previewText = `대상 ${NumberUtils.formatNumber(previewResponse.targetMemberCount)}명`
                + ` · 수신 동의 ${NumberUtils.formatNumber(previewResponse.pushConsentedMemberCount)}명`
                + ` · 토큰 ${NumberUtils.formatNumber(previewResponse.pushTokenCount)}개`;
            getElement('targetPreviewText').textContent = previewText;
            return previewText;
        } catch (error) {
            AdminApi.alertError(error, '대상 수 확인');
            return null;
        }
    }

    async function sendTest() {
        const contentParams = collectContentParams();
        if (!hasRequiredContent(contentParams) || isSubmitting) return;
        isSubmitting = true;
        try {
            const testResponse = await AdminApi.call('/api/admin/push-campaigns/test-send', contentParams);
            const testCampaign = testResponse.pushCampaign || {};
            alert(`테스트 발송: 성공 ${testCampaign.sentCount || 0}건, 실패 ${testCampaign.failureCount || 0}건`);
            PushCampaigns.load(0);
        } catch (error) {
            AdminApi.alertError(error, '테스트 발송');
        } finally {
            isSubmitting = false;
        }
    }

    // 발송 확인 모달: 대상 수 + 발송 시점
    async function confirmSend() {
        const isScheduled = selectedRadioValue('sendTiming') === 'SCHEDULE';
        if (isScheduled && !getElement('scheduledAtInput').value) {
            alert('예약 시각을 선택하세요.');
            return;
        }
        const previewText = await previewTarget();
        if (!previewText) return;
        const timingText = isScheduled
            ? `${TimeUtils.formatDateTime(getElement('scheduledAtInput').value)} 예약 발송`
            : '지금 바로 발송';
        const purposeText = isAdvertisement() ? '광고성' : '정보성';
        getElement('sendConfirmText').textContent = `${purposeText} 푸시를 ${timingText}합니다.\n${previewText}`;
        getElement('sendConfirmModal').showModal();
    }

    async function submitSend() {
        if (isSubmitting) return;
        isSubmitting = true;
        const isScheduled = selectedRadioValue('sendTiming') === 'SCHEDULE';
        try {
            await AdminApi.call('/api/admin/push-campaigns/create', {
                ...collectContentParams(),
                ...collectTargetParams(),
                scheduledAt: isScheduled ? getElement('scheduledAtInput').value : '',
            });
            getElement('sendConfirmModal').close();
            alert(isScheduled ? '예약했습니다.' : '발송을 시작했습니다.');
            PushCampaigns.load(0);
        } catch (error) {
            AdminApi.alertError(error, '푸시 발송');
        } finally {
            isSubmitting = false;
        }
    }

    document.addEventListener('DOMContentLoaded', onInputChanged);

    return {
        onInputChanged, onDeepLinkTypeChanged, onTargetTypeChanged, onSendTimingChanged,
        previewTarget, sendTest, confirmSend, submitSend,
    };
})();

window.PushCompose = PushCompose;
