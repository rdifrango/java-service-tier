After attending the 2014 version of AWS re:Invent and hearing the announcement of [Lambda](http://aws.amazon.com/lambda/), I was intrigued about the possibilities of what could be accomplished with it and if it could simplify my software and infrastructure.

First, what is [Lambda](http://aws.amazon.com/lambda/), from their Amazon’s page [Lambda](http://aws.amazon.com/lambda/) is:

> AWS Lambda is a compute service that runs your code in response to events and automatically manages the compute resources for you, making it easy to build applications that respond quickly to new information.

Now, doesn’t that sound great, I can build a service where I don’t have to manage the infrastructure associated with the resource. Now the question is, what scenario(s) would this apply to, again from their page:

> AWS Lambda starts running your code within milliseconds of an event such as an image upload, in-app activity, website click, or output from a connected device.”

That sentence, in particular the words “in-app activity” immediately made me think of a good use for the technology. We were in the process of building an in-house application where one of the requirements was to log each and every request/response to an external store or log stream.

Given this, the first order of business was to create my [Lambda](http://aws.amazon.com/lambda/) function that handles the event.  In order to do this, I highly recommend you read the [Getting Started Guide](http://docs.aws.amazon.com/lambda/latest/dg/welcome.html) and in particular the section on [handling custom events](http://docs.aws.amazon.com/lambda/latest/dg/getting-started-custom-events.html). I will attempt to summarize the high level steps I went through:

1.  I logged into the AWS Console, clicking on the Lambda link.
	1.  If you haven’t previously set it up, there will be a get started button which when clicked will create the appropriate IAM role to run your Lambda functions.
2.  I then clicked on the link to create a new function. All Lambda functions are [Node.js](http://nodejs.org/) based and accepts JSON as the incoming payload.  To help you get started, AWS does provide with a sample, but here’s what I ended up with:

```javascript
exports.audit = function(event, context) 
{ 
	console.log('method = ' + event.className); 
	console.log('method = ' + event.methodName); 
	console.log('requestData = ' + JSON.stringify(event.requestData)); 
	console.log('responseData = ' + JSON.stringify(event.responseData)); 
	context.done(null, 'Request Audit');
	// SUCCESS with message 
};```

Note: Essentially the code takes the incoming request, parses the JSON, logs the Class Name where the event occurred, the Method name that it occurred in, and the request and response data which are JSON.stringify() so that they appear appropriately in the log.

1.  Then I used the built-in [Lambda](http://aws.amazon.com/lambda/) tester app to ensure that my function executes as expected.  The nice thing about the app is that it allows you to enter the expected JSON and it calls your function in near real-time so that you can see what the output would be.

Now, that the [Lambda](http://aws.amazon.com/lambda/) function is in place, I need to wire it into my application.  In my case, we built a [Java Spring Boot](http://projects.spring.io/spring-boot/) based application so I did the following:

1.  Wired in the [AWS Java SDK](http://aws.amazon.com/sdk-for-java/) via my [Maven](http://maven.apache.org/) pom.xml.  In my case, I only wired in the Lambda jar file as follows:

`com.amazonaws aws-java-sdk-lambda 1.9.20.1`

1.  Created a file called AwsCredentials.properties that holds the access key and secret key.
2.  Create an Aspect that picked out all the entry points into my application as follows:

```java
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
```

1.  Then I ran the application to confirm it worked, namely connected to the AWS region and I could execute a call.
2.  Once, I ran a few local calls, I logged into the AWS console and looked at the log stream for the lambda function and I saw the function execution as follows:

`2015-02-27 00:51:48 UTC 2015-02-27T00:51:48.008Z 7w4122c2ydkstwi3 Loading Audit Event 2015-02-27 00:51:48 UTC START RequestId: cd859b76-be1a-11e4-bc5f-81b3a5a885a9 2015-02-27 00:51:48 UTC 2015-02-27T00:51:48.127Z cd859b76-be1a-11e4-bc5f-81b3a5a885a9 method = class com.difrango.cloudchallenge.controller.PersonController 2015-02-27 00:51:48 UTC 2015-02-27T00:51:48.127Z cd859b76-be1a-11e4-bc5f-81b3a5a885a9 method = getPeople 2015-02-27 00:51:48 UTC 2015-02-27T00:51:48.127Z cd859b76-be1a-11e4-bc5f-81b3a5a885a9 requestData = [] 2015-02-27 00:51:48 UTC 2015-02-27T00:51:48.127Z cd859b76-be1a-11e4-bc5f-81b3a5a885a9 responseData = [{"id":40,"name":"Rumpelstiltskin","tasks":[{"id":46,"name":"Spin straw into gold","description":null,"startDate":null,"endDate":null},{"id":47,"name":"Don't tell anyone my name","description":null,"startDate":null,"endDate":null}]},{"id":41,"name":"King","tasks":[{"id":48,"name":"Put miller's daughter in tower","description":null,"startDate":null,"endDate":null},{"id":49,"name":"Put straw in tower","description":null,"startDate":null,"endDate":null},{"id":50,"name":"Check to see if miller's daughter made gold from staw","description":null,"startDate":null,"endDate":null}]},{"id":42,"name":"Miller's Daughter","tasks":[{"id":51,"name":"Figure out Rumpelstiltskin's name","description":null,"startDate":null,"endDate":null}]},{"id":43,"name":"Ron DiFrango","tasks":[]}] 2015-02-27 00:51:48 UTC 2015-02-27T00:51:48.127Z cd859b76-be1a-11e4-bc5f-81b3a5a885a9 Message: "Request Audit" 2015-02-27 00:51:48 UTC END RequestId: cd859b76-be1a-11e4-bc5f-81b3a5a885a9 2015-02-27 00:51:48 UTC REPORT RequestId: cd859b76-be1a-11e4-bc5f-81b3a5a885a9 Duration: 118.45 ms Billed Duration: 200 ms Memory Size: 128 MB Max Memory Used: 27 MB`

So great, now my application is wired to send events to my lambda function and I have log stream of all my traffic.  Given that Lambda functions are Node.js, there is much more I could do, like log this to an [S3 Bucket](http://aws.amazon.com/s3), store in [Dynamo DB](http://aws.amazon.com/dynamodb/), etc.  In a future post, I might explore how that might be accomplished.

The question you might be asking yourself is, how is this better than a traditional approach.  In a traditional approach, I most likely would have posted the data onto a Message Queue, then built a listener app to read the messages off the queue and process them as appropriate.  With this approach, I have none of this so its greatly simplified.  The one negative to this approach is that I am tightly coupled to AWS’s service and implementation.

In closing, I believe that [Lambda](http://aws.amazon.com/lambda/) is a great offering and if you are tied to AWS as your cloud provider it can greatly simplify your software stack.
