package com.company.messenger.domain.notice;
import java.util.List;
public record NoticePageResponse(List<NoticeResponse> items, int page, int size, long totalElements) { }
