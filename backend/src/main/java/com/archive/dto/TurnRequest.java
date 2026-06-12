package com.archive.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 多轮问答 POST 请求体. */
@Data
public class TurnRequest {
    @NotBlank
    private String question;
}
