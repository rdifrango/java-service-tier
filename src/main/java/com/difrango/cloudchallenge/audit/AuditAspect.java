/* (C)2022 */
package com.difrango.cloudchallenge.audit;

import com.amazonaws.auth.ClasspathPropertiesFileCredentialsProvider;
import com.amazonaws.services.lambda.AWSLambdaAsyncClient;
import com.amazonaws.services.lambda.model.InvokeAsyncRequest;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javax.annotation.PostConstruct;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Aspect that picks out all the join points in the application where we want to log the events. */
@Aspect
@Component
public class AuditAspect {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ObjectMapper mapper;
    private ClasspathPropertiesFileCredentialsProvider propertiesFileCredentialsProvider;
    private AWSLambdaAsyncClient awsLambdaAsyncClient;

    @PostConstruct
    public void setup() {
        /*
         * Create a Jackson mapper to convert the parameters to JSON
         */
        mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        /*
         * Connect to the AWS service by using the classpath locator
         */
        propertiesFileCredentialsProvider = new ClasspathPropertiesFileCredentialsProvider();
        /*
         * Create an AWS Async client using the credentials found on the classpath.
         */
        awsLambdaAsyncClient =
                new AWSLambdaAsyncClient(propertiesFileCredentialsProvider.getCredentials());
    }

    /**
     * This method picks out all the public methods in our REST controller.
     *
     * @param pjp
     * @return
     * @throws Throwable
     */
    @Around(
            value =
                    "execution(public *"
                            + " com.difrango.cloudchallenge.controller.PersonController.*(..))")
    public Object doBasicProfiling(ProceedingJoinPoint pjp) throws Throwable {
        Object retVal = null;
        try {
            try {
                /*
                 * Execute the underlying method
                 */
                retVal = pjp.proceed();
                /*
                 * Return as normal
                 */
                return retVal;
            } catch (Exception e) {
                retVal = e;
                throw e;
            }
        } finally {
            /*
             * But before we leave, lets log this via our Lambda function.
             */
            try {
                /*
                 * Create the data to log.
                 */
                var data =
                        new AuditData(
                                pjp.getTarget().getClass().toString(),
                                pjp.getSignature().getName(),
                                pjp.getArgs(),
                                retVal);
                /*
                 * Convert it to JSON via Jackson
                 */
                var json = mapper.writeValueAsString(data);
                logger.debug("JSON: {}", json);
                /*
                 * Invoke the lambda function, asynchronously.  In this case we really don't care about check the result.
                 */
                var invokeAsyncRequest =
                        new InvokeAsyncRequest()
                                .withFunctionName("auditHandler")
                                .withInvokeArgs(json);
                var invokeAsyncResultFuture =
                        awsLambdaAsyncClient.invokeAsyncAsync(invokeAsyncRequest);
                /*
                 * We we will log it that we sent the event but discard the future as we don't want
                 * to block waiting for it..
                 */
                logger.debug("invokeAsyncResultFuture: {}", invokeAsyncResultFuture);
            } catch (JsonProcessingException e) {
                /*
                 * Log it if we have an error.
                 */
                logger.debug("Error processing", e);
            }
        }
    }
}
