/**
 * 관리자 대시보드 최근 목록 (#827): 가입 회원 · 등록 물품 · 교환 완료 (기존 recent-* API 재사용)
 */
(function () {
    async function fetchJson(url) {
        const response = await adminFetch.post(url);
        if (!response.ok) throw new Error(`${url} ${response.status}`);
        return response.json();
    }


    function loadRecentLists() {
        fetchJson('/api/admin/dashboard/recent-members')
            .then(data => renderRecentMembers((data.members && data.members.content) || []))
            .catch(recentError => console.error('최근 회원 로드 실패:', recentError));
        fetchJson('/api/admin/dashboard/recent-items')
            .then(data => renderRecentItems((data.items && data.items.content) || []))
            .catch(recentError => console.error('최근 물품 로드 실패:', recentError));
        fetchJson('/api/admin/dashboard/recent-trades')
            .then(data => renderRecentTrades(data.recentTrades || []))
            .catch(recentError => console.error('최근 교환완료 로드 실패:', recentError));
    }

    function renderEmptyRow(tableBody, message) {
        tableBody.innerHTML = `<tr><td class="text-center text-base-content/50 py-6">${message}</td></tr>`;
    }

    function renderRecentMembers(members) {
        const tableBody = document.getElementById('recentMembersBody');
        if (members.length === 0) return renderEmptyRow(tableBody, '가입 회원이 없습니다');
        tableBody.innerHTML = members.map(member => {
            const profileSrc = (member.profileUrl && member.profileUrl.trim()) ? escapeHtml(member.profileUrl) : '/assets/img/default-150x150.png';
            return `
            <tr class="cursor-pointer" onclick="location.href='/admin/members/${escapeHtml(member.memberId)}'">
                <td class="w-10"><div class="avatar"><div class="w-8 rounded-full">
                    <img src="${profileSrc}" alt="프로필" onerror="this.src='/assets/img/default-150x150.png'"/></div></div></td>
                <td class="font-medium truncate max-w-40">${escapeHtml(member.nickname) || '익명'}</td>
                <td class="text-right text-xs text-base-content/55 whitespace-nowrap">${TimeUtils.getRelativeTime(member.createdDate)}</td>
            </tr>`;
        }).join('');
    }

    function renderRecentItems(items) {
        const tableBody = document.getElementById('recentItemsBody');
        if (items.length === 0) return renderEmptyRow(tableBody, '등록 물품이 없습니다');
        tableBody.innerHTML = items.map(item => {
            const mainImageUrl = (item.itemImages && item.itemImages.length > 0 && item.itemImages[0].imageUrl)
                ? escapeHtml(item.itemImages[0].imageUrl) : '/assets/img/default-150x150.png';
            return `
            <tr class="cursor-pointer" onclick="location.href='/admin/items/${escapeHtml(item.itemId)}'">
                <td class="w-12"><div class="avatar"><div class="w-9 rounded-lg">
                    <img src="${mainImageUrl}" alt="물품" onerror="this.src='/assets/img/default-150x150.png'"/></div></div></td>
                <td><div class="font-medium truncate max-w-40">${escapeHtml(item.itemName) || '-'}</div>
                    <div class="text-xs text-base-content/55 tabular-nums">${NumberUtils.formatPrice(item.price)}</div></td>
                <td class="text-right text-xs text-base-content/55 whitespace-nowrap">${TimeUtils.getRelativeTime(item.createdDate)}</td>
            </tr>`;
        }).join('');
    }

    function renderRecentTrades(trades) {
        const tableBody = document.getElementById('recentTradesBody');
        if (trades.length === 0) return renderEmptyRow(tableBody, '교환 완료된 거래가 없습니다');
        tableBody.innerHTML = trades.map(trade => `
            <tr class="cursor-pointer" onclick="location.href='/admin/trades/${escapeHtml(trade.tradeRequestHistoryId)}'">
                <td><div class="text-sm truncate max-w-56">${escapeHtml(trade.takeItem?.itemName || '-')} <span class="text-base-content/40">↔</span> ${escapeHtml(trade.giveItem?.itemName || '-')}</div>
                    <div class="text-xs text-base-content/55 truncate max-w-56">${escapeHtml(trade.takeItem?.member?.nickname || '익명')} · ${escapeHtml(trade.giveItem?.member?.nickname || '익명')}</div></td>
                <td class="text-right text-xs text-base-content/55 whitespace-nowrap">${TimeUtils.getRelativeTime(trade.updatedDate)}</td>
            </tr>`).join('');
    }

    loadRecentLists();
})();
