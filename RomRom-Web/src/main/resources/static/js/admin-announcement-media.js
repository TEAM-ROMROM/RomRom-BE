/**
 * 관리자 공지 이미지·링크 입력 (#873): 이미지 파일 업로드(기존 /api/image/upload) > URL 채움 > 미리보기, 링크 형식 확인
 * 입력 id 규칙: {prefix}ImageUrl, {prefix}ImageFile, {prefix}ImagePreview, {prefix}LinkUrl (prefix: input 작성, edit 수정)
 */
const AnnouncementMediaInput = {
    // 서버 AdminLinkUrlRule 과 같은 허용 형식 (바꾸면 함께 변경)
    allowedLinkUrlPrefixes: ['https://', 'romrom://'],

    // 이미지 URL 입력·파일 선택 > 미리보기 연결
    bind: function (fieldIdPrefix) {
        const imageUrlInput = document.getElementById(`${fieldIdPrefix}ImageUrl`);
        const imageFileInput = document.getElementById(`${fieldIdPrefix}ImageFile`);
        imageUrlInput.addEventListener('input', () => this.updatePreview(fieldIdPrefix));
        imageFileInput.addEventListener('change', () => this.uploadSelectedImage(fieldIdPrefix));
    },

    setValues: function (fieldIdPrefix, imageUrl, linkUrl) {
        document.getElementById(`${fieldIdPrefix}ImageUrl`).value = imageUrl || '';
        document.getElementById(`${fieldIdPrefix}LinkUrl`).value = linkUrl || '';
        document.getElementById(`${fieldIdPrefix}ImageFile`).value = '';
        this.updatePreview(fieldIdPrefix);
    },

    readValues: function (fieldIdPrefix) {
        return {
            imageUrl: document.getElementById(`${fieldIdPrefix}ImageUrl`).value.trim(),
            linkUrl: document.getElementById(`${fieldIdPrefix}LinkUrl`).value.trim()
        };
    },

    // 빈 값 또는 허용 형식 (대소문자 무시)
    isAllowedLinkUrl: function (linkUrl) {
        if (!linkUrl) return true;
        const lowerCaseLinkUrl = linkUrl.toLowerCase();
        return this.allowedLinkUrlPrefixes.some(prefix => lowerCaseLinkUrl.startsWith(prefix));
    },

    updatePreview: function (fieldIdPrefix) {
        const imageUrl = document.getElementById(`${fieldIdPrefix}ImageUrl`).value.trim();
        const imagePreview = document.getElementById(`${fieldIdPrefix}ImagePreview`);
        imagePreview.classList.toggle('hidden', !imageUrl);
        if (imageUrl) {
            imagePreview.src = imageUrl;
        } else {
            imagePreview.removeAttribute('src');
        }
    },

    // 파일 업로드 > 받은 URL 입력 (업로드 중 버튼 비활성)
    uploadSelectedImage: async function (fieldIdPrefix) {
        const imageFileInput = document.getElementById(`${fieldIdPrefix}ImageFile`);
        const selectedImageFile = imageFileInput.files && imageFileInput.files[0];
        if (!selectedImageFile) return;
        const uploadButtonLabel = imageFileInput.closest('label');
        uploadButtonLabel.classList.add('btn-disabled');
        try {
            const imageUploadFormData = new FormData();
            imageUploadFormData.append('images', selectedImageFile);
            const uploadResponse = await adminFetch('/api/image/upload', { method: 'POST', body: imageUploadFormData });
            if (!uploadResponse.ok) throw new Error(`이미지 업로드 실패 (${uploadResponse.status})`);
            const uploadResult = await uploadResponse.json();
            const uploadedImageUrl = uploadResult.imageUrls && uploadResult.imageUrls[0];
            if (!uploadedImageUrl) throw new Error('업로드 결과에 이미지 주소가 없습니다.');
            document.getElementById(`${fieldIdPrefix}ImageUrl`).value = uploadedImageUrl;
            this.updatePreview(fieldIdPrefix);
        } catch (uploadError) {
            AdminApi.alertError(uploadError, '공지 이미지 업로드');
        } finally {
            uploadButtonLabel.classList.remove('btn-disabled');
            imageFileInput.value = '';
        }
    }
};

window.AnnouncementMediaInput = AnnouncementMediaInput;
