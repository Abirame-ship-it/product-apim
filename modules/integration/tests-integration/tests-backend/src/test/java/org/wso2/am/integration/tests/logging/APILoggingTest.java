/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.am.integration.tests.logging;

import org.apache.axis2.AxisFault;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationKeyDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationKeyGenerateRequestDTO;
import org.wso2.am.integration.test.utils.APIManagerIntegrationTestException;
import org.wso2.am.integration.test.utils.base.APIMIntegrationConstants;
import org.wso2.am.integration.test.utils.bean.APIRequest;
import org.wso2.am.integration.test.utils.http.HTTPSClientUtils;
import org.wso2.am.integration.tests.api.lifecycle.APIManagerLifecycleBaseTest;
import org.wso2.am.integration.tests.restapi.RESTAPITestConstants;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2.carbon.integration.common.admin.client.LogViewerClient;
import org.wso2.carbon.logging.view.data.xsd.LogEvent;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.xml.xpath.XPathExpressionException;

import static org.testng.Assert.assertEquals;

public class APILoggingTest extends APIManagerLifecycleBaseTest {
    private LogViewerClient logViewerClient;
    private String apiId;
    private String applicationId;

    @Factory(dataProvider = "userModeDataProvider")
    public APILoggingTest(TestUserMode userMode) {
        this.userMode = userMode;
    }

    @DataProvider
    public static Object[][] userModeDataProvider() {
        return new Object[][]{new Object[]{TestUserMode.SUPER_TENANT_ADMIN}, new Object[]{TestUserMode.TENANT_ADMIN}};
    }

    @BeforeClass(alwaysRun = true)
    public void initialize() throws APIManagerIntegrationTestException, AxisFault, XPathExpressionException {
        super.init();
        logViewerClient = new LogViewerClient(gatewayContextMgt.getContextUrls().getBackEndUrl(),
                createSession(gatewayContextMgt));
    }

    @Test(groups = {"wso2.am" }, description = "Sending http request to per API logging enabled API: ")
    public void testAPIPerAPILoggingTestcase() throws Exception {
        boolean isPercentEncoded = false;
        logViewerClient.clearLogs();

        // Get list of APIs without any API
        Map<String, String> header = new HashMap<>();
        byte[] encodedBytes = Base64.encodeBase64(RESTAPITestConstants.BASIC_AUTH_HEADER.getBytes(StandardCharsets.UTF_8));
        header.put("Authorization", "Basic " + new String(encodedBytes, StandardCharsets.UTF_8));
        header.put("Content-Type", "application/json");
        HttpResponse loggingResponse = HTTPSClientUtils.doGet(getStoreURLHttps()
                + "api/am/devops/v1/tenant-logs/carbon.super/apis?logging-enabled=false", header);
        Assert.assertEquals(loggingResponse.getData(), "{\"apis\":[]}");

        String API_NAME = "AddNewMediationAndInvokeAPITest";
        String API_CONTEXT = "AddNewMediationAndInvokeAPI";
        String API_TAGS = "testTag1, testTag2, testTag3";
        String API_END_POINT_POSTFIX_URL = "xmlapi";
        String API_VERSION_1_0_0 = "1.0.0";
        String APPLICATION_NAME = "AddNewMediationAndInvokeAPI";
        HttpResponse applicationResponse = restAPIStore.createApplication(APPLICATION_NAME,
                "Test Application AccessibilityOfBlockAPITestCase", APIMIntegrationConstants.APPLICATION_TIER.UNLIMITED,
                ApplicationDTO.TokenTypeEnum.JWT);
        applicationId = applicationResponse.getData();
        APIRequest apiRequest;
        String apiEndPointUrl = getAPIInvocationURLHttp(API_END_POINT_POSTFIX_URL, API_VERSION_1_0_0);
        apiRequest = new APIRequest(API_NAME, API_CONTEXT, new URL(apiEndPointUrl));
        apiRequest.setVersion(API_VERSION_1_0_0);
        apiRequest.setTiersCollection(APIMIntegrationConstants.API_TIER.UNLIMITED);
        apiRequest.setTier(APIMIntegrationConstants.API_TIER.UNLIMITED);
        apiRequest.setTags(API_TAGS);
        apiId = createPublishAndSubscribeToAPIUsingRest(apiRequest, restAPIPublisher, restAPIStore,
                applicationId, APIMIntegrationConstants.API_TIER.UNLIMITED);

        // Get list of APIs with a logging disabled API
        loggingResponse = HTTPSClientUtils.doGet(getStoreURLHttps()
                + "api/am/devops/v1/tenant-logs/carbon.super/apis?logging-enabled=false", header);
        Assert.assertEquals(loggingResponse.getData(), "{\"apis\":[{\"context\":\"/AddNewMediationAndInvokeAPI/1.0.0\",\"logLevel\":\"OFF\",\"apiId\":\"" + apiId + "\"}]}");

        // Enable logging of the API
        String addNewLoggerPayload = "{ \"logLevel\": \"FULL\" }";
        HTTPSClientUtils.doPut(getStoreURLHttps() + "api/am/devops/v1/tenant-logs/carbon.super/apis/" + apiId, header, addNewLoggerPayload);

        // Get list of APIs with a logging enabled API
        loggingResponse = HTTPSClientUtils.doGet(getStoreURLHttps()
                + "api/am/devops/v1/tenant-logs/carbon.super/apis?logging-enabled=false", header);
        Assert.assertEquals(loggingResponse.getData(), "{\"apis\":[{\"context\":\"/AddNewMediationAndInvokeAPI/1.0.0\",\"logLevel\":\"FULL\",\"apiId\":\"" + apiId + "\"}]}");

        // Create application and invoke API
        ArrayList grantTypes = new ArrayList();
        grantTypes.add("client_credentials");
        ApplicationKeyDTO applicationKeyDTO = restAPIStore.generateKeys(applicationId, "3600", null,
                ApplicationKeyGenerateRequestDTO.KeyTypeEnum.PRODUCTION, null, grantTypes);
        String accessToken = applicationKeyDTO.getToken().getAccessToken();
        HttpClient client = HttpClientBuilder.create().setHostnameVerifier(new AllowAllHostnameVerifier()).build();
        HttpGet request = new HttpGet(getAPIInvocationURLHttp(API_CONTEXT, API_VERSION_1_0_0));
        request.setHeader("Authorization", "Bearer " + accessToken);
        org.apache.http.HttpResponse response = client.execute(request);
        assertEquals(response.getStatusLine().getStatusCode(), HTTP_RESPONSE_CODE_OK,
                "Invocation fails for GET request");

        // Validate API Logs
        LogEvent[] logs = logViewerClient.getAllSystemLogs();
        for (LogEvent logEvent : logs) {
            String message = logEvent.getMessage();
            if (message.contains(" | response-out<<<< | GET | AddNewMediationAndInvokeAPI/1.0.0 | PAYLOAD | ")) {
                isPercentEncoded = true;
                break;
            }
        }
        Assert.assertTrue(isPercentEncoded,
                "Reserved character should be percent encoded while uri-template expansion");
    }

    @AfterClass(alwaysRun = true)
    void destroy() throws Exception {
        restAPIStore.deleteApplication(applicationId);
        restAPIPublisher.deleteAPI(apiId);
    }
}
