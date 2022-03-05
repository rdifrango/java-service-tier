package com.difrango.cloudchallenge.audit;

/**
 * Created by rdifrango on 2/19/15.
 */
record AuditData(String className, String methodName, Object[] requestData, Object responseData) {
}
