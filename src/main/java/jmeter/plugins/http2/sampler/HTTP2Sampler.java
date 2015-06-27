/*
 *  Copyright 2015 Ryo Okubo
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package jmeter.plugins.http2.sampler;

import java.net.InetSocketAddress;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;

import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.*;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

public class HTTP2Sampler extends AbstractSampler {

    private static final Logger log = LoggingManager.getLoggerForClass();

    public static final String METHOD = "HTTP2Sampler.method";
    public static final String DOMAIN = "HTTP2Sampler.domain";
    public static final String PORT = "HTTP2Sampler.port";
    public static final String PATH = "TTP2Sampler.host";

    public static final String DEFAULT_METHOD = "GET";

    public HTTP2Sampler() {
        super();
        setName("HTTP2 Sampler");
    }

    @Override
    public void setName(String name) {
        if (name != null) {
            setProperty(TestElement.NAME, name);
        }
    }

    @Override
    public String getName() {
        return getPropertyAsString(TestElement.NAME);
    }

    @Override
    public SampleResult sample(Entry e)
    {
        log.debug("sample()");

        SampleResult sampleResult = new SampleResult();
        sampleResult.setSampleLabel(getName());

        // Start sampling
        sampleResult.sampleStart();

        // Send H2 request
        NettyHttp2Client client = new NettyHttp2Client(getMethod(), getDomain(), getPort(), getPath());
        client.request();

        // End sampling
        sampleResult.sampleEnd();
        sampleResult.setSuccessful(true);

        return sampleResult;
    }

    public void setMethod(String value) {
      setProperty(METHOD, value);
    }

    private String getMethod() {
      return getPropertyAsString(METHOD);
    }

    public void setDomain(String value) {
      setProperty(DOMAIN, value);
    }

    private String getDomain() {
      return getPropertyAsString(DOMAIN);
    }

    public void setPort(int value) {
      setProperty(PORT, value);
    }

    private int getPort() {
      return getPropertyAsInt(PORT);
    }

    public void setPath(String value) {
      setProperty(PATH, value);
    }

    private String getPath() {
      return getPropertyAsString(PATH);
    }
}

