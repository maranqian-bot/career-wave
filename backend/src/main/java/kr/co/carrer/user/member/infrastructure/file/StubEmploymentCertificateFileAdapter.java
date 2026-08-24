package kr.co.carrer.user.member.infrastructure.file;

import kr.co.carrer.global.exception.CustomException;
import kr.co.carrer.user.member.dto.UserRegisterDto;
import kr.co.carrer.user.member.exception.UserAuthErrorCode;
import kr.co.carrer.user.member.service.EmploymentCertificateFilePort;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * 재직증명서 fileId 검증 stub — S3 미사용(mock-upload) 환경 전용.
 * 실 환경에서는 S3EmploymentCertificateFileAdapter 사용.
 * S3 adapter와 동일한 검증 로직(확장자 + Tika MIME)을 적용해
 * mock 환경과 생산 환경의 동작 일관성을 보장한다.
 * 중복 사용 방지는 company_profiles.cert_file_url UNIQUE 제약으로 DB 레벨에서 보장한다.
 *
 * 이전에는 @Profile({"local","test"}) 로 갈렸으나, 그 경우 profile 이 local/test 가
 * 아니면서 aws.s3.mock-upload=true 인 조합(데모 배포)에서 S3Config 가 통째로
 * 건너뛰어져 S3Client 빈이 없는데 S3 adapter 가 로드되며 기동에 실패했다.
 * S3Config 와 동일한 스위치로 통일해 두 빈이 항상 짝을 이루게 한다.
 */
@Slf4j
@ConditionalOnProperty(name = "aws.s3.mock-upload", havingValue = "true")
@Component
public class StubEmploymentCertificateFileAdapter implements EmploymentCertificateFilePort {

    private static final int MIN_FILE_ID_LENGTH = 8;
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final String ALLOWED_MIME = "application/pdf";

    private final Tika tika = new Tika();

    @Value("${aws.s3.bucket-name:careerwave-local}")
    private String bucketName;

    @Override
    public void validate(String fileId) {
        if (fileId == null || fileId.isBlank() || fileId.length() < MIN_FILE_ID_LENGTH) {
            throw new CustomException(UserAuthErrorCode.EMPLOYMENT_FILE_INVALID);
        }
    }

    @Override
    public String resolveUrl(String fileId) {
        return "https://s3.ap-northeast-2.amazonaws.com/" + bucketName + "/" + fileId;
    }

    @Override
    public String resolveFileName(String fileId) {
        return fileId;
    }

    @Override
    public UserRegisterDto.ResponseEmploymentCertificateUpload upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(UserAuthErrorCode.EMPLOYMENT_FILE_INVALID);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CustomException(UserAuthErrorCode.EMPLOYMENT_FILE_TOO_LARGE);
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        if (!originalName.toLowerCase().endsWith(".pdf")) {
            throw new CustomException(UserAuthErrorCode.EMPLOYMENT_FILE_UNSUPPORTED);
        }

        // Tika MIME 검증 (S3 adapter와 동일 — 확장자 위조 방어)
        try {
            String detectedMime = tika.detect(file.getBytes());
            if (!ALLOWED_MIME.equals(detectedMime)) {
                throw new CustomException(UserAuthErrorCode.EMPLOYMENT_FILE_UNSUPPORTED);
            }
        } catch (CustomException e) {
            throw e;
        } catch (IOException e) {
            throw new CustomException(UserAuthErrorCode.EMPLOYMENT_FILE_INVALID);
        }

        String fakeFileId = "stub-" + UUID.randomUUID();
        return new UserRegisterDto.ResponseEmploymentCertificateUpload(
                fakeFileId, originalName, ALLOWED_MIME, file.getSize(), Instant.now());
    }
}
