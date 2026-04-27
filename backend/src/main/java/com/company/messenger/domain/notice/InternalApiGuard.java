package com.company.messenger.domain.notice;

import com.company.messenger.global.exception.BusinessException;
import com.company.messenger.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InternalApiGuard {

    private final InternalApiProperties internalApiProperties;

    public void verify(String apiKey) {
        if (apiKey == null || !apiKey.equals(internalApiProperties.internalApiKey())) {
            throw new BusinessException(ErrorCode.INVALID_INTERNAL_API_KEY);
        }
    }
}

