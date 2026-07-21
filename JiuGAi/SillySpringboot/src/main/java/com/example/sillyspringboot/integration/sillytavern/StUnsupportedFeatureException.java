package com.example.sillyspringboot.integration.sillytavern;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;

/** StAdapter 方法尚未接入 ST 语义时抛出。 */
public class StUnsupportedFeatureException extends BusinessException {

    public StUnsupportedFeatureException() {
        super(ErrorCode.UNSUPPORTED_OPERATION, "该能力在当前版本不可用");
    }
}
