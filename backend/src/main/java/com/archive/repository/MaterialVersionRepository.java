package com.archive.repository;

import com.archive.entity.MaterialVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 材料版本仓库.
 *
 * @author Mavis
 */
@Repository
public interface MaterialVersionRepository extends JpaRepository<MaterialVersion, Long> {

    List<MaterialVersion> findByMaterialIdOrderByVersionNoDesc(Long materialId);

    Optional<MaterialVersion> findFirstByMaterialIdOrderByVersionNoDesc(Long materialId);

    Optional<MaterialVersion> findByMaterialIdAndVersionNo(Long materialId, Integer versionNo);

    Page<MaterialVersion> findByParseStatus(String parseStatus, Pageable pageable);

    long countByMaterialId(Long materialId);

    Optional<MaterialVersion> findByMaterialIdAndSha256(Long materialId, String sha256);

    @Query("SELECT mv FROM MaterialVersion mv WHERE mv.sha256 = :sha256")
    List<MaterialVersion> findBySha256(@Param("sha256") String sha256);

    @Modifying
    @Query("UPDATE MaterialVersion mv SET mv.parseStatus = :status, " +
           "mv.parsedAt = CURRENT_TIMESTAMP, mv.parseError = :error " +
           "WHERE mv.id = :id")
    int updateParseStatus(@Param("id") Long id,
                          @Param("status") String status,
                          @Param("error") String error);
}
