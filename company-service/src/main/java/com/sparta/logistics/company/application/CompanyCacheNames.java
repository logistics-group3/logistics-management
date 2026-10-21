package com.sparta.logistics.company.application;

/** 내부 조회 캐시 이름. 저장소 설정은 infrastructure/config/CompanyCacheConfig에 있다. */
public final class CompanyCacheNames {

    public static final String INTERNAL_COMPANY = "internalCompany";
    public static final String INTERNAL_PRODUCT = "internalProduct";

    private CompanyCacheNames() {
    }
}
