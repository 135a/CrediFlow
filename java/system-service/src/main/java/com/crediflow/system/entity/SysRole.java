package com.crediflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("cf_sys_role")
public class SysRole {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String roleCode;
    private String roleName;
    private Date createdAt;
    private Date updatedAt;
}
