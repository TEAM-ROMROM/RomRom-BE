/**
 * 공통 JavaScript 유틸리티 모듈
 * 모든 페이지에서 공통으로 사용되는 JavaScript 기능
 */

// 관리자 인증 관련 유틸리티
const AdminAuth = {
    // 토큰 검증
    checkToken: function() {
        // localStorage에서 adminAccessToken 확인
        const accessToken = localStorage.getItem('adminAccessToken');
        return accessToken && accessToken.trim() !== '';
    },
    
    // 토큰 가져오기
    getAccessToken: function() {
        return localStorage.getItem('adminAccessToken');
    },

    // 로그인 페이지로 리다이렉트
    redirectToLogin: function(error = '') {
        // localStorage에서 토큰 제거
        localStorage.removeItem('adminAccessToken');
        
        const loginUrl = error ? `/admin/login?error=${error}` : '/admin/login';
        if (window.location.pathname !== '/admin/login') {
            window.location.href = loginUrl;
        }
    },
    
    // 로그아웃 처리
    logout: async function() {
        try {
            // 서버에 로그아웃 요청
            await adminFetch('/api/admin/logout', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                }
            });
        } catch (error) {
            console.error('로그아웃 요청 실패:', error);
        } finally {
            // localStorage 정리
            localStorage.removeItem('adminAccessToken');
            // 로그인 페이지로 이동
            window.location.href = '/admin/login';
        }
    },

    // API 호출 시 400번대 응답 처리
    handleApiResponse: function(response) {
        if (response.status >= 400 && response.status < 500) {
            console.warn('클라이언트 오류 - 로그인 페이지로 이동:', response.status);
            AdminAuth.redirectToLogin();
            return false;
        }
        return true;
    }
};

// fetch API 래퍼 (관리자용)
const adminFetch = async function(url, options = {}) {
    try {
        // 기본 헤더 설정
        const defaultHeaders = {
            'Content-Type': 'application/json',
        };
        
        // accessToken이 있으면 Authorization 헤더 추가
        const accessToken = AdminAuth.getAccessToken();
        if (accessToken) {
            defaultHeaders['Authorization'] = `Bearer ${accessToken}`;
        }
        
        const response = await fetch(url, {
            ...options,
            headers: {
                ...defaultHeaders,
                ...options.headers,
            },
            credentials: 'include', // refreshToken 쿠키 자동 전송
        });

        // 400번대 응답 처리
        AdminAuth.handleApiResponse(response);
        
        return response;
    } catch (error) {
        console.error('API 호출 오류:', error);
        throw error;
    }
};

// 관리자 페이지 접근 권한 체크
function checkAdminAccess() {
    const currentPath = window.location.pathname;
    
    // 관리자 페이지가 아니면 체크하지 않음
    if (!currentPath.startsWith('/admin')) {
        return;
    }
    
    // 로그인 페이지는 체크하지 않음
    if (currentPath === '/admin/login') {
        return;
    }
    
    // 토큰 확인을 약간 지연시켜 쿠키 설정이 완료되도록 함
    setTimeout(() => {
        if (!AdminAuth.checkToken()) {
            console.warn('관리자 토큰이 없습니다. 로그인 페이지로 이동합니다.');
            AdminAuth.redirectToLogin('no_token');
        }
    }, 50);
}

// 공통 초기화 함수
document.addEventListener('DOMContentLoaded', function() {
    console.log('Common.js loaded successfully');
    
    // 자동 접근 권한 체크는 주석 처리 - 서버 측 필터에서 처리
    // checkAdminAccess();
});

// 시간 표시 유틸리티
const TimeUtils = {
    /**
     * 상대적 시간 표시 (방금전, X분전, X시간전, X일전, X달전, X년전)
     * @param {string|Date} dateString - 날짜 문자열 또는 Date 객체
     * @returns {string} 상대적 시간 문자열
     */
    getRelativeTime: function(dateString) {
        const now = new Date();
        const targetDate = new Date(dateString);
        const diffInSeconds = Math.floor((now - targetDate) / 1000);
        
        if (diffInSeconds < 60) {
            return '방금전';
        }
        
        const diffInMinutes = Math.floor(diffInSeconds / 60);
        if (diffInMinutes < 60) {
            return `${diffInMinutes}분전`;
        }
        
        const diffInHours = Math.floor(diffInMinutes / 60);
        if (diffInHours < 24) {
            return `${diffInHours}시간전`;
        }
        
        const diffInDays = Math.floor(diffInHours / 24);
        if (diffInDays < 30) {
            return `${diffInDays}일전`;
        }
        
        const diffInMonths = Math.floor(diffInDays / 30);
        if (diffInMonths < 12) {
            return `${diffInMonths}달전`;
        }
        
        const diffInYears = Math.floor(diffInMonths / 12);
        return `${diffInYears}년전`;
    },

    /**
     * 날짜를 YYYY-MM-DD 형식으로 포맷
     * @param {string|Date} dateString - 날짜 문자열 또는 Date 객체
     * @returns {string} 포맷된 날짜 문자열
     */
    formatDate: function(dateString) {
        const date = new Date(dateString);
        return date.toISOString().split('T')[0];
    },

    /**
     * 날짜를 YYYY-MM-DD HH:mm 형식으로 포맷
     * @param {string|Date} dateString - 날짜 문자열 또는 Date 객체
     * @returns {string} 포맷된 날짜시간 문자열
     */
    formatDateTime: function(dateString) {
        const date = new Date(dateString);
        return date.toLocaleString('ko-KR', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    }
};

// 숫자 포맷 유틸리티
const NumberUtils = {
    /**
     * 숫자를 천 단위 콤마로 포맷
     * @param {number} number - 포맷할 숫자
     * @returns {string} 포맷된 숫자 문자열
     */
    formatNumber: function(number) {
        return number ? number.toLocaleString() : '0';
    },

    /**
     * 가격을 원화 형식으로 포맷
     * @param {number} price - 가격
     * @returns {string} 포맷된 가격 문자열
     */
    formatPrice: function(price) {
        return price ? `₩${price.toLocaleString()}` : '가격 없음';
    }
};

// 전역 객체로 노출
window.AdminAuth = AdminAuth;
window.adminFetch = adminFetch;
window.TimeUtils = TimeUtils;
window.NumberUtils = NumberUtils;