/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mule.transport.file.FileTestUtils.createDataFile;
import static org.mule.transport.file.FileTestUtils.createFolder;
import org.mule.api.MuleMessage;
import org.mule.api.client.LocalMuleClient;

import java.io.File;

import org.junit.Test;

public class FileNameFilterTestCase extends AbstractFileFunctionalTestCase
{

    @Override
    protected String getConfigFile()
    {
        return "file-filename-filter-config.xml";
    }

    @Test
    public void filtersFile() throws Exception
    {
        File folder = createFolder(".mule");
        createDataFile(folder, TEST_MESSAGE);

        LocalMuleClient client = muleContext.getClient();

        MuleMessage response = client.request("vm://testOut", RECEIVE_TIMEOUT);

        assertNotNull("Did not processed the file", response);
        assertEquals(TEST_MESSAGE, response.getPayload());
    }
}
