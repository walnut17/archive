package com.archive.service;

import com.archive.dto.ProjectResponse;
import com.archive.entity.Project;
import com.archive.entity.User;
import com.archive.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 数据脱敏服务 (RI-69).
 */
@Service
@RequiredArgsConstructor
public class MaskingService {

    private final RbacService rbacService;
    private final UserRepository userRepository;

    public ProjectResponse applyMasking(Project project, Long viewerId) {
        ProjectResponse resp = ProjectResponse.from(project);
        resp.setCustomerName(project.getCustomerName());

        if (viewerId == null) {
            resp.setMasked(false);
            return resp;
        }

        User viewer = userRepository.findById(viewerId).orElse(null);
        if (viewer == null || rbacService.hasRole(viewerId, "admin")) {
            resp.setMasked(false);
            return resp;
        }

        boolean shouldMask = !Boolean.TRUE.equals(viewer.getSensitiveViewEnabled())
                && rbacService.hasRole(viewerId, "committee");

        if (!shouldMask) {
            resp.setMasked(false);
            return resp;
        }

        resp.setMasked(true);
        resp.setDisplayName(maskName(project.getCustomerName()));
        resp.setDisplayAmount(maskAmount(project.getAmountWan()));
        resp.setUnmaskRequestUrl("/api/projects/" + project.getId() + "/unmask-request");
        if (project.getCustomerName() != null) {
            resp.setCustomerName(maskName(project.getCustomerName()));
        }
        if (project.getAmountWan() != null) {
            resp.setAmountWan(null);
        }
        return resp;
    }

    private String maskName(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        return name.charAt(0) + "**";
    }

    private String maskAmount(Long amount) {
        if (amount == null) {
            return null;
        }
        return "***万";
    }
}
