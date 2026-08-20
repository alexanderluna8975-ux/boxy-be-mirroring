package com.boxy.boxy.core.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedBranchAccessException extends BusinessException {
    public UnauthorizedBranchAccessException(String branchId) {
        super("BRANCH_ACCESS_DENIED", "You do not have active authorization to access or operate on branch: " + branchId, HttpStatus.FORBIDDEN);
    }
}
