package com.streamsheet.spring.storage

import java.io.InputStream
import java.net.URI

/**
 * 파일 저장소 추상화 인터페이스
 * File storage abstraction interface
 *
 * NOTE: 로컬 파일 시스템, S3, Azure Blob 등 다양한 저장소를 지원하기 위한 인터페이스입니다.
 * Interface to support various storage backends like Local Filesystem, S3, Azure Blob.
 */
interface FileStorage {
    /**
     * 파일 저장
     * Save file
     *
     * @param fileName 저장할 파일 이름 / File name to save
     * @param inputStream 파일 내용 스트림 / File content stream
     * @param contentType 컨텐츠 타입 (MIME) / Content type (MIME)
     * @return 저장된 파일에 접근 가능한 URI (또는 키) / Accessible URI (or key) of the saved file
     */
    fun save(fileName: String, inputStream: InputStream, contentType: String): URI

    /**
     * 파일 삭제
     * Delete file
     *
     * @param fileUri 삭제할 파일의 URI / URI of the file to delete
     */
    fun delete(fileUri: URI)
}
