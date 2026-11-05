/**
 * 관리자 푸시 발송 > 발송 기록 (#874): 목록·열람률, 예약 취소, 상세, 발송 중이면 자동 새로고침
 */
const PushCampaigns = (function () {
    const PAGE_SIZE = 20;
    // 발송 중 캠페인 있을 때 목록 새로고침 간격 (밀리초)
    const SENDING_REFRESH_INTERVAL_MILLISECONDS = 5000;
    const STATUS_BADGE_BY_CODE = {
        SCHEDULED: { text: '예약', cls: 'badge-info' },
        SENDING: { text: '발송 중', cls: 'badge-warning' },
        COMPLETED: { text: '완료', cls: 'badge-success' },
        FAILED: { text: '실패', cls: 'badge-error' },
        CANCELED: { text: '취소', cls: 'badge-ghost' },
    };
    const TARGET_TYPE_LABEL_BY_CODE = { ALL: '전체', SEGMENT: '조건', MEMBERS: '회원 지정', TEST: '테스트' };
    const PURPOSE_LABEL_BY_CODE = { INFORMATION: '정보성', ADVERTISEMENT: '광고성' };
    // 목록 행 데이터 (pushCampaignId > 캠페인)
    const campaignById = {};
    let sendingRefreshTimer = null;
    const state = { currentPage: 0 };

    function statusBadgeHtml(statusCode) {
        const statusBadge = STATUS_BADGE_BY_CODE[statusCode] || { text: statusCode, cls: 'badge-neutral' };
        return `<span class="badge ${statusBadge.cls} badge-sm whitespace-nowrap">${escapeHtml(statusBadge.text)}</span>`;
    }

    // 열람률 = 열람 회원 / 발송 성공 (토큰 기준 근사)
    function openRateText(pushCampaign) {
        const sentCount = pushCampaign.sentCount || 0;
        const openedCount = pushCampaign.openedCount || 0;
        if (sentCount === 0) return `${openedCount}`;
        return `${openedCount} (${Math.round((openedCount / sentCount) * 1000) / 10}%)`;
    }

    function renderRow(pushCampaign) {
        campaignById[pushCampaign.pushCampaignId] = pushCampaign;
        const campaignIdText = escapeHtml(pushCampaign.pushCampaignId);
        const isCancelable = pushCampaign.status === 'SCHEDULED';
        const scheduleText = pushCampaign.status === 'SCHEDULED'
            ? `<div class="text-[11px] text-info">${escapeHtml(TimeUtils.formatDateTime(pushCampaign.scheduledAt))} 예약</div>` : '';
        return `<tr class="hover">
            <td class="text-xs whitespace-nowrap">${escapeHtml(TimeUtils.formatDateTime(pushCampaign.createdDate))}${scheduleText}</td>
            <td class="max-w-56 truncate" title="${escapeHtml(pushCampaign.title)}">${escapeHtml(pushCampaign.title)}</td>
            <td class="text-xs">${escapeHtml(PURPOSE_LABEL_BY_CODE[pushCampaign.purpose] || pushCampaign.purpose)}</td>
            <td class="text-xs">${escapeHtml(TARGET_TYPE_LABEL_BY_CODE[pushCampaign.targetType] || pushCampaign.targetType)}</td>
            <td>${statusBadgeHtml(pushCampaign.status)}</td>
            <td class="text-right">${NumberUtils.formatNumber(pushCampaign.targetMemberCount)}</td>
            <td class="text-right">${NumberUtils.formatNumber(pushCampaign.sentCount)}</td>
            <td class="text-right">${NumberUtils.formatNumber(pushCampaign.failureCount)}</td>
            <td class="text-right whitespace-nowrap">${escapeHtml(openRateText(pushCampaign))}</td>
            <td class="whitespace-nowrap">
                <button class="btn btn-ghost btn-xs" title="상세" onclick="PushCampaigns.openDetail('${campaignIdText}')">
                    <i data-lucide="file-text" class="size-4"></i>
                </button>
                ${isCancelable ? `<button class="btn btn-ghost btn-xs text-error" title="예약 취소" onclick="PushCampaigns.cancel('${campaignIdText}')">
                    <i data-lucide="x-circle" class="size-4"></i></button>` : ''}
            </td>
        </tr>`;
    }

    async function load(pageNumber) {
        state.currentPage = pageNumber || 0;
        try {
            const listResponse = await AdminApi.call('/api/admin/push-campaigns/list',
                { pageNumber: state.currentPage, pageSize: PAGE_SIZE });
            const pushCampaigns = listResponse.pushCampaigns || [];
            document.getElementById('campaignTableBody').innerHTML = pushCampaigns.map(renderRow).join('');
            document.getElementById('campaignEmptyMessage').classList.toggle('hidden', pushCampaigns.length > 0);
            AdminPager.render(document.getElementById('campaignPagination'), listResponse.currentPage || 0,
                listResponse.totalPages || 0, load);
            if (window.lucide) lucide.createIcons();
            scheduleRefreshWhileSending(pushCampaigns);
        } catch (error) {
            console.error('[admin push] campaign list failed', error);
        }
    }

    // 발송 중 캠페인 있으면 잠시 후 현재 페이지 다시 조회
    function scheduleRefreshWhileSending(pushCampaigns) {
        clearTimeout(sendingRefreshTimer);
        const hasSendingCampaign = pushCampaigns.some(pushCampaign => pushCampaign.status === 'SENDING');
        if (hasSendingCampaign) {
            sendingRefreshTimer = setTimeout(() => load(state.currentPage), SENDING_REFRESH_INTERVAL_MILLISECONDS);
        }
    }

    async function cancel(pushCampaignId) {
        if (!confirm('이 예약 푸시를 취소할까요?')) return;
        try {
            await AdminApi.call('/api/admin/push-campaigns/cancel', { pushCampaignId: pushCampaignId });
            load(state.currentPage);
        } catch (error) {
            AdminApi.alertError(error, '예약 취소');
        }
    }

    function detailRowHtml(labelText, valueHtml) {
        return `<div class="grid grid-cols-3 gap-2 py-1 border-b border-base-200">
            <span class="text-base-content/60">${escapeHtml(labelText)}</span><span class="col-span-2 break-all">${valueHtml}</span></div>`;
    }

    async function openDetail(pushCampaignId) {
        try {
            const detailResponse = await AdminApi.call('/api/admin/push-campaigns/detail', { pushCampaignId: pushCampaignId });
            const pushCampaign = detailResponse.pushCampaign || campaignById[pushCampaignId];
            const formatDateTimeOrDash = dateTimeText => dateTimeText ? escapeHtml(TimeUtils.formatDateTime(dateTimeText)) : '-';
            document.getElementById('campaignDetailContent').innerHTML = [
                detailRowHtml('상태', statusBadgeHtml(pushCampaign.status)),
                detailRowHtml('목적·대상', escapeHtml(`${PURPOSE_LABEL_BY_CODE[pushCampaign.purpose]} · ${TARGET_TYPE_LABEL_BY_CODE[pushCampaign.targetType]}`)),
                detailRowHtml('제목', escapeHtml(pushCampaign.title)),
                detailRowHtml('본문', `<span class="whitespace-pre-line">${escapeHtml(pushCampaign.body)}</span>`),
                detailRowHtml('이미지', escapeHtml(pushCampaign.imageUrl || '-')),
                detailRowHtml('이동 링크', escapeHtml(pushCampaign.deepLink || '-')),
                detailRowHtml('대상 조건', `<code class="text-xs">${escapeHtml(pushCampaign.targetFilter || '{}')}</code>`),
                detailRowHtml('예약·시작·완료', `${formatDateTimeOrDash(pushCampaign.scheduledAt)} / ${formatDateTimeOrDash(pushCampaign.startedAt)} / ${formatDateTimeOrDash(pushCampaign.completedAt)}`),
                detailRowHtml('대상 회원', NumberUtils.formatNumber(pushCampaign.targetMemberCount)),
                detailRowHtml('성공·실패·삭제 토큰', `${NumberUtils.formatNumber(pushCampaign.sentCount)} / ${NumberUtils.formatNumber(pushCampaign.failureCount)} / ${NumberUtils.formatNumber(pushCampaign.removedTokenCount)}`),
                detailRowHtml('열람', escapeHtml(openRateText(pushCampaign))),
                detailRowHtml('실패 사유', escapeHtml(pushCampaign.failureReason || '-')),
            ].join('');
            document.getElementById('campaignDetailModal').showModal();
        } catch (error) {
            AdminApi.alertError(error, '캠페인 상세');
        }
    }

    document.addEventListener('DOMContentLoaded', () => load(0));

    return {
        load, cancel, openDetail,
        get currentPage() { return state.currentPage; },
    };
})();

window.PushCampaigns = PushCampaigns;
