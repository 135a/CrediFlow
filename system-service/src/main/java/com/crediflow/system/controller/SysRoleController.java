package com.crediflow.system.controller;

import com.crediflow.common.web.Result;
import com.crediflow.system.entity.SysRole;
import com.crediflow.system.service.SysRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/role")
public class SysRoleController {
    
    @Autowired
    private SysRoleService sysRoleService;

    @PreAuthorize("hasAuthority('sys:role:list')")
    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.success(sysRoleService.list());
    }

    @PreAuthorize("hasAuthority('sys:role:add')")
    @PostMapping("/add")
    public Result<Boolean> add(@RequestBody SysRole role) {
        return Result.success(sysRoleService.save(role));
    }
}
