package com.carcare.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 对某条 AI 回答点赞/点踩。用于积累效果反馈，
 * 后续可以据此把差评的对话捞出来复盘提示词或知识库
 */
@Data
@Schema(description = "AI 回答反馈")
public class AiFeedbackDTO {

    @NotNull(message = "不能为空")
    @Min(value = 1, message = "只能是 1（赞）或 2（踩）")
    @Max(value = 2, message = "只能是 1（赞）或 2（踩）")
    @Schema(description = "1 赞 / 2 踩")
    private Integer feedback;
}
