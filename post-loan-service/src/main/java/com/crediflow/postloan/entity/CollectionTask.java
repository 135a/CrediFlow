package com.crediflow.postloan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("cf_collection_task")
public class CollectionTask {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long contractId;
    private Long planId;
    private Long userId;
    private String status; // INIT, IN_PROGRESS, FINISHED
    private String method; // SMS, PHONE
    private Date createdAt;
    private Date updatedAt;
}
