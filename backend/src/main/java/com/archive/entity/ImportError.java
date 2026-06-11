package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Excel 导入行级错误.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "import_error", indexes = {
        @Index(name = "idx_import_error_batch", columnList = "batch_id")
})
public class ImportError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "row_num", nullable = false)
    private Integer row;

    @Column(name = "col_num")
    private Integer column;

    @Column(name = "error_msg", length = 1000)
    private String errorMsg;
}
