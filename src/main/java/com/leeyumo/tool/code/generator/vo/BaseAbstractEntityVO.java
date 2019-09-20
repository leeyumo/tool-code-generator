package com.leeyumo.tool.code.generator.vo;

import com.leeyumo.twelve.commons.entity.AbstractBaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;


@Data
public class BaseAbstractEntityVO {

    @ApiModelProperty(
            value = "主键",
            name = "id"
    )
    private Long id;
    @ApiModelProperty(
            value = "创建时间",
            name = "createdAt"
    )
    private Long createdAt;
    @ApiModelProperty(
            value = "修改时间",
            name = "updatedAt"
    )
    private Long updatedAt;
    @ApiModelProperty(
            value = "逻辑删除标识",
            name = "deleted"
    )
    private Boolean deleted;

    protected BaseAbstractEntityVO(AbstractBaseEntity source) {
        this.setId(source.getId());
        this.setCreatedAt(source.getCreatedAt().toEpochMilli());
        this.setUpdatedAt(source.getUpdatedAt().toEpochMilli());
        this.setDeleted(source.getDeleted());
    }

    protected BaseAbstractEntityVO() {
    }
}
