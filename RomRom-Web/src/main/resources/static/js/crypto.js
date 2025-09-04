/**
 * 비밀번호 암호화/복호화 모듈
 * AES-GCM 알고리즘 사용
 */

class Crypto {
    constructor() {
        this.algorithm = 'AES-GCM';
        this.ivLength = 12; // GCM IV length
        this.tagLength = 16; // GCM tag length
    }

    /**
     * Base64 문자열을 ArrayBuffer로 변환
     * @param {string} base64 - Base64 인코딩된 문자열
     * @returns {ArrayBuffer} ArrayBuffer
     */
    base64ToArrayBuffer(base64) {
        const binaryString = atob(base64);
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
            bytes[i] = binaryString.charCodeAt(i);
        }
        return bytes.buffer;
    }

    /**
     * ArrayBuffer를 Base64 문자열로 변환
     * @param {ArrayBuffer} buffer - ArrayBuffer
     * @returns {string} Base64 인코딩된 문자열
     */
    arrayBufferToBase64(buffer) {
        const bytes = new Uint8Array(buffer);
        let binary = '';
        for (let i = 0; i < bytes.byteLength; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
    }

    /**
     * 문자열을 UTF-8 ArrayBuffer로 변환
     * @param {string} str - 변환할 문자열
     * @returns {ArrayBuffer} UTF-8 인코딩된 ArrayBuffer
     */
    stringToArrayBuffer(str) {
        const encoder = new TextEncoder();
        return encoder.encode(str);
    }

    /**
     * ArrayBuffer를 UTF-8 문자열로 변환
     * @param {ArrayBuffer} buffer - 변환할 ArrayBuffer
     * @returns {string} UTF-8 문자열
     */
    arrayBufferToString(buffer) {
        const decoder = new TextDecoder();
        return decoder.decode(buffer);
    }

    /**
     * 비밀번호를 AES-GCM 알고리즘으로 암호화
     * @param {string} password - 암호화할 비밀번호
     * @param {string} secretKey - 암호화 키 (Base64 인코딩된 문자열)
     * @returns {Promise<string>} 암호화된 비밀번호 (Base64 인코딩)
     */
    async encryptPassword(password, secretKey) {
        try {
            // 키 디코딩 및 생성
            const keyBuffer = this.base64ToArrayBuffer(secretKey);
            const cryptoKey = await crypto.subtle.importKey(
                'raw',
                keyBuffer,
                { name: this.algorithm },
                false,
                ['encrypt']
            );

            // IV 생성
            const iv = crypto.getRandomValues(new Uint8Array(this.ivLength));
            
            // 비밀번호를 ArrayBuffer로 변환
            const passwordBuffer = this.stringToArrayBuffer(password);

            // 암호화
            const encryptedBuffer = await crypto.subtle.encrypt(
                {
                    name: this.algorithm,
                    iv: iv,
                    tagLength: this.tagLength * 8
                },
                cryptoKey,
                passwordBuffer
            );

            // IV + 암호화된 데이터 결합
            const encryptedArray = new Uint8Array(encryptedBuffer);
            const combinedArray = new Uint8Array(this.ivLength + encryptedArray.length);
            combinedArray.set(iv, 0);
            combinedArray.set(encryptedArray, this.ivLength);

            // Base64로 인코딩하여 반환
            return this.arrayBufferToBase64(combinedArray.buffer);

        } catch (error) {
            console.error('비밀번호 암호화 실패:', error);
            throw new Error('비밀번호 암호화에 실패했습니다.');
        }
    }

    /**
     * 암호화된 비밀번호를 복호화
     * @param {string} encryptedPassword - 암호화된 비밀번호 (Base64 인코딩)
     * @param {string} secretKey - 복호화 키 (Base64 인코딩된 문자열)
     * @returns {Promise<string>} 복호화된 비밀번호
     */
    async decryptPassword(encryptedPassword, secretKey) {
        try {
            // 암호화된 데이터 디코딩
            const encryptedBuffer = this.base64ToArrayBuffer(encryptedPassword);
            const encryptedArray = new Uint8Array(encryptedBuffer);

            // IV와 암호화된 데이터 분리
            const iv = encryptedArray.slice(0, this.ivLength);
            const encryptedData = encryptedArray.slice(this.ivLength);

            // 키 디코딩 및 생성
            const keyBuffer = this.base64ToArrayBuffer(secretKey);
            const cryptoKey = await crypto.subtle.importKey(
                'raw',
                keyBuffer,
                { name: this.algorithm },
                false,
                ['decrypt']
            );

            // 복호화
            const decryptedBuffer = await crypto.subtle.decrypt(
                {
                    name: this.algorithm,
                    iv: iv,
                    tagLength: this.tagLength * 8
                },
                cryptoKey,
                encryptedData
            );

            // ArrayBuffer를 문자열로 변환하여 반환
            return this.arrayBufferToString(decryptedBuffer);

        } catch (error) {
            console.error('비밀번호 복호화 실패:', error);
            throw new Error('비밀번호 복호화에 실패했습니다.');
        }
    }

    /**
     * 새로운 AES 키 생성 (256bit)
     * @returns {Promise<string>} Base64로 인코딩된 AES 키
     */
    async generateAESKey() {
        try {
            const key = await crypto.subtle.generateKey(
                {
                    name: this.algorithm,
                    length: 256
                },
                true,
                ['encrypt', 'decrypt']
            );

            const keyBuffer = await crypto.subtle.exportKey('raw', key);
            return this.arrayBufferToBase64(keyBuffer);

        } catch (error) {
            console.error('AES 키 생성 실패:', error);
            throw new Error('AES 키 생성에 실패했습니다.');
        }
    }

    /**
     * 비밀번호 유효성 검증
     * @param {string} password - 검증할 비밀번호
     * @returns {boolean} 유효성 검증 결과
     */
    validatePassword(password) {
        if (!password || password.trim().length === 0) {
            return false;
        }
        
        // 8자 이상, 대소문자, 숫자, 특수문자 포함
        const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
        return passwordRegex.test(password);
    }
}

// 전역 인스턴스 생성
const Crypto = new Crypto();

// CommonJS/AMD 환경 지원
if (typeof module !== 'undefined' && module.exports) {
    module.exports = Crypto;
} else if (typeof define === 'function' && define.amd) {
    define(function() {
        return Crypto;
    });
}

// 사용 예시:
/*
// 키 생성
const secretKey = await Crypto.generateAESKey();

// 암호화
const encryptedPassword = await Crypto.encryptPassword('myPassword123!', secretKey);

// 복호화
const decryptedPassword = await Crypto.decryptPassword(encryptedPassword, secretKey);

// 비밀번호 유효성 검증
const isValid = Crypto.validatePassword('myPassword123!');
*/